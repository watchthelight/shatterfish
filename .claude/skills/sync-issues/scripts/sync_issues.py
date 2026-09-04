"""Mirror epics and stories to GitHub milestones and issues. Idempotent via marker comments."""
import json, re, subprocess, sys, argparse

REPO = "watchthelight/shatterfish"
EP = "_bmad-output/planning-artifacts/epics.md"
STATUS = "_bmad-output/implementation-artifacts/sprint-status.yaml"
BLOB = "https://github.com/watchthelight/shatterfish/blob/main/_bmad-output/planning-artifacts/epics.md"
STORY_EPICS = {1, 2}  # sync-issues scope: the current epic and the next one


def gh(*args, data=None):
    cmd = ["gh", *args]
    r = subprocess.run(cmd, input=json.dumps(data) if data is not None else None,
                       capture_output=True, text=True, encoding="utf-8")
    if r.returncode:
        raise SystemExit(f"gh failed: {' '.join(args)}\n{r.stderr}")
    return r.stdout


def anchor(n, m, title):
    slug = re.sub(r"[^a-z0-9 -]", "", f"story {n}{m} {title}".lower()).replace(" ", "-")
    return f"{BLOB}#{slug}"


text = open(EP, encoding="utf-8").read()

# ---- epic list table: number -> (title, goal, done-when) ----
donewhen, epictitle = {}, {}
for row in re.findall(r"^\| E(\d) \| ([^|]+) \| ([^|]+) \| ([^|]+) \|$", text, re.M):
    n, title, goal, dw = int(row[0]), row[1].strip(), row[2].strip(), row[3].strip()
    epictitle[n], donewhen[n] = title, (goal, dw)

# ---- epic sections and their stories ----
sections = re.split(r"(?m)^## Epic (\d+): (.+)$", text)
epics = {}
for i in range(1, len(sections), 3):
    n, title, body = int(sections[i]), sections[i + 1].strip(), sections[i + 2]
    parts = re.split(r"(?m)^### Story (\d+)\.(\d+): (.+)$", body)
    preamble = parts[0].strip()
    stories = []
    for j in range(1, len(parts), 4):
        stories.append({"n": int(parts[j]), "m": int(parts[j + 1]),
                        "title": parts[j + 2].strip(), "body": parts[j + 3].strip()})
    epics[n] = {"title": title, "preamble": preamble, "stories": stories}

status = open(STATUS, encoding="utf-8").read()


def story_status(key_prefix):
    m = re.search(rf"^  ({re.escape(key_prefix)}[^:]*): (\S+)$", status, re.M)
    return m.group(2) if m else "backlog"


def slugkey(n, m, title):
    slug = re.sub(r"[^\w]+", "-", title.lower()).strip("-")
    return f"{n}-{m}-{slug}"[:63]


AREA = {"harness": "area:harness", "codex": "area:codex", "brain": "area:brain",
        "rig": "area:rig", "overlay": "area:overlay", "lore": "area:lore"}


def labels_for(st, n):
    out = {"type:story", f"epic:E{n}"}
    b = st["body"].lower() + " " + st["title"].lower()
    for word, lab in AREA.items():
        if re.search(rf"\b{word}\b", b):
            out.add(lab)
    if re.search(r"docs/|documentation|results page|methodology page", b):
        out.add("area:docs")
    if re.search(r"\bci\b|nightly|workflow", b):
        out.add("area:ci")
    if re.search(r"observer|actionexecutor|\bbrain\b|leak|differential|oracle|parity|fairness", b):
        out.add("fairness")
    if re.search(r"hook|upstream.md|`core`|registry class", b):
        out.add("touches-upstream")
    return sorted(out)


def story_body(st, n, key):
    lines = st["body"].split("\n")
    cut = next((i for i, l in enumerate(lines) if l.startswith("**Acceptance Criteria")), len(lines))
    narrative = "\n".join(lines[:cut]).strip()
    criteria = "\n".join(lines[cut:]).strip()
    cur = story_status("{}-{}-".format(st["n"], st["m"]))
    return (f"<!-- shatterfish:story {key} -->\n"
            f"**Epic {n}: {epics[n]['title']}** · story {st['n']}.{st['m']} · status: `{cur}`\n\n"
            f"{narrative}\n\n{criteria}\n\n"
            f"---\nStory file: not yet created; `/next-story` writes it when this story starts.\n"
            f"Source: [epics.md, story {st['n']}.{st['m']}]({anchor(st['n'], st['m'], st['title'])})\n")


