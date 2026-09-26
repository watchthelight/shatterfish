package org.shatterfish.harness.agent;

import com.shatteredpixel.shatteredpixeldungeon.Dungeon;
import com.shatteredpixel.shatteredpixeldungeon.actors.Actor;
import com.shatteredpixel.shatteredpixeldungeon.actors.hero.HeroClass;
import com.shatteredpixel.shatteredpixeldungeon.actors.hero.HeroSubClass;
import com.shatteredpixel.shatteredpixeldungeon.actors.mobs.npcs.Shopkeeper;
import com.shatteredpixel.shatteredpixeldungeon.items.TengusMask;
import com.shatteredpixel.shatteredpixeldungeon.items.potions.PotionOfStrength;
import com.shatteredpixel.shatteredpixeldungeon.items.scrolls.ScrollOfUpgrade;
import com.shatteredpixel.shatteredpixeldungeon.items.stones.StoneOfIntuition;
import com.shatteredpixel.shatteredpixeldungeon.scenes.GameScene;
import com.watabou.utils.PathFinder;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.shatterfish.api.Action;
import org.shatterfish.api.Belief;
import org.shatterfish.api.Codex;
import org.shatterfish.api.Decider;
import org.shatterfish.api.ItemRef;
import org.shatterfish.api.ItemView;
import org.shatterfish.api.Observation;
import org.shatterfish.api.PromptKind;
import org.shatterfish.api.Weights;
import org.shatterfish.brain.Brain;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The real Brain answering the windows of story 4.11 in real Runs (story 4.11): a state is planted in
 * the game, the Action that opens the window is handed to the Brain as its own, and from there the
 * Brain plays, through the Run loop and the executor, while the game's state is watched.
 *
 * <p>The opener is handed over as if a Policy had chosen it, because no Policy yet wears a mask, talks
 * to a shopkeeper, reads a known scroll of upgrade or uses a stone of intuition on purpose; what is
 * under test is what the Brain does once the window is open. Paths abbreviate
 * {@code core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/} as {@code …/}.
 */
@Timeout(value = 5, unit = TimeUnit.MINUTES)
class BrainAtTheWindowsTest {

    private static final long SEED = 24_012_345L;

    private static final long SALT = 0x5A17_5A17L;

    /** Enough turns for the window and a little play after it; every Run here ends at this cap or earlier. */
    private static final int CAP = 20;

    /** The committed weights' features (weights/shatterfish.json), which the Evaluation requires by name. */
    private static Weights weights() {
        Map<String, Long> terms = new TreeMap<>(Map.of(
                "act_attack", 0L, "act_descend", 0L, "act_rest_hurt", 0L, "act_search", 0L, "act_wait", 0L,
                "depth", 10000L, "enemies", -3000L, "hp", 10L, "hunger", -5000L, "level", 5000L));
        terms.put("strength", 2000L);
        terms.putAll(Map.of("item", 2000L, "gold", 10L, "turn", -150L, "weapon", 2L, "armor", 4L, "cursed", -10000L));
        return new Weights("shatterfish", 2,
                terms.entrySet().stream().map(term -> new Weights.Term(term.getKey(), term.getValue())).toList());
    }

    /** A Brain on an empty Codex: nothing to believe about identities, which none of these windows needs. */
    private static Brain brain() {
        return new Brain(new Codex.Knowledge(new Codex.Manifest(Codex.VERSION, "v4.0.0", List.of("manifest.json")),
                List.of(), List.of(), List.of()), weights(), 19L);
    }

    /**
     * The Brain driven as its decider drives it -- update, decide, hand over -- except at the waits
     * {@code plant} answers for it, whose Action is handed to the Brain as its own. Every wait's screen
     * and the game state {@code watch} reads there are kept.
     */
    private static final class Driven implements Decider {

        private final Brain brain = brain();
        private final Function<Observation, Action> plant;
        private final Function<Observation, Object> watch;
        private Belief belief;
        final List<Observation> screens = new ArrayList<>();
        final List<Action> actions = new ArrayList<>();
        final List<Object> watched = new ArrayList<>();

