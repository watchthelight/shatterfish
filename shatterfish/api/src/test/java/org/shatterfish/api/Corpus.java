package org.shatterfish.api;

import java.util.ArrayList;
import java.util.List;

/**
 * A representative Observation for the codec tests: a 6 by 4 floor with every fog level, traps,
 * heaps of every shape the schema allows, blobs, a feeling, two transitions, three actors with
 * buffs, a hero with buffs, talents and quickslots, an inventory of seven items, a journal, a log
 * of every tone and a valid-Action set with every kind but the answer and the human's move; a
 * second Observation with the chasm Prompt open and the two answers as its Actions; and, for the
 * tests that vary every component, a sample of each record and the extra elements a list can take.
 */
final class Corpus {

    static final int WIDTH = 6;
    static final int HEIGHT = 4;

    private Corpus() {
    }

    static HeaderSection header() {
        return new HeaderSection(ObservationCodec.SCHEMA_VERSION, "v3.3.8", "", HeroClass.WARRIOR,
                List.of(Challenge.NO_FOOD, Challenge.DARKNESS), 3, 0, false, false, PromptKind.NONE);
    }

    static HeaderSection promptHeader() {
        HeaderSection h = header();
        return new HeaderSection(h.version(), h.upstreamTag(), h.codexVersion(), h.heroClass(), h.challenges(), h.depth(),
                h.branch(), h.sealed(), h.oracle(), PromptKind.CHASM_JUMP);
    }

    static List<Tile> tiles() {
        return List.of(
                Tile.ENTRANCE, Tile.EMPTY, Tile.EMPTY, Tile.EMPTY, Tile.GRASS, Tile.EMPTY,
                Tile.WATER, Tile.EMPTY, Tile.HIGH_GRASS, Tile.EMPTY_SP, Tile.PEDESTAL, Tile.EMPTY,
                Tile.WALL, Tile.DOOR, Tile.EMPTY, Tile.WALL_DECO, Tile.EMPTY, Tile.EXIT,
                Tile.WALL, Tile.EMPTY, Tile.EMPTY, Tile.NONE, Tile.NONE, Tile.NONE);
    }

    static List<Fog> fog() {
        List<Fog> fog = new ArrayList<>();
        for (int cell = 0; cell < WIDTH * HEIGHT; cell++) {
            fog.add(cell < 12 ? Fog.VISIBLE : cell < 18 ? Fog.VISITED : cell < 21 ? Fog.MAPPED : Fog.UNKNOWN);
        }
        return List.copyOf(fog);
    }

    static List<TrapView> traps() {
        return List.of(new TrapView(2, "Toxic gas trap", true), new TrapView(7, "Alarm trap", false));
    }

    static List<HeapView> heaps() {
        return List.of(
                new HeapView(3, HeapKind.HEAP, false, "Ration of Food", 0, ""),
                new HeapView(9, HeapKind.FOR_SALE, false, "Potion of Healing", 50, ""),
                new HeapView(10, HeapKind.CRYSTAL_CHEST, false, "", 0, "wand"),
                new HeapView(14, HeapKind.CHEST, true, "", 0, ""));
    }

    static List<BlobCell> blobs() {
        return List.of(new BlobCell(5, List.of("Fire")), new BlobCell(6, List.of("Fire", "ToxicGas")));
    }

    static List<TransitionView> transitions() {
        return List.of(new TransitionView(0, TransitionKind.REGULAR_ENTRANCE),
                new TransitionView(17, TransitionKind.REGULAR_EXIT));
    }

    static MapSection map() {
        return new MapSection(WIDTH, HEIGHT, tiles(), fog(), traps(), heaps(), blobs(), Feeling.GRASS, transitions());
    }

    static List<BuffView> ghostBuffs() {
        return List.of(new BuffView("Invisibility", true, 1200), new BuffView("Levitating", false, 0));
    }

