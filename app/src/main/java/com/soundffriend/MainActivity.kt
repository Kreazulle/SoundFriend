package com.soundffriend

import android.content.Context
import android.os.Build
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.items
import androidx.wear.compose.material.*
import androidx.wear.compose.navigation.SwipeDismissableNavHost
import androidx.wear.compose.navigation.composable
import androidx.wear.compose.navigation.rememberSwipeDismissableNavController
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.GenericShape
import androidx.wear.compose.foundation.CurvedLayout
import androidx.wear.compose.foundation.CurvedTextStyle
import androidx.wear.compose.foundation.CurvedDirection
import androidx.wear.compose.material.curvedText


class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        setContent {
            SoundFriendApp()
        }
    }
}

@Composable
fun SoundFriendApp(viewModel: WingViewModel = viewModel()) {
    val navController = rememberSwipeDismissableNavController()
    val alertMessage by viewModel.alertMessage.collectAsState()
    val selectedMixer by viewModel.selectedMixer.collectAsState()
    val discoveredMixers by viewModel.discoveredMixers.collectAsState()
    
    // Trigger vibration when alert arrives
    val context = androidx.compose.ui.platform.LocalContext.current
    LaunchedEffect(alertMessage) {
        if (alertMessage != null) {
            vibrate(context)
        }
    }

    // Automatically navigate to Main when a mixer is selected
    LaunchedEffect(selectedMixer) {
        if (selectedMixer != null) {
            navController.navigate("main") {
                popUpTo("settings") { inclusive = true }
            }
        }
    }

    val currentBackStackEntry by navController.currentBackStackEntryAsState()
    val isHelpScreen = currentBackStackEntry?.destination?.route == "help"

    MaterialTheme {
        Scaffold(
            timeText = {
                Box(modifier = Modifier.fillMaxSize()) {
                    if (isHelpScreen) {
                        // Black masks under the curved texts (Segment-cut style) - Only on Help Screen
                        Canvas(modifier = Modifier.fillMaxSize()) {
                            // Top Segment Mask (under clock) - Slightly larger (+10px/degrees equivalent)
                            drawArc(
                                color = Color.Black,
                                startAngle = 220f,
                                sweepAngle = 100f,
                                useCenter = false
                            )
                            // Bottom Segment Mask (under mixer info) - Slightly larger (+10px/degrees equivalent)
                            drawArc(
                                color = Color.Black,
                                startAngle = 40f,
                                sweepAngle = 100f,
                                useCenter = false
                            )
                        }
                    }

                    TimeText()
                    val bottomText = when {
                        selectedMixer != null -> selectedMixer!!.name
                        discoveredMixers.isEmpty() -> "No mixers found"
                        else -> null
                    }
                    
                    bottomText?.let { text ->
                        CurvedLayout(anchor = 90f, angularDirection = CurvedDirection.Angular.CounterClockwise) {
                            curvedText(
                                text = text,
                                style = CurvedTextStyle(
                                    fontSize = 12.sp,
                                    color = Color.Gray.copy(alpha = 0.6f)
                                )
                            )
                        }
                    }
                }
            }
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                SwipeDismissableNavHost(
                    navController = navController,
                    startDestination = if (selectedMixer == null) "settings" else "main"
                ) {
                    composable("main") {
                        MainScreen(
                            viewModel = viewModel,
                            onSettingsClick = { navController.navigate("settings") }
                        )
                    }
                    composable("settings") {
                        SettingsScreen(
                            viewModel = viewModel,
                            onBack = { 
                                // Reset selection if we go back without a valid mixer
                                if (selectedMixer == null) {
                                    // Handle no mixer state if needed
                                }
                                navController.popBackStack() 
                            },
                            onHelpClick = { navController.navigate("help") }
                        )
                    }
                    composable("help") {
                        HelpScreen(
                            viewModel = viewModel,
                            onBack = { navController.popBackStack() }
                        )
                    }
                }

                // Alert Overlay
                alertMessage?.let { message ->
                    LaunchedEffect(message) {
                        val pulseDuration = 1000L // 60 BPM = 1 pulse per second
                        while (true) {
                            vibrate(context, 200) // Pulse vibration
                            delay(pulseDuration)
                        }
                    }

                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Red.copy(alpha = 0.9f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = "HELP NEEDED:",
                                color = Color.White,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                textAlign = TextAlign.Center
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = message,
                                color = Color.White,
                                fontSize = 24.sp,
                                fontWeight = FontWeight.ExtraBold,
                                textAlign = TextAlign.Center
                            )
                            Spacer(modifier = Modifier.height(26.dp))
                            Button(
                                onClick = { viewModel.dismissAlert() },
                                shape = CircleShape,
                                colors = ButtonDefaults.buttonColors(backgroundColor = Color.White, contentColor = Color.Red),
                                modifier = Modifier.size(56.dp)
                            ) {
                                Text("OK", fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun MainScreen(viewModel: WingViewModel, onSettingsClick: () -> Unit) {
    val bpm by viewModel.bpm.collectAsState()
    val selectedMixer by viewModel.selectedMixer.collectAsState()

    // Animation for continuous fade-to-black
    var alpha by remember { mutableStateOf(1f) }
    
    // Use withFrameMillis for smooth, frame-synced animation
    LaunchedEffect(bpm) {
        val pulseDuration = (60000 / bpm.coerceAtLeast(1f)).toLong()
        while (true) {
            val startTime = withFrameMillis { it }
            var elapsed: Long
            do {
                elapsed = withFrameMillis { it } - startTime
                alpha = (1f - (elapsed.toFloat() / pulseDuration)).coerceIn(0f, 1f)
            } while (elapsed < pulseDuration)
            alpha = 0f
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = { viewModel.tapTempo() },
                    onLongPress = { 
                        viewModel.selectMixer(null) // Reset mixer to allow re-entry
                        onSettingsClick() 
                    }
                )
            },
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.graphicsLayer(alpha = alpha)
            ) {
                Text(
                    text = "${bpm.toInt()}",
                    fontSize = 48.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = Color.Red
                )
                Text(
                    text = "BPM",
                    fontSize = 16.sp,
                    color = Color.Red
                )
            }
        }
    }
}

@Composable
fun SettingsScreen(viewModel: WingViewModel, onBack: () -> Unit, onHelpClick: () -> Unit) {
    val mixers by viewModel.discoveredMixers.collectAsState()

    ScalingLazyColumn(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterVertically)
    ) {
        item {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(text = "Select Mixer", modifier = Modifier.padding(bottom = 8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = { viewModel.startDiscovery() },
                        shape = CircleShape,
                        colors = ButtonDefaults.buttonColors(backgroundColor = Color.Red, contentColor = Color.White),
                        modifier = Modifier.size(44.dp)
                    ) {
                        Text("Scan", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                    Button(
                        onClick = { 
                            viewModel.selectMixer(WingMixer("No Mixer", "0.0.0.0"))
                        },
                        shape = CircleShape,
                        colors = ButtonDefaults.buttonColors(backgroundColor = Color.Yellow, contentColor = Color.Black),
                        modifier = Modifier.size(44.dp)
                    ) {
                        Text(
                            text = "No\nMixer", 
                            fontSize = 9.sp, 
                            lineHeight = 10.sp,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center
                        )
                    }
                    Button(
                        onClick = onHelpClick,
                        shape = CircleShape,
                        colors = ButtonDefaults.buttonColors(backgroundColor = Color.Blue, contentColor = Color.White),
                        modifier = Modifier.size(44.dp)
                    ) {
                        Text("?", fontSize = 12.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
                    }
                }
            }
        }
        items(mixers) { mixer ->
            Chip(
                onClick = {
                    viewModel.selectMixer(mixer)
                    onBack()
                },
                label = { Text(mixer.name) },
                secondaryLabel = { Text(mixer.ip) },
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
fun HelpScreen(viewModel: WingViewModel, onBack: () -> Unit) {
    val deviceIp by viewModel.deviceIp.collectAsState()

    ScalingLazyColumn(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        item {
            Text(
                text = "Network Help", 
                fontWeight = FontWeight.Bold, 
                modifier = Modifier.padding(bottom = 8.dp),
                textAlign = TextAlign.Center
            )
        }
        item {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.fillMaxWidth(0.85f)
            ) {
                Text(text = "Watch IP:", fontSize = 12.sp, color = Color.LightGray)
                Text(text = deviceIp, fontSize = 16.sp, color = MaterialTheme.colors.primary, textAlign = TextAlign.Center)
            }
        }
        item {
            Spacer(modifier = Modifier.height(8.dp))
        }
        item {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.fillMaxWidth(0.85f)
            ) {
                Text(text = "OSC (WING):", fontSize = 12.sp, color = Color.LightGray)
                Text(text = "Port 10023", fontSize = 14.sp)
            }
        }
        item {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.fillMaxWidth(0.85f)
            ) {
                Text(text = "Alerts (UDP):", fontSize = 12.sp, color = Color.LightGray)
                Text(text = "Port 5005", fontSize = 14.sp)
            }
        }
        item {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.fillMaxWidth(0.85f)
            ) {
                Text(text = "Alerts (OSC):", fontSize = 12.sp, color = Color.LightGray)
                Text(text = "Port 5006", fontSize = 14.sp)
            }
        }
        item {
            Spacer(modifier = Modifier.height(12.dp))
        }
        item {
            Text(text = "Examples:", fontWeight = FontWeight.Bold, fontSize = 12.sp)
        }
        item {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.fillMaxWidth(0.85f)
            ) {
                Text(text = "Trigger Alert (UDP):", fontSize = 12.sp, color = Color.LightGray)
                Text(
                    text = "echo \"Drums Help\" > /dev/udp/[IP]/5005", 
                    fontSize = 10.sp, 
                    color = Color.Cyan,
                    textAlign = TextAlign.Center
                )
            }
        }
        item {
            Spacer(modifier = Modifier.height(8.dp))
        }
        item {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.fillMaxWidth(0.85f)
            ) {
                Text(text = "Trigger Alert (OSC):", fontSize = 12.sp, color = Color.LightGray)
                Text(text = "Port: 5006", fontSize = 10.sp, color = Color.Cyan)
                Text(text = "Address: /alert", fontSize = 10.sp, color = Color.Cyan)
                Text(text = "Value: \"Keys Help\"", fontSize = 10.sp, color = Color.Cyan, textAlign = TextAlign.Center)
            }
        }
        item {
            Spacer(modifier = Modifier.height(12.dp))
        }
        item {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.fillMaxWidth(0.85f)
            ) {
                Text(text = "Credits", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                Text(text = "© MULTIGRAMM Technology", fontSize = 10.sp, color = Color.Gray, textAlign = TextAlign.Center)
                Text(text = "Developer: Daniel Meșteru", fontSize = 10.sp, color = Color.Gray, textAlign = TextAlign.Center)
            }
        }
        item {
            Spacer(modifier = Modifier.height(12.dp))
        }
        item {
            Button(onClick = onBack) {
                Text("Back")
            }
        }
    }
}

fun vibrate(context: Context, durationMillis: Long = 500) {
    val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
        vibratorManager.defaultVibrator
    } else {
        @Suppress("DEPRECATION")
        context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
    }

    vibrator.vibrate(VibrationEffect.createOneShot(durationMillis, VibrationEffect.DEFAULT_AMPLITUDE))
}
