#!/usr/bin/env python3
"""Extract the committed summary of the Prison diagnosis (story 10.7, issue #353) from direction folders.

    python tools/prison-diagnosis.py <out-dir> <direction-folder> [<direction-folder> ...]

Each folder is the output of
    ./gradlew :rig:direction --args="--set standard --class ALL --first 80 --salt-base <1000|3000> --debug-record --jobs 3 --out <folder>"
and holds `current/` with one Run log (`<run>.jsonl`), its `.ending.json`, `.arrivals.tsv` and `.debug.ndjson` files.

The 960 full logs are not committed (about 1.8 MB of debug record a Run); this script reads them and writes three
small tab-separated files beside the page, which `PrisonDiagnosisPageTest` reads:

  runs.tsv      one row per Run: class, salt, seed code, ending, depth, killer, hero state at the end, reach
  arrivals.tsv  one row per Run and depth: the hero's level and strength on the first wait of the depth
  moments.tsv   the 20 most expensive decisions per class: HP lost between a decision and the next wait

HP lost per decision: the debug record writes a wait when an enemy is in view or the log gained a line. The loss of
the decision at wait k is hp(k) minus hp(k+1) when wait k+1 is in the record too (positive losses only); the last
wait of a Run that died loses the hero's remaining hp (the Run ends on that decision). The policy is the Run log's
own `decision.policy` at wait k.
"""
import glob
import json
import os
import sys

TOP = 20


def read_ndjson(path):
    out = []
    with open(path, encoding="utf-8") as f:
        for line in f:
            line = line.strip()
            if line:
                out.append(json.loads(line))
    return out


def clean(text):
    return str(text).replace("\t", " ").replace("\n", " ").replace("\r", " ")


def one_run(stem):
    log = read_ndjson(stem + ".jsonl")
    header = log[0]
    waits = {r["k"]: r for r in log if r.get("t") == "wait"}
    end = next((r for r in log if r.get("t") == "end"), None)
    ending = {}
    if os.path.exists(stem + ".ending.json"):
        with open(stem + ".ending.json", encoding="utf-8") as f:
            ending = json.load(f)
    arrivals = []
    arrivals_file = os.path.join(os.path.dirname(stem), "%s-%s-%s.arrivals.tsv" % (header["class"], header["seedcode"], header["salt"]))
    if os.path.exists(arrivals_file):
        with open(arrivals_file, encoding="utf-8") as f:
            next(f)
            for line in f:
                p = line.rstrip("\n").split("\t")
                arrivals.append((int(p[0]), int(p[2]), int(p[6])))
    dbg = read_ndjson(stem + ".debug.ndjson") if os.path.exists(stem + ".debug.ndjson") else []
    dwaits = [r for r in dbg if r.get("t") == "wait"]
    dend = next((r for r in dbg if r.get("t") == "end"), {})
    by_k = {r["k"]: r for r in dwaits}
    died = end is not None and end["outcome"]["cause"] == "DEATH"
    moments = []
    for r in dwaits:
        nxt = by_k.get(r["k"] + 1)
        if nxt is not None:
            loss = r["hp"] - nxt["hp"]
        elif died and r["k"] == end["k"]:
            loss = r["hp"]
        else:
            continue
        if loss <= 0:
            continue
        decision = waits.get(r["k"], {}).get("decision") or {}
        enemies = ";".join(sorted({e["name"] for e in r.get("enemies", [])}))
        moments.append((loss, r["k"], r["depth"], r["hp"], r["ht"], decision.get("policy", "-"),
                        decision.get("chosen", {}).get("why", r.get("why", "-")), enemies))
    last_why = ""
    if dwaits and died:
        last = dwaits[-1]
        last_why = str(last.get("why", ""))
    row = {
        "class": header["class"],
        "salt": int(header["salt"], 16),
        "seedcode": header["seedcode"],
        "cause": end["outcome"]["cause"] if end else "NOEND",
        "depth": end["outcome"]["depth"] if end else -1,
        "turns": end["outcome"]["turns"] // 1000 if end else -1,
        "killer": ending.get("killer", "-") if died else "-",
        "last_hp": ending.get("last_hp", dend.get("last_hp", -1)) if died else -1,
        "ht": ending.get("ht", dend.get("ht", -1)) if died else -1,
        "hunger_end": dend.get("hunger", "-") if died else "-",
        "cornered_last": 1 if last_why.startswith("cornered") else 0,
        "goo_kill": 1 if "Goo" in ending.get("boss_kills", []) else 0,
        "reach5": 1 if any(d >= 5 for d, _, _ in arrivals) else 0,
        "reach10": 1 if any(d >= 10 for d, _, _ in arrivals) else 0,
    }
    return row, arrivals, moments


