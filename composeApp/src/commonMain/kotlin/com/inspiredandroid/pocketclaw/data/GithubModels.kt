package com.inspiredandroid.pocketclaw.data

import androidx.compose.runtime.Immutable
import kotlinx.serialization.Serializable

/**
 * A connected GitHub account. The personal access token is stored separately (see
 * [GithubStore.getToken] / `appSettings.getGithubToken`) so the account JSON can be
 * persisted, exported, and rendered in the UI without leaking credentials.
 */
@Immutable
@Serializable
data class GithubAccount(
    val id: String,
    val login: String,
    /** API base URL — `https://api.github.com` for github.com, otherwise `https://<host>/api/v3` for GHES. */
    val apiBaseUrl: String = "https://api.github.com",
    /** Display URL of the GitHub instance, e.g. `https://github.com` — used for opening links. */
    val webBaseUrl: String = "https://github.com",
    val scopes: String = "",
)

/**
 * One notification surfaced via the `/notifications` endpoint. Mirrors the shape of
 * [EmailMessage] for the heartbeat pending-queue: `(accountId, threadId)` is the dedup
 * key, `subjectTitle` is what shows in the prompt, `url` is the API URL the AI can
 * fetch to read the underlying issue / PR.
 */
@Serializable
data class GithubNotification(
    val threadId: String,
    val accountId: String,
    val repo: String,
    /** "Issue", "PullRequest", "Commit", "Release", etc. */
    val type: String,
    val subjectTitle: String,
    /** "assign", "mention", "review_requested", "comment", … */
    val reason: String,
    val updatedAt: String = "",
    /** API URL of the underlying issue/PR — usable by the AI to fetch full details. */
    val subjectUrl: String = "",
    /** Optional last-comment URL, present when GitHub knows the latest comment. */
    val latestCommentUrl: String = "",
    val unread: Boolean = true,
)

/** Per-account sync watermark + last-error, mirrors [EmailSyncState]. */
@Serializable
data class GithubSyncState(
    val accountId: String,
    /** ISO-8601 timestamp of the latest notification we've already surfaced. */
    val lastSeenAt: String = "",
    val lastSyncEpochMs: Long = 0L,
    val unreadCount: Int = 0,
    val lastAttemptEpochMs: Long = 0L,
    val lastError: String? = null,
)
