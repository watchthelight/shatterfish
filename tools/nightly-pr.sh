#!/usr/bin/env bash
# Updates the one nightly results pull request (story 3.11, ADR-0002). Run by the nightly
# workflow's publish job from a checkout of main, after the play job's folder has been downloaded
# into build/nightly (night.jsonl, status.md, ledger-tonight.jsonl). Runs locally too: every
# GITHUB_* and NIGHTLY_* variable has a default.
#
# The branch rig/nightly is rebuilt on main every night and force-pushed with a lease. Nights
# recorded on it but not yet merged are carried forward, never dropped:
#  - the history is append-only and written only here, so the branch's copy is kept whenever
#    main's committed copy is a prefix of it;
#  - the ledger is also appended by ranked story pull requests, so it is merged as a set of lines:
#    main's lines in their order, then the branch's lines main does not have, then tonight's.
# Nothing here reaches beyond GitHub: git and gh only (NFR-8).
set -euo pipefail

branch=rig/nightly
ledger=registrations/ledger.jsonl
history=results/nightly/history.jsonl
out=${NIGHTLY_OUT:-build/nightly}
night=$out/night.jsonl
status_file=$out/status.md
tonight_ledger=$out/ledger-tonight.jsonl
workspace=${GITHUB_WORKSPACE:-$(pwd)}
summary=${GITHUB_STEP_SUMMARY:-/dev/null}
date=${NIGHTLY_DATE:-}
date=${date:-$(date -u +%F)}
run_url=${NIGHTLY_RUN_URL:-}
record_exit=${NIGHTLY_RECORD_EXIT:-}
gradle=${NIGHTLY_GRADLE:-./gradlew}
tmp=$(mktemp -d)

note() {
  echo "::warning::$1"
  echo "WARNING: $1" >> "$summary"
}

git config user.name watchthelight
git config user.email admin@watchthelight.org

# A night that broke before it could be recorded -- the build, the Rig, the recording -- still
# reaches the history, the page and the pull request as a failure, written here.
mkdir -p "$out"
if [ ! -s "$night" ]; then
  why="the night could not be recorded (recording exit ${record_exit:-unknown})"
  printf '{"causes":"","commit":"%s","date":"%s","finished":0,"incomplete":0,"ms":0,"pass":false,"registration":"","run":"%s","started":0,"unaccounted":0,"why":"%s"}\n' \
    "$(git rev-parse HEAD)" "$date" "$run_url" "$why" > "$night"
  echo "FAIL -- nightly smoke $date (a direction check under H-0001-nightly-smoke, never an acceptance): $why" > "$status_file"
fi
[ -s "$status_file" ] || echo "FAIL -- nightly smoke $date: no status was written" > "$status_file"
[ -f "$tonight_ledger" ] || : > "$tonight_ledger"
status=$(head -n 1 "$status_file")

# Whether the branch exists, told apart from not being able to ask.
lease=
set +e
git ls-remote --exit-code --heads origin "$branch" > /dev/null
remote=$?
set -e
if [ "$remote" -eq 0 ]; then
  git fetch --quiet origin "+refs/heads/$branch:refs/remotes/origin/$branch"
  lease=$(git rev-parse "refs/remotes/origin/$branch")
elif [ "$remote" -ne 2 ]; then
  echo "could not ask origin whether $branch exists (git ls-remote exit $remote)" >&2
  exit 1
fi

git show "HEAD:$ledger" 2>/dev/null | grep -v '^$' > "$tmp/main-ledger" || true
git show "HEAD:$history" 2>/dev/null | grep -v '^$' > "$tmp/main-history" || true
: > "$tmp/branch-ledger"
: > "$tmp/branch-history"
if [ -n "$lease" ]; then
  git show "$lease:$ledger" 2>/dev/null | grep -v '^$' > "$tmp/branch-ledger" || true
  git show "$lease:$history" 2>/dev/null | grep -v '^$' > "$tmp/branch-history" || true
fi

# The history: the branch's copy when main's is a prefix of it, main's otherwise, said aloud.
size=$(wc -c < "$tmp/main-history")
if [ "$(wc -c < "$tmp/branch-history")" -ge "$size" ] && cmp -s -n "$size" "$tmp/main-history" "$tmp/branch-history"; then
  cp "$tmp/branch-history" "$tmp/history"
else
  cp "$tmp/main-history" "$tmp/history"
  note "$branch's $history does not extend main's; its unmerged nights were not carried forward"
fi

# Tonight, unless this run has already been recorded (a re-run of the publish job alone).
if [ -n "$run_url" ] && grep -qF "\"run\":\"$run_url\"" "$tmp/history"; then
  note "the night of $run_url is already in the history; it is not appended twice"
else
  cat "$night" >> "$tmp/history"
fi

# The ledger: main's lines, then the branch's lines main lacks, then tonight's, each line once.
awk 'NF && !seen[$0]++' "$tmp/main-ledger" "$tmp/branch-ledger" "$tonight_ledger" > "$tmp/ledger"

git checkout --quiet -B "$branch"
cp "$tmp/ledger" "$ledger"
mkdir -p "$(dirname "$history")"
cp "$tmp/history" "$history"
"$gradle" -q --no-daemon :rig:nightly --args="$workspace page"

git add "$ledger" "$history" docs/results/nightly.md
git commit --quiet -m "Nightly smoke $date: ${status%% --*}" -m "$status"

# A push or pull request made with the workflow's token starts no other workflow, so the results
# pull request gets no build of its own: the page and the history are checked here instead.
"$gradle" -q --no-daemon :rig:test --tests '*NightlyTest'

git push --quiet --force-with-lease="$branch:$lease" origin "$branch"

{
  echo "$status"
  echo
  echo "One pull request, updated every night on \`$branch\`; nights not yet merged are carried"
  echo "forward. Every night is a direction check under H-0001, never an acceptance. This pull"
  echo "request runs no CI of its own: the workflow's token starts no other workflow, so the"
  echo "publish job checked the page and the history before pushing."
  echo
  echo "Run: ${run_url:-by hand}"
  echo "Page: docs/results/nightly.md"
} > "$tmp/body.md"
title="Nightly smoke: ${status%% --*} on $date"

number=$(gh pr list --head "$branch" --base main --state open --json number -q '.[0].number' || true)
if [ -n "$number" ]; then
  gh pr edit "$number" --title "$title" --body-file "$tmp/body.md"
else
  # Needs the repository setting "Allow GitHub Actions to create and approve pull requests", or
  # the first pull request opened by hand; later nights only edit it.
  gh pr create --head "$branch" --base main --title "$title" --body-file "$tmp/body.md" \
    || note "could not open the results pull request; the branch $branch is pushed"
fi
