package com.inspiredandroid.pocketclaw

import com.inspiredandroid.pocketclaw.data.AppSettings
import com.inspiredandroid.pocketclaw.data.ConversationStorage
import com.inspiredandroid.pocketclaw.data.DataRepository
import com.inspiredandroid.pocketclaw.data.EmailStore
import com.inspiredandroid.pocketclaw.data.GithubStore
import com.inspiredandroid.pocketclaw.data.HeartbeatManager
import com.inspiredandroid.pocketclaw.data.MemoryStore
import com.inspiredandroid.pocketclaw.data.NotificationStore
import com.inspiredandroid.pocketclaw.data.RemoteDataRepository
import com.inspiredandroid.pocketclaw.data.SmsDraftStore
import com.inspiredandroid.pocketclaw.data.SmsStore
import com.inspiredandroid.pocketclaw.data.TaskScheduler
import com.inspiredandroid.pocketclaw.data.TaskStore
import com.inspiredandroid.pocketclaw.data.ToolExecutor
import com.inspiredandroid.pocketclaw.data.runMigrations
import com.inspiredandroid.pocketclaw.email.EmailPoller
import com.inspiredandroid.pocketclaw.github.GithubPoller
import com.inspiredandroid.pocketclaw.inference.createLocalInferenceEngine
import com.inspiredandroid.pocketclaw.mcp.McpServerManager
import com.inspiredandroid.pocketclaw.network.Requests
import com.inspiredandroid.pocketclaw.notifications.NotificationReader
import com.inspiredandroid.pocketclaw.sms.SmsPoller
import com.inspiredandroid.pocketclaw.sms.SmsReader
import com.inspiredandroid.pocketclaw.sms.SmsSender
import com.inspiredandroid.pocketclaw.splinterlands.SplinterlandsApi
import com.inspiredandroid.pocketclaw.splinterlands.SplinterlandsBattleRunner
import com.inspiredandroid.pocketclaw.splinterlands.SplinterlandsStore
import com.inspiredandroid.pocketclaw.tools.CalendarPermissionController
import com.inspiredandroid.pocketclaw.tools.NotificationListenerController
import com.inspiredandroid.pocketclaw.tools.NotificationPermissionController
import com.inspiredandroid.pocketclaw.tools.SmsPermissionController
import com.inspiredandroid.pocketclaw.tools.SmsSendPermissionController
import com.inspiredandroid.pocketclaw.ui.chat.ChatViewModel
import com.inspiredandroid.pocketclaw.ui.sandbox.SandboxFileBrowserViewModel
import com.inspiredandroid.pocketclaw.ui.sandbox.SandboxPackagesViewModel
import com.inspiredandroid.pocketclaw.ui.sandbox.SandboxSessionViewModel
import com.inspiredandroid.pocketclaw.ui.settings.SandboxViewModel
import com.inspiredandroid.pocketclaw.ui.settings.SettingsViewModel
import com.inspiredandroid.pocketclaw.ui.settings.SplinterlandsViewModel
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

val appModule = module {
    single<CalendarPermissionController> { CalendarPermissionController() }
    single<NotificationPermissionController> { NotificationPermissionController() }
    single<SmsPermissionController> { SmsPermissionController() }
    single<SmsSendPermissionController> { SmsSendPermissionController() }
    single<SmsReader> { SmsReader() }
    single<SmsSender> { SmsSender() }
    single<NotificationListenerController> { NotificationListenerController() }
    single<NotificationReader> { NotificationReader() }
    single<AppSettings> {
        AppSettings(createSecureSettings()).also {
            it.runMigrations(createLegacySettings())
        }
    }
    single<Requests> {
        Requests()
    }
    single<ConversationStorage> {
        ConversationStorage(get())
    }
    single<ToolExecutor> {
        ToolExecutor()
    }
    single<MemoryStore> {
        MemoryStore(get())
    }
    single<TaskStore> {
        TaskStore(get())
    }
    single<EmailStore> {
        EmailStore(get())
    }
    single<EmailPoller> {
        EmailPoller(get<EmailStore>())
    }
    single<GithubStore> {
        GithubStore(get())
    }
    single<GithubPoller> {
        GithubPoller(get<GithubStore>())
    }
    single<SmsStore> {
        SmsStore(get())
    }
    single<SmsPoller> {
        SmsPoller(get<SmsStore>(), get<SmsReader>())
    }
    single<SmsDraftStore> {
        SmsDraftStore(get())
    }
    single<NotificationStore> {
        NotificationStore(get())
    }
    single<SplinterlandsStore> {
        SplinterlandsStore(get())
    }
    single<SplinterlandsApi> {
        SplinterlandsApi()
    }
    single<HeartbeatManager> {
        HeartbeatManager(get(), get(), get(), get(), get<GithubStore>())
    }
    single<McpServerManager> {
        McpServerManager(get())
    }
    single<RemoteDataRepository> {
        RemoteDataRepository(
            requests = get(),
            appSettings = get(),
            conversationStorage = get(),
            toolExecutor = get(),
            memoryStore = get(),
            taskStore = get(),
            heartbeatManager = get(),
            emailStore = get(),
            emailPoller = get(),
            githubStore = get(),
            githubPoller = get(),
            smsStore = get(),
            smsPoller = get(),
            smsReader = get(),
            smsPermissionController = get(),
            smsSendPermissionController = get(),
            smsSender = get(),
            smsDraftStore = get(),
            notificationStore = get(),
            notificationListenerController = get(),
            mcpServerManager = get(),
            sandboxController = get(),
            localInferenceEngine = createLocalInferenceEngine(),
        )
    }
    single<DataRepository> { get<RemoteDataRepository>() }
    single<SplinterlandsBattleRunner> {
        SplinterlandsBattleRunner(get(), get(), get<DataRepository>(), get<DaemonController>())
    }
    single<TaskScheduler> {
        TaskScheduler(
            get<DataRepository>(),
            get(),
            get(),
            get(),
            get(),
            get<EmailPoller>(),
            get<SmsStore>(),
            get<SmsPoller>(),
            get<NotificationStore>(),
            get<GithubStore>(),
            get<GithubPoller>(),
        )
    }
    single<DaemonController> { createDaemonController() }
    single<SandboxController> { createSandboxController() }
    viewModel { SettingsViewModel(get<DataRepository>(), get<DaemonController>(), get<NotificationPermissionController>(), get<TaskScheduler>()) }
    viewModel { SandboxViewModel(get<DataRepository>(), get<SandboxController>()) }
    viewModel { SandboxFileBrowserViewModel(get<SandboxController>()) }
    viewModel { SandboxPackagesViewModel(get<SandboxController>()) }
    viewModel { SandboxSessionViewModel(get<SandboxController>(), get<DataRepository>()) }
    viewModel { SplinterlandsViewModel(get<DataRepository>(), get(), get(), get<SplinterlandsApi>()) }
    viewModel { ChatViewModel(get<DataRepository>(), get<TaskScheduler>()) }
}
