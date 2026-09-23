package org.shatterfish.api;

import java.util.Comparator;
import java.util.List;

/**
 * The weights of a Brain's Evaluation, as data (story 4.5, FR-33).
 *
 * <p>A committed file under {@code weights/} holds them; the Rig reads it and hands this value to
 * the Brain at construction, because a Brain cannot open a file. Changing a weight therefore
 * changes what the Brain does without recompiling it, and because the Rig hashes the weights into
 * the Brain's configuration, a Registration tells two weight sets apart.
 *
 * <p>Weights are integers: an Evaluation is a sum of weight times feature, both integers, so two
 * machines compute the same score and the file has one spelling for each value. The score is read in
 * ten-thousandths, as every Decision's score is (ADR-0011), so a weight of 10000 on a feature that is
 * 0 or 1 is one point.
 *
 * @param name    the weight set's name, which is the Brain's name
 * @param version the weight set's version, bumped when a weight changes meaning or value
 * @param terms   one weight per feature, sorted by feature, each feature once
 */
public record Weights(String name, int version, List<Term> terms) {

    /** The version of the file's shape, which the reader checks. */
    public static final int FORMAT = 1;

    /**
     * The largest weight, either way. A feature is at most a few thousand (hit points are counted in
     * thousandths of the maximum), so a weight this size keeps every product and the sum of a dozen
     * of them far inside a {@code long}.
     */
    public static final long MAX_WEIGHT = 1_000_000_000L;

    public Weights {
        name = Canon.text(name, "weight set name");
        Canon.require(name.matches("[a-z][a-z0-9_]*"), "a weight set is named as a Brain is: " + name);
        Canon.require(version >= 1, "a weight set's version starts at 1: " + version);
        terms = Canon.sorted(terms, Comparator.comparing(Term::feature), "terms");
        for (int i = 1; i < terms.size(); i++) {
            Canon.require(!terms.get(i).feature().equals(terms.get(i - 1).feature()),
                    "a feature is weighted once: " + terms.get(i).feature());
        }
    }

    /** The weight of {@code feature}; refuses a feature the set does not state. */
    public long weight(String feature) {
        for (Term term : terms) {
            if (term.feature().equals(feature)) {
                return term.weight();
            }
        }
        throw new IllegalArgumentException("the weight set " + name + " states no weight for " + feature);
    }

    /** The features the set weights, in order. */
    public List<String> features() {
        return terms.stream().map(Term::feature).toList();
    }

    /** The canonical text of the set, which the Rig hashes into the Brain's configuration. */
    public String canonical() {
        JsonWriter out = new JsonWriter();
        out.beginObject();
        out.key("format").value(FORMAT);
        out.key("name").value(name);
        out.key("terms").beginObject();
        for (Term term : terms) {
            out.key(term.feature()).value(term.weight());
        }
        out.endObject();
        out.key("version").value(version);
        out.endObject();
        return out.toJson();
    }

    /**
     * One feature's weight.
     *
     * @param feature the feature's name, as the Evaluation defines it
     * @param weight  the weight, an integer per unit of the feature
     */
    public record Term(String feature, long weight) {

        public Term {
            feature = Canon.text(feature, "feature name");
            Canon.require(feature.matches("[a-z][a-z0-9_]*"), "a feature is named in lower snake case: " + feature);
            Canon.require(weight >= -MAX_WEIGHT && weight <= MAX_WEIGHT,
                    "a weight is at most " + MAX_WEIGHT + " either way: " + feature + " " + weight);
        }
    }
}
