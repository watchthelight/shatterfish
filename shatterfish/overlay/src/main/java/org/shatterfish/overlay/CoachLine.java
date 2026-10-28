package org.shatterfish.overlay;

import org.shatterfish.api.Observation;
import org.shatterfish.api.RunLog;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * The coach's sentence (story 8.8, FR-47, UJ-4): the Brain's choice put as one plain line in the game's own
 * words, {@code "read scroll (KHIT): worst case survivable"}. A pure function of a {@link RunLog.Decision} and
 * the Observation the Decision was made on, and of nothing else: no Brain, no game, no Oracle flag, so it can
 * say only what the bot knows and is the same line with the Oracle on or off.
 *
 * <p>The line is the Action in the words {@link ActionText} uses (an unidentified item is the name the screen
 * draws, its appearance, never its identity), a colon, and one reason clause. The clause is chosen from the
 * Policy's own name and the lead of the choice's one-line {@code why} (its grammar is held by
 * {@code DecisionShapeTest}: "retreat: stairs", "frontier 7", "test: scroll (scroll_hp_per_mille)"), and a
 * Safety flag the clause does not already say is added after it ("hurt", "hungry", "starving"). No number
 * the person would have to decode is in a clause: the distances and counts of a {@code why} are dropped.
 *
 * <p>Every clause is a {@link Template}, listed in {@link #templates()}, each with the Brain Rule (a row of
 * {@code docs/brain-rules.md}) its claim rests on. {@code CoachLineTest} requires a template for every Policy
 * name the Brain arbitrates, so a new Policy without a sentence fails the build and does not print the Action
 * alone by silence; a Decision whose {@code why} no template recognises yields the Action alone, and the
 * Action alone is never given an invented reason.
 */
final class CoachLine {

    /**
     * The longest line, in characters: the Panel's text is about 35 characters wide at its narrowest
     * (UX-DR2, {@link PanelLayout#MIN_WIDTH}), and the sentence must wrap to no more than two lines.
     */
    static final int MAX_LENGTH = 70;

    /** The rule slug of a template that rests on no game claim (the fallback says only that nothing above fired). */
    static final String NO_CLAIM = "none";

    /**
     * One reason clause.
     *
     * @param policy the Policy name whose Decisions it phrases
     * @param key    the lead of the {@code why} it answers, with a detail after a slash where the lead alone is not enough
     * @param text   the clause, lower case, with no digit
     * @param rule   the slug of the row of {@code docs/brain-rules.md} the clause rests on, or {@link #NO_CLAIM}
     */
    record Template(String policy, String key, String text, String rule) {
    }

