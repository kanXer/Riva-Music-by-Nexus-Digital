package com.nexusdigital.rivamusic.data

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdateCheckerTest {

    @Test
    fun testVersionComparison() {
        // Multi-part versions: 1.0.10 is greater than 1.0.2
        assertTrue(UpdateChecker.isVersionNewer("1.0.10", "1.0.2"))
        assertFalse(UpdateChecker.isVersionNewer("1.0.2", "1.0.10"))

        // Standard patch updates
        assertTrue(UpdateChecker.isVersionNewer("1.2.3", "1.2.2"))
        assertFalse(UpdateChecker.isVersionNewer("1.2.2", "1.2.3"))

        // Minor and major updates
        assertTrue(UpdateChecker.isVersionNewer("1.3.0", "1.2.2"))
        assertTrue(UpdateChecker.isVersionNewer("2.0.0", "1.2.2"))
        assertFalse(UpdateChecker.isVersionNewer("1.2.2", "2.0.0"))

        // Different segment counts: 1.2 vs 1.2.0, 1.2.1 vs 1.2
        assertFalse(UpdateChecker.isVersionNewer("1.2", "1.2.0"))
        assertFalse(UpdateChecker.isVersionNewer("1.2.0", "1.2"))
        assertTrue(UpdateChecker.isVersionNewer("1.2.1", "1.2"))
        assertFalse(UpdateChecker.isVersionNewer("1.2", "1.2.1"))

        // Exact match
        assertFalse(UpdateChecker.isVersionNewer("1.2.2", "1.2.2"))

        // Stripped suffixes or build metadata
        assertTrue(UpdateChecker.isVersionNewer("1.3.0-beta", "1.2.2"))
        assertFalse(UpdateChecker.isVersionNewer("1.2.2-release", "1.2.2"))
    }
}
