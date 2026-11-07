package com.sesamiwear.core.ble

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * 「Bluetooth接続を確認」のある時点の進捗（BL-271）。スマホの画面の描画と、ウォッチへ返す
 * 進捗メッセージ（[SesameBleProbeSnapshot.encode]）に使う。機密情報は含まない。
 *
 * @property stage 進み具合。
 * @property rssiDbm 平滑化後のRSSI（dBm）。電波を捉えていなければnull。
 * @property batteryPercentage ログインまで成功したときの電池残量（%）。
 * @property position ログインまで成功したときのサムターンの角度。
 */
@Serializable
data class SesameBleProbeSnapshot(
    val stage: SesameBleProbeStage,
    val rssiDbm: Int? = null,
    val batteryPercentage: Int? = null,
    val position: Int? = null,
) {
    /** 近さの段階。 */
    val level: SesameBleProximity.Level get() = SesameBleProximity.level(rssiDbm)

    /** 距離の表示文言（「約3m」など）。 */
    val distanceLabel: String get() = SesameBleProximity.distanceLabel(rssiDbm)

    /** レーダー上のブリップの位置（0.0=中心〜1.0=最外周）。見えていなければnull。 */
    val radarFraction: Float? get() = SesameBleProximity.radarFraction(rssiDbm)

    /**
     * ウォッチの円形画面向けの2行。1行目は段階、2行目は電波を捉えているときだけ距離を添える
     * （表示領域が狭いため、これ以上の情報は載せない）。
     */
    fun compactLines(): Pair<String, String?> = stage.shortLabel to rssiDbm?.let { distanceLabel }

    /** スマホ向けの1行（「発見・約3m（近い）」）。電波を捉えていなければ段階だけ。 */
    fun summaryLine(): String = if (rssiDbm == null) stage.label else "${stage.label}・$distanceLabel（${level.label}）"

    /** ログインまで成功したときの追加情報（「電池80%・角度12」）。得られていなければnull。 */
    fun connectionDetail(): String? =
        listOfNotNull(batteryPercentage?.let { "電池$it%" }, position?.let { "角度$it" })
            .takeIf { it.isNotEmpty() }
            ?.joinToString("・")

    /** ウォッチへ送るバイト列（JSON）。 */
    fun encode(): ByteArray = JSON.encodeToString(serializer(), this).toByteArray(Charsets.UTF_8)

    companion object {
        private val JSON = Json { ignoreUnknownKeys = true }

        /** [encode]の逆。壊れた・未知の段階を含むペイロードはnullを返す（新旧のバージョン混在で落ちない）。 */
        fun decode(payload: ByteArray): SesameBleProbeSnapshot? =
            try {
                JSON.decodeFromString(serializer(), String(payload, Charsets.UTF_8))
            } catch (
                @Suppress("SwallowedException") e: IllegalArgumentException,
            ) {
                null
            }
    }
}
