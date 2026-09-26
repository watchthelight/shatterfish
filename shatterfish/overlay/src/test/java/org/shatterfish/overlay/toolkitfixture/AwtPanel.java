package org.shatterfish.overlay.toolkitfixture;

import java.awt.Rectangle;

/** A deliberate violation for {@code OverlayToolkitTest.the_rule_bites}: a test fixture, never shipped. */
public final class AwtPanel {

    public Rectangle bounds() {
        return new Rectangle(0, 0, 200, 100);
    }
}
