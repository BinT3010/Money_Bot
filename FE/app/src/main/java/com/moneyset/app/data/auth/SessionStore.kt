package com.moneyset.app.data.auth

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.moneyset.app.domain.model.UserSession
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map

private val Context.sessionDataStore by preferencesDataStore(name = "session")

/** Lưu phiên đăng nhập (token + thông tin user) để mở app lại không phải đăng nhập. */
@Singleton
class SessionStore @Inject constructor(
    @ApplicationContext context: Context,
) {
    private val dataStore = context.sessionDataStore

    val session: Flow<UserSession?> = dataStore.data
        .catch { e -> if (e is IOException) emit(emptyPreferences()) else throw e }
        .map { prefs ->
            val token = prefs[TOKEN] ?: return@map null
            UserSession(token = token, name = prefs[NAME].orEmpty(), email = prefs[EMAIL].orEmpty())
        }

    suspend fun save(session: UserSession) {
        dataStore.edit {
            it[TOKEN] = session.token
            it[NAME] = session.name
            it[EMAIL] = session.email
        }
    }

    suspend fun clear() {
        dataStore.edit { it.clear() }
    }

    private companion object {
        val TOKEN = stringPreferencesKey("token")
        val NAME = stringPreferencesKey("name")
        val EMAIL = stringPreferencesKey("email")
    }
}
