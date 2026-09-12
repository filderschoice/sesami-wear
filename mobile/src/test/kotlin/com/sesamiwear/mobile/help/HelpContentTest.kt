package com.sesamiwear.mobile.help

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HelpContentTest {
    @Test
    fun `menu offers the three topics in a fixed order`() {
        assertEquals(
            listOf("credentials", "demo", "after-registration"),
            HelpContent.topics.map { it.id },
        )
    }

    @Test
    fun `every topic has a title and at least one paragraph`() {
        HelpContent.topics.forEach { topic ->
            assertTrue(topic.title.isNotBlank())
            assertTrue("本文が空の項目がある: ${topic.id}", topic.paragraphs.isNotEmpty())
            topic.paragraphs.forEach { assertTrue(it.isNotBlank()) }
        }
    }

    @Test
    fun `topic ids and titles are unique`() {
        assertEquals(HelpContent.topics.size, HelpContent.topics.map { it.id }.toSet().size)
        assertEquals(HelpContent.topics.size, HelpContent.topics.map { it.title }.toSet().size)
    }

    @Test
    fun `credentials topic keeps the sesame biz link`() {
        val link = HelpContent.credentials.link
        assertNotNull(link)
        assertEquals(HelpContent.SESAME_BIZ_DEVELOPER_URL, link?.url)
        assertTrue(HelpContent.SESAME_BIZ_DEVELOPER_URL.startsWith("https://"))
    }

    @Test
    fun `demo topic explains how to reach the demo device on the watch`() {
        val body = HelpContent.demo.paragraphs.joinToString(separator = "")
        assertTrue(body.contains("デモ（体験用）"))
        assertTrue(body.contains("タイル"))
        assertTrue(body.contains("コンプリケーション"))
    }

    @Test
    fun `only the credentials topic opens an external page`() {
        assertEquals(1, HelpContent.topics.count { it.link != null })
    }
}
