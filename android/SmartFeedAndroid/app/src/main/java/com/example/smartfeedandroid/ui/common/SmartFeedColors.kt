package com.example.smartfeedandroid.ui.common

import androidx.compose.ui.graphics.Color

val AppBackground = Color.White
val SoftBlue = Color(0xFF8FAADC)
val SoftBlueLight = Color(0xFFE7EEF8)
val SoftGreen = Color(0xFFA8C7A3)
val SoftRed = Color(0xFFE6AAA6)
val SoftRedLight = Color(0xFFF4E4E2)

val JournalPaper = Color(0xFFF9F8F4)
val JournalPaperDeep = Color(0xFFF1ECE2)
val JournalInk = Color(0xFF4A443F)
val JournalInkLight = Color(0xFF8A827B)
val JournalLine = Color(0xFFE5DED4)
val JournalGreen = Color(0xFFC1D5C9)
val JournalBlue = Color(0xFFB6C7D6)
val JournalPink = Color(0xFFE3C9D3)
val JournalYellow = Color(0xFFEFE2B9)
val JournalTerra = Color(0xFFDE9B8B)

fun topicColor(index: Int): Color {
    val colors = listOf(
        JournalBlue,
        JournalGreen,
        JournalYellow,
        JournalPink,
        JournalTerra,
        Color(0xFF9BC7C5),
        Color(0xFFD7C49E),
        Color(0xFFAEB7C2)
    )
    return colors[index % colors.size]
}
