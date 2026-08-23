package com.sesamiwear.wear.tile

import com.sesamiwear.core.TileDisplayState
import com.sesamiwear.core.api.SesameCommand

/**
 * Tileの表示状態から、タップ時に実行すべきコマンドを決定する。
 * 施錠中なら解錠を、解錠中なら施錠を提示する。施錠/解錠が混在（MIXED、複数デバイス一括操作
 * BL-071）の場合は「迷ったら安全側（施錠）」の方針で施錠を提示する（解錠のみ確認画面を挟む
 * 既存UX、[com.sesamiwear.core.api.SesameCommandConfirmation]と組み合わせて安全側は
 * 確認不要のまま維持する）。状態不明・通信中・スマホ未接続時はタップ不可（null）。
 */
object SesameTileActions {
    fun commandForState(state: TileDisplayState): SesameCommand? =
        when (state) {
            TileDisplayState.LOCKED -> SesameCommand.UNLOCK
            TileDisplayState.UNLOCKED, TileDisplayState.MIXED -> SesameCommand.LOCK
            TileDisplayState.IN_PROGRESS, TileDisplayState.DISCONNECTED, TileDisplayState.UNKNOWN -> null
        }
}
