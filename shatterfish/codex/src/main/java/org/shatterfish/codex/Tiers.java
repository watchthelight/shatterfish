package org.shatterfish.codex;

import com.shatteredpixel.shatteredpixeldungeon.items.Generator;
import org.shatterfish.api.Codex;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The tier tables (story 2.4): the generator's floor-set tier literal, private and so read from
 * source, one row of five weights per floor set ({@code depth / 5}, gated to the last row in
 * all three draws, which the reader holds), and the rules that draw by it: an armor picks its
 * class by the drawn index into the armor category's class list, a weapon or a missile picks
 * the tier category of the drawn index, each tier array cited to its own declaration.
 */
final class Tiers {

    private static final Pattern ROW = Pattern.compile("\\{\\s*(\\d+(?:\\s*,\\s*\\d+)*)\\s*\\}");

    private Tiers() {
    }

    static Codex.Tiers read(Path root) {
        Sources.Body generator = Sources.body(root, Generator.class);
        int literal = generator.find("float\\[\\]\\[\\] floorSetTierProbs\\s*=");
        if (literal < 0) {
            throw new IllegalStateException("Generator declares no floorSetTierProbs literal");
        }
        Sources.Body block = generator.block(literal);
        List<Codex.TierRow> rows = new ArrayList<>();
        for (int i = block.from() + 1; i < block.to(); i++) {
            Matcher row = ROW.matcher(Sources.stripComment(block.lines().get(i)));
            if (row.find()) {
                List<Integer> weights = new ArrayList<>();
                for (String weight : row.group(1).split(",")) {
                    weights.add(Integer.parseInt(weight.trim()));
                }
                int set = rows.size();
                rows.add(new Codex.TierRow(set, set == 0 ? 1 : set * 5, set * 5 + 4, weights));
            }
        }
        if (rows.size() != 5) {
            throw new IllegalStateException("the tier literal has " + rows.size() + " rows, not five; the reader knows five floor sets");
        }
        rows.set(4, new Codex.TierRow(4, 20, Mobs.MAX_DEPTH, rows.get(4).weights()));
        String armorAnchor = "public static Armor randomArmor\\s*\\(\\s*int floorSet\\s*\\)";
        Codex.Rule armor = rule(generator, "armor", armorAnchor);
        Codex.Rule weapon = rule(generator, "weapon", "public static MeleeWeapon randomWeapon\\s*\\(\\s*int floorSet\\s*,\\s*boolean useDefaults\\s*\\)");
        Codex.Rule missile = rule(generator, "missile", "public static MissileWeapon randomMissile\\s*\\(\\s*int floorSet\\s*,\\s*boolean useDefaults\\s*\\)");
        String gating = "GameMath.gate(0, floorSet, floorSetTierProbs.length-1)";
        for (Codex.Rule drawn : List.of(armor, weapon, missile)) {
            if (!drawn.expression().contains("Random.chances(floorSetTierProbs[floorSet])")) {
                throw new IllegalStateException("the " + drawn.what() + " rule no longer draws by the tier literal: " + drawn.expression());
            }
            if (!drawn.expression().contains(gating)) {
                throw new IllegalStateException("the " + drawn.what() + " rule no longer gates the floor set to the literal: " + drawn.expression());
            }
        }
        int gateLine = generator.block(generator.find(armorAnchor)).find("GameMath\\.gate\\(0, floorSet, floorSetTierProbs\\.length-1\\)");
        if (gateLine < 0) {
            throw new IllegalStateException("randomArmor no longer gates the floor set to the literal");
        }
        Codex.Rule gate = new Codex.Rule("gate", Sources.stripComment(generator.lines().get(gateLine)).trim(), generator.citation(gateLine));
        int tiers = generator.find("public static final Category\\[\\] wepTiers\\s*=");
        int missiles = generator.find("public static final Category\\[\\] misTiers\\s*=");
        if (tiers < 0 || missiles < 0) {
            throw new IllegalStateException("Generator declares no wepTiers or misTiers");
        }
        List<Codex.Rule> arrays = List.of(new Codex.Rule("wepTiers", Sources.text(generator.block(tiers)), generator.citation(tiers)),
                new Codex.Rule("misTiers", Sources.text(generator.block(missiles)), generator.citation(missiles)));
        return new Codex.Tiers(rows, generator.citation(literal), gate, armor, weapon, missile, arrays);
    }

    private static Codex.Rule rule(Sources.Body generator, String what, String anchor) {
        int line = generator.find(anchor);
        if (line < 0) {
            throw new IllegalStateException("Generator declares no " + what + " draw by floor set");
        }
        return new Codex.Rule(what, Sources.text(generator.block(line)), generator.citation(line));
    }
}
