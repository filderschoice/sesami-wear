package com.sesamiwear.mobile.credentials

import com.sesamiwear.core.SesameCredentials

/**
 * 設定画面での資格情報リストの追加・更新を行う（BL-100）。
 *
 * 以前は保存処理が `list.filterNot { it.uuid == 入力中のuuid } + 新しい値` となっており、
 * 編集対象の元uuidではなくフォームの現在値で絞り込んでいた。このため編集中にuuidを変更すると
 * 元の項目が残ったまま新しい項目が追加され、更新のつもりが重複登録になっていた。あわせて、
 * 編集した項目が常にリスト末尾へ移動し、一覧の並び順が保持されない問題もあった。
 *
 * 画面から切り離した純Kotlinのobjectとして持つことで、Android非依存にユニットテストできる
 * （`CLAUDE.md`「よく使うコマンド」の方針）。
 */
object CredentialsListEditor {
    /**
     * [credentialsList] へ [edited] を反映した新しいリストを返す。
     *
     * @param editingUuid 編集を開始した時点の対象uuid。新規追加の場合はnull。
     *   uuid自体が編集された場合でも、この値で元の項目を特定する。
     * @return 既存項目の更新時は同じ位置を保ったまま置き換えたリスト。新規追加時は末尾へ追加した
     *   リスト。編集でuuidを変更した結果、別の既存項目とuuidが衝突する場合は、その衝突した項目を
     *   取り除く（uuidをデバイスの一意キーとして扱うため、重複を残さない）。
     */
    fun upsert(
        credentialsList: List<SesameCredentials>,
        editingUuid: String?,
        edited: SesameCredentials,
    ): List<SesameCredentials> {
        // 新規追加でも、入力されたuuidが既存項目と一致する場合はその項目を更新する
        // （「既存uuidでの保存は上書き」という従来からの画面仕様を維持する）。
        val targetUuid = editingUuid ?: edited.uuid
        val targetIndex = credentialsList.indexOfFirst { it.uuid == targetUuid }
        if (targetIndex < 0) return credentialsList + edited

        return credentialsList
            .mapIndexed { index, credentials -> if (index == targetIndex) edited else credentials }
            // uuidを変更した結果、更新対象以外の項目とuuidが衝突した場合はその項目を取り除く。
            .filterIndexed { index, credentials -> index == targetIndex || credentials.uuid != edited.uuid }
    }
}
