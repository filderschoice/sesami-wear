package com.sesamiwear.wear.messaging

import com.sesamiwear.core.SesameCommandResult
import com.sesamiwear.core.SesameWearProtocol
import com.sesamiwear.wear.haptics.HapticPattern
import com.sesamiwear.wear.haptics.SesameHapticPatternResolver

/**
 * 受信メッセージ（path・payload）から、再生すべき[HapticPattern]を決定する。
 * MessageEvent型を直接受け取らずpath/payloadのプリミティブ値のみを扱うため、Android非依存でテストできる。
 * PATH_COMMAND_RESULT以外のメッセージは対象外としてnullを返す。
 */
object SesameResultHandler {
    fun hapticPatternForMessage(
        path: String,
        payload: ByteArray,
    ): HapticPattern? {
        if (path != SesameWearProtocol.PATH_COMMAND_RESULT) return null
        val result = SesameCommandResult.fromPayload(payload)
        return SesameHapticPatternResolver.resolve(result)
    }
}
