package com.vibecheck

import androidx.compose.ui.window.ComposeUIViewController
import platform.UIKit.UIViewController
import com.vibecheck.ui.App

fun MainViewController(): UIViewController = ComposeUIViewController {
    App()
}
