package com.sesamiwear.mobile

import kotlinx.coroutines.CancellationException

/**
 * システムからの入口（BroadcastReceiver・WearableListenerService・Activityのライフサイクル）で
 * 起動したコルーチンから例外が漏れ、プロセスごとアプリが落ちるのを防ぐ（BL-134）。
 *
 * これらの入口はコルーチンをその場で`launch`するだけで、例外を受け取る呼び出し元がいない。
 * 漏れた例外は既定のハンドラへ届いてプロセスを終了させるため、ホーム画面のウィジェットは
 * 最後に描いた表示（多くは操作直後の「通信中...」）のまま取り残される。ウィジェットは
 * 自発的な再描画契機を持たない（`updatePeriodMillis=0`）ので、この表示は次の再描画要求まで
 * 残り続け、繰り返せば「ウィジェットが繰り返し停止しています」の表示にもつながる。
 *
 * コルーチンのキャンセル（`CancellationException`）は捕捉せず再送出し、構造化並行性を壊さない。
 * Android非依存のためユニットテスト対象。
 */
object EntryPointGuard {
    /**
     * [block]を実行し、成功したらtrueを返す。例外で終わった場合は[onFailure]へ例外の型名を渡してfalseを返す。
     * 渡すのは型名だけで、例外メッセージは渡さない（接続先URLなどuuidを含む文字列がログへ流れないようにするため）。
     *
     * キャンセルだけを再送出するため、包括的なcatchの中で型を判定している。多段catchにしても
     * 判定の意味は変わらず、`catch (e: CancellationException) { throw e }` が別の検査に触れるだけのため。
     */
    @Suppress("InstanceOfCheckForException")
    suspend fun run(
        onFailure: (failureName: String) -> Unit,
        block: suspend () -> Unit,
    ): Boolean =
        try {
            block()
            true
        } catch (
            @Suppress("TooGenericExceptionCaught") e: Exception,
        ) {
            if (e is CancellationException) throw e
            onFailure(e.javaClass.simpleName)
            false
        }
}