        Driven(Function<Observation, Action> plant, Function<Observation, Object> watch) {
            this.plant = plant;
            this.watch = watch;
        }

        @Override
        public Action decide(Observation observation) {
            screens.add(observation);
            watched.add(watch.apply(observation));
            belief = brain.update(observation, belief);
            Action planted = plant.apply(observation);
            Brain.Decided decided = planted != null ? new Brain.Decided(planted, null, List.of(), "")
                    : brain.decide(observation, belief);
            belief = brain.handed(observation, belief, decided);
            actions.add(decided.action());
            return decided.action();
        }

        /** The index of the first screen with a Prompt of {@code kind} open, or -1. */
        int opened(PromptKind kind) {
            for (int i = 0; i < screens.size(); i++) {
                if (screens.get(i).prompt().kind() == kind) {
                    return i;
                }
            }
            return -1;
        }

        /** The index of the first screen after {@code from} with no Prompt open, or -1. */
        int closed(int from) {
            for (int i = from; i < screens.size(); i++) {
                if (screens.get(i).prompt().kind() == PromptKind.NONE) {
                    return i;
                }
            }
            return -1;
        }
    }

    /** The reference to the first item the screen lists by {@code name}. */
    private static ItemRef ref(Observation observation, String name) {
        List<ItemView> items = observation.inventory().items();
        for (int index = 0; index < items.size(); index++) {
            if (items.get(index).name().equals(name)) {
                return new ItemRef(index, name, items.get(index).quantity());
            }
        }
        return null;
    }

    /**
     * A planter that plants {@code state} on the first screen and hands over a search there, then hands
     * over the Action {@code opener} finds on the second screen, then leaves the Brain alone.
     */
    private static Function<Observation, Action> planting(Runnable state, Function<Observation, Action> opener) {
        int[] screen = {0};
        return observation -> {
            int at = screen[0]++;
            if (at == 0) {
                state.run();
                return new Action.Search();
            }
            if (at == 1) {
                Action open = opener.apply(observation);
                assertNotNull(open, "the opener is offered: " + observation.actions().actions());
                assertTrue(observation.actions().actions().contains(open), open + " is offered");
                return open;
            }
            return null;
        };
    }

    @Test
    @DisplayName("the Tengu mask, for every class: the class's subclass is taken within two waits of the window")
    void the_subclass_for_every_class() {
        Map<HeroClass, HeroSubClass> chosen = Map.of(
                HeroClass.WARRIOR, HeroSubClass.BERSERKER, HeroClass.MAGE, HeroSubClass.BATTLEMAGE,
                HeroClass.ROGUE, HeroSubClass.ASSASSIN, HeroClass.HUNTRESS, HeroSubClass.WARDEN,
                HeroClass.DUELIST, HeroSubClass.CHAMPION, HeroClass.CLERIC, HeroSubClass.PALADIN);
        for (HeroClass heroClass : HeroClass.values()) {
            TengusMask[] mask = new TengusMask[1];
            Driven brain = new Driven(planting(() -> {
                mask[0] = new TengusMask();
                assertTrue(mask[0].collect());
            }, observation -> {
                ItemRef ref = ref(observation, mask[0].name());
                return ref == null ? null : new Action.UseItem(ref, "WEAR");
            }), observation -> Dungeon.hero.subClass);
            new RunLoop().play(SEED, heroClass, SALT, brain, CAP);

            int opened = brain.opened(PromptKind.SUBCLASS);
            assertEquals(2, opened, heroClass + ": the mask's window is on the screen after the wear");
            int taken = brain.watched.indexOf(chosen.get(heroClass));
            assertTrue(taken > opened && taken <= opened + 2,
                    heroClass + " takes " + chosen.get(heroClass) + " within two waits: " + brain.watched);
        }
    }

