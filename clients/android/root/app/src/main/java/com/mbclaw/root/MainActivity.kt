package com.mbclaw.root

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.mbclaw.root.ui.MBclawMainScreen
import com.mbclaw.root.ui.theme.MBclawTheme

/**
 * MBclaw Root 主 Activity
 *
 * 保留 70% MiClaw 原有 UI 结构 + 替换 AI 后端为 MBclaw
 * 语音唤醒 → MBclaw 而非小爱同学
 */
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // AgentService 由 BootReceiver 或用户手动启动

        setContent {
            MBclawTheme(darkTheme = true) {
                Surface(modifier = Modifier.fillMaxSize()) {
                    MBclawMainScreen()
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
    }
}
