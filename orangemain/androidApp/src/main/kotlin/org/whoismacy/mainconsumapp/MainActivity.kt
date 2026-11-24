package org.whoismacy.mainconsumapp

import android.content.ClipboardManager
import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions
import org.koin.android.ext.koin.androidContext
import org.whoismacy.mainconsumapp.di.initKoin
import org.whoismacy.mainconsumapp.ui.components.ClipboardReader
import org.whoismacy.mainconsumapp.ui.components.LocalClipboardReader
import org.whoismacy.mainconsumapp.ui.components.LocalQrScanner
import org.whoismacy.mainconsumapp.ui.components.QrScanner

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        initKoin {
            androidContext(applicationContext)
        }

        setContent {
            val clipboardReader = remember {
                ClipboardReader {
                    val manager = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    val clip = manager.primaryClip
                    if (clip == null || clip.itemCount == 0) {
                        null
                    } else {
                        clip.getItemAt(0).coerceToText(this).toString().trim().ifEmpty { null }
                    }
                }
            }
            var pendingScan by remember { mutableStateOf<((String) -> Unit)?>(null) }
            val scanLauncher = rememberLauncherForActivityResult(ScanContract()) { result ->
                val handler = pendingScan
                pendingScan = null
                if (handler != null && !result.contents.isNullOrBlank()) handler(result.contents!!)
            }
            val qrScanner = remember(scanLauncher) {
                QrScanner { onResult ->
                    pendingScan = onResult
                    scanLauncher.launch(
                        ScanOptions()
                            .setDesiredBarcodeFormats(ScanOptions.QR_CODE)
                            .setPrompt("Scan the device QR code from the admin app")
                            .setBeepEnabled(false)
                            .setOrientationLocked(false)
                    )
                }
            }
            CompositionLocalProvider(
                LocalQrScanner provides qrScanner,
                LocalClipboardReader provides clipboardReader,
            ) {
                App()
            }
        }
    }
}
