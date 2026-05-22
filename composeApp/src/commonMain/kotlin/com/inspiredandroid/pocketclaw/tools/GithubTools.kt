@file:OptIn(kotlin.uuid.ExperimentalUuidApi::class)

package com.inspiredandroid.pocketclaw.tools

import com.inspiredandroid.pocketclaw.data.GithubAccount
import com.inspiredandroid.pocketclaw.data.GithubStore
import com.inspiredandroid.pocketclaw.github.GithubClient
import com.inspiredandroid.pocketclaw.github.GithubNotFoundException
import com.inspiredandroid.pocketclaw.network.tools.ParameterSchema
import com.inspiredandroid.pocketclaw.network.tools.Tool
import com.inspiredandroid.pocketclaw.network.tools.ToolInfo
import com.inspiredandroid.pocketclaw.network.tools.ToolSchema
import io.ktor.util.decodeBase64String
import pocketclaw.composeapp.generated.resources.Res
import pocketclaw.composeapp.generated.resources.tool_check_github_description
import pocketclaw.composeapp.generated.resources.tool_check_github_name
import pocketclaw.composeapp.generated.resources.tool_comment_github_description
import pocketclaw.composeapp.generated.resources.tool_comment_github_name
import pocketclaw.composeapp.generated.resources.tool_create_github_branch_description
import pocketclaw.composeapp.generated.resources.tool_create_github_branch_name
import pocketclaw.composeapp.generated.resources.tool_create_github_issue_description
import pocketclaw.composeapp.generated.resources.tool_create_github_issue_name
import pocketclaw.composeapp.generated.resources.tool_create_github_pr_description
import pocketclaw.composeapp.generated.resources.tool_create_github_pr_name
import pocketclaw.composeapp.generated.resources.tool_delete_github_file_description
import pocketclaw.composeapp.generated.resources.tool_delete_github_file_name
import pocketclaw.composeapp.generated.resources.tool_get_github_file_description
import pocketclaw.composeapp.generated.resources.tool_get_github_file_name
import pocketclaw.composeapp.generated.resources.tool_get_github_issue_description
import pocketclaw.composeapp.generated.resources.tool_get_github_issue_name
import pocketclaw.composeapp.generated.resources.tool_get_github_pr_description
import pocketclaw.composeapp.generated.resources.tool_get_github_pr_name
import pocketclaw.composeapp.generated.resources.tool_list_github_branches_description
import pocketclaw.composeapp.generated.resources.tool_list_github_branches_name
import pocketclaw.composeapp.generated.resources.tool_list_github_dir_description
import pocketclaw.composeapp.generated.resources.tool_list_github_dir_name
import pocketclaw.composeapp.generated.resources.tool_list_github_issues_description
import pocketclaw.composeapp.generated.resources.tool_list_github_issues_name
import pocketclaw.composeapp.generated.resources.tool_list_github_prs_description
import pocketclaw.composeapp.generated.resources.tool_list_github_prs_name
import pocketclaw.composeapp.generated.resources.tool_list_github_repos_description
import pocketclaw.composeapp.generated.resources.tool_list_github_repos_name
import pocketclaw.composeapp.generated.resources.tool_put_github_file_description
import pocketclaw.composeapp.generated.resources.tool_put_github_file_name
import pocketclaw.composeapp.generated.resources.tool_search_github_description
import pocketclaw.composeapp.generated.resources.tool_search_github_name
import pocketclaw.composeapp.generated.resources.tool_setup_github_description
import pocketclaw.composeapp.generated.resources.tool_setup_github_name
import kotlin.uuid.Uuid

/**
 * AI-facing tools for the GitHub integration. Mirrors [EmailTools]'s shape — every tool
 * is a factory that takes the [GithubStore] and returns an `object : Tool`. The auth /
 * repo manipulation primitives live on [GithubClient]; this layer just exposes them as
 * tool-calling functions with structured parameter schemas.
 */
object GithubTools {

