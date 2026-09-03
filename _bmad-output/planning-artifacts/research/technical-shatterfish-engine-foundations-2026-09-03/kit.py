"""Run-local helper: numbered source registry for research.md.

usage: uv run kit.py add "<url>" "<publisher>" "<pub_date>" "<confidence>" "<supports>"
       uv run kit.py table   -> markdown appendix rows
Sources are de-duplicated by URL; the number is stable once assigned.
"""
import json, sys
from pathlib import Path

HERE = Path(__file__).parent
DB = HERE / "sources.json"


def load():
    return json.loads(DB.read_text(encoding="utf-8")) if DB.exists() else []


def save(rows):
    DB.write_text(json.dumps(rows, indent=1, ensure_ascii=False), encoding="utf-8")


def add(url, publisher, pub_date, confidence, supports):
    rows = load()
    for r in rows:
        if r["url"] == url:
            if supports and supports not in r["supports"]:
                r["supports"] += "; " + supports
            save(rows)
            return r["n"]
    n = len(rows) + 1
    rows.append({"n": n, "url": url, "publisher": publisher, "pub_date": pub_date,
                 "accessed": "2026-09-03", "confidence": confidence, "supports": supports})
    save(rows)
    return n


def table():
    out = ["| n | Supports | Publisher | Published | Accessed | Confidence |", "|---|---|---|---|---|---|"]
    for r in load():
        out.append(f"| [{r['n']}] | {r['supports']} | [{r['publisher']}]({r['url']}) | {r['pub_date']} | {r['accessed']} | {r['confidence']} |")
    return "\n".join(out)


if __name__ == "__main__":
    cmd = sys.argv[1]
    if cmd == "add":
        print(add(*sys.argv[2:7]))
    elif cmd == "table":
        print(table())
