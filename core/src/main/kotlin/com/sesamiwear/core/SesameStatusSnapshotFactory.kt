package com.sesamiwear.core

/**
 * DataItem（DataMap）から取り出したプリミティブ値から[SesameStatusSnapshot]を構築する。
 * DataMap自体はAndroid依存のため、呼び出し側で値を取り出したうえで本関数に渡すことでテスト可能にする。
 *
 * 施錠状態も失敗もどちらも無い（＝同期すべき情報が無い）場合だけnullを返す。失敗だけが同期されている
 * 場合（一度も取得できていないまま認証エラーになった等、BL-140）はスナップショットを返す。
 */
object SesameStatusSnapshotFactory {
    fun create(
        hasIsLockedKey: Boolean,
        isLocked: Boolean,
        updatedAtEpochMillis: Long,
        lastFailureName: String? = null,
    ): SesameStatusSnapshot? {
        val failure = SesameStatusFailure.ofNameOrNull(lastFailureName)
        if (!hasIsLockedKey && failure == null) return null
        return SesameStatusSnapshot(
            isLocked = if (hasIsLockedKey) isLocked else null,
            updatedAtEpochMillis = if (hasIsLockedKey) updatedAtEpochMillis else null,
            lastFailure = failure,
        )
    }
}
