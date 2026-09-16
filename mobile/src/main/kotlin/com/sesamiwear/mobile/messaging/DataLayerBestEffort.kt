package com.sesamiwear.mobile.messaging

import com.google.android.gms.common.api.ApiException
import kotlinx.coroutines.CancellationException

/**
 * Wearable Data Layer API の呼び出しを「失敗しても呼び出し元の処理を止めない」ベストエフォート呼び出しに
 * する（BL-118）。
 *
 * Wear OS のコンパニオンアプリ（Pixel Watch アプリ等）が入っていない端末では、`DataClient` /
 * `MessageClient` のタスクが `ApiException`（API_UNAVAILABLE）で失敗しうる。以前は例外処理なしで
 * `await()` しており、資格情報の保存・削除時に起動したコルーチンから例外が漏れてアプリが落ちる経路があった。
 * ウォッチへの同期はスマホ側の処理（資格情報の保存、Sesame API の実行）の付随処理にすぎないため、
 * 失敗は [onFailure] へ通知するだけで握りつぶす。
 *
 * `ApiException` 以外（Play services 側の状態や端末構成に起因する実行時例外など）も同じく握りつぶす
 * （BL-134）。捕捉対象を `ApiException` に絞っていたため、想定外の例外が資格情報設定画面の
 * コルーチンから漏れ、後続のウィジェット再描画まで行われなくなる余地が残っていた。
 *
 * コルーチンのキャンセル（`CancellationException`）は捕捉せず再送出する。
 * Android 依存部から切り離し、ユニットテストで検証できるようにしている。
 */
object DataLayerBestEffort {
    /** `ApiException` 以外の失敗を [run] が [onFailure] へ通知するときのステータスコード（BL-134）。 */
    const val UNKNOWN_STATUS_CODE = -1

    /**
     * [block] を実行し、成功したら true を返す。[block] が失敗した場合は [onFailure] へステータスコード
     * （`ApiException` ならそのコード、それ以外は [UNKNOWN_STATUS_CODE]）を渡して false を返す。
     *
     * キャンセルだけを再送出するため、包括的なcatchの中で型を判定している
     * （[com.sesamiwear.mobile.EntryPointGuard] と同じ理由）。
     */
    @Suppress("InstanceOfCheckForException")
    suspend fun run(
        onFailure: (statusCode: Int) -> Unit,
        block: suspend () -> Unit,
    ): Boolean =
        try {
            block()
            true
        } catch (
            @Suppress("SwallowedException") e: ApiException,
        ) {
            // ログへ出すのはステータスコードのみ（資格情報・uuidを含めない）。
            onFailure(e.statusCode)
            false
        } catch (
            @Suppress("TooGenericExceptionCaught", "SwallowedException") e: Exception,
        ) {
            if (e is CancellationException) throw e
            onFailure(UNKNOWN_STATUS_CODE)
            false
        }
}
