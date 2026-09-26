package org.shatterfish.api;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/**
 * The Actions an Observation shows to be available (ADR-0014; story 1.12): what a person looking at
 * this screen could do next, computed from the Observation and nothing else. No game state is
 * read, nothing here imports the game, and the same Observation always gives the same set, which is
 * what lets a Brain hold only an Observation (ADR-0003) and what the determinism story rests on.
 *
 * <p>Two things this is not. It is not the executor's judgement: the executor re-validates and
 * rejects with a reason (ADR-0014), because the screen does not always say what the game will
 * allow — a cell drawn as floor can be a decoration the hero cannot enter
 * ({@code core/.../levels/Terrain.java:117-119}), and a locked chest is offered whether or not the
 * right key is in the pack. And it is not every legal input: a targeted item action is offered at
 * each character in view and at the hero's own cell rather than at every cell of the floor, since
 * the set is a menu a Brain chooses from and the cross product of items and cells is thousands of
 * entries per wait. A Brain may construct another target; the Observation checks the parameter and
 * the executor judges the Action.
 *
 * <p>Under a Prompt the only valid Actions are its own options (ADR-0014): a Brain that waits at a
 * Prompt is a Brain error, never a silent stall, so {@link Action.Wait} is absent there.
 */
public final class ValidActions {

    /**
     * The tiles a click walks onto. The game's own rule for the cell a click steps to is
     * {@code passable[cell] || avoid[cell]} ({@code core/.../actors/hero/Hero.java:1823-1826}), so
     * this is every visual whose terrain carries either flag
     * ({@code core/.../levels/Terrain.java:85-128}): the floors, grass, embers, water, a pedestal,
     * the doors that open, the stairs — and the two the game marks avoid rather than passable, the
     * chasm, which asks before the hero jumps ({@code core/.../levels/features/Chasm.java:59-62}),
     * and the well, which is how a person drinks it ({@code core/.../levels/Level.java:1253-1254}).
     * The first draft read the rule as "passable" alone and left the well out, so the bot could
     * never have taken a well; the review of this story found it, and
     * {@code WalkableTableTest} in the harness now holds the table to the game's flags.
     *
     * <p>A tile is a visual and not a terrain, so a few terrains are drawn as a walkable tile and
     * are not walkable: a custom decoration draws as floor and a decoration that keeps water's
     * pass-through draws as water, both solid ({@code Terrain.java:119-120}). A step onto one is
     * offered here and the executor makes the click, which is the same click a person makes on the
     * blacksmith's forge: the game takes it, finds no path, and the hero does not move. Nothing in
     * Shatterfish refuses it, and nothing should — the screen shows floor, so the bot may ask, and
     * the answer is the game's.
     */
    private static final Set<Tile> WALKABLE = EnumSet.of(Tile.EMPTY, Tile.EMPTY_SP, Tile.EMPTY_DECO,
            Tile.GRASS, Tile.HIGH_GRASS, Tile.FURROWED_GRASS, Tile.EMBERS, Tile.WATER, Tile.CHASM,
            Tile.EMPTY_WELL, Tile.WELL, Tile.PEDESTAL, Tile.DOOR, Tile.OPEN_DOOR, Tile.ENTRANCE,
            Tile.ENTRANCE_SP, Tile.EXIT, Tile.UNLOCKED_EXIT);

    /**
     * The tiles a click tries to unlock: the doors and the boss floor's exit
     * ({@code core/.../actors/hero/Hero.java:1984-1989}). A hero-locked door draws as a locked door
     * and is the same input (ADR-0005, {@link Tile}).
     */
    private static final Set<Tile> LOCKED = EnumSet.of(Tile.LOCKED_DOOR, Tile.CRYSTAL_DOOR, Tile.LOCKED_EXIT);

    /** The transitions a descent takes. */
    private static final Set<TransitionKind> DOWN = EnumSet.of(TransitionKind.REGULAR_EXIT, TransitionKind.BRANCH_EXIT);

    /** The transitions an ascent takes; the surface is the way out of the dungeon. */
    private static final Set<TransitionKind> UP = EnumSet.of(TransitionKind.REGULAR_ENTRANCE,
            TransitionKind.BRANCH_ENTRANCE, TransitionKind.SURFACE);

