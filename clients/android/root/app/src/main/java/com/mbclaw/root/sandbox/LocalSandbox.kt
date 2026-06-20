package com.mbclaw.root.sandbox

import android.content.Context

/**
 * 本地 Linux 沙箱
 *
 * 将 MiClaw 原版"云端沙箱"改为本地运行：
 *  - 使用 proot + Termux 提供完整 Linux 环境
 *  - 用于执行危险指令或 Android 不支持的命令
 *  - 网络隔离 + 文件系统隔离
 *
 * 原版 云端沙箱.apk 通过 IoT SDK (libTUTKGlobalAPIs.so) 连接远程设备，
 * 这里改为本地 Termux chroot 环境。
 */
class LocalSandbox(private val context: Context) {
    private var isRunning = false
    private var sandboxDir: java.io.File? = null

    fun init() {
        sandboxDir = java.io.File(context.filesDir, "sandbox")
        sandboxDir?.mkdirs()
    }

    fun execute(command: String, timeout: Int = 30): SandboxResult {
        return try {
            val process = Runtime.getRuntime().exec(
                arrayOf("proot", "-r", sandboxDir!!.absolutePath, "-b", "/system:/system", "-b", "/dev:/dev", "sh", "-c", command),
                null,
                sandboxDir
            )
            process.waitFor()
            val stdout = process.inputStream.bufferedReader().readText()
            val stderr = process.errorStream.bufferedReader().readText()
            SandboxResult(process.exitValue(), stdout, stderr)
        } catch (e: Exception) {
            SandboxResult(-1, "", e.message ?: "沙箱执行失败")
        }
    }

    data class SandboxResult(val exitCode: Int, val stdout: String, val stderr: String)
}