# ---- current GitHub state ----
issues = json.loads(gh("issue", "list", "-R", REPO, "--state", "all", "--limit", "500",
                       "--json", "number,title,body,state"))
by_marker = {}
for it in issues:
    for mk in re.findall(r"<!-- shatterfish:(epic \d+|story [\w-]+) -->", it.get("body") or ""):
        by_marker[mk] = it

plan = []
for n in sorted(epics):
    if n in STORY_EPICS:
        for st in epics[n]["stories"]:
            key = slugkey(st["n"], st["m"], st["title"])
            mk = f"story {key}"
            plan.append(("update" if mk in by_marker else "create", "story", n, st, key, mk))
    mk = f"epic {n}"
    plan.append(("update" if mk in by_marker else "create", "epic", n, None, None, mk))

if "--apply" not in sys.argv:
    for act, kind, n, st, key, mk in plan:
        name = f"E{n}.{st['m']} {st['title']}" if kind == "story" else f"E{n} {epics[n]['title']}"
        print(f"{act:6} {kind:5} {name[:88]}")
    print(f"\n{sum(1 for p in plan if p[0] == 'create')} to create, {sum(1 for p in plan if p[0] == 'update')} to update")
    raise SystemExit(0)

# ---- apply ----
created = {}
for act, kind, n, st, key, mk in plan:
    if kind != "story":
        continue
    title = f"E{n}.{st['m']} {st['title']}"
    body = story_body(st, n, key)
    labels = labels_for(st, n)
    if act == "create":
        out = gh("api", "-X", "POST", f"repos/{REPO}/issues", "--input", "-",
                 data={"title": title, "body": body, "labels": labels, "milestone": n + 1})
        num = json.loads(out)["number"]
        print(f"created #{num} {title[:70]}")
    else:
        num = by_marker[mk]["number"]
        gh("api", "-X", "PATCH", f"repos/{REPO}/issues/{num}", "--input", "-",
           data={"title": title, "body": body, "labels": labels, "milestone": n + 1})
        print(f"updated #{num} {title[:70]}")
    created[(st["n"], st["m"])] = num

for act, kind, n, st, key, mk in plan:
    if kind != "epic":
        continue
    goal, dw = donewhen.get(n, ("", ""))
    tasks = ""
    if n in STORY_EPICS:
        tasks = "\n".join(f"- [ ] #{created[(s['n'], s['m'])]}" for s in epics[n]["stories"])
    else:
        tasks = ("_Story issues are created when this epic becomes current or next "
                 "(one epic of lookahead), per the sync-issues scope rule._")
    body = (f"<!-- shatterfish:epic {n} -->\n"
            f"{goal}\n\n**Done when:** {dw}\n\n"
            f"**Stories** ({len(epics[n]['stories'])} in the epics file)\n\n{tasks}\n\n"
            f"---\nSource: [epics.md, Epic {n}]({BLOB}#epic-{n}-"
            + re.sub(r'[^a-z0-9 -]', '', epics[n]['title'].lower()).replace(' ', '-') + ")\n")
    title = f"E{n} {epics[n]['title']}"
    labels = ["type:epic", f"epic:E{n}"]
    if act == "create":
        out = gh("api", "-X", "POST", f"repos/{REPO}/issues", "--input", "-",
                 data={"title": title, "body": body, "labels": labels, "milestone": n + 1})
        print(f"created #{json.loads(out)['number']} {title}")
    else:
        num = by_marker[mk]["number"]
        gh("api", "-X", "PATCH", f"repos/{REPO}/issues/{num}", "--input", "-",
           data={"title": title, "body": body, "labels": labels, "milestone": n + 1})
        print(f"updated #{num} {title}")

# ---- milestone descriptions follow the epics file, which supersedes the bootstrap ----
for n in sorted(epics):
    goal, dw = donewhen.get(n, ("", ""))
    if not dw:
        continue
    gh("api", "-X", "PATCH", f"repos/{REPO}/milestones/{n + 1}", "--input", "-",
       data={"description": f"{goal} Done when: {dw}"})
print("milestone descriptions updated")
