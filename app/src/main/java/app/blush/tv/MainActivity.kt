package app.blush.tv

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.remember
import app.blush.tv.ui.AppState
import app.blush.tv.ui.BlushApp

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val repo = (application as BlushApplication).repo
        setContent {
            val app = remember { AppState(repo) }
            BlushApp(app, onExit = { finish() })
        }
    }
}
