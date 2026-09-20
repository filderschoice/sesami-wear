package com.sesamiwear.mobile.diagnostics

import android.content.Context
import android.os.Build
import com.sesamiwear.core.diagnostics.SesameDiagnosticsLog
import com.sesamiwear.mobile.BuildConfig
import com.sesamiwear.mobile.state.SharedPreferencesKeyValueStore

/**
 * 診断ログ（BL-188）の保存先をつないで生成する。保存する値に資格情報を含まないため、
 * 保存先は非暗号化のSharedPreferences（`LockStateStore`と同方針）。
 * 配線だけの薄いアダプタのためユニットテスト対象外。
 */
object DiagnosticsLogFactory {
    fun create(context: Context): SesameDiagnosticsLog =
        SesameDiagnosticsLog(SharedPreferencesKeyValueStore.forDiagnostics(context))
}

/**
 * 連携用の全文の先頭へ付ける環境の情報（BL-188）。
 *
 * 解析には「どのバージョンの、どのAndroidか」が要るため、ログ本体とは別に添える。
 * **端末を特定できる値（シリアル番号・広告IDなど）は載せない。** 機種名は不具合の再現に要る一方で
 * 個人の特定にはつながらないため含める。
 */
object DiagnosticsEnvironment {
    fun headerLines(): List<String> =
        listOf(
            "Sesami Wear ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})",
            "Android ${Build.VERSION.RELEASE} / ${Build.MANUFACTURER} ${Build.MODEL}",
            SEPARATOR,
        )

    private const val SEPARATOR = "----"
}
