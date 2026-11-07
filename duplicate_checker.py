from __future__ import annotations

import logging
import os
import re
import sys
import unicodedata
from dataclasses import dataclass
from datetime import datetime, timedelta, timezone
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
DEFAULT_SIMILARITY_THRESHOLD = 0.55
MAX_CLOSED_ISSUES = 300
RECENTLY_CLOSED_WINDOW = timedelta(days=60)
MAX_RELATED_MATCHES = 3
MIN_MEANINGFUL_TOKENS = 6
MAX_LOG_LINES = 20
MAX_LOG_FRAMES = 8
WORD_WEIGHT = 0.7
CHARACTER_WEIGHT = 0.3
REPOSITORY_PATTERN = re.compile(r"^[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+$")

IGNORED_SECTIONS = frozenset(
    {
        "affected area",
        "android version",
        "android version / rom",
        "archivetune version",
        "bug submission checklist",
        "category",
        "device model",
        "how often does it happen?",
        "installed release channel where the bug happens",
        "latest nightly verification",
        "nightly build used, or stable-only reason",
        "no ai / no llm policy",
        "pre-submission checklist",
        "release channel",
        "reproducibility",
        "required pre-submission checklist",
        "screenshots or screen recordings",
        "visual context & attachments",
        "visual evidence",
        "where does the bug happen?",
    }
)
LOG_SECTION_PREFIX = "logs"
NO_RESPONSE_VALUES = frozenset({"_no response_", "no response", "n/a", "none"})
IGNORED_FRAME_PREFIXES = (
    "android.",
    "androidx.",
    "com.android.",
    "dalvik.",
    "java.",
    "javax.",
    "jdk.",
    "kotlin.",
    "kotlinx.",
    "libcore.",
    "sun.",
)

SECTION_HEADING_PATTERN = re.compile(r"^\s{0,3}#{1,6}\s+(?P<heading>.+?)\s*#*\s*$")
CODE_FENCE_PATTERN = re.compile(
    r"^\s*(?P<fence>`{3,}|~{3,})[^\n]*\n(?P<code>.*?)^\s*(?P=fence)\s*$",
    re.DOTALL | re.MULTILINE,
)
HTML_COMMENT_PATTERN = re.compile(r"<!--.*?-->", re.DOTALL)
HTML_TAG_PATTERN = re.compile(r"<[^>]+>")
CHECKBOX_LINE_PATTERN = re.compile(r"^\s*[-*]\s+\[[ xX]\]\s.*$", re.MULTILINE)
MARKDOWN_IMAGE_PATTERN = re.compile(r"!\[[^\]]*\]\([^)]*\)")
MARKDOWN_LINK_PATTERN = re.compile(r"\[(?P<text>[^\]]*)\]\([^)]*\)")
URL_PATTERN = re.compile(r"\b(?:https?://|www\.)\S+", re.IGNORECASE)
ERROR_CODE_PATTERN = re.compile(
    r"\b(?P<kind>code|status code|status|error)\s*[:=#]?\s*(?P<value>\d{3,5})\b",
    re.IGNORECASE,
)
HEX_PATTERN = re.compile(r"\b(?:0x)?(?=[0-9a-f]*\d)[0-9a-f]{7,}\b", re.IGNORECASE)
NUMBER_PATTERN = re.compile(r"\b\d+(?:[.:/-]\d+)*\b")
MARKDOWN_SYMBOL_PATTERN = re.compile(r"[`*_>#|~]+")
WHITESPACE_PATTERN = re.compile(r"\s+")
TOKEN_PATTERN = re.compile(r"(?u)\b(?=\w*[^\W\d_])\w{2,}\b")
DIAGNOSTIC_LINE_PATTERN = re.compile(
    r"\b(?:exception|error|code|message|cause|caused by|failed|failure|fatal|crash)",
    re.IGNORECASE,
)
EXCEPTION_PATTERN = re.compile(
    r"\b(?:[A-Za-z_$][\w$]*\.)*[A-Z][\w$]*(?:Exception|Error|Throwable)\b"
)
STACK_FRAME_PATTERN = re.compile(
    r"\bat\s+(?P<frame>[A-Za-z_$][\w$]*(?:\.[\w$<>]+)+)\("
)


class DuplicateCheckerError(RuntimeError):
    pass


