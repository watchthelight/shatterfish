"""Reference values for Shatterfish's GSPRT, computed by Fishtest (story 3.6).

Fishtest carries no licence, so none of its code is in this repository. This script is ours: it
imports a Fishtest checkout that you clone yourself, asks its `sprt` class for the LLR of a set of
trinomial counts, and prints the answers one per line. `GsprtReferenceTest` holds Shatterfish's own
implementation of the published formula to those answers.

    git clone https://github.com/official-stockfish/fishtest <dir>
    git -C <dir> checkout 2e540196ed8a72283a17f40793defd0f4a45d9c9
    uv run --no-project --with scipy python tools/gsprt_reference.py <dir> \
        > shatterfish/rig/src/test/resources/gsprt-reference.txt

Fishtest states its hypotheses in logistic Elo and converts them to expected scores with
L(x) = 1 / (1 + 10^(-x/400)). A pair-score mean p is that score at x = -400 log10(1/p - 1), so each
case is handed to Fishtest as the Elo that means p, and the score Fishtest actually used is recorded
beside the result -- the Java test reads that, not the p it was asked for, so a rounding in the
conversion cannot pass for agreement.
"""
import math
import os
import subprocess
import sys

PIN = "2e540196ed8a72283a17f40793defd0f4a45d9c9"

HYPOTHESES = [(0.50, 0.55), (0.45, 0.55), (0.30, 0.35), (0.50, 0.60), (0.49, 0.51)]
RATES = [(0.05, 0.05), (0.10, 0.05), (0.01, 0.20)]
# Losses, ties, wins. Zeros to exercise the regularization, lopsided sets to exercise the clamp in
# both directions, one of every tie, and a spread of ordinary ones.
COUNTS = [
    (0, 1, 0), (0, 25, 0), (1, 0, 0), (0, 0, 1), (3, 10, 2), (2, 10, 3), (10, 30, 12),
    (40, 120, 55), (55, 120, 40), (0, 50, 30), (30, 50, 0), (100, 300, 140), (140, 300, 100),
    (1, 1, 1), (7, 0, 9), (250, 400, 260), (0, 0, 40), (40, 0, 0),
    # The regime the Rig actually stops in: a burn-in's worth of pairs, most of them ties.
    (1, 8, 1), (1, 7, 2), (2, 7, 1), (2, 14, 4), (4, 14, 2), (3, 21, 6), (6, 21, 3),
    (4, 28, 8), (8, 28, 4), (0, 9, 1), (1, 9, 0),
]


def elo(p):
    return -400 * math.log10(1 / p - 1)


def main(checkout):
    # Line feeds, whatever the platform: the file is pinned by its SHA-256 and checked in as text.
    sys.stdout.reconfigure(newline=chr(10))
    head = subprocess.run(["git", "-C", checkout, "rev-parse", "HEAD"], capture_output=True,
                          text=True, check=True).stdout.strip()
    if head != PIN:
        sys.exit("the checkout is at " + head + ", and these values are pinned to " + PIN)
    sys.path.insert(0, os.path.join(checkout, "server"))
    from fishtest.stats.sprt import sprt  # noqa: E402 -- imported from the checkout, not vendored

    # One case per line, whitespace separated, floats in `repr` -- the shortest text that reads back
    # as the same double, so the Java side compares against exactly what Fishtest computed.
    print("# GSPRT reference values from Fishtest " + PIN + ", written by tools/gsprt_reference.py")
    print("# p0 p1 alpha beta losses ties wins lower upper llr clamped raw")
    from fishtest.stats import LLRcalc  # noqa: E402
    for p0, p1 in HYPOTHESES:
        for alpha, beta in RATES:
            for losses, ties, wins in COUNTS:
                test = sprt(alpha=alpha, beta=beta, elo0=elo(p0), elo1=elo(p1),
                            elo_model="logistic")
                test.set_state([losses, ties, wins])
                # The LLR before `set_state` clamps it, which is the value a stop is decided on and
                # the one a trace records: N times the drift `set_state` itself computes.
                n, pdf = LLRcalc.results_to_pdf([losses, ties, wins])
                raw = n * LLRcalc.LLR_drift_variance_alt2(pdf, test.s0, test.s1)[0]
                print(" ".join([repr(test.s0), repr(test.s1), repr(alpha), repr(beta),
                                str(losses), str(ties), str(wins), repr(test.a), repr(test.b),
                                repr(test.llr), "true" if test.clamped else "false", repr(raw)]))


if __name__ == "__main__":
    main(sys.argv[1])
