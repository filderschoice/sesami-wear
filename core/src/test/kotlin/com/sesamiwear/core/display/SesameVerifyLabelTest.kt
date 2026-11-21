package com.sesamiwear.core.display

import org.junit.Assert.assertTrue
import org.junit.Test

/** 送信後の状態確認（BL-293）の設定文言の検証。API消費と遅れを利用者へ伝えていることを固定する。 */
class SesameVerifyLabelTest {
    @Test
    fun `the description tells that each command uses one more API request`() {
        assertTrue(SesameVerifyLabel.DESCRIPTION.contains("1回多く"))
        assertTrue(SesameVerifyLabel.DESCRIPTION.contains("月間"))
    }

    @Test
    fun `the description tells that the result is delayed by a few seconds`() {
        assertTrue(SesameVerifyLabel.DESCRIPTION.contains("数秒"))
    }

    @Test
    fun `the status text follows the setting`() {
        assertTrue(SesameVerifyLabel.statusOf(true) == "オン")
        assertTrue(SesameVerifyLabel.statusOf(false) == "オフ")
    }
}
