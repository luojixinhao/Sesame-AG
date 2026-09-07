package io.github.aoguai.sesameag.ui.screen.card

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import io.github.aoguai.sesameag.BuildConfig
import io.github.aoguai.sesameag.data.General
import io.github.aoguai.sesameag.ui.compose.CommonAlertDialog
import io.github.aoguai.sesameag.ui.extension.openUrl
import io.github.aoguai.sesameag.ui.permissions.PermissionHealthSnapshot
import io.github.aoguai.sesameag.ui.permissions.PermissionRequirement
import io.github.aoguai.sesameag.ui.screen.components.DelayedLoadingIndicator
import io.github.aoguai.sesameag.ui.viewmodel.MainViewModel
import io.github.aoguai.sesameag.util.ModuleStatus.MIN_SUPPORTED_LIBXPOSED_API

@Composable
fun ModuleStatusCard(
    status: MainViewModel.ModuleStatus,
    permissionHealth: PermissionHealthSnapshot,
    hasActiveUser: Boolean,
    isLegalAccepted: Boolean,
    isSavingLegalAcceptance: Boolean,
    onRefresh: () -> Unit,
    onLegalAcceptedChange: (Boolean) -> Unit,
) {
    val context = LocalContext.current
    var showActivationSteps by rememberSaveable { mutableStateOf(false) }
    val canConfirmLegal = hasActiveUser &&
        permissionHealth.item(PermissionRequirement.MODULE_FILE)?.isGranted == true &&
        !isSavingLegalAcceptance
    val title = when (status) {
        MainViewModel.ModuleStatus.Loading -> "正在检查模块"
        MainViewModel.ModuleStatus.NotActivated -> "模块未激活"
        is MainViewModel.ModuleStatus.Unsupported -> "请使用受支持的 LSPosed"
        is MainViewModel.ModuleStatus.PrerequisitesMissing -> "模块未激活"
        is MainViewModel.ModuleStatus.Activated -> "模块已激活"
    }
    val blocking = status is MainViewModel.ModuleStatus.NotActivated ||
        status is MainViewModel.ModuleStatus.Unsupported || status is MainViewModel.ModuleStatus.PrerequisitesMissing

<<<<<<< HEAD
                    is MainViewModel.ModuleStatus.Unsupported -> {
                        Icon(Icons.Outlined.Warning, "不受支持")
                        Column(Modifier.padding(start = 20.dp)) {
                            Text(
                                text = "框架 API 版本过低",
                                style = MaterialTheme.typography.titleMedium
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(text = "${status.frameworkName} ${status.frameworkVersion} · API ${status.apiVersion}", style = MaterialTheme.typography.bodySmall)
                            Text(
                                text = "请改用支持 libxposed API 101+ 的框架或管理器。",
                                style = MaterialTheme.typography.bodyMedium
                            )
                            Text(text = "点击或双击卡片查看排查说明", style = MaterialTheme.typography.bodySmall)
                        }
                    }

                    is MainViewModel.ModuleStatus.NotActivated -> {
                        Icon(Icons.Outlined.Warning, "未激活")
                        Column(Modifier.padding(start = 20.dp)) {
                            Text(text = "模块未激活或管理器未连接", style = MaterialTheme.typography.titleMedium)
                            Spacer(Modifier.height(4.dp))
                            Text(text = "首次安装后请在LSPosed 管理器中勾选模块，并确认目标应用已加入作用域", style = MaterialTheme.typography.bodyMedium)
                            Text(text = "点击或双击卡片查看排查说明", style = MaterialTheme.typography.bodySmall)
                        }
                    }

                    is MainViewModel.ModuleStatus.Loading -> {
                        CircularProgressIndicator(modifier = Modifier.size(24.dp))
                        Column(Modifier.padding(start = 20.dp)) {
                            Text(text = "正在检查模块状态...", style = MaterialTheme.typography.titleMedium)
                        }
                    }
=======
    Surface(modifier = Modifier.fillMaxWidth(), color = MaterialTheme.colorScheme.surfaceContainerLow) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "模块状态",
                    modifier = Modifier.weight(1f).semantics { heading() },
                    style = MaterialTheme.typography.titleMedium,
                )
                IconButton(onClick = onRefresh) {
                    Icon(Icons.Outlined.Refresh, contentDescription = "重新检查模块")
>>>>>>> c9bcd6a38ab66cb5470405b09c522c4173762e75
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                if (status is MainViewModel.ModuleStatus.Loading) {
                    DelayedLoadingIndicator(modifier = Modifier.size(24.dp).semantics { stateDescription = "检查中" })
                } else {
                    Icon(
                        imageVector = if (blocking) Icons.Outlined.Warning else Icons.Outlined.CheckCircle,
                        contentDescription = null,
                        tint = if (blocking) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                    )
                }
                Text(
                    title,
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.titleLarge,
                    color = if (blocking) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
                )
            }
            Text("版本 ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})", style = MaterialTheme.typography.bodyMedium)
            when (status) {
                is MainViewModel.ModuleStatus.Activated -> {
                    Text("${status.frameworkName} ${status.frameworkVersion} · API ${status.apiVersion}", style = MaterialTheme.typography.bodySmall)
                }
                is MainViewModel.ModuleStatus.PrerequisitesMissing -> {
                    Text("${status.frameworkName} ${status.frameworkVersion} · API ${status.apiVersion}", style = MaterialTheme.typography.bodySmall)
                    Text(
<<<<<<< HEAD
                        text = "当前模块要求 libxposed API 101+；若管理器或框架仍停留在 API 100，模块不会生效。",
                        style = MaterialTheme.typography.bodySmall
=======
                        when {
                            !permissionHealth.areRequiredPermissionsGranted -> "必需权限未就绪"
                            isSavingLegalAcceptance -> "正在保存协议确认"
                            !isLegalAccepted -> "请勾选使用协议"
                            else -> "正在确认激活条件"
                        },
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodyMedium,
>>>>>>> c9bcd6a38ab66cb5470405b09c522c4173762e75
                    )
                }
                is MainViewModel.ModuleStatus.Unsupported -> {
                    Text("${status.frameworkName} ${status.frameworkVersion} · API ${status.apiVersion}", style = MaterialTheme.typography.bodySmall)
                    Text(
<<<<<<< HEAD
                        text = "首次使用时，请先在管理器中激活模块、把目标应用加入作用域，然后重新打开目标应用或返回首页复查。",
                        style = MaterialTheme.typography.bodySmall
=======
                        when (status.reason) {
                            MainViewModel.ModuleStatus.UnsupportedReason.API_TOO_LOW -> "请更新至支持 API $MIN_SUPPORTED_LIBXPOSED_API 或更高版本的 LSPosed"
                            MainViewModel.ModuleStatus.UnsupportedReason.NON_LSPOSED -> "请使用 LSPosed"
                        },
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyMedium,
>>>>>>> c9bcd6a38ab66cb5470405b09c522c4173762e75
                    )
                }
                MainViewModel.ModuleStatus.NotActivated -> Text("等待 LSPosed 连接", style = MaterialTheme.typography.bodyMedium)
                MainViewModel.ModuleStatus.Loading -> Text("正在读取框架信息", style = MaterialTheme.typography.bodyMedium)
            }
            if (status is MainViewModel.ModuleStatus.NotActivated || status is MainViewModel.ModuleStatus.Unsupported) {
                Button(
                    onClick = {
                        if (status is MainViewModel.ModuleStatus.Unsupported) {
                            context.openUrl(General.PROJECT_HOMEPAGE_URL)
                        } else {
                            showActivationSteps = true
                        }
                    },
                    enabled = !isSavingLegalAcceptance,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                ) {
                    Text(
<<<<<<< HEAD
                        text = "LSPatch、NPatch 等内置打包/补丁式分发不被支持，运行时会被直接拦截。",
                        style = MaterialTheme.typography.titleSmall
=======
                        if (status is MainViewModel.ModuleStatus.Unsupported) "查看安装要求" else "查看激活步骤",
                        modifier = Modifier.weight(1f),
>>>>>>> c9bcd6a38ab66cb5470405b09c522c4173762e75
                    )
                    Icon(Icons.AutoMirrored.Outlined.ArrowForward, contentDescription = null)
                }
            }
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).toggleable(
                        value = isLegalAccepted,
                        enabled = canConfirmLegal,
                        role = Role.Checkbox,
                        onValueChange = onLegalAcceptedChange,
                    ),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Checkbox(checked = isLegalAccepted, onCheckedChange = null, enabled = canConfirmLegal)
                    Text(
                        "我已阅读、理解并接受 LICENSE 与 LEGAL 中的相关说明",
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(
                        onClick = { context.openUrl("https://github.com/Sesame-AG/Sesame-AG/blob/dev/LICENSE") },
                        modifier = Modifier.heightIn(min = 48.dp),
                    ) { Text("LICENSE") }
                    TextButton(
                        onClick = { context.openUrl("https://github.com/Sesame-AG/Sesame-AG/blob/dev/LEGAL.md") },
                        modifier = Modifier.heightIn(min = 48.dp),
                    ) { Text("LEGAL") }
                }
            }
        }
    }
    CommonAlertDialog(
        showDialog = showActivationSteps,
        onDismissRequest = { showActivationSteps = false },
        onConfirm = onRefresh,
        title = "启用模块",
        text = "LSPosed → 模块 → 芝麻粒 → 启用<br>作用域 → 目标应用",
        confirmText = "重新检查",
        dismissText = "关闭",
    )
}
