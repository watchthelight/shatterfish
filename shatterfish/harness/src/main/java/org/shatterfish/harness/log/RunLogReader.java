package org.shatterfish.harness.log;

import org.shatterfish.api.Action;
import org.shatterfish.api.HeroClass;
import org.shatterfish.api.ItemRef;
import org.shatterfish.api.PromptKind;
import org.shatterfish.api.RunLog;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A Run log, read back into the records it was written from (story 3.4).
 *
 * <p>{@code api} writes and never reads (story 2.1), so this is where a log becomes records again.
 * It reads the canonical shape and nothing else, through {@link Json}: a line the writer would never
 * have produced is refused naming the line, because a log is a generated file and every deviation
 * is a hand edit or a drift.
 *
 * <p>It does not verify chains. That is {@link RunLogVerifier}, deliberately separate and
 * deliberately built out of the file's own text rather than out of these records — a chain checked
 * by re-rendering what was just parsed would agree with any writer that agreed with itself.
 */
public final class RunLogReader {

    /** A log, as far as it could be read. */
    public record Log(List<RunLog> records, String partial, String unreadable) {

        /** Whether every line was a record this reader understood. */
        public boolean readable() {
            return unreadable.isEmpty();
        }

        /** Whether the last line reached the disk whole. A killed Run's does not. */
        public boolean complete() {
            return partial.isEmpty();
        }

        /** The header, which is the first record of every log. */
        public RunLog.Header header() {
            if (records.isEmpty() || !(records.get(0) instanceof RunLog.Header header)) {
                throw new IllegalStateException("a Run log begins with a header; this one does not");
            }
            return header;
        }

        /** The waits, in the order they were served. */
        public List<RunLog.Wait> waits() {
            List<RunLog.Wait> waits = new ArrayList<>();
            for (RunLog record : records) {
                if (record instanceof RunLog.Wait wait) {
                    waits.add(wait);
                }
            }
            return List.copyOf(waits);
        }

        /** The ending, when the Run reached one. */
        public RunLog.End end() {
            RunLog last = records.isEmpty() ? null : records.get(records.size() - 1);
            return last instanceof RunLog.End end ? end : null;
        }
    }

    private RunLogReader() {
    }

