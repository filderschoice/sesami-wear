package com.sesamiwear.mobile.help

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HelpContentTest {
    @Test
    fun `menu offers the topics in a fixed order`() {
        assertEquals(
            listOf("credentials", "api-limit", "demo", "after-registration", "widget"),
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
    fun `credentials topic keeps the sesame biz link and adds the official api key guide`() {
        assertEquals(
            listOf(HelpContent.SESAME_BIZ_DEVELOPER_URL, HelpContent.SESAME_API_KEY_GUIDE_URL),
            HelpContent.credentials.links.map { it.url },
        )
    }

    @Test
    fun `every link is https and has a label`() {
        val links = HelpContent.topics.flatMap { it.links }
        assertTrue(links.isNotEmpty())
        links.forEach { link ->
            assertTrue(link.url, link.url.startsWith("https://"))
            assertTrue(link.label.isNotBlank())
        }
    }

    @Test
    fun `links never contain raw non ascii characters`() {
        // Uri.parseは受け取った文字列をそのまま扱うため、日本語パスはエンコード済みで保持する（BL-144）。
        HelpContent.topics.flatMap { it.links }.forEach { link ->
            assertTrue(link.url, link.url.all { it.code in 0x20..0x7E })
        }
    }

    @Test
    fun `api limit topic explains the monthly limit, how it looks and where to check it`() {
        val body = HelpContent.apiLimit.paragraphs.joinToString(separator = "")
        assertTrue(body.contains("1か月あたり"))
        assertTrue(body.contains("上限"))
        // 失敗時の表示文言（core.SesameStatusFailure）と同じ言葉で書き、利用者が結び付けられるようにする。
        assertTrue(body.contains("認証エラー"))
        assertTrue(body.contains("biz.candyhouse.co"))
        // 実測値（1000回）は利用者の環境での値であり公式の記載を確認できていないため、断定しない。
        assertTrue("上限の具体的な回数を断定している", !body.contains("1000"))
        assertEquals(
            listOf(HelpContent.SESAME_BIZ_DEVELOPER_URL),
            HelpContent.apiLimit.links.map { it.url },
        )
    }

    @Test
    fun `demo topic explains how to reach the demo device on the watch`() {
        val body = HelpContent.demo.paragraphs.joinToString(separator = "")
        assertTrue(body.contains("「デモ」"))
        assertTrue(body.contains("タイル"))
        assertTrue(body.contains("コンプリケーション"))
    }

    @Test
    fun `demo topic also explains the phone widget demo`() {
        val body = HelpContent.demo.paragraphs.joinToString(separator = "")
        assertTrue(body.contains("ウィジェット"))
        assertTrue(body.contains("タップして設定"))
    }

    @Test
    fun `widget topic uses the same wording as the widget itself`() {
        val body = HelpContent.widget.paragraphs.joinToString(separator = "")
        // ウィジェットの表示文言（SesameWidget / SesameWidgetModel / SesameTileContent）と一致させる。
        listOf("変更", "全デバイス", "通信中...", "タップして設定").forEach { word ->
            assertTrue("ウィジェットの説明に「$word」が無い", body.contains(word))
        }
    }

    @Test
    fun `only the credentials and api limit topics open external pages`() {
        assertEquals(
            listOf("credentials", "api-limit"),
            HelpContent.topics.filter { it.links.isNotEmpty() }.map { it.id },
        )
    }
}
