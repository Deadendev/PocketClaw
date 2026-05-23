package com.inspiredandroid.pocketclaw.github

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

/**
 * Dialog for adding a new GitHub account.
 * Supports both GitHub.com and GitHub Enterprise Server with custom API endpoints.
 */
@Composable
fun GithubAddAccountDialog(
    onDismiss: () -> Unit,
    onAccountAdded: (login: String, token: String, apiBaseUrl: String) -> Unit,
) {
    var apiBaseUrl by remember { mutableStateOf("https://api.github.com") }
    var token by remember { mutableStateOf("") }
    var isValidating by remember { mutableStateOf(false) }
    var validationError by remember { mutableStateOf<String?>(null) }
    var validatedLogin by remember { mutableStateOf<String?>(null) }
    var showTokenHelp by remember { mutableStateOf(false) }

    val scope = rememberCoroutineScope()
    val isValid = token.isNotBlank() && validatedLogin != null && !isValidating

    if (showTokenHelp) {
        AlertDialog(
            onDismissRequest = { showTokenHelp = false },
            title = { Text("GitHub Personal Access Token") },
            text = {
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    Text(
                        text = "How to create a Personal Access Token:",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = """
1. Go to GitHub Settings → Developer settings → Personal access tokens
2. Click "Generate new token"
3. Give it a descriptive name (e.g., "PocketClaw")
4. Select scopes:
   • repo (full control of private repositories)
   • notifications (access to notifications)
5. Click "Generate token"
6. Copy the token immediately (you won't see it again)

For GitHub Enterprise Server:
• Use your Enterprise API base URL instead of api.github.com
• Example: https://github.example.com/api/v3
                        """.trimIndent(),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { showTokenHelp = false }) {
                    Text("OK")
                }
            },
        )
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add GitHub Account") },
        text = {
            Column {
                Text(
                    text = "Connect your GitHub account to sync notifications and access repositories.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(12.dp))

                OutlinedTextField(
                    value = apiBaseUrl,
                    onValueChange = {
                        apiBaseUrl = it
                        validatedLogin = null
                        validationError = null
                    },
                    label = { Text("GitHub API Base URL") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    enabled = !isValidating,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                )
                Text(
                    text = "Use 'https://api.github.com' for GitHub.com or your Enterprise URL",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp),
                )

                Spacer(Modifier.height(12.dp))

                OutlinedTextField(
                    value = token,
                    onValueChange = {
                        token = it
                        validatedLogin = null
                        validationError = null
                    },
                    label = { Text("Personal Access Token") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    enabled = !isValidating,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    trailingIcon = {
                        TextButton(onClick = { showTokenHelp = true }) {
                            Icon(Icons.Default.Info, contentDescription = "Help")
                        }
                    },
                )

                if (validationError != null) {
                    Spacer(Modifier.height(8.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                MaterialTheme.colorScheme.errorContainer,
                                RoundedCornerShape(4.dp),
                            )
                            .padding(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            Icons.Default.Close,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.width(20.dp),
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = validationError ?: "Unknown error",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                }

                if (validatedLogin != null) {
                    Spacer(Modifier.height(8.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                MaterialTheme.colorScheme.primaryContainer,
                                RoundedCornerShape(4.dp),
                            )
                            .padding(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            Icons.Default.Check,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.width(20.dp),
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = "Connected as: $validatedLogin",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                        )
                    }
                }

                if (isValidating) {
                    Spacer(Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center,
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.width(24.dp),
                            color = MaterialTheme.colorScheme.primary,
                        )
                        Spacer(Modifier.width(8.dp))
                        Text("Validating token...")
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (validatedLogin != null) {
                        onAccountAdded(validatedLogin!!, token, apiBaseUrl)
                        onDismiss()
                    }
                },
                enabled = isValid,
            ) {
                Text("Add Account")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        },
        modifier = Modifier.fillMaxWidth(0.9f),
    )

    // Auto-validate token when it changes and has content
    if (token.isNotBlank() && validatedLogin == null && !isValidating) {
        scope.launch {
            isValidating = true
            validationError = null
            try {
                val client = GithubClient(apiBaseUrl, token)
                val user = client.getAuthenticatedUser()
                validatedLogin = user.login
                client.close()
            } catch (e: Exception) {
                validationError = when (e) {
                    is GithubAuthException -> "Invalid token or insufficient permissions"
                    is GithubNotFoundException -> "GitHub API endpoint not found. Check your URL."
                    else -> "Error: ${e.message ?: "Unknown error"}"
                }
                validatedLogin = null
            } finally {
                isValidating = false
            }
        }
    }
}