    @Test
    @DisplayName("a talk with the shopkeeper: the Brain leaves, the gold is unchanged, and it does not talk again")
    void the_shopkeeper_is_left() {
        int[] cell = {-1};
        int[] gold = {-1};
        Driven brain = new Driven(planting(() -> {
            int hero = Dungeon.hero.pos;
            for (int offset : PathFinder.NEIGHBOURS8) {
                int at = hero + offset;
                if (cell[0] < 0 && Dungeon.level.passable[at] && Actor.findChar(at) == null
                        && Dungeon.level.heaps.get(at) == null) {
                    cell[0] = at;
                }
            }
            Shopkeeper shopkeeper = new Shopkeeper();
            shopkeeper.pos = cell[0];
            GameScene.add(shopkeeper);
            gold[0] = Dungeon.gold;
        }, observation -> new Action.Interact(cell[0])), observation -> Dungeon.gold);
        new RunLoop().play(SEED, HeroClass.WARRIOR, SALT, brain, CAP);

        int opened = brain.opened(PromptKind.SHOP);
        assertEquals(2, opened, "the shopkeeper's window follows the talk");
        int closed = brain.closed(opened);
        assertTrue(closed > opened && closed <= opened + 2, "and is closed within two waits: " + closed);
        assertEquals(new Action.DismissPrompt(), brain.actions.get(opened), "by leaving");
        for (int i = opened; i < brain.watched.size(); i++) {
            assertEquals(gold[0], brain.watched.get(i), "no gold changed hands");
        }
        assertFalse(brain.actions.subList(opened, brain.actions.size()).contains(new Action.Interact(cell[0])),
                "the talk is never handed over again: " + brain.actions);
    }

    @Test
    @DisplayName("two scrolls of upgrade read onto the weapon upgrade it once: the chained window is a Brain error")
    void two_scrolls_upgrade_once() {
        Driven brain = new Driven(planting(() -> {
            new ScrollOfUpgrade().identify();
            assertTrue(new ScrollOfUpgrade().quantity(2).collect());
        }, observation -> {
            ItemRef scroll = ref(observation, new ScrollOfUpgrade().name());
            ItemRef weapon = ref(observation, Dungeon.hero.belongings.weapon().name());
            return scroll == null || weapon == null ? null : new Action.UseItemOn(scroll, "READ", weapon);
        }), observation -> Dungeon.hero.belongings.weapon().level());
        RunOutcome outcome = new RunLoop().play(SEED, HeroClass.WARRIOR, SALT, brain, CAP);

        assertEquals(RunOutcome.Cause.BRAIN_ERROR, outcome.cause(), outcome.toString());
        assertTrue(outcome.detail().contains("upgrade window opened again"), outcome.detail());
        int opened = brain.opened(PromptKind.UPGRADE);
        assertEquals(2, opened, "the upgrade window follows the read");
        assertEquals(new Action.AnswerPrompt(0), brain.actions.get(opened), "the first is confirmed");
        assertEquals(0, brain.watched.get(opened), "the weapon at +0 when the window opened");
        assertEquals(1, brain.watched.get(brain.watched.size() - 1), "and +1 when the chained window came: one upgrade");
    }

    @Test
    @DisplayName("a stone of intuition on an unknown potion: the Brain, knowing nothing of its odds, leaves within two waits")
    void the_guess_is_left() {
        PotionOfStrength potion = new PotionOfStrength();
        StoneOfIntuition stone = new StoneOfIntuition();
        Driven brain = new Driven(planting(() -> {
            assertTrue(potion.collect());
            assertTrue(stone.collect());
        }, observation -> {
            ItemRef ref = ref(observation, stone.name());
            ItemRef target = ref(observation, potion.name());
            return ref == null || target == null ? null : new Action.UseItemOn(ref, "USE", target);
        }), observation -> potion.isKnown() + "/" + Dungeon.hero.belongings.getItem(StoneOfIntuition.class));
        new RunLoop().play(SEED, HeroClass.WARRIOR, SALT, brain, CAP);

        int opened = brain.opened(PromptKind.GUESS);
        assertEquals(2, opened, "the guess window follows the stone's use");
        int closed = brain.closed(opened);
        assertTrue(closed > opened && closed <= opened + 2, "and is closed within two waits: " + closed);
        assertFalse(potion.isKnown(), "nothing was guessed");
        assertTrue(brain.watched.get(closed).toString().startsWith("false/"), brain.watched.toString());
        assertFalse(brain.watched.get(closed).toString().endsWith("/null"),
                "and the stone is still held: " + brain.watched);
    }
}