@dataclass(frozen=True)
class Issue:
    id: int
    number: int
    title: str
    body: str
    labels: frozenset[str]
    state: str
    state_reason: str | None
    closed_at: datetime | None


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

    def get_issue(self, issue_number: int) -> Issue:
        url = f"{self._issues_url()}/{issue_number}"
        issue = parse_issue(self._json(self._request("GET", url)))
        if issue is None:
            raise DuplicateCheckerError(
                "GitHub returned an unexpected response while reading the issue."
            )
        return issue

    def get_issues(
        self,
        state: str,
        sort: str,
        limit: int | None = None,
    ) -> tuple[Issue, ...]:
        url: str | None = self._issues_url()
        params: dict[str, str | int] | None = {
            "state": state,
            "sort": sort,
            "direction": "desc",
            "per_page": 100,
        }
        issues: list[Issue] = []

        while url is not None and (limit is None or len(issues) < limit):
            response = self._request("GET", url, params=params)
            payload = self._json(response)
            if not isinstance(payload, list):
                raise DuplicateCheckerError(
                    "GitHub returned an unexpected response while listing issues."
                )

            issues.extend(
                issue for issue in map(parse_issue, payload) if issue is not None
            )
            next_link = response.links.get("next")
            url = next_link.get("url") if next_link else None
            params = None

        return tuple(issues[:limit])

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
                "color": "cfd3d7",
                "description": "This issue duplicates another issue",
            },
            expected_statuses={201, 422},
        )
        if create_response.status_code == 422:
            logging.info("The duplicate label was created by another workflow run.")

    def apply_duplicate_label(self, issue_number: int) -> None:
        url = f"{self._issues_url()}/{issue_number}/labels"
        self._request("POST", url, json={"labels": [DUPLICATE_LABEL]})

    def add_duplicate_comment(
        self,
        issue_number: int,
        match: DuplicateMatch,
        related_matches: Sequence[DuplicateMatch],
    ) -> bool:
        marker = f"{COMMENT_MARKER_PREFIX}{match.issue.number} -->"
        if self._has_comment(issue_number, marker):
            return False

        url = f"{self._issues_url()}/{issue_number}/comments"
        self._request(
            "POST",
            url,
            json={"body": build_duplicate_comment(marker, match, related_matches)},
            expected_statuses={201},
        )
        return True

    def close_as_duplicate(self, issue_number: int, original: Issue) -> None:
        url = f"{self._issues_url()}/{issue_number}"
        response = self._request(
            "PATCH",
            url,
            json={
                "state": "closed",
                "state_reason": "duplicate",
                "duplicate_issue_id": original.id,
            },
            expected_statuses={200, 422},
        )
        if response.status_code == 200:
            return

        logging.warning(
            "GitHub rejected the duplicate reference to #%d (%s); "
            "closing as duplicate without the linked reference.",
            original.number,
            self._response_message(response),
        )
        self._request(
            "PATCH",
            url,
            json={"state": "closed", "state_reason": "duplicate"},
        )

    def _issues_url(self) -> str:
        return f"{API_BASE_URL}/repos/{self._repository}/issues"

    def _has_comment(self, issue_number: int, marker: str) -> bool:
        url: str | None = f"{self._issues_url()}/{issue_number}/comments?per_page=100"

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


def parse_timestamp(value: Any) -> datetime | None:
    if not isinstance(value, str):
        return None
    try:
        return datetime.fromisoformat(value.replace("Z", "+00:00"))
    except ValueError:
        return None


def parse_issue(item: Any) -> Issue | None:
    if not isinstance(item, dict) or "pull_request" in item:
        return None

    issue_id = item.get("id")
    number = item.get("number")
    title = item.get("title")
    state = item.get("state")
    if (
        not isinstance(issue_id, int)
        or not isinstance(number, int)
        or not isinstance(title, str)
        or not isinstance(state, str)
    ):
        return None

    body = item.get("body")
    state_reason = item.get("state_reason")
    raw_labels = item.get("labels")
    labels = (
        frozenset(
            label["name"].casefold()
            for label in raw_labels
            if isinstance(label, dict) and isinstance(label.get("name"), str)
        )
        if isinstance(raw_labels, list)
        else frozenset()
    )
    return Issue(
        id=issue_id,
        number=number,
        title=title,
        body=body if isinstance(body, str) else "",
        labels=labels,
        state=state,
        state_reason=state_reason if isinstance(state_reason, str) else None,
        closed_at=parse_timestamp(item.get("closed_at")),
    )


def is_duplicate(issue: Issue) -> bool:
    return issue.state_reason == "duplicate" or DUPLICATE_LABEL in issue.labels


def is_candidate(issue: Issue, issue_number: int, closed_after: datetime) -> bool:
    if issue.number >= issue_number or is_duplicate(issue):
        return False
    if issue.state == "open":
        return True
    return issue.closed_at is not None and issue.closed_at >= closed_after


