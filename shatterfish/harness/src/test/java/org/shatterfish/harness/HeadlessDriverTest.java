package org.shatterfish.harness;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class HeadlessDriverTest {

    @Test
    void bootsTheHeadlessBackendForThePinnedUpstream() {
        HeadlessDriver.Boot boot = HeadlessDriver.boot();
        assertEquals("HeadlessDesktop", boot.applicationType());
        assertEquals("3.3.8", boot.upstreamVersion(),
                "the harness runs the release docs/UPSTREAM.md pins; if the pin moved, this and the"
                        + " ledger move together");
    }
}
