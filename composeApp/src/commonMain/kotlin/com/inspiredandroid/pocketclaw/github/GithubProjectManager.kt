package com.inspiredandroid.pocketclaw.github

import com.inspiredandroid.pocketclaw.data.GithubAccount
import com.inspiredandroid.pocketclaw.data.GithubStore
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

/**
 * Enhanced GitHub repository manager supporting full project lifecycle:
 * - Issue & PR management
 * - Release management
 * - Workflow execution tracking
 * - Advanced search capabilities
 */
@OptIn(ExperimentalTime::class)
class GithubProjectManager(
    private val githubStore: GithubStore,
    private val clientFactory: (apiBaseUrl: String, token: String) -> GithubEnhancedClient = ::GithubEnhancedClient,
) {
    private val clients = mutableMapOf<String, GithubEnhancedClient>()

    private suspend fun getClient(account: GithubAccount): GithubEnhancedClient {
        val key = account.id
        return clients.getOrPut(key) {
            clientFactory(account.apiBaseUrl, githubStore.getToken(account.id))
        }
    }

    // ========== Issue Management ==========

    suspend fun getIssues(
        account: GithubAccount,
        owner: String,
        repo: String,
        state: String = "open",
        labels: List<String> = emptyList(),
    ): List<GithubIssueDto> = getClient(account).listIssues(
        owner = owner,
        repo = repo,
        state = state,
        labels = labels,
    )

    suspend fun createIssue(
        account: GithubAccount,
        owner: String,
        repo: String,
        title: String,
        body: String? = null,
        labels: List<String> = emptyList(),
        assignees: List<String> = emptyList(),
    ): GithubIssueDto = getClient(account).createIssue(
        owner = owner,
        repo = repo,
        title = title,
        body = body,
        labels = labels,
        assignees = assignees,
    )

    suspend fun updateIssue(
        account: GithubAccount,
        owner: String,
        repo: String,
        number: Int,
        title: String? = null,
        body: String? = null,
        state: String? = null,
    ): GithubIssueDto = getClient(account).updateIssue(
        owner = owner,
        repo = repo,
        number = number,
        title = title,
        body = body,
        state = state,
    )

    suspend fun closeIssue(
        account: GithubAccount,
        owner: String,
        repo: String,
        number: Int,
    ): GithubIssueDto = updateIssue(
        account = account,
        owner = owner,
        repo = repo,
        number = number,
        state = "closed",
    )

    // ========== Pull Request Management ==========

    suspend fun getPullRequests(
        account: GithubAccount,
        owner: String,
        repo: String,
        state: String = "open",
    ): List<GithubPullRequestDto> = getClient(account).listPullRequests(
        owner = owner,
        repo = repo,
        state = state,
    )

    suspend fun createPullRequest(
        account: GithubAccount,
        owner: String,
        repo: String,
        title: String,
        head: String,
        base: String,
        body: String? = null,
        draft: Boolean = false,
    ): GithubPullRequestDto = getClient(account).createPullRequest(
        owner = owner,
        repo = repo,
        title = title,
        head = head,
        base = base,
        body = body,
        draft = draft,
    )

    suspend fun mergePullRequest(
        account: GithubAccount,
        owner: String,
        repo: String,
        number: Int,
        mergeMethod: String = "squash",
    ): Boolean {
        return try {
            getClient(account).mergePullRequest(
                owner = owner,
                repo = repo,
                number = number,
                mergeMethod = mergeMethod,
            )
            true
        } catch (e: Exception) {
            false
        }
    }

    // ========== Comments ==========

    suspend fun addComment(
        account: GithubAccount,
        owner: String,
        repo: String,
        issueNumber: Int,
        comment: String,
    ): GithubCommentDto = getClient(account).addIssueComment(
        owner = owner,
        repo = repo,
        number = issueNumber,
        body = comment,
    )

    suspend fun getComments(
        account: GithubAccount,
        owner: String,
        repo: String,
        issueNumber: Int,
    ): List<GithubCommentDto> = getClient(account).listIssueComments(
        owner = owner,
        repo = repo,
        number = issueNumber,
    )

    // ========== Releases ==========

    suspend fun getReleases(
        account: GithubAccount,
        owner: String,
        repo: String,
    ): List<GithubReleaseDto> = getClient(account).listReleases(owner, repo)

    suspend fun createRelease(
        account: GithubAccount,
        owner: String,
        repo: String,
        tagName: String,
        name: String? = null,
        body: String? = null,
        draft: Boolean = false,
        prerelease: Boolean = false,
    ): GithubReleaseDto = getClient(account).createRelease(
        owner = owner,
        repo = repo,
        tagName = tagName,
        name = name,
        body = body,
        draft = draft,
        prerelease = prerelease,
    )

    // ========== Workflow Management ==========

    suspend fun getWorkflowRuns(
        account: GithubAccount,
        owner: String,
        repo: String,
    ): List<GithubWorkflowRunDto> = getClient(account).listWorkflowRuns(owner, repo)

    suspend fun triggerWorkflow(
        account: GithubAccount,
        owner: String,
        repo: String,
        workflowId: String,
        ref: String = "main",
    ): Boolean = getClient(account).triggerWorkflow(owner, repo, workflowId, ref)

    // ========== Search ==========

    suspend fun searchIssues(
        account: GithubAccount,
        query: String,
    ): List<GithubIssueDto> = getClient(account).searchIssuesAdvanced(query).items

    suspend fun searchCode(
        account: GithubAccount,
        query: String,
    ): List<GithubCodeSearchResultDto> = getClient(account).searchCode(query).items

    // ========== Repository Info ==========

    suspend fun getRepositories(account: GithubAccount): List<GithubRepoDto> =
        getClient(account).listRepos()

    suspend fun getRepository(
        account: GithubAccount,
        owner: String,
        repo: String,
    ): GithubRepoDto = getClient(account).getRepo(owner, repo)

    suspend fun getBranches(
        account: GithubAccount,
        owner: String,
        repo: String,
    ): List<GithubBranchDto> = getClient(account).listBranches(owner, repo)

    suspend fun getCommits(
        account: GithubAccount,
        owner: String,
        repo: String,
    ): List<GithubCommitDto> = getClient(account).listCommits(owner, repo)

    // ========== File Management ==========

    suspend fun getFile(
        account: GithubAccount,
        owner: String,
        repo: String,
        path: String,
        ref: String? = null,
    ): GithubContentDto? = getClient(account).getFile(owner, repo, path, ref)

    suspend fun updateFile(
        account: GithubAccount,
        owner: String,
        repo: String,
        path: String,
        content: String,
        message: String,
        sha: String? = null,
    ): GithubFileWriteResult = getClient(account).putFile(
        owner = owner,
        repo = repo,
        path = path,
        contentText = content,
        commitMessage = message,
        sha = sha,
    )

    // ========== Cleanup ==========

    fun closeAllClients() {
        clients.values.forEach { it.close() }
        clients.clear()
    }
}
