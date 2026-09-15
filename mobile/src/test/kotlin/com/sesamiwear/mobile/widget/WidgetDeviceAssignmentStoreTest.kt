package com.sesamiwear.mobile.widget

import com.sesamiwear.core.SesameDemoMode
import com.sesamiwear.core.SesameWearProtocol
import com.sesamiwear.mobile.state.InMemoryKeyValueStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class WidgetDeviceAssignmentStoreTest {
    private val keyValueStore = InMemoryKeyValueStore()
    private val store = WidgetDeviceAssignmentStore(keyValueStore)

    @Test
    fun `unassigned widget has no device`() {
        assertNull(store.assignedDeviceUuid(1))
    }

    @Test
    fun `assignments are kept per widget instance`() {
        store.assign(1, "uuid-front")
        store.assign(2, "uuid-back")

        assertEquals("uuid-front", store.assignedDeviceUuid(1))
        assertEquals("uuid-back", store.assignedDeviceUuid(2))
        assertEquals("uuid-front", WidgetDeviceAssignmentStore(keyValueStore).assignedDeviceUuid(1))
    }

    @Test
    fun `reassigning replaces the previous device`() {
        store.assign(1, "uuid-front")
        store.assign(1, "uuid-back")

        assertEquals("uuid-back", store.assignedDeviceUuid(1))
    }

    @Test
    fun `removing deleted widgets keeps the others`() {
        store.assign(1, "uuid-front")
        store.assign(2, "uuid-back")
        store.assign(3, "uuid-front")

        store.remove(listOf(1, 3))

        assertNull(store.assignedDeviceUuid(1))
        assertEquals("uuid-back", store.assignedDeviceUuid(2))
        assertNull(store.assignedDeviceUuid(3))
    }

    @Test
    fun `unassigning a device clears every widget assigned to it`() {
        store.assign(1, "uuid-front")
        store.assign(2, "uuid-back")
        store.assign(3, "uuid-front")

        store.unassignDevice("uuid-front")

        assertNull(store.assignedDeviceUuid(1))
        assertEquals("uuid-back", store.assignedDeviceUuid(2))
        assertNull(store.assignedDeviceUuid(3))
    }

    @Test
    fun `registering a real device releases widgets assigned to the demo device`() {
        store.assign(1, SesameDemoMode.DEMO_DEVICE_UUID)
        store.assign(2, SesameWearProtocol.ALL_DEVICES_TARGET_UUID)

        store.onRegisteredDevicesChanged(registeredDeviceCount = 0)
        assertEquals(SesameDemoMode.DEMO_DEVICE_UUID, store.assignedDeviceUuid(1))

        store.onRegisteredDevicesChanged(registeredDeviceCount = 1)
        assertNull(store.assignedDeviceUuid(1))
        assertEquals(SesameWearProtocol.ALL_DEVICES_TARGET_UUID, store.assignedDeviceUuid(2))
    }

    @Test
    fun `corrupted stored value is treated as unassigned`() {
        keyValueStore.putString("widget_device_assignments", "[1,2")

        assertNull(store.assignedDeviceUuid(1))
        store.assign(1, "uuid-front")
        assertEquals("uuid-front", store.assignedDeviceUuid(1))
    }
}
