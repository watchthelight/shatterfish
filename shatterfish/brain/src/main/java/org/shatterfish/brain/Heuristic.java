package org.shatterfish.brain;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a named threshold, odds, budget or ordering choice that a Policy decides by (story 7.1,
 * FR-18). It changes nothing at run time: it carries a slug and nothing else, so that the link from
 * the choice to the claim behind it is machine-readable.
 *
 * <p>The slug names a row of {@code docs/brain-rules.md}, either a Rule row (the mechanic the value
 * rests on) or a measured-heuristic row (a Results page or idea record that played the value), or
 * the sentinel {@link #UNLINKED} for a value nobody can yet account for. {@code HeuristicLinkTest}
 * finds the annotated fields by reflection over the {@code brain} classes and fails on a slug that
 * names none of these.
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
public @interface Heuristic {

    /** The slug of the unlinked heuristics, which the test lists by name and counts. */
    String UNLINKED = "unlinked";

    /** The slug of the row that accounts for the value, or {@link #UNLINKED}. */
    String value();
}