def main(argv):
    if len(argv) < 3:
        print(__doc__)
        return 2
    out = argv[1]
    os.makedirs(out, exist_ok=True)
    runs, arrs, moms = [], [], {}
    for folder in argv[2:]:
        stems = sorted(p[:-len(".jsonl")] for p in glob.glob(os.path.join(folder, "current", "*.jsonl")))
        if not stems:
            print("no Run logs under " + folder, file=sys.stderr)
            return 2
        for stem in stems:
            row, arrivals, moments = one_run(stem)
            runs.append(row)
            for d, lvl, st in arrivals:
                arrs.append((row["class"], row["salt"], d, lvl, st))
            for m in moments:
                moms.setdefault(row["class"], []).append((m, row["salt"], row["seedcode"]))
    runs.sort(key=lambda r: (r["class"], r["salt"]))
    cols = ["class", "salt", "seedcode", "cause", "depth", "turns", "killer", "last_hp", "ht", "hunger_end",
            "cornered_last", "goo_kill", "reach5", "reach10"]
    with open(os.path.join(out, "runs.tsv"), "w", encoding="utf-8", newline="\n") as f:
        f.write("\t".join(cols) + "\n")
        for r in runs:
            f.write("\t".join(clean(r[c]) for c in cols) + "\n")
    arrs.sort()
    with open(os.path.join(out, "arrivals.tsv"), "w", encoding="utf-8", newline="\n") as f:
        f.write("class\tsalt\tdepth\tlevel\tstrength\n")
        for a in arrs:
            f.write("\t".join(str(x) for x in a) + "\n")
    with open(os.path.join(out, "moments.tsv"), "w", encoding="utf-8", newline="\n") as f:
        f.write("class\trank\tsalt\tseedcode\tk\tdepth\thp\tht\tloss\tpolicy\twhy\tenemies\n")
        for cls in sorted(moms):
            best = sorted(moms[cls], key=lambda x: (-x[0][0], x[1], x[0][1]))[:TOP]
            for rank, (m, salt, code) in enumerate(best, 1):
                loss, k, depth, hp, ht, policy, why, enemies = m
                f.write("\t".join([cls, str(rank), str(salt), code, str(k), str(depth), str(hp), str(ht), str(loss),
                                   clean(policy), clean(why), clean(enemies)]) + "\n")
    print("runs %d, arrivals %d" % (len(runs), len(arrs)))
    return 0




# ---------------------------------------------------------------------------------------------------------------
# `tables <dir>`: the markdown tables of the page, from the three files. PrisonDiagnosisPageTest recomputes every
# one of them in Java from the same files and compares them with the page, so the page cannot drift from the files.
# ---------------------------------------------------------------------------------------------------------------
from decimal import Decimal, ROUND_HALF_UP, getcontext

CLASSES = ["WARRIOR", "MAGE", "ROGUE", "HUNTRESS", "DUELIST", "CLERIC"]

#: (story, label, killers the family counts). The order here is only the tie-break; the page orders by the model.
FAMILIES = [
    ("10.8", "Skeleton bone burst", ["Skeleton"]),
    ("10.9", "DM-100 lightning", ["DM100"]),
    ("10.10", "Guard chain pull", ["Guard"]),
    ("10.11", "Thief and Bandit steal", ["Thief", "Bandit"]),
    ("10.12", "Necromancer priority", ["Necromancer", "Necromancer$NecroSkeleton"]),
    ("10.13", "Swarm split", ["Swarm"]),
    ("10.15", "Prison food budgeting", ["Hunger"]),
]
GOO = ("(none open)", "Goo on depth 5", ["Goo"])


