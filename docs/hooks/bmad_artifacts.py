"""MkDocs hook: publish BMAD's output folder under /bmad/ without copying it.

BMAD writes its artifacts (brief, PRD, architecture, epics, stories, test plans)
to ``_bmad-output/`` at the repository root, outside ``docs/``. Rather than
moving BMAD's output folder or committing a copy, this hook registers every
Markdown file found there as a generated page at ``bmad/<relative path>`` and
writes a ``bmad/index.md`` listing them, so the site always mirrors what is on
disk. Those pages are part of the ``--strict`` build: a broken link inside a BMAD
artifact fails CI, which is the intended documentation-currency check.

See docs/adr/0004-documentation-system.md.
"""

from __future__ import annotations

import logging
from pathlib import Path

from mkdocs.config.defaults import MkDocsConfig
from mkdocs.structure.files import File, Files, InclusionLevel

log = logging.getLogger("mkdocs.plugins.shatterfish.bmad")

PREFIX = "bmad"
SECTIONS = {
    "planning-artifacts": "Planning artifacts",
    "implementation-artifacts": "Implementation artifacts",
    "test-artifacts": "Test artifacts",
}


def _output_dir(config: MkDocsConfig) -> Path:
    return Path(config.config_file_path).parent / "_bmad-output"


def _title(path: Path) -> str:
    """First H1 in the file, else the file name."""
    try:
        with path.open(encoding="utf-8") as fh:
            for line in fh:
                if line.startswith("# "):
                    return line[2:].strip()
    except OSError:
        pass
    return path.stem


def on_files(files: Files, config: MkDocsConfig) -> Files:
    out = _output_dir(config)
    found: dict[str, list[tuple[str, str]]] = {key: [] for key in SECTIONS}
    other: list[tuple[str, str]] = []

    if out.is_dir():
        for path in sorted(out.rglob("*.md")):
            rel = path.relative_to(out).as_posix()
            src_uri = f"{PREFIX}/{rel}"
            # NOT_IN_NAV: built and link-checked, but exempt from the omitted-files warning;
            # the generated bmad/index.md is the nav entry that links to them.
            files.append(File.generated(config, src_uri, abs_src_path=str(path),
                                        inclusion=InclusionLevel.NOT_IN_NAV))
            entry = (rel, _title(path))
            top = rel.split("/", 1)[0]
            (found[top] if top in found else other).append(entry)

    lines = [
        "# BMAD artifacts",
        "",
        "Rendered straight from `_bmad-output/` at build time; edit the source files, "
        "never this page. Planning artifacts are the governing documents once the "
        "product owner has approved them (bootstrap prompt, section 0).",
        "",
    ]
    total = 0
    for key, heading in SECTIONS.items():
        lines += [f"## {heading}", ""]
        entries = found[key]
        if not entries:
            lines += ["_Nothing yet._", ""]
            continue
        for rel, title in entries:
            lines.append(f"- [{title}]({rel})")
            total += 1
        lines.append("")
    if other:
        lines += ["## Other", ""]
        lines += [f"- [{title}]({rel})" for rel, title in other]
        lines.append("")
        total += len(other)

    files.append(File.generated(config, f"{PREFIX}/index.md", content="\n".join(lines)))
    log.info("shatterfish.bmad: published %d artifact page(s) from %s", total, out)
    return files


def on_serve(server, config: MkDocsConfig, builder):
    out = _output_dir(config)
    if out.is_dir():
        server.watch(str(out))
    return server
