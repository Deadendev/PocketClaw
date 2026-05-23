package com.inspiredandroid.pocketclaw.github

import com.inspiredandroid.pocketclaw.httpClient
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.request.patch
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import io.ktor.serialization.kotlinx.json.json
import io.ktor.util.encodeBase64
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject

/**
 * REST client for the GitHub v3 API. One instance per account so each owns its own
 * `(apiBaseUrl, token)` pair. Only the endpoints the AI tools call are exposed —
 * everything else (releases, gists, actions, …) is omitted on purpose to keep the
 * surface tight.
 *
 * Errors propagate as [GithubApiException] subtypes so callers can pattern-match on
 * permission/rate-limit/not-found cases without parsing JSON.
 */
class GithubClient(
    private val apiBaseUrl: String,
    private val token: String,
) {
    private val client: HttpClient = httpClient {
        install(ContentNegotiation) {
            json(jsonCodec)
        }
        install(HttpTimeout) {
            requestTimeoutMillis = 30_000
            connectTimeoutMillis = 10_000
        }
    }

    private suspend fun authed(block: suspend (token: String, baseUrl: String) -> HttpResponse): HttpResponse {
        return block(token, apiBaseUrl.trimEnd('/'))
    }

    private fun mapError(status: HttpStatusCode, body: String): GithubApiException {
        val message = parseErrorMessage(body) ?: status.description
        return when (status.value) {
            401, 403 -> if (status.value == 403 && body.contains("rate limit", ignoreCase = true)) {
                GithubRateLimitException(message)
            } else {
                GithubAuthException(message)
            }
            404 -> GithubNotFoundException(message)
            422 -> GithubValidationException(message)
            else -> GithubGenericException("${status.value} $message")
        }
    }

    private fun parseErrorMessage(body: String): String? = try {
        jsonCodec.parseToJsonElement(body).let { el ->
            val obj = el as? JsonObject ?: return@let null
            val msg = (obj["message"] as? JsonPrimitive)?.content
            val errors = (obj["errors"] as? kotlinx.serialization.json.JsonArray)
                ?.joinToString { (it as? JsonObject)?.get("message")?.toString() ?: it.toString() }
            listOfNotNull(msg, errors?.takeIf { it.isNotBlank() }).joinToString(": ").ifBlank { null }
        }
    } catch (_: Exception) {
        null
    }

    /**
     * Fetch the authenticated user — used by `setup_github` to validate the token.
     *
     * Tries `Authorization: Bearer <token>` first (current GitHub recommendation) and, if that
     * comes back 401, retries once with the legacy `Authorization: token <token>` header.
     * Both forms work against api.github.com, but a few older GitHub Enterprise Server installs
     * only accept the legacy form. The fallback turns "this works in curl but the app says
     * Bad credentials" into "the app just works".
     */
    suspend fun getAuthenticatedUser(): GithubUserDto {
        val bearer = authed { tk, base ->
            client.get("$base/user") {
                header("Authorization", "Bearer $tk")
                header("Accept", "application/vnd.github+json")
                header("X-GitHub-Api-Version", "2022-11-28")
            }
        }
        if (bearer.status.isSuccess()) return bearer.body()
        if (bearer.status.value != 401) throw mapError(bearer.status, bearer.bodyAsText())

        // 401 with Bearer — retry with legacy `token` scheme before giving up.
        val legacy = authed { tk, base ->
            client.get("$base/user") {
                header("Authorization", "token $tk")
                header("Accept", "application/vnd.github+json")
                header("X-GitHub-Api-Version", "2022-11-28")
            }
        }
        return legacy.expect()
    }

    suspend fun listRepos(visibility: String? = null, perPage: Int = 30, page: Int = 1): List<GithubRepoDto> = authed { tk, base ->
        client.get("$base/user/repos") {
            header("Authorization", "Bearer $tk")
            header("Accept", "application/vnd.github+json")
            parameter("per_page", perPage)
            parameter("page", page)
            parameter("sort", "updated")
            if (visibility != null) parameter("visibility", visibility)
        }
    }.expect()

    suspend fun getRepo(owner: String, repo: String): GithubRepoDto = authed { tk, base ->
        client.get("$base/repos/$owner/$repo") {
            header("Authorization", "Bearer $tk")
            header("Accept", "application/vnd.github+json")
        }
    }.expect()

    /**
     * Fetch a file's content + sha at [ref] (branch, tag, or commit). Returns null
     * when the path resolves to a directory; the caller should switch to a directory
     * listing in that case.
     */
    suspend fun getFile(owner: String, repo: String, path: String, ref: String? = null): GithubContentDto? = authed { tk, base ->
        client.get("$base/repos/$owner/$repo/contents/$path") {
            header("Authorization", "Bearer $tk")
            header("Accept", "application/vnd.github+json")
            if (ref != null) parameter("ref", ref)
        }
    }.let { resp ->
        if (resp.status == HttpStatusCode.NotFound) return@let null
        if (!resp.status.isSuccess()) throw mapError(resp.status, resp.bodyAsText())
        // Could be a single file (object) or a directory (array). For ai/repo-edit
        // workflows we only care about files; signal a directory hit by returning null.
        val raw = resp.bodyAsText()
        if (raw.trimStart().startsWith("[")) null else jsonCodec.decodeFromString(GithubContentDto.serializer(), raw)
    }

    suspend fun listDirectory(owner: String, repo: String, path: String, ref: String? = null): List<GithubContentDto> = authed { tk, base ->
        client.get("$base/repos/$owner/$repo/contents/$path") {
            header("Authorization", "Bearer $tk")
            header("Accept", "application/vnd.github+json")
            if (ref != null) parameter("ref", ref)
        }
    }.let { resp ->
        if (!resp.status.isSuccess()) throw mapError(resp.status, resp.bodyAsText())
        val raw = resp.bodyAsText()
        if (raw.trimStart().startsWith("[")) {
            jsonCodec.decodeFromString(kotlinx.serialization.builtins.ListSerializer(GithubContentDto.serializer()), raw)
        } else {
            // Single file, not a directory — wrap so callers get a uniform list.
            listOf(jsonCodec.decodeFromString(GithubContentDto.serializer(), raw))
        }
    }

    /**
     * Create or update a file via the Contents API. Pass [sha] when updating; omit
     * for a fresh create. [contentText] is the raw file content (not pre-encoded).
     */
    suspend fun putFile(
        owner: String,
        repo: String,
        path: String,
        contentText: String,
        commitMessage: String,
        branch: String? = null,
        sha: String? = null,
    ): GithubFileWriteResult = authed { tk, base ->
        val body = buildJsonObject {
            put("message", JsonPrimitive(commitMessage))
            put("content", JsonPrimitive(contentText.encodeToByteArray().encodeBase64()))
            if (branch != null) put("branch", JsonPrimitive(branch))
            if (sha != null) put("sha", JsonPrimitive(sha))
        }
        client.put("$base/repos/$owner/$repo/contents/$path") {
            header("Authorization", "Bearer $tk")
            header("Accept", "application/vnd.github+json")
            contentType(ContentType.Application.Json)
            setBody(body)
        }
    }.expect()

    suspend fun deleteFile(
        owner: String,
        repo: String,
        path: String,
        sha: String,
        commitMessage: String,
        branch: String? = null,
    ): GithubFileWriteResult = authed { tk, base ->
        val body = buildJsonObject {
            put("message", JsonPrimitive(commitMessage))
            put("sha", JsonPrimitive(sha))
            if (branch != null) put("branch", JsonPrimitive(branch))
        }
        client.delete("$base/repos/$owner/$repo/contents/$path") {
            header("Authorization", "Bearer $tk")
            header("Accept", "application/vnd.github+json")
            contentType(ContentType.Application.Json)
            setBody(body)
        }
    }.expect()

    suspend fun listBranches(owner: String, repo: String, perPage: Int = 30): List<GithubBranchDto> = authed { tk, base ->
        client.get("$base/repos/$owner/$repo/branches") {
            header("Authorization", "Bearer $tk")
            header("Accept", "application/vnd.github+json")
            parameter("per_page", perPage)
        }
    }.expect()

    /** Resolve a branch / tag / commit ref to its head commit sha. */
    suspend fun getRefSha(owner: String, repo: String, ref: String): String = authed { tk, base ->
        client.get("$base/repos/$owner/$repo/git/ref/heads/$ref") {
            header("Authorization", "Bearer $tk")
            header("Accept", "application/vnd.github+json")
        }
    }.expect<GithubRefDto>().objectField.sha

    /** Create a branch [newBranch] pointing at the head of [fromBranch]. */
    suspend fun createBranch(owner: String, repo: String, newBranch: String, fromBranch: String): GithubRefDto = authed { tk, base ->
        val sha = getRefSha(owner, repo, fromBranch)
        val body = buildJsonObject {
            put("ref", JsonPrimitive("refs/heads/$newBranch"))
            put("sha", JsonPrimitive(sha))
        }
        client.post("$base/repos/$owner/$repo/git/refs") {
            header("Authorization", "Bearer $tk")
            header("Accept", "application/vnd.github+json")
            contentType(ContentType.Application.Json)
            setBody(body)
        }
    }.expect()

    suspend fun listPullRequests(
        owner: String,
        repo: String,
        state: String = "open",
        perPage: Int = 20,
    ): List<GithubPullRequestDto> = authed { tk, base ->
        client.get("$base/repos/$owner/$repo/pulls") {
            header("Authorization", "Bearer $tk")
            header("Accept", "application/vnd.github+json")
            parameter("state", state)
            parameter("per_page", perPage)
        }
    }.expect()

    suspend fun getPullRequest(owner: String, repo: String, number: Int): GithubPullRequestDto = authed { tk, base ->
        client.get("$base/repos/$owner/$repo/pulls/$number") {
            header("Authorization", "Bearer $tk")
            header("Accept", "application/vnd.github+json")
        }
    }.expect()

    suspend fun createPullRequest(
        owner: String,
        repo: String,
        title: String,
        head: String,
        base: String,
        body: String? = null,
        draft: Boolean = false,
    ): GithubPullRequestDto = authed { tk, baseUrl ->
        val payload = buildJsonObject {
            put("title", JsonPrimitive(title))
            put("head", JsonPrimitive(head))
            put("base", JsonPrimitive(base))
            if (body != null) put("body", JsonPrimitive(body))
            if (draft) put("draft", JsonPrimitive(true))
        }
        client.post("$baseUrl/repos/$owner/$repo/pulls") {
            header("Authorization", "Bearer $tk")
            header("Accept", "application/vnd.github+json")
            contentType(ContentType.Application.Json)
            setBody(payload)
        }
    }.expect()

    suspend fun listIssues(
        owner: String,
        repo: String,
        state: String = "open",
        perPage: Int = 20,
    ): List<GithubIssueDto> = authed { tk, base ->
        client.get("$base/repos/$owner/$repo/issues") {
            header("Authorization", "Bearer $tk")
            header("Accept", "application/vnd.github+json")
            parameter("state", state)
            parameter("per_page", perPage)
        }
    }.expect<List<GithubIssueDto>>()
        // /issues returns PRs too — drop them so AI sees real issues only.
        .filter { it.pullRequest == null }

    suspend fun getIssue(owner: String, repo: String, number: Int): GithubIssueDto = authed { tk, base ->
        client.get("$base/repos/$owner/$repo/issues/$number") {
            header("Authorization", "Bearer $tk")
            header("Accept", "application/vnd.github+json")
        }
    }.expect()

    suspend fun createIssue(
        owner: String,
        repo: String,
        title: String,
        body: String? = null,
        labels: List<String> = emptyList(),
    ): GithubIssueDto = authed { tk, base ->
        val payload = buildJsonObject {
            put("title", JsonPrimitive(title))
            if (body != null) put("body", JsonPrimitive(body))
            if (labels.isNotEmpty()) {
                put(
                    "labels",
                    buildJsonArray { labels.forEach { add(JsonPrimitive(it)) } },
                )
            }
        }
        client.post("$base/repos/$owner/$repo/issues") {
            header("Authorization", "Bearer $tk")
            header("Accept", "application/vnd.github+json")
            contentType(ContentType.Application.Json)
            setBody(payload)
        }
    }.expect()

    /** Comment on either an issue or a PR — the endpoint is the same. */
    suspend fun addIssueComment(owner: String, repo: String, number: Int, body: String): GithubCommentDto = authed { tk, base ->
        val payload = buildJsonObject { put("body", JsonPrimitive(body)) }
        client.post("$base/repos/$owner/$repo/issues/$number/comments") {
            header("Authorization", "Bearer $tk")
            header("Accept", "application/vnd.github+json")
            contentType(ContentType.Application.Json)
            setBody(payload)
        }
    }.expect()

    suspend fun listNotifications(since: String? = null, perPage: Int = 30): List<GithubNotificationDto> = authed { tk, base ->
        client.get("$base/notifications") {
            header("Authorization", "Bearer $tk")
            header("Accept", "application/vnd.github+json")
            parameter("per_page", perPage)
            if (since != null) parameter("since", since)
        }
    }.expect()

    suspend fun searchIssues(query: String, perPage: Int = 20): GithubSearchResultDto<GithubIssueDto> = authed { tk, base ->
        client.get("$base/search/issues") {
            header("Authorization", "Bearer $tk")
            header("Accept", "application/vnd.github+json")
            parameter("q", query)
            parameter("per_page", perPage)
        }
    }.expect()

    private suspend inline fun <reified T> HttpResponse.expect(): T {
        if (!status.isSuccess()) throw mapError(status, bodyAsText())
        return body()
    }

    fun close() = client.close()

    private companion object {
        val jsonCodec = Json {
            ignoreUnknownKeys = true
            isLenient = true
            explicitNulls = false
            coerceInputValues = true
        }
    }
}

