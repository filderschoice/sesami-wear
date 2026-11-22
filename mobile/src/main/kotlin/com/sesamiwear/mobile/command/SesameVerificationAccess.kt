package com.sesamiwear.mobile.command

import com.sesamiwear.core.SesameBatteryLevel
import com.sesamiwear.core.SesameCredentials
import com.sesamiwear.core.SesameStatusMeasurement
import com.sesamiwear.core.SesameStatusReading
import com.sesamiwear.core.SesameStatusRoute
import com.sesamiwear.core.api.SesameApiException
import com.sesamiwear.core.api.SesameApiFailureLog
import com.sesamiwear.core.api.SesameApiOperation
import com.sesamiwear.core.api.SesameCommand
import com.sesamiwear.core.api.SesameCommandVerification
import com.sesamiwear.core.api.SesameStatus
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay

/**
 * 施錠/解錠をWeb APIで送った後の状態照合（BL-293）の設定と実行。
 *
 * [isEnabled]がfalse（既定）なら何もしない（状態取得を追加しない）。trueのときだけ、送信の[waitMillis]後に
 * 状態を1回取得し、[SesameCommandVerification]で送った命令と照合する。待ち時間は初期値4秒
 * （仕様の3〜5秒の中央）で、クラウドから実機までの反映遅延は実機で調整する。
 * 取得の1回は月間リクエスト上限（BL-141）を1回余分に消費するため、[SesameApiAccess.recordApiCall]で数える。
 *
 * 照合できなかった場合（取得失敗・古い値）は[Result.Unverified]とし、命令の成否は変えない。
 * 待ち時間のあいだに呼び出しが取り消された場合（ウィジェットの約9秒の打ち切りなど）は、
 * 取り消しがそのまま伝わる。このとき送信時点の状態は既に保存済みのため、照合しない場合と同じ表示になる。
 */
class SesameVerificationAccess(
    val isEnabled: () -> Boolean = { false },
    val waitMillis: Long = DEFAULT_WAIT_MILLIS,
    val pause: suspend (Long) -> Unit = { delay(it) },
) {
    /** 照合の結果。 */
    sealed interface Result {
        /** 設定がOFFのため照合しなかった。 */
        data object Skipped : Result

        /** 状態が命令と一致した。 */
        data object Confirmed : Result

        /** 取得に失敗した、または応答が送信より古く、判断できなかった。 */
        data object Unverified : Result

        /** 送信より新しい状態が命令と食い違っていた。[reading]は実際の状態。 */
        data class NotReflected(val reading: SesameStatusReading) : Result
    }

    /** [command]を[sentAtMillis]に送った[credentials]のデバイスを、設定がONなら照合する。 */
    suspend fun verify(
        api: SesameApiAccess,
        credentials: SesameCredentials,
        command: SesameCommand,
        sentAtMillis: Long,
    ): Result {
        if (!isEnabled()) return Result.Skipped
        return try {
            pause(waitMillis)
            api.recordApiCall()
            val status = fetchStatusOrNull(api, credentials)
            if (status == null) unverified(api, command, "fetch failed") else judge(api, command, status, sentAtMillis)
        } catch (e: CancellationException) {
            // 照合が終わらないまま呼び出しが取り消された。照合できなかったことをlogcatで区別できるようにする。
            api.logResponse("verify ${command.name}: cancelled")
            throw e
        }
    }

    /**
     * 状態を1回取得する。取得に失敗した場合はnull（送信は成功済みのため、例外を呼び出し元へ漏らさない）。
     * [SesameApiException]以外の想定外の例外も同様にnullへ倒す。ただし取り消し（[CancellationException]）は
     * 握らず伝える。
     */
    private suspend fun fetchStatusOrNull(
        api: SesameApiAccess,
        credentials: SesameCredentials,
    ): SesameStatus? {
        val result = runCatching { api.clientFactory(credentials).getStatus() }
        result.getOrNull()?.let { return it }
        val failure = result.exceptionOrNull()
        if (failure is CancellationException) throw failure
        if (failure is SesameApiException) {
            api.logFailure(SesameApiFailureLog.describe(SesameApiOperation.STATUS, failure))
        } else {
            // 例外の本文には資格情報やURLが含まれうるため、想定外の例外は固定文だけを残す。
            api.logResponse("verify status fetch: unexpected error")
        }
        return null
    }

    private fun judge(
        api: SesameApiAccess,
        command: SesameCommand,
        status: SesameStatus,
        sentAtMillis: Long,
    ): Result {
        // 実機検証（BL-306）でtimestampの意味・単位を確かめるため、値そのものを残す（資格情報ではない）。
        api.logResponse(
            "verify ${command.name}: status timestamp=${status.timestamp} sentAtMillis=$sentAtMillis " +
                "inLockRange=${status.isInLockRange}",
        )
        return judgeVerdict(api, command, status, sentAtMillis)
    }

    private fun judgeVerdict(
        api: SesameApiAccess,
        command: SesameCommand,
        status: SesameStatus,
        sentAtMillis: Long,
    ): Result =
        when (SesameCommandVerification.judge(command, status, sentAtMillis)) {
            SesameCommandVerification.Verdict.CONFIRMED -> {
                api.logResponse("verify ${command.name}: confirmed")
                Result.Confirmed
            }
            SesameCommandVerification.Verdict.UNVERIFIABLE -> unverified(api, command, "stale or no timestamp")
            SesameCommandVerification.Verdict.NOT_REFLECTED -> {
                api.logResponse("verify ${command.name}: not reflected")
                Result.NotReflected(
                    SesameStatusReading(
                        isLocked = status.isInLockRange,
                        measurement =
                            SesameStatusMeasurement(
                                batteryPercentage = SesameBatteryLevel.percentageOf(status.batteryVoltage),
                                position = status.position,
                                route = SesameStatusRoute.WEB_API,
                            ),
                    ),
                )
            }
        }

    private fun unverified(
        api: SesameApiAccess,
        command: SesameCommand,
        reason: String,
    ): Result {
        api.logResponse("verify ${command.name}: unverified ($reason)")
        return Result.Unverified
    }

    companion object {
        /** 送信から状態取得までの待ち時間（BL-293の3〜5秒の中央）。 */
        const val DEFAULT_WAIT_MILLIS = 4_000L
    }
}
