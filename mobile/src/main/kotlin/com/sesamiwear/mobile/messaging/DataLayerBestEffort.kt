package com.sesamiwear.mobile.messaging

import com.google.android.gms.common.api.ApiException

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
 * コルーチンのキャンセル（`CancellationException`）は捕捉しない（`ApiException` だけを対象にしている）。
 * Android 依存部から切り離し、ユニットテストで検証できるようにしている。
 */
object DataLayerBestEffort {
    /**
     * [block] を実行し、成功したら true を返す。[block] が `ApiException` を投げた場合は
     * [onFailure] へステータスコードを渡して false を返す（例外は外へ送出しない）。
     */
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
        }
}
