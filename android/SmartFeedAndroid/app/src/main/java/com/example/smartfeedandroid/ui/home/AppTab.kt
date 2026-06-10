package com.example.smartfeedandroid.ui.home

import androidx.annotation.StringRes
import com.example.smartfeedandroid.R

enum class AppTab(@StringRes val labelRes: Int) {
    Home(R.string.tab_home),
    Analysis(R.string.tab_analysis),
    Profile(R.string.tab_profile)
}
