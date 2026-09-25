package org.shatterfish.brain;

import org.shatterfish.api.Action;
import org.shatterfish.api.ActorView;
import org.shatterfish.api.Hunger;
import org.shatterfish.api.ItemKind;
import org.shatterfish.api.ItemRef;
import org.shatterfish.api.ItemView;
import org.shatterfish.api.Observation;
import org.shatterfish.api.PromptKind;
import org.shatterfish.api.RunLog;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Eat before starving, and never waste food (story 4.9, FR-31).
 *
 * <p>The game never shows the hunger value, only its icon (docs/rules/buffs.md): none below 300,
 * hungry from 300, starving at 450, where the value stops (Hunger.java:96-102, :178-186). Eating
 * lowers the value by the food's energy and never below 0 (Hunger.java:146-148), so energy beyond
 * the hunger is lost. Hence:
 *
 * <ul>
 *   <li>No icon: never eat. The hunger could be anywhere below 300.</li>
 *   <li>Hungry: eat a food of at most 300 energy, the largest such, which the hunger (at least 300)
 *       takes whole. A larger food waits for starving.</li>
 *   <li>Starving: the hunger is exactly 450, so eat the food that wastes least, the largest first.</li>
 * </ul>
 *
 * <p>Mystery meat is eaten last and only while starving: one time in five each it sets the hero
 * alight, roots it, poisons it or slows it (MysteryMeat.java:54-73).
 *
 * <p>Hungry, the Policy acts only on a calm screen (no Prompt open, no enemy in view): eating takes
 * three turns (Food.java:47, :87), which an enemy in view would spend hitting the hero. Starving, it
 * also acts with enemies in view that are not beside the hero and not offered as an Attack, on the
 * waits the fight Policy stands aside (an enemy that cannot move or be reached keeps the screen from
 * ever being calm again). Food is known by the name the inventory shows, from {@link #ENERGY}; a food
 * the table does not name is not eaten.
 */
final class Eat implements Policy {

    /** The Policy's name, as the Decision records it and {@link Brain#policyNames()} lists it. */
    static final String NAME = "eat";

    /** The hunger at which the icon turns hungry, and the energy a hungry hero always takes whole (Hunger.java:40). */
    static final int HUNGRY = 300;

    /** The hunger at which the icon turns starving, where it stops rising (Hunger.java:41, :96-102). */
    static final int STARVING = 450;

    /** The name of the food with side effects, eaten last and only while starving. */
    static final String MYSTERY_MEAT = "mystery meat";

    /**
     * Each food's energy by the name the inventory shows it under. The pasty takes a holiday's name
     * when one is on (Pasty.java:175-198, items.properties:637-645); the lunar new year's steamed
     * fish clears 300 and leaves fish leftovers (Pasty.java:104-120, :231-236).
     */
    static final Map<String, Integer> ENERGY = energies();

    /**
     * The foods the Codex names that the Policy never eats: a raw blandfruit refuses to be eaten and
     * spends no time (Blandfruit.java:106-110), and a blandfruit made into chunks or infused applies
     * the potion it was brewed with (Blandfruit.java:115-118).
     */
    static final Set<String> UNEATEN = Set.of("blandfruit", "blandfruit chunks");

    private static Map<String, Integer> energies() {
        Map<String, Integer> energy = new LinkedHashMap<>();
        energy.put("ration of food", 300);      // Food.java:51
        energy.put("pasty", 450);               // Pasty.java:50
        for (String holiday : List.of("amulet of yendor?", "easter egg", "rainbow potion", "green cake", "pumpkin pie",
                "blue cake", "candy cane", "sparkling potion")) {
            energy.put(holiday, 450);           // Pasty.java:50, under a holiday's name
        }
        energy.put("steamed fish", 300);        // Pasty.java:104-108
        energy.put("fish leftovers", 150);      // Pasty.java:231-236
        energy.put("small food ration", 150);   // SmallRation.java:31
        energy.put("supply ration", 200);       // SupplyRation.java:38
        energy.put("dungeon berry", 100);       // Berry.java:37
        energy.put("meat pie", 900);            // MeatPie.java:37
        energy.put(MYSTERY_MEAT, 150);          // MysteryMeat.java:41
        energy.put("chargrilled meat", 150);    // ChargrilledMeat.java:31
        energy.put("stewed meat", 150);         // StewedMeat.java:32
        energy.put("frozen carpaccio", 150);    // FrozenCarpaccio.java:41
        energy.put("phantom meat", 450);        // PhantomMeat.java:38
        return java.util.Collections.unmodifiableMap(energy);
    }

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public String goal() {
        return "eat: hunger";
    }

    @Override
    public boolean enters(Observation observation, Memory memory) {
        Hunger hunger = observation.hero().hunger();
        return hunger == Hunger.HUNGRY ? Explore.calm(observation)
                : hunger == Hunger.STARVING && observation.header().prompt() == PromptKind.NONE && !pressed(observation);
    }

    /**
     * Whether an enemy stands beside the hero or is offered as an Attack: the three turns of a meal
     * would be free hits. A starving hero with only distant, sleeping or unreachable enemies in view
     * eats when the fight Policy stands aside, since starving turns regeneration off and costs hit
     * points every turn it lasts (Regeneration.java:56, Hunger.java:78-85).
     */
    static boolean pressed(Observation observation) {
        int hero = observation.hero().cell();
        int width = observation.map().width();
        for (ActorView enemy : Fight.enemies(observation)) {
            if (Math.max(Math.abs(hero % width - enemy.cell() % width), Math.abs(hero / width - enemy.cell() / width)) <= 1) {
                return true;
            }
        }
        return observation.actions().actions().stream().anyMatch(action -> action instanceof Action.Attack);
    }

    @Override
    public RunLog.Choice choose(Observation observation, Memory memory, List<Action> offered, Stream stream) {
        List<RunLog.Choice> ranked = ranked(observation, memory, offered, stream);
        return ranked.isEmpty() ? null : ranked.get(0);
    }

    /** A food held, the Action that eats it, and its energy. */
    private record Meal(Action eat, String name, int energy) {
    }

    /**
     * The foods the Policy would eat at the hunger shown, best first: at hungry, those of at most
     * {@link #HUNGRY} energy, largest first; at starving, least wasted first, then largest, mystery
     * meat last. Ties go to the order the inventory lists them.
     */
    @Override
    public List<RunLog.Choice> ranked(Observation observation, Memory memory, List<Action> offered, Stream stream) {
        Hunger hunger = observation.hero().hunger();
        if (hunger == Hunger.NONE) {
            return List.of();
        }
        List<Meal> meals = new ArrayList<>();
        List<ItemView> items = observation.inventory().items();
        for (int index = 0; index < items.size(); index++) {
            ItemView item = items.get(index);
            Integer energy = ENERGY.get(item.name());
            if (item.kind() != ItemKind.FOOD || energy == null) {
                continue;
            }
            Action eat = new Action.UseItem(new ItemRef(index, item.name(), item.quantity()), "EAT");
            if (!offered.contains(eat)) {
                continue;
            }
            boolean fits = hunger == Hunger.STARVING || (energy <= HUNGRY && !item.name().equals(MYSTERY_MEAT));
            if (fits) {
                meals.add(new Meal(eat, item.name(), energy));
            }
        }
        // Stable: equal meals keep the inventory's order.
        meals.sort(Comparator.<Meal>comparingInt(meal -> meal.name().equals(MYSTERY_MEAT) ? 1 : 0)
                .thenComparingInt(meal -> Math.max(0, meal.energy() - STARVING))
                .thenComparing(Comparator.comparingInt(Meal::energy).reversed()));
        List<RunLog.Choice> ranked = new ArrayList<>();
        for (Meal meal : meals) {
            ranked.add(new RunLog.Choice(meal.eat(), ranked.isEmpty() ? Policies.CERTAIN : 0, "eat: " + meal.name()));
        }
        return ranked;
    }
}
