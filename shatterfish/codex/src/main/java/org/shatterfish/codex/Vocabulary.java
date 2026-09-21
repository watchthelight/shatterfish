package org.shatterfish.codex;

import org.shatterfish.api.Codex;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Pattern;

/**
 * The vocabulary diff (story 2.8): what the two games call things, and where the same words mean
 * different numbers.
 *
 * <p>The key is the words a player sees, not a class name, because the thing this table exists to
 * classify is a sentence someone wrote in a forum post. The two games share almost no class names
 * anyway; the classes are what each row cites. Names are matched without regard to case, since one
 * game writes "Potion of Healing" and the other "potion of healing", and both spellings are kept.
 *
 * <p>Both sides are read the same way, by {@link Stated}, from the line of each game's own source
 * that states the number. That symmetry is the whole guarantee: a difference this table reports is
 * a difference between two games rather than between two readers, and a mechanic one game writes
 * as an expression is carried as that expression rather than resolved into a number it never
 * states. A fact is comparable only where both games state a plain integer; everything else the
 * row carries as the text each side writes and judges nothing.
 *
 * <p>What a mob is, is decided by the mob table rather than by a key's spelling: this game names
 * buffs and items inside its mob classes, and a row calling a buff a mob would be a difference
 * invented by the reader.
 *
 * <p>Nothing reads this table. It is the input the variant classifier of epic 7 will use.
 */
final class Vocabulary {

    static final String FOR = "the input the epic 7 variant classifier will use; nothing reads it yet";

    /** This game's own source, read as text so both sides of a row are read the same way. */
    static final String HERE = Sources.SOURCE_ROOT + "com/shatteredpixel/shatteredpixeldungeon/";

    private static final Pattern HEALTH = Pattern.compile("\\bHT\\s*=\\s*([^;]+);");
    private static final Pattern DEFENCE = Pattern.compile("\\bdefenseSkill\\s*=\\s*([^;]+);");
    private static final String MOBS = "actors.mobs.";

    /** What this game calls a roll the other game calls something else. */
    private static final Map<String, String> HERE_ROLLS = Map.of("dr", "drRoll");

    private Vocabulary() {
    }

    /** Every name either game gives a mob or an item, once, with what each side says about it. */
    static Codex.Vocabulary read(Path root, List<Codex.StringEntry> strings, List<Codex.MobEntry> mobs,
                                 List<Codex.ItemEntry> items) {
        Stated here = new Stated(root, HERE);
        Map<String, Codex.MobEntry> byClass = new TreeMap<>();
        for (Codex.MobEntry mob : mobs) {
            byClass.put(mob.className(), mob);
        }
        Map<String, Codex.VocabularyEntry> rows = new TreeMap<>();
        for (Codex.StringEntry entry : strings) {
            if (!entry.suffix().equals("name") || !entry.className().startsWith(MOBS) || !isMob(entry.className(), byClass)) {
                continue;
            }
            put(rows, "mob", entry.value(), entry.className(), facts(here, entry.className(), byClass), entry.citation(), true);
        }
        for (Codex.ItemEntry item : items) {
            put(rows, "item", item.name(), item.className(), List.of(), item.nameCitation(), true);
        }
        for (Vanilla.Named named : Vanilla.named(root)) {
            put(rows, named.kind(), named.name(), named.className(), named.facts(), named.citation(), false);
        }
        List<Codex.VocabularyEntry> entries = new ArrayList<>(rows.values());
        for (int i = 0; i < entries.size(); i++) {
            entries.set(i, compared(entries.get(i)));
        }
        return new Codex.Vocabulary(entries, Upstream.tag(root), Vanilla.tag(root), FOR);
    }

    /**
     * Whether a name this game's strings give under its mobs package names a mob. The mob table is
     * the authority on what a mob is: this game declares buffs and items inside its mob classes,
     * and their names are under the same package prefix. A class the mob table carries, or one
     * whose variants it carries, is a mob; anything else is a name of something that is not.
     */
    private static boolean isMob(String className, Map<String, Codex.MobEntry> byClass) {
        if (byClass.containsKey(className)) {
            return true;
        }
        return byClass.keySet().stream().anyMatch(k -> k.startsWith(className + "."));
    }

