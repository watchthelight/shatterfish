#!/usr/bin/env python3
"""Pool the per-class tables of several :rig:direction folders into one table.

    python tools/pool-direction.py <folder> [<folder> ...]

Each folder holds a direction.md. Rows of the form `n/m | n/m` (and the Runs row) are summed
over the folders for each class; the pooled block is recomputed from the classes, not copied.
Rows that are means or medians are not pooled (they do not add up) and are left out.
"""
import re
import sys
from pathlib import Path

ORDER = ["WARRIOR", "MAGE", "ROGUE", "HUNTRESS", "DUELIST", "CLERIC"]
FRACTION = re.compile(r"\| ([^|]+?) \| (\d+)/(\d+) \| (\d+)/(\d+) \|")
RUNS = re.compile(r"\| Runs \| (\d+) \| (\d+) \|")


def pool(folders):
    """Returns ({(class, row): [base_n, base_m, cur_n, cur_m]}, row keys in first-seen order)."""
    rows = {}
    order = []
    for folder in folders:
        cls = None
        for line in (Path(folder) / "direction.md").read_text(encoding="utf-8").splitlines():
            m = re.match(r"#### (\w+)", line)
            if m:
                cls = m.group(1)
                continue
            if cls is None or cls == "pooled":
                continue
            m = RUNS.match(line)
            if m:
                key, vals = (cls, "Runs"), [int(m.group(1)), 0, int(m.group(2)), 0]
            else:
                m = FRACTION.match(line)
                if not m:
                    continue
                key, vals = (cls, m.group(1)), [int(m.group(i)) for i in (2, 3, 4, 5)]
            if key not in rows:
                rows[key] = [0, 0, 0, 0]
                order.append(key)
            rows[key] = [a + b for a, b in zip(rows[key], vals)]
    return rows, order


def render(rows, order):
    names = []
    for _, name in order:
        if name not in names:
            names.append(name)
    classes = [c for c in ORDER if any(k[0] == c for k in rows)]
    classes += sorted({k[0] for k in rows} - set(classes))
    out = []
    for cls in classes + ["pooled"]:
        out += [f"#### {cls}", "", "| | base | current | delta |", "|---|---|---|---|"]
        for name in names:
            if cls == "pooled":
                parts = [rows[(c, name)] for c in classes if (c, name) in rows]
                v = [sum(p[i] for p in parts) for i in range(4)] if parts else None
            else:
                v = rows.get((cls, name))
            if v is None:
                continue
            if name == "Runs":
                out.append(f"| Runs | {v[0]} | {v[2]} | {v[2] - v[0]:+d} |")
            else:
                out.append(f"| {name} | {v[0]}/{v[1]} | {v[2]}/{v[3]} | {v[2] - v[0]:+d} |")
        out.append("")
    return "\n".join(out)


if __name__ == "__main__":
    if len(sys.argv) < 2:
        sys.exit(__doc__)
    sys.stdout.reconfigure(encoding="utf-8")
    print(render(*pool(sys.argv[1:])))