def build_duplicate_comment(
    marker: str,
    match: DuplicateMatch,
    related_matches: Sequence[DuplicateMatch],
) -> str:
    original = match.issue.number
    closed_note = " (already closed)" if match.issue.state != "open" else ""
    lines = [
        marker,
        f"This issue appears to be a duplicate of #{original}{closed_note} "
        f"with {match.score:.0%} similarity, so it is being closed automatically.",
        "",
        f"Please follow #{original} and add any new details there instead.",
    ]
    if related_matches:
        lines.extend(["", "Other similar issues:"])
        lines.extend(
            f"- #{related.issue.number} ({related.score:.0%} similarity)"
            for related in related_matches
        )
    lines.extend(
        [
            "",
            "If this is not a duplicate, reply with what makes it different and a "
            "maintainer can reopen it. Reopened issues are not closed again by this check.",
        ]
    )
    return "\n".join(lines)


def normalize_heading(heading: str) -> str:
    return heading.strip(" *_:").casefold()


def split_sections(body: str) -> list[tuple[str | None, str]]:
    sections: list[tuple[str | None, list[str]]] = [(None, [])]
    in_fence = False
    for line in body.splitlines():
        if line.lstrip().startswith(("```", "~~~")):
            in_fence = not in_fence
        heading = None if in_fence else SECTION_HEADING_PATTERN.match(line)
        if heading is not None:
            sections.append((normalize_heading(heading.group("heading")), []))
        else:
            sections[-1][1].append(line)
    return [(heading, "\n".join(lines)) for heading, lines in sections]


def remove_format_characters(text: str) -> str:
    return "".join(
        character for character in text if unicodedata.category(character) != "Cf"
    )


def tokenize_error_codes(text: str) -> str:
    return ERROR_CODE_PATTERN.sub(
        lambda match: (
            f" {match.group('kind').replace(' ', '').casefold()}{match.group('value')} "
        ),
        text,
    )


def clean_text(text: str) -> str:
    text = remove_format_characters(text)
    text = HTML_COMMENT_PATTERN.sub(" ", text)
    text = CHECKBOX_LINE_PATTERN.sub(" ", text)
    text = MARKDOWN_IMAGE_PATTERN.sub(" ", text)
    text = MARKDOWN_LINK_PATTERN.sub(r"\g<text>", text)
    text = HTML_TAG_PATTERN.sub(" ", text)
    text = URL_PATTERN.sub(" ", text)
    text = tokenize_error_codes(text)
    text = HEX_PATTERN.sub(" ", text)
    text = NUMBER_PATTERN.sub(" ", text)
    text = MARKDOWN_SYMBOL_PATTERN.sub(" ", text)
    return WHITESPACE_PATTERN.sub(" ", text).strip()


def summarize_log(text: str) -> str:
    exceptions = list(dict.fromkeys(EXCEPTION_PATTERN.findall(text)))
    frames = [
        frame
        for frame in dict.fromkeys(
            match.group("frame") for match in STACK_FRAME_PATTERN.finditer(text)
        )
        if not frame.startswith(IGNORED_FRAME_PREFIXES)
    ][:MAX_LOG_FRAMES]
    diagnostic_lines = list(
        dict.fromkeys(
            cleaned
            for line in text.splitlines()
            if DIAGNOSTIC_LINE_PATTERN.search(line)
            and not STACK_FRAME_PATTERN.search(line)
            for cleaned in [clean_text(line)]
            if cleaned
        )
    )[:MAX_LOG_LINES]
    identifiers = " ".join(exceptions + frames).replace(".", " ").replace("$", " ")
    return " ".join(part for part in (*diagnostic_lines, identifiers) if part)


def normalize_prose(text: str) -> str:
    summarized = CODE_FENCE_PATTERN.sub(
        lambda match: f"\n{summarize_log(match.group('code'))}\n",
        text,
    )
    cleaned = clean_text(summarized)
    return "" if cleaned.casefold() in NO_RESPONSE_VALUES else cleaned


def normalize_body(body: str) -> str:
    parts: list[str] = []
    for heading, content in split_sections(body):
        if heading in IGNORED_SECTIONS:
            continue
        if heading is not None and heading.startswith(LOG_SECTION_PREFIX):
            parts.append(summarize_log(content))
        else:
            parts.append(normalize_prose(content))
    return " ".join(part for part in parts if part)


def normalize_title(title: str) -> str:
    return clean_text(re.sub(r"^\s*\[[^\]]*\]\s*:?", " ", title))


def issue_document(title: str, body: str) -> str:
    normalized_title = normalize_title(title)
    return " ".join(
        part
        for part in (normalized_title, normalized_title, normalize_body(body))
        if part
    )


def meaningful_token_count(text: str) -> int:
    return len(TOKEN_PATTERN.findall(text))


def similarity_scores(
    vectorizer: TfidfVectorizer,
    documents: Sequence[str],
    candidate_count: int,
) -> Any:
    try:
        vectors = vectorizer.fit_transform(documents)
    except ValueError as error:
        if "empty vocabulary" in str(error).casefold():
            return None
        raise
    return cosine_similarity(vectors[0:1], vectors[1 : candidate_count + 1]).ravel()


