package org.shatterfish.codex;

import com.shatteredpixel.shatteredpixeldungeon.items.Generator;
import com.shatteredpixel.shatteredpixeldungeon.items.potions.Potion;
import com.shatteredpixel.shatteredpixeldungeon.items.potions.exotic.ExoticPotion;
import com.shatteredpixel.shatteredpixeldungeon.items.rings.Ring;
import com.shatteredpixel.shatteredpixeldungeon.items.scrolls.Scroll;
import com.shatteredpixel.shatteredpixeldungeon.items.scrolls.exotic.ExoticScroll;
import com.shatteredpixel.shatteredpixeldungeon.items.trinkets.ExoticCrystals;
import org.shatterfish.api.Codex;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The decks (story 2.3): every generator category from the enum's public fields, its two
 * category-deck weights and its class list with the first and second deck weights, cited to
 * the constant and to the class list's assignment; the appearance-label pools of the three
 * identifiable families, read from the {@code put} lines of their private maps and named from
 * the bundle; the exotic swap from the public maps and the chance from its method's source.
 * The generator's Run-mutable deck state ({@code probs}, {@code seed}, {@code dropped},
 * {@code using2ndProbs}) is never read.
 */
final class Decks {

    private static final Pattern PUT = Pattern.compile("^\\s*put\\(\\s*\"(\\w+)\"\\s*,");

    private Decks() {
    }

    static Codex.Decks read(Path root) {
        Sources.Body generator = Sources.body(root, Generator.class);
        Sources.Body categoryBody = generator.block(generator.declaration("Category"));
        List<Codex.CategoryEntry> categories = new ArrayList<>();
        for (Generator.Category category : Generator.Category.values()) {
            int constant = categoryBody.find("^\\s*" + Pattern.quote(category.name()) + "\\s*\\(");
            if (constant < 0) {
                throw new IllegalStateException("Generator.Category." + category.name() + " has no declaration line");
            }
            int classesLine = categoryBody.find("^\\s*" + Pattern.quote(category.name()) + "\\.classes\\s*=");
            List<Codex.Weighted> classes = new ArrayList<>();
            if (category.classes != null) {
                float[] first = category.defaultProbs != null ? category.defaultProbs : category.probs;
                if (first == null || first.length != category.classes.length) {
                    throw new IllegalStateException("Generator.Category." + category.name() + " lists " + category.classes.length
                            + " classes and " + (first == null ? "no" : first.length) + " weights");
                }
                float[] second = category.defaultProbs2;
                if (second != null && second.length != first.length) {
                    throw new IllegalStateException("Generator.Category." + category.name() + "'s second deck has " + second.length + " weights");
                }
                for (int i = 0; i < category.classes.length; i++) {
                    int a = integral(first[i], category.name());
                    int b = second == null ? 0 : integral(second[i], category.name());
                    classes.add(new Codex.Weighted(Sources.name(category.classes[i]), a, b, a + b));
                }
            }
            categories.add(new Codex.CategoryEntry(category.name(), integral(category.firstProb, category.name()),
                    integral(category.secondProb, category.name()), Sources.name(category.superClass), classes,
                    categoryBody.citation(constant), classesLine < 0 ? categoryBody.citation(constant) : categoryBody.citation(classesLine)));
        }
        List<Codex.LabelPool> pools = List.of(pool(root, Potion.class, "colors"), pool(root, Scroll.class, "runes"), pool(root, Ring.class, "gems"));
        return new Codex.Decks(categories, pools, exotic(root));
    }

    private static int integral(float weight, String category) {
        if (weight != Math.rint(weight) || weight < 0) {
            throw new IllegalStateException("Generator.Category." + category + " has a weight that is not a whole number: " + weight);
        }
        return (int) weight;
    }

    /** A family's label pool: the keys in the order of the map's puts, each named from the bundle by the family's key. */
    static Codex.LabelPool pool(Path root, Class<?> family, String field) {
        Sources.Body body = Sources.body(root, family);
        int declaration = body.find("\\b" + field + "\\s*=\\s*new LinkedHashMap");
        if (declaration < 0) {
            throw new IllegalStateException(family.getName() + " declares no " + field + " map");
        }
        Sources.Body map = body.block(declaration);
        List<Codex.Label> labels = new ArrayList<>();
        for (int i = map.from(); i < map.to(); i++) {
            Matcher put = PUT.matcher(Sources.stripComment(map.lines().get(i)));
            if (put.find()) {
                Names.Named name = Names.lookup(root, Names.key(family, Names.lower(put.group(1))));
                labels.add(new Codex.Label(put.group(1), name.value(), map.citation(i), name.citation()));
            }
        }
        if (labels.isEmpty()) {
            throw new IllegalStateException(family.getName() + "." + field + " holds no labels");
        }
        return new Codex.LabelPool(family.getSimpleName(), labels, body.citation(declaration));
    }

    /** The exotic swap: the two public maps as pairs, the chance method's returns as the expression, the chance without the trinket. */
    static Codex.ExoticSwap exotic(Path root) {
        List<Codex.ExoticPair> pairs = new ArrayList<>();
        for (Map.Entry<Class<? extends Potion>, Class<? extends ExoticPotion>> pair : ExoticPotion.regToExo.entrySet()) {
            pairs.add(new Codex.ExoticPair(Sources.name(pair.getKey()), Sources.name(pair.getValue())));
        }
        for (Map.Entry<Class<? extends Scroll>, Class<? extends ExoticScroll>> pair : ExoticScroll.regToExo.entrySet()) {
            pairs.add(new Codex.ExoticPair(Sources.name(pair.getKey()), Sources.name(pair.getValue())));
        }
        Sources.Body body = Sources.body(root, ExoticCrystals.class);
        int declaration = body.find("public static float consumableExoticChance\\s*\\(\\s*int level\\s*\\)");
        if (declaration < 0) {
            throw new IllegalStateException("ExoticCrystals declares no consumableExoticChance(int level)");
        }
        String expression = String.join(" | ", Sources.returns(body.block(declaration)));
        int withoutTrinket = Sources.thousandths(Float.toString(ExoticCrystals.consumableExoticChance(-1)));
        if (withoutTrinket < 0) {
            throw new IllegalStateException("the exotic chance without the trinket is not a number: " + ExoticCrystals.consumableExoticChance(-1));
        }
        return new Codex.ExoticSwap(pairs, expression, withoutTrinket, body.citation(declaration));
    }
}
