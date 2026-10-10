package io.github.overcuriousity.peephole

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import io.github.overcuriousity.peephole.ui.PeepholeApp
import io.github.overcuriousity.peephole.ui.PeepholeTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val app = application as PeepholeApplication
        setContent {
            PeepholeTheme {
                PeepholeApp(app)
            }
        }
    }
}
