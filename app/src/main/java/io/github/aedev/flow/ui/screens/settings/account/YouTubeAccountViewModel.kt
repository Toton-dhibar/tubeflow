package io.github.aedev.flow.ui.screens.settings.account

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.aedev.flow.data.local.CookieImportFailure
import io.github.aedev.flow.data.local.CookieImportResult
import io.github.aedev.flow.data.local.YouTubeCookieAuth
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

@HiltViewModel
internal class YouTubeAccountViewModel
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
        private val cookieAuth: YouTubeCookieAuth,
    ) : ViewModel() {
        val account = cookieAuth.account

        private val _isWorking = MutableStateFlow(false)
        val isWorking: StateFlow<Boolean> = _isWorking.asStateFlow()

        private val _failure = MutableStateFlow<CookieImportFailure?>(null)
        val failure: StateFlow<CookieImportFailure?> = _failure.asStateFlow()

        init {
            viewModelScope.launch {
                try {
                    cookieAuth.restore()
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    _failure.value = CookieImportFailure.STORAGE
                }
            }
        }

        fun importText(input: String) {
            viewModelScope.launch { importInput(input) }
        }

        fun importFile(uri: Uri) {
            viewModelScope.launch {
                _isWorking.value = true
                _failure.value = null
                val input =
                    try {
                        withContext(Dispatchers.IO) {
                            context.contentResolver
                                .openInputStream(uri)
                                ?.bufferedReader()
                                ?.use { it.readText() }
                        }
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (_: Exception) {
                        null
                    }
                if (input == null) {
                    _failure.value = CookieImportFailure.FILE_READ
                    _isWorking.value = false
                } else {
                    importInput(input)
                }
            }
        }

        fun signOut() {
            viewModelScope.launch {
                _isWorking.value = true
                _failure.value = null
                try {
                    cookieAuth.signOut()
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    _failure.value = CookieImportFailure.STORAGE
                } finally {
                    _isWorking.value = false
                }
            }
        }

        fun clearFailure() {
            _failure.value = null
        }

        private suspend fun importInput(input: String) {
            _isWorking.value = true
            _failure.value = null
            try {
                when (val result = cookieAuth.import(input)) {
                    is CookieImportResult.Imported -> Unit
                    is CookieImportResult.Rejected -> _failure.value = result.reason
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                _failure.value = CookieImportFailure.STORAGE
            } finally {
                _isWorking.value = false
            }
        }
    }
