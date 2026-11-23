package com.myuntis.app

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.myuntis.app.data.local.DataStoreManager
import com.myuntis.app.data.notifications.AppForeground
import com.myuntis.app.data.notifications.NavRequests
import com.myuntis.app.ui.navigation.AppNavGraph
import com.myuntis.app.ui.theme.MyUntisTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var dataStore: DataStoreManager

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        handleIntent(intent)

        setContent {
            val isDarkMode by dataStore.isDarkMode.collectAsState(initial = false)

            MyUntisTheme(darkTheme = isDarkMode) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color    = MaterialTheme.colorScheme.background
                ) {
                    AppNavGraph()
                }
            }
        }
    }

    override fun onStart() {
        super.onStart();
        AppForeground.isForeground = true
    }

    override fun onStop()  {
        super.onStop();
        AppForeground.isForeground = false
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        intent?.getStringExtra(NavRequests.EXTRA_ROUTE)?.let {
            NavRequests.request(it)
            intent.removeExtra(NavRequests.EXTRA_ROUTE)
        }
    }
}