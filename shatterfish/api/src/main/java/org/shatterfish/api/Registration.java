package org.shatterfish.api;

/**
 * The hypothesis, fixed before the numbers are seen (story 3.5, FR-22, ADR-0012).
 *
 * <p><b>Why this exists.</b> Without it a comparison can be run, looked at, and then adjusted — a
 * bound, a seed set, a stopping rule — until the answer is the wanted one, with nothing recording
 * that anything was adjusted. Every field below is a decision that changes what a result means, so
 * every one of them is stated in advance, committed to the repository, and hashed. A Run log
 * records the hash, not the name, because a name can be pointed at different bytes afterwards.
 *
 * <p><b>The salt is not here, and its absence is the point.</b> ADR-0007 names the attack: the
 * mixing function is published, so a salt anyone can know in advance lets a Brain's author compute
 * the game's coming draws as pure data — whether the next attack hits, what the next chest holds.
 * A Registration is written before the Runs and is public, so a salt in it would be a salt the
 * Brain's author has. The salts are drawn when each pair executes and appear only in the Run logs
 * they belong to, which is late enough to be useless to a Brain and early enough to be replayable
 * (ADR-0012's own mitigation, line 118).
 *
 * <p>What enforces that is structural: there is no field for a salt, a file carrying a member this
 * record does not have is refused when the Rig reads it, and {@code Salt.draw()} takes its value
 * from a secret at the moment a Run executes, so there is nothing to write down in advance. The
 * compact constructor adds a third lock — see {@link #noSaltHere} — which looks for the
 * <em>shape</em> of a salt in the fields that hold prose, because a word blacklist cannot catch
 * sixteen hex digits and the first draft of this class tried to.
 *
 * <p><b>It is in {@code api} on purpose.</b> The Rig reads one to decide what it may run; a Results
 * page reads one to say what was claimed. Neither owns it, and a value owned by the thing that
 * checks it is not a check.
 *
 * @param hypothesis   the id, which is also the file name: what a Results page cites
 * @param claim        what is being asserted, in one sentence, for a person rather than a machine
 * @param brainA       the Brain the comparison measures against, or null when this Registration
 *                     fixes a baseline rather than a comparison. ADR-0012 describes comparisons,
 *                     and every field it lists is about one -- but E3's own done-when is that a
 *                     baseline is published, and the nightly smoke job compares nothing at all. A
 *                     baseline is a hypothesis stated before the numbers just as a comparison is;
 *                     only the shape differs, and a form that could not express one would have
 *                     forced the first Registration to name a Brain that does not exist
 * @param brainB       the Brain being measured
 * @param seedSet      the Seed set's name, which decides which Runs are played
 * @param seedVersion  the Seed set schema version, so "standard" means the same thing later
 * @param alphaPerMil  the false-accept rate, in thousandths. A rate is not a float here for the
 *                     reason every number in a hashed value is not a float: two machines agreeing
 *                     on a float's text is a thing to hope for rather than rely on (ADR-0011)
 * @param betaPerMil   the false-reject rate, in thousandths
 * @param burnIn       pairs played before any stop is allowed, which guards the approximation the
 *                     bounds rest on (ADR-0012)
 * @param maximum      pairs after which the result is undecided rather than waited for
 * @param budgetMs     the per-Decision budget for the comparisons where thinking time is the thing
 *                     being changed; 0 when the comparison does not constrain it
 * @param machineClass what the numbers were measured on, because throughput is not portable
 * @param p0PerMil     the pair-score mean under H0, in thousandths: what "no better" means for this
 *                     comparison. Story 3.5 shipped the record without it and ADR-0012 lists it;
 *                     a sequential test cannot run without its two hypotheses (story 3.6)
 * @param p1PerMil     the pair-score mean under H1, in thousandths, above {@code p0PerMil}. Both are
 *                     0 for a baseline, which tests nothing, and are then absent from the canonical
 *                     text -- so a baseline committed before they existed keeps its hash
 * @param missingPerMil the largest fraction of pairs, in thousandths, that may have a Run missing
 *                     before the comparison is void. ADR-0012 requires it and story 3.6 first
 *                     shipped without it, which let a Brain turn its losses into ties by crashing:
 *                     a missing Run scores a half, a death scores nothing, and both the mean and
 *                     the variance move the test toward accepting. 0 for a baseline
 * @param releaseLevel whether this Registration claims a release-level result, which is the only
 *                     kind that may touch the holdout set (FR-20)
 */