    static List<ActorView> actors() {
        return List.of(
                new ActorView(4, "Rat", Alignment.ENEMY, 11, false, Emote.NONE, List.of()),
                new ActorView(8, "Albino rat", Alignment.ENEMY, 6, false, Emote.SLEEP,
                        List.of(new BuffView("Poisoned", true, 350))),
                new ActorView(11, "Ghost", Alignment.NEUTRAL, 11, true, Emote.INVESTIGATE, ghostBuffs()));
    }

    static ActorsSection actorsSection() {
        return new ActorsSection(actors());
    }

    static List<TalentView> talents() {
        return List.of(new TalentView(1, "Hearty Meal", 2), new TalentView(1, "Iron Will", 0),
                new TalentView(1, "Veteran's Intuition", 1), new TalentView(2, "Iron Stomach", 1),
                new TalentView(3, "Cleave", 2));
    }

    static List<QuickslotView> quickslots() {
        return List.of(new QuickslotView("Wand of magic missile", false), new QuickslotView("Turquoise potion", false),
                new QuickslotView("Scroll of magic mapping", true), new QuickslotView("", false),
                new QuickslotView("", false), new QuickslotView("", false));
    }

    static HeroSection hero() {
        return new HeroSection(0, "Bash", HeroSubclass.GLADIATOR, "Heroic Leap", 14, 30, 75, 40, 55, 0, 15, 0, 342, 4,
                Hunger.HUNGRY, List.of(new BuffView("Mind vision", true, 500), new BuffView("Well fed", false, 0)),
                talents(), List.of(0, 1, 0, 0), quickslots());
    }

    static List<ItemView> items() {
        return List.of(
                new ItemView(ItemKind.WEAPON, "Longsword", 1, true, 2, true, false, "", EquipSlot.WEAPON,
                        List.of("DROP", "THROW", "UNEQUIP"), ""),
                new ItemView(ItemKind.ARMOR, "Leather armor", 1, true, 0, true, false, "", EquipSlot.ARMOR,
                        List.of("DROP", "THROW", "UNEQUIP"), ""),
                new ItemView(ItemKind.RING, "Garnet ring", 1, false, 0, false, false, "", EquipSlot.RING,
                        List.of("DROP", "THROW", "UNEQUIP"), ""),
                new ItemView(ItemKind.WAND, "Wand of magic missile", 1, true, 1, true, false, "3/3", EquipSlot.NONE,
                        List.of("DROP", "THROW", "ZAP"), "ZAP"),
                new ItemView(ItemKind.POTION, "Turquoise potion", 2, false, 0, false, false, "2", EquipSlot.NONE,
                        List.of("DRINK", "DROP", "THROW"), "DRINK"),
                new ItemView(ItemKind.FOOD, "Ration of food", 1, true, 0, true, false, "", EquipSlot.NONE,
                        List.of("DROP", "EAT", "THROW"), "EAT"),
                new ItemView(ItemKind.SCROLL, "Scroll of upgrade", 1, true, 0, true, false, "", EquipSlot.NONE,
                        List.of("DROP", "READ", "THROW"), "READ"));
    }

    static InventorySection inventory() {
        return new InventorySection(items());
    }

    static ItemRef longsword() {
        return new ItemRef(0, "Longsword", 1);
    }

    static ItemRef wand() {
        return new ItemRef(3, "Wand of magic missile", 1);
    }

    static ItemRef potion() {
        return new ItemRef(4, "Turquoise potion", 2);
    }

    static ItemRef scroll() {
        return new ItemRef(6, "Scroll of upgrade", 1);
    }

    static List<NoteView> notes() {
        return List.of(new NoteView(NoteKind.LANDMARK, 2, "Sacrificial fire", "", 1),
                new NoteView(NoteKind.KEY, 3, "Iron key", "", 2),
                new NoteView(NoteKind.CUSTOM, 3, "Rat king", "Behind the door by the entrance.", 1));
    }

    static List<KnownAppearance> known() {
        return List.of(new KnownAppearance(ItemKind.POTION, "Potion of healing"),
                new KnownAppearance(ItemKind.SCROLL, "Scroll of upgrade"),
                new KnownAppearance(ItemKind.RING, "Ring of might"));
    }

