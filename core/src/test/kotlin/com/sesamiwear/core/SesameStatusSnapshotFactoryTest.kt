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
    fun `returns null when nothing was synced at all`() {
        val snapshot =
            SesameStatusSnapshotFactory.create(
                hasIsLockedKey = false,
                isLocked = false,
                updatedAtEpochMillis = 0L,
            )

        assertNull(snapshot)
    }

    @Test
    fun `keeps the lock state and adds the failure when both are synced`() {
        val snapshot =
            SesameStatusSnapshotFactory.create(
                hasIsLockedKey = true,
                isLocked = true,
                updatedAtEpochMillis = 1000L,
                lastFailureName = "AUTH_OR_QUOTA",
            )

        assertEquals(
            SesameStatusSnapshot(
                isLocked = true,
                updatedAtEpochMillis = 1000L,
                lastFailure = SesameStatusFailure.AUTH_OR_QUOTA,
            ),
            snapshot,
        )
    }

    @Test
    fun `creates a failure only snapshot when the state was never fetched`() {
        val snapshot =
            SesameStatusSnapshotFactory.create(
                hasIsLockedKey = false,
                isLocked = false,
                updatedAtEpochMillis = 0L,
                lastFailureName = "COMMUNICATION",
            )

        assertEquals(
            SesameStatusSnapshot(
                isLocked = null,
                updatedAtEpochMillis = null,
                lastFailure = SesameStatusFailure.COMMUNICATION,
            ),
            snapshot,
        )
    }

    @Test
    fun `an unknown failure name is treated as no failure`() {
        val snapshot =
            SesameStatusSnapshotFactory.create(
                hasIsLockedKey = true,
                isLocked = false,
                updatedAtEpochMillis = 1000L,
                lastFailureName = "RETIRED_VALUE",
            )

        assertEquals(SesameStatusSnapshot(isLocked = false, updatedAtEpochMillis = 1000L), snapshot)
    }
}
