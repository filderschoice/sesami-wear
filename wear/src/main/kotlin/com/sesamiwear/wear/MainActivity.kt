package com.sesamiwear.wear

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.wear.compose.material.MaterialTheme
import com.sesamiwear.wear.status.SesameStatusListScreen

/**
 * ウォッチのアプリ本体。登録済みセサミの状態一覧を出す（BL-170）。
 *
 * 初回実装以降「Sesami Wear」と表示するだけのスタブだったが、Tileには入りきらない情報
 * （電池残量・経路）の置き場として状態一覧にした。操作はTile・Complicationから行う。
 * 画面そのものの実装は[SesameStatusListScreen]にある。
 */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Box(modifier = Modifier.fillMaxSize()) {
                    SesameStatusListScreen()
                }
            }
        }
    }
}
