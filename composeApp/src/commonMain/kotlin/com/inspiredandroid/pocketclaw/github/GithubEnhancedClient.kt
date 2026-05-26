package com.inspiredandroid.pocketclaw.github

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
 * Enhanced GitHub v3 API client with project management capabilities.
 * Supports: Issues, PRs, Workflows, Releases, Commits, and Advanced Search.
 */
class GithubEnhancedClient(
    private val apiBaseUrl: String,
    private val token: String,
) {
    private val client: HttpClient = createHttpClient()

    private fun createHttpClient(): HttpClient = HttpClient {
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
            listOfNotNull(msg, errors?.takeIf { it.isNotBlank() }).joinToString(": ").ifblank { null }
        }
    } catch (_: Exception) {
        null
    }

    // ========== User & Auth ==========

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

        val legacy = authed { tk, base ->
            client.get("$base/user") {
                header("Authorization", "token $tk")
                header("Accept", "application/vnd.github+json")
                header("X-GitHub-Api-Version", "2022-11-28")
            }
        }
        return legacy.expect()
    }

    // ========== Repositories ==========

    suspend fun listRepos(visibility: String? = null, perPage: Int = 30, page: Int = 1): List<GithubRepoDto> = authed { tk, base ->
        client.get("$base/user/repos") {
            header("Authorization", "Bearer $tk")
            parameter("per_page", perPage)
            parameter("page", page)
            parameter("sort", "updated")
            if (visibility != null) parameter("visibility", visibility)
        }
    }.expect()

    suspend fun getRepo(owner: String, repo: String): GithubRepoDto = authed { tk, base ->
        client.get("$base/repos/$owner/$repo") {
            header("Authorization", "Bearer $tk")
        }
    }.expect()

    // ========== Issues Management ==========

    suspend fun listIssues(
        owner: String,
        repo: String,
        state: String = "open",
        labels: List<String> = emptyList(),
        assignee: String? = null,
        perPage: Int = 30,
        page: Int = 1,
    ): List<GithubIssueDto> = authed { tk, base ->
        client.get("$base/repos/$owner/$repo/issues") {
            header("Authorization", "Bearer $tk")
            parameter("state", state)
            parameter("per_page", perPage)
            parameter("page", page)
            if (labels.isNotEmpty()) parameter("labels", labels.joinToString(","))
            if (assignee != null) parameter("assignee", assignee)
        }
    }.expect<List<GithubIssueDto>>()
        .filter { it.pullRequest == null }

    suspend fun getIssue(owner: String, repo: String, number: Int): GithubIssueDto = authed { tk, base ->
        client.get("$base/repos/$owner/$repo/issues/$number") {
            header("Authorization", "Bearer $tk")
        }
    }.expect()

    suspend fun createIssue(
        owner: String,
        repo: String,
        title: String,
        body: String? = null,
        labels: List<String> = emptyList(),
        assignees: List<String> = emptyList(),
    ): GithubIssueDto = authed { tk, base ->
        val payload = buildJsonObject {
            put("title", JsonPrimitive(title))
            if (body != null) put("body", JsonPrimitive(body))
            if (labels.isNotEmpty()) {
                put("labels", buildJsonArray { labels.forEach { add(JsonPrimitive(it)) } })
            }
            if (assignees.isNotEmpty()) {
                put("assignees", buildJsonArray { assignees.forEach { add(JsonPrimitive(it)) } })
            }
        }
        client.post("$base/repos/$owner/$repo/issues") {
            header("Authorization", "Bearer $tk")
            contentType(ContentType.Application.Json)
            setBody(payload)
        }
    }.expect()

    suspend fun updateIssue(
        owner: String,
        repo: String,
        number: Int,
        title: String? = null,
        body: String? = null,
        state: String? = null,
        labels: List<String>? = null,
        assignees: List<String>? = null,
    ): GithubIssueDto = authed { tk, base ->
        val payload = buildJsonObject {
            if (title != null) put("title", JsonPrimitive(title))
            if (body != null) put("body", JsonPrimitive(body))
            if (state != null) put("state", JsonPrimitive(state))
            if (labels != null) {
                put("labels", buildJsonArray { labels.forEach { add(JsonPrimitive(it)) } })
            }
            if (assignees != null) {
                put("assignees", buildJsonArray { assignees.forEach { add(JsonPrimitive(it)) } })
            }
        }
        client.patch("$base/repos/$owner/$repo/issues/$number") {
            header("Authorization", "Bearer $tk")
            contentType(ContentType.Application.Json)
            setBody(payload)
        }
    }.expect()

    // ========== Pull Requests ==========

    suspend fun listPullRequests(
        owner: String,
        repo: String,
        state: String = "open",
        perPage: Int = 30,
        page: Int = 1,
    ): List<GithubPullRequestDto> = authed { tk, base ->
        client.get("$base/repos/$owner/$repo/pulls") {
            header("Authorization", "Bearer $tk")
            parameter("state", state)
            parameter("per_page", perPage)
            parameter("page", page)
        }
    }.expect()

    suspend fun getPullRequest(owner: String, repo: String, number: Int): GithubPullRequestDto = authed { tk, base ->
        client.get("$base/repos/$owner/$repo/pulls/$number") {
            header("Authorization", "Bearer $tk")
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
            contentType(ContentType.Application.Json)
            setBody(payload)
        }
    }.expect()

    suspend fun mergePullRequest(
        owner: String,
        repo: String,
        number: Int,
        commitTitle: String? = null,
        commitMessage: String? = null,
        mergeMethod: String = "merge",
    ): GithubMergeResultDto = authed { tk, base ->
        val payload = buildJsonObject {
            if (commitTitle != null) put("commit_title", JsonPrimitive(commitTitle))
            if (commitMessage != null) put("commit_message", JsonPrimitive(commitMessage))
            put("merge_method", JsonPrimitive(mergeMethod))
        }
        client.put("$base/repos/$owner/$repo/pulls/$number/merge") {
            header("Authorization", "Bearer $tk")
            contentType(ContentType.Application.Json)
            setBody(payload)
        }
    }.expect()

    // ========== Comments ==========

    suspend fun addIssueComment(owner: String, repo: String, number: Int, body: String): GithubCommentDto = authed { tk, base ->
        val payload = buildJsonObject { put("body", JsonPrimitive(body)) }
        client.post("$base/repos/$owner/$repo/issues/$number/comments") {
            header("Authorization", "Bearer $tk")
            contentType(ContentType.Application.Json)
            setBody(payload)
        }
    }.expect()

    suspend fun listIssueComments(owner: String, repo: String, number: Int, perPage: Int = 30): List<GithubCommentDto> = authed { tk, base ->
        client.get("$base/repos/$owner/$repo/issues/$number/comments") {
            header("Authorization", "Bearer $tk")
            parameter("per_page", perPage)
        }
    }.expect()

    // ========== Branches & Tags ==========

    suspend fun listBranches(owner: String, repo: String, perPage: Int = 30): List<GithubBranchDto> = authed { tk, base ->
        client.get("$base/repos/$owner/$repo/branches") {
            header("Authorization", "Bearer $tk")
            parameter("per_page", perPage)
        }
    }.expect()

    suspend fun createBranch(owner: String, repo: String, newBranch: String, fromBranch: String): GithubRefDto = authed { tk, base ->
        val sha = getRefSha(owner, repo, fromBranch)
        val body = buildJsonObject {
            put("ref", JsonPrimitive("refs/heads/$newBranch"))
            put("sha", JsonPrimitive(sha))
        }
        client.post("$base/repos/$owner/$repo/git/refs") {
            header("Authorization", "Bearer $tk")
            contentType(ContentType.Application.Json)
            setBody(body)
        }
    }.expect()

    suspend fun getRefSha(owner: String, repo: String, ref: String): String = authed { tk, base ->
        client.get("$base/repos/$owner/$repo/git/ref/heads/$ref") {
            header("Authorization", "Bearer $tk")
        }
    }.expect<GithubRefDto>().objectField.sha

    // ========== Releases ==========

    suspend fun listReleases(owner: String, repo: String, perPage: Int = 30): List<GithubReleaseDto> = authed { tk, base ->
        client.get("$base/repos/$owner/$repo/releases") {
            header("Authorization", "Bearer $tk")
            parameter("per_page", perPage)
        }
    }.expect()

    suspend fun createRelease(
        owner: String,
        repo: String,
        tagName: String,
        name: String? = null,
        body: String? = null,
        draft: Boolean = false,
        prerelease: Boolean = false,
    ): GithubReleaseDto = authed { tk, base ->
        val payload = buildJsonObject {
            put("tag_name", JsonPrimitive(tagName))
            if (name != null) put("name", JsonPrimitive(name))
            if (body != null) put("body", JsonPrimitive(body))
            put("draft", JsonPrimitive(draft))
            put("prerelease", JsonPrimitive(prerelease))
        }
        client.post("$base/repos/$owner/$repo/releases") {
            header("Authorization", "Bearer $tk")
            contentType(ContentType.Application.Json)
            setBody(payload)
        }
    }.expect()

    // ========== Workflow Runs & Actions ==========

    suspend fun listWorkflowRuns(owner: String, repo: String, perPage: Int = 30): List<GithubWorkflowRunDto> = authed { tk, base ->
        client.get("$base/repos/$owner/$repo/actions/runs") {
            header("Authorization", "Bearer $tk")
            parameter("per_page", perPage)
        }
    }.expect<GithubWorkflowRunsResponseDto>().workflowRuns

    suspend fun getWorkflowRun(owner: String, repo: String, runId: Long): GithubWorkflowRunDto = authed { tk, base ->
        client.get("$base/repos/$owner/$repo/actions/runs/$runId") {
            header("Authorization", "Bearer $tk")
        }
    }.expect()

    suspend fun triggerWorkflow(owner: String, repo: String, workflowId: String, ref: String = "main"): Boolean = authed { tk, base ->
        client.post("$base/repos/$owner/$repo/actions/workflows/$workflowId/dispatches") {
            header("Authorization", "Bearer $tk")
            contentType(ContentType.Application.Json)
            setBody(buildJsonObject { put("ref", JsonPrimitive(ref)) })
        }
    }.status == HttpStatusCode.NoContent

    // ========== Commits ==========

    suspend fun listCommits(owner: String, repo: String, perPage: Int = 30): List<GithubCommitDto> = authed { tk, base ->
        client.get("$base/repos/$owner/$repo/commits") {
            header("Authorization", "Bearer $tk")
            parameter("per_page", perPage)
        }
    }.expect()

    suspend fun getCommit(owner: String, repo: String, sha: String): GithubDetailedCommitDto = authed { tk, base ->
        client.get("$base/repos/$owner/$repo/commits/$sha") {
            header("Authorization", "Bearer $tk")
        }
    }.expect()

    // ========== Search ==========

    suspend fun searchIssuesAdvanced(query: String, perPage: Int = 30): GithubSearchResultDto<GithubIssueDto> = authed { tk, base ->
        client.get("$base/search/issues") {
            header("Authorization", "Bearer $tk")
            parameter("q", query)
            parameter("per_page", perPage)
        }
    }.expect()

    suspend fun searchCode(query: String, perPage: Int = 30): GithubSearchResultDto<GithubCodeSearchResultDto> = authed { tk, base ->
        client.get("$base/search/code") {
            header("Authorization", "Bearer $tk")
            parameter("q", query)
            parameter("per_page", perPage)
        }
    }.expect()

    // ========== Notifications ==========

    suspend fun listNotifications(since: String? = null, perPage: Int = 30): List<GithubNotificationDto> = authed { tk, base ->
        client.get("$base/notifications") {
            header("Authorization", "Bearer $tk")
            parameter("per_page", perPage)
            if (since != null) parameter("since", since)
        }
    }.expect()

    suspend fun markNotificationAsRead(threadId: String): Boolean = authed { tk, base ->
        client.patch("$base/notifications/threads/$threadId") {
            header("Authorization", "Bearer $tk")
        }
    }.status.isSuccess()

    // ========== Files ==========

    suspend fun getFile(owner: String, repo: String, path: String, ref: String? = null): GithubContentDto? = authed { tk, base ->
        client.get("$base/repos/$owner/$repo/contents/$path") {
            header("Authorization", "Bearer $tk")
            if (ref != null) parameter("ref", ref)
        }
    }.let { resp ->
        if (resp.status == HttpStatusCode.NotFound) return@let null
        if (!resp.status.isSuccess()) throw mapError(resp.status, resp.bodyAsText())
        val raw = resp.bodyAsText()
        if (raw.trimStart().startsWith("[")) null else jsonCodec.decodeFromString(GithubContentDto.serializer(), raw)
    }

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
            contentType(ContentType.Application.Json)
            setBody(body)
        }
    }.expect()

    // ========== Utilities ==========

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

// ========== DTOs - Unique to Enhanced Client ==========

@Serializable
data class GithubDetailedCommitDto(
    val sha: String,
    val commit: GithubCommitDetailsDto? = null,
    @SerialName("html_url") val htmlUrl: String = "",
    val author: GithubOwnerDto? = null,
)

@Serializable
data class GithubCommitDetailsDto(
    val message: String,
    val author: GithubAuthorDto? = null,
)

@Serializable
data class GithubCodeSearchResultDto(
    val name: String,
    val path: String,
    val repository: GithubRepoDto? = null,
    @SerialName("html_url") val htmlUrl: String = "",
)

// ========== Reuse DTOs and Exceptions from GithubClient ==========
// All other DTOs and Exceptions are imported from GithubClient.kt
