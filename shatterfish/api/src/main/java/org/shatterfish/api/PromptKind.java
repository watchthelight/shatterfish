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
 *
 * <p>{@link #MESSAGE} is the plain message the game shows and a person taps away: the line the
 * sewers post when the hero tries to leave without the amulet, the blacksmith's word when the
 * entrance is reached without a pickaxe. Story 1.10 made such a window a
 * failure of every read, on the reading that only a window the game waits on is a Prompt; story
 * 1.13 found that it waits on these too, since the hero is ready underneath and the Run cannot go
 * on until the window goes. It carries no options, and the one Action it offers is
 * {@code DismissPrompt}.
 *
 * <p>Story 4.11 names three windows an item opens that are not windows of options, and which a Run
 * used to end on as unknown windows:
 * <ul>
 *   <li>{@link #UPGRADE}, the scroll of upgrade's or magical infusion's confirmation of the item
 *       chosen ({@code core/.../windows/WndUpgrade.java:75-486}), with its two buttons, upgrade and
 *       back;</li>
 *   <li>{@link #GUESS}, the stone of intuition's guess
 *       ({@code core/.../items/stones/StoneOfIntuition.java:94-224}), whose options are the item
 *       types it draws as icons, named, and the guess button once one is chosen;</li>
 *   <li>{@link #SPELL}, the Cleric's holy tome's spell list
 *       ({@code core/.../windows/WndClericSpells.java:59-130}), whose spells are icons the section
 *       does not carry, so the one Action it offers is {@code DismissPrompt}.</li>
 * </ul>
 */
public enum PromptKind {
    NONE, SUBCLASS, TALENT, QUEST, SHOP, ALCHEMY, CHASM_JUMP, HARMFUL_POTION, RESURRECTION, ITEM, OTHER,
    MESSAGE, UPGRADE, GUESS, SPELL
}
