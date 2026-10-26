package org.shatterfish.harness.redetermine;

import com.shatteredpixel.shatteredpixeldungeon.actors.mobs.Mimic;
import com.shatteredpixel.shatteredpixeldungeon.actors.mobs.Statue;
import com.shatteredpixel.shatteredpixeldungeon.actors.mobs.npcs.Blacksmith;
import com.shatteredpixel.shatteredpixeldungeon.actors.mobs.npcs.Ghost;
import com.shatteredpixel.shatteredpixeldungeon.actors.mobs.npcs.Imp;
import com.shatteredpixel.shatteredpixeldungeon.actors.mobs.npcs.Wandmaker;
import com.shatteredpixel.shatteredpixeldungeon.items.trinkets.Trinket;
import org.json.JSONArray;
import org.json.JSONObject;
import org.shatterfish.api.HeapKind;
import org.shatterfish.api.HeapView;
import org.shatterfish.api.Observation;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * The redetermination scrub of ADR-0009, as a function of the snapshot's bundles, the Observation at its wait and the
 * sample. It names every row of the table in {@link ScrubRow} and applies them in that order; a caller may pick which
 * (the differential test removes one to see its own failure name it).
 *
 * <p>Its inputs are the bundles of the live Run's save, the Observation the copy must reproduce, the cells in the
 * hero's field of view (which the screen's lit region shows), and the sample. It writes the bundles of the copy. It
 * holds no game state of its own and touches the game's statics only through {@link GameStatics#guarded}.
 */
public final class Scrub {

    private Scrub() {
    }

    /** A snapshot the scrub declines to scrub, with the reason; the Run is untouched. */
    public static final class Refused extends IllegalArgumentException {
        private static final long serialVersionUID = 1L;

        public Refused(String message) {
            super(message);
        }
    }

    public record Input(Map<String, byte[]> files, String gameFile, String levelFile, Observation observation, boolean[] fov,
                        int depth, int branch) {
    }

    public record Output(Map<String, byte[]> files, long salt, Report report) {
    }

    /** What each row found and changed, by row. */
    public static final class Report {
        final Map<ScrubRow, Integer> held = new EnumMap<>(ScrubRow.class);
        final Map<ScrubRow, Integer> changed = new EnumMap<>(ScrubRow.class);

        /** How many elements the snapshot held for {@code row} to act on. */
        public int held(ScrubRow row) {
            return held.getOrDefault(row, 0);
        }

        /** How many of them the row changed. */
        public int changed(ScrubRow row) {
            return changed.getOrDefault(row, 0);
        }

        @Override
        public String toString() {
            Map<ScrubRow, String> lines = new LinkedHashMap<>();
            for (ScrubRow row : ScrubRow.values()) {
                lines.put(row, held(row) + "/" + changed(row));
            }
            return "Report" + lines;
        }
    }

    public static Output scrub(Input in, ScrubSample sample) {
        return scrub(in, sample, EnumSet.allOf(ScrubRow.class));
    }

    public static Output scrub(Input in, ScrubSample sample, Set<ScrubRow> rows) {
        return GameStatics.guarded(() -> run(in, sample, rows));
    }

    private static Output run(Input in, ScrubSample sample, Set<ScrubRow> rows) {
        JSONObject game = Ctx.read(in.files().get(in.gameFile()));
        JSONObject levelRoot = Ctx.read(in.files().get(in.levelFile()));
        JSONObject level = levelRoot.getJSONObject("level");
        Ctx c = new Ctx(game, level, in.observation(), in.fov(), in.depth(), sample, rows);
        refuse(c, in);

        for (HeapView heap : c.observation.map().heaps()) {
            if (heap.kind() == HeapKind.TOMB || heap.kind() == HeapKind.REMAINS) {
                throw new Refused("a " + heap.kind() + " is drawn at cell " + heap.cell()
                        + ", and what it holds is hidden state the scrub does not redraw");
            }
        }
        if (c.on(ScrubRow.STEALTHY_MIMIC_KEPT)) {
            c.held(ScrubRow.STEALTHY_MIMIC_KEPT, 1);
        }
        // What the screen never shows goes first: the re-rolls below can refuse, and a refusal must never be decided by an
        // item the copy is about to lose (an unseen heap, a thief out of view).
        if (c.on(ScrubRow.UNSEEN_ITEMS)) {
            ItemRows.unseen(c);
        }
        if (c.on(ScrubRow.MOBS_OUT_OF_VIEW)) {
            MobRows.outOfView(c);
        }
        if (c.on(ScrubRow.POTION_SCROLL_RING_CLASSES)) {
            ItemRows.classes(c);
        }
        if (c.on(ScrubRow.ITEM_LEVEL_AND_CURSE)) {
            ItemRows.levels(c);
        }
        if (c.on(ScrubRow.MOB_AI_AND_HP)) {
            MobRows.aiAndHealth(c);
        }
        CellRows.apply(c);
        if (c.on(ScrubRow.CONTAINER_CONTENTS)) {
            ItemRows.containers(c);
        }
        if (c.on(ScrubRow.HERO_HIDDEN_BUFFS)) {
            GameRows.heroBuffs(c);
        }
        if (c.on(ScrubRow.DUNGEON_SEED)) {
            GameRows.seed(c);
        }
        if (c.on(ScrubRow.GENERATOR_AND_QUEUES)) {
            GameRows.generator(c);
        }
        long salt = Long.MIN_VALUE;
        if (c.on(ScrubRow.ROLLOUT_SALT)) {
            salt = sample.rolloutSalt();
            c.held(ScrubRow.ROLLOUT_SALT, 1);
            c.changed(ScrubRow.ROLLOUT_SALT, 1);
        }

        Map<String, byte[]> files = new LinkedHashMap<>();
        for (Map.Entry<String, byte[]> file : in.files().entrySet()) {
            boolean other = !file.getKey().equals(in.gameFile()) && !file.getKey().equals(in.levelFile());
            if (other && c.on(ScrubRow.DUNGEON_SEED)) {
                continue;
            }
            files.put(file.getKey(), file.getValue());
        }
        files.put(in.gameFile(), Ctx.write(game));
        levelRoot.put("level", level);
        files.put(in.levelFile(), Ctx.write(levelRoot));
        return new Output(files, salt, c.report);
    }

