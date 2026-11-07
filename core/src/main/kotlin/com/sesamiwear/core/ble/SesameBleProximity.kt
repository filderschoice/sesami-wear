package com.sesamiwear.core.ble

import kotlin.math.pow
import kotlin.math.roundToInt

/**
 * BLEの受信信号強度（RSSI）から、Sesameまでの目安の距離と近さの段階を求める（BL-271）。
 *
 * 対数距離減衰モデル `d = 10^((P1m - RSSI) / (10 * n))` を使う。RSSIは壁・人体・端末の向きで
 * 大きくぶれるため、**距離は目安**であり、利用者へは「約3m」のように丸めて示し、
 * 段階（[Level]）も併記する。[REFERENCE_RSSI_AT_1M_DBM]と[PATH_LOSS_EXPONENT]は実測で補正する
 * （BL-276）。mobileのレーダー画面とwearの小画面で同じ値を使うためcoreへ置く。
 *
 * Android非依存のためユニットテスト対象。
 */
object SesameBleProximity {
    /** 距離の段階。宣言順に近い。 */
    enum class Level(
        val label: String,
    ) {
        VERY_NEAR("とても近い"),
        NEAR("近い"),
        MEDIUM("やや遠い"),
        FAR("遠い"),
        NOT_SEEN("見えない"),
    }

    /** 1m地点で受信するRSSI（dBm）の想定値。実測で補正する前提の初期値。 */
    const val REFERENCE_RSSI_AT_1M_DBM = -60

    /** 屋内の減衰係数（自由空間は2、遮蔽物があるほど大きい）。 */
    const val PATH_LOSS_EXPONENT = 2.2

    /** レーダーの輪の距離（m）。内側から順。画面の輪はこの値に合わせて等間隔に描く。 */
    val RING_DISTANCES_METERS: List<Int> = listOf(1, 3, 5, 10)

    /** 「とても近い」の上限（m）。 */
    private const val VERY_NEAR_MAX_METERS = 1.5

    /** 「近い」の上限（m）。 */
    private const val NEAR_MAX_METERS = 4.0

    /** 「やや遠い」の上限（m）。これを超えると「遠い」。 */
    private const val MEDIUM_MAX_METERS = 10.0

    private const val DB_PER_DECADE = 10.0

    /** 1m未満は、RSSIのぶれが距離の差より大きいため1mとして扱う。 */
    private const val MIN_DISTANCE_METERS = 1.0

    /** [rssiDbm]から求めた目安の距離（m）。1m未満は1mへ丸める。 */
    fun distanceMeters(rssiDbm: Int): Double {
        val exponent = (REFERENCE_RSSI_AT_1M_DBM - rssiDbm) / (DB_PER_DECADE * PATH_LOSS_EXPONENT)
        return DB_PER_DECADE.pow(exponent).coerceAtLeast(MIN_DISTANCE_METERS)
    }

    /** [rssiDbm]の段階。nullは見えていない（スキャンに映っていない）ことを表す。 */
    fun level(rssiDbm: Int?): Level {
        if (rssiDbm == null) return Level.NOT_SEEN
        val meters = distanceMeters(rssiDbm)
        return when {
            meters <= VERY_NEAR_MAX_METERS -> Level.VERY_NEAR
            meters <= NEAR_MAX_METERS -> Level.NEAR
            meters <= MEDIUM_MAX_METERS -> Level.MEDIUM
            else -> Level.FAR
        }
    }

    /**
     * 距離の表示文言。最外周の輪（10m）を超えるものは「10m以上」。
     * 小数は出さない（RSSIの精度に対して細かすぎるため）。
     */
    fun distanceLabel(rssiDbm: Int?): String {
        if (rssiDbm == null) return "距離不明"
        val meters = distanceMeters(rssiDbm)
        val maxRing = RING_DISTANCES_METERS.last()
        return if (meters > maxRing) "${maxRing}m以上" else "約${meters.roundToInt()}m"
    }

    /**
     * レーダー上のブリップの、中心からの距離（0.0=中心、1.0=最外周）。
     * 輪が[RING_DISTANCES_METERS]の順に等間隔となるよう、輪の間を線形に補間する
     * （近い側ほど細かく動かせ、遠い側で画面外へ出ない）。見えていない場合はnullを返す。
     */
    fun radarFraction(rssiDbm: Int?): Float? {
        if (rssiDbm == null) return null
        val meters = distanceMeters(rssiDbm)
        val rings = RING_DISTANCES_METERS
        return when {
            meters >= rings.last() -> 1f
            meters <= rings.first() -> 0f
            else -> {
                val upperIndex = rings.indexOfFirst { meters <= it }
                val lower = rings[upperIndex - 1]
                val upper = rings[upperIndex]
                val within = ((meters - lower) / (upper - lower)).toFloat()
                (upperIndex + within) / rings.size
            }
        }
    }
}
