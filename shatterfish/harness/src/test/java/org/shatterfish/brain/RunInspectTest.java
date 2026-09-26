package org.shatterfish.brain;

import com.shatteredpixel.shatteredpixeldungeon.Dungeon;
import com.shatteredpixel.shatteredpixeldungeon.items.Heap;
import com.shatteredpixel.shatteredpixeldungeon.items.Item;
import com.shatteredpixel.shatteredpixeldungeon.levels.RegularLevel;
import com.shatteredpixel.shatteredpixeldungeon.levels.Terrain;
import com.shatteredpixel.shatteredpixeldungeon.levels.rooms.Room;
import com.shatteredpixel.shatteredpixeldungeon.levels.traps.Trap;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.shatterfish.api.Action;
import org.shatterfish.api.Belief;
import org.shatterfish.api.Codex;
import org.shatterfish.api.Decider;
import org.shatterfish.api.Fog;
import org.shatterfish.api.HeapView;
import org.shatterfish.api.LogLine;
import org.shatterfish.api.MapSection;
import org.shatterfish.api.Observation;
import org.shatterfish.api.RunLog;
import org.shatterfish.api.SeedSet;
import org.shatterfish.api.Tile;
import org.shatterfish.api.TrapView;
import org.shatterfish.api.Weights;
import org.shatterfish.harness.agent.RunLoop;
import org.shatterfish.harness.log.Json;
import org.shatterfish.harness.log.RunLogReader;
import org.shatterfish.harness.rng.DeciderSeeds;

