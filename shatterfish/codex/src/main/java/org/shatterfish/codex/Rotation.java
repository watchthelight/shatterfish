package org.shatterfish.codex;

import com.shatteredpixel.shatteredpixeldungeon.actors.buffs.ChampionEnemy;
import com.shatteredpixel.shatteredpixeldungeon.actors.mobs.Elemental;
import com.shatteredpixel.shatteredpixeldungeon.actors.mobs.Mob;
import com.shatteredpixel.shatteredpixeldungeon.actors.mobs.MobSpawner;
import com.shatteredpixel.shatteredpixeldungeon.actors.mobs.Shaman;
import org.shatterfish.api.Challenge;
import org.shatterfish.api.Codex;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The spawn rotation (story 2.2), read from the spawner's own source: {@code getMobRotation}
 * draws and {@code standardMobRotation} is private, so the per-depth literal is read case by
 * case, the random families are read from their {@code random()} methods, the rare additions
 * from {@code addRareMobs}, the alternate swap chance from {@code swapMobAlts}, the alternates
 * from the public map, and the champion rule from {@code ChampionEnemy.rollForChampion}. Every
 * rule is cited to the line it was read from.
 */
final class Rotation {

    private static final Pattern CASE = Pattern.compile("^\\s*case\\s+(\\d+)\\s*:(.*)$");
    private static final Pattern TOKEN = Pattern.compile("(\\w+)\\.(class|random\\(\\))");
    private static final Pattern RARE = Pattern.compile("^\\s*if\\s*\\(\\s*Random\\.Float\\(\\)\\s*<\\s*([\\d.]+)f\\s*\\)\\s*rotation\\.add\\((\\w+)\\.class\\);");
    private static final Pattern ALT_CHANCE = Pattern.compile("^\\s*float altChance\\s*=\\s*(.+);");
    private static final Pattern THRESHOLD = Pattern.compile("roll\\s*<\\s*([\\d.]+)f");
    private static final Pattern RETURN_CLASS = Pattern.compile("return\\s+(\\w+)\\.class;");
    private static final Pattern BUFF = Pattern.compile("buffCls\\s*=\\s*(\\w+)\\.class;");
    private static final Pattern EXCLUSION = Pattern.compile("if\\s*\\(\\s*m instanceof (\\w+)\\s*&&\\s*Dungeon\\.scalingDepth\\(\\)\\s*<=\\s*(\\d+)\\s*\\)\\s*return;");

    private Rotation() {
    }

    static Codex.SpawnRotation read(Path root) {
        Sources.Body spawner = Sources.body(root, MobSpawner.class);
        Map<String, List<Codex.Odds>> families = new LinkedHashMap<>();
        families.put("Shaman", family(root, Shaman.class));
        families.put("Elemental", family(root, Elemental.class));
        return new Codex.SpawnRotation(depths(spawner, families), rareMobs(spawner), alternateChance(spawner),
                alternateChanceExpression(spawner), alternates(spawner), champion(root));
    }

    /** The standard rotation per depth, from the private method's case literals. */
    static List<Codex.RotationDepth> depths(Sources.Body spawner, Map<String, List<Codex.Odds>> families) {
        Sources.Body method = spawner.block(spawner.find("standardMobRotation\\s*\\(\\s*int depth\\s*\\)"));
        TreeMap<Integer, Codex.RotationDepth> byDepth = new TreeMap<>();
        List<Integer> pending = new ArrayList<>();
        int pendingLine = -1;
        StringBuilder literal = null;
        for (int i = method.from(); i < method.to(); i++) {
            String line = method.lines().get(i);
            if (literal == null) {
                Matcher c = CASE.matcher(line);
                if (c.find()) {
                    if (pending.isEmpty()) {
                        pendingLine = i;
                    }
                    String rest = line;
                    Matcher each = Pattern.compile("case\\s+(\\d+)\\s*:").matcher(rest);
                    while (each.find()) {
                        pending.add(Integer.parseInt(each.group(1)));
                    }
                }
                if (line.contains("return new ArrayList")) {
                    literal = new StringBuilder(line);
                }
            } else {
                literal.append(' ').append(line);
            }
            if (literal != null && line.contains("));")) {
                List<Codex.RotationEntry> entries = entries(literal.toString(), families);
                if (pending.isEmpty()) {
                    throw new IllegalStateException(spawner.path() + ":" + (i + 1) + ": a rotation literal with no case");
                }
                for (int depth : pending) {
                    byDepth.put(depth, new Codex.RotationDepth(depth, entries, spawner.citation(pendingLine)));
                }
                pending.clear();
                literal = null;
            }
        }
        for (int depth = 1; depth <= Mobs.MAX_DEPTH; depth++) {
            if (!byDepth.containsKey(depth)) {
                throw new IllegalStateException("the spawner lists no rotation for depth " + depth);
            }
        }
        return new ArrayList<>(byDepth.values());
    }