// region DTOs

@Serializable
data class GithubUserDto(
    val login: String,
    val id: Long = 0L,
    val name: String = "",
    val email: String = "",
    @SerialName("html_url") val htmlUrl: String = "",
)

@Serializable
data class GithubRepoDto(
    val id: Long = 0L,
    val name: String,
    @SerialName("full_name") val fullName: String,
    val private: Boolean = false,
    val fork: Boolean = false,
    @SerialName("html_url") val htmlUrl: String = "",
    val description: String? = null,
    @SerialName("default_branch") val defaultBranch: String = "main",
    @SerialName("updated_at") val updatedAt: String = "",
    val owner: GithubOwnerDto? = null,
)

@Serializable
data class GithubOwnerDto(val login: String, val type: String = "User")

@Serializable
data class GithubContentDto(
    val type: String,
    val name: String,
    val path: String,
    val sha: String,
    val size: Long = 0L,
    val encoding: String = "",
    val content: String = "",
    @SerialName("html_url") val htmlUrl: String = "",
    @SerialName("download_url") val downloadUrl: String? = null,
)

@Serializable
data class GithubBranchDto(
    val name: String,
    val protected: Boolean = false,
    val commit: GithubCommitRefDto? = null,
)

@Serializable
data class GithubCommitRefDto(val sha: String, val url: String = "")

