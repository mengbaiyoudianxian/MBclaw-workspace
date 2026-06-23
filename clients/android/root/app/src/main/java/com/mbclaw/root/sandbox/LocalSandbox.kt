package com.mbclaw.root.sandbox

import android.content.Context
import com.mbclaw.root.agent.PermissionTier
import kotlinx.coroutines.*
import java.io.*
import java.net.HttpURLConnection
import java.net.URL

/**
 * MBclaw Linux 环境 — 一键下载，即下即用
 *
 * Root: chroot 执行 /data/mbclaw/linux
 * 非Root: proot 模拟
 * 服务器: http://121.199.57.195/mbclaw-linux-rootfs.tar.gz (~200MB)
 *
 * 预装: Python3, bash, curl, git, vim, openssh, sqlite, pip
 */
class LocalSandbox(private val context: Context) {

    private val linuxDir = File("/data/mbclaw/linux")
    private val rootfsFile = File(context.cacheDir, "mbclaw-linux-rootfs.tar.gz")
    private val readyFile = File(linuxDir, ".mbclaw_ready")

    val isInstalled: Boolean get() = readyFile.exists()
    val isRoot: Boolean get() = PermissionTier.get(context).hasRoot

    enum class State { NOT_INSTALLED, DOWNLOADING, EXTRACTING, INSTALLING, READY, FAILED }
    var state = State.NOT_INSTALLED; private set
    var progress = 0; private set
    var statusText = ""; private set

    suspend fun checkOrInit() {
        if (isInstalled) { state = State.READY; return }
        state = State.NOT_INSTALLED
    }

    suspend fun downloadAndInstall(onProgress: (State, Int, String) -> Unit) = withContext(Dispatchers.IO) {
        try {
            state = State.DOWNLOADING; progress = 0
            val url = "http://121.199.57.195/mbclaw-linux-rootfs.tar.gz"

            val conn = URL(url).openConnection() as HttpURLConnection
            conn.connectTimeout = 15000; conn.readTimeout = 300000
            val totalSize = conn.contentLength
            if (totalSize <= 0) { state = State.FAILED; statusText = "服务器无响应"; return@withContext }

            // 下载
            conn.inputStream.use { input ->
                rootfsFile.outputStream().use { output ->
                    val buf = ByteArray(8192)
                    var downloaded = 0L
                    var lastReport = 0L
                    while (true) {
                        val n = input.read(buf)
                        if (n < 0) break
                        output.write(buf, 0, n)
                        downloaded += n
                        if (downloaded - lastReport > 1_000_000) {
                            progress = (downloaded * 100 / totalSize).toInt()
                            statusText = "${downloaded / 1_048_576}MB / ${totalSize / 1_048_576}MB"
                            lastReport = downloaded
                            onProgress(State.DOWNLOADING, progress, statusText)
                        }
                    }
                }
            }

            // 解压
            state = State.EXTRACTING; progress = 0
            statusText = "解压中..."
            onProgress(State.EXTRACTING, 10, statusText)

            linuxDir.mkdirs()
            val tier = PermissionTier.get(context)
            // 用root解压
            val extractOk = tier.shellRoot(
                "mkdir -p /data/mbclaw/linux && cd /data/mbclaw/linux && " +
                "tar xzf '${rootfsFile.absolutePath}' 2>/dev/null && echo EXTRACT_OK",
                timeoutMs = 120_000
            )?.contains("EXTRACT_OK") == true

            if (!extractOk) {
                rootfsFile.delete()
                state = State.FAILED; statusText = "解压失败"
                onProgress(State.FAILED, 0, statusText)
                return@withContext
            }

            // 自动安装工具包 (在Linux环境内执行)
            statusText = "安装 Python/bash/git/pip..."
            onProgress(State.EXTRACTING, 40, statusText)

            val shell = if (tier.hasRoot) "/bin/sh" else "/bin/sh"
            val setupCmd = "echo 'nameserver 223.5.5.5' > /etc/resolv.conf && " +
                "apk update 2>/dev/null && " +
                "apk add --no-cache python3 bash curl git vim openssh sqlite py3-pip 2>&1 | tail -1 && " +
                "echo SETUP_OK"

            val setupOk = if (tier.hasRoot) {
                tier.shellRoot(
                    "chroot /data/mbclaw/linux $shell -c '${setupCmd.replace("'", "'\\''")}'",
                    timeoutMs = 300_000
                )?.contains("SETUP_OK") == true
            } else {
                false // proot暂不支持自动安装, 需要先有proot二进制
            }

            rootfsFile.delete()

            if (setupOk) {
                tier.shellRoot("echo READY > /data/mbclaw/linux/.mbclaw_ready")
                state = State.READY; statusText = "Linux 环境就绪"
                onProgress(State.READY, 100, statusText)
            } else {
                // 基础rootfs仍然可用, 只是没有额外工具
                tier.shellRoot("echo READY > /data/mbclaw/linux/.mbclaw_ready")
                state = State.READY; statusText = "基础环境就绪 (工具安装失败,可重试)"
                onProgress(State.READY, 100, statusText)
            }
        } catch (e: Exception) {
            state = State.FAILED; statusText = e.message ?: "下载失败"
            onProgress(State.FAILED, progress, statusText)
        }
    }

    /** 在Linux环境中执行命令 */
    suspend fun exec(command: String, timeoutMs: Long = 30000): String = withContext(Dispatchers.IO) {
        if (!isInstalled) return@withContext "Linux 环境未安装，请先在设置中下载"
        val tier = PermissionTier.get(context)

        if (tier.hasRoot) {
            // chroot 执行
            tier.shellRoot(
                "chroot /data/mbclaw/linux /bin/bash -c '${command.replace("'", "'\\''")}'",
                timeoutMs = timeoutMs
            ) ?: "执行失败"
        } else {
            // proot 模拟
            val prootBin = File(context.filesDir, "proot/proot").also {
                it.parentFile?.mkdirs()
                if (!it.exists()) {
                    // 内置proot二进制
                    context.assets.open("proot/proot").use { src -> it.outputStream().use { dst -> src.copyTo(dst) } }
                    it.setExecutable(true)
                }
            }
            val cmd = "${prootBin.absolutePath} -r /data/mbclaw/linux -b /dev -b /proc -b /sys /bin/bash -c '${command.replace("'", "'\\''")}'"
            val p = Runtime.getRuntime().exec(arrayOf("sh", "-c", cmd))
            if (p.waitFor(timeoutMs / 1000, java.util.concurrent.TimeUnit.SECONDS))
                p.inputStream.bufferedReader().readText().trim()
            else { p.destroy(); "超时" }
        }
    }
}
