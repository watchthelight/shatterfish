package org.shatterfish.brain;

import org.shatterfish.api.Action;
import org.shatterfish.api.Codex;
import org.shatterfish.api.HeroClass;
import org.shatterfish.api.Observation;
import org.shatterfish.api.PromptKind;
import org.shatterfish.api.RunLog;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * How the Brain answers each kind of Prompt (story 4.11, FR-31). Every kind the Observation can
 * carry has a rule here, chosen by {@link #rule}, whose switch names every kind: a kind the api
 * gains and this class does not name is a build failure, and a Prompt whose rule finds nothing it
 * recognises is a {@link BrainError}, which the Run ends on as a Brain error rather than stalling.
 *
 * <p>The rules, by kind:
 * <ul>
 *   <li><b>general</b> (talent, quest, alchemy, chasm, harmful potion, resurrection, item, other,
 *       message): story 4.1's rule as stories 4.8 and 4.10 and issue #170 left it. "yes" to the
 *       broken seal's transfer, and to the chasm's jump only when the Brain meant one, else decline
 *       where a declining answer is offered (any confirmation is also declined by "No, I changed my
 *       mind", the chasm's among them, except the unknown scroll's cancel, whose "Yes, I'm positive"
 *       is the only answer that closes it), else the lowest answer, else dismiss.</li>
 *   <li><b>subclass</b>: the subclass {@link #SUBCLASSES} names for the hero's class, then "yes"
 *       to the game's are-you-sure.</li>
 *   <li><b>shop</b>: leave. The Brain has no model of prices, the shopkeeper's first option is to
 *       sell, which opens the bag, and the buy window's only button spends gold.</li>
 *   <li><b>upgrade</b>: upgrade, when the window is the one the read onto an item opened; the window
 *       the game chains after it, while more upgrade items are held, is confirmed when its item is the
 *       worn armour, where every upgrade goes (story 4.13), and is a Brain error otherwise.</li>
 *   <li><b>guess</b>: the identity the Beliefs rate likeliest for the item the window names, if the
 *       window offers it; else leave, which spends nothing.</li>
 *   <li><b>spell</b>: leave. The section carries no spell, since the Brain casts none.</li>
 * </ul>
 */
final class Answers {

    private Answers() {
    }

    /** A Prompt the Brain cannot answer: a Brain error, never a stall (story 4.11). */
    static final class BrainError extends org.shatterfish.api.Decider.CannotDecide {
        private static final long serialVersionUID = 1L;

        BrainError(String message) {
            super(message);
        }
    }

    /** The rules by which Prompts are answered. */
    enum Rule {
        GENERAL, SUBCLASS, SHOP, UPGRADE, GUESS, SPELL
    }

    /**
     * The rule for a kind of Prompt. The switch names every kind, so a kind the api gains is a
     * compile error here until it is given a rule; {@link PromptKind#NONE} is no Prompt, and asking
     * for its rule is a Brain error.
     */
    static Rule rule(PromptKind kind) {
        return switch (kind) {
            case NONE -> throw new BrainError("no Prompt is open, so there is nothing to answer");
            case SUBCLASS -> Rule.SUBCLASS;
            case SHOP -> Rule.SHOP;
            case UPGRADE -> Rule.UPGRADE;
            case GUESS -> Rule.GUESS;
            case SPELL -> Rule.SPELL;
            case TALENT, QUEST, ALCHEMY, CHASM_JUMP, HARMFUL_POTION, RESURRECTION, ITEM, OTHER, MESSAGE ->
                    Rule.GENERAL;
        };
    }

    /**
     * The subclass chosen for each class, by the name its button shows
     * ({@code core/.../actors/hero/HeroClass.java:87-92}; the buttons are the subclasses' short
     * descriptions, {@code core/.../windows/WndChooseSubclass.java:100-120}, each of which names its
     * subclass, {@code core/src/main/assets/messages/actors/actors.properties:838-871}). The Brain
     * fights in melee and uses no ability, so each is the subclass that pays off without being
     * asked to: the Berserker's rage and the Battlemage's staff effects come with melee, the
     * Assassin's prepared strike with the invisibility the Rogue already has, the Warden's grass
     * with walking, the Champion's second weapon and extra charges passively, and the Paladin's
     * spells are short-range. A judgement, not a Codex fact; a later strategy story may revisit it.
     */
    static final Map<HeroClass, String> SUBCLASSES = Map.of(
            HeroClass.WARRIOR, "Berserker",
            HeroClass.MAGE, "Battlemage",
            HeroClass.ROGUE, "Assassin",
            HeroClass.HUNTRESS, "Warden",
            HeroClass.DUELIST, "Champion",
            HeroClass.CLERIC, "Paladin");

    /** The upgrade window's confirming button ({@code core/src/main/assets/messages/windows/windows.properties}, {@code wndupgrade.upgrade}). */
    static final String UPGRADE = "upgrade";

    /** Every way of closing {@code observation}'s Prompt the screen offers, best first. */
    static List<RunLog.Choice> ranked(Observation observation, Memory memory, List<Action> offered,
                                      Codex.Knowledge knowledge) {
        PromptKind kind = observation.prompt().kind();
        List<RunLog.Choice> ranked = switch (rule(kind)) {
            case GENERAL -> general(observation, memory, offered);
            case SUBCLASS -> subclass(observation, offered);
            case SHOP, SPELL -> leave(observation, offered);
            case UPGRADE -> upgrade(observation, memory, offered);
            case GUESS -> guess(observation, offered, knowledge);
        };
        if (ranked.isEmpty()) {
            throw new BrainError("the " + kind + " Prompt \"" + observation.prompt().title() + "\" offers "
                    + observation.prompt().options() + " and no answer its rule recognises");
        }
        return ranked;
    }

    /**
     * Story 4.1's rule, as story 4.8 left it: affirm the seal, else decline, else the lowest answer, else
     * dismiss. Issue #170: "No, I changed my mind" declines whatever the kind, and the chasm's "yes" is
     * pressed only for a jump the Brain meant ({@link #jumpMeant}).
     */
    static List<RunLog.Choice> general(Observation observation, Memory memory, List<Action> offered) {
        List<String> labels = observation.prompt().options();
        String title = observation.prompt().title().strip();
        List<RunLog.Choice> ranked = new ArrayList<>();
        PromptKind kind = observation.prompt().kind();
        if (kind == PromptKind.CHASM_JUMP && jumpMeant(observation, memory)) {
            for (Action action : offered) {
                if (action instanceof Action.AnswerPrompt answer && startsWithYes(label(labels, answer))) {
                    ranked.add(new RunLog.Choice(answer, Policies.CERTAIN, "jump: " + label(labels, answer)));
                }
            }
        }
        if (AFFIRMED.stream().anyMatch(title::equalsIgnoreCase)) {
            for (Action action : offered) {
                if (action instanceof Action.AnswerPrompt answer && YES.equalsIgnoreCase(label(labels, answer))) {
                    ranked.add(new RunLog.Choice(answer, Policies.CERTAIN, "affirm: " + label(labels, answer)));
                }
            }
        }
        List<Action.AnswerPrompt> declining = new ArrayList<>();
        List<Action.AnswerPrompt> answers = new ArrayList<>();
        Action dismiss = null;
        boolean item = kind == PromptKind.ITEM || kind == PromptKind.HARMFUL_POTION;
        boolean scrollCancel = item && SCROLL_CANCEL.equals(observation.prompt().text().strip());
        for (Action action : offered) {
            if (action instanceof Action.AnswerPrompt answer) {
                String said = label(labels, answer);
                boolean changedMind = ITEM_DECLINE.equalsIgnoreCase(said);
                // A confirmation is declined by "No, I changed my mind", unless it is the scroll's
                // cancel, where that answer would reopen the picker (story 4.10). Story 4.10 read it for
                // the item confirmations alone, and the chasm's, which draws the same words
                // (levels.properties:3-4), was answered by its lowest button, "Yes, I know what I'm
                // doing": a jump nobody meant (issue #170).
                if (changedMind && scrollCancel) {
                    continue;
                }
                (declines(said) || changedMind ? declining : answers).add(answer);
            } else if (action instanceof Action.DismissPrompt) {
                dismiss = action;
            }
        }
        declining.sort(Comparator.comparingInt(Action.AnswerPrompt::option));
        answers.sort(Comparator.comparingInt(Action.AnswerPrompt::option));
        boolean affirmed = !ranked.isEmpty();
        boolean jumping = kind == PromptKind.CHASM_JUMP && affirmed;
        for (Action.AnswerPrompt answer : declining) {
            ranked.add(new RunLog.Choice(answer, ranked.isEmpty() ? Policies.CERTAIN : 0,
                    "decline: " + label(labels, answer)));
        }
        for (Action.AnswerPrompt answer : answers) {
            String label = label(labels, answer);
            if (affirmed && YES.equalsIgnoreCase(label) || jumping && startsWithYes(label)) {
                continue;
            }
            ranked.add(new RunLog.Choice(answer, ranked.isEmpty() ? Policies.CERTAIN : 0,
                    "answer: " + (label.isEmpty() ? Integer.toString(answer.option()) : label)));
        }
        if (dismiss != null) {
            ranked.add(new RunLog.Choice(dismiss, ranked.isEmpty() ? Policies.CERTAIN : 0, "dismiss"));
        }
        return ranked;
    }

    /**
     * Whether the chasm's question follows a jump the Brain meant (issue #170): the last Action it handed
     * over was a Step onto a cell this screen draws as a chasm. {@code Brain.decide} offers no Policy a
     * Step onto a chasm, so none reaches the game today, and the question is answered "no"; a Policy that
     * one day means to descend by jumping hands over that Step past the filter, and its "yes" is then
     * pressed here. A question that follows anything else -- no Action of the Brain's, or a Step onto a
     * cell the screen did not draw as a chasm -- is one the Brain did not ask for, and is declined.
     */
    static boolean jumpMeant(Observation observation, Memory memory) {
        return memory != null && Beliefs.STEP.equals(memory.last()) && memory.stepped() >= 0
                && Explore.chasm(observation, memory.stepped());
    }

    /**
     * The subclass the hero's class takes, then "yes" to the are-you-sure that follows it
     * ({@code WndChooseSubclass.java:103-116}, whose buttons read "Yes, I've made my choice." and
     * "No, I'll decide later.", {@code windows.properties:39-40}).
     */
    static List<RunLog.Choice> subclass(Observation observation, List<Action> offered) {
        List<String> labels = observation.prompt().options();
        String wanted = SUBCLASSES.get(observation.header().heroClass());
        List<RunLog.Choice> ranked = new ArrayList<>();
        for (Action action : offered) {
            if (action instanceof Action.AnswerPrompt answer && names(label(labels, answer), wanted)) {
                ranked.add(new RunLog.Choice(answer, Policies.CERTAIN, "subclass: " + wanted));
                return ranked;
            }
        }
        for (Action action : offered) {
            if (action instanceof Action.AnswerPrompt answer && startsWithYes(label(labels, answer))
                    && names(observation.prompt().title(), wanted)) {
                ranked.add(new RunLog.Choice(answer, Policies.CERTAIN, "affirm: " + wanted));
                return ranked;
            }
        }
        return ranked;
    }

    /** The windows the Brain leaves, as its reasons name them. */
    private static final Map<PromptKind, String> WINDOWS = Map.of(
            PromptKind.SHOP, "shop", PromptKind.GUESS, "guess", PromptKind.SPELL, "spell");

    /** Leave the window by the back key, which is all these Prompts need (ValidActions.CLOSED_BY_BACK). */
    static List<RunLog.Choice> leave(Observation observation, List<Action> offered) {
        List<RunLog.Choice> ranked = new ArrayList<>();
        for (Action action : offered) {
            if (action instanceof Action.DismissPrompt) {
                ranked.add(new RunLog.Choice(action, Policies.CERTAIN, LEAVE + WINDOWS.get(observation.prompt().kind())));
            }
        }
        return ranked;
    }

    /**
     * The upgrade window's upgrade button (WndUpgrade.java:431-457), when the window is the one the last
     * Action opened by reading onto an item. After an upgrade the game opens the window again for the
     * same item while another upgrade item is held (WndUpgrade.java:447-455), so confirming whatever
     * upgrade window shows would spend every scroll held on one item. That chained window is a Brain
     * error: its other button, "Back", reopens the item selector (WndUpgrade.java:461-471), a window
     * no Action answers, so there is no answer that leaves the Run where a person would leave it.
     */
    static List<RunLog.Choice> upgrade(Observation observation, Memory memory, List<Action> offered) {
        // The chain on a worn weapon or armour is accepted (story 4.13). The test-item Policy reads a
        // known scroll of upgrade onto the worn weapon or armour (TestItem.target) and an unknown one onto
        // the armour, so a chained window spends the next scroll on the item the first went onto: the
        // whole stack goes there, not shared between the two as TestItem.target would share it. It is
        // still the better answer: "Back" reopens a selector no Action answers. The item is known by its
        // slot when the read was handed over, not by its name, which a lifted curse changes.
        boolean chainOnArmour = memory.last().equals(ANSWERED) && memory.windows().worn();
        if (!READ_ONTO.equals(memory.last()) && !chainOnArmour) {
            throw new BrainError("the upgrade window opened again after " + (memory.last().isEmpty()
                    ? "no Action" : memory.last()) + ", not after reading onto an item: confirming it would spend"
                    + " another upgrade on " + (memory.windows().target().isEmpty() ? "the same item"
                    : memory.windows().target()) + ", and backing out opens a selector no Action answers");
        }
        List<String> labels = observation.prompt().options();
        String item = memory.windows().target().isEmpty() ? "item" : memory.windows().target();
        List<RunLog.Choice> ranked = new ArrayList<>();
        for (Action action : offered) {
            if (action instanceof Action.AnswerPrompt answer && UPGRADE.equalsIgnoreCase(label(labels, answer))) {
                ranked.add(new RunLog.Choice(answer, Policies.CERTAIN, "upgrade: " + item));
            }
        }
        return ranked;
    }

    /** The kind of the Action that reads a scroll onto an item, as {@link Beliefs#kind} names it. */
    static final String READ_ONTO = "UseItemOn";

    /** The kind of an answer to a Prompt, as {@link Beliefs#kind} names it. */
    static final String ANSWERED = "AnswerPrompt";

    /** The start of the reason the prompt Policy gives for leaving a window. */
    static final String LEAVE = "leave: ";

    /**
     * The identity the Beliefs rate likeliest for the item the guess window names in its title
     * (StoneOfIntuition.java:101-104), pressed where the window first offers it: the icon, and on the
     * next wait the guess button, which the window lists first once it shows and labels with the same
     * name (StoneOfIntuition.java:196-199). With no odds for the item, or its likeliest identity not
     * among the icons (an exotic item, which the Codex's families do not cover), the window is left,
     * which spends nothing.
     */
    static List<RunLog.Choice> guess(Observation observation, List<Action> offered, Codex.Knowledge knowledge) {
        List<String> labels = observation.prompt().options();
        String item = observation.prompt().title().strip();
        String likeliest = null;
        if (knowledge != null) {
            for (Beliefs.Guess guess : Beliefs.identities(observation, knowledge)) {
                if (guess.label().equalsIgnoreCase(item) && !guess.odds().isEmpty()) {
                    likeliest = guess.odds().get(0).name();
                    break;
                }
            }
        }
        List<RunLog.Choice> ranked = new ArrayList<>();
        if (likeliest != null) {
            Action.AnswerPrompt first = null;
            for (Action action : offered) {
                if (action instanceof Action.AnswerPrompt answer && likeliest.equalsIgnoreCase(label(labels, answer))
                        && (first == null || answer.option() < first.option())) {
                    first = answer;
                }
            }
            if (first != null) {
                ranked.add(new RunLog.Choice(first, Policies.CERTAIN, "guess: " + likeliest));
            }
        }
        for (RunLog.Choice leave : leave(observation, offered)) {
            ranked.add(new RunLog.Choice(leave.action(), ranked.isEmpty() ? Policies.CERTAIN : 0, leave.why()));
        }
        return ranked;
    }

    /**
     * The answers that decline, as the Prompt labels them. A Prompt the Brain does not understand is
     * usually a confirmation -- jump into the chasm, drink the unknown potion, leave the item behind
     * -- and declining is what leaves the Run as it was.
     */
    private static final List<String> DECLINING = List.of("no", "cancel", "never mind", "not now");

    /**
     * The Prompts, by title, whose "yes" is what leaves the Run as it was (story 4.8). The Warrior
     * putting on new armour is asked whether to move the broken seal from the armour coming off
     * (Armor.java:261-283, titled with the seal's name, items.properties:2392); "no" leaves the seal
     * on the armour in the pack, where it does nothing for him (items.properties:96).
     */
    private static final List<String> AFFIRMED = List.of("broken seal");

    /** The answer that affirms such a Prompt. */
    private static final String YES = "yes";

    /**
     * The one item confirmation the general rule affirms (story 4.10): an unknown inventory scroll read
     * without a target asks this when its picker is sent away, and "Yes, I'm positive" consumes it,
     * where "No, I changed my mind" reopens a picker no Action answers (InventoryScroll.java:52-80,
     * :137-139; items.properties:1155-1157).
     */
    static final String SCROLL_CANCEL = "Do you really want to cancel this scroll usage? The scroll wasn't previously"
            + " identified, so it will be consumed anyway.";

    /**
     * The answer that declines every other item confirmation (story 4.10): a known harmful potion's
     * drink and a beneficial potion's throw (Potion.java:239-252, :265-281; items.properties:740-741),
     * and the scroll's cancel read otherwise.
     */
    static final String ITEM_DECLINE = "No, I changed my mind";

    /** Whether a button label declines. Case-blind without a Locale, which the Brain may not read. */
    static boolean declines(String label) {
        String stripped = label.strip();
        return DECLINING.stream().anyMatch(stripped::equalsIgnoreCase);
    }

    /** Whether a label begins with "yes", case-blind. */
    private static boolean startsWithYes(String label) {
        return label.length() >= YES.length() && label.regionMatches(true, 0, YES, 0, YES.length());
    }

    /** Whether {@code text} names {@code name} as a word, case-blind, the game's highlight marks aside. */
    static boolean names(String text, String name) {
        if (name == null) {
            return false;
        }
        String plain = text.replace("_", "");
        for (int at = 0; at + name.length() <= plain.length(); at++) {
            if (plain.regionMatches(true, at, name, 0, name.length())
                    && (at == 0 || !Character.isLetter(plain.charAt(at - 1)))
                    && (at + name.length() == plain.length() || !Character.isLetter(plain.charAt(at + name.length())))) {
                return true;
            }
        }
        return false;
    }

    /** The label of the button an answer presses, or empty when the Prompt draws none for it. */
    static String label(List<String> labels, Action.AnswerPrompt answer) {
        return answer.option() >= 0 && answer.option() < labels.size() ? labels.get(answer.option()).strip() : "";
    }
}
