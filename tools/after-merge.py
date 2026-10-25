#!/usr/bin/env python3
"""The bookkeeping after a pull request is merged, in one command.

    python tools/after-merge.py <PR> [--key <sprint key>] [--status done] [--close] [--dry-run]
    python tools/after-merge.py --self-test

It reads the merged PR from GitHub (always `-R watchthelight/shatterfish`, never the
upstream remote) and then:

* for a story branch (`story/<key>`, or any PR given `--key`), sets
  `development_status.<key>` in sprint-status.yaml (default `done`, keeping any trailing
  comment) and `last_updated`;
* checks every issue the PR closes, and with `--close` closes any GitHub left open;
* checks that README.md mentions the PR or one of its issues, and says so if not
  (the line itself is prose, so it is written by hand);
* says whether the PR touched upstream code or the codex, in which case
  `./gradlew :codex:generate` must show no drift.

It changes local files only; commit them as usual. Nothing is pushed.
"""
import argparse
import datetime
import json
import re
import subprocess
import sys
from pathlib import Path

REPO = "watchthelight/shatterfish"
ROOT = Path(__file__).resolve().parent.parent
SPRINT = ROOT / "_bmad-output" / "implementation-artifacts" / "sprint-status.yaml"
README = ROOT / "README.md"
CODEX_PATHS = ("core/", "SPD-classes/", "codex/", "shatterfish/codex/")


def gh(*args):
    out = subprocess.run(["gh", *args, "-R", REPO], capture_output=True, text=True, encoding="utf-8")
    if out.returncode != 0:
        sys.exit(f"gh {' '.join(args)} failed: {out.stderr.strip()}")
    return out.stdout


def set_status(text, key, status, today):
    """Returns sprint-status text with key set to status and last_updated set to today."""
    pattern = re.compile(rf"^(  {re.escape(key)}:)[ \t]*[^#\r\n]*?([ \t]*#[^\r\n]*)?$", re.M)
    if not pattern.search(text):
        raise KeyError(f"{key} is not in development_status")
    text = pattern.sub(lambda m: f"{m.group(1)} {status}{m.group(2) or ''}", text, count=1)
    return re.sub(r"^last_updated:.*$", f"last_updated: {today}", text, count=1, flags=re.M)


def self_test():
    sample = ("last_updated: 2026-09-26\ndevelopment_status:\n"
              "  5-9-a: in-progress  # slice merged\n  5-10-b: backlog\n")
    got = set_status(sample, "5-9-a", "done", "2026-10-24")
    assert "last_updated: 2026-10-24" in got, got
    assert "  5-9-a: done  # slice merged\n" in got, got
    assert "  5-10-b: backlog\n" in got, got
    got = set_status(sample, "5-10-b", "ready-for-dev", "2026-10-24")
    assert "  5-10-b: ready-for-dev\n" in got, got
    try:
        set_status(sample, "5-9", "done", "2026-10-24")
        raise AssertionError("a prefix of a key must not match")
    except KeyError:
        pass
    print("self-test passed")


def main():
    parser = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    parser.add_argument("pr", nargs="?", type=int)
    parser.add_argument("--key", help="the sprint-status key, when the branch name is not story/<key>")
    parser.add_argument("--status", default="done")
    parser.add_argument("--close", action="store_true", help="close issues the merge left open")
    parser.add_argument("--dry-run", action="store_true")
    parser.add_argument("--self-test", action="store_true")
    args = parser.parse_args()
    sys.stdout.reconfigure(encoding="utf-8")
    if args.self_test:
        return self_test()
    if args.pr is None:
        parser.error("a PR number is required")

    pr = json.loads(gh("pr", "view", str(args.pr), "--json",
                       "number,title,state,headRefName,mergeCommit,closingIssuesReferences,files"))
    if pr["state"] != "MERGED":
        sys.exit(f"#{pr['number']} is {pr['state']}, not merged; nothing to do")
    sha = (pr.get("mergeCommit") or {}).get("oid", "")[:9]
    print(f"#{pr['number']} {pr['title']} (merged as {sha})")

    branch = pr["headRefName"]
    key = args.key or (branch[len("story/"):] if branch.startswith("story/") else None)
    if key:
        today = datetime.date.today().isoformat()
        text = SPRINT.read_text(encoding="utf-8")
        try:
            new = set_status(text, key, args.status, today)
        except KeyError as e:
            sys.exit(f"sprint-status: {e.args[0]}; pass --key <sprint key>")
        if new != text and not args.dry_run:
            SPRINT.write_bytes(new.encode("utf-8"))
        print(f"sprint-status: {key} -> {args.status}, last_updated {today}"
              + (" (dry run)" if args.dry_run else ""))
    else:
        print(f"sprint-status: not a story branch ({branch}) and no --key; unchanged")

    issues = [i["number"] for i in pr.get("closingIssuesReferences") or []]
    for number in issues:
        state = json.loads(gh("issue", "view", str(number), "--json", "state"))["state"]
        if state == "OPEN" and args.close and not args.dry_run:
            gh("issue", "close", str(number), "-c", f"Fixed by #{pr['number']} ({sha}).")
            state = "CLOSED (closed now)"
        print(f"issue #{number}: {state}")

    readme = README.read_text(encoding="utf-8")
    refs = [pr["number"], *issues]
    if any(re.search(rf"#{n}\b", readme) for n in refs):
        print("README: mentions it")
    else:
        print(f"README: no mention of {', '.join('#' + str(n) for n in refs)}; add a line under 'Where it stands'")

    paths = [f["path"] for f in pr.get("files") or []]
    touched = sorted({p for p in paths if p.startswith(CODEX_PATHS)})
    if touched:
        print(f"codex: {len(touched)} file(s) under upstream or codex paths; run ./gradlew :codex:generate and check git diff codex/")
    else:
        print("codex: untouched")


if __name__ == "__main__":
    main()
