package ru.muz.desktop.ui

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.window.Window

@Composable
fun AriaWindow(onExit: () -> Unit) {
    Window(onCloseRequest = onExit, title = "Aria") {
        Text("Aria")
    }
}
