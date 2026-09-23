package org.shatterfish.rig;

import org.shatterfish.api.Decider;
import org.shatterfish.api.SeedSet;
import org.shatterfish.harness.agent.RandomAgent;
import org.shatterfish.harness.agent.WithholdingAgent;
import org.shatterfish.harness.rng.Mix;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * The Brains the Rig can be told to run (story 3.3), by the names it answers to on the command
 * line.
 *
 * <p>There is one so far. {@code random} is the harness's own random agent, which is not a Brain at
 * all — it is the Baseline every later Brain is measured against (story 3.9), and having it here
 * now is what lets the runner be built and measured before a Brain exists. A real Brain joins this
 * list in E4 by being named here; nothing else about the runner changes.
 *
 * <p>A Brain is a {@link Decider}: an Observation in, an Action out, and no second argument to ask
 * for anything else.
 *
 * <p><b>A Decider is never handed the salt.</b> A Brain that made its own randomness from the clock
 * would not be reproducible, so one that wants randomness is given a seed — but not <em>that</em>
 * one. The salt is what the harness reseeds the game's own generator from at every wait
 * ({@code RngControl}, ADR-0007), the mixing function is published on the methodology page, and the
 * game's generator is a published LCG. A Decider holding the salt could therefore compute the
 * game's coming draws with nothing but the 64-bit arithmetic {@code java.lang} already gives it:
 * at the first wait of the first floor it would know whether its next attack lands and what the
 * next chest holds. ADR-0007 rejected that attack in advance -- "a salt it never sees cannot be
 * predicted" -- and {@code Salt} says the salt is "shown to nothing that plays".
 *
 * <p>So a Decider's seed is {@link #agentSeed}, derived from the Run's own triple: the seed, the
 * hero class and the challenge flags. Those are things a human at the same screen has — the seed is
 * on the screen they typed it into — and they are fixed before the Run starts, so the Decider's
 * stream is reproducible from the tuple alone. The salt still varies the game; the two are
 * different axes, which is why {@code RunLoop.play} has always taken them as two parameters.
 */
public final class Brains {

    /** The Baseline: a uniform choice from the valid set, which is what "no Brain" measures as. */
    public static final String RANDOM = "random";

    /**
     * The deliberately worse Brain (story 3.9, SM-5): the Baseline with {@code Descend} taken out of
     * what it may choose. It exists so the Rig can be shown rejecting something.
     */
    public static final String NO_DESCEND = "random_nodescend";

    /**
     * The worse Brain that is worse (story 3.9): the Baseline with {@code Rest} withheld. The first
     * one, {@link #NO_DESCEND}, played the same Run as the Baseline on every triple of
     * {@code standard} that reached an ending, because the random agent never takes the stairs;
     * withholding {@code Attack} ({@link #NO_ATTACK}) changed one pair of the 25 in {@code smoke}.
     * Without {@code Rest} a random Run takes many more, shorter Actions and dies far sooner in turns:
     * a median of 288 against the Baseline's 1,384 on {@code standard}.
     */
    public static final String NO_REST = "random_norest";

    /**
     * The Baseline with {@code Attack} withheld: the second candidate story 3.9 tried on
     * {@code smoke}, kept so that the number the worse-Brain page quotes for it can be reproduced.
     * Not registered on any accepting set.
     */
    public static final String NO_ATTACK = "random_noattack";

    /**
     * The random agent drawing from another stream (story 3.10): equal in strength to the Baseline
     * by construction, and playing different Runs, so a comparison of the two is H0 exactly. It is
     * the Brain an UNDECIDED result is shown with.
     */
    public static final String TWIN = "random_twin";

    /** Mixed into a Run's agent seed to give the twin its own stream. */
    static final long TWIN_STREAM = 0x7715_7715L;

    /** The Brains that are the Baseline with one kind of Action withheld, and which kind. */
    private static final java.util.Map<String, Class<? extends org.shatterfish.api.Action>> WITHHELD =
            java.util.Map.of(NO_DESCEND, org.shatterfish.api.Action.Descend.class,
                    NO_REST, org.shatterfish.api.Action.Rest.class,
                    NO_ATTACK, org.shatterfish.api.Action.Attack.class);

    /** The kind of Action a withholding Brain withholds, or null for any other Brain. */
    static Class<? extends org.shatterfish.api.Action> withheld(String name) {
        return WITHHELD.get(name);
    }

