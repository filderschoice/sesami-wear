package com.sesamiwear.mobile.diagnostics

import android.content.Context
import android.util.Log
import com.sesamiwear.core.api.SesameApiFailureLog
import com.sesamiwear.core.diagnostics.SesameDiagnosticsLog

/**
 * 通信の詳細を`Log.w`と操作履歴（診断ログ）の両方へ残す（BL-302）。
 *
 * これまでHTTPの応答・失敗の理由・経路の成否・BLEの段階は`Log.w`（タグ`SesameApi`）にしか出しておらず、
 * テストユーザーの端末で再現しても利用者はlogcatを取り出せないため確かめられなかった。操作履歴へも残し、
 * 「コピー」「共有」で連携してもらえるようにする。
 *
 * `Log.w`へはこれまでどおりの行を出す（実機検証の`adb logcat -s SesameApi`を変えないため）。操作履歴へは
 * [SesameDiagnosticsLog.recordDetail]が`device=`（uuidの先頭8文字）を除き、資格情報らしい値を伏せてから保存する。
 * 保存に失敗しても施錠・解錠を止めない（記録は補助のため）。
 * 配線だけの薄いアダプタのためユニットテスト対象外（伏せ方と保持件数は`SesameDiagnosticsLogTest`で検証済み）。
 */
class DiagnosticsDetailLogger(
    private val log: SesameDiagnosticsLog,
    private val nowMillis: () -> Long = System::currentTimeMillis,
) {
    fun warn(message: String) {
        Log.w(SesameApiFailureLog.TAG, message)
        runCatching { log.recordDetail(nowMillis(), message) }
            .onFailure { Log.w(SesameApiFailureLog.TAG, "diagnostics detail not saved: ${it.javaClass.simpleName}") }
    }

    companion object {
        fun create(context: Context): DiagnosticsDetailLogger =
            DiagnosticsDetailLogger(DiagnosticsLogFactory.create(context.applicationContext))
    }
}
