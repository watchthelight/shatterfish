package org.shatterfish.harness.redetermine;

import com.shatteredpixel.shatteredpixeldungeon.Dungeon;
import com.shatteredpixel.shatteredpixeldungeon.actors.buffs.Buff;
import com.shatteredpixel.shatteredpixeldungeon.ui.BuffIndicator;
import com.shatteredpixel.shatteredpixeldungeon.utils.DungeonSeed;
import com.watabou.utils.Reflection;
import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * The rows about the Run rather than the floor: the dungeon seed, the generator's state and the queues of rooms, the
 * counters that remember what has been generated, the quests, the statistics that count the unseen, and the hero's
 * buffs that carry hidden state. Each is a place the true state hides what the player cannot know, and each is replaced
 * by what a fresh Run would hold, or by what the sample says.
 */
final class GameRows {

    private GameRows() {
    }

    /**
     * Hero buffs without an icon that the scrubber keeps: mechanical state a player can derive from what the screen
     * shows (hunger, regeneration, the warrior's shield). Any other buff without an icon is dropped; a new one in the
     * game fails closed, and {@code RedeterminationBuffsTest} lists them.
     */
    static final Set<String> NO_ICON_KEPT = Set.of(
            "com.shatteredpixel.shatteredpixeldungeon.actors.buffs.Regeneration",
            "com.shatteredpixel.shatteredpixeldungeon.actors.buffs.Hunger",
            "com.shatteredpixel.shatteredpixeldungeon.items.BrokenSeal$WarriorShield");

    static final String SET_TRACKER =
            "com.shatteredpixel.shatteredpixeldungeon.items.weapon.missiles.MissileWeapon$UpgradedSetTracker";

    // --- DUNGEON_SEED -------------------------------------------------------------------------------------------------

    /**
     * The seed the copy generates its unvisited floors from is the sample's, and the seed code with it. The copy is
     * marked a daily Run, which the game itself treats as a Run that neither leaves nor finds a hero's remains
     * ({@code Bones.java:64-66}, {@code :155-158}), so a hero who dies in a rollout cannot reach a later Run through
     * the profile. The floors the Run has visited are not the sample's to keep: the floor it stands on stays, and the
     * others are dropped with their files, so a rollout that climbs generates a floor from the sample's seed.
     */
    static void seed(Ctx c) {
        long seed = Math.floorMod(c.sample.dungeonSeed(), DungeonSeed.TOTAL_SEEDS);
        c.game.put("seed", seed);
        c.game.put("custom_seed", DungeonSeed.convertToCode(seed));
        c.game.put("daily", true);
        c.game.put("daily_replay", false);
        JSONArray generated = new JSONArray();
        JSONArray before = c.game.optJSONArray("generated_levels");
        generated.put(c.depth);
        c.game.put("generated_levels", generated);
        c.held(ScrubRow.DUNGEON_SEED, 1);
        c.changed(ScrubRow.DUNGEON_SEED, 1 + (before != null && before.length() > 1 ? 1 : 0));
    }

    // --- GENERATOR_AND_QUEUES --------------------------------------------------------------------------------------------

    static void generator(Ctx c) {
        int changed = 0;
        JSONObject fresh = GameStatics.freshRunState(c.streams.seed("generator", 0));
        List<String> keys = new ArrayList<>(fresh.keySet());
        keys.sort(String::compareTo);
        for (String key : keys) {
            if (!fresh.get(key).toString().equals(String.valueOf(c.game.opt(key)))) {
                changed++;
            }
            c.game.put(key, fresh.get(key));
        }
        JSONObject limited = c.game.optJSONObject("limited_drops");
        if (limited != null) {
            for (String key : new ArrayList<>(limited.keySet())) {
                if (limited.optInt(key) != 0) {
                    changed++;
                }
                limited.put(key, 0);
            }
        }
        JSONObject quests = c.game.optJSONObject("quests");
        if (quests != null) {
            for (String key : new ArrayList<>(quests.keySet())) {
                JSONObject fresher = new JSONObject();
                fresher.put("spawned", false);
                quests.put(key, fresher);
            }
        }
        if (c.game.optDouble("mobs_to_champion", 1) != 1) {
            changed++;
        }
        c.game.put("mobs_to_champion", 1);
        int spawners = 0;
        for (JSONObject mob : Ctx.objects(c.level.optJSONArray("mobs"))) {
            if (Ctx.className(mob).endsWith(".DemonSpawner")) {
                spawners++;
            }
        }
        c.game.put("spawnersAlive", spawners);
        for (String key : new ArrayList<>(c.game.keySet())) {
            if (key.startsWith("flr_expl_")) {
                if (c.game.optInt(key) != 0) {
                    changed++;
                }
                c.game.put(key, 0);
            }
        }
        c.game.put("expl_score", 0);
        c.held(ScrubRow.GENERATOR_AND_QUEUES, 1);
        c.changed(ScrubRow.GENERATOR_AND_QUEUES, changed);
    }

    // --- HERO_HIDDEN_BUFFS -----------------------------------------------------------------------------------------------

    /**
     * Buffs the screen lists carry an icon; the others are state the Observation never shows, and some of it is
     * hidden truth (the set tracker keeps the enchantment of every thrown set the hero has met, including the sets
     * of items still on the floor). A buff with an icon stays; one without stays only if it is on
     * {@link #NO_ICON_KEPT}; the set tracker stays with its enchantments emptied.
     */
    static void heroBuffs(Ctx c) {
        JSONObject hero = c.game.getJSONObject("hero");
        JSONArray buffs = hero.optJSONArray("buffs");
        if (buffs == null) {
            return;
        }
        int held = 0;
        int changed = 0;
        JSONArray kept = new JSONArray();
        for (JSONObject buff : Ctx.objects(buffs)) {
            String name = Ctx.className(buff);
            if (name.equals(SET_TRACKER)) {
                held++;
                boolean hidden = buff.optJSONArray("set_enchants") != null && buff.getJSONArray("set_enchants").length() > 0;
                buff.put("set_enchants", new JSONArray());
                buff.put("set_ids_enchants", new JSONArray());
                if (hidden) {
                    changed++;
                }
                kept.put(buff);
            } else if (NO_ICON_KEPT.contains(name) || iconed(name)) {
                kept.put(buff);
            } else {
                held++;
                changed++;
            }
        }
        hero.put("buffs", kept);
        c.held(ScrubRow.HERO_HIDDEN_BUFFS, held);
        c.changed(ScrubRow.HERO_HIDDEN_BUFFS, changed);
    }

    static boolean iconed(String className) {
        Class<?> cls = ItemRows.classOf(className);
        if (cls == null || !Buff.class.isAssignableFrom(cls)) {
            return false;
        }
        try {
            Object made = Reflection.newInstance(cls);
            if (!(made instanceof Buff buff)) {
                return false;
            }
            // Some icons ask the buff's target (a Char); the hero stands in, as it does for the buff it came from.
            buff.target = Dungeon.hero;
            return buff.icon() != BuffIndicator.NONE;
        } catch (RuntimeException e) {
            // An icon that cannot be read is an icon the screen cannot be relied on to draw: dropped, not kept.
            return false;
        }
    }
}
