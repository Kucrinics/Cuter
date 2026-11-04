package com.cuter

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.lifecycle.viewmodel.compose.viewModel
import com.cuter.ui.editor.EditorScreen
import com.cuter.ui.home.HomeScreen
import com.cuter.ui.home.HomeViewModel
import com.cuter.ui.theme.CuterTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            CuterTheme {
                CuterRoot()
            }
        }
    }
}

@Composable
private fun CuterRoot() {
    var projectId by rememberSaveable { mutableStateOf("") }

    if (projectId.isBlank()) {
        HomeScreen(
            vm = viewModel<HomeViewModel>(),
            onOpenProject = { projectId = it },
        )
    } else {
        EditorScreen(
            projectId = projectId,
            onBack = { projectId = "" },
        )
    }
}
