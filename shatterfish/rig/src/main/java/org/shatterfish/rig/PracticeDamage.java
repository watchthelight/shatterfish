package org.shatterfish.rig;

import org.shatterfish.harness.log.DebugRecord;
import org.shatterfish.harness.log.Json;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

/**
 * The hit points a practice hero lost on Tengu's floor, split by source (story 10.21, issue #367).
 *
 * <p><b>What it reads.</b> The Run's debug record: one line per hero turn on the sealed floor, with the hero's hit
 * points, its buffs, Tengu's distance, the cells of the smoke bombs, shockers, fire and shock the screen draws
 * (story 10.21) and the game log. Nothing else: the game log does not print a blow or its size (a hit is a number
 * floating over the sprite, not a line), so a blow is told by the screen around it. Between two consecutive
 * lines the loss is the hit points that went, and it is given to a source by what the screen shows at the two
 * lines:
 *
 * <ol>
 *   <li>{@link Source#BOMB}: a smoke bomb drawn at the earlier line within two cells of the hero (the blast
 *       radius) and gone at the later;</li>
 *   <li>{@link Source#SHOCK}: the hero stands, at the later line, beside a shocker or on a cell its blob is drawn
 *       on at either line;</li>
 *   <li>{@link Source#TRAP}: the log of the later line says a dart trap went off;</li>
 *   <li>{@link Source#FIRE}: the hero is burning, or stands on a fire blob;</li>
 *   <li>{@link Source#POISON}: the hero is poisoned;</li>
 *   <li>{@link Source#MELEE} and {@link Source#THROWN}: Tengu is in view and he is beside the hero (melee) or
 *       farther (his thrown attack), at the earlier line.</li>
 * </ol>
 *
 * A turn with more than one candidate is <i>ambiguous</i>: its loss goes to the first source above whose own
 * size can hold it (a burning tick is at most {@link #BURN_TICK} and a poison tick {@link #POISON_TICK}; a blow is
 * more), and the rest to the sources after it, Tengu's last. The table reports the hit points lost in ambiguous turns so that a reader can see how much
 * of the split is a decision. The last wait of a Run that died is followed by the blow that killed it: the hit
 * points the hero had left are the loss of that turn. A source the screen cannot show (a blow from out of view,
 * an effect not yet drawn) falls to {@link Source#UNKNOWN}.
 */
final class PracticeDamage {

    enum Source { MELEE, THROWN, BOMB, FIRE, SHOCK, POISON, TRAP, UNKNOWN }

    /** The most a burning tick takes at depth 10: 1 to 3 + 10/4 (Burning.java:105-117, cited by upstream-reader). */
    static final int BURN_TICK = 5;

    /** The most a poison tick takes from the dart trap's 8: left/3 + 1 (Poison.java:112-115, TenguDartTrap.java:40). */
    static final int POISON_TICK = 3;

    /** The bomb's blast radius in cells (Tengu.java:630), measured as the greater of the column and row distance. */
    static final int BLAST = 2;

    /** The width of a level's map in cells: the debug record gives cells, not columns. */
    static final int WIDTH = 32;

    /** What one Run lost: hit points by source, how many of them in ambiguous turns, and how many it drank back. */
    record Loss(Map<Source, Integer> bySource, int ambiguous, int healed, int turns) {

        int total() {
            return bySource.values().stream().mapToInt(Integer::intValue).sum();
        }
    }

    private record Line(int hp, int cell, boolean burning, boolean poisoned, int bossDist, Set<Integer> bombs,
                        Set<Integer> shock, Set<Integer> shockers, Set<Integer> fire, boolean trap) {
    }

    private PracticeDamage() {
    }

    /** The loss of the Run whose log is {@code log}, from its debug record; empty when there is none. */
    static Loss of(Path log) {
        Path debug = DebugRecord.beside(log);
        List<Line> lines = new ArrayList<>();
        int last = -1;
        if (Files.isRegularFile(debug)) {
            try (Stream<String> all = Files.lines(debug, StandardCharsets.UTF_8)) {
                for (String text : (Iterable<String>) all::iterator) {
                    if (text.contains("\"t\":\"wait\"") && text.contains("\"sealed\":true")) {
                        lines.add(parse(text));
                    } else if (text.contains("\"t\":\"end\"")) {
                        last = 0;
                    }
                }
            } catch (IOException | RuntimeException unreadable) {
                // A record that cannot be read shows no loss.
            }
        }
        return split(lines, last == 0);
    }

