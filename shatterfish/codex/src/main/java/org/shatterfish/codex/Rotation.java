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
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The spawn rotation (story 2.2), read from the spawner's own source: {@code getMobRotation}
 * draws and {@code standardMobRotation} is private, so the per-depth literal is read case by
 * case, the random families are read from their {@code random()} methods, the rare additions
 * from {@code addRareMobs}, the alternate swap chance from {@code swapMobAlts}, the alternates
 * from the public map, and the champion rule from {@code ChampionEnemy.rollForChampion}. Every
 * rule is cited to the line it was read from, and every shape the reader relies on is checked
 * against a count the source gives for free, so that a rule the reader stops seeing fails the
 * generation rather than vanishing.
 */
final class Rotation {

    private static final Pattern CASE = Pattern.compile("\\bcase\\s+(\\d+)\\s*:");
    private static final Pattern DEFAULT = Pattern.compile("\\bdefault\\s*:");
    private static final Pattern TOKEN = Pattern.compile("(\\w+)\\.(class|random\\(\\))");
    private static final Pattern RARE = Pattern.compile("^\\s*if\\s*\\(\\s*Random\\.Float\\(\\)\\s*<\\s*([\\d.]+[fF]?)\\s*\\)\\s*rotation\\.add\\((\\w+)\\.class\\);");
    private static final Pattern ALT_CHANCE = Pattern.compile("^\\s*float altChance\\s*=\\s*(.+);");
    private static final Pattern LITERAL_TIMES_CALL = Pattern.compile("^(.+?)\\s*\\*\\s*([\\w.]+\\(\\))$");
    private static final Pattern THRESHOLD = Pattern.compile("roll\\s*<\\s*([\\d.]+[fF]?)");
    private static final Pattern RETURN_CLASS = Pattern.compile("return\\s+(\\w+)\\.class;");
    private static final Pattern BUFF = Pattern.compile("buffCls\\s*=\\s*(\\w+)\\.class;");
    private static final Pattern DRAW = Pattern.compile("switch\\s*\\(\\s*Random\\.Int\\(\\s*(\\d+)\\s*\\)\\s*\\)");
    private static final Pattern EXCLUSION = Pattern.compile("if\\s*\\(\\s*m instanceof (\\w+)\\s*&&\\s*Dungeon\\.scalingDepth\\(\\)\\s*<=\\s*(\\d+)\\s*\\)\\s*return;");

    private Rotation() {
    }

    /** The rotation, with every class named canonically through {@code canonical} (a simple name to the table's name). */
    static Codex.SpawnRotation read(Path root, Map<String, String> canonical) {
        Sources.Body spawner = Sources.body(root, MobSpawner.class);
        List<Codex.Family> families = List.of(family(root, Shaman.class, canonical), family(root, Elemental.class, canonical));
        TreeSet<String> familyNames = new TreeSet<>();
        for (Codex.Family family : families) {
            familyNames.add(family.className());
        }
        int[] defaultDepth = new int[1];
        List<Codex.RotationDepth> depths = depths(spawner, familyNames, canonical, defaultDepth);
        TreeSet<String> listedAsThemselves = new TreeSet<>();
        for (Codex.RotationDepth depth : depths) {
            for (Codex.RotationEntry entry : depth.entries()) {
                if (!entry.family()) {
                    listedAsThemselves.add(entry.className());
                }
            }
        }
        return new Codex.SpawnRotation(depths, defaultDepth[0], families, rareMobs(spawner, canonical),
                alternateChance(spawner), alternateChanceExpression(spawner), alternates(spawner, canonical, listedAsThemselves),
                champion(root, canonical));
    }