@Serializable
data class GithubRefDto(
    val ref: String,
    @SerialName("object") val objectField: GithubCommitRefDto,
)

@Serializable
data class GithubFileWriteResult(
    val content: GithubContentDto? = null,
    val commit: GithubCommitDto? = null,
)

@Serializable
data class GithubCommitDto(
    val sha: String,
    @SerialName("html_url") val htmlUrl: String = "",
    val message: String = "",
)

@Serializable
data class GithubPullRequestDto(
    val number: Int,
    val title: String,
    val state: String,
    val draft: Boolean = false,
    @SerialName("html_url") val htmlUrl: String = "",
    val body: String? = null,
    val user: GithubOwnerDto? = null,
    val head: GithubBranchRefDto? = null,
    val base: GithubBranchRefDto? = null,
    @SerialName("merged") val merged: Boolean = false,
    @SerialName("created_at") val createdAt: String = "",
    @SerialName("updated_at") val updatedAt: String = "",
)

@Serializable
data class GithubBranchRefDto(val ref: String, val sha: String = "")

@Serializable
data class GithubIssueDto(
    val number: Int,
    val title: String,
    val state: String,
    @SerialName("html_url") val htmlUrl: String = "",
    val body: String? = null,
    val user: GithubOwnerDto? = null,
    val labels: List<GithubLabelDto> = emptyList(),
    @SerialName("pull_request") val pullRequest: JsonObject? = null,
    @SerialName("created_at") val createdAt: String = "",
    @SerialName("updated_at") val updatedAt: String = "",
    val comments: Int = 0,
)