    /**
     * What this game states about one of its mobs, read from its own source the way the other
     * game's is read. A number the game does not state at construction is not published: where the
     * mob table says the stats are set later or depend on the run, the class states nothing the
     * table can carry, and a sentinel zero published as a fact would say this game's golden bee
     * has no hit points.
     */
    private static List<Codex.Rule> facts(Stated here, String className, Map<String, Codex.MobEntry> byClass) {
        List<Codex.Rule> facts = new ArrayList<>();
        if (!setLater(className, byClass)) {
            add(facts, stated(here, className, byClass, HEALTH, "health", "ht"));
            add(facts, stated(here, className, byClass, DEFENCE, "defence", "defenseSkill"));
        }
        for (String roll : Vanilla.ROLLS) {
            add(facts, here.method(className, roll, HERE_ROLLS.getOrDefault(roll, roll)));
        }
        return facts;
    }

    /** One number this game states, unless the mob table says that number depends on the run. */
    private static Codex.Rule stated(Stated here, String className, Map<String, Codex.MobEntry> byClass,
                                     Pattern pattern, String what, String field) {
        for (Codex.MobEntry mob : related(className, byClass)) {
            if (mob.runDependent().contains(field)) {
                return null;
            }
        }
        return here.stated(className, pattern, what);
    }

    /** Whether the mob table says this class's stats are set after it is built. */
    private static boolean setLater(String className, Map<String, Codex.MobEntry> byClass) {
        return related(className, byClass).stream().anyMatch(Codex.MobEntry::statsSetLater);
    }

    /** The mob table's entries for a class: the class itself, or the variants declared inside it. */
    private static List<Codex.MobEntry> related(String className, Map<String, Codex.MobEntry> byClass) {
        Codex.MobEntry own = byClass.get(className);
        if (own != null) {
            return List.of(own);
        }
        return byClass.entrySet().stream()
                .filter(e -> e.getKey().startsWith(className + "."))
                .map(Map.Entry::getValue)
                .toList();
    }

    private static void add(List<Codex.Rule> facts, Codex.Rule rule) {
        if (rule != null) {
            facts.add(rule);
        }
    }

    /** One side of one name, added to the row that name already has or to a row of its own. */
    private static void put(Map<String, Codex.VocabularyEntry> rows, String kind, String name, String className,
                            List<Codex.Rule> facts, Codex.Citation citation, boolean here) {
        String key = kind + " " + Names.display(name);
        Codex.VocabularyEntry held = rows.get(key);
        Codex.VocabularySide side = new Codex.VocabularySide(name, List.of(className), facts, List.of(citation));
        if (held == null) {
            rows.put(key, new Codex.VocabularyEntry(kind, Names.display(name),
                    here ? side : null, here ? null : side, List.of()));
            return;
        }
        Codex.VocabularySide mine = here ? held.here() : held.there();
        rows.put(key, new Codex.VocabularyEntry(held.kind(), held.name(),
                here ? joined(mine, side) : held.here(), here ? held.there() : joined(mine, side), List.of()));
    }

    /**
     * Two classes of one game under one name: both are named, since the row is about the name.
     *
     * <p>Where the two state the same mechanic, they have to state it alike. A row can carry one
     * value for a mechanic, and choosing between two classes that disagree would attribute a
     * number to a class that does not state it — a citation pointing at a line saying something
     * else, which is the thing this table exists to stop.
     */
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
            Codex.Rule same = facts.stream().filter(f -> f.what().equals(fact.what())).findFirst().orElse(null);
            if (same == null) {
                facts.add(fact);
            } else if (!same.expression().equals(fact.expression())) {
                throw new IllegalStateException(held.name() + ": " + String.join(" and ", classes)
                        + " state " + fact.what() + " differently (" + same.expression() + ", " + fact.expression() + ")");
            }
        }
        return new Codex.VocabularySide(held.name(), classes, facts, citations);
    }

    /**
     * The differences a row can state: a fact both sides give, where the two differ. A fact only
     * one side gives is not a difference the table judges, and neither is a fact the two write in
     * shapes that cannot be compared — a number against a method's text — which the row says
     * plainly rather than resolving. Two sides that state a mechanic in the same words do not
     * differ, whatever shape those words are in.
     */
    private static Codex.VocabularyEntry compared(Codex.VocabularyEntry entry) {
        if (entry.here() == null || entry.there() == null) {
            return entry;
        }
        List<Codex.MechanicDifference> differences = new ArrayList<>();
        for (Codex.Rule mine : entry.here().facts()) {
            for (Codex.Rule theirs : entry.there().facts()) {
                if (!mine.what().equals(theirs.what()) || mine.expression().equals(theirs.expression())) {
                    continue;
                }
                boolean numbers = mine.expression().matches("-?\\d+") && theirs.expression().matches("-?\\d+");
                differences.add(new Codex.MechanicDifference(mine.what(), mine.expression(), theirs.expression(), numbers));
            }
        }
        return new Codex.VocabularyEntry(entry.kind(), entry.name(), entry.here(), entry.there(), differences);
    }
}
