from __future__ import annotations

import logging
import os
import re
import sys
from dataclasses import dataclass
from datetime import datetime, timezone
from typing import Any, Sequence
from urllib.parse import quote

import requests
from requests import Response, Session
from sklearn.feature_extraction.text import TfidfVectorizer
from sklearn.metrics.pairwise import cosine_similarity


API_BASE_URL = "https://api.github.com"
API_VERSION = "2022-11-28"
COMMENT_MARKER_PREFIX = "<!-- duplicate-checker:"
DUPLICATE_LABEL = "duplicate"
REQUEST_TIMEOUT = (5, 30)
SIMILARITY_THRESHOLD = 0.65
REPOSITORY_PATTERN = re.compile(r"^[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+$")


class DuplicateCheckerError(RuntimeError):
    pass


@dataclass(frozen=True)
class Issue:
    number: int
    title: str
    body: str


@dataclass(frozen=True)
class DuplicateMatch:
    issue: Issue
    score: float


class GitHubClient:
    def __init__(self, repository: str, token: str) -> None:
        self._repository = repository
        self._session = Session()
        self._session.headers.update(
            {
                "Accept": "application/vnd.github+json",
                "Authorization": f"Bearer {token}",
                "User-Agent": "local-tfidf-duplicate-checker",
                "X-GitHub-Api-Version": API_VERSION,
            }
        )

    def close(self) -> None:
        self._session.close()

    def get_open_issues(self, excluded_issue_number: int) -> tuple[Issue, ...]:
        url: str | None = f"{API_BASE_URL}/repos/{self._repository}/issues"
        params: dict[str, str | int] | None = {
            "state": "open",
            "per_page": 100,
            "page": 1,
        }
        issues: list[Issue] = []

        while url is not None:
            response = self._request("GET", url, params=params)
            payload = self._json(response)
            if not isinstance(payload, list):
                raise DuplicateCheckerError(
                    "GitHub returned an unexpected response while listing issues."
                )

            for item in payload:
                if not isinstance(item, dict) or "pull_request" in item:
                    continue

                number = item.get("number")
                title = item.get("title")
                if (
                    not isinstance(number, int)
                    or number == excluded_issue_number
                    or not isinstance(title, str)
                ):
                    continue

                body = item.get("body")
                issues.append(
                    Issue(
                        number=number,
                        title=title,
                        body=body if isinstance(body, str) else "",
                    )
                )

            next_link = response.links.get("next")
            url = next_link.get("url") if next_link else None
            params = None

        return tuple(issues)

    def ensure_duplicate_label(self) -> None:
        encoded_label = quote(DUPLICATE_LABEL, safe="")
        label_url = (
            f"{API_BASE_URL}/repos/{self._repository}/labels/{encoded_label}"
        )
        response = self._request("GET", label_url, expected_statuses={200, 404})
        if response.status_code == 200:
            return

        create_url = f"{API_BASE_URL}/repos/{self._repository}/labels"
        create_response = self._request(
            "POST",
            create_url,
            json={
                "name": DUPLICATE_LABEL,
                "color": "d73a4a",
                "description": "Potential duplicate of another issue",
            },
            expected_statuses={201, 422},
        )
        if create_response.status_code == 422:
            logging.info("The duplicate label was created by another workflow run.")

    def apply_duplicate_label(self, issue_number: int) -> None:
        url = (
            f"{API_BASE_URL}/repos/{self._repository}/issues/"
            f"{issue_number}/labels"
        )
        self._request("POST", url, json={"labels": [DUPLICATE_LABEL]})

    def add_duplicate_comment(
        self,
        issue_number: int,
        original_issue_number: int,
    ) -> bool:
        marker = f"{COMMENT_MARKER_PREFIX}{original_issue_number} -->"
        if self._has_comment(issue_number, marker):
            return False

        url = (
            f"{API_BASE_URL}/repos/{self._repository}/issues/"
            f"{issue_number}/comments"
        )
        self._request(
            "POST",
            url,
            json={"body": f"{marker}\nPotential duplicate of #{original_issue_number}"},
            expected_statuses={201},
        )
        return True

    def _has_comment(self, issue_number: int, marker: str) -> bool:
        url: str | None = (
            f"{API_BASE_URL}/repos/{self._repository}/issues/"
            f"{issue_number}/comments?per_page=100"
        )

        while url is not None:
            response = self._request("GET", url)
            payload = self._json(response)
            if not isinstance(payload, list):
                raise DuplicateCheckerError(
                    "GitHub returned an unexpected response while listing comments."
                )

            for item in payload:
                if not isinstance(item, dict):
                    continue
                body = item.get("body")
                if isinstance(body, str) and marker in body:
                    return True

            next_link = response.links.get("next")
            url = next_link.get("url") if next_link else None

        return False

    def _request(
        self,
        method: str,
        url: str,
        *,
        expected_statuses: set[int] | None = None,
        **kwargs: Any,
    ) -> Response:
        accepted_statuses = expected_statuses or {200}
        try:
            response = self._session.request(
                method,
                url,
                timeout=REQUEST_TIMEOUT,
                **kwargs,
            )
        except requests.Timeout as error:
            raise DuplicateCheckerError(
                "The GitHub API request timed out. Run the workflow again later."
            ) from error
        except requests.RequestException as error:
            raise DuplicateCheckerError(
                f"The GitHub API request failed: {error}"
            ) from error

        if response.status_code in accepted_statuses:
            return response

        message = self._response_message(response)
        remaining = response.headers.get("X-RateLimit-Remaining")
        if (
            response.status_code == 429
            or remaining == "0"
            or "rate limit" in message.casefold()
        ):
            raise DuplicateCheckerError(self._rate_limit_message(response, message))

        raise DuplicateCheckerError(
            f"GitHub API returned HTTP {response.status_code}: {message}"
        )

    @staticmethod
    def _json(response: Response) -> Any:
        try:
            return response.json()
        except requests.JSONDecodeError as error:
            raise DuplicateCheckerError(
                "GitHub returned a response that was not valid JSON."
            ) from error

    @staticmethod
    def _response_message(response: Response) -> str:
        try:
            payload = response.json()
        except requests.JSONDecodeError:
            return response.text.strip() or "No error details were provided."
        if isinstance(payload, dict) and isinstance(payload.get("message"), str):
            return payload["message"]
        return "No error details were provided."

    @staticmethod
    def _rate_limit_message(response: Response, message: str) -> str:
        retry_after = response.headers.get("Retry-After")
        if retry_after:
            return (
                "GitHub API rate limit exceeded. "
                f"Retry after {retry_after} seconds. Details: {message}"
            )

        reset_value = response.headers.get("X-RateLimit-Reset")
        if reset_value and reset_value.isdigit():
            reset_at = datetime.fromtimestamp(
                int(reset_value),
                tz=timezone.utc,
            ).isoformat()
            return (
                "GitHub API rate limit exceeded. "
                f"The limit resets at {reset_at}. Details: {message}"
            )

        return f"GitHub API rate limit exceeded. Details: {message}"