    /**
     * Resolves the account + binds a [GithubClient]. Most tools take an optional
     * `account_id` parameter; when omitted we pick the only account, or fail with a
     * disambiguation hint.
     */
    private suspend fun <T> withClient(
        store: GithubStore,
        accountId: String?,
        block: suspend (GithubAccount, GithubClient) -> T,
    ): T {
        val accounts = store.getAccounts()
        val account = when {
            accounts.isEmpty() -> error("No GitHub accounts connected. Use setup_github first.")
            accountId != null -> accounts.find { it.id == accountId }
                ?: error("Account not found: $accountId")
            accounts.size == 1 -> accounts.first()
            else -> error(
                "Multiple GitHub accounts connected (${accounts.joinToString { it.login }}). " +
                    "Pass account_id to disambiguate.",
            )
        }
        val client = GithubClient(account.apiBaseUrl, store.getToken(account.id))
        try {
            return block(account, client)
        } finally {
            client.close()
        }
    }

    fun setupGithubTool(store: GithubStore) = object : Tool {
        override val schema = ToolSchema(
            name = "setup_github",
            description = "Connect a GitHub account using a personal access token. The token is " +
                "validated against the GitHub API before being stored. For github.com, generate a " +
                "fine-grained token at github.com/settings/tokens (recommended scopes: repo, " +
                "read:user, notifications). For GitHub Enterprise Server, also pass api_base_url " +
                "(e.g. https://github.example.com/api/v3).",
            parameters = mapOf(
                "token" to ParameterSchema(type = "string", description = "Personal access token", required = true),
                "api_base_url" to ParameterSchema(
                    type = "string",
                    description = "API base URL — defaults to https://api.github.com. Set for GitHub Enterprise.",
                    required = false,
                ),
                "web_base_url" to ParameterSchema(
                    type = "string",
                    description = "Web URL of the GitHub host — defaults to https://github.com.",
                    required = false,
                ),
            ),
        )

        override suspend fun execute(args: Map<String, Any>): Any {
            val token = (args["token"] as? String)?.trim()
                ?: return mapOf("success" to false, "error" to "Missing token")
            if (token.isEmpty()) return mapOf("success" to false, "error" to "Token is empty")

            val apiBaseUrl = (args["api_base_url"] as? String)?.trim()?.ifEmpty { null }
                ?: "https://api.github.com"
            val webBaseUrl = (args["web_base_url"] as? String)?.trim()?.ifEmpty { null }
                ?: deriveWebBase(apiBaseUrl)

            val client = GithubClient(apiBaseUrl, token)
            return try {
                val user = client.getAuthenticatedUser()
                val accountId = Uuid.random().toString()
                val account = GithubAccount(
                    id = accountId,
                    login = user.login,
                    apiBaseUrl = apiBaseUrl,
                    webBaseUrl = webBaseUrl,
                )
                store.addAccount(account)
                store.setToken(accountId, token)
                mapOf(
                    "success" to true,
                    "account_id" to accountId,
                    "login" to user.login,
                    "api_base_url" to apiBaseUrl,
                    "message" to "Connected GitHub account ${user.login}.",
                )
            } catch (e: Exception) {
                mapOf("success" to false, "error" to "Auth failed: ${e.message}")
            } finally {
                client.close()
            }
        }
    }

    fun listReposTool(store: GithubStore) = object : Tool {
        override val schema = ToolSchema(
            name = "list_github_repos",
            description = "List repositories the authenticated user has access to, sorted by " +
                "most recently updated. Returns full_name, default_branch, visibility, and the " +
                "web URL.",
            parameters = mapOf(
                "account_id" to ParameterSchema("string", "Account id (omit if only one account)", false),
                "visibility" to ParameterSchema("string", "all | public | private (default all)", false),
                "page" to ParameterSchema("integer", "Page number (default 1)", false),
            ),
        )

        override suspend fun execute(args: Map<String, Any>): Any = runCatching {
            withClient(store, args["account_id"] as? String) { _, client ->
                val repos = client.listRepos(
                    visibility = args["visibility"] as? String,
                    page = (args["page"] as? Number)?.toInt() ?: 1,
                )
                mapOf(
                    "success" to true,
                    "count" to repos.size,
                    "repos" to repos.map {
                        mapOf(
                            "full_name" to it.fullName,
                            "private" to it.private,
                            "fork" to it.fork,
                            "default_branch" to it.defaultBranch,
                            "description" to (it.description ?: ""),
                            "html_url" to it.htmlUrl,
                            "updated_at" to it.updatedAt,
                        )
                    },
                )
            }
        }.fold({ it }, ::errorMap)
    }

