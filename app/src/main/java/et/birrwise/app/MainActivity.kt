package et.birrwise.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import et.birrwise.app.ui.BirrWiseApp
import et.birrwise.app.ui.theme.BirrWiseTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            BirrWiseTheme {
                BirrWiseApp()
            }
        }
    }
}
