package com.sesamiwear.wear.messaging

import android.content.Context
import com.google.android.gms.wearable.Wearable
import kotlinx.coroutines.tasks.await

/**
 * ペアリング済みスマホのノードIDを取得する薄いアダプタ。Android Google Play Services依存のため
 * ユニットテスト対象外（実際の接続動作はBL-011で人手検証）。
 */
object SesameConnectedNodeProvider {
    suspend fun firstConnectedNodeId(context: Context): String? =
        Wearable.getNodeClient(context).connectedNodes.await().firstOrNull()?.id
}
