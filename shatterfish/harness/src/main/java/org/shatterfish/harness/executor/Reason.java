package org.shatterfish.harness.executor;

/**
 * Why an Action was not applied (ADR-0014). A rejection is a value and never an exception: a Brain
 * that asks for something it cannot have is answered, not crashed, and the Run log records the
 * answer (ADR-0011). Every reason here is decided before the game is touched, so a rejected Action
 * leaves the state exactly as it was.
 */
public enum Reason {

    /** The hero is not waiting for input, so no click a person could make would land either. */
    NOT_AT_AN_INPUT_WAIT,

    /** The Observation's own set does not carry this Action: it is not on the screen's menu. */
    NOT_OFFERED,

    /**
     * The item reference names an item the pack does not hold at that position any more, which is
     * the desync check of ADR-0014's option 11: a Replay that drifted acts on nothing rather than
     * on the wrong item.
     */
    ITEM_MOVED,

    /**
     * The game opened no selector for an Action that carries a target, or a different one than the
     * target fits. The item was executed, so this is the one reason that can follow a change; it
     * means the tables of {@code ValidActions} and the game disagree, and it is worth a story.
     */
    NO_SELECTOR,

    /** The window in front has no such button, so the answer names nothing a person could press. */
    NO_SUCH_OPTION,

    /**
     * The press cancelled what the hero was doing instead of doing anything of its own. The wait
     * and rest buttons ask {@code GameScene.cancel()} first and stop there when it answers true,
     * which it does while the hero holds an action, is resting, or has a selector open
     * ({@code core/.../scenes/GameScene.java:1723-1736}; {@code …/ui/Toolbar.java:201-204}). The
     * game did change — that is what cancelling is — so this rejection, like {@code NO_SELECTOR},
     * can follow a change.
     */
    CANCELLED_INSTEAD,

    /**
     * The kind has no path at this tag: the armour ability, whose call belongs to the ability
     * stories, and {@code MoveTo}, which is a human's click on a distant cell and never the bot's.
     * Neither is offered by the valid set, so this is what a Brain gets for building one by hand.
     * The completeness test names the human inputs that have no Action kind at all, which is the
     * other half of FR-4.
     */
    UNSUPPORTED
}