    /** The counted classes of one literal, in first-appearance order, families expanded. */
    static List<Codex.RotationEntry> entries(String literal, Map<String, List<Codex.Odds>> families) {
        String list = literal.substring(literal.indexOf("Arrays.asList(") + "Arrays.asList(".length());
        LinkedHashMap<String, Integer> counts = new LinkedHashMap<>();
        Matcher token = TOKEN.matcher(list);
        while (token.find()) {
            String key = token.group(1) + (token.group(2).equals("class") ? "" : ".random()");
            counts.merge(key, 1, Integer::sum);
        }
        List<Codex.RotationEntry> entries = new ArrayList<>();
        for (Map.Entry<String, Integer> count : counts.entrySet()) {
            String key = count.getKey();
            if (key.endsWith(".random()")) {
                String family = key.substring(0, key.length() - ".random()".length());
                List<Codex.Odds> odds = families.get(family);
                if (odds == null) {
                    throw new IllegalStateException("the rotation draws from a family the generator does not read: " + family);
                }
                entries.add(new Codex.RotationEntry(family, count.getValue(), odds));
            } else {
                entries.add(new Codex.RotationEntry(key, count.getValue(), List.of()));
            }
        }
        return entries;
    }

    /**
     * A family's odds from its {@code random()} method: an optional alternate drawn first at its
     * own chance, then thresholds on one roll; the last return takes the remainder. Thousandths,
     * the remainder absorbing the rounding.
     */
    static List<Codex.Odds> family(Path root, Class<?> family) {
        Sources.Body body = Sources.body(root, family);
        Sources.Body method = body.block(body.find("public static Class<\\? extends \\w+> random\\s*\\(\\s*\\)"));
        int alternatePerMille = 0;
        String alternate = null;
        List<String> classes = new ArrayList<>();
        List<Integer> thresholds = new ArrayList<>();
        boolean sawRoll = false;
        for (int i = method.from(); i < method.to(); i++) {
            String line = method.lines().get(i);
            Matcher chance = ALT_CHANCE.matcher(line);
            if (chance.find()) {
                alternatePerMille = Sources.thousandths(chance.group(1).replaceAll("\\*.*$", ""));
            }
            if (line.contains("float roll")) {
                sawRoll = true;
            }
            Matcher threshold = THRESHOLD.matcher(line);
            if (threshold.find()) {
                thresholds.add(Sources.thousandths(threshold.group(1) + "f"));
            }
            Matcher returned = RETURN_CLASS.matcher(line);
            if (returned.find()) {
                if (!sawRoll && alternate == null && alternatePerMille > 0) {
                    alternate = returned.group(1);
                } else {
                    classes.add(returned.group(1));
                }
            }
        }
        if (classes.size() != thresholds.size() + 1) {
            throw new IllegalStateException(family.getName() + ".random() is not thresholds and a remainder: " + classes + " " + thresholds);
        }
        List<Codex.Odds> odds = new ArrayList<>();
        int remaining = 1000 - alternatePerMille;
        if (alternate != null) {
            odds.add(new Codex.Odds(alternate, alternatePerMille));
        }
        int previous = 0;
        int given = 0;
        for (int i = 0; i < thresholds.size(); i++) {
            int share = Math.round((thresholds.get(i) - previous) * remaining / 1000f);
            odds.add(new Codex.Odds(classes.get(i), share));
            given += share;
            previous = thresholds.get(i);
        }
        odds.add(new Codex.Odds(classes.get(classes.size() - 1), remaining - given));
        return odds;
    }