    fun getFileTool(store: GithubStore) = object : Tool {
        override val schema = ToolSchema(
            name = "get_github_file",
            description = "Read a file from a GitHub repository at the given ref (branch / tag " +
                "/ commit). Returns the decoded content as text plus the file's SHA — keep the " +
                "SHA if you plan to call put_github_file or delete_github_file on the same " +
                "path. For directory paths use list_github_dir instead.",
            parameters = mapOf(
                "account_id" to ParameterSchema("string", "Account id (omit if only one account)", false),
                "owner" to ParameterSchema("string", "Repo owner / org", true),
                "repo" to ParameterSchema("string", "Repo name", true),
                "path" to ParameterSchema("string", "Path inside the repo", true),
                "ref" to ParameterSchema("string", "Branch, tag, or commit (default: repo's default branch)", false),
            ),
        )

        override suspend fun execute(args: Map<String, Any>): Any = runCatching {
            val owner = args.req("owner")
            val repo = args.req("repo")
            val path = args.req("path")
            val ref = args["ref"] as? String
            withClient(store, args["account_id"] as? String) { _, client ->
                val file = client.getFile(owner, repo, path, ref)
                    ?: return@withClient mapOf(
                        "success" to false,
                        "error" to "Path '$path' is a directory or not found. Try list_github_dir.",
                    )
                val text = if (file.encoding == "base64") {
                    runCatching { file.content.replace("\n", "").decodeBase64String() }.getOrDefault(file.content)
                } else {
                    file.content
                }
                mapOf(
                    "success" to true,
                    "path" to file.path,
                    "sha" to file.sha,
                    "size" to file.size,
                    "html_url" to file.htmlUrl,
                    "content" to text,
                )
            }
        }.fold({ it }, ::errorMap)
    }

    fun listDirTool(store: GithubStore) = object : Tool {
        override val schema = ToolSchema(
            name = "list_github_dir",
            description = "List entries (files and subdirectories) inside a directory of a " +
                "GitHub repository at the given ref. Returns each entry's path, type, and size.",
            parameters = mapOf(
                "account_id" to ParameterSchema("string", "Account id (omit if only one account)", false),
                "owner" to ParameterSchema("string", "Repo owner / org", true),
                "repo" to ParameterSchema("string", "Repo name", true),
                "path" to ParameterSchema("string", "Directory path inside the repo (use empty string for the root)", true),
                "ref" to ParameterSchema("string", "Branch, tag, or commit", false),
            ),
        )

        override suspend fun execute(args: Map<String, Any>): Any = runCatching {
            val owner = args.req("owner")
            val repo = args.req("repo")
            val path = (args["path"] as? String).orEmpty()
            val ref = args["ref"] as? String
            withClient(store, args["account_id"] as? String) { _, client ->
                val entries = client.listDirectory(owner, repo, path, ref)
                mapOf(
                    "success" to true,
                    "count" to entries.size,
                    "entries" to entries.map {
                        mapOf("type" to it.type, "name" to it.name, "path" to it.path, "size" to it.size, "sha" to it.sha)
                    },
                )
            }
        }.fold({ it }, ::errorMap)
    }

    fun putFileTool(store: GithubStore) = object : Tool {
        override val schema = ToolSchema(
            name = "put_github_file",
            description = "Create a new file or update an existing one in a GitHub repository. " +
                "When updating, pass the current sha (from get_github_file). Pair with a target " +
                "branch — typically a feature branch you created via create_github_branch — and " +
                "follow up with create_github_pr to merge.",
            parameters = mapOf(
                "account_id" to ParameterSchema("string", "Account id (omit if only one account)", false),
                "owner" to ParameterSchema("string", "Repo owner / org", true),
                "repo" to ParameterSchema("string", "Repo name", true),
                "path" to ParameterSchema("string", "Path inside the repo", true),
                "content" to ParameterSchema("string", "Full new file content (utf-8 text)", true),
                "commit_message" to ParameterSchema("string", "Commit message", true),
                "branch" to ParameterSchema("string", "Target branch (default: repo's default branch)", false),
                "sha" to ParameterSchema("string", "Existing file sha — required for updates, omit when creating", false),
            ),
        )

        override suspend fun execute(args: Map<String, Any>): Any = runCatching {
            val owner = args.req("owner")
            val repo = args.req("repo")
            val path = args.req("path")
            val content = args.req("content")
            val message = args.req("commit_message")
            val branch = args["branch"] as? String
            val sha = args["sha"] as? String
            withClient(store, args["account_id"] as? String) { _, client ->
                val result = client.putFile(owner, repo, path, content, message, branch, sha)
                mapOf(
                    "success" to true,
                    "path" to (result.content?.path ?: path),
                    "new_sha" to (result.content?.sha ?: ""),
                    "commit_sha" to (result.commit?.sha ?: ""),
                    "commit_url" to (result.commit?.htmlUrl ?: ""),
                )
            }
        }.fold({ it }, ::errorMap)
    }