@Serializable
data class GithubLabelDto(val name: String, val color: String = "")

@Serializable
data class GithubCommentDto(
    val id: Long,
    val body: String,
    @SerialName("html_url") val htmlUrl: String = "",
    val user: GithubOwnerDto? = null,
)

@Serializable
data class GithubNotificationDto(
    val id: String,
    val unread: Boolean = true,
    val reason: String,
    @SerialName("updated_at") val updatedAt: String = "",
    val subject: GithubNotificationSubjectDto,
    val repository: GithubRepoDto,
)

@Serializable
data class GithubNotificationSubjectDto(
    val title: String,
    val url: String = "",
    @SerialName("latest_comment_url") val latestCommentUrl: String? = null,
    val type: String = "",
)

@Serializable
data class GithubSearchResultDto<T>(
    @SerialName("total_count") val totalCount: Int = 0,
    val items: List<T> = emptyList(),
)

// endregion

// region Exceptions

sealed class GithubApiException(message: String) : Exception(message)
class GithubAuthException(message: String) : GithubApiException(message)
class GithubNotFoundException(message: String) : GithubApiException(message)
class GithubRateLimitException(message: String) : GithubApiException(message)
class GithubValidationException(message: String) : GithubApiException(message)
class GithubGenericException(message: String) : GithubApiException(message)

// endregion
