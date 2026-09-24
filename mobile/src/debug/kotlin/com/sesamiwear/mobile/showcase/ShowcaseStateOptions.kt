package com.sesamiwear.mobile.showcase

import com.sesamiwear.core.SesameStatusFailure
import com.sesamiwear.core.SesameStatusRoute

/**
 * 撮影モードの操作画面（BL-213）で、1台の状態の各項目に並べる選択肢。
 * 画面から切り離しておき、選択中の判定を単体テストできるようにしている。
 */
object ShowcaseStateOptions {
    /** 選択肢1つ。[label]はチップに出す語。 */
    data class Option<T>(
        val label: String,
        val value: T,
    )

    val lock: List<Option<Boolean?>> =
        listOf(Option("施錠中", true), Option("解錠中", false), Option("未取得", null))

    val battery: List<Option<Int?>> =
        listOf(100, 85, 50, 20, 5).map { Option<Int?>("$it%", it) } + Option("不明", null)

    val route: List<Option<SesameStatusRoute?>> =
        listOf(
            Option("Bluetooth", SesameStatusRoute.BLE),
            Option("インターネット", SesameStatusRoute.WEB_API),
            Option("不明", null),
        )

    val failure: List<Option<SesameStatusFailure?>> =
        listOf(
            Option("なし", null),
            Option("認証", SesameStatusFailure.AUTH_OR_QUOTA),
            Option("通信", SesameStatusFailure.COMMUNICATION),
            Option("端末設定", SesameStatusFailure.BACKGROUND_RESTRICTED),
        )

    val age: List<Option<Long>> =
        listOf(
            Option("たった今", 0L),
            Option("5分前", 5 * MINUTE_MILLIS),
            Option("1時間前", 60 * MINUTE_MILLIS),
            Option("1日前", 24 * 60 * MINUTE_MILLIS),
        )

    /**
     * 経過時間[ageMillis]に当たる選択肢。保存後も時間が進むため完全一致ではなく、
     * [ageMillis]以下で最も大きいものを選ぶ（「5分前」を選んで3分経てば8分前だが「5分前」のまま）。
     */
    fun ageOptionOf(ageMillis: Long): Option<Long> = age.last { it.value <= ageMillis.coerceAtLeast(0) }

    private const val MINUTE_MILLIS = 60_000L
}
