# Sourced by the operations scripts (merge-queue.sh, gradle-gate.sh, measure.sh).
# SF_STATE is where locks, the cap file and logs live. It is never a session scratchpad:
# set it in the environment, or it defaults to <temp dir>/shatterfish-ops.
# SF_ROOT is the main checkout (the parent of .git), found from any worktree.
# SF_TOOLS is the tools/ folder of the checkout the script was started from.
if [ -z "$SF_STATE" ]; then
  _t=$(cygpath -u "${TEMP:-/tmp}" 2>/dev/null || echo "${TEMP:-${TMPDIR:-/tmp}}")
  SF_STATE=$_t/shatterfish-ops
fi
mkdir -p "$SF_STATE"
if [ -z "$SF_ROOT" ]; then
  SF_ROOT=$(cd "$(git rev-parse --git-common-dir)/.." && pwd)
fi
SF_TOOLS=${SF_TOOLS:-$(cd "$(dirname "$0")" && pwd)}
export SF_STATE SF_ROOT SF_TOOLS
