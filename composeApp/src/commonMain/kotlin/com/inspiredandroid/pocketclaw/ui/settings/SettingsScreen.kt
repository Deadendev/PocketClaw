@file:OptIn(ExperimentalMaterial3Api::class)

package com.inspiredandroid.pocketclaw.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Alignment.Companion.CenterHorizontally
import androidx.compose.ui.Alignment.Companion.CenterVertically
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.inspiredandroid.pocketclaw.BackIcon
import com.inspiredandroid.pocketclaw.SandboxController
import com.inspiredandroid.pocketclaw.Version
import com.inspiredandroid.pocketclaw.data.EmailAccount
import com.inspiredandroid.pocketclaw.data.HeartbeatLogEntry
import com.inspiredandroid.pocketclaw.data.ImportSection
import com.inspiredandroid.pocketclaw.data.MemoryEntry
import com.inspiredandroid.pocketclaw.data.ScheduledTask
import com.inspiredandroid.pocketclaw.data.Service
import com.inspiredandroid.pocketclaw.data.SharedJson
import com.inspiredandroid.pocketclaw.data.TaskStatus
import com.inspiredandroid.pocketclaw.data.TaskTrigger
import com.inspiredandroid.pocketclaw.data.ThemeMode
import com.inspiredandroid.pocketclaw.data.detectImportSections
import com.inspiredandroid.pocketclaw.formatFileSize
import com.inspiredandroid.pocketclaw.inference.DevicePerformance
import com.inspiredandroid.pocketclaw.inference.DownloadError
import com.inspiredandroid.pocketclaw.inference.LocalModel
import com.inspiredandroid.pocketclaw.inference.calculateDevicePerformance
import com.inspiredandroid.pocketclaw.inference.estimateGpuMemoryMb
import com.inspiredandroid.pocketclaw.mcp.PopularMcpServer
import com.inspiredandroid.pocketclaw.network.dtos.SponsorsResponseDto
import com.inspiredandroid.pocketclaw.network.tools.ToolInfo
import com.inspiredandroid.pocketclaw.saveFileToDevice
import com.inspiredandroid.pocketclaw.ui.PocketClawClearableTextField
import com.inspiredandroid.pocketclaw.ui.PocketClawOutlinedTextField
import com.inspiredandroid.pocketclaw.ui.components.PocketClawSlider
import com.inspiredandroid.pocketclaw.ui.components.SettingsListItem
import com.inspiredandroid.pocketclaw.ui.components.VerticalScrollbarForScroll
import com.inspiredandroid.pocketclaw.ui.handCursor
import com.inspiredandroid.pocketclaw.ui.icons.DragIndicator
import com.inspiredandroid.pocketclaw.ui.icons.Replay
import com.inspiredandroid.pocketclaw.ui.icons.Visibility
import com.inspiredandroid.pocketclaw.ui.icons.VisibilityOff
import com.inspiredandroid.pocketclaw.ui.pocketclawAdaptiveCardBorder
import com.inspiredandroid.pocketclaw.ui.pocketclawAdaptiveCardColors
import com.inspiredandroid.pocketclaw.ui.pocketclawAdaptiveCardSurface
import com.inspiredandroid.pocketclaw.ui.sandbox.SandboxProgressRow
import io.github.vinceglb.filekit.dialogs.FileKitType
import io.github.vinceglb.filekit.dialogs.compose.rememberFilePickerLauncher
import io.github.vinceglb.filekit.readBytes
import pocketclaw.composeapp.generated.resources.Res
import pocketclaw.composeapp.generated.resources.default_soul
import pocketclaw.composeapp.generated.resources.github_mark
import pocketclaw.composeapp.generated.resources.ic_arrow_drop_down
import pocketclaw.composeapp.generated.resources.litert_cancel
import pocketclaw.composeapp.generated.resources.litert_context_size
import pocketclaw.composeapp.generated.resources.litert_download
import pocketclaw.composeapp.generated.resources.litert_error_download_incomplete
import pocketclaw.composeapp.generated.resources.litert_error_network
import pocketclaw.composeapp.generated.resources.litert_error_not_enough_disk_space
import pocketclaw.composeapp.generated.resources.litert_free_space
import pocketclaw.composeapp.generated.resources.litert_on_device_description
import pocketclaw.composeapp.generated.resources.litert_performance_good
import pocketclaw.composeapp.generated.resources.litert_performance_ok
import pocketclaw.composeapp.generated.resources.litert_performance_poor
import pocketclaw.composeapp.generated.resources.litert_recommended
import pocketclaw.composeapp.generated.resources.litert_tool_support
import pocketclaw.composeapp.generated.resources.settings_add_service
import pocketclaw.composeapp.generated.resources.settings_ai_mistakes_warning
import pocketclaw.composeapp.generated.resources.settings_api_key_label
import pocketclaw.composeapp.generated.resources.settings_api_key_optional_label
import pocketclaw.composeapp.generated.resources.settings_base_url_label
import pocketclaw.composeapp.generated.resources.settings_become_sponsor
import pocketclaw.composeapp.generated.resources.settings_business_partnerships
import pocketclaw.composeapp.generated.resources.settings_business_partnerships_description
import pocketclaw.composeapp.generated.resources.settings_contact_sponsorship
import pocketclaw.composeapp.generated.resources.settings_daemon_mode
import pocketclaw.composeapp.generated.resources.settings_daemon_mode_description
import pocketclaw.composeapp.generated.resources.settings_documentation
import pocketclaw.composeapp.generated.resources.settings_dynamic_ui
import pocketclaw.composeapp.generated.resources.settings_dynamic_ui_description
import pocketclaw.composeapp.generated.resources.settings_export
import pocketclaw.composeapp.generated.resources.settings_export_import_description
import pocketclaw.composeapp.generated.resources.settings_export_import_title
import pocketclaw.composeapp.generated.resources.settings_export_preview_title
import pocketclaw.composeapp.generated.resources.settings_free_fallback
import pocketclaw.composeapp.generated.resources.settings_free_tier_description
import pocketclaw.composeapp.generated.resources.settings_free_tier_title
import pocketclaw.composeapp.generated.resources.settings_heartbeat_recent
import pocketclaw.composeapp.generated.resources.settings_import
import pocketclaw.composeapp.generated.resources.settings_import_error
import pocketclaw.composeapp.generated.resources.settings_import_partial
import pocketclaw.composeapp.generated.resources.settings_import_preview_title
import pocketclaw.composeapp.generated.resources.settings_import_replace_all
import pocketclaw.composeapp.generated.resources.settings_import_replace_all_description
import pocketclaw.composeapp.generated.resources.settings_import_section_conversations
import pocketclaw.composeapp.generated.resources.settings_import_section_email
import pocketclaw.composeapp.generated.resources.settings_import_section_heartbeat
import pocketclaw.composeapp.generated.resources.settings_import_section_mcp
import pocketclaw.composeapp.generated.resources.settings_import_section_memory
import pocketclaw.composeapp.generated.resources.settings_import_section_scheduling
import pocketclaw.composeapp.generated.resources.settings_import_section_services
import pocketclaw.composeapp.generated.resources.settings_import_section_soul
import pocketclaw.composeapp.generated.resources.settings_import_section_tools
import pocketclaw.composeapp.generated.resources.settings_import_success
import pocketclaw.composeapp.generated.resources.settings_mcp_cancel
import pocketclaw.composeapp.generated.resources.settings_memories
import pocketclaw.composeapp.generated.resources.settings_memories_all_title
import pocketclaw.composeapp.generated.resources.settings_memories_delete
import pocketclaw.composeapp.generated.resources.settings_memories_description
import pocketclaw.composeapp.generated.resources.settings_memories_edit_cancel
import pocketclaw.composeapp.generated.resources.settings_memories_edit_save
import pocketclaw.composeapp.generated.resources.settings_memories_edit_title
import pocketclaw.composeapp.generated.resources.settings_memories_show_all
import pocketclaw.composeapp.generated.resources.settings_open_github_issue
import pocketclaw.composeapp.generated.resources.settings_openai_compatible_or_other_service
import pocketclaw.composeapp.generated.resources.settings_openai_compatible_providers
import pocketclaw.composeapp.generated.resources.settings_openai_compatible_setup_ollama
import pocketclaw.composeapp.generated.resources.settings_remove_service
import pocketclaw.composeapp.generated.resources.settings_reorder_content_description
import pocketclaw.composeapp.generated.resources.settings_request_integration_description
import pocketclaw.composeapp.generated.resources.settings_request_integration_title
import pocketclaw.composeapp.generated.resources.settings_sandbox_cancel
import pocketclaw.composeapp.generated.resources.settings_sandbox_description
import pocketclaw.composeapp.generated.resources.settings_sandbox_disk_usage
import pocketclaw.composeapp.generated.resources.settings_sandbox_install
import pocketclaw.composeapp.generated.resources.settings_sandbox_install_packages
import pocketclaw.composeapp.generated.resources.settings_sandbox_subtab_files
import pocketclaw.composeapp.generated.resources.settings_sandbox_subtab_packages
import pocketclaw.composeapp.generated.resources.settings_sandbox_subtab_terminal
import pocketclaw.composeapp.generated.resources.settings_sandbox_uninstall
import pocketclaw.composeapp.generated.resources.settings_sandbox_uninstall_confirm
import pocketclaw.composeapp.generated.resources.settings_scheduled_tasks
import pocketclaw.composeapp.generated.resources.settings_scheduled_tasks_cancel
import pocketclaw.composeapp.generated.resources.settings_scheduled_tasks_description
import pocketclaw.composeapp.generated.resources.settings_sign_in_copy_api_key_from
import pocketclaw.composeapp.generated.resources.settings_sms
import pocketclaw.composeapp.generated.resources.settings_soul
import pocketclaw.composeapp.generated.resources.settings_soul_description
import pocketclaw.composeapp.generated.resources.settings_soul_reset
import pocketclaw.composeapp.generated.resources.settings_soul_reset_cancel
import pocketclaw.composeapp.generated.resources.settings_soul_reset_confirm
import pocketclaw.composeapp.generated.resources.settings_soul_save
import pocketclaw.composeapp.generated.resources.settings_sponsors_monthly
import pocketclaw.composeapp.generated.resources.settings_sponsors_past
import pocketclaw.composeapp.generated.resources.settings_status_checking
import pocketclaw.composeapp.generated.resources.settings_status_connected
import pocketclaw.composeapp.generated.resources.settings_status_error
import pocketclaw.composeapp.generated.resources.settings_status_error_connection_failed
import pocketclaw.composeapp.generated.resources.settings_status_error_invalid_key
import pocketclaw.composeapp.generated.resources.settings_status_error_quota_exhausted
import pocketclaw.composeapp.generated.resources.settings_status_error_rate_limited
import pocketclaw.composeapp.generated.resources.settings_tab_agent
import pocketclaw.composeapp.generated.resources.settings_tab_general
import pocketclaw.composeapp.generated.resources.settings_tab_integrations
import pocketclaw.composeapp.generated.resources.settings_tab_sandbox
import pocketclaw.composeapp.generated.resources.settings_tab_services
import pocketclaw.composeapp.generated.resources.settings_tab_tools
import pocketclaw.composeapp.generated.resources.settings_task_details_consecutive_failures
import pocketclaw.composeapp.generated.resources.settings_task_details_created
import pocketclaw.composeapp.generated.resources.settings_task_details_last_result
import pocketclaw.composeapp.generated.resources.settings_task_details_next_run
import pocketclaw.composeapp.generated.resources.settings_task_details_no_heartbeat_runs
import pocketclaw.composeapp.generated.resources.settings_task_details_no_runs
import pocketclaw.composeapp.generated.resources.settings_task_details_on_every_heartbeat
import pocketclaw.composeapp.generated.resources.settings_task_details_schedule
import pocketclaw.composeapp.generated.resources.settings_task_details_scheduled_for
import pocketclaw.composeapp.generated.resources.settings_task_details_status
import pocketclaw.composeapp.generated.resources.settings_task_details_trigger
import pocketclaw.composeapp.generated.resources.settings_theme
import pocketclaw.composeapp.generated.resources.settings_theme_dark
import pocketclaw.composeapp.generated.resources.settings_theme_description
import pocketclaw.composeapp.generated.resources.settings_theme_light
import pocketclaw.composeapp.generated.resources.settings_theme_oled
import pocketclaw.composeapp.generated.resources.settings_theme_system
import pocketclaw.composeapp.generated.resources.settings_tools_description
import pocketclaw.composeapp.generated.resources.settings_tools_none_available
import pocketclaw.composeapp.generated.resources.settings_ui_scale
import pocketclaw.composeapp.generated.resources.settings_version
import pocketclaw.composeapp.generated.resources.snackbar_email_removed
import pocketclaw.composeapp.generated.resources.snackbar_github_removed
import pocketclaw.composeapp.generated.resources.snackbar_mcp_server_removed
import pocketclaw.composeapp.generated.resources.snackbar_memory_deleted
import pocketclaw.composeapp.generated.resources.snackbar_service_removed
import pocketclaw.composeapp.generated.resources.snackbar_task_cancelled
import pocketclaw.composeapp.generated.resources.snackbar_undo
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.launch
import kotlinx.datetime.TimeZone
import kotlinx.datetime.offsetAt
import kotlinx.datetime.toLocalDateTime
import kotlinx.serialization.json.jsonObject
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.resources.vectorResource
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel
import sh.calvin.reorderable.ReorderableColumn
import kotlin.math.roundToInt
import kotlin.time.Instant

