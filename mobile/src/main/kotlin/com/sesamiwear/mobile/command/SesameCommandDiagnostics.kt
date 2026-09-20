package com.sesamiwear.mobile.command

import com.sesamiwear.core.SesameCredentials
import com.sesamiwear.core.SesameDemoMode
import com.sesamiwear.core.SesameStatusRoute
import com.sesamiwear.core.api.SesameCommand
import com.sesamiwear.core.diagnostics.SesameDiagnosticsEntry
import com.sesamiwear.core.diagnostics.SesameDiagnosticsOperation
import com.sesamiwear.core.diagnostics.SesameDiagnosticsOutcome
import com.sesamiwear.mobile.state.LockStateStore

/**
 * [SesameDeviceCommandExecutor]の結果を診断ログ（BL-188）の1件へ組み立てて渡す。
 *
 * 通信エラーなどが起きたときに、利用者から内容を連携してもらって解析するためのもの。
 * **uuidは渡さない。** 記録へ載せるのは利用者が付けた表示名だけで、uuidが分からないデバイス
 * （資格情報が消えている等）は[UNKNOWN_DEVICE_NAME]にする
 * （`rules/guardrails-unified.v1.md` 3.3、2026-09-20のユーザー判断）。
 *
 * 実行口へ直接書かずに分けているのは、`SesameDeviceCommandExecutor.kt`の関数数がdetektの
 * 上限（`TooManyFunctions`）に達しているため。Android非依存のためユニットテスト対象。
 *
 * @param record 組み立てた1件の渡し先。既定では何もしない（診断ログを配線しない呼び出し元のため）。
 */
class SesameCommandDiagnostics(
    private val loadCredentials: () -> List<SesameCredentials> = { emptyList() },
    private val lockStateStore: LockStateStore? = null,
    private val record: (SesameDiagnosticsEntry) -> Unit = {},
    private val nowMillis: () -> Long = System::currentTimeMillis,
) {
    /**
     * [uuid]への[operation]が[outcome]で終わったことを記録する。
     *
     * 失敗の理由は、資格情報が使えないならその旨、そうでなければ保存済みの失敗分類
     * （[LockStateStore]へ直前に保存されたもの）から引く。成功・無視では理由を付けない。
     */
    fun record(
        uuid: String,
        operation: SesameDiagnosticsOperation,
        outcome: SesameDiagnosticsOutcome,
        route: SesameStatusRoute? = null,
    ) {
        record(
            SesameDiagnosticsEntry(
                epochMillis = nowMillis(),
                operation = operation,
                deviceName = deviceNameOf(uuid),
                outcome = outcome,
                route = route,
                reason = if (outcome == SesameDiagnosticsOutcome.FAILURE) failureReasonOf(uuid) else null,
            ),
        )
    }

    /** 施錠/解錠のコマンドを診断ログの操作へ読み替える。 */
    fun operationOf(command: SesameCommand): SesameDiagnosticsOperation =
        when (command) {
            SesameCommand.LOCK -> SesameDiagnosticsOperation.LOCK
            SesameCommand.UNLOCK -> SesameDiagnosticsOperation.UNLOCK
        }

    private fun deviceNameOf(uuid: String): String =
        when {
            SesameDemoMode.isDemoDevice(uuid) -> SesameDemoMode.DEMO_DEVICE_DISPLAY_NAME
            else ->
                loadCredentials()
                    .find { it.uuid == uuid }
                    ?.displayName
                    ?.ifBlank { null }
                    ?: UNKNOWN_DEVICE_NAME
        }

    private fun failureReasonOf(uuid: String): String {
        val credentials = loadCredentials().find { it.uuid == uuid }
        if (credentials?.secretKeyBytesOrNull == null) return NO_CREDENTIALS_REASON
        return lockStateStore?.load(uuid)?.lastFailure?.shortLabel ?: UNKNOWN_REASON
    }

    companion object {
        /** 表示名が分からないデバイス。**uuidは出さない。** */
        const val UNKNOWN_DEVICE_NAME = "名前未設定"

        /** 資格情報が未登録、または保存済みの鍵が16バイトとして読めないときの理由。 */
        const val NO_CREDENTIALS_REASON = "資格情報が未登録または不正"

        /** 失敗したが分類が保存されていないときの理由。 */
        const val UNKNOWN_REASON = "原因不明"
    }
}