    fun deleteFileTool(store: GithubStore) = object : Tool {
        override val schema = ToolSchema(
            name = "delete_github_file",
            description = "Delete a file from a GitHub repository. Requires the file's current sha.",
            parameters = mapOf(
                "account_id" to ParameterSchema("string", "Account id (omit if only one account)", false),
                "owner" to ParameterSchema("string", "Repo owner / org", true),
                "repo" to ParameterSchema("string", "Repo name", true),
                "path" to ParameterSchema("string", "Path inside the repo", true),
                "sha" to ParameterSchema("string", "Current file sha (from get_github_file)", true),
                "commit_message" to ParameterSchema("string", "Commit message", true),
                "branch" to ParameterSchema("string", "Target branch (default: repo's default branch)", false),
            ),
        )

        override suspend fun execute(args: Map<String, Any>): Any = runCatching {
            val owner = args.req("owner")
            val repo = args.req("repo")
            val path = args.req("path")
            val sha = args.req("sha")
            val message = args.req("commit_message")
            val branch = args["branch"] as? String
            withClient(store, args["account_id"] as? String) { _, client ->
                val result = client.deleteFile(owner, repo, path, sha, message, branch)
                mapOf(
                    "success" to true,
                    "path" to path,
                    "commit_sha" to (result.commit?.sha ?: ""),
                    "commit_url" to (result.commit?.htmlUrl ?: ""),
                )
            }
        }.fold({ it }, ::errorMap)
    }

    fun listBranchesTool(store: GithubStore) = object : Tool {
        override val schema = ToolSchema(
            name = "list_github_branches",
            description = "List branches of a GitHub repository.",
            parameters = mapOf(
                "account_id" to ParameterSchema("string", "Account id (omit if only one account)", false),
                "owner" to ParameterSchema("string", "Repo owner / org", true),
                "repo" to ParameterSchema("string", "Repo name", true),
            ),
        )

        override suspend fun execute(args: Map<String, Any>): Any = runCatching {
            withClient(store, args["account_id"] as? String) { _, client ->
                val branches = client.listBranches(args.req("owner"), args.req("repo"))
                mapOf(
                    "success" to true,
                    "count" to branches.size,
                    "branches" to branches.map {
                        mapOf("name" to it.name, "protected" to it.protected, "sha" to (it.commit?.sha ?: ""))
                    },
                )
            }
        }.fold({ it }, ::errorMap)
    }

    fun createBranchTool(store: GithubStore) = object : Tool {
        override val schema = ToolSchema(
            name = "create_github_branch",
            description = "Create a new branch in a GitHub repository pointing at the head of an " +
                "existing branch. Use this before put_github_file when preparing a pull request.",
            parameters = mapOf(
                "account_id" to ParameterSchema("string", "Account id (omit if only one account)", false),
                "owner" to ParameterSchema("string", "Repo owner / org", true),
                "repo" to ParameterSchema("string", "Repo name", true),
                "new_branch" to ParameterSchema("string", "Name of the new branch (no refs/heads/ prefix)", true),
                "from_branch" to ParameterSchema("string", "Branch to fork from (default: repo's default branch)", false),
            ),
        )

        override suspend fun execute(args: Map<String, Any>): Any = runCatching {
            val owner = args.req("owner")
            val repo = args.req("repo")
            val newBranch = args.req("new_branch")
            val fromBranch = (args["from_branch"] as? String)?.takeIf { it.isNotBlank() }
            withClient(store, args["account_id"] as? String) { _, client ->
                val base = fromBranch ?: client.getRepo(owner, repo).defaultBranch
                val ref = client.createBranch(owner, repo, newBranch, base)
                mapOf(
                    "success" to true,
                    "ref" to ref.ref,
                    "sha" to ref.objectField.sha,
                    "from_branch" to base,
                )
            }
        }.fold({ it }, ::errorMap)
    }