internal val StatusColorConnected = Color(0xFF4CAF50)
internal val StatusColorChecking = Color(0xFFFF9800)
internal val StatusColorError = Color(0xFFF44336)
internal val StatusColorUnknown = Color(0xFF9E9E9E)

@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel = koinViewModel(),
    sandboxViewModel: SandboxViewModel = koinViewModel(),
    onNavigateBack: () -> Unit,
    navigationTabBar: (@Composable () -> Unit)? = null,
) {
    val uiState by viewModel.state.collectAsStateWithLifecycle()
    val sandboxState by sandboxViewModel.state.collectAsStateWithLifecycle()

    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    androidx.compose.runtime.DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                viewModel.onScreenVisible()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    SettingsScreenContent(
        uiState = uiState,
        actions = viewModel.actions,
        sandboxState = sandboxState,
        onToggleSandbox = sandboxViewModel::onToggleSandbox,
        onSetupSandbox = sandboxViewModel::onSetupSandbox,
        onCancelSandbox = sandboxViewModel::onCancelSandbox,
        onResetSandbox = sandboxViewModel::onResetSandbox,
        onInstallPackages = sandboxViewModel::onInstallPackages,
        onNavigateBack = onNavigateBack,
        navigationTabBar = navigationTabBar,
    )
}