def weighted_issue_text(title: str, body: str) -> str:
    return "\n".join(part for part in (title.strip(), title.strip(), body.strip()) if part)


def find_duplicate(
    title: str,
    body: str,
    existing_issues: Sequence[Issue],
) -> DuplicateMatch | None:
    if not existing_issues:
        return None

    documents = [weighted_issue_text(title, body)]
    documents.extend(
        weighted_issue_text(issue.title, issue.body) for issue in existing_issues
    )

    try:
        vectors = TfidfVectorizer(stop_words="english").fit_transform(documents)
    except ValueError as error:
        if "empty vocabulary" in str(error).casefold():
            logging.info("No meaningful terms were available for duplicate comparison.")
            return None
        raise

    scores = cosine_similarity(vectors[0:1], vectors[1:]).ravel()
    best_index = int(scores.argmax())
    best_score = float(scores[best_index])
    if best_score <= SIMILARITY_THRESHOLD:
        return None

    return DuplicateMatch(issue=existing_issues[best_index], score=best_score)


def read_positive_integer_environment(name: str) -> int:
    value = os.environ.get(name, "")
    try:
        parsed_value = int(value)
    except ValueError as error:
        raise DuplicateCheckerError(
            f"The {name} environment variable must be a positive integer."
        ) from error
    if parsed_value <= 0:
        raise DuplicateCheckerError(
            f"The {name} environment variable must be a positive integer."
        )
    return parsed_value


def validate_repository(repository: str) -> str:
    if not REPOSITORY_PATTERN.fullmatch(repository):
        raise DuplicateCheckerError(
            "Repository must use the 'owner/repository' format."
        )
    return repository


def run(title: str, body: str, repository: str) -> None:
    token = os.environ.get("GITHUB_TOKEN", "").strip()
    if not token:
        raise DuplicateCheckerError("The GITHUB_TOKEN environment variable is required.")

    issue_number = read_positive_integer_environment("GITHUB_ISSUE_NUMBER")
    validated_repository = validate_repository(repository)
    client = GitHubClient(validated_repository, token)
    try:
        existing_issues = client.get_open_issues(issue_number)
        logging.info("Comparing issue #%d with %d open issues.", issue_number, len(existing_issues))
        match = find_duplicate(title, body, existing_issues)
        if match is None:
            logging.info("No issue exceeded the %.2f similarity threshold.", SIMILARITY_THRESHOLD)
            return

        logging.info(
            "Issue #%d matched issue #%d with similarity %.4f.",
            issue_number,
            match.issue.number,
            match.score,
        )
        client.ensure_duplicate_label()
        client.apply_duplicate_label(issue_number)
        comment_added = client.add_duplicate_comment(issue_number, match.issue.number)
        if comment_added:
            logging.info("Added a duplicate reference comment to issue #%d.", issue_number)
        else:
            logging.info("The duplicate reference comment already exists.")
    finally:
        client.close()


def main(argv: Sequence[str]) -> int:
    logging.basicConfig(level=logging.INFO, format="%(levelname)s: %(message)s")
    if len(argv) != 3:
        logging.error(
            "Usage: duplicate_checker.py <issue-title> <issue-body> <owner/repository>"
        )
        return 2

    title, body, repository = argv
    try:
        run(title, body or "", repository)
    except DuplicateCheckerError as error:
        logging.error("Duplicate check failed: %s", error)
        return 1
    except Exception:
        logging.exception("Duplicate check failed with an unexpected error.")
        return 1
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