def load(d):
    def rows(name):
        with open(os.path.join(d, name), encoding="utf-8") as f:
            head = f.readline().rstrip("\n").split("\t")
            return [dict(zip(head, line.rstrip("\n").split("\t"))) for line in f]
    return rows("runs.tsv"), rows("arrivals.tsv"), rows("moments.tsv")


def pct(n, d):
    if d == 0:
        return "-"
    t = (n * 1000 + d // 2) // d
    return "%d.%d%%" % (t // 10, t % 10)


def mean1(total, n):
    if n == 0:
        return "-"
    t = (total * 20 + n) // (2 * n)
    return "%d.%d" % (t // 10, t % 10)


def cols(runs):
    return [(c, [r for r in runs if r["class"] == c]) for c in CLASSES] + [("pooled", runs)]


def table(header, rows):
    out = ["| " + " | ".join(header) + " |", "|" + "---|" * len(header)]
    out += ["| " + " | ".join(r) + " |" for r in rows]
    return "\n".join(out)


def deaths_in(rs, lo, hi):
    return [r for r in rs if r["cause"] == "DEATH" and lo <= int(r["depth"]) <= hi]


def reach_table(runs):
    cs = cols(runs)

    def row(label, f):
        return [label] + [f(rs) for _, rs in cs]
    rows = [
        row("Runs", lambda rs: str(len(rs))),
        row("reach depth 5", lambda rs: "%d/%d" % (sum(int(r["reach5"]) for r in rs), len(rs))),
        row("reach depth 10", lambda rs: "%d/%d" % (sum(int(r["reach10"]) for r in rs), len(rs))),
        row("Goo kills", lambda rs: "%d/%d" % (sum(int(r["goo_kill"]) for r in rs), len(rs))),
        row("deaths on depth 5", lambda rs: str(len(deaths_in(rs, 5, 5)))),
        row("of them to Goo", lambda rs: str(len([r for r in deaths_in(rs, 5, 5) if r["killer"] == "Goo"]))),
        row("deaths on depths 1 to 4", lambda rs: str(len(deaths_in(rs, 1, 4)))),
        row("deaths on depths 6 to 9", lambda rs: str(len(deaths_in(rs, 6, 9)))),
        row("deaths on depth 10 or deeper", lambda rs: str(len(deaths_in(rs, 10, 99)))),
    ]
    return table([""] + [c for c, _ in cs], rows)


def hunger_table(runs):
    cs = cols(runs)

    def deaths(rs):
        return deaths_in(rs, 1, 99)

    def deep(rs):
        return deaths_in(rs, 6, 9)

    def share(f, base):
        def cell(rs):
            n = len([r for r in base(rs) if f(r)])
            return "%d (%s)" % (n, pct(n, len(base(rs))))
        return cell
    starving = lambda r: r["hunger_end"] == "STARVING"
    hungry = lambda r: r["hunger_end"] == "HUNGRY"
    killer = lambda r: r["killer"] == "Hunger"
    rows = [["deaths"] + [str(len(deaths(rs))) for _, rs in cs]]
    rows.append(["killer is Hunger"] + [share(killer, deaths)(rs) for _, rs in cs])
    rows.append(["STARVING on the last Decision"] + [share(starving, deaths)(rs) for _, rs in cs])
    rows.append(["HUNGRY on the last Decision"] + [share(hungry, deaths)(rs) for _, rs in cs])
    rows.append(["deaths on depths 6 to 9"] + [str(len(deep(rs))) for _, rs in cs])
    rows.append(["of them killer Hunger"] + [share(killer, deep)(rs) for _, rs in cs])
    rows.append(["of them STARVING on the last Decision"] + [share(starving, deep)(rs) for _, rs in cs])
    rows.append(["of them HUNGRY on the last Decision"] + [share(hungry, deep)(rs) for _, rs in cs])
    return table([""] + [c for c, _ in cs], rows)


def killer_tables(runs):
    out = []
    for c, rs in cols(runs):
        rows = []
        for depth in range(1, 40):
            ds = deaths_in(rs, depth, depth)
            if not ds:
                continue
            counts = {}
            for r in ds:
                counts[r["killer"]] = counts.get(r["killer"], 0) + 1
            order = sorted(counts.items(), key=lambda kv: (-kv[1], kv[0]))
            rows.append([str(depth), str(len(ds)), ", ".join("%s %d" % kv for kv in order)])
        out.append("#### %s\n\n%s" % (c, table(["depth", "deaths", "killers"], rows)))
    return "\n\n".join(out)


def arrivals_table(arrivals):
    rows = []
    for c in CLASSES + ["pooled"]:
        cells = []
        for depth in range(1, 10):
            a = [x for x in arrivals if (c == "pooled" or x["class"] == c) and int(x["depth"]) == depth]
            n = len(a)
            cells.append("-" if n == 0 else "%s / %s (%d)" % (mean1(sum(int(x["level"]) for x in a), n),
                                                              mean1(sum(int(x["strength"]) for x in a), n), n))
        rows.append([c] + cells)
    return table(["class"] + ["d%d" % i for i in range(1, 10)], rows)


def moments_tables(moments):
    out = []
    for c in CLASSES:
        rows = []
        for m in [x for x in moments if x["class"] == c]:
            rows.append([m["rank"], "%s/%s" % (m["seedcode"], m["salt"]), m["depth"], "%s/%s" % (m["hp"], m["ht"]),
                         m["loss"], m["policy"], m["why"].replace("|", "/"), m["enemies"] or "-"])
        out.append("#### %s\n\n%s" % (c, table(["rank", "seed/salt", "depth", "hp", "lost", "policy", "why", "enemies in view"], rows)))
    return "\n\n".join(out)


def gain(runs, arrivals, killers):
    """Expected Runs more reaching depth 8 if `killers` never killed on depths 5 to 7 (one class), as a Decimal.
    A(d) = Runs with an arrival on depth d; D(d) = deaths on d; F(d) = those to `killers`. The Runs saved on d meet
    the other deaths of d at the rate everyone did: A'(d+1) = A'(d) * (A(d) - D(d) + F(d)) / A(d), against
    A0(d+1) = A0(d) * (A(d) - D(d)) / A(d) with nothing removed. The gain is A'(8) - A0(8). Depth 10 has no figure: no Run arrives on depth 9."""
    getcontext().prec = 40
    A = {d: len({x["salt"] for x in arrivals if int(x["depth"]) == d}) for d in range(5, 9)}
    a1 = Decimal(A[5])
    a0 = Decimal(A[5])
    for d in range(5, 8):
        D = len(deaths_in(runs, d, d))
        F = len([r for r in deaths_in(runs, d, d) if r["killer"] in killers])
        if A[d] == 0:
            return Decimal(0)
        a1 = a1 * Decimal(A[d] - D + F) / Decimal(A[d])
        a0 = a0 * Decimal(A[d] - D) / Decimal(A[d])
    return a1 - a0


def fmt_gain(x):
    t = int((x * 10).quantize(Decimal(1), rounding=ROUND_HALF_UP))
    return "+%d.%d" % (t // 10, t % 10)


def story_rows(runs, arrivals):
    out = []
    for story, label, killers in FAMILIES + [GOO]:
        per, deaths = [], []
        for c in CLASSES:
            rs = [r for r in runs if r["class"] == c]
            ar = [a for a in arrivals if a["class"] == c]
            per.append(gain(rs, ar, killers))
            deaths.append(len([r for r in deaths_in(rs, 5, 9) if r["killer"] in killers]))
        out.append((story, label, killers, per, deaths))
    return out


def stories_table(runs, arrivals):
    rows = story_rows(runs, arrivals)
    rows.sort(key=lambda r: -sum(r[3]))  # stable: ties keep the FAMILIES order
    body = []
    for story, label, killers, per, deaths in rows:
        body.append([story, label, ", ".join(killers)] + [str(n) for n in deaths] + [str(sum(deaths))]
                    + [fmt_gain(g) for g in per] + [fmt_gain(sum(per))])
    head = ["story", "family", "killers counted"] + ["deaths " + c for c in CLASSES] + ["deaths pooled"] \
        + ["+d8 " + c for c in CLASSES] + ["+d8 pooled"]
    return table(head, body)


def other_killers(runs):
    named = {k for _, _, ks in FAMILIES + [GOO] for k in ks}
    counts = {}
    for r in deaths_in(runs, 6, 9):
        if r["killer"] not in named:
            counts[r["killer"]] = counts.get(r["killer"], 0) + 1
    return sorted(counts.items(), key=lambda kv: (-kv[1], kv[0]))


def story_of(killer):
    for story, _, killers in FAMILIES:
        if killer in killers:
            return story
    return "-"


def killer_share(runs):
    """Every killer of the 960 Runs: deaths on depths 1 to 4, 5, 6 to 9, the share of all deaths, the story that owns it."""
    names = sorted({r["killer"] for r in deaths_in(runs, 1, 99)})
    total = len(deaths_in(runs, 1, 99))
    rows = []
    for k in names:
        mine = [r for r in deaths_in(runs, 1, 99) if r["killer"] == k]
        n = len(mine)
        rows.append((n, k, [str(len([r for r in mine if lo <= int(r["depth"]) <= hi])) for lo, hi in ((1, 4), (5, 5), (6, 9))],
                     pct(n, total)))
    rows.sort(key=lambda x: (-x[0], x[1]))
    return table(["killer", "depths 1 to 4", "depth 5", "depths 6 to 9", "deaths", "share of all deaths", "story"],
                 [[k] + d + [str(n), p, story_of(k)] for n, k, d, p in rows])


def cornered_table(runs):
    cs = cols(runs)
    rows = []
    for label, lo, hi in (("deaths on depths 6 to 9", 6, 9), ("deaths on depths 1 to 9", 1, 9)):
        rows.append([label + ", last Decision says cornered"] + [
            "%d of %d (%s)" % (len([r for r in deaths_in(rs, lo, hi) if r["cornered_last"] == "1"]), len(deaths_in(rs, lo, hi)),
                               pct(len([r for r in deaths_in(rs, lo, hi) if r["cornered_last"] == "1"]), len(deaths_in(rs, lo, hi))))
            for _, rs in cs])
    return table([""] + [c for c, _ in cs], rows)


def goo_table(runs):
    cs = cols(runs)
    goo = lambda rs: [r for r in deaths_in(rs, 1, 99) if r["killer"] == "Goo"]
    rows = [["deaths to Goo"] + [str(len(goo(rs))) for _, rs in cs]]
    for state in ("NONE", "HUNGRY", "STARVING"):
        rows.append(["of them hunger " + state + " on the last Decision"] + [
            "%d (%s)" % (len([r for r in goo(rs) if r["hunger_end"] == state]),
                         pct(len([r for r in goo(rs) if r["hunger_end"] == state]), len(goo(rs)))) for _, rs in cs])
    return table([""] + [c for c, _ in cs], rows)


def tables_main(d):
    runs, arrivals, moments = load(d)
    print("## reach\n\n" + reach_table(runs))
    print("\n## hunger\n\n" + hunger_table(runs))
    print("\n## killers\n\n" + killer_tables(runs))
    print("\n## arrivals\n\n" + arrivals_table(arrivals))
    print("\n## moments\n\n" + moments_tables(moments))
    print("\n## stories\n\n" + stories_table(runs, arrivals))
    print("\n## goo\n\n" + goo_table(runs))
    print("\n## killer share\n\n" + killer_share(runs))
    print("\n## other killers on depths 6 to 9\n\n" + ", ".join("%s %d" % kv for kv in other_killers(runs)))
    print("\n## cornered\n\n" + cornered_table(runs))


if __name__ == "__main__":
    if len(sys.argv) == 3 and sys.argv[1] == "tables":
        tables_main(sys.argv[2])
        sys.exit(0)
    sys.exit(main(sys.argv))
