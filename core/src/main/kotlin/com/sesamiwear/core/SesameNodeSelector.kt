package com.sesamiwear.core

/**
 * Wearable Data Layerの接続先ノードから、スマホのノードを選ぶ（BL-312）。
 *
 * ウォッチのWearサービスは、スマホが機内モードでも到達できる接続先として中継ノード（[CLOUD_NODE_ID]）を
 * 持ち続ける。`connectedNodes`の先頭がこの中継ノードになる並びでは、スマホではなく中継へ宛てて送ってしまい、
 * 届いたことにされて捨てられるため、中継ノードは選ばない。
 */
object SesameNodeSelector {
    /** Wearable Data Layerが、クラウド経由の中継を表すノードに付けるID。 */
    const val CLOUD_NODE_ID = "cloud"

    /** [nodeIds]（接続順）から、中継ノードを除いた先頭を返す。無ければnull。 */
    fun selectPhoneNodeId(nodeIds: List<String>): String? = nodeIds.firstOrNull { it != CLOUD_NODE_ID }
}
