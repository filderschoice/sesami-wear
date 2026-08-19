package com.sesamiwear.wear.haptics

import com.sesamiwear.core.SesameCommandResult

/**
 * コマンド実行結果から再生すべき[HapticPattern]を決定する。Android非依存のためユニットテスト対象。
 */
object SesameHapticPatternResolver {
    fun resolve(result: SesameCommandResult): HapticPattern =
        when (result) {
            SesameCommandResult.SUCCESS -> HapticPattern.SUCCESS
            SesameCommandResult.FAILURE -> HapticPattern.FAILURE
        }
}
