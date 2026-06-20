package com.mbclaw.nonroot.sandbox

import android.content.Context
import kotlinx.coroutines.*
import java.io.File

/**
 * Termux 沙箱 — NonRoot 版完整 Linux 环境
 *
 * 策略:
 *   1. 检测是否已安装 Termux → 直接复用
 *   2. 未安装 → 引导用户安装 (通过 Intent 跳转 F-Droid 或直接下载 APK)
 *   3. 配置阿里云镜像源加速
 *   4. 通过 Termux:API 或 Runtime.exec("termux-bash") 执行命令
 *
 * Termux 包依赖:
 *   - proot (用户态 chroot)
 *   - python / clang / git (开发环境)
 *   - termux-api (Android API 桥接)
 */
class TermuxSandbox(private val context: Context) {

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val termuxDataDir = File("/data/data/com.termux/files")
    private val termuxHome = File(termuxDataDir, "home")
    private val mbclawDir = File(termuxHome, ".mbclaw")

    var isReady: Boolean = false; private set
    var termuxInstalled: Boolean = false; private set

    companion object {
        const val TERMUX_PACKAGE = "com.termux"
        const val TERMUX_API_PACKAGE = "com.termux.api"
        // 阿里云镜像 (默认)
        const val ALI_MIRROR = "https://mirrors.aliyun.com/termux/apt"
        // 备用: 清华
        const val TSINGHUA_MIRROR = "https://mirrors.tuna.tsinghua.edu.cn/termux"
        // Termux APK 下载 (F-Droid)
        const val TERMUX_APK_URL = "https://f-droid.org/repo/com.termux_118.apk"
    }

    suspend fun init(): SandboxResult = withContext(Dispatchers.IO) {
        try {
            // 检测 Termux 是否安装
            termuxInstalled = try {
                context.packageManager.getPackageInfo(TERMUX_PACKAGE, 0); true
            } catch (_: Exception) { false }

            if (!termuxInstalled) {
                return@withContext SandboxResult(-1, "", "Termux 未安装，请先安装 Termux")
            }

            // 初始化 MBclaw 工作目录
            mbclawDir.mkdirs()

            // 配置镜像源
            configureMirror()

            // 安装必要包
            installBasePackages()

            isReady = true
            SandboxResult(0, "Termux 沙箱就绪", "")
        } catch (e: Exception) {
            SandboxResult(-1, "", "初始化失败: ${e.message}")
        }
    }

    private fun configureMirror() {
        val sourcesList = File(termuxDataDir, "usr/etc/apt/sources.list")
        if (sourcesList.exists()) {
            val content = sourcesList.readText()
            if (!content.contains("aliyun")) {
                sourcesList.writeText("deb $ALI_MIRROR stable main\n")
            }
        }
    }

    private fun installBasePackages() {
        kotlinx.coroutines.runBlocking { execute("apt update -qq && apt install -y -qq proot python clang git curl wget 2>&1", 120) }
    }

    suspend fun execute(command: String, timeout: Int = 30): SandboxResult = withContext(Dispatchers.IO) {
        try {
            // 通过 Termux 执行: am 广播发送命令 → Termux:Tasker 接收执行
            // 或直接用 Runtime.exec 调用 termux 二进制
            val cmd = arrayOf(
                "/data/data/com.termux/files/usr/bin/bash", "-c",
                "cd ${mbclawDir.absolutePath} && $command"
            )
            val process = Runtime.getRuntime().exec(cmd)
            val stdout = process.inputStream.bufferedReader().readText()
            val stderr = process.errorStream.bufferedReader().readText()
            val exitCode = process.waitFor()
            SandboxResult(exitCode, stdout, stderr)
        } catch (e: Exception) {
            SandboxResult(-1, "", "执行失败: ${e.message}")
        }
    }

    /** 用 proot 创建一个隔离的 Linux 环境 */
    suspend fun createProotEnv(rootfsDir: String = "$mbclawDir/rootfs"): SandboxResult {
        return execute("""
            mkdir -p $rootfsDir
            proot -0 -r $rootfsDir -b /dev:/dev -b /proc:/proc -b /sys:/sys /usr/bin/bash -c "echo 'proot ready'"
        """.trimIndent(), 30)
    }

    suspend fun pipInstall(pkg: String): SandboxResult =
        execute("pip install $pkg", 120)

    suspend fun startPythonServer(scriptPath: String, port: Int = 8001): SandboxResult =
        execute("cd ${File(scriptPath).parent} && python ${File(scriptPath).name} --host 127.0.0.1 --port $port &", 10)

    fun destroy() { scope.launch { isReady = false } }

    // ── Termux APK 安装引导 ──
    fun getInstallIntent(): android.content.Intent {
        return try {
            context.packageManager.getLaunchIntentForPackage(TERMUX_PACKAGE)
                ?: android.content.Intent(android.content.Intent.ACTION_VIEW).apply {
                    data = android.net.Uri.parse(TERMUX_APK_URL)
                }
        } catch (_: Exception) {
            android.content.Intent(android.content.Intent.ACTION_VIEW).apply {
                data = android.net.Uri.parse(TERMUX_APK_URL)
            }
        }
    }

    data class SandboxResult(val exitCode: Int, val stdout: String, val stderr: String) {
        val isSuccess: Boolean get() = exitCode == 0
    }
}

// 后台沙箱服务
class TermuxSandboxService : android.app.Service() {
    private lateinit var sandbox: TermuxSandbox
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    override fun onCreate() {
        super.onCreate()
        sandbox = TermuxSandbox(this)
        scope.launch { sandbox.init() }
    }

    override fun onBind(intent: android.content.Intent?): android.os.IBinder? = null

    override fun onStartCommand(intent: android.content.Intent?, flags: Int, startId: Int): Int {
        scope.launch { sandbox.execute(intent?.getStringExtra("cmd") ?: "echo pong") }
        return START_STICKY
    }

    override fun onDestroy() { scope.cancel(); sandbox.destroy(); super.onDestroy() }
}
