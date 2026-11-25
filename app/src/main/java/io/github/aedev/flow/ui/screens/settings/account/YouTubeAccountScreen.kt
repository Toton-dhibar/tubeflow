package io.github.aedev.flow.ui.screens.settings.account

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccountCircle
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.UploadFile
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import io.github.aedev.flow.R
import io.github.aedev.flow.data.local.CookieImportFailure
import io.github.aedev.flow.innertube.models.AccountInfo
import io.github.aedev.flow.ui.components.settings.SettingsPage
import io.github.aedev.flow.ui.components.shared.FlowAlertDialog

private const val ANY_FILE = "*/*"
private val AccountAvatarSize = 48.dp

@Composable
internal fun YouTubeAccountScreen(
    onBack: (() -> Unit)?,
    highlight: String?,
    viewModel: YouTubeAccountViewModel = hiltViewModel(),
) {
    val account by viewModel.account.collectAsStateWithLifecycle()
    val isWorking by viewModel.isWorking.collectAsStateWithLifecycle()
    val failure by viewModel.failure.collectAsStateWithLifecycle()
    var cookieText by remember { mutableStateOf("") }
    var confirmSignOut by remember { mutableStateOf(false) }
    val picker =
        rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            uri?.let(viewModel::importFile)
        }
    LaunchedEffect(account) {
        if (account != null) cookieText = ""
    }

    SettingsPage(
        title = stringResource(R.string.youtube_account_title),
        onBack = onBack,
        highlight = highlight,
    ) {
        item("youtube_account.warning") {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    text = stringResource(R.string.youtube_account_cookie_warning),
                    color = MaterialTheme.colorScheme.onErrorContainer,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(16.dp),
                )
            }
        }

        if (account != null) {
            item("youtube_account.profile") { YouTubeAccountProfile(account!!) }
            item("youtube_account.sign_out") {
                Button(
                    onClick = { confirmSignOut = true },
                    enabled = !isWorking,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(Icons.Outlined.Delete, contentDescription = null)
                    Text(
                        text = stringResource(R.string.youtube_account_sign_out),
                        modifier = Modifier.padding(start = 8.dp),
                    )
                }
            }
        } else {
            item("youtube_account.import") {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        value = cookieText,
                        onValueChange = { cookieText = it },
                        label = { Text(stringResource(R.string.youtube_account_cookie_text_label)) },
                        placeholder = { Text(stringResource(R.string.youtube_account_cookie_text_hint)) },
                        minLines = 5,
                        maxLines = 10,
                        enabled = !isWorking,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Button(
                        onClick = { picker.launch(arrayOf(ANY_FILE)) },
                        enabled = !isWorking,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Icon(Icons.Outlined.UploadFile, contentDescription = null)
                        Text(
                            text = stringResource(R.string.youtube_account_choose_file),
                            modifier = Modifier.padding(start = 8.dp),
                        )
                    }
                    Button(
                        onClick = {
                            viewModel.importText(cookieText)
                        },
                        enabled = !isWorking && cookieText.isNotBlank(),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        if (isWorking) {
                            CircularProgressIndicator(modifier = Modifier.size(20.dp))
                        } else {
                            Icon(Icons.Outlined.AccountCircle, contentDescription = null)
                            Text(
                                text = stringResource(R.string.youtube_account_import),
                                modifier = Modifier.padding(start = 8.dp),
                            )
                        }
                    }
                }
            }
        }
        failure?.let { error ->
            item("youtube_account.failure") {
                Text(
                    text = stringResource(error.stringResource()),
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
    }

    if (confirmSignOut) {
        FlowAlertDialog(
            onDismissRequest = { confirmSignOut = false },
            title = { Text(stringResource(R.string.youtube_account_sign_out_title)) },
            text = { Text(stringResource(R.string.youtube_account_sign_out_warning)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmSignOut = false
                        viewModel.signOut()
                    },
                ) {
                    Text(stringResource(R.string.youtube_account_sign_out))
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmSignOut = false }) {
                    Text(stringResource(R.string.cancel))
                }
            },
        )
    }
}

@Composable
private fun YouTubeAccountProfile(account: AccountInfo) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (account.thumbnailUrl.isNullOrBlank()) {
            Icon(
                Icons.Outlined.AccountCircle,
                contentDescription = null,
                modifier = Modifier.size(AccountAvatarSize),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            AsyncImage(
                model = account.thumbnailUrl,
                contentDescription = stringResource(R.string.youtube_account_avatar_description),
                modifier = Modifier.size(AccountAvatarSize),
            )
        }
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(account.name, style = MaterialTheme.typography.titleMedium)
            account.channelHandle?.let {
                Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

private fun CookieImportFailure.stringResource(): Int =
    when (this) {
        CookieImportFailure.EMPTY -> R.string.youtube_account_error_empty

        CookieImportFailure.INVALID_FORMAT -> R.string.youtube_account_error_format

        CookieImportFailure.MISSING_REQUIRED_COOKIES -> R.string.youtube_account_error_missing_cookies

        CookieImportFailure.REQUIRED_COOKIE_EXPIRED,
        CookieImportFailure.COOKIES_EXPIRED,
        -> R.string.youtube_account_error_expired

        CookieImportFailure.NETWORK -> R.string.youtube_account_error_network

        CookieImportFailure.STORAGE -> R.string.youtube_account_error_storage

        CookieImportFailure.FILE_READ -> R.string.youtube_account_error_file
    }
