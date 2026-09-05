package org.shatterfish.api;

/**
 * The kinds of Prompt a hero can be waiting under: the windows the game opens on its own and
 * waits for the player to answer (ADR-0006's list, and the resurrection window of ADR-0013), or
 * {@link #NONE}. The header carries the kind; the prompt section of story 1.7 carries the text and
 * the options.
 */
public enum PromptKind {
    NONE, SUBCLASS, TALENT, QUEST, SHOP, ALCHEMY, CHASM_JUMP, HARMFUL_POTION, RESURRECTION
}