    fun listPullRequestsTool(store: GithubStore) = object : Tool {
        override val schema = ToolSchema(
            name = "list_github_prs",
            description = "List pull requests in a GitHub repository.",
            parameters = mapOf(
                "account_id" to ParameterSchema("string", "Account id (omit if only one account)", false),
                "owner" to ParameterSchema("string", "Repo owner / org", true),
                "repo" to ParameterSchema("string", "Repo name", true),
                "state" to ParameterSchema("string", "open | closed | all (default open)", false),
            ),
        )

        override suspend fun execute(args: Map<String, Any>): Any = runCatching {
            withClient(store, args["account_id"] as? String) { _, client ->
                val prs = client.listPullRequests(
                    args.req("owner"),
                    args.req("repo"),
                    state = (args["state"] as? String) ?: "open",
                )
                mapOf(
                    "success" to true,
                    "count" to prs.size,
                    "pulls" to prs.map(::prSummary),
                )
            }
        }.fold({ it }, ::errorMap)
    }

    fun getPullRequestTool(store: GithubStore) = object : Tool {
        override val schema = ToolSchema(
            name = "get_github_pr",
            description = "Get full details of a pull request including head/base branches, body, and merge state.",
            parameters = mapOf(
                "account_id" to ParameterSchema("string", "Account id (omit if only one account)", false),
                "owner" to ParameterSchema("string", "Repo owner / org", true),
                "repo" to ParameterSchema("string", "Repo name", true),
                "number" to ParameterSchema("integer", "PR number", true),
            ),
        )

        override suspend fun execute(args: Map<String, Any>): Any = runCatching {
            val number = (args["number"] as? Number)?.toInt()
                ?: error("Missing or invalid 'number'")
            withClient(store, args["account_id"] as? String) { _, client ->
                val pr = client.getPullRequest(args.req("owner"), args.req("repo"), number)
                mapOf(
                    "success" to true,
                    "pr" to prSummary(pr).plus("body" to (pr.body ?: "")),
                )
            }
        }.fold({ it }, ::errorMap)
    }

    fun createPullRequestTool(store: GithubStore) = object : Tool {
        override val schema = ToolSchema(
            name = "create_github_pr",
            description = "Open a pull request from one branch to another. Typical workflow: " +
                "create_github_branch → put_github_file (commit changes) → create_github_pr.",
            parameters = mapOf(
                "account_id" to ParameterSchema("string", "Account id (omit if only one account)", false),
                "owner" to ParameterSchema("string", "Repo owner / org", true),
                "repo" to ParameterSchema("string", "Repo name", true),
                "title" to ParameterSchema("string", "PR title", true),
                "head" to ParameterSchema("string", "Source branch (the one with new commits)", true),
                "base" to ParameterSchema("string", "Target branch to merge into (default: repo's default branch)", false),
                "body" to ParameterSchema("string", "PR description (markdown)", false),
                "draft" to ParameterSchema("boolean", "Open as draft (default false)", false),
            ),
        )

        override suspend fun execute(args: Map<String, Any>): Any = runCatching {
            val owner = args.req("owner")
            val repo = args.req("repo")
            val head = args.req("head")
            val base = (args["base"] as? String)?.takeIf { it.isNotBlank() }
            val draft = (args["draft"] as? Boolean) ?: false
            withClient(store, args["account_id"] as? String) { _, client ->
                val targetBase = base ?: client.getRepo(owner, repo).defaultBranch
                val pr = client.createPullRequest(
                    owner = owner,
                    repo = repo,
                    title = args.req("title"),
                    head = head,
                    base = targetBase,
                    body = args["body"] as? String,
                    draft = draft,
                )
                mapOf(
                    "success" to true,
                    "number" to pr.number,
                    "state" to pr.state,
                    "draft" to pr.draft,
                    "html_url" to pr.htmlUrl,
                    "head" to (pr.head?.ref ?: head),
                    "base" to (pr.base?.ref ?: targetBase),
                )
            }
        }.fold({ it }, ::errorMap)
    }

