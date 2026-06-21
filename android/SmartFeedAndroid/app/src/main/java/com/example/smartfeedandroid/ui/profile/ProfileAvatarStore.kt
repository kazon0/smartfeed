package com.example.smartfeedandroid.ui.profile

import android.content.Context

internal class ProfileAvatarStore(context: Context) {
    private val preferences = context.getSharedPreferences(
        "smartfeed_profile_avatars",
        Context.MODE_PRIVATE
    )

    fun load(userId: String): String? {
        return preferences.getString(key(userId), null)?.takeIf { it.isNotBlank() }
    }

    fun save(userId: String, uri: String) {
        preferences.edit().putString(key(userId), uri).apply()
    }

    fun clear(userId: String) {
        preferences.edit().remove(key(userId)).apply()
    }

    private fun key(userId: String): String = "avatar_$userId"
}
