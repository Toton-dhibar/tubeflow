package io.github.aedev.flow.data.local

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.aedev.flow.innertube.YouTube
import io.github.aedev.flow.innertube.models.AccountInfo
import io.github.aedev.flow.innertube.utils.CookieParseResult
import io.github.aedev.flow.innertube.utils.YouTubeCookieParser
import io.ktor.client.plugins.ClientRequestException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

internal sealed interface CookieImportResult {
    data class Imported(val account: AccountInfo) : CookieImportResult

    data class Rejected(val reason: CookieImportFailure) : CookieImportResult
}

internal enum class CookieImportFailure {
    EMPTY,
    INVALID_FORMAT,
    MISSING_REQUIRED_COOKIES,
    REQUIRED_COOKIE_EXPIRED,
    COOKIES_EXPIRED,
    NETWORK,
    STORAGE,
    FILE_READ,
}

@Singleton
class YouTubeCookieAuth
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
    ) {
        private val prefsFile = File(context.applicationInfo.dataDir, "shared_prefs/$PREFERENCES_FILE.xml")
        private val _account = MutableStateFlow<AccountInfo?>(null)
        val account: StateFlow<AccountInfo?> = _account.asStateFlow()
        private val restoreMutex = Mutex()
        private var restored = false

        suspend fun restore() {
            restoreMutex.withLock {
                if (restored) return@withLock
                withContext(Dispatchers.IO) {
                    if (prefsFile.exists()) {
                        val prefs = encryptedPreferences()
                        val cookie = prefs.getString(KEY_COOKIE, null)
                        if (!cookie.isNullOrBlank()) {
                            YouTube.cookie = cookie
                            _account.value = readAccount(prefs)
                        }
                    }
                }
                restored = true
            }
        }

        internal suspend fun import(input: String): CookieImportResult {
            restore()
            val parsed =
                when (val result = YouTubeCookieParser.parse(input)) {
                    is CookieParseResult.Valid -> result
                    CookieParseResult.Empty -> return CookieImportResult.Rejected(CookieImportFailure.EMPTY)
                    CookieParseResult.InvalidFormat -> return CookieImportResult.Rejected(CookieImportFailure.INVALID_FORMAT)
                    CookieParseResult.MissingRequiredCookies -> return CookieImportResult.Rejected(CookieImportFailure.MISSING_REQUIRED_COOKIES)
                    CookieParseResult.RequiredCookieExpired -> return CookieImportResult.Rejected(CookieImportFailure.REQUIRED_COOKIE_EXPIRED)
                }

            val previousCookie = YouTube.cookie
            val previousAccount = _account.value
            YouTube.cookie = parsed.cookieHeader
            val account =
                try {
                    withContext(Dispatchers.IO) { YouTube.accountInfo().getOrThrow() }
                } catch (cancelled: CancellationException) {
                    YouTube.cookie = previousCookie
                    throw cancelled
                } catch (error: Exception) {
                    YouTube.cookie = previousCookie
                    val failure =
                        if (error.isCookieRejection()) {
                            CookieImportFailure.COOKIES_EXPIRED
                        } else {
                            CookieImportFailure.NETWORK
                        }
                    return CookieImportResult.Rejected(failure)
                }

            try {
                withContext(Dispatchers.IO) {
                    check(
                        encryptedPreferences()
                            .edit()
                            .putString(KEY_COOKIE, parsed.cookieHeader)
                            .putString(KEY_NAME, account.name)
                            .putString(KEY_EMAIL, account.email)
                            .putString(KEY_CHANNEL_HANDLE, account.channelHandle)
                            .putString(KEY_AVATAR, account.thumbnailUrl)
                            .commit(),
                    )
                }
            } catch (cancelled: CancellationException) {
                YouTube.cookie = previousCookie
                _account.value = previousAccount
                throw cancelled
            } catch (error: Exception) {
                YouTube.cookie = previousCookie
                _account.value = previousAccount
                return CookieImportResult.Rejected(CookieImportFailure.STORAGE)
            }
            _account.value = account
            restored = true
            return CookieImportResult.Imported(account)
        }

        suspend fun signOut() {
            restore()
            withContext(Dispatchers.IO) {
                if (prefsFile.exists()) check(encryptedPreferences().edit().clear().commit())
            }
            YouTube.cookie = null
            _account.value = null
            restored = true
        }

        private fun encryptedPreferences() =
            EncryptedSharedPreferences.create(
                context,
                PREFERENCES_FILE,
                MasterKey
                    .Builder(context)
                    .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                    .build(),
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
            )

        private fun readAccount(prefs: android.content.SharedPreferences): AccountInfo? {
            val name = prefs.getString(KEY_NAME, null)?.takeIf(String::isNotBlank) ?: return null
            return AccountInfo(
                name = name,
                email = prefs.getString(KEY_EMAIL, null),
                channelHandle = prefs.getString(KEY_CHANNEL_HANDLE, null),
                thumbnailUrl = prefs.getString(KEY_AVATAR, null),
            )
        }

        private fun Throwable.isCookieRejection(): Boolean =
            generateSequence(this) { it.cause }.any { error ->
                error is ClientRequestException && error.response.status.value in setOf(401, 403)
                    || error is NullPointerException
            }

        private companion object {
            const val PREFERENCES_FILE = "youtube_cookie_auth"
            const val KEY_COOKIE = "cookie"
            const val KEY_NAME = "account_name"
            const val KEY_EMAIL = "account_email"
            const val KEY_CHANNEL_HANDLE = "account_channel_handle"
            const val KEY_AVATAR = "account_avatar"
        }
    }
