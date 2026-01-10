package com.ai.assistance.operit.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.wakeWordPreferencesDataStore: DataStore<Preferences> by
    preferencesDataStore(name = "wake_word_preferences")

class WakeWordPreferences(private val context: Context) {

    private val dataStore = context.wakeWordPreferencesDataStore

    companion object {
        private val KEY_ALWAYS_LISTENING_ENABLED = booleanPreferencesKey("always_listening_enabled")
        private val KEY_WAKE_PHRASE = stringPreferencesKey("wake_phrase")
        private val KEY_WAKE_PHRASE_REGEX_ENABLED = booleanPreferencesKey("wake_phrase_regex_enabled")
        private val KEY_VOICE_CALL_INACTIVITY_TIMEOUT_SECONDS =
            intPreferencesKey("voice_call_inactivity_timeout_seconds")
        private val KEY_WAKE_GREETING_ENABLED = booleanPreferencesKey("wake_greeting_enabled")
        private val KEY_WAKE_GREETING_TEXT = stringPreferencesKey("wake_greeting_text")

        private val KEY_VOICE_AUTO_ATTACH_ENABLED = booleanPreferencesKey("voice_auto_attach_enabled")
        private val KEY_VOICE_AUTO_ATTACH_SCREEN_ENABLED = booleanPreferencesKey("voice_auto_attach_screen_enabled")
        private val KEY_VOICE_AUTO_ATTACH_NOTIFICATIONS_ENABLED =
            booleanPreferencesKey("voice_auto_attach_notifications_enabled")
        private val KEY_VOICE_AUTO_ATTACH_SCREEN_KEYWORD = stringPreferencesKey("voice_auto_attach_screen_keyword")
        private val KEY_VOICE_AUTO_ATTACH_NOTIFICATIONS_KEYWORD =
            stringPreferencesKey("voice_auto_attach_notifications_keyword")

        const val DEFAULT_WAKE_PHRASE = "小欧"
        const val DEFAULT_WAKE_PHRASE_REGEX_ENABLED = false
        const val DEFAULT_ALWAYS_LISTENING_ENABLED = false
        const val DEFAULT_VOICE_CALL_INACTIVITY_TIMEOUT_SECONDS = 15
        const val DEFAULT_WAKE_GREETING_ENABLED = true
        const val DEFAULT_WAKE_GREETING_TEXT = "我在"

        const val DEFAULT_VOICE_AUTO_ATTACH_ENABLED = true
        const val DEFAULT_VOICE_AUTO_ATTACH_SCREEN_ENABLED = true
        const val DEFAULT_VOICE_AUTO_ATTACH_NOTIFICATIONS_ENABLED = true
        const val DEFAULT_VOICE_AUTO_ATTACH_SCREEN_KEYWORD = "屏幕"
        const val DEFAULT_VOICE_AUTO_ATTACH_NOTIFICATIONS_KEYWORD = "通知"
    }

    val alwaysListeningEnabledFlow: Flow<Boolean> =
        dataStore.data.map { prefs ->
            prefs[KEY_ALWAYS_LISTENING_ENABLED] ?: DEFAULT_ALWAYS_LISTENING_ENABLED
        }

    val wakePhraseFlow: Flow<String> =
        dataStore.data.map { prefs ->
            prefs[KEY_WAKE_PHRASE] ?: DEFAULT_WAKE_PHRASE
        }

    val wakePhraseRegexEnabledFlow: Flow<Boolean> =
        dataStore.data.map { prefs ->
            prefs[KEY_WAKE_PHRASE_REGEX_ENABLED] ?: DEFAULT_WAKE_PHRASE_REGEX_ENABLED
        }

    val voiceCallInactivityTimeoutSecondsFlow: Flow<Int> =
        dataStore.data.map { prefs ->
            prefs[KEY_VOICE_CALL_INACTIVITY_TIMEOUT_SECONDS]
                ?: DEFAULT_VOICE_CALL_INACTIVITY_TIMEOUT_SECONDS
        }

    val wakeGreetingEnabledFlow: Flow<Boolean> =
        dataStore.data.map { prefs ->
            prefs[KEY_WAKE_GREETING_ENABLED] ?: DEFAULT_WAKE_GREETING_ENABLED
        }