    /**
     * Item actions that always open the cell selector, so the input carries a cell and the plain
     * shape is never right. Each is an identifier whose class reaches {@code GameScene.selectCell}
     * at the tag: a throw ({@code core/.../items/Item.java:71}), a wand's zap
     * ({@code core/.../items/wands/Wand.java:78}), a bomb lit and thrown
     * ({@code core/.../items/bombs/Bomb.java:82}), a spell or the chains cast
     * ({@code core/.../items/spells/TargetedSpell.java:46};
     * {@code core/.../items/artifacts/EtherealChains.java:59}), a weapon's ability
     * ({@code core/.../items/weapon/melee/MeleeWeapon.java:65}), the talisman's scry
     * ({@code core/.../items/artifacts/TalismanOfForesight.java:70}), the armband's steal
     * ({@code core/.../items/artifacts/MasterThievesArmband.java:69}), the key inserted
     * ({@code core/.../items/artifacts/SkeletonKey.java:78}) and the sandals' root
     * ({@code core/.../items/artifacts/SandalsOfNature.java:84}). The identifiers are the ones the
     * item window executes, which is what the inventory section carries (ADR-0006, the Items row).
     */
    private static final Set<String> AT_A_CELL = Set.of("THROW", "ZAP", "LIGHTTHROW", "CAST", "ABILITY",
            "SCRY", "STEAL", "INSERT", "ROOT");

    /**
     * Item actions that can open the bag, so the input may carry another item. Every one of them is
     * an action whose class reaches {@code GameScene.selectItem} at the tag: a scroll read from the
     * pack ({@code core/.../items/scrolls/InventoryScroll.java:39-49}, which is the scrolls of
     * identify, remove curse, transmutation and upgrade), a stone used on an item
     * ({@code core/.../items/stones/InventoryStone.java:55-66}), the seal affixed to armour
     * ({@code core/.../items/BrokenSeal.java}), the staff imbued with a wand
     * ({@code core/.../items/weapon/melee/MagesStaff.java}), the spellbook added to
     * ({@code core/.../items/artifacts/UnstableSpellbook.java}), the sandals fed a seed
     * ({@code core/.../items/artifacts/SandalsOfNature.java}), the horn stored into
     * ({@code core/.../items/artifacts/HornOfPlenty.java}), the shard's identification
     * ({@code core/.../items/trinkets/ShardOfOblivion.java:53}), the resin and the liquid metal
     * applied ({@code core/.../items/ArcaneResin.java:56};
     * {@code core/.../items/LiquidMetal.java}), a glyph inscribed
     * ({@code core/.../items/Stylus.java:45}), a dart tipped
     * ({@code core/.../items/weapon/missiles/darts/Dart.java:66}), a glyph transferred
     * ({@code core/.../items/armor/ClassArmor.java:54}) and the rose outfitted
     * ({@code core/.../items/artifacts/DriedRose.java}).
     *
     * <p>Can, not does: {@code READ} opens the bag for those four scrolls and for the scroll of
     * enchantment ({@code core/.../items/scrolls/exotic/ScrollOfEnchantment.java:45}) and not for
     * any other scroll, and the identifier is all the Observation carries — as is true for the player, who reads an
     * unknown scroll and finds out. So both shapes are offered for these, the plain action and the
     * action on each other item, and the executor takes the one the game asks for (story 1.13). An
     * item action outside both tables is offered plainly; if the game opens a selector for it, the
     * executor reports it rather than guessing, which is the completeness test's business.
     *
     * <p>Story 1.14 narrowed this to the targeted shape alone and its review put it back. The
     * narrowing looked right — pressing an action that opens the bag and stopping there does
     * nothing — but {@code READ} is in this table, so it took reading most of the scroll table away
     * from the bot, and the executor read the scroll and then reported the Action refused. The bug
     * it was meant to fix belongs to the executor, which now cancels a window no Action can answer.
     */
    private static final Set<String> ON_AN_ITEM = Set.of("READ", "USE", "AFFIX", "IMBUE", "ADD", "FEED",
            "STORE", "IDENTIFY", "APPLY", "INSCRIBE", "TIP", "TRANSFER", "OUTFIT");

    /**
     * The Prompts with buttons that the back key also closes (story 4.11). Every window's back key
     * hides it ({@code core/.../ui/Window.java:223-225}); these are the ones a Brain can leave instead
     * of answering:
     * <ul>
     *   <li>a shop: the shopkeeper's window, and the trade window a step onto an item for sale opens,
     *       whose back key leaves the item on its heap and the gold untouched
     *       ({@code core/.../actors/mobs/npcs/Shopkeeper.java:238-308};
     *       {@code core/.../windows/WndTradeItem.java:140-219}, {@code :222-231}). The trade window
     *       draws a buy button, and a steal button beside it when the hero holds the thieves'
     *       armband; leaving takes neither. Not clean everywhere: the trade window opened from the
     *       shopkeeper's sell bag reopens that bag when it closes ({@code WndTradeItem.java:231};
     *       {@code Shopkeeper.java:217-233}), a window no Action answers, which only the shopkeeper's
     *       "sell" option reaches;</li>
     *   <li>the stone of intuition's guess, which consumes nothing until the guess is pressed
     *       ({@code core/.../items/stones/StoneOfIntuition.java:113-142});</li>
     *   <li>the holy tome's spell list, which casts nothing until a spell is pressed
     *       ({@code core/.../windows/WndClericSpells.java:186-206}).</li>
     * </ul>
     * Not the resurrection window, whose back key does nothing
     * ({@code core/.../windows/WndResurrect.java:181-182}), and not the upgrade window, whose back key
     * reopens the item selector behind it ({@code core/.../windows/WndUpgrade.java:497-504}).
     */
    static final Set<PromptKind> CLOSED_BY_BACK = EnumSet.of(PromptKind.SHOP, PromptKind.GUESS, PromptKind.SPELL);