    static Loss split(List<Line> lines, boolean died) {
        Map<Source, Integer> by = new EnumMap<>(Source.class);
        for (Source s : Source.values()) {
            by.put(s, 0);
        }
        int ambiguous = 0;
        int healed = 0;
        for (int i = 0; i < lines.size(); i++) {
            Line before = lines.get(i);
            Line after = i + 1 < lines.size() ? lines.get(i + 1) : null;
            int lost;
            if (after == null) {
                if (!died) {
                    break;
                }
                lost = before.hp();
                after = before;
            } else {
                lost = before.hp() - after.hp();
            }
            if (lost < 0) {
                healed -= lost;
                continue;
            }
            if (lost == 0) {
                continue;
            }
            List<Source> candidates = candidates(before, after);
            if (candidates.size() > 1) {
                ambiguous += lost;
            }
            attribute(by, candidates, lost);
        }
        return new Loss(by, ambiguous, healed, lines.size());
    }

    private static void attribute(Map<Source, Integer> by, List<Source> candidates, int lost) {
        if (candidates.isEmpty()) {
            by.merge(Source.UNKNOWN, lost, Integer::sum);
            return;
        }
        int left = lost;
        for (Source source : candidates) {
            boolean last = source == candidates.get(candidates.size() - 1);
            int cap = source == Source.FIRE ? BURN_TICK : source == Source.POISON ? POISON_TICK : Integer.MAX_VALUE;
            int take = last ? left : Math.min(left, cap);
            by.merge(source, take, Integer::sum);
            left -= take;
            if (left == 0) {
                break;
            }
        }
    }

    /** The sources the screen shows for a loss between {@code before} and {@code after}, in the order of the class comment. */
    static List<Source> candidates(Line before, Line after) {
        List<Source> found = new ArrayList<>();
        for (int bomb : before.bombs()) {
            if (!after.bombs().contains(bomb) && near(bomb, after.cell())) {
                found.add(Source.BOMB);
                break;
            }
        }
        boolean beside = false;
        for (int shocker : after.shockers()) {
            beside |= Math.max(Math.abs(shocker % WIDTH - after.cell() % WIDTH),
                    Math.abs(shocker / WIDTH - after.cell() / WIDTH)) <= 1;
        }
        if (beside || after.shock().contains(after.cell()) || before.shock().contains(after.cell())) {
            found.add(Source.SHOCK);
        }
        if (after.trap()) {
            found.add(Source.TRAP);
        }
        if (before.burning() || after.burning() || after.fire().contains(after.cell())) {
            found.add(Source.FIRE);
        }
        if (before.poisoned() || after.poisoned()) {
            found.add(Source.POISON);
        }
        int dist = before.bossDist() >= 0 ? before.bossDist() : after.bossDist();
        if (dist >= 0) {
            found.add(dist <= 1 ? Source.MELEE : Source.THROWN);
        }
        return found;
    }

    private static boolean near(int a, int b) {
        return Math.max(Math.abs(a % WIDTH - b % WIDTH), Math.abs(a / WIDTH - b / WIDTH)) <= BLAST;
    }

    private static Line parse(String text) {
        Map<String, String> wait = Json.object(text);
        int hp = Json.integer(wait.get("hp"));
        int cell = Json.integer(wait.get("hero_cell"));
        boolean burning = false;
        boolean poisoned = false;
        for (String buff : Json.array(wait.getOrDefault("hero_buffs", "[]"))) {
            String name = Json.string(Json.object(buff).get("name")).toLowerCase(java.util.Locale.ROOT);
            burning |= name.contains("burning");
            poisoned |= name.contains("poison");
        }
        int dist = -1;
        for (String enemy : Json.array(wait.getOrDefault("enemies", "[]"))) {
            Map<String, String> e = Json.object(enemy);
            if (Json.string(e.get("name")).equals("Tengu")) {
                dist = Json.integer(e.get("dist"));
            }
        }
        boolean trap = false;
        for (String line : Json.array(wait.getOrDefault("log", "[]"))) {
            trap |= Json.string(line).contains("dart trap activates");
        }
        return new Line(hp, cell, burning, poisoned, dist, cells(wait, "bombs"), cells(wait, "shock"),
                cells(wait, "shockers"), cells(wait, "fire"), trap);
    }

    private static Set<Integer> cells(Map<String, String> wait, String key) {
        Set<Integer> out = new HashSet<>();
        for (String raw : Json.array(wait.getOrDefault(key, "[]"))) {
            out.add(Json.integer(raw));
        }
        return out;
    }
}
