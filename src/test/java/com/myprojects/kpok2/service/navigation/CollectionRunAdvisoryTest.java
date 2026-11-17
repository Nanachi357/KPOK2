package com.myprojects.kpok2.service.navigation;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CollectionRunAdvisoryTest {

    @Test
    void ordinaryRunNeedsNoWarning() {
        CollectionRunAdvisory advisory = CollectionRunAdvisory.assess(99, 4);

        assertEquals(CollectionRunAdvisory.Kind.NONE, advisory.kind());
        assertEquals(25, advisory.parallelRounds());
        assertFalse(advisory.requiresWarning());
    }

    @Test
    void largeRunWarnsWithoutChangingTheRequestedCount() {
        CollectionRunAdvisory advisory = CollectionRunAdvisory.assess(100, 3);

        assertEquals(CollectionRunAdvisory.Kind.LARGE, advisory.kind());
        assertEquals(100, advisory.attempts());
        assertEquals(34, advisory.parallelRounds());
        assertTrue(advisory.requiresWarning());
    }

    @Test
    void unlimitedRunAlwaysWarns() {
        CollectionRunAdvisory advisory = CollectionRunAdvisory.assess(0, 10);

        assertEquals(CollectionRunAdvisory.Kind.UNLIMITED, advisory.kind());
        assertTrue(advisory.requiresWarning());
    }

    @Test
    void invalidSettingsAreRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> CollectionRunAdvisory.assess(-1, 2));
        assertThrows(IllegalArgumentException.class,
                () -> CollectionRunAdvisory.assess(10, 0));
    }
}
