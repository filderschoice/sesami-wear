package com.sesamiwear.mobile.ble

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/** 保持した接続の入れ物（BL-279）。寿命・取り出し・戻し・入れ替えを、仮想時間で確かめる。 */
@OptIn(ExperimentalCoroutinesApi::class)
class HeldSessionStoreTest {
    private class FakeSession : AutoCloseable {
        var closed = false

        override fun close() {
            closed = true
        }
    }

    @Test
    fun `closes itself after the ttl`() =
        runTest {
            val store = HeldSessionStore<FakeSession>(backgroundScope, ttlMillis = 1_000)
            val session = FakeSession()

            store.retain("a", session)
            advanceTimeBy(999)
            assertFalse(session.closed)
            assertTrue(store.has("a"))

            advanceTimeBy(2)
            runCurrent()
            assertTrue(session.closed)
            assertFalse(store.has("a"))
        }

    @Test
    fun `take hands over ownership and stops the timer`() =
        runTest {
            val store = HeldSessionStore<FakeSession>(backgroundScope, ttlMillis = 1_000)
            val session = FakeSession()
            store.retain("a", session)

            val taken = store.take("a")
            advanceTimeBy(5_000)
            runCurrent()

            assertSame(session, taken)
            assertFalse(session.closed)
            assertNull(store.take("a"))
        }

    @Test
    fun `retaining again restarts the ttl`() =
        runTest {
            val store = HeldSessionStore<FakeSession>(backgroundScope, ttlMillis = 1_000)
            val session = FakeSession()
            store.retain("a", session)
            advanceTimeBy(800)
            store.retain("a", checkNotNull(store.take("a")))

            advanceTimeBy(800)
            runCurrent()
            assertFalse(session.closed)

            advanceTimeBy(300)
            runCurrent()
            assertTrue(session.closed)
        }

    @Test
    fun `retaining another value under the same key closes the old one`() =
        runTest {
            val store = HeldSessionStore<FakeSession>(backgroundScope, ttlMillis = 1_000)
            val old = FakeSession()
            val new = FakeSession()
            store.retain("a", old)

            store.retain("a", new)

            assertTrue(old.closed)
            assertFalse(new.closed)
            assertSame(new, store.take("a"))
        }

    @Test
    fun `keys are independent`() =
        runTest {
            val store = HeldSessionStore<FakeSession>(backgroundScope, ttlMillis = 1_000)
            val a = FakeSession()
            val b = FakeSession()
            store.retain("a", a)
            store.retain("b", b)

            assertSame(a, store.take("a"))
            assertTrue(store.has("b"))
            assertFalse(b.closed)
        }

    @Test
    fun `releaseAll closes every held session`() =
        runTest {
            val store = HeldSessionStore<FakeSession>(backgroundScope, ttlMillis = 1_000)
            val a = FakeSession()
            val b = FakeSession()
            store.retain("a", a)
            store.retain("b", b)

            store.releaseAll()

            assertTrue(a.closed && b.closed)
            assertFalse(store.has("a") || store.has("b"))
        }
}
