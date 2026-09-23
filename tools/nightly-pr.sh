#!/usr/bin/env bash
# Updates the one nightly results pull request (story 3.11, ADR-0002). Run by the nightly workflow
# from a checkout of main, after the Rig has played the night and `:rig:nightly ... night` has
# written build/nightly/night.jsonl and build/nightly/status.md.
#
# The branch rig/nightly is rebuilt on main every night and force-pushed. Nights recorded on it but
# not yet merged are carried forward, never dropped: the ledger and the nightly history are
# append-only, so the branch's copy is taken as the base whenever main's committed copy is a prefix
# of it, and tonight's lines are appended to that. Nothing here reaches beyond GitHub: git and gh
# only (NFR-8).
set -euo pipefail

branch=rig/nightly
ledger=registrations/ledger.jsonl
history=results/nightly/history.jsonl
night=build/nightly/night.jsonl
status_file=build/nightly/status.md
tmp=${RUNNER_TEMP:-$(mktemp -d)}

if [ ! -f "$night" ]; then
  echo "no night was recorded (build/nightly/night.jsonl is missing); nothing to publish" >&2
  exit 1
fi

git config user.name watchthelight
git config user.email admin@watchthelight.org

# Tonight's ledger lines: whatever the Rig appended past the ledger main committed.
committed=$(git show "HEAD:$ledger" | wc -l)
tail -n "+$((committed + 1))" "$ledger" > "$tmp/tonight-ledger.jsonl"

git fetch --quiet origin "$branch" 2>/dev/null || true

# base_of <path> <out>: main's committed copy, or the branch's when main's is a prefix of it.
# When the branch's copy is not an extension of main's, the branch's unmerged lines cannot be
# carried forward without rewriting main's history; that is said aloud rather than done quietly.
base_of() {
  local path=$1 out=$2 size
  git show "HEAD:$path" > "$out" 2>/dev/null || : > "$out"
  if git show "origin/$branch:$path" > "$tmp/previous" 2>/dev/null; then
    size=$(wc -c < "$out")
    if [ "$(wc -c < "$tmp/previous")" -ge "$size" ] && cmp -s -n "$size" "$out" "$tmp/previous"; then
      cp "$tmp/previous" "$out"
    else
      echo "::warning::$branch's $path does not extend main's; its unmerged lines are not carried forward"
      echo "WARNING: $branch's \`$path\` does not extend main's; its unmerged lines were not carried forward." \
        >> "${GITHUB_STEP_SUMMARY:-/dev/null}"
    fi
  fi
}

base_of "$ledger" "$tmp/ledger"
base_of "$history" "$tmp/history"

git checkout --quiet -B "$branch"
cat "$tmp/ledger" "$tmp/tonight-ledger.jsonl" > "$ledger"
mkdir -p "$(dirname "$history")"
cat "$tmp/history" "$night" > "$history"
./gradlew -q --no-daemon :rig:nightly --args="$GITHUB_WORKSPACE page"

status=$(head -n 1 "$status_file")
git add "$ledger" "$history" docs/results/nightly.md
git commit --quiet -m "Nightly smoke $(date -u +%F): ${status%% --*}" -m "$status"
git push --quiet --force origin "$branch"

{
  echo "$status"
  echo
  echo "One pull request, updated every night on \`$branch\`; nights not yet merged are carried"
  echo "forward. Every night is a direction check under H-0001, never an acceptance."
  echo
  echo "Run: $GITHUB_SERVER_URL/$GITHUB_REPOSITORY/actions/runs/$GITHUB_RUN_ID"
  echo "Page: docs/results/nightly.md"
} > "$tmp/body.md"
title="Nightly smoke: ${status%% --*} on $(date -u +%F)"

if gh pr view "$branch" --json number > /dev/null 2>&1; then
  gh pr edit "$branch" --title "$title" --body-file "$tmp/body.md"
else
  # Needs the repository setting "Allow GitHub Actions to create and approve pull requests". If it
  # is off, the branch is still pushed and the job summary still says what happened.
  gh pr create --head "$branch" --base main --title "$title" --body-file "$tmp/body.md" \
    || echo "could not open the results pull request; the branch $branch is pushed" >&2
fi
