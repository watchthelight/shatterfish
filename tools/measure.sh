# usage: measure.sh <label> [--baseline <git-ref|folder>]
# The PR measurement in one command: :rig:direction on the standard set, all classes, first 40
# of each, at salts 1000 and 3000 (--jobs 4), each slice behind the resource gate, then the two
# slices pooled into one table that is printed and saved.
# Run it from the worktree whose code is being measured. Output: $SF_STATE/measure/<label>/
# (1000/, 3000/, one log per slice, pooled.md). --out gets a Windows-form path (cygpath -m):
# an MSYS /c/... path makes Java write under C:\c\ (29 GB of rig output ended up there once).
. "$(dirname "$0")/ops-env.sh"
LABEL=$1; shift
[ -n "$LABEL" ] || { echo "usage: measure.sh <label> [--baseline <git-ref|folder>]"; exit 2; }
BASE=
SET=standard
while [ $# -gt 0 ]; do
  case "$1" in
    --baseline) BASE=$2; shift 2 ;;
    --set) SET=$2; shift 2 ;;
    *) echo "unknown argument $1"; exit 2 ;;
  esac
done
# goo and tengu are registered sets, holdout is refused during development.
case "$SET" in goo|holdout|tengu) echo "measure.sh refuses the $SET set"; exit 2 ;; esac
OUT=$SF_STATE/measure/$LABEL
rm -rf "$OUT"; mkdir -p "$OUT"
OUTW=$(cygpath -m "$OUT")
[ -n "$OUTW" ] || { echo "cygpath failed"; exit 1; }
BASEARG=
if [ -n "$BASE" ]; then
  [ -d "$BASE" ] && BASE=$(cygpath -m "$(cd "$BASE" && pwd)")
  BASEARG="--baseline $BASE"
fi
for SALT in 1000 3000; do
  sh "$SF_TOOLS/gradle-gate.sh"
  ./gradlew :rig:direction --args="--set $SET --class ALL --first 40 --salt-base $SALT $BASEARG --jobs 4 --out $OUTW/$SALT" --console=plain > "$OUT/$SALT.log" 2>&1 \
    || { echo "slice $SALT failed, see $OUT/$SALT.log"; tail -n 30 "$OUT/$SALT.log"; exit 1; }
done
python "$SF_TOOLS/pool-direction.py" "$OUT/1000" "$OUT/3000" | tee "$OUT/pooled.md"
echo "saved $OUT/pooled.md"
