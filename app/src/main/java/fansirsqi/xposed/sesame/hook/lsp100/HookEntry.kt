package fansirsqi.xposed.sesame.hook.lsp100

import android.content.pm.ApplicationInfo
import android.util.Log
import fansirsqi.xposed.sesame.data.General
import fansirsqi.xposed.sesame.hook.ApplicationHook
import fansirsqi.xposed.sesame.hook.XposedEnv
import io.github.libxposed.api.XposedInterface
import io.github.libxposed.api.XposedModule
import io.github.libxposed.api.XposedModuleInterface.ModuleLoadedParam
import io.github.libxposed.api.XposedModuleInterface.PackageLoadedParam
import io.github.libxposed.api.XposedModuleInterface.PackageReadyParam

class HookEntry() : XposedModule() {
    private val tag = "LsposedEntry"
    private var processName = ""
    private var runtimeApiVersion = 100
    private var initialized = false
    private val customHooker = ApplicationHook()

    // API 100 仍通过构造器传入框架接口和进程参数，这里保留旧签名避免直接断兼容。
    constructor(base: XposedInterface, param: ModuleLoadedParam) : this() {
        initialize(base, param.processName)
    }

    override fun onModuleLoaded(param: ModuleLoadedParam) {
        initialize(this, param.processName)
    }

    override fun onPackageLoaded(param: PackageLoadedParam) {
        try {
            if (!shouldHandlePackage(param.packageName)) return
            if (runtimeApiVersion >= 101) {
                val defaultClassLoaderName = extractDefaultPackageClassLoader(param)?.javaClass?.name ?: "null"
                logInfo(
                    "onPackageLoaded ${param.packageName} observed runtimeApiVersion=$runtimeApiVersion with defaultClassLoader=$defaultClassLoaderName, waiting for onPackageReady"
                )
                return
            }
            val legacyClassLoader = extractLegacyPackageClassLoader(param)
            if (legacyClassLoader == null) {
                val defaultClassLoaderName = extractDefaultPackageClassLoader(param)?.javaClass?.name ?: "null"
                logInfo(
                    "onPackageLoaded ${param.packageName} only exposed defaultClassLoader=$defaultClassLoaderName, waiting for onPackageReady"
                )
                return
            }
            prepareEnv(param.packageName, param.applicationInfo, legacyClassLoader)
            customHooker.loadPackage(param)
            logInfo("Hooking ${param.packageName} in process $processName via onPackageLoaded")
        } catch (e: Throwable) {
            logError("Hook failed - ${e.message}", e)
        }
    }

    override fun onPackageReady(param: PackageReadyParam) {
        try {
            if (!shouldHandlePackage(param.packageName)) return
            prepareEnv(param.packageName, param.applicationInfo, param.classLoader)
            customHooker.loadPackage(param)
            logInfo("Hooking ${param.packageName} in process $processName via onPackageReady")
        } catch (e: Throwable) {
            logError("Hook failed - ${e.message}", e)
        }
    }

    private fun initialize(base: XposedInterface, processName: String) {
        if (initialized) return
        initialized = true
        this.processName = processName
        customHooker.xposedInterface = base
        runtimeApiVersion = resolveRuntimeApiVersion(base)

        logInfo("Initialized for process $processName", base)

        val frameworkName = runCatching { base.frameworkName }.getOrDefault("unknown")
        val frameworkVersion = runCatching { base.frameworkVersion }.getOrDefault("unknown")
        val frameworkVersionCode = runCatching { base.frameworkVersionCode }.getOrDefault(-1L)
        val moduleProcess = resolveModuleProcessName(base) ?: "unknown"
        logInfo(
            "Framework from base: $frameworkName $frameworkVersion $frameworkVersionCode api=$runtimeApiVersion target_model_process: $moduleProcess",
            base
        )
    }

    private fun prepareEnv(packageName: String, applicationInfo: ApplicationInfo, classLoader: ClassLoader) {
        XposedEnv.classLoader = classLoader
        XposedEnv.appInfo = applicationInfo
        XposedEnv.packageName = packageName
        XposedEnv.processName = processName
    }

    private fun shouldHandlePackage(packageName: String): Boolean {
        return General.PACKAGE_NAME == packageName
    }

    private fun extractDefaultPackageClassLoader(param: PackageLoadedParam): ClassLoader? {
        return runCatching {
            param.defaultClassLoader
        }.getOrNull()
            ?: runCatching {
                param.javaClass.methods
                    .firstOrNull { it.name == "getDefaultClassLoader" && it.parameterCount == 0 }
                    ?.invoke(param) as? ClassLoader
            }.getOrNull()
    }

    private fun extractLegacyPackageClassLoader(param: PackageLoadedParam): ClassLoader? {
        return runCatching {
            param.javaClass.methods
                .firstOrNull { it.name == "getClassLoader" && it.parameterCount == 0 }
                ?.invoke(param) as? ClassLoader
        }.getOrNull()
            ?: runCatching {
                param.javaClass.methods
                    .firstOrNull { it.name == "getAppClassLoader" && it.parameterCount == 0 }
                    ?.invoke(param) as? ClassLoader
            }.getOrNull()
    }

    private fun resolveModuleProcessName(base: XposedInterface): String? {
        return runCatching { base.moduleApplicationInfo.processName }.getOrNull()
            ?: runCatching {
                base.javaClass.methods
                    .firstOrNull { it.name == "getApplicationInfo" && it.parameterCount == 0 }
                    ?.invoke(base) as? ApplicationInfo
            }.getOrNull()?.processName
    }

    private fun resolveRuntimeApiVersion(base: XposedInterface): Int {
        return runCatching {
            base.javaClass.methods
                .firstOrNull { it.name == "getApiVersion" && it.parameterCount == 0 }
                ?.invoke(base) as? Int
        }.getOrNull()
            ?: runCatching { XposedInterface::class.java.getDeclaredField("API").getInt(null) }.getOrDefault(100)
    }

    private fun logInfo(message: String, base: XposedInterface? = null) {
        logWithPriority(Log.INFO, message, null, base)
    }

    private fun logError(message: String, throwable: Throwable? = null, base: XposedInterface? = null) {
        logWithPriority(Log.ERROR, message, throwable, base)
    }

    private fun logWithPriority(
        priority: Int,
        message: String,
        throwable: Throwable?,
        base: XposedInterface? = null
    ) {
        val logger = base ?: customHooker.xposedInterface
        if (logger != null) {
            if (throwable != null) {
                logger.log(priority, tag, message, throwable)
            } else {
                logger.log(priority, tag, message)
            }
            return
        }
        if (throwable != null) {
            log(priority, tag, message, throwable)
        } else {
            log(priority, tag, message)
        }
    }
}