public record Registration(String hypothesis, String claim, Brain brainA, Brain brainB,
                           String seedSet, int seedVersion, int alphaPerMil, int betaPerMil,
                           int burnIn, int maximum, int budgetMs, String machineClass,
                           boolean releaseLevel, int p0PerMil, int p1PerMil,
                           int missingPerMil) {

    /** The id's shape: readable, sortable, and safe as a file name on every platform. */
    public static final String ID_PATTERN = "H-[0-9]{4}(-[a-z0-9]+)*";

    /**
     * The words a Registration may not use as words.
     *
     * <p>Whole words, not substrings: the first draft refused a machine class of {@code basalt-ci}
     * and a claim mentioning Salt Lake, which is a guard that costs more than it catches.
     */
    private static final String[] FORBIDDEN = {"salt", "salts", "seedcode", "seedcodes"};

    /**
     * How many hex digits in a row are too many for a field that holds prose.
     *
     * <p>A salt is sixteen. Eight is well under that and well over anything that turns up in a
     * sentence by accident, and the fields this is applied to are the ones a person writes: a
     * claim, a machine class, a Brain's name. The constrained hex fields — a commit, a
     * configuration hash — are exempt by not being passed here, which is the honest arrangement:
     * this cannot tell a salt from a commit, and pretending otherwise would refuse every
     * Registration ever written.
     */
    private static final int TOO_MUCH_HEX = 8;

    /**
     * <b>What actually keeps a salt out of a Registration.</b>
     *
     * <p>Two things, and neither of them is the check below. {@link org.shatterfish.api.Registration}
     * has no field for one, and a file carrying a member this record does not have is refused by
     * the Rig's canonical round-trip; and the salt is drawn from a secret at the moment a Run
     * executes, so there is nothing to write down in advance even for somebody who wanted to.
     *
     * <p>The check below is a third lock on the same door, for the case those two do not cover:
     * somebody writing a salt into a field that holds prose because recording it seemed tidy. It
     * looks for the shape rather than the word, because a salt is sixteen hex digits and contains
     * neither of the words a blacklist would hold.
     */
    private static void noSaltHere(String what, String word) {
        String lower = ascii(word);
        for (String banned : FORBIDDEN) {
            Canon.require(!isWord(lower, banned),
                    "a Registration is written before the Runs and is public, so a salt in one is a"
                            + " salt the Brain's author has (ADR-0007); " + what + " says: " + word);
        }
        int run = 0;
        for (int i = 0; i < lower.length(); i++) {
            char c = lower.charAt(i);
            boolean hex = (c >= '0' && c <= '9') || (c >= 'a' && c <= 'f');
            run = hex ? run + 1 : 0;
            Canon.require(run < TOO_MUCH_HEX,
                    "a Registration is public and is written before the Runs, so a run of " + run
                            + " hex digits in " + what + " is refused whatever it is: a salt is"
                            + " sixteen of them, and the shape is the only thing a reader can check"
                            + " (ADR-0007). " + what + " says: " + word);
        }
    }

    /** Whether {@code banned} appears in {@code text} as a whole word. */
    private static boolean isWord(String text, String banned) {
        int at = text.indexOf(banned);
        while (at >= 0) {
            boolean before = at == 0 || !letter(text.charAt(at - 1));
            int after = at + banned.length();
            if (before && (after == text.length() || !letter(text.charAt(after)))) {
                return true;
            }
            at = text.indexOf(banned, at + 1);
        }
        return false;
    }

    private static boolean letter(char c) {
        return (c >= 'a' && c <= 'z') || (c >= '0' && c <= '9');
    }

    /** One Brain, as a Registration names it: what it is and what it was built from. */
    public record Brain(String name, String commit, String configHash) {

        public Brain {
            Canon.text(name, "a Brain's name");
            Canon.require(name.matches(RunLog.BRAIN_PATTERN), "a Brain's name is lower case: " + name);
            Canon.text(commit, "a Brain's commit");
            Canon.require(commit.matches("[0-9a-f]{7,40}"),
                    "a Brain's commit is a git object name: " + commit);
            Canon.text(configHash, "a Brain's configuration hash");
            Canon.require(configHash.matches("[0-9a-f]{64}"),
                    "a Brain's configuration hash is a SHA-256 in lower-case hex: " + configHash);
        }
    }

    public Registration {
        Canon.text(hypothesis, "a hypothesis id");
        Canon.require(hypothesis.matches(ID_PATTERN),
                "a hypothesis id looks like H-0001-a-short-name: " + hypothesis);
        Canon.text(claim, "what a Registration claims");
        Canon.require(!claim.isBlank(), "a Registration says in words what it is claiming");
        // Bounded, because the claim is concatenated into the reason a held-out publication records
        // and a Results page prints. A hypothesis that needs more than this is a document, and the
        // Registration should cite it.
        Canon.require(claim.length() <= 2000,
                "a claim is one sentence a person reads, not " + claim.length() + " characters");
        Canon.require(brainB != null, "a Registration names the Brain it is about");
        Canon.require(brainA == null || !brainA.equals(brainB),
                "a comparison of a Brain against itself measures the Seed set, not the Brain");
        Canon.text(seedSet, "a Seed set's name");
        Canon.require(seedSet.matches("[a-z][a-z0-9]*"), "a Seed set's name: " + seedSet);
        Canon.require(seedVersion >= 1, "a Seed set version is positive: " + seedVersion);
        Canon.require(alphaPerMil >= 1 && alphaPerMil < 1000,
                "a false-accept rate is a rate: " + alphaPerMil + " per mil");
        Canon.require(betaPerMil >= 1 && betaPerMil < 1000,
                "a false-reject rate is a rate: " + betaPerMil + " per mil");
        // Together below one, or the lower bound log(β/(1−α)) lands above the upper log((1−β)/α)
        // and almost every run accepts at the burn-in.
        Canon.require(alphaPerMil + betaPerMil < 1000,
                "the two error rates together are below one: " + alphaPerMil + " + " + betaPerMil
                        + " per mil");
        Canon.require(burnIn >= 1, "pairs are played before a stop is allowed: " + burnIn);
        Canon.require(maximum > burnIn,
                "a maximum past which a result is undecided, and it is more than the burn-in: "
                        + maximum + " after " + burnIn);
        Canon.require(budgetMs >= 0, "a per-Decision budget is not negative: " + budgetMs);
        if (brainA == null) {
            Canon.require(p0PerMil == 0 && p1PerMil == 0 && missingPerMil == 0,
                    "a baseline tests no hypothesis, so it states no p0, p1 or missing fraction: "
                            + p0PerMil + ", " + p1PerMil + ", " + missingPerMil);
        } else {
            // H1 above one half, so that accepting means "better" and not merely "not much worse":
            // a comparison registered at p0 = 0.40, p1 = 0.48 accepts two equal Brains.
            Canon.require(p0PerMil > 0 && p0PerMil < p1PerMil && p1PerMil < 1000 && p1PerMil > 500,
                    "a comparison states H0 and H1 as pair-score means with 0 < p0 < p1 < 1 and"
                            + " H1 above one half: " + p0PerMil + ", " + p1PerMil + " per mil");
            Canon.require(missingPerMil >= 0 && missingPerMil < 1000,
                    "a missing fraction is a fraction: " + missingPerMil + " per mil");
        }
        Canon.text(machineClass, "a machine class");
        Canon.require(!machineClass.isBlank(),
                "a Registration says what class of machine its numbers are from");
        Canon.require(machineClass.length() <= 200, "a machine class is a name, not a paragraph");
        noSaltHere("the hypothesis id", hypothesis);
        noSaltHere("the claim", claim);
        noSaltHere("the Seed set", seedSet);
        noSaltHere("the machine class", machineClass);
        noSaltHere("the measured Brain's name", brainB.name());
        if (brainA != null) {
            noSaltHere("the baseline Brain's name", brainA.name());
        }
    }


    /** ASCII lower case, which is all this needs and all it is allowed. */
    private static String ascii(String word) {
        StringBuilder out = new StringBuilder(word.length());
        for (int i = 0; i < word.length(); i++) {
            char c = word.charAt(i);
            out.append(c >= 'A' && c <= 'Z' ? (char) (c + 32) : c);
        }
        return out.toString();
    }

    /**
     * A baseline: one Brain, no hypotheses to test between.
     *
     * <p>Every baseline states {@code p0} and {@code p1} as zero, so this spares its callers the two
     * zeros and keeps a comparison the only kind that has to think about them.
     */
    public Registration(String hypothesis, String claim, Brain brainA, Brain brainB, String seedSet,
                        int seedVersion, int alphaPerMil, int betaPerMil, int burnIn, int maximum,
                        int budgetMs, String machineClass, boolean releaseLevel) {
        this(hypothesis, claim, brainA, brainB, seedSet, seedVersion, alphaPerMil, betaPerMil, burnIn,
                maximum, budgetMs, machineClass, releaseLevel, 0, 0, 0);
    }

    /** Whether this fixes a comparison of two Brains, rather than a baseline for one. */
    public boolean comparison() {
        return brainA != null;
    }

    /** The id, which is also the name of the file this was read from. */
    public String id() {
        return hypothesis;
    }

    /**
     * This Registration as canonical JSON: sorted keys, no whitespace, whole numbers.
     *
     * <p>The same rules the Run log is written under and the methodology page publishes, so a
     * stranger can recompute {@link #hash()} with a script. A rate is thousandths for the reason
     * ADR-0011 gives: no floats anywhere a hash is taken.
     */
    public String canonical() {
        JsonWriter out = new JsonWriter();
        out.beginObject();
        out.key("alpha_per_mil").value(alphaPerMil);
        if (brainA != null) {
            // Absent rather than null when there is none: the format writes no nulls (ADR-0011),
            // and a baseline Registration is not a comparison with a missing half.
            brain(out, "brain_a", brainA);
        }
        brain(out, "brain_b", brainB);
        out.key("beta_per_mil").value(betaPerMil);
        out.key("budget_ms").value(budgetMs);
        out.key("burn_in").value(burnIn);
        out.key("claim").value(claim);
        out.key("hypothesis").value(hypothesis);
        out.key("machine_class").value(machineClass);
        out.key("maximum").value(maximum);
        if (brainA != null) {
            // Only a comparison has hypotheses. Absent rather than zero for a baseline, so the
            // baselines committed before these fields existed keep the hash every log stamped.
            out.key("missing_per_mil").value(missingPerMil);
            out.key("p0_per_mil").value(p0PerMil);
            out.key("p1_per_mil").value(p1PerMil);
        }
        out.key("release_level").value(releaseLevel);
        out.key("seed_set").value(seedSet);
        out.key("seed_version").value(seedVersion);
        out.endObject();
        return out.toJson();
    }

    private static void brain(JsonWriter out, String key, Brain brain) {
        out.key(key).beginObject();
        out.key("commit").value(brain.commit());
        out.key("config").value(brain.configHash());
        out.key("name").value(brain.name());
        out.endObject();
    }

    /**
     * SHA-256 over {@link #canonical()}, in lower-case hex.
     *
     * <p>This is what a Run log records, rather than the id. An id alone lets the file it names be
     * edited and re-committed after the Runs, leaving every log still agreeing with it; the hash
     * pins the bytes.
     */
    /**
     * SHA-256 over any canonical text, in lower-case hex.
     *
     * <p>Exposed so that a caller holding the bytes it actually read can hash <em>those</em> rather
     * than a re-rendering of what it parsed out of them. The two agree when the file is canonical,
     * which is precisely why hashing the re-rendering proved nothing: both sides of any comparison
     * moved together.
     */
    public static String hashOf(String canonical) {
        return Sha256.hex(Sha256.digest(Utf8.encode(canonical)));
    }

    public String hash() {
        // Through the module's own digest and its own UTF-8 encoder. `api` reaches no JDK type
        // beyond the language itself (ApiBoundaryTest), which is why `Sha256` exists at all: a
        // module that may not call MessageDigest and must nevertheless hash what it writes.
        return hashOf(canonical());
    }

    /**
     * What a Run log's header records: the id and the first sixteen hex digits of the hash.
     *
     * <p>Sixteen rather than sixty-four because the header is read by people as often as by
     * scripts, and this value has to be recognisable beside a run id on the same line. It is a
     * pointer to a committed file, not a commitment on its own: the full hash is in the ledger and
     * the file is in git, and both are where a dispute is settled.
     */
    public String stamp() {
        return hypothesis + "@" + hash().substring(0, 16);
    }
}