    /** The standard rotation per depth, from the private method's case literals; the default arm's depth reported. */
    static List<Codex.RotationDepth> depths(Sources.Body spawner, TreeSet<String> familyNames, Map<String, String> canonical, int[] defaultDepth) {
        Sources.Body method = spawner.block(spawner.find("standardMobRotation\\s*\\(\\s*int depth\\s*\\)"));
        TreeMap<Integer, Codex.RotationDepth> byDepth = new TreeMap<>();
        List<Integer> pending = new ArrayList<>();
        boolean pendingDefault = false;
        int pendingLine = -1;
        StringBuilder literal = null;
        int literals = 0;
        for (int i = method.from(); i < method.to(); i++) {
            String line = Sources.stripComment(method.lines().get(i));
            if (literal == null) {
                Matcher c = CASE.matcher(line);
                boolean any = false;
                while (c.find()) {
                    if (pending.isEmpty()) {
                        pendingLine = i;
                    }
                    pending.add(Integer.parseInt(c.group(1)));
                    any = true;
                }
                if (DEFAULT.matcher(line).find()) {
                    if (pending.isEmpty()) {
                        pendingLine = i;
                    }
                    pendingDefault = true;
                    any = true;
                }
                if (line.contains("return new ArrayList")) {
                    if (!line.contains("Arrays.asList(")) {
                        throw new IllegalStateException(spawner.path() + ":" + (i + 1) + ": a rotation literal that is not Arrays.asList");
                    }
                    literal = new StringBuilder(line);
                } else if (!any && line.contains("return")) {
                    throw new IllegalStateException(spawner.path() + ":" + (i + 1) + ": a return in standardMobRotation that is not a rotation literal");
                }
            } else {
                literal.append(' ').append(line);
            }
            if (literal != null && line.contains("));")) {
                literals++;
                if (pending.isEmpty()) {
                    throw new IllegalStateException(spawner.path() + ":" + (i + 1) + ": a rotation literal with no case");
                }
                List<Codex.RotationEntry> entries = entries(literal.toString(), familyNames, canonical, spawner.path() + ":" + (pendingLine + 1));
                for (int depth : pending) {
                    if (depth < 1 || depth > Mobs.MAX_DEPTH) {
                        throw new IllegalStateException("the spawner lists depth " + depth + " outside 1 to " + Mobs.MAX_DEPTH + "; raise Mobs.MAX_DEPTH");
                    }
                    byDepth.put(depth, new Codex.RotationDepth(depth, entries, spawner.citation(pendingLine)));
                }
                if (pendingDefault) {
                    defaultDepth[0] = pending.get(0);
                }
                pending.clear();
                pendingDefault = false;
                literal = null;
            }
        }
        for (int depth = 1; depth <= Mobs.MAX_DEPTH; depth++) {
            if (!byDepth.containsKey(depth)) {
                throw new IllegalStateException("the spawner lists no rotation for depth " + depth);
            }
        }
        if (defaultDepth[0] == 0) {
            throw new IllegalStateException("standardMobRotation has no default arm");
        }
        if (literals != new TreeSet<>(byDepth.values().stream().map(d -> d.citation().line()).toList()).size()) {
            throw new IllegalStateException("the rotation literals and the case groups do not agree");
        }
        return new ArrayList<>(byDepth.values());
    }

    /** The counted classes of one literal, in first-appearance order, each named canonically. */
    static List<Codex.RotationEntry> entries(String literal, TreeSet<String> familyNames, Map<String, String> canonical, String where) {
        String list = literal.substring(literal.indexOf("Arrays.asList(") + "Arrays.asList(".length());
        LinkedHashMap<String, Integer> counts = new LinkedHashMap<>();
        Matcher token = TOKEN.matcher(list);
        while (token.find()) {
            boolean family = !token.group(2).equals("class");
            String name = token.group(1);
            if (family && !familyNames.contains(name)) {
                throw new IllegalStateException(where + ": the rotation draws from a family the generator does not read: " + name);
            }
            String key = (family ? "family " : "class ") + name;
            counts.merge(key, 1, Integer::sum);
        }
        List<Codex.RotationEntry> entries = new ArrayList<>();
        for (Map.Entry<String, Integer> count : counts.entrySet()) {
            boolean family = count.getKey().startsWith("family ");
            String name = count.getKey().substring(count.getKey().indexOf(' ') + 1);
            entries.add(new Codex.RotationEntry(family ? name : canonical(canonical, name, where), count.getValue(), family));
        }
        return entries;
    }

    private static String canonical(Map<String, String> canonical, String simpleName, String where) {
        String name = canonical.get(simpleName);
        if (name == null) {
            throw new IllegalStateException(where + " names a class the mobs table does not hold: " + simpleName);
        }
        return name;
    }

