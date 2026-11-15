package news.openwire

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import news.openwire.ui.home.HomeScreen
import news.openwire.ui.theme.OpenWireTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            OpenWireTheme {
                HomeScreen()
            }
        }
    }
}
