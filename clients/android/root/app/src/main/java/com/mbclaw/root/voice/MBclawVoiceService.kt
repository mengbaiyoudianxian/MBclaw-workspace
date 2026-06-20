package com.mbclaw.root.voice

import android.app.Service
import android.content.Intent
import android.os.Bundle
import android.os.IBinder
import android.service.voice.VoiceInteractionService
import android.service.voice.VoiceInteractionSession
import android.service.voice.VoiceInteractionSessionService

/**
 * MBclaw 语音唤醒服务
 *
 * 通过 VoiceInteractionService 框架实现：
 *   - 热词唤醒 (如 "Hey MBclaw" / "你好小孟")
 *   - DSP 一直监听，低功耗不费电
 *   - 唤醒后打开 MBclaw 主界面并自动开始语音输入
 *
 * Root 优势：
 *   - 可以替换系统默认语音助手
 *   - Magisk 模块可替换小爱同学的 DSP 唤醒词固件
 *   - 系统级权限：锁屏唤醒、后台录音
 */

// ── Session Service (Android 框架入口) ──

class MBclawVoiceSessionService : VoiceInteractionSessionService() {
    override fun onNewSession(args: Bundle?): VoiceInteractionSession {
        return MBclawVoiceSession(this)
    }
}

// ── Voice Session (实际处理) ──

class MBclawVoiceSession(service: VoiceInteractionSessionService) :
    VoiceInteractionSession(service) {

    override fun onCreate() {
        super.onCreate()
        // 注册热词: "Hey MBclaw" / "你好小孟"
        // 需要 DSP 固件支持，Root 设备可通过 Magisk 模块替换
        val hotwords = arrayOf(
            // 简单版: 软件层 VAD 检测
            // 完整版: 替换 DSP 唤醒词固件 (需 Magisk 模块)
        )
        // setKeyphraseHints(hotwords)
    }

    override fun onLaunchVoiceActivity() {
        // 唤醒后打开 MBclaw MainActivity
        val intent = Intent(context, Class.forName("com.mbclaw.root.MainActivity")).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            putExtra("voice_triggered", true)
            putExtra("auto_start_listening", true)
        }
        context.startActivity(intent)
    }

    override fun onStartListening(recognizerIntent: Intent?) {
        // 开始语音识别
        // 呼叫 Android SpeechRecognizer 或本地 VAD 引擎
        super.onStartListening(recognizerIntent)
    }

    override fun onResults(results: Bundle?) {
        // 识别结果 → 直接发送给 MBclaw Agent
        val matches = results?.getStringArrayList(android.speech.SpeechRecognizer.RESULTS_RECOGNITION)
        val text = matches?.firstOrNull() ?: return
        // 通过广播或 Intent 传给 MainActivity
        val intent = Intent("com.mbclaw.VOICE_RESULT").apply {
            putExtra("text", text)
            setPackage(context.packageName)
        }
        context.sendBroadcast(intent)
    }
}

// ── 传统 Service 兼容层 (非系统默认语音助手时使用) ──

class MBclawVoiceService : Service() {
    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        // 兼容方案: 前台服务 + 麦克风持续监听
        // 用于非系统镜像模式下替代 DSP 唤醒
    }
}

// ── 语音识别广播接收处理 ──

class VoiceResultReceiver : android.content.BroadcastReceiver() {
    override fun onReceive(context: android.content.Context, intent: Intent) {
        val text = intent.getStringExtra("text") ?: return
        // 转发到 MainActivity 处理
        val mainIntent = Intent(context, Class.forName("com.mbclaw.root.MainActivity")).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            putExtra("voice_text", text)
        }
        context.startActivity(mainIntent)
    }
}
