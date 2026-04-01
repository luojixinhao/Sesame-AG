package io.github.aoguai.sesameag.task

import io.github.aoguai.sesameag.model.BaseModel
import io.github.aoguai.sesameag.model.modelFieldExt.TimeWindowListModelField
import io.github.aoguai.sesameag.util.Log
import io.github.aoguai.sesameag.util.TimeTriggerEvaluator
import io.github.aoguai.sesameag.util.TimeTriggerParseOptions
import io.github.aoguai.sesameag.util.TimeTriggerParser

/**
 * 通用任务工具类
 *
 * 提供任务相关的通用功能，包括时间判断和状态更新。
 */
object TaskCommon {
    private val AFTER_EIGHT_AM_SPEC = TimeTriggerParser.parse(
        "0800-2400",
        TimeTriggerParseOptions(
            allowCheckpoints = false,
            allowWindows = true,
            allowBlockedWindows = false,
            tag = "TaskCommon"
        )
    )
    
    @Volatile
    @JvmField
    var IS_ENERGY_TIME: Boolean = false
    
    @Volatile
    @JvmField
    var IS_AFTER_8AM: Boolean = false
    
    @Volatile
    @JvmField
    var IS_MODULE_SLEEP_TIME: Boolean = false

    @JvmStatic
    fun update() {
        val currentTimeMillis = System.currentTimeMillis()

        // 只收能量时间检查
        IS_ENERGY_TIME = checkTimeRangeConfig(
            BaseModel.energyTime,
            "只收能量时间",
            currentTimeMillis
        )

        // 模块休眠时间检查
        IS_MODULE_SLEEP_TIME = checkTimeRangeConfig(
            BaseModel.modelSleepTime,
            "模块休眠时间",
            currentTimeMillis
        )

        // 是否过了 8 点
        IS_AFTER_8AM = TimeTriggerEvaluator.evaluateNow(AFTER_EIGHT_AM_SPEC, currentTimeMillis).allowNow

        // 输出状态更新日志（已注释）
        /*
        Log.runtime("TaskCommon Update 完成:\n" +
                "只收能量时间配置: $IS_ENERGY_TIME\n" +
                "模块休眠配置: $IS_MODULE_SLEEP_TIME\n" +
                "当前是否过了8点: $IS_AFTER_8AM")
        */
    }

    /**
     * 检查时间配置是否在当前时间范围内
     *
     * @param field 配置字段
     * @param label 配置标签（用于日志输出）
     * @param currentTime 当前时间
     * @return 是否在时间范围内
     */
    private fun checkTimeRangeConfig(
        field: TimeWindowListModelField,
        label: String,
        currentTime: Long
    ): Boolean {
        if (field.isDisabled()) {
            Log.runtime("$label 配置已关闭")
            return false
        }

        Log.runtime("获取 $label 配置: ${field.value}")
        return field.isActive(currentTime)
    }
}

