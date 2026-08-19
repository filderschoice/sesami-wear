package com.sesamiwear.wear.tile

import com.sesamiwear.core.TileDisplayState
import com.sesamiwear.core.api.SesameCommand

/**
 * Tileの表示状態から、タップ時に実行すべきコマンドを決定する。
 * 施錠中なら解錠を、解錠中なら施錠を提示する。状態不明・通信中・スマホ未接続時はタップ不可（null）。
 */
object SesameTileActions {
    fun commandForState(state: TileDisplayState): SesameCommand? =
        when (state) {
            TileDisplayState.LOCKED -> SesameCommand.UNLOCK
            TileDisplayState.UNLOCKED -> SesameCommand.LOCK
            TileDisplayState.IN_PROGRESS, TileDisplayState.DISCONNECTED, TileDisplayState.UNKNOWN -> null
        }
}