def rank_duplicates(
    title: str,
    body: str,
    candidates: Sequence[Issue],
    reference_issues: Sequence[Issue] = (),
) -> list[DuplicateMatch]:
    if not candidates:
        return []

    query_document = issue_document(title, body)
    if meaningful_token_count(query_document) < MIN_MEANINGFUL_TOKENS:
        logging.info("The issue has too little descriptive text for duplicate comparison.")
        return []

    corpus_issues = [*candidates, *reference_issues]
    documents = [query_document]
    documents.extend(issue_document(issue.title, issue.body) for issue in corpus_issues)

    word_scores = similarity_scores(
        TfidfVectorizer(
            stop_words="english",
            ngram_range=(1, 2),
            sublinear_tf=True,
            token_pattern=TOKEN_PATTERN.pattern,
        ),
        documents,
        len(candidates),
    )
    if word_scores is None:
        logging.info("No meaningful terms were available for duplicate comparison.")
        return []

    character_scores = similarity_scores(
        TfidfVectorizer(analyzer="char_wb", ngram_range=(3, 5), sublinear_tf=True),
        documents,
        len(candidates),
    )
    matches: list[DuplicateMatch] = []
    for index, issue in enumerate(candidates):
        score = WORD_WEIGHT * float(word_scores[index])
        if character_scores is not None:
            score += CHARACTER_WEIGHT * float(character_scores[index])
        matches.append(DuplicateMatch(issue=issue, score=score))

    return sorted(matches, key=lambda match: match.score, reverse=True)


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


def read_threshold_environment(name: str, default: float) -> float:
    value = os.environ.get(name, "").strip()
    if not value:
        return default
    try:
        parsed_value = float(value)
    except ValueError as error:
        raise DuplicateCheckerError(
            f"The {name} environment variable must be a number between 0 and 1."
        ) from error
    if not 0.0 < parsed_value < 1.0:
        raise DuplicateCheckerError(
            f"The {name} environment variable must be a number between 0 and 1."
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
    threshold = read_threshold_environment(
        "DUPLICATE_SIMILARITY_THRESHOLD",
        DEFAULT_SIMILARITY_THRESHOLD,
    )
    validated_repository = validate_repository(repository)
    client = GitHubClient(validated_repository, token)
    try:
        current_issue = client.get_issue(issue_number)
        if current_issue.state != "open":
            logging.info("Issue #%d is no longer open; skipping.", issue_number)
            return
        if current_issue.state_reason == "reopened":
            logging.info(
                "Issue #%d was reopened, so it is not closed again automatically.",
                issue_number,
            )
            return

        open_issues = client.get_issues("open", "created")
        closed_issues = client.get_issues("closed", "updated", MAX_CLOSED_ISSUES)
        closed_after = datetime.now(timezone.utc) - RECENTLY_CLOSED_WINDOW
        known_issues = {
            issue.number: issue
            for issue in (*closed_issues, *open_issues)
            if issue.number != issue_number
        }
        candidates = tuple(
            issue
            for issue in known_issues.values()
            if is_candidate(issue, issue_number, closed_after)
        )
        candidate_numbers = {issue.number for issue in candidates}
        reference_issues = tuple(
            issue
            for issue in known_issues.values()
            if issue.number not in candidate_numbers
        )
        logging.info(
            "Comparing issue #%d with %d earlier issues (%d more used for term weighting).",
            issue_number,
            len(candidates),
            len(reference_issues),
        )

        ranked_matches = rank_duplicates(title, body, candidates, reference_issues)
        for ranked in ranked_matches[: MAX_RELATED_MATCHES + 1]:
            logging.info("  #%d scored %.4f", ranked.issue.number, ranked.score)

        qualifying_matches = [
            match for match in ranked_matches if match.score >= threshold
        ]
        if not qualifying_matches:
            logging.info("No issue reached the %.2f similarity threshold.", threshold)
            return

        match = qualifying_matches[0]
        related_matches = qualifying_matches[1 : MAX_RELATED_MATCHES + 1]
        logging.info(
            "Issue #%d matched issue #%d with similarity %.4f.",
            issue_number,
            match.issue.number,
            match.score,
        )

        client.ensure_duplicate_label()
        client.apply_duplicate_label(issue_number)
        if client.add_duplicate_comment(issue_number, match, related_matches):
            logging.info("Added a duplicate reference comment to issue #%d.", issue_number)
        else:
            logging.info("The duplicate reference comment already exists.")

        client.close_as_duplicate(issue_number, match.issue)
        logging.info(
            "Closed issue #%d as a duplicate of #%d.",
            issue_number,
            match.issue.number,
        )
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
