package org.shatterfish.harness.redetermine;

import com.shatteredpixel.shatteredpixeldungeon.actors.Char;
import com.shatteredpixel.shatteredpixeldungeon.actors.buffs.ShieldBuff;
import com.shatteredpixel.shatteredpixeldungeon.actors.mobs.Mimic;
import com.shatteredpixel.shatteredpixeldungeon.actors.mobs.Mob;
import com.shatteredpixel.shatteredpixeldungeon.actors.mobs.npcs.NPC;
import com.shatteredpixel.shatteredpixeldungeon.levels.Terrain;
import com.watabou.utils.Bundle;
import com.watabou.utils.Reflection;
import org.json.JSONArray;
import org.json.JSONObject;
import org.shatterfish.api.ActorView;
import org.shatterfish.api.Alignment;
import org.shatterfish.api.Emote;
import org.shatterfish.api.Fog;
import org.shatterfish.api.HeapKind;
import org.shatterfish.api.HeapView;
import org.shatterfish.api.ObservationCodec;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.SplittableRandom;

/**
 * The rows about mobs. A mob the screen draws is an actor of the Observation at its cell; every other mob is hidden
 * state, so it goes, and the sample's remembered mobs come back on cells the player has visited and left. A mob the
 * screen draws keeps what the screen shows of it (name, alignment, buffs with icons, the bar's pips, the emote) and
 * has the rest re-sampled: the AI state within what its emote allows, the target that state needs, and its exact
 * health within the pips.
 */
final class MobRows {

    private MobRows() {
    }

    static final Set<HeapKind> CHESTS = Set.of(HeapKind.CHEST, HeapKind.LOCKED_CHEST, HeapKind.CRYSTAL_CHEST,
            HeapKind.EBONY_CHEST);

    /** A hidden mimic: the game's own test (Mimic subclass, passive), as the Observer applies it. */
    static boolean hiddenMimic(JSONObject mob) {
        return ItemRows.isA(Ctx.className(mob), Mimic.class) && "PASSIVE".equals(mob.optString("state"));
    }

    static boolean drawnAsChest(Ctx c, JSONObject mob) {
        HeapView heap = c.heapsByCell.get(mob.optInt("pos", -1));
        return heap != null && CHESTS.contains(heap.kind());
    }

    // --- MOBS_OUT_OF_VIEW ---------------------------------------------------------------------------------------------

    static void outOfView(Ctx c) {
        List<JSONObject> mobs = Ctx.objects(c.level.optJSONArray("mobs"));
        List<JSONObject> kept = new ArrayList<>();
        Set<Integer> shown = new HashSet<>();
        int removed = 0;
        for (JSONObject mob : mobs) {
            int pos = mob.optInt("pos", -1);
            boolean visible = c.actorsByCell.containsKey(pos);
            if (visible || (hiddenMimic(mob) && drawnAsChest(c, mob))) {
                kept.add(mob);
                if (visible) {
                    shown.add(pos);
                }
            } else {
                removed++;
            }
        }
        for (ActorView actor : c.observation.actors().actors()) {
            if (!shown.contains(actor.cell())) {
                throw new Scrub.Refused("the Observation shows " + actor.name() + " at cell " + actor.cell()
                        + " and the bundle holds no mob there: the scrub cannot reproduce what the screen draws");
            }
        }
        int added = addRemembered(c, kept);
        kept.sort(Comparator.comparingInt(m -> m.optInt("pos")));
        c.level.put("mobs", Ctx.arrayOf(kept));
        c.level.put("mobs_to_spawn", new JSONArray());
        Set<Integer> keptIds = new HashSet<>();
        for (JSONObject mob : kept) {
            keptIds.add(mob.optInt("id"));
        }
        int hero = c.game.getJSONObject("hero").optInt("id", 1);
        for (JSONObject mob : kept) {
            // A reference to a mob that is gone would name a hidden thing; the hero is always there.
            if (mob.has("enemy_id") && mob.optInt("enemy_id") != hero && !keptIds.contains(mob.optInt("enemy_id"))) {
                mob.remove("enemy_id");
                mob.put("target", -1);
            }
            mob.remove("wander_positions");
            mob.remove("wander_pos_idx");
        }
        c.game.put("nextid", maxId(c.game, c.level) + 1);
        c.held(ScrubRow.MOBS_OUT_OF_VIEW, removed + added);
        c.changed(ScrubRow.MOBS_OUT_OF_VIEW, removed + added);
    }

    private static int addRemembered(Ctx c, List<JSONObject> kept) {
        Set<Integer> occupied = new HashSet<>();
        for (JSONObject mob : kept) {
            occupied.add(mob.optInt("pos"));
        }
        occupied.add(c.game.getJSONObject("hero").optInt("pos"));
        for (HeapView heap : c.observation.map().heaps()) {
            occupied.add(heap.cell());
        }
        int[] terrain = Ctx.ints(c.level.getJSONArray("map"));
        List<ScrubSample.Remembered> sorted = new ArrayList<>(c.sample.remembered());
        sorted.sort(Comparator.comparingInt(ScrubSample.Remembered::cell).thenComparing(ScrubSample.Remembered::mobClass));
        int nextId = maxId(c.game, c.level) + 1;
        int added = 0;
        for (ScrubSample.Remembered remembered : sorted) {
            int cell = remembered.cell();
            if (cell < 0 || cell >= c.length || c.fog[cell] != Fog.VISITED || occupied.contains(cell)) {
                continue;
            }
            int flags = Terrain.flags[terrain[cell]];
            if ((flags & Terrain.PASSABLE) == 0 || (flags & Terrain.AVOID) != 0) {
                continue;
            }
            JSONObject fresh = freshMob(remembered.mobClass(), cell);
            if (fresh == null) {
                continue;
            }
            SplittableRandom random = c.streams.of("remembered-state", cell);
            fresh.put("state", random.nextBoolean() ? "SLEEPING" : "WANDERING");
            fresh.put("id", nextId++);
            occupied.add(cell);
            kept.add(fresh);
            added++;
        }
        return added;
    }