    /** Reads {@code file}. A line this reader cannot make sense of is reported, never thrown. */
    public static Log of(Path file) {
        String text;
        try {
            text = Files.readString(file, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("the Run log " + file + " could not be read", e);
        }
        return of(text);
    }

    /** Reads a log's text. */
    public static Log of(String text) {
        List<RunLog> records = new ArrayList<>();
        int from = 0;
        while (true) {
            int feed = text.indexOf('\n', from);
            if (feed < 0) {
                return new Log(List.copyOf(records), text.substring(from), "");
            }
            String line = text.substring(from, feed);
            // A log fetched over HTTP or checked out with autocrlf carries a carriage return the
            // writer never wrote. The format says line feeds only, and that is worth saying; it is
            // not worth being unable to read the file over.
            if (line.endsWith("\r")) {
                line = line.substring(0, line.length() - 1);
            }
            try {
                records.add(record(line));
            } catch (RuntimeException unreadable) {
                return new Log(List.copyOf(records), "",
                        "line " + (records.size() + 1) + ": " + unreadable.getMessage());
            }
            from = feed + 1;
        }
    }

    /** One line, as the record it was written from. */
    public static RunLog record(String line) {
        Map<String, String> held = Json.object(line);
        String kind = Json.string(Json.required(held, "t", "record"));
        return switch (kind) {
            case "header" -> header(held);
            case "wait" -> wait(held);
            case "prompt" -> prompt(held);
            case "mode" -> new RunLog.Mode(Json.number(Json.required(held, "k", "mode")),
                    Json.string(Json.required(held, "mode", "mode")),
                    Json.string(Json.required(held, "speed", "mode")));
            case "shadow" -> new RunLog.Shadow(Json.number(Json.required(held, "k", "shadow")),
                    decision(Json.required(held, "decision", "shadow")),
                    held.containsKey("skipped") && Json.bool(held.get("skipped")));
            case "note" -> new RunLog.Note(Json.number(Json.required(held, "k", "note")),
                    Json.string(Json.required(held, "text", "note")));
            case "boundary" -> new RunLog.Boundary(Json.number(Json.required(held, "k", "boundary")),
                    salt(Json.string(Json.required(held, "salt", "boundary"))),
                    Json.string(Json.required(held, "chainAt", "boundary")));
            case "unsupported" -> new RunLog.Unsupported(
                    Json.number(Json.required(held, "k", "unsupported")),
                    Json.string(Json.required(held, "input", "unsupported")));
            case "end" -> end(held);
            default -> throw new IllegalArgumentException("the Run log has no record kind " + kind);
        };
    }

    private static RunLog.Header header(Map<String, String> held) {
        Map<String, String> brain = Json.object(Json.required(held, "brain", "header"));
        return new RunLog.Header(
                Json.integer(Json.required(held, "v", "header")),
                Json.string(Json.required(held, "tag", "header")),
                Json.string(Json.required(held, "commit", "header")),
                HeroClass.valueOf(Json.string(Json.required(held, "class", "header"))),
                Json.integer(Json.required(held, "challenges", "header")),
                Json.number(Json.required(held, "seed", "header")),
                Json.string(Json.required(held, "seedcode", "header")),
                salt(Json.string(Json.required(held, "salt", "header"))),
                Json.integer(Json.required(held, "cap", "header")),
                Json.integer(Json.required(held, "profile", "header")),
                Json.integer(Json.required(held, "obsv", "header")),
                Json.integer(Json.required(held, "codex", "header")),
                new RunLog.Brain(Json.string(Json.required(brain, "name", "brain")),
                        Json.string(Json.required(brain, "commit", "brain")),
                        Json.string(Json.required(brain, "config", "brain"))),
                Json.string(Json.required(held, "registration", "header")),
                Json.bool(Json.required(held, "oracle", "header")),
                Json.string(Json.required(held, "machine", "header")),
                Json.string(Json.required(held, "started", "header")),
                held.containsKey("driver") ? Json.string(held.get("driver")) : "",
                held.containsKey("interface") ? Json.integer(held.get("interface")) : -1,
                held.containsKey("controller") ? Json.integer(held.get("controller")) : -1);
    }

    private static RunLog.Wait wait(Map<String, String> held) {
        Map<String, String> sections = new LinkedHashMap<>();
        for (Map.Entry<String, String> section : Json.object(Json.required(held, "sections", "wait")).entrySet()) {
            sections.put(section.getKey(), Json.string(section.getValue()));
        }
        List<Integer> highlights = new ArrayList<>();
        if (held.containsKey("highlights")) {
            for (String cell : Json.array(held.get("highlights"))) {
                highlights.add(Json.integer(cell));
            }
        }
        return new RunLog.Wait(
                Json.number(Json.required(held, "k", "wait")),
                Json.number(Json.required(held, "turn", "wait")),
                Json.integer(Json.required(held, "depth", "wait")),
                Json.integer(Json.required(held, "branch", "wait")),
                Json.string(Json.required(held, "obs", "wait")),
                Map.copyOf(sections),
                action(Json.required(held, "action", "wait")),
                Json.bool(Json.required(held, "applied", "wait")),
                Json.string(Json.required(held, "actor", "wait")),
                held.containsKey("decision") ? decision(held.get("decision")) : null,
                held.containsKey("belief") ? Json.string(held.get("belief")) : "",
                List.copyOf(highlights),
                // The one field the chain leaves out, and the one a Replay must not compare.
                held.containsKey("think_ms") ? Json.number(held.get("think_ms")) : 0);
    }

    private static RunLog.Prompt prompt(Map<String, String> held) {
        return new RunLog.Prompt(Json.number(Json.required(held, "k", "prompt")),
                PromptKind.valueOf(Json.string(Json.required(held, "prompt", "prompt"))),
                action(Json.required(held, "answer", "prompt")));
    }

    private static RunLog.End end(Map<String, String> held) {
        Map<String, String> outcome = Json.object(Json.required(held, "outcome", "end"));
        return new RunLog.End(Json.number(Json.required(held, "k", "end")),
                new RunLog.Outcome(
                        Json.bool(Json.required(outcome, "win", "outcome")),
                        Json.bool(Json.required(outcome, "ascended", "outcome")),
                        Json.number(Json.required(outcome, "score", "outcome")),
                        Json.integer(Json.required(outcome, "depth", "outcome")),
                        Json.number(Json.required(outcome, "turns", "outcome")),
                        Json.string(Json.required(outcome, "cause", "outcome")),
                        Json.integer(Json.required(outcome, "bosses", "outcome"))),
                Json.bool(Json.required(held, "verifiable", "end")),
                held.containsKey("detail") ? Json.string(held.get("detail")) : "");
    }

    private static RunLog.Decision decision(String raw) {
        Map<String, String> held = Json.object(raw);
        List<RunLog.Choice> alternatives = new ArrayList<>();
        for (String choice : Json.array(Json.required(held, "alternatives", "decision"))) {
            alternatives.add(choice(choice));
        }
        List<String> flags = new ArrayList<>();
        for (String flag : Json.array(Json.required(held, "flags", "decision"))) {
            flags.add(Json.string(flag));
        }
        return new RunLog.Decision(Json.string(Json.required(held, "goal", "decision")),
                choice(Json.required(held, "chosen", "decision")), List.copyOf(alternatives),
                List.copyOf(flags), Json.string(Json.required(held, "policy", "decision")));
    }

    private static RunLog.Choice choice(String raw) {
        Map<String, String> held = Json.object(raw);
        return new RunLog.Choice(action(Json.required(held, "action", "choice")),
                Json.number(Json.required(held, "score", "choice")),
                Json.string(Json.required(held, "why", "choice")));
    }

    /** A salt, written as the sixteen lower-case hex digits the file name also carries. */
    private static long salt(String hex) {
        if (!hex.matches("[0-9a-f]{16}")) {
            throw new IllegalArgumentException("a salt is sixteen lower-case hex digits: " + hex);
        }
        return Long.parseUnsignedLong(hex, 16);
    }

    // ------------------------------------------------------------------------------ the Actions

    /**
     * One Action, back from the object the writer wrote.
     *
     * <p>Every kind is named. A kind this build does not have is refused rather than skipped: a
     * Replay that silently dropped an Action would reproduce a different Run and report that it had
     * reproduced the same one, which is the failure this whole story exists to make impossible.
     */
    public static Action action(String raw) {
        Map<String, String> held = Json.object(raw);
        String kind = Json.string(Json.required(held, "kind", "action"));
        return switch (kind) {
            case "Step" -> new Action.Step(cell(held));
            case "MoveTo" -> new Action.MoveTo(cell(held));
            case "Attack" -> new Action.Attack(cell(held));
            case "Interact" -> new Action.Interact(cell(held));
            case "PickUp" -> new Action.PickUp();
            case "OpenChest" -> new Action.OpenChest(cell(held));
            case "Buy" -> new Action.Buy(cell(held));
            case "Unlock" -> new Action.Unlock(cell(held));
            case "Descend" -> new Action.Descend();
            case "Ascend" -> new Action.Ascend();
            case "UseItem" -> new Action.UseItem(item(held, "item"), verb(held));
            case "UseItemAt" -> new Action.UseItemAt(item(held, "item"), verb(held), cell(held));
            case "UseItemOn" -> new Action.UseItemOn(item(held, "item"), verb(held), item(held, "target"));
            case "Rest" -> new Action.Rest(Json.bool(Json.required(held, "full", "Rest")));
            case "Search" -> new Action.Search();
            case "Talent" -> new Action.Talent(Json.string(Json.required(held, "talent", "Talent")));
            case "Ability" -> new Action.Ability(Json.string(Json.required(held, "ability", "Ability")));
            case "AbilityAt" -> new Action.AbilityAt(
                    Json.string(Json.required(held, "ability", "AbilityAt")), cell(held));
            case "AnswerPrompt" -> new Action.AnswerPrompt(
                    Json.integer(Json.required(held, "option", "AnswerPrompt")));
            case "DismissPrompt" -> new Action.DismissPrompt();
            case "Wait" -> new Action.Wait();
            default -> throw new IllegalArgumentException("this build has no Action of kind " + kind
                    + "; a log naming one was written by another build, and replaying it by skipping"
                    + " the Action would reproduce a different Run");
        };
    }

    private static int cell(Map<String, String> held) {
        return Json.integer(Json.required(held, "cell", "action"));
    }

    private static String verb(Map<String, String> held) {
        return Json.string(Json.required(held, "action", "action"));
    }

    private static ItemRef item(Map<String, String> held, String key) {
        Map<String, String> item = Json.object(Json.required(held, key, "action"));
        return new ItemRef(Json.integer(Json.required(item, "index", "item")),
                Json.string(Json.required(item, "name", "item")),
                Json.integer(Json.required(item, "quantity", "item")));
    }
}
