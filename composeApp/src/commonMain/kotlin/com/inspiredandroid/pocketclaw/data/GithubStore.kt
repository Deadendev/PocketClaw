package com.inspiredandroid.pocketclaw.data

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.serializer

/**
 * Persistence layer for GitHub accounts, tokens, sync state, and the pending
 * notification queue. Mirrors [EmailStore] exactly — same locking discipline, same
 * keyed-PendingQueue pattern, same separate password ('token' here) storage.
 */
class GithubStore(private val appSettings: AppSettings) {

    private val json = SharedJson
    private val mutex = Mutex()
    private val pendingQueue = PendingQueue<GithubNotification, Pair<String, String>>(
        readJson = appSettings::getGithubPendingJson,
        writeJson = appSettings::setGithubPendingJson,
        serializer = ListSerializer(serializer<GithubNotification>()),
        keyOf = { it.accountId to it.threadId },
    )

    fun getAccounts(): List<GithubAccount> {
        val raw = appSettings.getGithubAccountsJson()
        if (raw.isEmpty()) return emptyList()
        return try {
            json.decodeFromString<List<GithubAccount>>(raw)
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun getAccount(id: String): GithubAccount? = getAccounts().find { it.id == id }

    suspend fun addAccount(account: GithubAccount): GithubAccount = mutex.withLock {
        val accounts = getAccounts().toMutableList()
        accounts.removeAll { it.id == account.id }
        accounts.add(account)
        appSettings.setGithubAccountsJson(json.encodeToString(accounts))
        account
    }

    suspend fun removeAccount(id: String): Boolean = mutex.withLock {
        val accounts = getAccounts().toMutableList()
        val removed = accounts.removeAll { it.id == id }
        if (removed) {
            appSettings.setGithubAccountsJson(json.encodeToString(accounts))
            appSettings.removeGithubToken(id)
            removeSyncState(id)
        }
        removed
    }

    fun getToken(accountId: String): String = appSettings.getGithubToken(accountId)

    suspend fun setToken(accountId: String, token: String) {
        appSettings.setGithubToken(accountId, token)
    }

    fun getSyncState(accountId: String): GithubSyncState {
        val raw = appSettings.getGithubSyncStateJson(accountId)
        if (raw.isEmpty()) return GithubSyncState(accountId = accountId)
        return try {
            json.decodeFromString<GithubSyncState>(raw)
        } catch (_: Exception) {
            GithubSyncState(accountId = accountId)
        }
    }

    suspend fun updateSyncState(state: GithubSyncState) = mutex.withLock {
        appSettings.setGithubSyncStateJson(state.accountId, json.encodeToString(state))
    }

    private fun removeSyncState(accountId: String) {
        appSettings.setGithubSyncStateJson(accountId, "")
    }

    fun getAllSyncStates(): Map<String, GithubSyncState> = getAccounts().associate { it.id to getSyncState(it.id) }

    fun getPending(): List<GithubNotification> = pendingQueue.get()

    suspend fun addPending(notifications: List<GithubNotification>) = pendingQueue.add(notifications)

    suspend fun removePending(notifications: List<GithubNotification>) = pendingQueue.remove(notifications)
}
