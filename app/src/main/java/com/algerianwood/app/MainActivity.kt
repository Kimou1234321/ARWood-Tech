package com.algerianwood.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.algerianwood.app.navigation.AppNavGraph
import com.algerianwood.app.ui.theme.ARWoodTechTheme
import com.algerianwood.app.ui.theme.AppThemeMode
import com.algerianwood.app.ui.theme.ThemePreferences

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val themePrefs = ThemePreferences(this)
        setContent {
            var themeMode by rememberSaveable { mutableStateOf(themePrefs.load()) }
            ARWoodTechTheme(mode = themeMode) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    AppNavGraph(
                        themeMode = themeMode,
                        onToggleTheme = {
                            themeMode = themeMode.toggled()
                            themePrefs.save(themeMode)
                        }
                    )
                }
            }
        }
    }
}
