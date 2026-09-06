package org.shatterfish.api;

import java.util.List;
import java.util.Objects;

/**
 * The open Prompt, if any: its kind, the window's title and text, and the labels of its buttons
 * in the order they are drawn ({@code core/.../windows/WndOptions.java:57}, {@code :92}; ADR-0006,
 * Prompt). An answer is an {@link Action.AnswerPrompt} with the index of a label. With no Prompt
 * open the kind is {@link PromptKind#NONE} and everything else is empty; the header carries the
 * same kind, and the Observation holds the two to each other.
 *
 * @param kind the Prompt's kind
 * @param title the window's title, or empty
 * @param text the window's text, or empty
 * @param options the button labels, in drawing order
 */
public record PromptSection(PromptKind kind, String title, String text, List<String> options) {

    /** No Prompt open. */
    public static final PromptSection NONE = new PromptSection(PromptKind.NONE, "", "", List.of());

    public PromptSection {
        Objects.requireNonNull(kind, "kind");
        title = Canon.text(title, "prompt title");
        text = Canon.text(text, "prompt text");
        options = Canon.positional(options, "prompt options");
        if (kind == PromptKind.NONE) {
            Canon.require(title.isEmpty() && text.isEmpty() && options.isEmpty(),
                    "with no Prompt open there is no title, text or option");
        }
    }
}