    private ValidActions() {
    }

    /** The Actions this Observation shows to be available, in the section's canonical order. */
    public static ActionsSection of(Observation observation) {
        List<Action> actions = new ArrayList<>();
        if (observation.prompt().kind() != PromptKind.NONE) {
            // A Prompt in front takes the whole wait: its buttons are the only inputs (ADR-0014).
            for (int option = 0; option < observation.prompt().options().size(); option++) {
                actions.add(new Action.AnswerPrompt(option));
            }
            if (observation.prompt().options().isEmpty() || CLOSED_BY_BACK.contains(observation.prompt().kind())) {
                // A Prompt with no buttons is a message, and the one thing a person can do with it
                // is send it away (core/.../ui/Window.java:223-225; story 1.13). A few Prompts with
                // buttons are sent away the same way and nothing follows (story 4.11).
                actions.add(new Action.DismissPrompt());
            }
            return new ActionsSection(actions);
        }
        MapSection map = observation.map();
        HeroSection hero = observation.hero();
        neighbours(map, hero.cell(), observation, actions);
        underfoot(map, hero.cell(), observation.header(), actions);
        items(observation, actions);
        hero(hero, targets(observation), actions);
        // The buttons that need nothing. Resting until something happens is the rest button;
        // waiting one turn is the wait button, which is the same call with the flag down
        // (core/.../ui/Toolbar.java:203, :225), so Rest(false) is not offered beside Wait: one
        // human input, one entry. Story 1.13 found the pair while writing the executor.
        actions.add(new Action.Rest(true));
        actions.add(new Action.Search());
        actions.add(new Action.Wait());
        return new ActionsSection(actions);
    }

    /** A step, an attack or an interaction for each of the eight cells around the hero. */
    private static void neighbours(MapSection map, int cell, Observation observation, List<Action> actions) {
        int width = map.width();
        int x = cell % width;
        int y = cell / width;
        for (int dy = -1; dy <= 1; dy++) {
            for (int dx = -1; dx <= 1; dx++) {
                if (dx == 0 && dy == 0) {
                    continue;
                }
                int at = x + dx;
                int down = y + dy;
                if (at < 0 || at >= width || down < 0 || down >= map.height()) {
                    continue;
                }
                int neighbour = at + down * width;
                ActorView actor = actorAt(observation.actors(), neighbour);
                if (actor != null) {
                    // A click on a character is an attack or an interaction, never a step; the
                    // alignment is the one the screen shows (ADR-0006, the Mobs row).
                    actions.add(actor.alignment() == Alignment.ENEMY
                            ? new Action.Attack(neighbour)
                            : new Action.Interact(neighbour));
                } else if (WALKABLE.contains(map.tiles().get(neighbour))) {
                    actions.add(new Action.Step(neighbour));
                } else if (LOCKED.contains(map.tiles().get(neighbour))) {
                    // A click on a locked door or the boss floor's exit is the unlock input, and
                    // the key in the pack is the game's business (Hero.java:1984-1989).
                    actions.add(new Action.Unlock(neighbour));
                }
            }
        }
    }

