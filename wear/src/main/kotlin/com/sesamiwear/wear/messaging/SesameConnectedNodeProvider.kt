package com.sesamiwear.wear.messaging

import android.content.Context
import com.google.android.gms.wearable.Wearable
import com.sesamiwear.core.SesameNodeSelector
import kotlinx.coroutines.tasks.await

/**
 * ペアリング済みスマホのノードIDを取得する薄いアダプタ（クラウド中継ノードは選ばない、BL-312）。Android Google Play Services依存のため
 * ユニットテスト対象外（実際の接続動作はBL-011で人手検証）。
 */
object SesameConnectedNodeProvider {
    suspend fun firstConnectedNodeId(context: Context): String? =
        SesameNodeSelector.selectPhoneNodeId(
            Wearable.getNodeClient(context).connectedNodes.await().map { it.id },
        )

    /**
     * ペアリング済みスマホのうち、**近くにいる**（`isNearby`）ものだけを返す（BL-276）。
     *
     * [firstConnectedNodeId]は、スマホが近くにいなくてもクラウド経由で届くノードを返すことがある。施錠・解錠の依頼は
     * スマホ側がWeb APIで実行するため遠隔でも届けばよいが、「Bluetooth接続を確認」はスマホがSesameの近くで
     * Bluetoothを使う処理で、近くにいない（クラウド経由の）スマホへ依頼すると、いつ実行されるか分からない
     * （機内モードにしても「探索中」のまま、実際は届かず・後から届く）。接続確認の画面だけ、こちらを使う。
     */
    suspend fun firstNearbyNodeId(context: Context): String? =
        Wearable.getNodeClient(context).connectedNodes.await().firstOrNull { it.isNearby }?.id
}