@Composable
fun SettingsScreenContent(
    uiState: SettingsUiState,
    actions: SettingsActions = SettingsActions.NoOp,
    sandboxState: SandboxUiState = SandboxUiState(),
    onToggleSandbox: (Boolean) -> Unit = {},
    onSetupSandbox: () -> Unit = {},
    onCancelSandbox: () -> Unit = {},
    onResetSandbox: () -> Unit = {},
    onInstallPackages: () -> Unit = {},
    onNavigateBack: () -> Unit = {},
    navigationTabBar: (@Composable () -> Unit)? = null,
) {
    val snackbarHostState = remember { SnackbarHostState() }
    val undoLabel = stringResource(Res.string.snackbar_undo)
    val memoryDeletedMsg = stringResource(Res.string.snackbar_memory_deleted)
    val taskCancelledMsg = stringResource(Res.string.snackbar_task_cancelled)
    val emailRemovedMsg = stringResource(Res.string.snackbar_email_removed)
    val githubRemovedMsg = stringResource(Res.string.snackbar_github_removed)
    val serviceRemovedMsg = stringResource(Res.string.snackbar_service_removed)
    val mcpServerRemovedMsg = stringResource(Res.string.snackbar_mcp_server_removed)

    LaunchedEffect(uiState.pendingDeletion) {
        val deletion = uiState.pendingDeletion ?: return@LaunchedEffect
        snackbarHostState.currentSnackbarData?.dismiss()
        val message = when (deletion) {
            is PendingDeletion.Memory -> memoryDeletedMsg
            is PendingDeletion.Task -> taskCancelledMsg
            is PendingDeletion.EmailAccount -> emailRemovedMsg
            is PendingDeletion.GithubAccount -> githubRemovedMsg
            is PendingDeletion.Service -> serviceRemovedMsg
            is PendingDeletion.McpServer -> mcpServerRemovedMsg
        }
        val result = snackbarHostState.showSnackbar(
            message = message,
            actionLabel = undoLabel,
            duration = SnackbarDuration.Short,
        )
        if (result == SnackbarResult.ActionPerformed) {
            actions.onUndoDelete()
        }
    }

    val pendingDeletion = uiState.pendingDeletion
    val filteredMemories = remember(uiState.memories, pendingDeletion) {
        if (pendingDeletion is PendingDeletion.Memory) uiState.memories.filter { it.key != pendingDeletion.key }.toImmutableList() else uiState.memories
    }
    val filteredTasks = remember(uiState.scheduledTasks, pendingDeletion) {
        if (pendingDeletion is PendingDeletion.Task) uiState.scheduledTasks.filter { it.id != pendingDeletion.id }.toImmutableList() else uiState.scheduledTasks
    }
    val filteredEmailAccounts = remember(uiState.emailAccounts, pendingDeletion) {
        if (pendingDeletion is PendingDeletion.EmailAccount) uiState.emailAccounts.filter { it.id != pendingDeletion.id }.toImmutableList() else uiState.emailAccounts
    }
    val filteredGithubAccounts = remember(uiState.githubAccounts, pendingDeletion) {
        if (pendingDeletion is PendingDeletion.GithubAccount) uiState.githubAccounts.filter { it.id != pendingDeletion.id }.toImmutableList() else uiState.githubAccounts
    }
    val filteredServices = remember(uiState.configuredServices, pendingDeletion) {
        if (pendingDeletion is PendingDeletion.Service) uiState.configuredServices.filter { it.instanceId != pendingDeletion.instanceId }.toImmutableList() else uiState.configuredServices
    }
    val filteredMcpServers = remember(uiState.mcpServers, pendingDeletion) {
        if (pendingDeletion is PendingDeletion.McpServer) uiState.mcpServers.filter { it.id != pendingDeletion.serverId }.toImmutableList() else uiState.mcpServers
    }

    val filteredUiState = remember(uiState, filteredMemories, filteredTasks, filteredEmailAccounts, filteredGithubAccounts, filteredServices, filteredMcpServers) {
        uiState.copy(
            memories = filteredMemories,
            scheduledTasks = filteredTasks,
            emailAccounts = filteredEmailAccounts,
            githubAccounts = filteredGithubAccounts,
            configuredServices = filteredServices,
            mcpServers = filteredMcpServers,
        )
    }

    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).navigationBarsPadding().statusBarsPadding().imePadding()) {
        Column(Modifier.fillMaxSize(), horizontalAlignment = CenterHorizontally) {
            if (navigationTabBar != null) {
                Row(
                    modifier = Modifier.fillMaxWidth().defaultMinSize(minHeight = 64.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = CenterVertically,
                ) {
                    navigationTabBar()
                }
            } else {
                TopBar(onNavigateBack = onNavigateBack)
            }

            val visibleTabs = remember(sandboxState.showSandbox) {
                SettingsTab.entries.filter { it != SettingsTab.Sandbox || sandboxState.showSandbox }.toImmutableList()
            }

            SettingsTabSelector(
                tabs = visibleTabs,
                currentTab = filteredUiState.currentTab,
                onSelectTab = actions.onSelectTab,
            )

            val settingsScrollState = rememberScrollState()
            Box(Modifier.weight(1f).fillMaxWidth()) {
                Column(
                    Modifier.fillMaxWidth().verticalScroll(settingsScrollState),
                    horizontalAlignment = CenterHorizontally,
                ) {
                    Spacer(Modifier.height(16.dp))

                    val maxContentWidth = when (filteredUiState.currentTab) {
                        SettingsTab.Services -> 500.dp
                        else -> 900.dp
                    }
                    Column(
                        Modifier.widthIn(max = maxContentWidth).fillMaxWidth().padding(horizontal = 16.dp),
                        horizontalAlignment = CenterHorizontally,
                    ) {
                        when (filteredUiState.currentTab) {
                            SettingsTab.General -> {
                                GeneralContent(uiState = filteredUiState, actions = actions)
                            }

                            SettingsTab.Agent -> {
                                AgentContent(uiState = filteredUiState, actions = actions)
                            }

                            SettingsTab.Services -> {
                                ServicesContent(uiState = filteredUiState, actions = actions)
                            }

                            SettingsTab.Integrations -> {
                                IntegrationsContent()
                            }

                            SettingsTab.Tools -> {
                                ToolsContent(
                                    tools = filteredUiState.tools,
                                    onToggleTool = actions.onToggleTool,
                                    mcpServers = filteredUiState.mcpServers,
                                    onAddMcpServer = actions.onAddMcpServer,
                                    onRemoveMcpServer = actions.onRemoveMcpServer,
                                    onToggleMcpServer = actions.onToggleMcpServer,
                                    onRefreshMcpServer = actions.onRefreshMcpServer,
                                    showAddMcpServerDialog = filteredUiState.showAddMcpServerDialog,
                                    onShowAddMcpServerDialog = actions.onShowAddMcpServerDialog,
                                    onAddPopularMcpServer = actions.onAddPopularMcpServer,
                                )
                            }

                            SettingsTab.Sandbox -> {
                                SandboxSettingsCard(
                                    sandboxState = sandboxState,
                                    onToggleSandbox = onToggleSandbox,
                                    onSetupSandbox = onSetupSandbox,
                                    onCancelSandbox = onCancelSandbox,
                                    onResetSandbox = onResetSandbox,
                                    onInstallPackages = onInstallPackages,
                                )
                            }
                        }

                        Spacer(Modifier.height(16.dp))
                    }

                    Spacer(Modifier.weight(1f))

                    BottomInfo()
                }
                VerticalScrollbarForScroll(
                    scrollState = settingsScrollState,
                    modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight(),
                )
            }
        }
        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 16.dp),
        ) { data ->
            Snackbar(snackbarData = data)
        }
    }
}

