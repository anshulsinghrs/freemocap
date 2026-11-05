package com.physioar.app

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.physioar.app.session.SessionViewModel
import com.physioar.app.ui.screens.ExerciseLibraryScreen
import com.physioar.app.ui.screens.ResultsScreen
import com.physioar.app.ui.screens.SessionScreen
import com.physioar.app.ui.theme.PhysioArTheme
import com.physioar.engine.exercise.SessionSummary
import com.physioar.engine.exercises.kneeextension.KneeExtensionParameters

private sealed interface Screen {
    data object Library : Screen
    data class Session(val params: KneeExtensionParameters, val front: Boolean) : Screen
    data class Results(val summary: SessionSummary) : Screen
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            PhysioArTheme {
                Surface(Modifier.fillMaxSize()) { PhysioArApp() }
            }
        }
    }
}

@Composable
private fun PhysioArApp() {
    val vm: SessionViewModel = viewModel()
    var screen by remember { mutableStateOf<Screen>(Screen.Library) }

    when (val s = screen) {
        Screen.Library -> ExerciseLibraryScreen(onStart = { p, front -> screen = Screen.Session(p, front) })
        is Screen.Session -> CameraPermissionGate(onDenied = { screen = Screen.Library }) {
            BackHandler { vm.reset(); screen = Screen.Library }
            SessionScreen(vm, s.params, s.front, onFinished = { summary ->
                vm.reset()
                screen = Screen.Results(summary)
            })
        }
        is Screen.Results -> ResultsScreen(s.summary, onDone = { screen = Screen.Library })
    }
}

@Composable
private fun CameraPermissionGate(onDenied: () -> Unit, content: @Composable () -> Unit) {
    val context = LocalContext.current
    var granted by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED)
    }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted = it }
    if (granted) {
        content()
    } else {
        Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("PhysioAR needs the camera to see your movement. Video is processed on this phone only and is never recorded or uploaded.")
            Button(onClick = { launcher.launch(Manifest.permission.CAMERA) }) { Text("Allow camera") }
            Button(onClick = onDenied) { Text("Back") }
        }
    }
}
