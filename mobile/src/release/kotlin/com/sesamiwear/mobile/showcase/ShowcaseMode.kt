package com.sesamiwear.mobile.showcase

import android.content.Context
import com.sesamiwear.core.SesameKeyValueStore
import com.sesamiwear.mobile.command.SesameDeviceCommands

/**
 * 撮影モード（BL-212）の**リリース版スタブ**。撮影モードはデバッグ版だけの機能で、リリース版
 * （Google Playへ配信するAAB）には実装を載せない。main側の差し替え口がコンパイルできるよう、
 * 同じ名前・同じ引数の関数だけを置き、常に「撮影モードではない」と答える。
 *
 * 実装は`src/debug`の同名オブジェクト。関数を増やすときは両方へ同じ形で足す
 * （片方だけに足すと、もう片方のビルドタイプでコンパイルが通らない）。
 */
@Suppress("UNUSED_PARAMETER", "FunctionOnlyReturningConstant")
object ShowcaseMode {
    fun isActive(context: Context): Boolean = false

    fun credentialsKeyValueStore(context: Context): SesameKeyValueStore? = null

    fun lockStateKeyValueStore(context: Context): SesameKeyValueStore? = null

    fun widgetAssignmentsKeyValueStore(context: Context): SesameKeyValueStore? = null

    fun commandsOrNull(context: Context): SesameDeviceCommands? = null
}
