#!/usr/bin/env python3
"""Split the eight grouped bestiary lore files into one file per claim (story 7.7, ADR-0021).

Committed and run once: it reads lore/bestiary/<group>.md, writes lore/bestiary/<group>-<n>.md for
every claim, writes the migration manifest the LoreMigrationTest holds the result to, and removes
the grouped files. Run it again after that and it finds no grouped file to read and stops.

Every field of a claim carries over verbatim. Three are re-spelled, and the test applies the same
mapping in reverse: the tier label "1 (TIER1)" becomes `tier: 1`, the source "<url>" becomes
`source_url: url` with a `source_type` read from the host, and a date of "not stated by the source"
becomes `date: unknown`. The verdict moves from a bullet to the body of the file.
"""
import hashlib
import json
import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
LORE = ROOT / "lore" / "bestiary"
MANIFEST = ROOT / "shatterfish" / "codex" / "src" / "test" / "resources" / "lore-migration.tsv"
GRADED = "v4.0.0"
TIER = {"1": "TIER1", "F": "CONTRADICTED", "3": "UNVERIFIED"}
FIELDS = ["Claim", "Tier", "Source", "Variant", "Version", "Date", "Retrieved", "Verdict", "Cites", "Card"]
HOSTS = {
    "pixeldungeon.fandom.com": "wiki",
    "tvtropes.org": "wiki",
    "steamcommunity.com": "forum",
    "docs.oldmartijntje.nl": "guide",
    "spd-huntress-guide.pages.dev": "guide",
    "www.youtube.com": "video",
}


def quote(text):
    return json.dumps(text, ensure_ascii=False)


def sha(text):
    return hashlib.sha256(text.encode("utf-8")).hexdigest()


def main():
    sources = sorted(p for p in LORE.glob("*.md") if not re.search(r"-\d+\.md$", p.name))
    if not sources:
        print("no grouped lore file under lore/bestiary/: nothing to split")
        return 0
    manifest = []
    counts = []
    for source in sources:
        group = source.stem
        text = source.read_text(encoding="utf-8")
        subject = ("", "")
        heading = ""
        parts = re.split(r"^(### Claim \S+\n|## .*\n)", text, flags=re.M)
        n = 0
        for i in range(1, len(parts), 2):
            marker, body = parts[i], parts[i + 1]
            if marker.startswith("## "):
                heading = marker.strip()
                m = re.match(r"^## (.*) \(`([^`]+)`\)$", heading)
                subject = (m.group(1), m.group(2)) if m else (heading[3:], "")
                continue
            cid = marker.split()[2]
            n += 1
            fields = dict(re.findall(r"^- \*\*([^:]+):\*\* (.*)$", body, flags=re.M))
            verdict_key = next(k for k in fields if k.startswith("Verdict against"))
            original = {
                "Claim": fields["Claim"],
                "Tier": fields["Tier"],
                "Source": fields["Source"],
                "Variant": fields["Variant"],
                "Version": fields["Version"],
                "Date": fields["Date"],
                "Retrieved": fields["Retrieved"],
                "Verdict": fields[verdict_key],
                "Cites": fields.get("Cites", ""),
                "Card": fields["Bestiary card"],
            }
            tier = original["Tier"].split()[0]
            assert TIER[tier] in original["Tier"], cid
            url = original["Source"].strip("<>")
            host = re.sub(r"https?://([^/]+)/.*", r"\1", url)
            cites = re.findall(r"`([^`]+)`", original["Cites"])
            assert original["Cites"] == ", ".join("`%s`" % c for c in cites), cid
            card = original["Card"].strip("`")
            date = "unknown" if original["Date"] == "not stated by the source" else original["Date"]
            lines = [
                "---",
                "id: " + cid,
                "topic: bestiary",
                "group: " + group,
                "subject: " + quote(subject[0]),
                "subject_class: " + subject[1],
                "source_url: " + url,
                "source_type: " + HOSTS[host],
                "date: " + date,
                "retrieved: " + original["Retrieved"],
                "variant: " + original["Variant"],
                "version_claimed: " + quote(original["Version"]),
                "tier: " + tier,
                "graded_against: " + GRADED,
                "status: current",
                "claim: " + quote(original["Claim"]),
            ]
            lines.append("cites:" if cites else "cites: []")
            lines += ["  - " + c for c in cites]
            lines += ["implemented_in: []", "card: " + card, "---", "",
                      "## Verdict against `%s`" % GRADED, "", original["Verdict"], ""]
            (LORE / (cid + ".md")).write_text("\n".join(lines), encoding="utf-8", newline="\n")
            manifest.append("\t".join([cid, group, heading] + [sha(original[f]) for f in FIELDS]))
        counts.append((group, n))
        source.unlink()
    MANIFEST.parent.mkdir(parents=True, exist_ok=True)
    MANIFEST.write_text("\n".join(manifest) + "\n", encoding="utf-8", newline="\n")
    print("split %d claims; per group: %s" % (len(manifest), ", ".join("%s %d" % c for c in counts)))
    return 0


if __name__ == "__main__":
    sys.exit(main())
