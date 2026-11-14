package com.myhealth.app.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.myhealth.app.AppViewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { AppTheme { AppRoot() } }
    }
}

private val TABS = listOf("☀️" to "오늘", "🌙" to "수면", "📊" to "분석", "⚖️" to "체중", "⚙️" to "설정")

@Composable
fun AppRoot(vm: AppViewModel = viewModel(), startTab: Int = 0) {
    var tab by rememberSaveable { mutableIntStateOf(startTab) }
    val snack = remember { SnackbarHostState() }
    val message by vm.message.collectAsStateWithLifecycle()

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { vm.onResume() }
    LaunchedEffect(message) {
        message?.let { snack.showSnackbar(it); vm.message.value = null }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snack) },
        bottomBar = {
            NavigationBar {
                TABS.forEachIndexed { i, (icon, label) ->
                    NavigationBarItem(
                        selected = tab == i, onClick = { tab = i },
                        icon = { Text(icon, fontSize = 20.sp) }, label = { Text(label) },
                    )
                }
            }
        },
    ) { pad ->
        Box(Modifier.fillMaxSize().padding(pad)) {
            when (tab) {
                0 -> TodayScreen(vm, onTab = { tab = it })
                1 -> SleepLogScreen(vm)
                2 -> AnalysisScreen(vm)
                3 -> WeightScreen(vm)
                else -> SettingsScreen(vm)
            }
        }
    }
}
