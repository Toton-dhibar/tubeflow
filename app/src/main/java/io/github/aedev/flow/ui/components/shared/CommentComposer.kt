package io.github.aedev.flow.ui.components.shared

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import io.github.aedev.flow.R
import io.github.aedev.flow.data.comments.CommentPostFailure
import io.github.aedev.flow.data.comments.CommentPostUiState
import io.github.aedev.flow.data.model.Comment

data class CommentComposerState(
    val videoKey: String,
    val isSignedIn: Boolean,
    val canPostComment: Boolean,
    val postState: CommentPostUiState,
)

@Composable
fun CommentComposer(
    state: CommentComposerState,
    target: String,
    canSubmit: Boolean,
    onSubmit: (String) -> Unit,
    modifier: Modifier = Modifier,
    isReply: Boolean = false,
) {
    var text by rememberSaveable(state.videoKey, target) { mutableStateOf("") }
    LaunchedEffect(state.postState.successVersion, state.postState.successTarget) {
        if (state.postState.successTarget == target) text = ""
    }

    Column(modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
        if (!state.isSignedIn) {
            Text(
                text = stringResource(R.string.comment_sign_in_prompt),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            return@Column
        }

        Row {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                modifier = Modifier.weight(1f),
                placeholder = {
                    Text(
                        stringResource(
                            if (isReply) R.string.reply_comment_placeholder else R.string.add_comment_placeholder,
                        ),
                    )
                },
                enabled = !state.postState.isPosting,
                maxLines = 4,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
            )
            Button(
                onClick = { onSubmit(text) },
                enabled =
                    canSubmit &&
                        text.isNotBlank() &&
                        !state.postState.isPosting &&
                        !state.postState.isCoolingDown,
                modifier = Modifier.padding(start = 8.dp),
            ) {
                Text(stringResource(R.string.comment_send))
            }
        }
        if (!canSubmit && !isReply) {
            Text(
                text = stringResource(R.string.comment_form_unavailable),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
        if (state.postState.failureTarget == target) {
            val message =
                when (state.postState.failure) {
                    CommentPostFailure.COOKIES_EXPIRED -> R.string.comment_error_cookies_expired
                    CommentPostFailure.COMMENTS_DISABLED -> R.string.comment_error_comments_disabled
                    CommentPostFailure.RATE_LIMITED -> R.string.comment_error_rate_limited
                    CommentPostFailure.SPAM_BLOCK -> R.string.comment_error_spam_block
                    CommentPostFailure.NETWORK -> R.string.comment_error_network
                    CommentPostFailure.FORM_UNAVAILABLE -> R.string.comment_form_unavailable
                    null -> null
                }
            message?.let {
                Text(
                    text = stringResource(it),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        }
    }
}

@Composable
fun CommentReplyComposer(
    state: CommentComposerState,
    parent: Comment,
    onSubmit: (Comment, String) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (!state.isSignedIn || parent.replyParams.isNullOrBlank()) return
    var isOpen by rememberSaveable(state.videoKey, parent.id) { mutableStateOf(false) }
    Column(modifier.fillMaxWidth()) {
        TextButton(onClick = { isOpen = !isOpen }) {
            Text(stringResource(if (isOpen) R.string.comment_cancel_reply else R.string.comment_reply))
        }
        if (isOpen) {
            CommentComposer(
                state = state,
                target = parent.id,
                canSubmit = true,
                onSubmit = { onSubmit(parent, it) },
                isReply = true,
            )
        }
    }
}