    fun listIssuesTool(store: GithubStore) = object : Tool {
        override val schema = ToolSchema(
            name = "list_github_issues",
            description = "List issues in a repository (PRs are excluded — use list_github_prs for those).",
            parameters = mapOf(
                "account_id" to ParameterSchema("string", "Account id (omit if only one account)", false),
                "owner" to ParameterSchema("string", "Repo owner / org", true),
                "repo" to ParameterSchema("string", "Repo name", true),
                "state" to ParameterSchema("string", "open | closed | all (default open)", false),
            ),
        )

        override suspend fun execute(args: Map<String, Any>): Any = runCatching {
            withClient(store, args["account_id"] as? String) { _, client ->
                val issues = client.listIssues(
                    args.req("owner"),
                    args.req("repo"),
                    state = (args["state"] as? String) ?: "open",
                )
                mapOf(
                    "success" to true,
                    "count" to issues.size,
                    "issues" to issues.map(::issueSummary),
                )
            }
        }.fold({ it }, ::errorMap)
    }

    fun getIssueTool(store: GithubStore) = object : Tool {
        override val schema = ToolSchema(
            name = "get_github_issue",
            description = "Read the full body and metadata of an issue.",
            parameters = mapOf(
                "account_id" to ParameterSchema("string", "Account id (omit if only one account)", false),
                "owner" to ParameterSchema("string", "Repo owner / org", true),
                "repo" to ParameterSchema("string", "Repo name", true),
                "number" to ParameterSchema("integer", "Issue number", true),
            ),
        )

        override suspend fun execute(args: Map<String, Any>): Any = runCatching {
            val number = (args["number"] as? Number)?.toInt()
                ?: error("Missing or invalid 'number'")
            withClient(store, args["account_id"] as? String) { _, client ->
                val issue = client.getIssue(args.req("owner"), args.req("repo"), number)
                mapOf("success" to true, "issue" to issueSummary(issue).plus("body" to (issue.body ?: "")))
            }
        }.fold({ it }, ::errorMap)
    }

    fun createIssueTool(store: GithubStore) = object : Tool {
        override val schema = ToolSchema(
            name = "create_github_issue",
            description = "Create a new issue in a GitHub repository.",
            parameters = mapOf(
                "account_id" to ParameterSchema("string", "Account id (omit if only one account)", false),
                "owner" to ParameterSchema("string", "Repo owner / org", true),
                "repo" to ParameterSchema("string", "Repo name", true),
                "title" to ParameterSchema("string", "Issue title", true),
                "body" to ParameterSchema("string", "Issue body (markdown)", false),
                "labels" to ParameterSchema(
                    type = "array",
                    description = "Labels to apply (must already exist on the repo)",
                    required = false,
                ),
            ),
        )

        override suspend fun execute(args: Map<String, Any>): Any = runCatching {
            val labels = (args["labels"] as? List<*>)
                ?.mapNotNull { (it as? String)?.takeIf { s -> s.isNotBlank() } }
                .orEmpty()
            withClient(store, args["account_id"] as? String) { _, client ->
                val issue = client.createIssue(
                    owner = args.req("owner"),
                    repo = args.req("repo"),
                    title = args.req("title"),
                    body = args["body"] as? String,
                    labels = labels,
                )
                mapOf(
                    "success" to true,
                    "number" to issue.number,
                    "state" to issue.state,
                    "html_url" to issue.htmlUrl,
                )
            }
        }.fold({ it }, ::errorMap)
    }

    fun commentTool(store: GithubStore) = object : Tool {
        override val schema = ToolSchema(
            name = "comment_github",
            description = "Add a comment to an issue or pull request — the endpoint is the same.",
            parameters = mapOf(
                "account_id" to ParameterSchema("string", "Account id (omit if only one account)", false),
                "owner" to ParameterSchema("string", "Repo owner / org", true),
                "repo" to ParameterSchema("string", "Repo name", true),
                "number" to ParameterSchema("integer", "Issue or PR number", true),
                "body" to ParameterSchema("string", "Comment body (markdown)", true),
            ),
        )

        override suspend fun execute(args: Map<String, Any>): Any = runCatching {
            val number = (args["number"] as? Number)?.toInt()
                ?: error("Missing or invalid 'number'")
            withClient(store, args["account_id"] as? String) { _, client ->
                val comment = client.addIssueComment(
                    owner = args.req("owner"),
                    repo = args.req("repo"),
                    number = number,
                    body = args.req("body"),
                )
                mapOf(
                    "success" to true,
                    "comment_id" to comment.id,
                    "html_url" to comment.htmlUrl,
                )
            }
        }.fold({ it }, ::errorMap)
    }

