package org.shatterfish.rig;

import org.shatterfish.api.Action;
import org.shatterfish.api.RunLog;
import org.shatterfish.harness.log.RunLogReader;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * A human's Run beside the Brain's shadow, one line per turn (story 5.9): the wait, the turn, what the
 * person did, what the Brain would have done with its score (and whether its answer came late), whether
 * the two agree, the person's notes, and where a Replay stops. Rendered from the Run log, like the
 * strategy log ({@link StrategyLog}), so there is one source of truth; {@code :rig:strategy} writes it
 * as {@code <run-id>.human.txt} beside every log with a human's waits, and prints it.
 *
 * <p>It counts and scores nothing: an Overlay log is never a Rig number ({@link OverlayLogs}), and this
 * is a debug view of one, as the strategy log is.
 */
public final class HumanPlay {

    /** The file a human log's comparison is written to, beside it. */
    public static final String SUFFIX = ".human.txt";

    private HumanPlay() {
    }

    /** Whether {@code log} holds any wait a human took. */
    public static boolean human(RunLogReader.Log log) {
        return log.waits().stream().anyMatch(wait -> RunLog.HUMAN.equals(wait.actor()));
    }

    /** The comparison, one line per wait, ending with a newline. */
    public static String render(RunLogReader.Log log) {
        if (!log.readable()) {
            throw new IllegalArgumentException("an unreadable Run log has nothing to compare: " + log.unreadable());
        }
        RunLog.Header header = log.header();
        Map<Long, RunLog.Wait> waits = new TreeMap<>();
        Map<Long, RunLog.Shadow> shadows = new LinkedHashMap<>();
        Map<Long, List<String>> notes = new TreeMap<>();
        Map<Long, String> unsupported = new TreeMap<>();
        for (RunLog record : log.records()) {
            switch (record) {
                case RunLog.Wait wait -> waits.put(wait.k(), wait);
                case RunLog.Shadow shadow -> shadows.put(shadow.k(), shadow);
                case RunLog.Note note -> notes.computeIfAbsent(note.k(), k -> new ArrayList<>()).add(note.text());
                case RunLog.Unsupported mark -> unsupported.merge(mark.k(), mark.input(), (a, b) -> a + "; " + b);
                default -> {
                }
            }
        }
        TreeMap<Long, Boolean> keys = new TreeMap<>();
        waits.keySet().forEach(k -> keys.put(k, true));
        unsupported.keySet().forEach(k -> keys.put(k, true));
        notes.keySet().forEach(k -> keys.put(k, true));
        StringBuilder text = new StringBuilder();
        text.append("# ").append(header.runId()).append('\n');
        text.append("# a human's play beside the Brain's shadow; ").append(header.heroClass().name().toLowerCase(java.util.Locale.ROOT))
                .append(", seed ").append(header.seedCode()).append(", tag ").append(header.tag()).append('\n');
        text.append("# wait | turn | you | the Brain would (score) | agree | note\n");
        int agreed = 0;
        int compared = 0;
        for (long k : keys.keySet()) {
            RunLog.Wait wait = waits.get(k);
            RunLog.Shadow shadow = shadows.get(k);
            StringBuilder line = new StringBuilder();
            line.append("k=").append(k);
            line.append(" | turn ").append(wait == null ? "-" : String.valueOf(wait.turn() / 1000));
            line.append(" | you: ").append(wait == null ? "(no Action recorded)" : words(wait.action()));
            if (shadow == null) {
                line.append(" | Brain: (no shadow)");
            } else {
                RunLog.Choice chosen = shadow.decision().chosen();
                line.append(" | Brain: ").append(words(chosen.action())).append(' ')
                        .append(java.math.BigDecimal.valueOf(chosen.score(), 4).toPlainString())
                        .append(shadow.skipped() ? " (late)" : "");
            }
            if (wait != null && shadow != null) {
                boolean same = wait.action().equals(shadow.decision().chosen().action());
                compared++;
                if (same) {
                    agreed++;
                }
                line.append(" | agree ").append(same ? "yes" : "no");
            } else {
                line.append(" | agree -");
            }
            if (notes.containsKey(k)) {
                line.append(" | note: ").append(StrategyLog.clean(String.join(" / ", notes.get(k))));
            }
            if (unsupported.containsKey(k)) {
                line.append(" | replay stops here: ").append(StrategyLog.clean(unsupported.get(k)));
            }
            text.append(line).append('\n');
        }
        text.append("# agreed at ").append(agreed).append(" of ").append(compared).append(" waits with a shadow");
        RunLog.End end = log.end();
        text.append(end == null ? "; no end: the Run was cut short"
                : "; end " + end.outcome().cause() + ", depth " + end.outcome().depth()
                        + (end.verifiable() ? ", replayable" : ", not replayable past the first mark")).append('\n');
        return text.toString();
    }

    /** An Action as a person reads it: its kind in words, and a cell, item or option where it has one. */
    static String words(Action action) {
        return StrategyLog.clean(switch (action) {
            case Action.Step step -> "step to " + step.cell();
            case Action.MoveTo move -> "walk to " + move.cell();
            case Action.Attack attack -> "attack " + attack.cell();
            case Action.Interact interact -> "interact " + interact.cell();
            case Action.PickUp ignored -> "pick up";
            case Action.OpenChest chest -> "open " + chest.cell();
            case Action.Buy buy -> "buy " + buy.cell();
            case Action.Unlock unlock -> "unlock " + unlock.cell();
            case Action.Descend ignored -> "descend";
            case Action.Ascend ignored -> "ascend";
            case Action.UseItem use -> use.action().toLowerCase(java.util.Locale.ROOT) + " " + use.item().name();
            case Action.UseItemAt use -> use.action().toLowerCase(java.util.Locale.ROOT) + " " + use.item().name()
                    + " at " + use.cell();
            case Action.UseItemOn use -> use.action().toLowerCase(java.util.Locale.ROOT) + " " + use.item().name()
                    + " on " + use.target().name();
            case Action.Rest rest -> rest.full() ? "rest" : "wait";
            case Action.Search ignored -> "search";
            case Action.Talent talent -> "talent " + talent.talent();
            case Action.Ability ability -> "ability " + ability.ability();
            case Action.AbilityAt ability -> "ability " + ability.ability() + " at " + ability.cell();
            case Action.AnswerPrompt answer -> "answer option " + answer.option();
            case Action.DismissPrompt ignored -> "dismiss";
            case Action.Wait ignored -> "wait";
        });
    }

    /** Writes the comparison of {@code log} beside it and returns where. */
    static Path written(Path log, String rendered) {
        String name = log.getFileName().toString();
        Path out = log.resolveSibling(name.substring(0, name.length() - ".jsonl".length()) + SUFFIX);
        try {
            Files.writeString(out, rendered, StandardCharsets.UTF_8);
        } catch (IOException cannot) {
            throw new UncheckedIOException(cannot);
        }
        return out;
    }
}