    static JournalSection journal() {
        return new JournalSection(notes(), known());
    }

    static List<LogLine> lines() {
        return List.of(new LogLine(LogTone.PLAIN, "You see a crab."), new LogLine(LogTone.NEGATIVE, "The crab bites you."),
                new LogLine(LogTone.POSITIVE, "You feel your wounds close."), new LogLine(LogTone.WARNING, "You are hungry."),
                new LogLine(LogTone.HIGHLIGHT, "This ring is cursed!"));
    }

    static LogSection log() {
        return new LogSection(lines());
    }

    /** Every kind but the answer and the human's move, each once, all valid against the corpus. */
    static List<Action> actions() {
        return List.of(new Action.Step(1), new Action.Attack(4), new Action.Interact(11),
                new Action.PickUp(), new Action.OpenChest(14), new Action.Buy(9), new Action.Unlock(13),
                new Action.Descend(), new Action.Ascend(), new Action.UseItem(potion(), "DRINK"),
                new Action.UseItemAt(wand(), "ZAP", 4), new Action.UseItemOn(scroll(), "READ", longsword()),
                new Action.Rest(false), new Action.Search(),
                new Action.Talent("Iron Will"), new Action.Ability("Heroic Leap"), new Action.AbilityAt("Heroic Leap", 3),
                new Action.Wait());
    }

    static ActionsSection actionsSection() {
        return new ActionsSection(actions());
    }

    static List<Action> answers() {
        return List.of(new Action.AnswerPrompt(0), new Action.AnswerPrompt(1));
    }

    /** A human's click on a distant cell: an Action of the Run log, never of a valid set. */
    static List<Action> humanActions() {
        return List.of(new Action.MoveTo(17));
    }

    static PromptSection chasmPrompt() {
        return new PromptSection(PromptKind.CHASM_JUMP, "Chasm", "Do you really want to jump into the chasm?",
                List.of("Yes", "No"));
    }

    static Observation observation() {
        return new Observation(header(), map(), actorsSection(), hero(), inventory(), journal(), log(), actionsSection(),
                PromptSection.NONE);
    }

    /** The same screen with the chasm Prompt open: the answers are the only Actions. */
    static Observation promptObservation() {
        return new Observation(promptHeader(), map(), actorsSection(), hero(), inventory(), journal(), log(),
                new ActionsSection(answers()), chasmPrompt());
    }

    /** {@code observation} with the section of {@code section}'s type replaced. */
    static Observation with(Observation observation, Object section) {
        return new Observation(
                section instanceof HeaderSection s ? s : observation.header(),
                section instanceof MapSection s ? s : observation.map(),
                section instanceof ActorsSection s ? s : observation.actors(),
                section instanceof HeroSection s ? s : observation.hero(),
                section instanceof InventorySection s ? s : observation.inventory(),
                section instanceof JournalSection s ? s : observation.journal(),
                section instanceof LogSection s ? s : observation.log(),
                section instanceof ActionsSection s ? s : observation.actions(),
                section instanceof PromptSection s ? s : observation.prompt());
    }