    fun checkNotificationsTool(store: GithubStore) = object : Tool {
        override val schema = ToolSchema(
            name = "check_github",
            description = "List GitHub notifications that have arrived since the last time " +
                "PocketClaw surfaced new activity (assignments, mentions, review requests, " +
                "comments). Like check_email but for GitHub. Returns the underlying issue/PR " +
                "URLs the AI can fetch with get_github_issue / get_github_pr.",
            parameters = mapOf(
                "account_id" to ParameterSchema("string", "Account id (checks all accounts if omitted)", false),
            ),
        )

        override suspend fun execute(args: Map<String, Any>): Any = runCatching {
            val accountId = args["account_id"] as? String
            val accounts = if (accountId != null) listOfNotNull(store.getAccount(accountId)) else store.getAccounts()
            if (accounts.isEmpty()) {
                return@runCatching mapOf("success" to false, "error" to "No GitHub accounts connected. Use setup_github first.")
            }
            val pending = store.getPending()
                .filter { accountId == null || it.accountId == accountId }
            mapOf(
                "success" to true,
                "count" to pending.size,
                "notifications" to pending.map {
                    mapOf(
                        "thread_id" to it.threadId,
                        "account_id" to it.accountId,
                        "repo" to it.repo,
                        "type" to it.type,
                        "title" to it.subjectTitle,
                        "reason" to it.reason,
                        "updated_at" to it.updatedAt,
                        "subject_url" to it.subjectUrl,
                    )
                },
            )
        }.fold({ it }, ::errorMap)
    }

    fun searchTool(store: GithubStore) = object : Tool {
        override val schema = ToolSchema(
            name = "search_github",
            description = "Search issues and pull requests using the GitHub search syntax — " +
                "e.g. `is:issue is:open repo:owner/name label:bug` or `is:pr author:@me`.",
            parameters = mapOf(
                "account_id" to ParameterSchema("string", "Account id (omit if only one account)", false),
                "query" to ParameterSchema("string", "GitHub search query (issues + PRs scope)", true),
            ),
        )

        override suspend fun execute(args: Map<String, Any>): Any = runCatching {
            withClient(store, args["account_id"] as? String) { _, client ->
                val result = client.searchIssues(args.req("query"))
                mapOf(
                    "success" to true,
                    "total_count" to result.totalCount,
                    "items" to result.items.map(::issueSummary),
                )
            }
        }.fold({ it }, ::errorMap)
    }

    // region Helpers

    private fun Map<String, Any>.req(key: String): String =
        (this[key] as? String)?.takeIf { it.isNotBlank() }
            ?: error("Missing required parameter: $key")

    private fun errorMap(e: Throwable): Map<String, Any?> = when (e) {
        is GithubNotFoundException -> mapOf("success" to false, "error" to "Not found: ${e.message}")
        else -> mapOf("success" to false, "error" to (e.message ?: e::class.simpleName ?: "GitHub call failed"))
    }

    private fun prSummary(pr: com.inspiredandroid.pocketclaw.github.GithubPullRequestDto): Map<String, Any?> = mapOf(
        "number" to pr.number,
        "title" to pr.title,
        "state" to pr.state,
        "draft" to pr.draft,
        "merged" to pr.merged,
        "html_url" to pr.htmlUrl,
        "head" to (pr.head?.ref ?: ""),
        "base" to (pr.base?.ref ?: ""),
        "user" to (pr.user?.login ?: ""),
        "updated_at" to pr.updatedAt,
    )

    private fun issueSummary(issue: com.inspiredandroid.pocketclaw.github.GithubIssueDto): Map<String, Any?> = mapOf(
        "number" to issue.number,
        "title" to issue.title,
        "state" to issue.state,
        "html_url" to issue.htmlUrl,
        "user" to (issue.user?.login ?: ""),
        "labels" to issue.labels.map { it.name },
        "comments" to issue.comments,
        "updated_at" to issue.updatedAt,
    )

    // endregion

    // region ToolInfo registry