    static List<Codex.RareMob> rareMobs(Sources.Body spawner) {
        Sources.Body method = spawner.block(spawner.find("public static void addRareMobs\\s*\\("));
        List<Codex.RareMob> rare = new ArrayList<>();
        int depth = -1;
        for (int i = method.from(); i < method.to(); i++) {
            String line = method.lines().get(i);
            Matcher c = CASE.matcher(line);
            if (c.find()) {
                depth = Integer.parseInt(c.group(1));
            }
            Matcher r = RARE.matcher(line);
            if (r.find()) {
                if (depth < 0) {
                    throw new IllegalStateException(spawner.path() + ":" + (i + 1) + ": a rare mob with no case");
                }
                rare.add(new Codex.RareMob(depth, r.group(2), Sources.thousandths(r.group(1) + "f"), spawner.citation(i)));
            }
        }
        return rare;
    }

    static int alternateChance(Sources.Body spawner) {
        String expression = alternateChanceExpression(spawner);
        return Sources.thousandths(expression.replaceAll("\\*.*$", ""));
    }

    static String alternateChanceExpression(Sources.Body spawner) {
        Sources.Body method = spawner.block(spawner.find("private static void swapMobAlts\\s*\\("));
        for (int i = method.from(); i < method.to(); i++) {
            Matcher m = ALT_CHANCE.matcher(method.lines().get(i));
            if (m.find()) {
                return m.group(1).trim();
            }
        }
        throw new IllegalStateException(spawner.path() + ": swapMobAlts declares no altChance");
    }

    /** The alternates from the public map, sorted by name, each cited to its own put. */
    static List<Codex.RareAlt> alternates(Sources.Body spawner) {
        TreeMap<String, String> byName = new TreeMap<>();
        for (Map.Entry<Class<? extends Mob>, Class<? extends Mob>> alt : MobSpawner.RARE_ALTS.entrySet()) {
            byName.put(alt.getKey().getSimpleName(), Sources.name(alt.getValue()));
        }
        List<Codex.RareAlt> alternates = new ArrayList<>();
        for (Map.Entry<String, String> alt : byName.entrySet()) {
            int line = spawner.find("RARE_ALTS\\.put\\(\\s*" + Pattern.quote(alt.getKey()) + "\\.class");
            if (line < 0) {
                throw new IllegalStateException(spawner.path() + ": RARE_ALTS holds " + alt.getKey() + " with no put to cite");
            }
            alternates.add(new Codex.RareAlt(alt.getKey(), alt.getValue(), spawner.citation(line)));
        }
        return alternates;
    }

    static Codex.ChampionRule champion(Path root) {
        Sources.Body body = Sources.body(root, ChampionEnemy.class);
        int declaration = body.find("public static void rollForChampion\\s*\\(");
        Sources.Body method = body.block(declaration);
        List<String> buffs = new ArrayList<>();
        List<Codex.Exclusion> exclusions = new ArrayList<>();
        String counter = null;
        String challenge = null;
        for (int i = method.from(); i < method.to(); i++) {
            String line = method.lines().get(i);
            Matcher b = BUFF.matcher(line);
            while (b.find()) {
                buffs.add(b.group(1));
            }
            Matcher e = EXCLUSION.matcher(line);
            if (e.find()) {
                exclusions.add(new Codex.Exclusion(e.group(1), Integer.parseInt(e.group(2))));
            }
            if (line.contains("mobsToChampion +=")) {
                counter = line.trim();
            }
            Matcher c = Pattern.compile("Challenges\\.(\\w+)").matcher(line);
            if (c.find() && line.contains("isChallenged")) {
                challenge = c.group(1);
            }
        }
        if (counter == null || challenge == null) {
            throw new IllegalStateException("rollForChampion no longer states its counter or its challenge");
        }
        return new Codex.ChampionRule(Challenge.valueOf(challenge), buffs, exclusions, counter, body.citation(declaration));
    }
}
