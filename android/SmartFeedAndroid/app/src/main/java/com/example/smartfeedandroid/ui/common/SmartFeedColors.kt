package com.example.smartfeedandroid.ui.common

import androidx.compose.ui.graphics.Color

val AppBackground = Color.White
val SoftBlue = Color(0xFF8FAADC)
val SoftBlueLight = Color(0xFFE7EEF8)
val SoftGreen = Color(0xFFA8C7A3)
val SoftRed = Color(0xFFC77878)
val SoftRedLight = Color(0xFFF4E4E2)

fun topicColor(index: Int): Color {
    val colors = listOf(
        Color(0xFF8FAADC),
        Color(0xFFA8C7A3),
        Color(0xFFE6B89C),
        Color(0xFFC4B5D9),
        Color(0xFFE3A6A1),
        Color(0xFF9BC7C5),
        Color(0xFFD7C49E),
        Color(0xFFAEB7C2)
    )
    return colors[index % colors.size]
}
