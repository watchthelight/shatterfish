# usage: merge-queue.sh <PR>[:<sprint key>] ...   merge each PR into main, one at a time
#        merge-queue.sh --one <PR> <sprint key or ->   merge one PR
# Each merge waits for the PR's checks, takes the merge lock, merges --no-ff in the
# .claude/worktrees/merge worktree (detached on origin/main) as watchthelight, runs the local
# gate, then makes ONE push: the merge commit and the bookkeeping commit together, so main sees
# a single workflow run. State (lock, logs) is in $SF_STATE, see tools/ops-env.sh.
. "${SF_TOOLS:-$(dirname "$0")}/ops-env.sh"
R=watchthelight/shatterfish

if [ "$1" != "--one" ]; then
  [ $# -gt 0 ] || { echo "usage: merge-queue.sh <PR>[:<sprint key>] ..."; exit 2; }
  for a in "$@"; do
    P=${a%%:*}; K=${a#*:}; [ "$K" = "$a" ] && K=-
    # Run a copy: editing this file while a merge is running must not change that merge.
    cp "$0" "$SF_STATE/mq-run-$P.sh"
    sh "$SF_STATE/mq-run-$P.sh" --one "$P" "$K"; echo "== $P rc=$?"
  done
  echo "== QUEUE DONE"
  exit 0
fi

P=$2; K=${3:--}
W=$SF_ROOT/.claude/worktrees/merge
[ -d "$W" ] || git -C "$SF_ROOT" worktree add --detach "$W" origin/main || exit 1
cd "$W" || exit 1
while :; do s=$(gh pr checks $P -R $R --json bucket 2>/dev/null || echo '[]'); if [ "$s" != "[]" ] && jq -e 'all(.bucket!="pending")' <<<"$s" >/dev/null; then break; fi; sleep 60; done
jq -e 'any(.bucket=="fail")' <<<"$s" >/dev/null && { echo "$P CI FAIL"; exit 1; }
# One merge at a time in the shared merge worktree: agents queue their own PRs now.
L=$SF_STATE/merge.lock; until mkdir "$L" 2>/dev/null; do sleep 30; done; trap 'rmdir "$L"' EXIT INT TERM
B=$(gh pr view $P -R $R --json headRefName -q .headRefName); T=$(gh pr view $P -R $R --json title -q .title)
git fetch -q origin && git reset -q --hard && git checkout -q --detach origin/main || exit 1
git -c user.name=watchthelight -c user.email=admin@watchthelight.org merge --no-ff -q "origin/$B" -m "Merge #$P: $T" || { echo "MERGE-CONFLICT$P"; git diff --name-only --diff-filter=U; git merge --abort; exit 1; }
G=0
./gradlew -q --max-workers=2 assemble testClasses :api:test :brain:test :overlay:test -Pshatterfish.testForks=1 --console=plain > "$SF_STATE/m$P.log" 2>&1 || G=1
if [ $G = 1 ]; then
  # Local gate: everything compiles plus the fast modules; harness and rig tests already ran green in the PR CI and run again on main after the push.
  # Retry each failed test class alone once: the gate runs on a loaded machine and timing tests flake under load.
  F=$(grep -l "<failure\|<error" shatterfish/*/build/test-results/test/*.xml 2>/dev/null)
  if [ -n "$F" ] && [ $(echo "$F" | wc -l) -le 3 ]; then
    G=0
    for x in $F; do m=$(echo "$x" | cut -d/ -f2); c=$(basename "$x" .xml | sed 's/^TEST-//')
      echo "RETRY $m $c"; ./gradlew -q --max-workers=1 :$m:test --tests "$c" -Pshatterfish.testForks=1 --console=plain >> "$SF_STATE/m$P.log" 2>&1 && echo "FLAKE-PASSED-ALONE $c" || { echo "STILL-FAILS $c"; G=1; }
    done
  fi
fi
if [ $G = 0 ] && uv run --no-project --with-requirements docs/requirements.txt mkdocs build --strict -q > "$SF_STATE/m${P}d.log" 2>&1; then :
else echo GATE-FAIL$P; tail -n 40 "$SF_STATE/m$P.log" "$SF_STATE/m${P}d.log"; exit 1; fi

# Bookkeeping that edits files runs before the push, so its commit travels with the merge commit.
# The GitHub-side steps (issue states) need the PR merged, so they run after the push.
if [ "$K" != "-" ]; then python tools/after-merge.py $P --key $K --phase files; else python tools/after-merge.py $P --phase files; fi
if ! git diff --quiet; then git add -A _bmad-output README.md docs 2>/dev/null; git -c user.name=watchthelight -c user.email=admin@watchthelight.org commit -q -m "Bookkeeping after #$P"; fi
git push -q origin HEAD:main && echo PUSHED$P || exit 1

# GitHub marks the PR merged a moment after the push; wait up to 5 minutes for it.
n=0; until [ "$(gh pr view $P -R $R --json state -q .state)" = MERGED ] || [ $n -ge 30 ]; do sleep 10; n=$((n+1)); done
python tools/after-merge.py $P --phase github --close
