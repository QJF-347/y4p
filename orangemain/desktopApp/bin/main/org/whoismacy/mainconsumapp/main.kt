package org.whoismacy.mainconsumapp

import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import org.whoismacy.mainconsumapp.di.initKoin

fun main() {
    initKoin()

    application {
        Window(
            onCloseRequest = ::exitApplication,
            title = "🍊 OrangeMain POS",
        ) {
            App()
        }
    }
}
