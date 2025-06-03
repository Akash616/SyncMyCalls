package io.logger.utility

import android.app.Activity
import android.view.Window
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import io.logger.R

object Utils {

    fun setStatusBarColor(activity: Activity, isDarkTheme: Boolean) {
        val window: Window = activity.window

        @Suppress("DEPRECATION")
        window.statusBarColor = if (isDarkTheme) { //status bar background color
            ContextCompat.getColor(activity, R.color.black_light)
        } else {
            ContextCompat.getColor(activity, R.color.status_bar_color)
        }

        // status bar icon/text color
        val insetsController = WindowCompat.getInsetsController(window, window.decorView)
        insetsController?.isAppearanceLightStatusBars = !isDarkTheme
    }
}