    /**
     * A family's odds from its {@code random()} method: an optional alternate drawn first at its
     * own chance (a literal, times a trinket's multiplier the expression keeps), then strictly
     * rising thresholds on one roll; the last return takes the remainder. Thousandths, the
     * remainder absorbing the rounding.
     */
    static Codex.Family family(Path root, Class<?> family, Map<String, String> canonical) {
        Sources.Body body = Sources.body(root, family);
        int declaration = body.find("public static Class<\\? extends \\w+> random\\s*\\(\\s*\\)");
        Sources.Body method = body.block(declaration);
        String where = body.path() + ":" + (declaration + 1);
        int alternatePerMille = 0;
        String alternateExpression = null;
        String alternate = null;
        List<String> classes = new ArrayList<>();
        List<Integer> thresholds = new ArrayList<>();
        List<String> thresholdExpressions = new ArrayList<>();
        boolean sawRoll = false;
        for (int i = method.from(); i < method.to(); i++) {
            String line = Sources.stripComment(method.lines().get(i));
            Matcher chance = ALT_CHANCE.matcher(line);
            if (chance.find()) {
                alternateExpression = chance.group(1).trim();
                alternatePerMille = required(literalOf(alternateExpression, where), alternateExpression, where);
            }
            if (line.contains("float roll")) {
                sawRoll = true;
            }
            Matcher threshold = THRESHOLD.matcher(line);
            if (threshold.find()) {
                thresholds.add(required(threshold.group(1), threshold.group(1), where));
                thresholdExpressions.add(line.trim());
            }
            Matcher returned = RETURN_CLASS.matcher(line);
            if (returned.find()) {
                if (!sawRoll && alternate == null && alternateExpression != null) {
                    alternate = returned.group(1);
                } else {
                    classes.add(returned.group(1));
                }
            }
        }
        if (classes.size() != thresholds.size() + 1) {
            throw new IllegalStateException(where + ": random() is not thresholds and a remainder: " + classes + " " + thresholds);
        }
        for (int i = 0; i < thresholds.size(); i++) {
            if (thresholds.get(i) <= (i == 0 ? 0 : thresholds.get(i - 1)) || thresholds.get(i) >= 1000) {
                throw new IllegalStateException(where + ": the thresholds do not rise inside a thousand: " + thresholds);
            }
        }
        List<Codex.Odds> odds = new ArrayList<>();
        int remaining = 1000 - alternatePerMille;
        if (alternate != null) {
            odds.add(new Codex.Odds(canonical(canonical, alternate, where), alternatePerMille, "Random.Float() < " + alternateExpression));
        }
        int previous = 0;
        int given = 0;
        for (int i = 0; i < thresholds.size(); i++) {
            int share = Math.round((thresholds.get(i) - previous) * remaining / 1000f);
            odds.add(new Codex.Odds(canonical(canonical, classes.get(i), where), share, thresholdExpressions.get(i)));
            given += share;
            previous = thresholds.get(i);
        }
        odds.add(new Codex.Odds(canonical(canonical, classes.get(classes.size() - 1), where), remaining - given, "else"));
        return new Codex.Family(family.getSimpleName(), odds, body.citation(declaration));
    }

    /** The literal factor of {@code literal * Ident.call()}, or the whole expression when it is a plain literal. */
    private static String literalOf(String expression, String where) {
        Matcher m = LITERAL_TIMES_CALL.matcher(expression);
        if (m.matches()) {
            return m.group(1).trim();
        }
        if (expression.contains("*") || expression.contains("+") || expression.contains("-")) {
            throw new IllegalStateException(where + ": a chance expression the reader does not know the shape of: " + expression);
        }
        return expression;
    }

    private static int required(String number, String expression, String where) {
        int thousandths = Sources.thousandths(number);
        if (thousandths < 0) {
            throw new IllegalStateException(where + ": not a chance the reader can state as thousandths: " + expression);
        }
        return thousandths;
    }

    static List<Codex.RareMob> rareMobs(Sources.Body spawner, Map<String, String> canonical) {
        Sources.Body method = spawner.block(spawner.find("public static void addRareMobs\\s*\\("));
        List<Codex.RareMob> rare = new ArrayList<>();
        int depth = -1;
        int adds = 0;
        for (int i = method.from(); i < method.to(); i++) {
            String line = Sources.stripComment(method.lines().get(i));
            Matcher c = CASE.matcher(line);
            if (c.find()) {
                depth = Integer.parseInt(c.group(1));
            }
            if (line.contains("rotation.add(")) {
                adds++;
            }
            Matcher r = RARE.matcher(line);
            if (r.find()) {
                if (depth < 0) {
                    throw new IllegalStateException(spawner.path() + ":" + (i + 1) + ": a rare mob with no case");
                }
                String where = spawner.path() + ":" + (i + 1);
                rare.add(new Codex.RareMob(depth, canonical(canonical, r.group(2), where), required(r.group(1), r.group(1), where), spawner.citation(i)));
            }
        }
        if (adds != rare.size()) {
            throw new IllegalStateException(spawner.path() + ": addRareMobs adds " + adds + " times and the reader read " + rare.size());
        }
        return rare;
    }