@Composable
private fun TopBar(onNavigateBack: () -> Unit) {
    Row {
        IconButton(
            modifier = Modifier.handCursor(),
            onClick = onNavigateBack,
        ) {
            Icon(
                imageVector = BackIcon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onBackground,
            )
        }
        Spacer(Modifier.weight(1f))
    }
}

@Composable
private fun SettingsTabSelector(
    tabs: ImmutableList<SettingsTab>,
    currentTab: SettingsTab,
    onSelectTab: (SettingsTab) -> Unit,
) {
    Surface(
        modifier = Modifier.widthIn(max = 900.dp).fillMaxWidth().padding(vertical = 8.dp),
        color = Color.Transparent,
    ) {
        Row(
            modifier = Modifier
                .padding(4.dp)
                .horizontalScroll(rememberScrollState()),
        ) {
            tabs.forEach { tab ->
                val isSelected = currentTab == tab
                Surface(
                    modifier = Modifier
                        .handCursor()
                        .clip(RoundedCornerShape(50))
                        .clickable { onSelectTab(tab) },
                    shape = RoundedCornerShape(50),
                    color = if (isSelected) {
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
                    } else {
                        Color.Transparent
                    },
                ) {
                    Text(
                        text = when (tab) {
                            SettingsTab.General -> stringResource(Res.string.settings_tab_general)
                            SettingsTab.Agent -> stringResource(Res.string.settings_tab_agent)
                            SettingsTab.Services -> stringResource(Res.string.settings_tab_services)
                            SettingsTab.Tools -> stringResource(Res.string.settings_tab_tools)
                            SettingsTab.Sandbox -> stringResource(Res.string.settings_tab_sandbox)
                            SettingsTab.Integrations -> stringResource(Res.string.settings_tab_integrations)
                        },
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        color = MaterialTheme.colorScheme.primary,
                        style = MaterialTheme.typography.labelLarge,
                        maxLines = 1,
                    )
                }
            }
        }
    }
}

