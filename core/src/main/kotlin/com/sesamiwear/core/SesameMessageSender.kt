package com.sesamiwear.core

/**
 * Wearable Data Layer API（MessageClient）によるメッセージ送信を抽象化する。
 * Android/Google Play Services依存の実装（MessageClientベース）は mobile / wear 側に置き、
 * 呼び出し側ロジックをAndroid非依存のままユニットテスト可能にする。
 */
interface SesameMessageSender {
    suspend fun send(
        nodeId: String,
        path: String,
        payload: ByteArray,
    )
}
