package com.example.smartfeedandroid.ui.profile

import org.junit.Assert.assertEquals
import org.junit.Test

class ProfileFormattingTest {
    @Test
    fun avatarInitialPrefersDisplayNameThenEmail() {
        assertEquals("J", profileInitial("Journal Reader", "reader@example.com"))
        assertEquals("R", profileInitial("", "reader@example.com"))
        assertEquals("S", profileInitial("", ""))
    }

    @Test
    fun profileDateUsesIsoCalendarDate() {
        assertEquals("2026-06-22", profileDate("2026-06-22T03:00:00Z"))
        assertEquals("-", profileDate(""))
    }
}
