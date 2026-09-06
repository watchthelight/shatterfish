package org.shatterfish.api;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The one encoder of an Observation (ADR-0005, options 8 and 9): a canonical binary form, and the
 * hashes over it. Every record is written in the order of its components, and every component
 * is written, which {@code CodecReflectionTest} holds by varying each one; lists are written in
 * the order their records fix, so shuffling any input list changes nothing, which
 * {@code CodecCanonicalTest} holds.
 *
 * <p>The bytes are: the schema version, then the nine sections in {@link #SECTIONS} order, each
 * as {@link Encoder} writes them. Each section's hash is SHA-256 over that section's bytes alone,
 * and the Observation's hash is SHA-256 over the version followed by the section hashes, so a
 * differential test can name the section that differs. An Action is written as its kind's name
 * followed by its components.
 *
 * <p>The version is the header's, and must be this codec's; it is bumped whenever the encoding of
 * any section changes, and a Replay refuses to compare across versions.
 */
public final class ObservationCodec {

    /** The version of the encoding this codec writes; story 1.6's was 1, story 1.7's nine sections are 2. */
    public static final int SCHEMA_VERSION = 2;

    public static final String HEADER = "header";
    public static final String MAP = "map";
    public static final String ACTORS = "actors";
    public static final String HERO = "hero";
    public static final String INVENTORY = "inventory";
    public static final String JOURNAL = "journal";
    public static final String LOG = "log";
    public static final String ACTIONS = "actions";
    public static final String PROMPT = "prompt";

    /** The sections, in encoding order. */
    public static final List<String> SECTIONS = List.of(HEADER, MAP, ACTORS, HERO, INVENTORY, JOURNAL, LOG, ACTIONS, PROMPT);

    /**
     * The width of the bar {@link #healthPips} quantises to, as a fraction of world units: a
     * sixteen-unit sprite's bar at camera zoom 1, the sprite's width times four sixths
     * ({@code core/.../ui/CharHealthIndicator.java:50-51}). The bar is drawn to the pixel of the
     * zoom, the lit part rounded up ({@code core/.../ui/HealthBar.java:66-69}), so what a player
     * sees over a sprite depends on the sprite's width, ten to twenty-seven units at the tag, and
     * on the zoom, which the player can set from 1 ({@code core/.../scenes/PixelScene.java:144}).
     * The constant is a convention, then, and a fair one by another window: examining a character
     * shows a bar about a hundred UI units wide at a UI zoom of at least two
     * ({@code core/.../windows/WndInfoMob.java:58-59}, {@code :72}, {@code :77};
     * {@code core/.../windows/WndTitledMessage.java:32}; {@code PixelScene.java:133-137},
     * {@code :150}), which resolves every point of health of every character that is not a boss,
     * and a boss's bar prints its health as a number ({@code core/.../ui/BossHealthBar.java:205-206}).
     * Eleven pips are coarser than either; the schema keeps ADR-0006's quantisation, which is a
     * loss to the brain and never a leak.
     */
    public static final int HEALTH_BAR_WIDTH_NUMERATOR = 32;
    public static final int HEALTH_BAR_WIDTH_DENOMINATOR = 3;

    /** The most pips a health bar shows: {@code ceil(32 / 3)}. */
    public static final int MAX_HEALTH_PIPS = 11;

    private ObservationCodec() {
    }

    /**
     * A character's health as its bar shows it: {@code ceil(hp / max * W)} lit pixels of a bar
     * {@code W} pixels wide, in integers only.
     *
     * @param hp the health, from 0 to {@code max}
     * @param max what the bar is full at, the greater of the maximum health and the health plus shielding
     */
    public static int healthPips(int hp, int max) {
        Canon.require(max > 0, "a health bar is full at more than nothing: " + max);
        Canon.require(hp >= 0 && hp <= max, "health is between 0 and " + max + ": " + hp);
        long scaled = (long) hp * HEALTH_BAR_WIDTH_NUMERATOR;
        long unit = (long) max * HEALTH_BAR_WIDTH_DENOMINATOR;
        return (int) ((scaled + unit - 1) / unit);
    }

    /** The canonical bytes of the whole Observation: the version, then every section. */
    public static byte[] encode(Observation observation) {
        checkVersion(observation);
        Encoder out = new Encoder();
        out.int32(observation.header().version());
        for (String section : SECTIONS) {
            writeSection(out, observation, section);
        }
        return out.toByteArray();
    }

    /** The canonical bytes of one section, without the version. */
    public static byte[] encodeSection(Observation observation, String section) {
        checkVersion(observation);
        Encoder out = new Encoder();
        writeSection(out, observation, section);
        return out.toByteArray();
    }

    /** SHA-256 of each section's bytes, in section order, in hex. */
    public static Map<String, String> sectionHashes(Observation observation) {
        Map<String, String> hashes = new LinkedHashMap<>();
        for (String section : SECTIONS) {
            hashes.put(section, Sha256.hex(Sha256.digest(encodeSection(observation, section))));
        }
        return Collections.unmodifiableMap(hashes);
    }

    /** SHA-256 over the version and the section hashes, in hex. */
    public static String hash(Observation observation) {
        checkVersion(observation);
        Encoder out = new Encoder();
        out.int32(observation.header().version());
        for (String section : SECTIONS) {
            out.raw(Sha256.digest(encodeSection(observation, section)));
        }
        return Sha256.hex(Sha256.digest(out.toByteArray()));
    }

    /**
     * The canonical bytes of any record of the schema on its own, for the test that varies each
     * component and for the order of the actions section; not part of the encoding of an
     * Observation.
     */
    static byte[] encodeValue(Object value) {
        Encoder out = new Encoder();
        if (value instanceof HeaderSection header) {
            write(out, header);
        } else if (value instanceof MapSection map) {
            write(out, map);
        } else if (value instanceof ActorsSection actors) {
            write(out, actors);
        } else if (value instanceof HeroSection hero) {
            write(out, hero);
        } else if (value instanceof InventorySection inventory) {
            write(out, inventory);
        } else if (value instanceof JournalSection journal) {
            write(out, journal);
        } else if (value instanceof LogSection log) {
            write(out, log);
        } else if (value instanceof ActionsSection actions) {
            write(out, actions);
        } else if (value instanceof PromptSection prompt) {
            write(out, prompt);
        } else if (value instanceof TrapView trap) {
            write(out, trap);
        } else if (value instanceof HeapView heap) {
            write(out, heap);
        } else if (value instanceof BlobCell blob) {
            write(out, blob);
        } else if (value instanceof TransitionView transition) {
            write(out, transition);
        } else if (value instanceof ActorView actor) {
            write(out, actor);
        } else if (value instanceof BuffView buff) {
            write(out, buff);
        } else if (value instanceof TalentView talent) {
            write(out, talent);
        } else if (value instanceof QuickslotView quickslot) {
            write(out, quickslot);
        } else if (value instanceof ItemView item) {
            write(out, item);
        } else if (value instanceof NoteView note) {
            write(out, note);
        } else if (value instanceof KnownAppearance known) {
            write(out, known);
        } else if (value instanceof LogLine line) {
            write(out, line);
        } else if (value instanceof ItemRef ref) {
            write(out, ref);
        } else if (value instanceof Action action) {
            write(out, action);
        } else if (value instanceof Observation observation) {
            return encode(observation);
        } else {
            throw new IllegalArgumentException("not a record of the schema: " + value);
        }
        return out.toByteArray();
    }

    private static void checkVersion(Observation observation) {
        Canon.require(observation.header().version() == SCHEMA_VERSION,
                "this codec writes schema version " + SCHEMA_VERSION + ", not " + observation.header().version()
                        + "; a Replay refuses to compare across versions");
    }

    private static void writeSection(Encoder out, Observation observation, String section) {
        switch (section) {
            case HEADER -> write(out, observation.header());
            case MAP -> write(out, observation.map());
            case ACTORS -> write(out, observation.actors());
            case HERO -> write(out, observation.hero());
            case INVENTORY -> write(out, observation.inventory());
            case JOURNAL -> write(out, observation.journal());
            case LOG -> write(out, observation.log());
            case ACTIONS -> write(out, observation.actions());
            case PROMPT -> write(out, observation.prompt());
            default -> throw new IllegalArgumentException("no section named " + section + "; the sections are " + SECTIONS);
        }
    }

    private static void write(Encoder out, HeaderSection header) {
        out.int32(header.version());
        out.string(header.upstreamTag());
        out.string(header.codexVersion());
        out.name(header.heroClass());
        out.count(header.challenges().size());
        for (Challenge challenge : header.challenges()) {
            out.name(challenge);
        }
        out.int32(header.depth());
        out.int32(header.branch());
        out.bool(header.sealed());
        out.bool(header.oracle());
        out.name(header.prompt());
    }

    private static void write(Encoder out, MapSection map) {
        out.int32(map.width());
        out.int32(map.height());
        out.count(map.tiles().size());
        for (Tile tile : map.tiles()) {
            out.name(tile);
        }
        out.count(map.fog().size());
        for (Fog fog : map.fog()) {
            out.name(fog);
        }
        out.count(map.traps().size());
        for (TrapView trap : map.traps()) {
            write(out, trap);
        }
        out.count(map.heaps().size());
        for (HeapView heap : map.heaps()) {
            write(out, heap);
        }
        out.count(map.blobs().size());
        for (BlobCell blob : map.blobs()) {
            write(out, blob);
        }
        out.name(map.feeling());
        out.count(map.transitions().size());
        for (TransitionView transition : map.transitions()) {
            write(out, transition);
        }
    }

    private static void write(Encoder out, TrapView trap) {
        out.int32(trap.cell());
        out.string(trap.kind());
        out.bool(trap.active());
    }

    private static void write(Encoder out, HeapView heap) {
        out.int32(heap.cell());
        out.name(heap.kind());
        out.bool(heap.hidden());
        out.string(heap.item());
        out.int32(heap.price());
        out.string(heap.category());
    }

    private static void write(Encoder out, BlobCell blob) {
        out.int32(blob.cell());
        out.count(blob.kinds().size());
        for (String kind : blob.kinds()) {
            out.string(kind);
        }
    }

    private static void write(Encoder out, TransitionView transition) {
        out.int32(transition.cell());
        out.name(transition.kind());
    }

    private static void write(Encoder out, ActorsSection actors) {
        out.count(actors.actors().size());
        for (ActorView actor : actors.actors()) {
            write(out, actor);
        }
    }

    private static void write(Encoder out, ActorView actor) {
        out.int32(actor.cell());
        out.string(actor.name());
        out.name(actor.alignment());
        out.int32(actor.healthPips());
        out.bool(actor.invisible());
        out.name(actor.emote());
        out.count(actor.buffs().size());
        for (BuffView buff : actor.buffs()) {
            write(out, buff);
        }
    }

    private static void write(Encoder out, BuffView buff) {
        out.string(buff.name());
        out.bool(buff.timed());
        out.int32(buff.turnsHundredths());
    }

    private static void write(Encoder out, HeroSection hero) {
        out.int32(hero.cell());
        out.string(hero.name());
        out.name(hero.subclass());
        out.string(hero.ability());
        out.int32(hero.level());
        out.int32(hero.exp());
        out.int32(hero.expToLevel());
        out.int32(hero.hp());
        out.int32(hero.ht());
        out.int32(hero.shield());
        out.int32(hero.strength());
        out.int32(hero.strengthBonus());
        out.int32(hero.gold());
        out.int32(hero.energy());
        out.name(hero.hunger());
        out.count(hero.buffs().size());
        for (BuffView buff : hero.buffs()) {
            write(out, buff);
        }
        out.count(hero.talents().size());
        for (TalentView talent : hero.talents()) {
            write(out, talent);
        }
        out.count(hero.talentPointsAvailable().size());
        for (int points : hero.talentPointsAvailable()) {
            out.int32(points);
        }
        out.count(hero.quickslots().size());
        for (QuickslotView quickslot : hero.quickslots()) {
            write(out, quickslot);
        }
    }

    private static void write(Encoder out, TalentView talent) {
        out.int32(talent.tier());
        out.string(talent.name());
        out.int32(talent.points());
    }

    private static void write(Encoder out, QuickslotView quickslot) {
        out.string(quickslot.item());
        out.bool(quickslot.placeholder());
    }

    private static void write(Encoder out, InventorySection inventory) {
        out.count(inventory.items().size());
        for (ItemView item : inventory.items()) {
            write(out, item);
        }
    }

    private static void write(Encoder out, ItemView item) {
        out.name(item.kind());
        out.string(item.name());
        out.int32(item.quantity());
        out.bool(item.levelKnown());
        out.int32(item.visiblyUpgraded());
        out.bool(item.cursedKnown());
        out.bool(item.visiblyCursed());
        out.string(item.status());
        out.name(item.slot());
        out.count(item.actions().size());
        for (String action : item.actions()) {
            out.string(action);
        }
        out.string(item.defaultAction());
    }

    private static void write(Encoder out, JournalSection journal) {
        out.count(journal.notes().size());
        for (NoteView note : journal.notes()) {
            write(out, note);
        }
        out.count(journal.known().size());
        for (KnownAppearance known : journal.known()) {
            write(out, known);
        }
    }

    private static void write(Encoder out, NoteView note) {
        out.name(note.kind());
        out.int32(note.depth());
        out.string(note.title());
        out.string(note.text());
        out.int32(note.quantity());
    }

    private static void write(Encoder out, KnownAppearance known) {
        out.name(known.kind());
        out.string(known.name());
    }

    private static void write(Encoder out, LogSection log) {
        out.count(log.lines().size());
        for (LogLine line : log.lines()) {
            write(out, line);
        }
    }

    private static void write(Encoder out, LogLine line) {
        out.name(line.tone());
        out.string(line.text());
    }

    private static void write(Encoder out, ActionsSection actions) {
        out.count(actions.actions().size());
        for (Action action : actions.actions()) {
            write(out, action);
        }
    }

    private static void write(Encoder out, ItemRef ref) {
        out.int32(ref.index());
        out.string(ref.name());
        out.int32(ref.quantity());
    }

    private static void write(Encoder out, Action action) {
        out.string(action.kind());
        if (action instanceof Action.Step step) {
            out.int32(step.cell());
        } else if (action instanceof Action.MoveTo move) {
            out.int32(move.cell());
        } else if (action instanceof Action.Attack attack) {
            out.int32(attack.cell());
        } else if (action instanceof Action.Interact interact) {
            out.int32(interact.cell());
        } else if (action instanceof Action.OpenChest chest) {
            out.int32(chest.cell());
        } else if (action instanceof Action.Buy buy) {
            out.int32(buy.cell());
        } else if (action instanceof Action.Unlock unlock) {
            out.int32(unlock.cell());
        } else if (action instanceof Action.UseItem use) {
            write(out, use.item());
            out.string(use.action());
        } else if (action instanceof Action.UseItemAt use) {
            write(out, use.item());
            out.string(use.action());
            out.int32(use.cell());
        } else if (action instanceof Action.UseItemOn use) {
            write(out, use.item());
            out.string(use.action());
            write(out, use.target());
        } else if (action instanceof Action.UseItemOption use) {
            write(out, use.item());
            out.string(use.action());
            out.int32(use.option());
        } else if (action instanceof Action.Rest rest) {
            out.bool(rest.full());
        } else if (action instanceof Action.Talent talent) {
            out.string(talent.talent());
        } else if (action instanceof Action.Ability ability) {
            out.string(ability.ability());
        } else if (action instanceof Action.AbilityAt ability) {
            out.string(ability.ability());
            out.int32(ability.cell());
        } else if (action instanceof Action.AnswerPrompt answer) {
            out.int32(answer.option());
        }
        // PickUp, Descend, Ascend, Search and Wait carry nothing but their kind.
    }

    private static void write(Encoder out, PromptSection prompt) {
        out.name(prompt.kind());
        out.string(prompt.title());
        out.string(prompt.text());
        out.count(prompt.options().size());
        for (String option : prompt.options()) {
            out.string(option);
        }
    }
}
