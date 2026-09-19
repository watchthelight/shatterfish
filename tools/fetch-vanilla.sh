#!/usr/bin/env sh
# Put the second pinned source of the Codex where the generator can read it.
#
# Vanilla Pixel Dungeon is read only: story 2.8's vocabulary diff needs a side to compare
# this fork against. Nothing here is built or compiled against, so the tree is extracted
# rather than checked out, and it never enters the index: `git archive` writes the pinned
# commit straight into the folder. Running this twice is the same as running it once.
#
# The pin lives in `vanilla.pin` beside this script's repository root, so the script, the
# generator and `docs/UPSTREAM.md` all read one file rather than three copies of a hash.
set -eu

root=$(cd "$(dirname "$0")/.." && pwd)
pin="$root/vanilla.pin"
[ -f "$pin" ] || { echo "no pin at $pin" >&2; exit 1; }

value() { sed -n "s/^$1=//p" "$pin" | head -1; }
repository=$(value repository)
tag=$(value tag)
commit=$(value commit)
folder=$(value folder)
[ -n "$repository" ] && [ -n "$tag" ] && [ -n "$commit" ] && [ -n "$folder" ] || {
    echo "$pin does not name a repository, a tag, a commit and a folder" >&2; exit 1; }

target="$root/$folder"
if [ -f "$target/.pinned" ] && [ "$(cat "$target/.pinned")" = "$commit" ]; then
    echo "$folder is already $tag ($commit)"
    exit 0
fi

# A remote named `vanilla` so the fetch is one word and the pin is visible in `git remote`.
if ! git -C "$root" remote get-url vanilla >/dev/null 2>&1; then
    git -C "$root" remote add vanilla "$repository"
fi
git -C "$root" fetch --quiet --depth=1 vanilla "$tag" --no-tags

have=$(git -C "$root" rev-parse FETCH_HEAD)
[ "$have" = "$commit" ] || {
    echo "the $tag tag of $repository is $have, and the pin says $commit" >&2; exit 1; }

rm -rf "$target"
mkdir -p "$target"
git -C "$root" archive "$commit" | tar -x -C "$target"
printf '%s' "$commit" > "$target/.pinned"
echo "$folder is $tag ($commit)"
