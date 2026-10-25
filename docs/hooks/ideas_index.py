"""MkDocs hook: fill the ideas list on docs/ideas/index.md from the folder.

Each idea is a file ``docs/ideas/<yyyy-mm-dd>-<slug>.md`` that starts with a ``# Title`` line.
Writing the list at build time means no committed list can drift, and no two pull requests that
add an idea edit a common line. ``IdeasIndexTest`` holds the same file-name and title rules.
"""

from __future__ import annotations

import re
from pathlib import Path

MARKER = "<!-- ideas-list -->"
NAME = re.compile(r"^(\d{4}-\d{2}-\d{2})-([a-z0-9]+(?:-[a-z0-9]+)*)\.md$")


def ideas_list(folder: Path) -> str:
    """One bullet per idea file, newest first, as a link with its date and title."""
    items = []
    for path in sorted(folder.glob("*.md"), reverse=True):
        match = NAME.match(path.name)
        if not match:
            continue
        first = path.read_text(encoding="utf-8").splitlines()[0]
        title = first[2:].strip() if first.startswith("# ") else path.stem
        items.append(f"- {match.group(1)}: [{title}]({path.name})")
    return "\n".join(items)


def on_page_markdown(markdown, page, config, files):
    if page.file.src_uri.replace("\\", "/") != "ideas/index.md":
        return markdown
    folder = Path(config["docs_dir"]) / "ideas"
    return markdown.replace(MARKER, ideas_list(folder))