    /** The corpus's instances of each record of the schema, every shape the corpus has. */
    static List<Object> samples(Class<?> record) {
        if (record == Observation.class) {
            return List.of(observation(), promptObservation());
        } else if (record == HeaderSection.class) {
            return List.of(header(), promptHeader());
        } else if (record == MapSection.class) {
            return List.of(map());
        } else if (record == ActorsSection.class) {
            return List.of(actorsSection());
        } else if (record == HeroSection.class) {
            return List.of(hero());
        } else if (record == InventorySection.class) {
            return List.of(inventory());
        } else if (record == JournalSection.class) {
            return List.of(journal());
        } else if (record == LogSection.class) {
            return List.of(log());
        } else if (record == ActionsSection.class) {
            return List.of(actionsSection(), new ActionsSection(answers()));
        } else if (record == PromptSection.class) {
            return List.of(PromptSection.NONE, chasmPrompt());
        } else if (record == TrapView.class) {
            return List.copyOf(traps());
        } else if (record == HeapView.class) {
            return List.copyOf(heaps());
        } else if (record == BlobCell.class) {
            return List.copyOf(blobs());
        } else if (record == TransitionView.class) {
            return List.copyOf(transitions());
        } else if (record == ActorView.class) {
            return List.copyOf(actors());
        } else if (record == BuffView.class) {
            List<Object> buffs = new ArrayList<>(ghostBuffs());
            buffs.add(new BuffView("Poisoned", true, 350));
            return buffs;
        } else if (record == TalentView.class) {
            return List.copyOf(talents());
        } else if (record == QuickslotView.class) {
            return List.copyOf(quickslots());
        } else if (record == ItemView.class) {
            return List.copyOf(items());
        } else if (record == NoteView.class) {
            return List.copyOf(notes());
        } else if (record == KnownAppearance.class) {
            return List.copyOf(known());
        } else if (record == LogLine.class) {
            return List.copyOf(lines());
        } else if (record == ItemRef.class) {
            return List.of(longsword(), wand(), potion(), scroll());
        } else if (Action.class.isAssignableFrom(record)) {
            List<Object> found = new ArrayList<>();
            for (Action action : actions()) {
                if (action.getClass() == record) {
                    found.add(action);
                }
            }
            for (Action action : answers()) {
                if (action.getClass() == record) {
                    found.add(action);
                }
            }
            for (Action action : humanActions()) {
                if (action.getClass() == record) {
                    found.add(action);
                }
            }
            if (!found.isEmpty()) {
                return found;
            }
        }
        throw new IllegalArgumentException("no sample for " + record.getName());
    }

    /** Elements a list of {@code elementType} can gain in the corpus without breaking any record's rules. */
    static List<Object> extras(Class<?> elementType) {
        if (elementType == TrapView.class) {
            return List.of(new TrapView(19, "Blade trap", true));
        } else if (elementType == HeapView.class) {
            return List.of(new HeapView(16, HeapKind.CHEST, false, "", 0, ""));
        } else if (elementType == BlobCell.class) {
            return List.of(new BlobCell(1, List.of("Web")));
        } else if (elementType == TransitionView.class) {
            return List.of(new TransitionView(20, TransitionKind.BRANCH_EXIT));
        } else if (elementType == ActorView.class) {
            return List.of(new ActorView(1, "Sewer snake", Alignment.ENEMY, 3, false, Emote.ALERT, List.of()));
        } else if (elementType == BuffView.class) {
            return List.of(new BuffView("Weakness", true, 100));
        } else if (elementType == TalentView.class) {
            return List.of(new TalentView(2, "Improvised Projectiles", 1));
        } else if (elementType == QuickslotView.class) {
            return List.of(new QuickslotView("Scroll of upgrade", false));
        } else if (elementType == ItemView.class) {
            return List.of(new ItemView(ItemKind.FOOD, "Pasty", 1, true, 0, true, false, "", EquipSlot.NONE,
                    List.of("DROP", "EAT", "THROW"), "EAT"));
        } else if (elementType == NoteView.class) {
            return List.of(new NoteView(NoteKind.LANDMARK, 4, "Shop", "", 1));
        } else if (elementType == KnownAppearance.class) {
            return List.of(new KnownAppearance(ItemKind.SCROLL, "Scroll of identify"));
        } else if (elementType == LogLine.class) {
            return List.of(new LogLine(LogTone.PLAIN, "You hear something die."));
        } else if (elementType == Action.class) {
            return List.of(new Action.Step(7), new Action.Rest(true));
        } else if (elementType == String.class) {
            return List.of("zz");
        } else if (elementType == Integer.class) {
            return List.of(3);
        } else if (elementType.isEnum()) {
            return List.of((Object[]) elementType.getEnumConstants());
        }
        throw new IllegalArgumentException("no extras for " + elementType.getName());
    }
}
