package com.sesamiwear.core.haptics

import com.sesamiwear.core.SesameCommandResult

/**
 * コマンド実行結果から再生すべき[HapticPattern]を決定する。Android非依存のためユニットテスト対象。
 * wear（Data Layer経由で受け取った結果）とmobile（ウィジェットの実行結果、BL-129）で共用する。
 */
object SesameHapticPatternResolver {
    fun resolve(result: SesameCommandResult): HapticPattern =
        when (result) {
            SesameCommandResult.SUCCESS -> HapticPattern.SUCCESS
            SesameCommandResult.FAILURE -> HapticPattern.FAILURE
        }
}
