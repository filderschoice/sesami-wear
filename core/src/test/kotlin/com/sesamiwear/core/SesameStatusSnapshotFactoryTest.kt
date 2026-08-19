package com.sesamiwear.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SesameStatusSnapshotFactoryTest {
    @Test
    fun `creates snapshot when the key is present`() {
        val snapshot =
            SesameStatusSnapshotFactory.create(
                hasIsLockedKey = true,
                isLocked = true,
                updatedAtEpochMillis = 1000L,
            )

        assertEquals(SesameStatusSnapshot(isLocked = true, updatedAtEpochMillis = 1000L), snapshot)
    }

    @Test
    fun `returns null when the key is absent (never synced)`() {
        val snapshot =
            SesameStatusSnapshotFactory.create(
                hasIsLockedKey = false,
                isLocked = false,
                updatedAtEpochMillis = 0L,
            )

        assertNull(snapshot)
    }
}
