package com.sesamiwear.mobile.widget

import com.sesamiwear.core.SesameDeviceSummary
import com.sesamiwear.core.display.SesameDeviceTargets

/**
 * ホーム画面ウィジェットの「◀ ▶」で対象デバイスを順送りする（BL-175）。
 *
 * 巡回する並びは**デバイス選択画面と同じ**（[SesameDeviceTargets.choices]。2台以上なら先頭に
 * 「全デバイス」、0台ならデモ用デバイスのみ）。選択画面を開かなくても、そこから選べるものすべてへ
 * 到達できるようにするため（2026-09-20、ユーザー確認済み）。端まで行ったら反対側へ回り込む。
 *
 * 高さ1マスの表示（[SesameWidgetLayout.MEDIUM]）には選択画面を開くチップを置く余地が無いため、
 * この面に限ってwearのTileに無い操作を許容している（DESIGN.md「意図的に揃えていない点」）。
 *
 * **切り替えではSesame Web APIを呼ばない。** 保存済みの状態を出すだけで、月間リクエスト回数
 * （BL-141）を消費しない。
 *
 * Android非依存のためユニットテスト対象。
 */
object WidgetDeviceCycle {
    /** 1つ後ろ（▶）へ進む。 */
    const val FORWARD = 1

    /** 1つ前（◀）へ戻る。 */
    const val BACKWARD = -1

    /**
     * [currentUuid]から[step]だけ進めた先のuuid。選択肢が空（登録0台かつデモも選べない）なら null、
     * [currentUuid]が選択肢に無い（削除されたデバイスを割り当てたままなど）場合は先頭の選択肢を返す。
     */
    fun next(
        currentUuid: String,
        registeredDevices: List<SesameDeviceSummary>,
        step: Int,
    ): String? {
        val choices = SesameDeviceTargets.choices(registeredDevices)
        val index = choices.indexOfFirst { it.uuid == currentUuid }
        return when {
            choices.isEmpty() -> null
            index < 0 -> choices.first().uuid
            // Kotlinの % は負を返しうるため、サイズを足してから再度剰余を取り、必ず0以上にする。
            else -> choices[((index + step) % choices.size + choices.size) % choices.size].uuid
        }
    }
}