    /**
     * The files that decide what a Brain does, by name.
     *
     * <p>FR-20 allows the held-out set one use per <em>Brain version</em>, and a version has to be
     * something that changes when the Brain changes and not when anything else does. The
     * repository's HEAD is not that: a README typo moves it, which would have handed an unchanged
     * Brain a fresh allowance every time somebody fixed a comment. The commit that last touched
     * these paths is.
     *
     * <p>A Brain that adds a file and does not add it here is a Brain whose version stops moving
     * when that file changes, so this list is part of what a Brain is, not an optimisation.
     */
    private static List<String> sourceOf(String name) {
        if (RANDOM.equals(named(name))) {
            return List.of("shatterfish/harness/src/main/java/org/shatterfish/harness/agent/RandomAgent.java",
                    "shatterfish/rig/src/main/java/org/shatterfish/rig/Brains.java");
        }
        if (TWIN.equals(name)) {
            return List.of("shatterfish/harness/src/main/java/org/shatterfish/harness/agent/RandomAgent.java",
                    "shatterfish/rig/src/main/java/org/shatterfish/rig/Brains.java");
        }
        if (WITHHELD.containsKey(name)) {
            return List.of("shatterfish/harness/src/main/java/org/shatterfish/harness/agent/WithholdingAgent.java",
                    "shatterfish/rig/src/main/java/org/shatterfish/rig/Brains.java");
        }
        throw new IllegalStateException("the Brain " + name + " has not said which files decide"
                + " what it does, and FR-20's budget is counted per Brain version");
    }

    /**
     * The commit that last changed this Brain, which is the version FR-20's budget is counted per.
     *
     * <p>Empty when git cannot say — a source export, a shallow clone. A caller that needs the
     * answer to enforce something should refuse on empty rather than carry on, and the one that
     * does is {@link Registrations#refusal}.
     */
    public static String version(Path root, String name) {
        List<String> command = new ArrayList<>(List.of("git", "log", "-1", "--format=%H", "--"));
        command.addAll(sourceOf(name));
        try {
            Process process = new ProcessBuilder(command).directory(root.toFile()).start();
            String said = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            process.getErrorStream().readAllBytes();
            return process.waitFor() == 0 && said.strip().matches("[0-9a-f]{40}") ? said.strip() : "";
        } catch (IOException | RuntimeException cannot) {
            return "";
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            return "";
        }
    }

    private Brains() {
    }

    /** Every name the Rig answers to, in the order it lists them. */
    public static List<String> names() {
        return List.of(RANDOM, NO_DESCEND, NO_REST, NO_ATTACK, TWIN);
    }

    /** Whether the Rig has a Brain of this name. Asking does not build one. */
    public static boolean has(String name) {
        return names().contains(name);
    }

    /** Refuses a name the Rig does not have, saying which it does. */
    public static String named(String name) {
        if (!has(name)) {
            throw new IllegalArgumentException("there is no Brain named " + name + "; the Rig knows " + names());
        }
        return name;
    }

    /**
     * The seed a Decider draws from: a function of the Run's own triple and nothing else.
     *
     * <p>Never the salt — see the note on this class. The triple is what a human at the same screen
     * has, so a Decider seeded from it learns nothing it could not have, and the Decider's stream
     * is reproducible from the tuple without recording a second number.
     */
    public static long agentSeed(SeedSet.Entry triple) {
        if (triple == null) {
            throw new IllegalArgumentException("a Decider is seeded from the triple it plays");
        }
        return Mix.mix(Mix.mix(triple.seed(), triple.heroClass().ordinal()), triple.challengeFlags());
    }

    /**
     * The seed the Decider named {@code name} draws from on {@code triple}: the triple's own for
     * every Brain but the twin, whose stream is the triple's mixed with {@link #TWIN_STREAM}.
     */
    static long seedOf(String name, SeedSet.Entry triple) {
        return TWIN.equals(named(name)) ? Mix.mix(agentSeed(triple), TWIN_STREAM) : agentSeed(triple);
    }

    /**
     * The Decider named {@code name}, seeded from the triple it will play, refusing a name the Rig
     * does not have.
     */
    public static Decider of(String name, SeedSet.Entry triple) {
        named(name);
        if (RANDOM.equals(name)) {
            return new RandomAgent(agentSeed(triple));
        }
        if (TWIN.equals(name)) {
            return new RandomAgent(seedOf(name, triple));
        }
        if (WITHHELD.containsKey(name)) {
            return new WithholdingAgent(agentSeed(triple), WITHHELD.get(name));
        }
        throw new IllegalStateException("the Rig names the Brain " + name + " and cannot build one");
    }

    /**
     * What identifies the build of a Brain, for the Run log's header. The Baseline has no source of
     * its own beyond the harness, so its commit is the Shatterfish commit the invocation states and
     * its configuration is the empty one.
     */
    public static String configHash(String name) {
        if (!RANDOM.equals(named(name)) && !WITHHELD.containsKey(name) && !TWIN.equals(name)) {
            // A real Brain states its own configuration. Returning zeros for it would put an
            // unfalsifiable claim in every log header it wrote, and the Registration (story 3.5)
            // is the thing that pins a Brain's configuration -- so this refuses rather than
            // quietly describing nothing.
            throw new IllegalStateException("the Brain " + name + " has not said what its"
                    + " configuration is, and a log header may not claim it has none");
        }
        return "0".repeat(64);
    }
}