    private static final List<Template> TEMPLATES = List.of(
            new Template("answer-prompt", "jump", "the chasm is the way down", "chasm-plunge"),
            new Template("answer-prompt", "affirm", "to confirm what it planned", "option-press-closes-window"),
            new Template("answer-prompt", "decline", "to say no to the question", "item-confirmation-decline"),
            new Template("answer-prompt", "answer", "to answer the question", "option-press-closes-window"),
            new Template("answer-prompt", "dismiss", "to close the window", "option-press-closes-window"),
            new Template("answer-prompt", "subclass", "to pick a subclass", "subclass-window"),
            new Template("answer-prompt", "upgrade", "to upgrade the best gear", "upgrade-window"),
            new Template("answer-prompt", "guess", "the likeliest kind", "guess-window"),
            new Template("answer-prompt", "leave", "to leave the window", "shun-no-time-windows"),
            new Template("heal", "heal", "hurt, with enemies near", "heal-threshold"),
            new Template("douse", "douse/water", "burning, water is near", "burning-douse"),
            new Template("douse", "douse/ground", "burning, bare ground is near", "burning-douse"),
            new Template("zap", "zap", "an enemy is in staff range", "staff-zap"),
            new Template("shoot", "shoot", "an enemy is in bow range", "bow-shoot"),
            new Template("ranged", "throw", "to hit from a distance", "missile-throw"),
            new Template("one-ply", "heal", "a drink is worth it", "heal-threshold"),
            new Template("one-ply", "retreat", "to back away from the fight", "heal-threshold"),
            new Template("one-ply", "reposition", "to fight from a better cell", "adjacent-chebyshev"),
            new Template("one-ply", "attack", "the fight is worth it", "hit-chance"),
            new Template("fight", "chase", "to follow the enemy", "sleeping-mob-out-of-view"),
            new Template("fight", "hold", "to hold a good spot", "adjacent-chebyshev"),
            new Template("fight", "retreat/stairs", "to leave by the stairs", "depth-1-up-stairs"),
            new Template("fight", "retreat/water", "to get out of the water", "water-bound-enemies"),
            new Template("fight", "retreat", "to back away from the fight", "heal-threshold"),
            new Template("fight", "attack", "an enemy is next to it", "hunting-adjacent-fights"),
            new Template("fight", "chokepoint", "to fight where few reach it", "adjacent-chebyshev"),
            new Template("fight", "approach", "to close in on the enemy", "hunting-adjacent-fights"),
            new Template("fight", "cornered", "cornered, so it fights", "faster-enemy-no-outrunning"),
            new Template("eat", "eat", "the hunger icon says eat", "hunger-icon-states"),
            new Template("eat", "eat/water", "water first, then the meat", "blandfruit-mystery-meat"),
            new Template("test-item", "escape", "to get clear of the effect", "test-spot-door-water"),
            new Template("test-item", "upgrade", "to upgrade the best gear", "upgrade-allocation"),
            new Template("test-item", "drink", "a known gain, drunk at once", "strength-experience-potions"),
            new Template("test-item", "cell", "to reach a safe place to test", "test-spot-door-water"),
            new Template("test-item", "rest", "to rest after a test", "regen-rest-test"),
            new Template("test-item", "test/healing", "it may heal, and it is hurt", "unknown-potion-heal-half"),
            new Template("test-item", "test/gain", "it may be a gain", "strength-experience-potions"),
            new Template("test-item", "test/inventory", "gear to pick, so worth a read", "scroll-picker"),
            new Template("test-item", "test/scroll", "full health, so a read is safe", "unknown-scroll-full-health"),
            new Template("test-item", "test", "worst case survivable", "safe-test-worst-cases"),
            new Template("pick-up", "item", "an item worth taking", "pickup-equip-weights"),
            new Template("pick-up", "take", "an item worth taking", "pickup-equip-weights"),
            new Template("open-chest", "open", "a chest to open", "chest-open-key"),
            new Template("open-chest", "chest", "a chest to open", "chest-open-key"),
            new Template("equip", "wear", "better gear to wear", "pickup-equip-weights"),
            new Template("descend", "spent", "the floor is done", "time-and-hunger-costs"),
            new Template("descend", "hungry", "hungry, food lies below", "floor-food-guarantees"),
            new Template("descend", "overstayed", "it has stayed long enough", "time-and-hunger-costs"),
            new Template("descend", "unlock", "to open the locked exit", "goo-worn-key"),
            new Template("descend", "no exit", "to search for a hidden exit", "hidden-exit-search"),
            new Template("descend", "rest", "to rest before going down", "rest-before-descend"),
            new Template("explore", "frontier", "to uncover more of the floor", "step-cells"),
            new Template("explore", "search", "to look for a hidden way", "search-radius-cost"),
            new Template("explore", "away", "to keep clear of fire or gas", "gas-fire-avoid"),
            new Template("explore", "stuck", "it cannot move as asked", "root-vertigo-refusal"),
            new Template("explore", "rest", "to rest before going down", "rest-before-descend"),
            new Template("plunge", "jump", "no way down but the chasm", "chasm-plunge"),
            new Template("plunge", "rest", "to rest before the jump", "chasm-plunge"),
            new Template("fight", "dodge", "to dodge the pump", "goo-pump"),
            new Template("fight", "goo/attack", "to fight Goo", "goo-engage"),
            new Template("fight", "goo/wait", "to wait for Goo's move", "goo-pump"),
            new Template("fight", "goo/approach", "to close in on Goo", "goo-engage"),
            new Template("fallback", "fallback", "nothing better to do", NO_CLAIM));

    /** The flag phrases, in the order a Decision's flags are tried: the first one the clause does not already say is added. */
    private static final List<String[]> FLAG_PHRASES = List.of(
            new String[] {"starving", "starving"}, new String[] {"hp-low", "hurt"}, new String[] {"hungry", "hungry"});

    private CoachLine() {
    }

    /** Every template, for the docs page and the tests. */
    static List<Template> templates() {
        return TEMPLATES;
    }

    /** The Policy names that have a template, in the order first met. */
    static List<String> policies() {
        List<String> names = new ArrayList<>();
        for (Template template : TEMPLATES) {
            if (!names.contains(template.policy())) {
                names.add(template.policy());
            }
        }
        return names;
    }

