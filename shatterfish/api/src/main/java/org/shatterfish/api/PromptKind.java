package org.shatterfish.api;

/**
 * The kinds of Prompt a hero can be waiting under: the windows the game opens on its own and
 * waits for the player to answer (ADR-0006's list, and the resurrection window of ADR-0013), or
 * {@link #NONE}. The header carries the kind; the prompt section of story 1.7 carries the text and
 * the options.
 *
 * <p>Story 1.10 maps the windows to kinds: the subclass choice, the quest windows of the ghost,
 * the wandmaker, the imp and the blacksmith, the shop's trade window, the resurrection window,
 * and a window of options by the class that opened it, the chasm's jump, a potion's harmful-drink
 * warning, the talents pane's random-talent confirmation. {@link #ALCHEMY} is never produced at
 * the tag, since alchemy is a scene and not a window. Two members close the list: {@link #ITEM}
 * for a window of options an item opened, a confirmation or a choice such as the enchantment's
 * three, and {@link #OTHER} for one from an origin the mapping does not name, whose title, text
 * and labels the section still carries as the screen draws them.
 */
public enum PromptKind {
    NONE, SUBCLASS, TALENT, QUEST, SHOP, ALCHEMY, CHASM_JUMP, HARMFUL_POTION, RESURRECTION, ITEM, OTHER
}
