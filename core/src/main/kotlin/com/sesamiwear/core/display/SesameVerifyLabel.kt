package com.sesamiwear.core.display

/**
 * 「送信後の状態確認」設定（BL-293）の表示文言。
 *
 * ONにすると命令ごとにWeb APIを2回多く使い（月間リクエスト上限、BL-141）、結果が数秒遅れることを
 * 設定画面で伝える。Android非依存のためユニットテスト対象。
 */
object SesameVerifyLabel {
    const val TITLE = "送信後の状態確認"

    const val DESCRIPTION =
        "インターネット経由で施錠・解錠するとき、送信の前と、送信して数秒待ったあとに鍵の状態を確認し、命令どおりに動いたかを確かめます。" +
            "動いていなければ（Hub 3がオフラインで届いていない場合を含む）「未反映」として知らせます。" +
            "施錠・解錠のたびにWeb APIを2回多く使うため、月間のリクエスト上限に早く達します。" +
            "また、結果が出るまで数秒遅れます。Bluetoothで操作したときは確認しません。"

    fun statusOf(isEnabled: Boolean): String = if (isEnabled) "オン" else "オフ"
}