    val setupGithubToolInfo = ToolInfo("setup_github", "Setup GitHub", "Connect a GitHub account", Res.string.tool_setup_github_name, Res.string.tool_setup_github_description)
    val listReposToolInfo = ToolInfo("list_github_repos", "List Repos", "List GitHub repos", Res.string.tool_list_github_repos_name, Res.string.tool_list_github_repos_description)
    val getFileToolInfo = ToolInfo("get_github_file", "Get File", "Read a file from a GitHub repo", Res.string.tool_get_github_file_name, Res.string.tool_get_github_file_description)
    val listDirToolInfo = ToolInfo("list_github_dir", "List Directory", "List a directory in a GitHub repo", Res.string.tool_list_github_dir_name, Res.string.tool_list_github_dir_description)
    val putFileToolInfo = ToolInfo("put_github_file", "Write File", "Create or update a file in a GitHub repo", Res.string.tool_put_github_file_name, Res.string.tool_put_github_file_description)
    val deleteFileToolInfo = ToolInfo("delete_github_file", "Delete File", "Delete a file from a GitHub repo", Res.string.tool_delete_github_file_name, Res.string.tool_delete_github_file_description)
    val listBranchesToolInfo = ToolInfo("list_github_branches", "List Branches", "List branches in a GitHub repo", Res.string.tool_list_github_branches_name, Res.string.tool_list_github_branches_description)
    val createBranchToolInfo = ToolInfo("create_github_branch", "Create Branch", "Create a branch in a GitHub repo", Res.string.tool_create_github_branch_name, Res.string.tool_create_github_branch_description)
    val listPRsToolInfo = ToolInfo("list_github_prs", "List Pull Requests", "List pull requests", Res.string.tool_list_github_prs_name, Res.string.tool_list_github_prs_description)
    val getPRToolInfo = ToolInfo("get_github_pr", "Get Pull Request", "Read a PR's details", Res.string.tool_get_github_pr_name, Res.string.tool_get_github_pr_description)
    val createPRToolInfo = ToolInfo("create_github_pr", "Open Pull Request", "Open a pull request", Res.string.tool_create_github_pr_name, Res.string.tool_create_github_pr_description)
    val listIssuesToolInfo = ToolInfo("list_github_issues", "List Issues", "List issues", Res.string.tool_list_github_issues_name, Res.string.tool_list_github_issues_description)
    val getIssueToolInfo = ToolInfo("get_github_issue", "Get Issue", "Read an issue's details", Res.string.tool_get_github_issue_name, Res.string.tool_get_github_issue_description)
    val createIssueToolInfo = ToolInfo("create_github_issue", "Create Issue", "Open a new issue", Res.string.tool_create_github_issue_name, Res.string.tool_create_github_issue_description)
    val commentToolInfo = ToolInfo("comment_github", "Comment", "Comment on an issue or PR", Res.string.tool_comment_github_name, Res.string.tool_comment_github_description)
    val checkNotificationsToolInfo = ToolInfo("check_github", "Check GitHub", "Check for new GitHub activity", Res.string.tool_check_github_name, Res.string.tool_check_github_description)
    val searchToolInfo = ToolInfo("search_github", "Search GitHub", "Search issues and PRs", Res.string.tool_search_github_name, Res.string.tool_search_github_description)

    val githubToolDefinitions = listOf(
        setupGithubToolInfo,
        listReposToolInfo,
        getFileToolInfo,
        listDirToolInfo,
        putFileToolInfo,
        deleteFileToolInfo,
        listBranchesToolInfo,
        createBranchToolInfo,
        listPRsToolInfo,
        getPRToolInfo,
        createPRToolInfo,
        listIssuesToolInfo,
        getIssueToolInfo,
        createIssueToolInfo,
        commentToolInfo,
        checkNotificationsToolInfo,
        searchToolInfo,
    )

    fun getGithubTools(store: GithubStore): List<Tool> = listOf(
        setupGithubTool(store),
        listReposTool(store),
        getFileTool(store),
        listDirTool(store),
        putFileTool(store),
        deleteFileTool(store),
        listBranchesTool(store),
        createBranchTool(store),
        listPullRequestsTool(store),
        getPullRequestTool(store),
        createPullRequestTool(store),
        listIssuesTool(store),
        getIssueTool(store),
        createIssueTool(store),
        commentTool(store),
        checkNotificationsTool(store),
        searchTool(store),
    )

    // endregion
}

private fun deriveWebBase(apiBaseUrl: String): String {
    // api.github.com → github.com; <host>/api/v3 → <host>
    val trimmed = apiBaseUrl.trimEnd('/')
    return when {
        trimmed.startsWith("https://api.github.com") -> "https://github.com"
        trimmed.endsWith("/api/v3") -> trimmed.removeSuffix("/api/v3")
        else -> trimmed
    }
}
