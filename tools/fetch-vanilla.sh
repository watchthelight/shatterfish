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
#
# No named remote is added. `git fetch <url> <tag>` needs none, and a remote that stayed
# behind would be a second repository for `gh` to target — the hazard CLAUDE.md already
# records for `upstream` — and would make `git merge` against a tree the docs call
# "never merged" a single command away.
set -eu

root=$(cd "$(dirname "$0")/.." && pwd)
pin="$root/vanilla.pin"
[ -f "$pin" ] || { echo "no pin at $pin" >&2; exit 1; }

# One value, stated once. Two `commit=` lines would leave this script and the generator
# reading different halves of the same pin, and the carriage return a Windows checkout can
# leave behind would make two identical-looking hashes compare unequal.
value() {
    found=$(sed -n "s/^$1=//p" "$pin" | tr -d '\r')
    count=$(printf '%s' "$found" | grep -c . || true)
    [ "$count" = "1" ] || { echo "$pin does not state $1 exactly once" >&2; exit 1; }
    printf '%s' "$found"
}
repository=$(value repository)
tag=$(value tag)
commit=$(value commit)
folder=$(value folder)

# The folder is removed below, so it is checked before anything else: a pin naming `.`,
# an absolute path, or anything that climbs is a pin that would delete the repository.
case "$folder" in
    ''|.|..|/*|*..*|*/*) echo "$pin names a folder this script will not remove: $folder" >&2; exit 1;;
esac

# One file the other game certainly has, so that "the tree is there" is a claim about the
# tree rather than about the folder existing.
sentinel="core/src/main/java/com/watabou/pixeldungeon/actors/mobs/Rat.java"

target="$root/$folder"
if [ -f "$target/.pinned" ] && [ "$(tr -d '\r' < "$target/.pinned")" = "$commit" ] && [ -f "$target/$sentinel" ]; then
    echo "$folder is already $tag ($commit)"
    exit 0
fi

git -C "$root" fetch --quiet --depth=1 --no-tags "$repository" "$tag"

have=$(git -C "$root" rev-parse FETCH_HEAD)
[ "$have" = "$commit" ] || {
    echo "the $tag tag of $repository is $have, and the pin says $commit" >&2; exit 1; }

rm -rf "$target"
mkdir -p "$target"
# Through a file rather than a pipe: `tar` exits zero on empty input, so a failed archive
# down a pipeline would leave an empty tree that the marker below then blesses as pinned.
git -C "$root" archive --format=tar "$commit" > "$target/.vanilla.tar"
tar -xf "$target/.vanilla.tar" -C "$target"
rm -f "$target/.vanilla.tar"
[ -f "$target/$sentinel" ] || { echo "$folder was written without $sentinel" >&2; rm -rf "$target"; exit 1; }

printf '%s' "$commit" > "$target/.pinned"
echo "$folder is $tag ($commit)"
