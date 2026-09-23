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
 * (ADR-0012's own mitigation, line 118). The compact constructor refuses a Brain name or a machine
 * class that smuggles one in as text.
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
 * @param releaseLevel whether this Registration claims a release-level result, which is the only
 *                     kind that may touch the holdout set (FR-20)
 */
public record Registration(String hypothesis, String claim, Brain brainA, Brain brainB,
                           String seedSet, int seedVersion, int alphaPerMil, int betaPerMil,
                           int burnIn, int maximum, int budgetMs, String machineClass,
                           boolean releaseLevel) {

    /** The id's shape: readable, sortable, and safe as a file name on every platform. */
    public static final String ID_PATTERN = "H-[0-9]{4}(-[a-z0-9]+)*";

    /**
     * The words a Registration may not contain anywhere, in any field.
     *
     * <p>Crude on purpose. The rule "a Registration carries no salt" is worth more than a tidy
     * implementation of it, and the failure this prevents — a salt written into the `claim` line,
     * or into a machine class, by somebody who meant well — is exactly the kind that would survive
     * a review of the field list.
     */
    private static final String[] FORBIDDEN = {"salt", "seedcode"};

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
        Canon.require(!claim.isEmpty(), "a Registration says in words what it is claiming");
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
        Canon.require(burnIn >= 1, "pairs are played before a stop is allowed: " + burnIn);
        Canon.require(maximum > burnIn,
                "a maximum past which a result is undecided, and it is more than the burn-in: "
                        + maximum + " after " + burnIn);
        Canon.require(budgetMs >= 0, "a per-Decision budget is not negative: " + budgetMs);
        Canon.text(machineClass, "a machine class");
        Canon.require(!machineClass.isEmpty(),
                "a Registration says what class of machine its numbers are from");
        forbidden(hypothesis, claim, seedSet, machineClass, brainB.name());
        if (brainA != null) {
            forbidden(brainA.name());
        }
    }

    /**
     * Refuses a Registration that names a salt in any of its words.
     *
     * <p>A Registration is committed before the Runs and is public, so anything in it is something
     * the Brain's author has. The field list has no salt in it; this is about the fields that take
     * free text, where one could be written by somebody who thought recording it was tidy.
     */
    private static void forbidden(String... words) {
        for (String word : words) {
            // Lowered by hand. `String.toLowerCase` without a Locale is locale-dependent -- the
            // Turkish one maps I to a dotless i -- and `java.util.Locale` is a type `api` may not
            // reach (ApiBoundaryTest). Every word this looks for is ASCII.
            String lower = ascii(word);
            for (String banned : FORBIDDEN) {
                Canon.require(!lower.contains(banned),
                        "a Registration is written before the Runs and is public, so a salt in one"
                                + " is a salt the Brain's author has (ADR-0007): " + word);
            }
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
    public String hash() {
        // Through the module's own digest and its own UTF-8 encoder. `api` reaches no JDK type
        // beyond the language itself (ApiBoundaryTest), which is why `Sha256` exists at all: a
        // module that may not call MessageDigest and must nevertheless hash what it writes.
        return Sha256.hex(Sha256.digest(Utf8.encode(canonical())));
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