@Composable
private fun BottomInfo() {
    Text(
        text = stringResource(Res.string.settings_ai_mistakes_warning),
        style = MaterialTheme.typography.bodySmall,
        textAlign = TextAlign.Center,
        color = MaterialTheme.colorScheme.onBackground,
    )

    Spacer(Modifier.height(8.dp))

    val uriHandler = LocalUriHandler.current

    Row(
        modifier = Modifier.padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            stringResource(Res.string.settings_version, Version.APP_VERSION),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onBackground,
        )

        Spacer(Modifier.width(8.dp))

        Icon(
            modifier = Modifier
                .clip(CircleShape)
                .size(24.dp)
                .clickable(onClick = {
                    uriHandler.openUri("https://github.com/Deadendev/PocketClaw")
                })
                .handCursor(),
            painter = painterResource(Res.drawable.github_mark),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onBackground,
        )

        Spacer(Modifier.width(12.dp))

        Text(
            text = stringResource(Res.string.settings_documentation),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier
                .clickable { uriHandler.openUri("https://kai9000.com/docs/") }
                .handCursor(),
        )
    }

    Spacer(Modifier.height(8.dp))
}

@Composable
internal fun SettingsCard(
    modifier: Modifier = Modifier,
    innerPadding: Boolean = true,
    onClick: (() -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    Card(
        modifier = modifier,
        colors = pocketclawAdaptiveCardColors(),
        border = pocketclawAdaptiveCardBorder(),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .then(if (onClick != null) Modifier.clickable(onClick = onClick).handCursor() else Modifier)
                .then(if (innerPadding) Modifier.padding(16.dp) else Modifier),
        ) {
            content()
        }
    }
}

@Composable
internal fun ToggleableHeadline(
    title: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    actions: @Composable RowScope.() -> Unit = {},
) {
    val switchInteractionSource = remember { MutableInteractionSource() }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(
                interactionSource = switchInteractionSource,
                indication = null,
            ) { onCheckedChange(!checked) }
            .handCursor(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.weight(1f),
        )
        actions()
        Switch(
            checked = checked,
            onCheckedChange = null,
            interactionSource = switchInteractionSource,
        )
    }
    Spacer(Modifier.size(4.dp))
    Text(
        text = description,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}