    static int alternateChance(Sources.Body spawner) {
        String expression = alternateChanceExpression(spawner);
        String where = spawner.path() + " swapMobAlts";
        return required(literalOf(expression, where), expression, where);
    }

    static String alternateChanceExpression(Sources.Body spawner) {
        Sources.Body method = spawner.block(spawner.find("private static void swapMobAlts\\s*\\("));
        for (int i = method.from(); i < method.to(); i++) {
            Matcher m = ALT_CHANCE.matcher(Sources.stripComment(method.lines().get(i)));
            if (m.find()) {
                return m.group(1).trim();
            }
        }
        throw new IllegalStateException(spawner.path() + ": swapMobAlts declares no altChance");
    }

    /**
     * The alternates from the public map, sorted by name, each cited to its own put and marked
     * reachable when the class is listed as itself in some rotation, which is where the swap
     * looks (a family's abstract class is never listed as itself).
     */
    static List<Codex.RareAlt> alternates(Sources.Body spawner, Map<String, String> canonical, TreeSet<String> listedAsThemselves) {
        TreeMap<String, String> byName = new TreeMap<>();
        for (Map.Entry<Class<? extends Mob>, Class<? extends Mob>> alt : MobSpawner.RARE_ALTS.entrySet()) {
            String key = alt.getKey().getSimpleName();
            if (byName.put(key, Sources.name(alt.getValue())) != null) {
                throw new IllegalStateException("RARE_ALTS keys two classes named " + key);
            }
        }
        List<Codex.RareAlt> alternates = new ArrayList<>();
        for (Map.Entry<String, String> alt : byName.entrySet()) {
            int line = spawner.find("RARE_ALTS\\.put\\(\\s*" + Pattern.quote(alt.getKey()) + "\\.class");
            if (line < 0) {
                throw new IllegalStateException(spawner.path() + ": RARE_ALTS holds " + alt.getKey() + " with no put to cite");
            }
            String className = canonical.getOrDefault(alt.getKey(), alt.getKey());
            alternates.add(new Codex.RareAlt(className, alt.getValue(), listedAsThemselves.contains(className), spawner.citation(line)));
        }
        return alternates;
    }

    static Codex.ChampionRule champion(Path root, Map<String, String> canonical) {
        Sources.Body body = Sources.body(root, ChampionEnemy.class);
        int declaration = body.find("public static void rollForChampion\\s*\\(");
        Sources.Body method = body.block(declaration);
        String where = body.path() + ":" + (declaration + 1);
        List<String> buffs = new ArrayList<>();
        List<Codex.Exclusion> exclusions = new ArrayList<>();
        String counter = null;
        String challenge = null;
        int drawRange = -1;
        int instanceOfs = 0;
        for (int i = method.from(); i < method.to(); i++) {
            String line = Sources.stripComment(method.lines().get(i));
            Matcher b = BUFF.matcher(line);
            while (b.find()) {
                buffs.add(b.group(1));
            }
            Matcher d = DRAW.matcher(line);
            if (d.find()) {
                drawRange = Integer.parseInt(d.group(1));
            }
            if (line.contains("instanceof")) {
                instanceOfs++;
            }
            Matcher e = EXCLUSION.matcher(line);
            if (e.find()) {
                exclusions.add(new Codex.Exclusion(canonical(canonical, e.group(1), where), Integer.parseInt(e.group(2))));
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
            throw new IllegalStateException(where + ": rollForChampion no longer states its counter or its challenge");
        }
        if (drawRange != buffs.size()) {
            throw new IllegalStateException(where + ": the buffs are drawn from Random.Int(" + drawRange + ") but " + buffs.size() + " are named");
        }
        if (instanceOfs != exclusions.size()) {
            throw new IllegalStateException(where + ": " + instanceOfs + " instanceof exclusions and the reader read " + exclusions.size());
        }
        return new Codex.ChampionRule(Challenge.valueOf(challenge), buffs, exclusions, counter, body.citation(declaration));
    }
}