    /** The coverage limits: what the scrub declines rather than scrub wrongly. */
    private static void refuse(Ctx c, Input in) {
        if (in.depth() % 5 == 0 || in.depth() > 25) {
            throw new Refused("depth " + in.depth() + " is a boss floor, or past the last: rollouts there are not scrubbed");
        }
        if (in.branch() != 0) {
            throw new Refused("branch " + in.branch() + " is a side branch: rollouts there are not scrubbed");
        }
        if (c.level.optBoolean("locked")) {
            throw new Refused("the floor is locked: rollouts there are not scrubbed");
        }
        JSONObject quests = c.game.optJSONObject("quests");
        if (quests != null) {
            for (String key : quests.keySet()) {
                JSONObject quest = quests.optJSONObject(key);
                // Only a quest the player has taken up is known to them. That one has been offered, and its rewards are
                // hidden state the scrub does not redraw. That a quest merely spawned on a floor is hidden state, and a
                // refusal on it would be a signal a search could read; GameRows resets it silently (fairness re-review).
                if (quest != null && (quest.optBoolean("given") || quest.optBoolean("started")
                        || quest.optBoolean("completed") || quest.optBoolean("processed"))) {
                    throw new Refused("a quest has been taken up, and its rewards are hidden state the scrub does not redraw");
                }
            }
        }
        if (heldTrinket(c.game.optJSONObject("hero"))) {
            throw new Refused("the hero holds a trinket, whose hidden rolls (a clock, a curse chance) the scrub does not redraw");
        }
        for (JSONObject mob : Ctx.objects(c.level.optJSONArray("mobs"))) {
            if (!c.actorsByCell.containsKey(mob.optInt("pos", -1))) {
                continue;
            }
            String name = Ctx.className(mob);
            if (ItemRows.isA(name, Mimic.class)) {
                throw new Refused("a mimic is in view, and the loot it carries is hidden state the scrub does not redraw");
            }
            if (ItemRows.isA(name, Statue.class)) {
                throw new Refused("a statue is in view, and the weapon it holds is hidden state the scrub does not redraw");
            }
            if (ItemRows.isA(name, Ghost.class) || ItemRows.isA(name, Wandmaker.class) || ItemRows.isA(name, Blacksmith.class)
                    || ItemRows.isA(name, Imp.class)) {
                throw new Refused("a quest giver is in view, and its quest is hidden state the scrub does not redraw");
            }
        }
        if (c.on(ScrubRow.STEALTHY_MIMIC_KEPT)) {
            for (HeapView heap : c.observation.map().heaps()) {
                if (MobRows.CHESTS.contains(heap.kind())) {
                    throw new Refused("a chest is drawn at cell " + heap.cell() + ": it may be a mimic, and a true mimic kept"
                            + " beside the true chests would teach a search which chest bites (audit B2)");
                }
            }
            for (JSONObject mob : Ctx.objects(c.level.optJSONArray("mobs"))) {
                if (MobRows.hiddenMimic(mob) && MobRows.drawnAsChest(c, mob)) {
                    throw new Refused("a hidden mimic is drawn as a chest at cell " + mob.optInt("pos"));
                }
            }
        }
    }

    private static boolean heldTrinket(Object node) {
        if (node instanceof JSONObject object) {
            if (ItemRows.isA(Ctx.className(object), Trinket.class)) {
                return true;
            }
            for (String key : object.keySet()) {
                if (heldTrinket(object.get(key))) {
                    return true;
                }
            }
        } else if (node instanceof JSONArray array) {
            for (int i = 0; i < array.length(); i++) {
                if (heldTrinket(array.get(i))) {
                    return true;
                }
            }
        }
        return false;
    }
}
