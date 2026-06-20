package com.mbclaw.nonroot

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.mbclaw.nonroot.ui.MainChatScreen
import com.mbclaw.nonroot.ui.theme.MBclawLiteTheme

/**
 * MBclaw Lite — 非Root主Activity
 * 模仿GPT简洁聊天界面 + MiClaw智能助手元素
 */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MBclawLiteTheme {
                Surface(Modifier.fillMaxSize()) {
                    MainChatScreen()
                }
            }
        }
    }
}
