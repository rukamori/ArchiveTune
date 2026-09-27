import json
import re
import subprocess
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
RESOURCE_ROOT = ROOT / "app/src/main/res"
OUTPUT = RESOURCE_ROOT / "raw/translation_contributors.json"
IGNORED_AUTHORS = {"weblate:commit", "codeberg translate", "anonymous", "anynymous"}
MAX_CONTRIBUTORS = 6


def git(*args: str) -> str:
    return subprocess.run(
        ["git", *args], cwd=ROOT, check=True, capture_output=True,
        text=True, encoding="utf-8", timeout=300,
    ).stdout


def main() -> None:
    if git("rev-parse", "--is-shallow-repository").strip() != "false":
        raise RuntimeError("Translation credits require a complete Git history (fetch-depth: 0)")

    languages = {
        path.name.removeprefix("values-")
        for path in RESOURCE_ROOT.glob("values-*")
        if path.is_dir()
    }
    contributors: dict[str, list[str]] = {}
    history = git(
        "log", "HEAD", "--no-merges", "--format=%x1e%an%x00%s",
        "--name-only", "--regexp-ignore-case", "--grep=^Translated using Weblate",
        "--", "app/src/main/res",
    )
    for record in history.split("\x1e"):
        if not record.strip():
            continue
        header, *paths = record.splitlines()
        author, subject = header.split("\x00", 1)
        author = author.strip()
        if not author or author.casefold() in IGNORED_AUTHORS:
            continue
        if not subject.casefold().startswith("translated using weblate"):
            continue
        for path in paths:
            match = re.fullmatch(r"app/src/main/res/values-([^/]+)/[^/]+\.xml", path)
            if match is None or match[1] not in languages:
                continue
            names = contributors.setdefault(match[1], [])
            if author not in names and len(names) < MAX_CONTRIBUTORS:
                names.append(author)

    if not contributors:
        raise RuntimeError("No translation credits found in Git history")
    data = [
        {"qualifier": language, "contributors": contributors[language]}
        for language in sorted(contributors)
    ]
    OUTPUT.write_text(json.dumps(data, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print(f"Generated translation credits for {len(data)} languages")


if __name__ == "__main__":
    main()