    private static JSONObject freshMob(String className, int cell) {
        Class<?> cls = ItemRows.classOf(className);
        if (cls == null || !Mob.class.isAssignableFrom(cls) || NPC.class.isAssignableFrom(cls)) {
            return null;
        }
        Object made = Reflection.newInstance(cls);
        if (!(made instanceof Mob mob) || mob.properties().contains(Char.Property.BOSS)) {
            return null;
        }
        mob.pos = cell;
        Bundle outer = new Bundle();
        outer.put("mob", mob);
        return new JSONObject(outer.toString()).getJSONObject("mob");
    }

    /** The largest actor id in either bundle: mobs, buffs, blobs, the respawner and the hero all carry one. */
    static int maxId(JSONObject game, JSONObject level) {
        int[] max = {0};
        walkIds(game, max);
        walkIds(level, max);
        return max[0];
    }

    private static void walkIds(Object node, int[] max) {
        if (node instanceof JSONObject object) {
            for (String key : object.keySet()) {
                Object value = object.get(key);
                if (key.equals("id") && value instanceof Integer id) {
                    max[0] = Math.max(max[0], id);
                } else if (value instanceof JSONObject || value instanceof JSONArray) {
                    walkIds(value, max);
                }
            }
        } else if (node instanceof JSONArray array) {
            for (int i = 0; i < array.length(); i++) {
                Object value = array.get(i);
                if (value instanceof JSONObject || value instanceof JSONArray) {
                    walkIds(value, max);
                }
            }
        }
    }

    // --- MOB_AI_AND_HP ---------------------------------------------------------------------------------------------------

    static void aiAndHealth(Ctx c) {
        JSONObject hero = c.game.getJSONObject("hero");
        int heroPos = hero.optInt("pos");
        int heroId = hero.optInt("id", 1);
        int held = 0;
        int changed = 0;
        for (JSONObject mob : Ctx.objects(c.level.optJSONArray("mobs"))) {
            ActorView actor = c.actorsByCell.get(mob.optInt("pos", -1));
            if (actor == null) {
                continue;
            }
            held++;
            JSONObject before = Ctx.copy(mob);
            int pos = mob.optInt("pos");
            SplittableRandom random = c.streams.of("mob", pos);
            if (actor.alignment() == Alignment.ENEMY) {
                state(mob, actor, random, heroPos, heroId);
            }
            health(mob, actor, random);
            if (!before.toString().equals(mob.toString())) {
                changed++;
            }
        }
        c.held(ScrubRow.MOB_AI_AND_HP, held);
        c.changed(ScrubRow.MOB_AI_AND_HP, changed);
    }

    /** The states each emote allows: what the sprite shows is the only thing the screen says about the AI. */
    private static void state(JSONObject mob, ActorView actor, SplittableRandom random, int heroPos, int heroId) {
        String current = mob.optString("state");
        if (!Set.of("SLEEPING", "WANDERING", "INVESTIGATING", "HUNTING").contains(current)) {
            return;
        }
        String state;
        Emote emote = actor.emote();
        switch (emote) {
            case SLEEP -> state = "SLEEPING";
            case ALERT -> state = "HUNTING";
            case INVESTIGATE -> state = "INVESTIGATING";
            case LOST -> state = "WANDERING";
            default -> state = random.nextBoolean() ? "WANDERING" : "HUNTING";
        }
        mob.put("state", state);
        if (state.equals("HUNTING") || state.equals("INVESTIGATING")) {
            mob.put("target", heroPos);
        } else {
            mob.put("target", -1);
        }
        if (state.equals("HUNTING")) {
            mob.put("enemy_id", heroId);
            mob.put("seen", true);
        } else {
            mob.remove("enemy_id");
            mob.put("seen", false);
        }
    }

    private static void health(JSONObject mob, ActorView actor, SplittableRandom random) {
        int ht = mob.optInt("HT");
        if (ht <= 0) {
            return;
        }
        int shield = 0;
        JSONArray buffs = mob.optJSONArray("buffs");
        for (JSONObject buff : Ctx.objects(buffs)) {
            if (ItemRows.isA(Ctx.className(buff), ShieldBuff.class)) {
                shield += buff.optInt("shielding");
            }
        }
        List<Integer> valid = new ArrayList<>();
        for (int hp = 1; hp <= ht; hp++) {
            int max = Math.max(hp + shield, ht);
            if (ObservationCodec.healthPips(Math.max(0, Math.min(hp, max)), max) == actor.healthPips()) {
                valid.add(hp);
            }
        }
        if (valid.isEmpty()) {
            return;
        }
        mob.put("HP", valid.get(random.nextInt(valid.size())));
    }
}