    /**
     * The sentence for {@code decision} made on {@code observation} (null observation: the Action in its plainer
     * words, as {@link ActionText} does for a null context). Null for no Decision.
     */
    static String of(RunLog.Decision decision, Observation observation) {
        if (decision == null) {
            return null;
        }
        String action = ActionText.of(decision.chosen().action(), observation);
        Template template = template(decision);
        if (template == null) {
            return action;
        }
        String reason = template.text();
        String flag = flag(decision.flags(), reason);
        String line = action + ": " + reason + (flag == null ? "" : ", " + flag);
        if (line.length() > MAX_LENGTH) {
            line = action + ": " + reason;
        }
        return line.length() > MAX_LENGTH ? action : line;
    }

    /** The template that phrases {@code decision}'s reason, or null when none does (the Action stands alone). */
    static Template template(RunLog.Decision decision) {
        String why = decision.chosen().why();
        if (why == null || why.isBlank()) {
            return null;
        }
        String policy = decision.policy();
        String head = head(why);
        String detail = detail(why);
        String key = switch (policy) {
            case "answer-prompt" -> head;
            case "heal", "zap", "shoot", "ranged" -> policy.equals("ranged") ? "throw" : policy;
            case "douse" -> detail.contains("bare") ? "douse/ground" : "douse/water";
            case "one-ply", "fight" -> fightKey(policy, head, detail);
            case "eat" -> detail.equals("water") ? "eat/water" : "eat";
            case "test-item" -> head.equals("test") ? testKey(why) : head;
            case "pick-up", "open-chest", "equip" -> head;
            case "descend" -> descendKey(head, detail);
            case "explore" -> exploreKey(head);
            case "plunge" -> head.equals("rest") ? "rest" : "jump";
            case "fallback" -> "fallback";
            default -> null;
        };
        if (key == null) {
            return null;
        }
        for (Template template : TEMPLATES) {
            if (template.policy().equals(policy) && template.key().equals(key)) {
                return template;
            }
        }
        return null;
    }

    private static String fightKey(String policy, String head, String detail) {
        if (policy.equals("fight") && head.equals("retreat")) {
            return detail.contains("stairs") ? "retreat/stairs" : detail.startsWith("leave water") ? "retreat/water" : "retreat";
        }
        if (policy.equals("fight") && head.equals("goo")) {
            return detail.startsWith("attack") ? "goo/attack" : detail.startsWith("wait") ? "goo/wait" : "goo/approach";
        }
        return head;
    }

    private static String testKey(String why) {
        if (why.contains("healing_odds")) {
            return "test/healing";
        }
        if (why.contains("gain_odds")) {
            return "test/gain";
        }
        if (why.contains("inventory_odds")) {
            return "test/inventory";
        }
        if (why.contains("scroll_hp")) {
            return "test/scroll";
        }
        return "test";
    }

    private static String descendKey(String head, String detail) {
        return switch (head) {
            case "descend", "exit", "boss exit" -> detail.startsWith("spent") ? "spent"
                    : detail.startsWith("hungry") ? "hungry" : detail.startsWith("overstayed") ? "overstayed" : null;
            case "unlock", "no exit", "rest" -> head;
            default -> null;
        };
    }

    private static String exploreKey(String head) {
        return switch (head) {
            case "frontier", "away", "rest" -> head;
            case "search", "search-spot" -> "search";
            case "rooted", "vertigo" -> "stuck";
            default -> null;
        };
    }

    /** The word, or words, before the first colon of {@code why}, or its first word when it has no colon; lower case. */
    private static String head(String why) {
        int colon = why.indexOf(':');
        String head = colon >= 0 ? why.substring(0, colon) : why.split("\\s+", 2)[0];
        return head.strip().toLowerCase(Locale.ROOT);
    }

    /** What follows the head: after the colon, or after the first word; lower case. */
    private static String detail(String why) {
        int colon = why.indexOf(':');
        String rest = colon >= 0 ? why.substring(colon + 1) : why.contains(" ") ? why.substring(why.indexOf(' ') + 1) : "";
        return rest.strip().toLowerCase(Locale.ROOT);
    }

    /** The first flag phrase of {@code flags} that {@code reason} does not already say, or null. */
    private static String flag(List<String> flags, String reason) {
        for (String[] pair : FLAG_PHRASES) {
            if (flags.contains(pair[0]) && !reason.contains(pair[1])) {
                return pair[1];
            }
        }
        return null;
    }
}