import java.io.IOException;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * A debugging tool, not a check (issues #181-#184, #165): replays a Run log headlessly and writes, for every
 * wait, what the Brain saw and why it chose -- the heaps on the screen with the pick-up Policy's verdict on
 * each, the doors with their walkability around and through the avoided regions, the traps in view, the
 * frontier counts, the explore and pick-up Policies' own choices -- and, on lines marked ORACLE, what the
 * level really holds (rooms, true heap contents, hidden traps). The ORACLE lines are for a person
 * diagnosing a Run and never reach a Brain.
 *
 * <p>It lives in the brain's package, in the harness's tests, because it reads the Brain's package-private
 * Policies and Memory and the game's level at once; no module's main code may do both.
 *
 * <p>Skipped unless {@code SF_INSPECT_LOG} names a log. {@code SF_INSPECT_OUT} is the folder the dump
 * ({@code inspect.txt}) goes in; {@code SF_WEIGHTS} the weight set (default the committed one);
 * {@code SF_INSPECT_FOLLOW=no} plays the Brain live instead of following the log; {@code SF_INSPECT_FORCE=force}
 * keeps following the logged Actions past the first divergence wherever the screen offers them (an Overlay
 * log diverges headlessly, #169). The Brain is built on an empty Codex, as the Overlay builds it
 * ({@code OverlayAgents}). Run it with
 * {@code ./gradlew :harness:test --tests org.shatterfish.brain.RunInspectTest --rerun}.
 */
class RunInspectTest {

    private static final Set<Tile> DOORS = EnumSet.of(Tile.DOOR, Tile.OPEN_DOOR, Tile.LOCKED_DOOR, Tile.CRYSTAL_DOOR,
            Tile.BARRICADE, Tile.LOCKED_EXIT);

    @Test
    @EnabledIfEnvironmentVariable(named = "SF_INSPECT_LOG", matches = ".+")
    void inspect() throws IOException {
        Path logFile = Path.of(System.getenv("SF_INSPECT_LOG"));
        Path out = Path.of(System.getenv("SF_INSPECT_OUT"));
        String follow = System.getenv().getOrDefault("SF_INSPECT_FOLLOW", "yes");
        Files.createDirectories(out);
        RunLogReader.Log log = RunLogReader.of(logFile);
        RunLog.Header header = log.header();
        Weights weights = weights(Path.of(System.getenv().getOrDefault("SF_WEIGHTS", "../../weights/shatterfish.json")));
        Codex.Knowledge empty = new Codex.Knowledge(new Codex.Manifest(Codex.VERSION, "v4.0.0", List.of("manifest.json")),
                List.of(), List.of(), List.of());
        Brain brain = new Brain(empty, weights, DeciderSeeds.brain("shatterfish"));
        try (PrintWriter w = new PrintWriter(Files.newBufferedWriter(out.resolve("inspect.txt"), StandardCharsets.UTF_8))) {
            Inspector inspector = new Inspector(brain, follow.equals("yes") ? log.waits() : List.of(), w);
            SeedSet.Entry triple = new SeedSet.Entry(header.seed(), header.heroClass(), header.challenges(), header.seedCode());
            Path scratch = Files.createTempDirectory(out, "replay");
            var outcome = new RunLoop().playTriple(triple, header.salt(), inspector, 3000,
                    new RunLoop.Logging(scratch, "0".repeat(40), new RunLog.Brain("inspect", "0".repeat(40), "0".repeat(64)),
                            "", "inspect"));
            inspector.floorEnd();
            w.println("END " + outcome);
        }
    }

    static Weights weights(Path file) throws IOException {
        Map<String, String> held = Json.object(Files.readString(file, StandardCharsets.UTF_8).strip());
        List<Weights.Term> terms = new ArrayList<>();
        for (Map.Entry<String, String> term : Json.object(Json.required(held, "terms", "weights")).entrySet()) {
            terms.add(new Weights.Term(term.getKey(), Json.number(term.getValue())));
        }
        return new Weights(Json.string(Json.required(held, "name", "weights")),
                Json.integer(Json.required(held, "version", "weights")), terms);
    }

    static final class Inspector implements Decider {
        final Brain brain;
        final List<RunLog.Wait> waits;
        final PrintWriter w;
        Belief belief;
        int at;
        boolean following;
        boolean force = "force".equals(System.getenv("SF_INSPECT_FORCE"));
        int k;
        int depth = -1;
        com.shatteredpixel.shatteredpixeldungeon.levels.Level level;
        List<LogLine> lastLines = List.of();
        String lastOracle = "";
        final Map<Integer, List<Integer>> path = new TreeMap<>();
        final Map<String, String> heapsSeen = new LinkedHashMap<>();

        Inspector(Brain brain, List<RunLog.Wait> waits, PrintWriter w) {
            this.brain = brain;
            this.waits = waits;
            this.w = w;
            this.following = !waits.isEmpty();
        }

        @Override
        public Action decide(Observation obs) {
            k++;
            int d = obs.header().depth();
            if (d != depth) {
                if (depth >= 0) {
                    floorEnd();
                }
                depth = d;
                floorStart();
            }
            belief = brain.update(obs, belief);
            Memory memory = Memory.of(belief);
            Brain.Decided shadow = brain.decide(obs, belief);
            RunLog.Wait wait = following && at < waits.size() ? waits.get(at) : null;
            String match = "live";
            if (wait != null) {
                TreeSet<String> diff = new TreeSet<>();
                obs.sectionHashes().forEach((key, value) -> {
                    if (!value.equals(wait.sections().get(key))) {
                        diff.add(key);
                    }
                });
                diff.remove("log");
                if (!diff.isEmpty()) {
                    w.println("DIVERGED at wait " + wait.k() + " sections " + diff);
                    following = false;
                    match = "DIVERGED";
                } else {
                    match = obs.hash().equals(wait.obs()) ? "hash=" : "hash~log";
                }
            }
            Action action;
            Brain.Decided handed;
            String logged = "";
            if (!following && force) {
                // Past the divergence: keep the logged Actions where the screen offers them, so the hero's
                // path tracks the Run; fights that last a different number of blows are the shadow's.
                List<Action> offered = obs.actions().actions();
                while (at < waits.size() && waits.get(at).action() instanceof Action.Attack
                        && !offered.contains(waits.get(at).action())) {
                    at++;
                }
                RunLog.Wait next = at < waits.size() ? waits.get(at) : null;
                boolean shadowFights = shadow.decision() != null && shadow.decision().policy().equals("fight");
                boolean logFights = next != null && next.decision() != null && next.decision().policy().equals("fight");
                if (next != null && next.depth() == obs.header().depth() && !(shadowFights && !logFights)
                        && offered.contains(next.action())) {
                    action = next.action();
                    handed = new Brain.Decided(action, next.decision(), next.highlights(), "");
                    at++;
                    logged = " forced=log[" + next.k() + "] " + action + " " + (next.decision() == null ? ""
                            : next.decision().policy() + "|" + next.decision().chosen().why());
                } else {
                    action = shadow.action();
                    handed = shadow;
                    logged = " forced=shadow (log[" + (next == null ? -1 : next.k()) + "] " + (next == null ? "" : next.action()) + ")";
                }
                dump(obs, memory, shadow, "FORCED", logged);
                path.computeIfAbsent(d, x -> new ArrayList<>()).add(obs.hero().cell());
                belief = brain.handed(obs, belief, handed);
                return action;
            }
            if (following && wait != null) {
                action = wait.action();
                handed = new Brain.Decided(action, wait.decision(), wait.highlights(), "");
                at++;
                logged = " logged=" + action + " " + (wait.decision() == null ? "" : wait.decision().policy() + "|"
                        + wait.decision().chosen().why());
                if (shadow.action() == null || !shadow.action().equals(action)) {
                    logged += "  !! shadow differs";
                }
            } else {
                action = shadow.action();
                handed = shadow;
            }
            dump(obs, memory, shadow, match, logged);
            path.computeIfAbsent(d, x -> new ArrayList<>()).add(obs.hero().cell());
            belief = brain.handed(obs, belief, handed);
            return action;
        }

        private String xy(MapSection map, int cell) {
            return cell + "(" + cell % map.width() + "," + cell / map.width() + ")";
        }

        private void dump(Observation obs, Memory memory, Brain.Decided shadow, String match, String logged) {
            MapSection map = obs.map();
            var hero = obs.hero();
            w.println();
            w.println("== wait " + k + " depth " + obs.header().depth() + " [" + match + "] hero " + xy(map, hero.cell())
                    + " hp " + hero.hp() + "/" + hero.ht() + " streak " + memory.streak());
            w.println("  shadow=" + shadow.action() + " "
                    + (shadow.decision() == null ? shadow.why() : shadow.decision().policy() + "|" + shadow.decision().chosen().why())
                    + logged);
            // new log lines
            List<LogLine> lines = obs.log().lines();
            int common = 0;
            for (int j = Math.min(lines.size(), lastLines.size()); j >= 0; j--) {
                if (lines.subList(0, j).equals(lastLines.subList(lastLines.size() - j, lastLines.size()))) {
                    common = j;
                    break;
                }
            }
            for (LogLine line : lines.subList(common, lines.size())) {
                w.println("  LOG " + line.text());
            }
            lastLines = lines;
            // heaps
            boolean[] walk = Explore.walkable(obs, memory);
            int[] dist = Pickup.distances(map, walk, hero.cell());
            boolean[] open = Explore.walkable(obs, memory, false);
            int[] openDist = Pickup.distances(map, open, hero.cell());
            Pickup pickup = new Pickup(new Evaluation(brain.weights()),
                    new Codex.Knowledge(new Codex.Manifest(Codex.VERSION, "v4.0.0", List.of("manifest.json")),
                            List.of(), List.of(), List.of()));
            boolean calm = Explore.calm(obs);
            for (HeapView heap : map.heaps()) {
                StringBuilder s = new StringBuilder("  HEAP " + xy(map, heap.cell()) + " " + heap.kind()
                        + (heap.hidden() ? " hidden" : "") + " '" + heap.item() + "'" + " fog=" + map.fog().get(heap.cell()));
                boolean takeable = Pickup.takeable(heap, obs, memory);
                s.append(" takeable=").append(takeable);
                int dd = heap.cell() < dist.length ? dist[heap.cell()] : -1;
                s.append(" dist=").append(dd);
                if (takeable && dd >= 0) {
                    int turns = dd + 1 + (dd > 0 && Pickup.passiveInView(obs) ? 1 : 0);
                    long net = pickup.worth(heap.item(), obs) + new Evaluation(brain.weights()).turns(turns);
                    s.append(" worth=").append(pickup.worth(heap.item(), obs)).append(" net=").append(net);
                }
                int od = heap.cell() < openDist.length ? openDist[heap.cell()] : -1;
                s.append(" noavoidDist=").append(od);
                if (!calm) {
                    s.append(" (not calm)");
                }
                w.println(s);
                heapsSeen.putIfAbsent(obs.header().depth() + ":" + heap.cell() + ":" + heap.kind() + ":" + heap.item(),
                        "first seen wait " + k);
            }
            w.println("  AVOID " + memory.avoided(obs.header().depth(), obs.header().branch(), memory.waits())
                    + " blocked=" + memory.blocked().stream().filter(sp -> sp.on(obs.header().depth(), obs.header().branch())).map(Memory.Spot::cell).toList()
                    + " fleeting=" + memory.fleeting().stream().filter(c -> c.depth() == obs.header().depth() && c.until() >= memory.waits()).map(Memory.Cloud::cell).toList()
                    + " memwaits=" + memory.waits());
            // doors
            StringBuilder doors = new StringBuilder();
            for (int cell = 0; cell < map.cells(); cell++) {
                Tile tile = map.tiles().get(cell);
                if (DOORS.contains(tile)) {
                    boolean unknownNear = false;
                    int x = cell % map.width();
                    int y = cell / map.width();
                    for (int dy = -1; dy <= 1; dy++) {
                        for (int dx = -1; dx <= 1; dx++) {
                            int nx = x + dx;
                            int ny = y + dy;
                            if (nx >= 0 && ny >= 0 && nx < map.width() && ny < map.height()
                                    && map.fog().get(nx + ny * map.width()) == Fog.UNKNOWN) {
                                unknownNear = true;
                            }
                        }
                    }
                    doors.append(" ").append(cell).append(":").append(tile).append("/").append(map.fog().get(cell))
                            .append(walk[cell] ? "/walk" : "/nowalk").append(unknownNear ? "/UNKNOWN-NEAR" : "")
                            .append(dist[cell] >= 0 ? "/d" + dist[cell] : "/unreached")
                            .append(open[cell] ? "/noavoid-walk" : "/noavoid-nowalk")
                            .append(openDist[cell] >= 0 ? "/nd" + openDist[cell] : "/noavoid-unreached");
                }
            }
            w.println("  DOORS" + doors);
            StringBuilder traps = new StringBuilder();
            for (TrapView trap : map.traps()) {
                traps.append(" ").append(trap.cell()).append(":").append(trap.kind()).append(trap.active() ? "" : "(spent)");
            }
            w.println("  TRAPS" + traps);
            int openReach = 0;
            for (int cell = 0; cell < map.cells(); cell++) {
                if (Explore.frontier(map, open, cell) && openDist[cell] >= 0) {
                    openReach++;
                }
            }
            int reach = 0;
            int unreach = 0;
            for (int cell = 0; cell < map.cells(); cell++) {
                if (Explore.frontier(map, walk, cell)) {
                    if (dist[cell] >= 0) {
                        reach++;
                    } else {
                        unreach++;
                    }
                }
            }
            RunLog.Choice explore = new Explore().choose(obs, memory, obs.actions().actions(), null);
            RunLog.Choice pick = pickup.enters(obs, memory) ? pickup.choose(obs, memory, obs.actions().actions(), null) : null;
            long searched = memory.dwelt().stream().filter(spot -> spot.on(obs.header().depth(), obs.header().branch())).count();
            w.println("  FRONTIER reachable=" + reach + " unreachable=" + unreach + " searched=" + searched
                    + " noavoidReachable=" + openReach + " spent=" + Explore.spent(obs, memory) + " calm=" + calm);
            w.println("  EXPLORE " + (explore == null ? "null" : explore.action() + " " + explore.why())
                    + "  PICKUP " + (pick == null ? "null" : pick.action() + " " + pick.why()));
            w.println("  INV " + obs.inventory().items().stream().map(item -> item.name()
                    + (item.quantity() > 1 ? " x" + item.quantity() : "")).toList());
            oracle();
        }

        private void oracle() {
            StringBuilder s = new StringBuilder("  ORACLE traps:");
            for (Trap trap : Dungeon.level.traps.valueList()) {
                s.append(" ").append(trap.pos).append(":").append(trap.getClass().getSimpleName())
                        .append(trap.visible ? "/vis" : "/HIDDEN").append(trap.active ? "" : "/spent");
            }
            s.append(" | heaps:");
            for (Heap heap : Dungeon.level.heaps.valueList()) {
                s.append(" ").append(heap.pos).append(":").append(heap.type).append(heap.items.stream()
                        .map(item -> item.getClass().getSimpleName()).toList());
            }
            String now = s.toString();
            if (!now.equals(lastOracle)) {
                w.println(now);
                lastOracle = now;
            }
        }

        void floorStart() {
            level = Dungeon.level;
            w.println();
            w.println("######## FLOOR " + Dungeon.depth + " width " + level.width() + " height " + level.height()
                    + " class " + level.getClass().getSimpleName() + " (ORACLE below)");
            if (level instanceof RegularLevel) {
                try {
                    var field = RegularLevel.class.getDeclaredField("rooms");
                    field.setAccessible(true);
                    @SuppressWarnings("unchecked")
                    List<Room> rooms = (List<Room>) field.get(level);
                    for (Room room : rooms) {
                        w.println("  ROOM " + room.getClass().getName().replace("com.shatteredpixel.shatteredpixeldungeon.levels.rooms.", "")
                                + " x " + room.left + ".." + room.right + " y " + room.top + ".." + room.bottom);
                    }
                } catch (ReflectiveOperationException e) {
                    w.println("  rooms: " + e);
                }
            }
            for (int cell = 0; cell < level.length(); cell++) {
                int t = level.map[cell];
                if (t == Terrain.LOCKED_DOOR || t == Terrain.SECRET_DOOR || t == Terrain.CRYSTAL_DOOR || t == Terrain.DOOR
                        || t == Terrain.BARRICADE) {
                    w.println("  DOORTILE " + cell + " (" + cell % level.width() + "," + cell / level.width() + ") "
                            + (t == Terrain.LOCKED_DOOR ? "LOCKED_DOOR" : t == Terrain.SECRET_DOOR ? "SECRET_DOOR"
                            : t == Terrain.CRYSTAL_DOOR ? "CRYSTAL_DOOR" : t == Terrain.BARRICADE ? "BARRICADE" : "DOOR"));
                }
            }
            for (Heap heap : level.heaps.valueList()) {
                w.println("  HEAPTRUE " + heap.pos + " (" + heap.pos % level.width() + "," + heap.pos / level.width() + ") "
                        + heap.type + " " + heap.items.stream().map(Item::name).toList()
                        + " " + heap.items.stream().map(item -> item.getClass().getSimpleName()).toList());
            }
            for (Trap trap : level.traps.valueList()) {
                w.println("  TRAPTRUE " + trap.pos + " (" + trap.pos % level.width() + "," + trap.pos / level.width() + ") "
                        + trap.getClass().getSimpleName() + (trap.visible ? " visible" : " HIDDEN"));
            }
            w.println(ascii(false));
        }

        void floorEnd() {
            w.println();
            w.println("######## END OF FLOOR " + depth + " (ORACLE map, path marked *)");
            w.println(ascii(true));
            w.println("  HEAPS SEEN on the screen so far: " + heapsSeen);
            w.flush();
        }

        private String ascii(boolean withPath) {
            int width = level.width();
            char[] c = new char[level.length()];
            for (int cell = 0; cell < c.length; cell++) {
                int t = level.map[cell];
                c[cell] = switch (t) {
                    case Terrain.WALL, Terrain.WALL_DECO -> '#';
                    case Terrain.DOOR, Terrain.OPEN_DOOR -> '+';
                    case Terrain.LOCKED_DOOR -> 'L';
                    case Terrain.SECRET_DOOR -> 'S';
                    case Terrain.CRYSTAL_DOOR -> 'X';
                    case Terrain.ENTRANCE, Terrain.ENTRANCE_SP -> '<';
                    case Terrain.EXIT, Terrain.UNLOCKED_EXIT, Terrain.LOCKED_EXIT -> '>';
                    case Terrain.SECRET_TRAP -> 't';
                    case Terrain.TRAP -> '^';
                    case Terrain.INACTIVE_TRAP -> 'v';
                    case Terrain.WATER -> '~';
                    case Terrain.GRASS, Terrain.HIGH_GRASS, Terrain.FURROWED_GRASS -> '"';
                    case Terrain.EMBERS -> ',';
                    case Terrain.CHASM -> ' ';
                    case Terrain.BARRICADE -> '=';
                    case Terrain.BOOKSHELF -> 'B';
                    case Terrain.STATUE, Terrain.STATUE_SP -> 'T';
                    case Terrain.PEDESTAL -> 'P';
                    default -> '.';
                };
            }
            if (withPath && path.containsKey(depth)) {
                for (int cell : path.get(depth)) {
                    if (c[cell] == '.' || c[cell] == '"' || c[cell] == ',' || c[cell] == '~') {
                        c[cell] = '*';
                    } else if (c[cell] == 't' || c[cell] == '^') {
                        c[cell] = '!';
                    }
                }
            }
            for (Heap heap : level.heaps.valueList()) {
                c[heap.pos] = heap.type == Heap.Type.HEAP ? 'h' : heap.type == Heap.Type.CHEST ? 'C'
                        : heap.type == Heap.Type.LOCKED_CHEST ? 'K' : heap.type == Heap.Type.SKELETON ? 'k'
                        : heap.type == Heap.Type.REMAINS ? 'r' : heap.type == Heap.Type.TOMB ? 'M' : 'H';
            }
            StringBuilder s = new StringBuilder();
            s.append("     ");
            for (int x = 0; x < width; x++) {
                s.append(x % 10);
            }
            s.append('\n');
            for (int y = 0; y < level.height(); y++) {
                s.append(String.format("%4d ", y * width));
                s.append(c, y * width, width).append('\n');
            }
            return s.toString();
        }
    }
}