    val wakeGreetingTextFlow: Flow<String> =
        dataStore.data.map { prefs ->
            prefs[KEY_WAKE_GREETING_TEXT] ?: DEFAULT_WAKE_GREETING_TEXT
        }

    val voiceAutoAttachEnabledFlow: Flow<Boolean> =
        dataStore.data.map { prefs ->
            prefs[KEY_VOICE_AUTO_ATTACH_ENABLED] ?: DEFAULT_VOICE_AUTO_ATTACH_ENABLED
        }

    val voiceAutoAttachScreenEnabledFlow: Flow<Boolean> =
        dataStore.data.map { prefs ->
            prefs[KEY_VOICE_AUTO_ATTACH_SCREEN_ENABLED] ?: DEFAULT_VOICE_AUTO_ATTACH_SCREEN_ENABLED
        }

    val voiceAutoAttachNotificationsEnabledFlow: Flow<Boolean> =
        dataStore.data.map { prefs ->
            prefs[KEY_VOICE_AUTO_ATTACH_NOTIFICATIONS_ENABLED]
                ?: DEFAULT_VOICE_AUTO_ATTACH_NOTIFICATIONS_ENABLED
        }

    val voiceAutoAttachScreenKeywordFlow: Flow<String> =
        dataStore.data.map { prefs ->
            prefs[KEY_VOICE_AUTO_ATTACH_SCREEN_KEYWORD] ?: DEFAULT_VOICE_AUTO_ATTACH_SCREEN_KEYWORD
        }

    val voiceAutoAttachNotificationsKeywordFlow: Flow<String> =
        dataStore.data.map { prefs ->
            prefs[KEY_VOICE_AUTO_ATTACH_NOTIFICATIONS_KEYWORD]
                ?: DEFAULT_VOICE_AUTO_ATTACH_NOTIFICATIONS_KEYWORD
        }

    suspend fun saveAlwaysListeningEnabled(enabled: Boolean) {
        dataStore.edit { prefs ->
            prefs[KEY_ALWAYS_LISTENING_ENABLED] = enabled
        }
    }

    suspend fun saveWakePhrase(phrase: String) {
        dataStore.edit { prefs ->
            prefs[KEY_WAKE_PHRASE] = phrase
        }
    }

    suspend fun saveWakePhraseRegexEnabled(enabled: Boolean) {
        dataStore.edit { prefs ->
            prefs[KEY_WAKE_PHRASE_REGEX_ENABLED] = enabled
        }
    }

    suspend fun saveVoiceCallInactivityTimeoutSeconds(seconds: Int) {
        dataStore.edit { prefs ->
            prefs[KEY_VOICE_CALL_INACTIVITY_TIMEOUT_SECONDS] = seconds
        }
    }

    suspend fun saveWakeGreetingEnabled(enabled: Boolean) {
        dataStore.edit { prefs ->
            prefs[KEY_WAKE_GREETING_ENABLED] = enabled
        }
    }

    suspend fun saveWakeGreetingText(text: String) {
        dataStore.edit { prefs ->
            prefs[KEY_WAKE_GREETING_TEXT] = text
        }
    }

    suspend fun saveVoiceAutoAttachEnabled(enabled: Boolean) {
        dataStore.edit { prefs ->
            prefs[KEY_VOICE_AUTO_ATTACH_ENABLED] = enabled
        }
    }

    suspend fun saveVoiceAutoAttachScreenEnabled(enabled: Boolean) {
        dataStore.edit { prefs ->
            prefs[KEY_VOICE_AUTO_ATTACH_SCREEN_ENABLED] = enabled
        }
    }

    suspend fun saveVoiceAutoAttachNotificationsEnabled(enabled: Boolean) {
        dataStore.edit { prefs ->
            prefs[KEY_VOICE_AUTO_ATTACH_NOTIFICATIONS_ENABLED] = enabled
        }
    }

    suspend fun saveVoiceAutoAttachScreenKeyword(keyword: String) {
        dataStore.edit { prefs ->
            prefs[KEY_VOICE_AUTO_ATTACH_SCREEN_KEYWORD] = keyword
        }
    }

    suspend fun saveVoiceAutoAttachNotificationsKeyword(keyword: String) {
        dataStore.edit { prefs ->
            prefs[KEY_VOICE_AUTO_ATTACH_NOTIFICATIONS_KEYWORD] = keyword
        }
    }
}
