package org.shatterfish.codex;

import org.shatterfish.api.Codex;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * The vocabulary diff (story 2.8): what the two games call things, and where the same words mean
 * different numbers.
 *
 * <p>The key is the words a player sees, not a class name, because the thing this table exists to
 * classify is a sentence someone wrote in a forum post. The two games share almost no class names
 * anyway; the classes are what each row cites. Names are matched without regard to case, since one
 * game writes "Potion of Healing" and the other "potion of healing", and both spellings are kept.
 *
 * <p>A mechanic is compared only where both games state the same kind of fact in the same shape: a
 * mob's health and defence are numbers in both, so a row can say they differ. Everything else — a
 * roll one game writes as a method and the other measures, an item's numbers that one states as
 * constructor arguments — is carried as the text each side states and marked as not comparable.
 * The table's job is to say what each game says, not to decide what a difference means.
 *
 * <p>Nothing reads this table. It is the input the variant classifier of epic 7 will use.
 */
final class Vocabulary {

    static final String FOR = "the input the epic 7 variant classifier will use; nothing reads it yet";

    private Vocabulary() {
    }

    /** Every name either game gives a mob or an item, once, with what each side says about it. */
    static Codex.Vocabulary read(Path root, List<Codex.StringEntry> strings, List<Codex.MobEntry> mobs,
                                 List<Codex.ItemEntry> items) {
        Map<String, Codex.VocabularyEntry> rows = new TreeMap<>();
        Map<String, Codex.MobEntry> byClass = new TreeMap<>();
        for (Codex.MobEntry mob : mobs) {
            byClass.put(mob.className(), mob);
        }
        for (Codex.StringEntry entry : strings) {
            if (!entry.suffix().equals("name") || entry.className().isEmpty() || !entry.className().startsWith("actors.mobs.")) {
                continue;
            }
            Codex.MobEntry mob = byClass.get(entry.className());
            List<Codex.Rule> facts = new ArrayList<>();
            if (mob != null) {
                facts.add(new Codex.Rule("health", String.valueOf(mob.ht()), mob.citation()));
                facts.add(new Codex.Rule("defence", String.valueOf(mob.defenseSkill()), mob.citation()));
                facts.add(new Codex.Rule("damageRoll", mob.damage().expression(), mob.damage().citation()));
                facts.add(new Codex.Rule("dr", mob.dr().expression(), mob.dr().citation()));
            }
            put(rows, "mob", entry.value(), entry.className(), facts, entry.citation(), true);
        }
        for (Codex.ItemEntry item : items) {
            put(rows, "item", item.name(), item.className(), List.of(), item.nameCitation(), true);
        }
        for (Vanilla.Named mob : Vanilla.mobs(root)) {
            put(rows, "mob", mob.name(), mob.className(), mob.facts(), mob.citation(), false);
        }
        for (Vanilla.Named item : Vanilla.items(root)) {
            put(rows, "item", item.name(), item.className(), item.facts(), item.citation(), false);
        }
        List<Codex.VocabularyEntry> entries = new ArrayList<>(rows.values());
        for (int i = 0; i < entries.size(); i++) {
            entries.set(i, compared(entries.get(i)));
        }
        return new Codex.Vocabulary(entries, Upstream.tag(root), Vanilla.tag(root), FOR);
    }

    /** One side of one name, added to the row that name already has or to a row of its own. */
    private static void put(Map<String, Codex.VocabularyEntry> rows, String kind, String name, String className,
                            List<Codex.Rule> facts, Codex.Citation citation, boolean here) {
        String key = kind + " " + Names.lower(name).trim();
        Codex.VocabularyEntry held = rows.get(key);
        Codex.VocabularySide side = new Codex.VocabularySide(name, List.of(className), facts, List.of(citation));
        if (held == null) {
            rows.put(key, new Codex.VocabularyEntry(kind, Names.lower(name).trim(),
                    here ? side : null, here ? null : side, List.of()));
            return;
        }
        Codex.VocabularySide mine = here ? held.here() : held.there();
        rows.put(key, new Codex.VocabularyEntry(held.kind(), held.name(),
                here ? joined(mine, side) : held.here(), here ? held.there() : joined(mine, side), List.of()));
    }

    /** Two classes of one game under one name: both are named, since the row is about the name. */
    private static Codex.VocabularySide joined(Codex.VocabularySide held, Codex.VocabularySide side) {
        if (held == null) {
            return side;
        }
        List<String> classes = new ArrayList<>(held.classNames());
        classes.addAll(side.classNames());
        List<Codex.Citation> citations = new ArrayList<>(held.citations());
        citations.addAll(side.citations());
        List<Codex.Rule> facts = new ArrayList<>(held.facts());
        for (Codex.Rule fact : side.facts()) {
            if (facts.stream().noneMatch(f -> f.what().equals(fact.what()))) {
                facts.add(fact);
            }
        }
        return new Codex.VocabularySide(held.name(), classes, facts, citations);
    }

    /**
     * The differences a row can state: a fact both sides give, where the two differ. A fact only
     * one side gives is not a difference the table judges, and neither is a fact the two write in
     * shapes that cannot be compared — a number against a method's text — which the row says
     * plainly rather than resolving.
     */
    private static Codex.VocabularyEntry compared(Codex.VocabularyEntry entry) {
        if (entry.here() == null || entry.there() == null) {
            return entry;
        }
        List<Codex.MechanicDifference> differences = new ArrayList<>();
        for (Codex.Rule mine : entry.here().facts()) {
            for (Codex.Rule theirs : entry.there().facts()) {
                if (!mine.what().equals(theirs.what())) {
                    continue;
                }
                boolean numbers = mine.expression().matches("-?\\d+") && theirs.expression().matches("-?\\d+");
                if (numbers && mine.expression().equals(theirs.expression())) {
                    continue;
                }
                differences.add(new Codex.MechanicDifference(mine.what(), mine.expression(), theirs.expression(), numbers));
            }
        }
        return new Codex.VocabularyEntry(entry.kind(), entry.name(), entry.here(), entry.there(), differences);
    }
}
