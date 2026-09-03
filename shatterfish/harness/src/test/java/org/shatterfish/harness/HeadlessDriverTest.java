package org.shatterfish.harness;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class HeadlessDriverTest {

    @Test
    void bootsHeadlessBackendAndExits() {
        HeadlessDriver.Boot boot = HeadlessDriver.boot();
        assertEquals("HeadlessDesktop", boot.applicationType());
    }
}
