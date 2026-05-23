package com.inspiredandroid.pocketclaw.github

import com.inspiredandroid.pocketclaw.data.GithubAccount
import com.inspiredandroid.pocketclaw.data.GithubNotification
import com.inspiredandroid.pocketclaw.data.GithubStore
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

/**
 * Polls a GitHub account's `/notifications` endpoint and writes new entries into the
 * [GithubStore] pending queue. Mirrors [com.inspiredandroid.pocketclaw.email.EmailPoller]
 * — same `lastSeenAt` watermark + dedup-against-pending pattern. Failures are
 * recorded in the per-account sync state so the settings UI can surface them.
 */
@OptIn(ExperimentalTime::class)
class GithubPoller(
    private val githubStore: GithubStore,
    private val clientFactory: (apiBaseUrl: String, token: String) -> GithubClient = ::GithubClient,
) {
    suspend fun poll(account: GithubAccount) {
        val syncState = githubStore.getSyncState(account.id)
        val attemptAt = Clock.System.now().toEpochMilliseconds()
        val client = clientFactory(account.apiBaseUrl, githubStore.getToken(account.id))
        try {
            val raw = client.listNotifications(since = syncState.lastSeenAt.ifBlank { null })
            // The /notifications endpoint already filters by `since`; we still drop
            // anything <= our watermark and anything already queued so a flapping
            // updated_at can't double-deliver. Newest-last for stable consumption.
            val pendingKeysForAccount = githubStore.getPending()
                .asSequence()
                .filter { it.accountId == account.id }
                .map { it.threadId }
                .toSet()
            val fresh = raw
                .asSequence()
                .filter { it.id !in pendingKeysForAccount }
                .filter { syncState.lastSeenAt.isBlank() || it.updatedAt > syncState.lastSeenAt }
                .toList()
                .takeLast(MAX_PER_POLL)

            if (fresh.isNotEmpty()) {
                githubStore.addPending(
                    fresh.map { dto ->
                        GithubNotification(
                            threadId = dto.id,
                            accountId = account.id,
                            repo = dto.repository.fullName,
                            type = dto.subject.type,
                            subjectTitle = dto.subject.title,
                            reason = dto.reason,
                            updatedAt = dto.updatedAt,
                            subjectUrl = dto.subject.url,
                            latestCommentUrl = dto.subject.latestCommentUrl.orEmpty(),
                            unread = dto.unread,
                        )
                    },
                )
            }

            // Watermark advances to the newest updatedAt we observed (whether queued or
            // already-delivered) so we don't keep reprocessing the same window.
            val newestUpdate = raw.maxByOrNull { it.updatedAt }?.updatedAt ?: syncState.lastSeenAt
            githubStore.updateSyncState(
                syncState.copy(
                    lastSeenAt = newestUpdate,
                    lastSyncEpochMs = attemptAt,
                    lastAttemptEpochMs = attemptAt,
                    unreadCount = raw.count { it.unread },
                    lastError = null,
                ),
            )
        } catch (e: Exception) {
            githubStore.updateSyncState(
                syncState.copy(
                    lastAttemptEpochMs = attemptAt,
                    lastError = e.message ?: e::class.simpleName ?: "Poll failed",
                ),
            )
        } finally {
            client.close()
        }
    }

    companion object {
        const val MAX_PER_POLL = 30
    }
}
