package com.sesamiwear.core

/**
 * DataItem（DataMap）から取り出したプリミティブ値から[SesameStatusSnapshot]を構築する。
 * DataMap自体はAndroid依存のため、呼び出し側で値を取り出したうえで本関数に渡すことでテスト可能にする。
 */
object SesameStatusSnapshotFactory {
    fun create(
        hasIsLockedKey: Boolean,
        isLocked: Boolean,
        updatedAtEpochMillis: Long,
    ): SesameStatusSnapshot? =
        if (hasIsLockedKey) {
            SesameStatusSnapshot(isLocked = isLocked, updatedAtEpochMillis = updatedAtEpochMillis)
        } else {
            null
        }
}
