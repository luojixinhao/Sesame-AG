package fansirsqi.xposed.sesame.service.patch

import com.niki.cmd.Shell
import com.niki.cmd.model.bean.ShellResult
import fansirsqi.xposed.sesame.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import java.io.File

class SafeRootShell : Shell {
    companion object {
        private const val TAG = "SafeRootShell"
    }

    override val TEST_TIMEOUT: Long = 20_000L
    override val PERMISSION_LEVEL: String = "Root"

    override suspend fun isAvailable(): Boolean {
        // 直接传原始命令，不需要自己拼 "su -c ..."
        val result = runCommand("echo test", TEST_TIMEOUT)

        val available = result.isSuccess && result.stdout.trim().contains("test")
        if (!available) {
            Log.w(TAG, "Root检测失败: Code=${result.exitCode}, Err='${result.stderr}'")
        }
        return available
    }

    override suspend fun exec(command: String): ShellResult {
        return runCommand(command, Long.MAX_VALUE)
    }

    override suspend fun exec(command: String, timeoutMillis: Long): ShellResult {
        return runCommand(command, timeoutMillis)
    }

    private suspend fun runCommand(cmd: String, timeoutMillis: Long): ShellResult {
        return withContext(Dispatchers.IO) {
            try {
                if (timeoutMillis < Long.MAX_VALUE) {
                    withTimeout(timeoutMillis) { executeSu(cmd) }
                } else {
                    executeSu(cmd)
                }
            } catch (e: Exception) {
                Log.e(TAG, "命令执行异常: ${e.message}")
                ShellResult.error(e.message ?: "Execution failed")
            }
        }
    }

    /**
     * 🔥 核心修复：使用 String[] 数组传参
     * 这样 Java 就不会因为空格或引号而错误地切分命令了
     */
    private fun executeSu(command: String): ShellResult {
        val failures = ArrayList<String>()
        for (suPath in resolveSuCandidates()) {
            try {
                val process = Runtime.getRuntime().exec(arrayOf(suPath, "-c", command))
                val stdout = process.inputStream.bufferedReader().use { it.readText() }.trim()
                val stderr = process.errorStream.bufferedReader().use { it.readText() }.trim()
                val exitCode = process.waitFor()
                return ShellResult(stdout, stderr, exitCode)
            } catch (t: Throwable) {
                failures.add("$suPath -> ${t.message}")
            }
        }
        throw IllegalStateException(
            failures.joinToString(" | ").ifBlank { "未找到可用的 su 可执行文件" }
        )
    }

    private fun resolveSuCandidates(): List<String> {
        val pathCandidates = System.getenv("PATH")
            .orEmpty()
            .split(":")
            .mapNotNull { dir ->
                val path = dir.trim()
                if (path.isEmpty()) null else path.trimEnd('/') + "/su"
            }

        return (
            listOf(
                "/system/bin/su",
                "/system/xbin/su",
                "/sbin/su",
                "/vendor/bin/su",
                "/su/bin/su",
                "/magisk/.core/bin/su",
                "/debug_ramdisk/su",
                "su"
            ) + pathCandidates
            ).distinct().filter { candidate ->
            !candidate.contains("/") || File(candidate).exists()
        }
    }
}