    /** What the hero's own cell offers: a heap, and the way down or up. */
    private static void underfoot(MapSection map, int cell, HeaderSection header, List<Action> actions) {
        boolean heapUnderfoot = false;
        for (HeapView heap : map.heaps()) {
            if (heap.cell() != cell) {
                continue;
            }
            // What a click on the heap under the hero does, as the hero decides it
            // (core/.../actors/hero/Hero.java:1965-1982): a plain heap is picked up; a for-sale
            // heap is bought when it is one item with a price and picked up otherwise, which the
            // section says, since the price is carried exactly for a single for-sale item
            // (ADR-0006, the Heaps row); every other kind, a locked chest included, is opened, and
            // whether the key is in the pack is the game's to answer (Hero.java:2445-2463).
            if (heap.kind() == HeapKind.HEAP) {
                actions.add(new Action.PickUp());
            } else if (heap.kind() == HeapKind.FOR_SALE) {
                actions.add(heap.price() > 0 ? new Action.Buy(cell) : new Action.PickUp());
            } else {
                actions.add(new Action.OpenChest(cell));
            }
            heapUnderfoot = true;
        }
        for (TransitionView transition : map.transitions()) {
            if (transition.cell() != cell) {
                continue;
            }
            // The boss lock refuses every transition while the fight is on
            // (core/.../levels/Level.java:181, :645-650), which the header carries as sealed, so
            // the set never offers a descent the game would refuse.
            if (header.sealed()) {
                continue;
            }
            // A heap on the stairs takes the click first. The hero decides by cell in one chain,
            // and the heap branch stands above the transition branch
            // (core/.../actors/hero/Hero.java:1965, :2000); at the hero's own cell the branch's
            // "no enemies in sight" half is satisfied by `cell == pos` whatever else is on the
            // screen, so the heap always wins here. Offering a descent that picks an item up
            // instead would put a Descend in the Run log for a pick-up, which is a Replay that
            // reads as a lie; the item is taken first and the stairs are offered at the next wait.
            if (heapUnderfoot) {
                continue;
            }
            if (DOWN.contains(transition.kind())) {
                actions.add(new Action.Descend());
            } else if (UP.contains(transition.kind())) {
                actions.add(new Action.Ascend());
            }
        }
    }

    /** Every action the item window offers for every item in the pack, in the shape its target has. */
    private static void items(Observation observation, List<Action> actions) {
        List<ItemView> inventory = observation.inventory().items();
        for (int index = 0; index < inventory.size(); index++) {
            ItemView item = inventory.get(index);
            ItemRef ref = new ItemRef(index, item.name(), item.quantity());
            for (String action : item.actions()) {
                if (AT_A_CELL.contains(action)) {
                    for (int target : targets(observation)) {
                        actions.add(new Action.UseItemAt(ref, action, target));
                    }
                } else {
                    // Both shapes, which is the rule the table is written for: these identifiers
                    // *can* open the bag and mostly do not. READ opens one for five scrolls and
                    // for no other, so a set that offered only the targeted shape would have taken
                    // reading a scroll of magic mapping away from the bot entirely — the review of
                    // story 1.14 caught that, after this was narrowed to fix a different bug. The
                    // executor takes whichever shape the game asks for, and cancels a window an
                    // Action cannot answer.
                    actions.add(new Action.UseItem(ref, action));
                    if (ON_AN_ITEM.contains(action)) {
                        for (int other = 0; other < inventory.size(); other++) {
                            if (other != index) {
                                ItemView target = inventory.get(other);
                                actions.add(new Action.UseItemOn(ref, action,
                                        new ItemRef(other, target.name(), target.quantity())));
                            }
                        }
                    }
                }
            }
        }
    }

    /**
     * The cells a targeted item action is offered at: every character in view and the hero's own
     * cell. The floor's other cells are legal targets the game would accept and are left out, as
     * the class comment says.
     */
    private static List<Integer> targets(Observation observation) {
        List<Integer> cells = new ArrayList<>();
        cells.add(observation.hero().cell());
        for (ActorView actor : observation.actors().actors()) {
            cells.add(actor.cell());
        }
        return cells;
    }

    /** The talents with a point to spend and room for it. */
    private static void hero(HeroSection hero, List<Integer> targets, List<Action> actions) {
        for (TalentView talent : hero.talents()) {
            // A tier's stars are the points the pane offers to spend (core/.../ui/TalentsPane.java,
            // the star row); a talent of a tier with none is drawn and not spendable. A talent that
            // already holds its most takes no more: the pane's button spends a point only below it
            // (core/.../ui/TalentButton.java:117-121), and the executor refuses one above, which is a
            // refusal that ends a Run (issue #162).
            if (hero.talentPointsAvailable().get(talent.tier() - 1) > 0 && talent.points() < talent.maxPoints()) {
                actions.add(new Action.Talent(talent.name()));
            }
        }
        // The armour ability is not offered. The Action kinds exist (ADR-0014) and the executor has
        // no path for them yet, so offering them would put in the menu something no executor would
        // apply, which is the one thing a menu must not do: the story that gives the ability its
        // call is the story that adds it here. Story 1.12 offered both shapes and story 1.13's
        // review found the contradiction, the property test never having reached a hero with one.
    }

    private static ActorView actorAt(ActorsSection actors, int cell) {
        for (ActorView actor : actors.actors()) {
            if (actor.cell() == cell) {
                return actor;
            }
        }
        return null;
    }
}
