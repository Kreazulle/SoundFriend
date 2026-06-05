/*
 * SoundFriend - A Wear OS remote for Behringer WING mixers.
 * This file contains the main entry point and UI navigation for the application.
 */
package com.soundffriend

import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.Brush
import android.content.Context
import android.net.wifi.WifiManager
import android.os.Build
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.compose.animation.core.*
import androidx.compose.foundation.Image
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import kotlin.math.abs
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
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
import androidx.wear.compose.foundation.lazy.ScalingLazyColumnDefaults
import androidx.wear.compose.foundation.lazy.AutoCenteringParams
import androidx.wear.compose.foundation.lazy.items
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
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


/**
 * A custom shape that draws a rounded equilateral triangle, used for the Help button.
 */
val HelpTriangleShape = GenericShape { size, _ ->
    val s = size.width
    val h = s * 0.866f // Inaltimea triunghiului echilateral (sqrt(3)/2)
    val yOffset = size.height - h // Aliniat la baza (bottom)
    val r = s * 0.12f // Raza de rotunjire

    // Varful de sus (centrat)
    moveTo(s * 0.5f, yOffset + r * 0.4f)
    quadraticTo(s * 0.5f, yOffset, s * 0.5f + r * 0.8f, yOffset + r * 0.8f)
    
    // Latura dreapta spre coltul dreapta-jos
    lineTo(s - r * 0.8f, yOffset + h - r * 1.2f)
    quadraticTo(s, yOffset + h, s - r * 2f, yOffset + h)
    
    // Baza dreapta spre coltul stanga-jos
    lineTo(r * 2f, yOffset + h)
    quadraticTo(0f, yOffset + h, r * 0.8f, yOffset + h - r * 1.2f)

    close()
}

/**
 * The main activity for the SoundFriend Wear OS application.
 *
 * This activity initializes the splash screen, ensures the screen stays on during use,
 * and acquires a MulticastLock to allow the discovery of mixers via UDP broadcasts.
 */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        // Acquire MulticastLock to receive UDP broadcasts for mixer discovery
        try {
            val wm = applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
            val lock = wm.createMulticastLock("SoundFriendLock")
            lock.setReferenceCounted(true)
            lock.acquire()
        } catch (e: Exception) {
            e.printStackTrace()
        }

        setContent {
            SoundFriendApp()
        }
    }
}

/**
 * The root Composable of the application.
 *
 * Manages the navigation state, notification alerts, and the main layout structure
 * using a [Scaffold] with Wear OS specific components.
 *
 * @param viewModel The [WingViewModel] that provides the application state and business logic.
 */
@Composable
fun SoundFriendApp(viewModel: WingViewModel = viewModel()) {
    val navController = rememberSwipeDismissableNavController()
    val notification by viewModel.notification.collectAsState()
    val selectedMixer by viewModel.selectedMixer.collectAsState()
    val discoveredMixers by viewModel.discoveredMixers.collectAsState()
    val isScanning by viewModel.isScanning.collectAsState()
    
    var animationFinished by remember { mutableStateOf(false) }

    // Trigger vibration when alert arrives
    val context = androidx.compose.ui.platform.LocalContext.current
    LaunchedEffect(notification) {
        if (notification != null) {
            vibrate(context)
        }
    }

    // Automatically navigate to Main ONLY if DEMO is selected
    LaunchedEffect(selectedMixer) {
        if (selectedMixer?.ip == "0.0.0.0") {
            navController.navigate("main") {
                // We keep the settings in backstack so we can go back
                launchSingleTop = true
            }
        }
    }

    // Navigate to Settings when discovery finds mixers
    LaunchedEffect(isScanning, discoveredMixers) {
        if (!isScanning && discoveredMixers.isNotEmpty() && selectedMixer == null) {
            navController.navigate("settings")
        }
    }

    val currentBackStackEntry by navController.currentBackStackEntryAsState()
    val isHelpScreen = currentBackStackEntry?.destination?.route == "help"

    MaterialTheme {
        Scaffold(
            timeText = {
                if (animationFinished) {
                    Box(modifier = Modifier.fillMaxSize()) {
                        if (isHelpScreen && (notification == null)) {
                            // Draw semi-transparent black masks under the curved texts 
                            // to create a "segment-cut" aesthetic on the Help screen.
                            Canvas(modifier = Modifier.fillMaxSize()) {
                                // Top Segment Mask (under clock)
                                drawArc(
                                    color = Color.Black,
                                    startAngle = 220f,
                                    sweepAngle = 100f,
                                    useCenter = false
                                )
                                // Bottom Segment Mask (under mixer info)
                                drawArc(
                                    color = Color.Black,
                                    startAngle = 40f,
                                    sweepAngle = 100f,
                                    useCenter = false
                                )
                            }
                        }

                        TimeText()
                        val bottomText = if (selectedMixer != null) {
                            selectedMixer!!.name
                        } else {
                            "NO MIXER"
                        }
                        
                        CurvedLayout(anchor = 90f, angularDirection = CurvedDirection.Angular.CounterClockwise) {
                            curvedText(
                                text = bottomText,
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
            val navigateToHelp = {
                if (navController.currentDestination?.route != "help") {
                    navController.navigate("help")
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(Unit) {
                        detectVerticalDragGestures { _, dragAmount ->
                            if (dragAmount > 50) { // Swipe Down
                                navigateToHelp()
                            }
                        }
                    }
            ) {
                if (isScanning && discoveredMixers.isEmpty()) {
                    RadarScreen(
                        onLongPress = {
                            viewModel.stopDiscovery()
                        }
                    )
                } else {
                    SwipeDismissableNavHost(
                        navController = navController,
                        startDestination = "settings"
                    ) {
                        composable("main") {
                            MainScreen(
                                viewModel = viewModel,
                                onSwipeUp = { 
                                    viewModel.selectMixer(null)
                                    navController.popBackStack("settings", inclusive = false) 
                                },
                                onSwipeDown = navigateToHelp
                            )
                        }
                        composable("settings") {
                            MixerSelectionScreen(
                                viewModel = viewModel,
                                onMixerSelected = { mixer ->
                                    if (mixer.ip == "0.0.0.0") {
                                        navController.navigate("main")
                                    } else {
                                        navController.navigate("fx_selection")
                                    }
                                },
                                onSwipeDown = navigateToHelp,
                                onHelpClick = navigateToHelp
                            )
                        }
                        composable("fx_selection") {
                            FxSelectionScreen(
                                viewModel = viewModel,
                                onFxSelected = {
                                    navController.navigate("main")
                                },
                                onSwipeUp = { 
                                    viewModel.selectMixer(null)
                                    navController.popBackStack("settings", inclusive = false) 
                                },
                                onSwipeDown = navigateToHelp
                            )
                        }
                        composable("help") {
                            HelpScreen(
                                viewModel = viewModel,
                                onBack = { navController.popBackStack() }
                            )
                        }
                    }
                }

                if (!animationFinished) {
                    LandingAnimation(onFinished = { animationFinished = true })
                }

                // Notification Overlay
                notification?.let { notif ->
                    LaunchedEffect(notif) {
                        val pulseDuration = 1000L // 60 BPM = 1 pulse per second
                        while (true) {
                            vibrate(context, 200) // Pulse vibration
                            delay(pulseDuration)
                        }
                    }

                    if (notif.type == NotificationType.ALERT) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(Color.Red.copy(alpha = 0.9f))
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth(0.9f)
                                    .align(Alignment.Center),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    text = "HELP NEEDED:",
                                    color = Color.White,
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold,
                                    textAlign = TextAlign.Center
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = notif.text,
                                    color = Color.White,
                                    fontSize = 24.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    textAlign = TextAlign.Center
                                )
                            }

                            Button(
                                onClick = { viewModel.dismissAlert() },
                                shape = CircleShape,
                                colors = ButtonDefaults.buttonColors(backgroundColor = Color.White, contentColor = Color.Red),
                                modifier = Modifier
                                    .size(56.dp)
                                    .padding(bottom = 16.dp)
                                    .align(Alignment.BottomCenter)
                            ) {
                                Text("OK", fontWeight = FontWeight.Bold)
                            }
                        }
                    } else {
                        // INFO Message - Blue background, scrollable, OK at the end
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(Color.Blue.copy(alpha = 0.9f)),
                            contentAlignment = Alignment.Center
                        ) {
                            ScalingLazyColumn(
                                modifier = Modifier.fillMaxSize(),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically)
                            ) {
                                item {
                                    Text(
                                        text = notif.text,
                                        color = Color.White,
                                        fontSize = 18.sp,
                                        fontWeight = FontWeight.Bold,
                                        textAlign = TextAlign.Center,
                                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 20.dp)
                                    )
                                }
                                item {
                                    Button(
                                        onClick = { viewModel.dismissAlert() },
                                        shape = CircleShape,
                                        colors = ButtonDefaults.buttonColors(backgroundColor = Color.White, contentColor = Color.Blue),
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
    }
}

/**
 * Displays a landing animation when the app starts.
 *
 * Features a red ring expansion and a hole-reveal effect (feathered edge) that transitions
 * from a black screen to the main content.
 *
 * @param onFinished Callback invoked when the animation completes.
 */
@Composable
fun LandingAnimation(onFinished: () -> Unit) {
    val animationProgress = remember { Animatable(0f) }
    
    LaunchedEffect(Unit) {
        animationProgress.animateTo(
            targetValue = 1f,
            animationSpec = tween(durationMillis = 4000, easing = LinearOutSlowInEasing)
        )
        onFinished()
    }

    Canvas(
        modifier = Modifier
            .fillMaxSize()
            .graphicsLayer(compositingStrategy = CompositingStrategy.Offscreen)
    ) {
        val maxRadius = size.maxDimension * 1.5f
        
        val redProgress = (animationProgress.value * 1.05f).coerceAtMost(1f)
        val redRadius = maxRadius * redProgress
        
        val holeProgress = ((animationProgress.value - 0.35f) * 1.54f).coerceIn(0f, 1f)
        val holeRadius = maxRadius * holeProgress
        
        val strokeWidth = 6.dp.toPx()
        val featherWidth = 40.dp.toPx() // Lățimea efectului de feather (fade)
        
        // 1. Fundal negru
        drawRect(color = Color.Black)
        
        // 2. Reveal the content with a "hole" effect using a radial gradient with feathering
        if (holeRadius > 0f) {
            drawCircle(
                brush = androidx.compose.ui.graphics.Brush.radialGradient(
                    colors = listOf(Color.Transparent, Color.Black),
                    center = center,
                    radius = (holeRadius + featherWidth).coerceAtLeast(0.1f)
                ),
                radius = holeRadius + featherWidth,
                blendMode = BlendMode.DstIn // Keeps what's inside the transparent part of the gradient
            )
        }
        
        // 3. Inelul roșu conducător
        if (redRadius > 0f && redProgress < 1f) {
            drawCircle(
                color = Color.Red,
                radius = redRadius,
                style = Stroke(width = strokeWidth),
                alpha = 1f - redProgress
            )
        }
    }
}

/**
 * The main interface for interacting with the mixer's tempo.
 *
 * Displays the current BPM and allows the user to set it via tap gestures.
 * Includes a visual pulse animation synchronized with the BPM.
 *
 * @param viewModel The application ViewModel.
 * @param onSwipeUp Navigation callback for swiping up (back to settings).
 * @param onSwipeDown Navigation callback for help.
 */
@Composable
fun MainScreen(viewModel: WingViewModel, onSwipeUp: () -> Unit, onSwipeDown: () -> Unit) {
    val bpm by viewModel.bpm.collectAsState()
    val context = androidx.compose.ui.platform.LocalContext.current

    // Animation for continuous pulse (fade-to-black) effect synced with BPM
    var alpha by remember { mutableStateOf(1f) }
    
    // Smooth frame-synced animation loop
    LaunchedEffect(bpm) {
        val pulseDuration = (60000 / bpm.coerceAtLeast(1f)).toLong()
        while (true) {
            val startTime = withFrameMillis { it }
            var elapsed: Long
            do {
                elapsed = withFrameMillis { it } - startTime
                // Linearly decrease alpha over the duration of a beat
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
                    onTap = { 
                        viewModel.tapTempo() 
                    },
                    onLongPress = { 
                        viewModel.selectMixer(null) // Reset mixer to allow re-entry
                        onSwipeUp() 
                    }
                )
            }
            .pointerInput(Unit) {
                detectVerticalDragGestures(
                    onVerticalDrag = { _, _ -> },
                    onDragEnd = { /* Logic handled by generic detector below */ },
                    onDragCancel = { }
                )
            }
            .pointerInput(Unit) {
                // A more robust vertical swipe detector for Main screen
                var totalY = 0f
                detectVerticalDragGestures(
                    onDragEnd = {
                        if (totalY < -80f) onSwipeUp()
                        else if (totalY > 80f) onSwipeDown()
                        totalY = 0f
                    },
                    onVerticalDrag = { change, dragAmount ->
                        change.consume()
                        totalY += dragAmount
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

/**
 * A visual representation of the mixer discovery process.
 *
 * Displays a radar-like animation with sweeping arcs and pulsing concentric circles.
 *
 * @param onLongPress Callback to stop the discovery process.
 */
@Composable
fun RadarScreen(onLongPress: () -> Unit) {
    val infiniteTransition = rememberInfiniteTransition(label = "RadarTransition")
    
    val angle by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "RadarAngle"
    )

    val pulse by infiniteTransition.animateFloat(
        initialValue = 0.4f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "RadarPulse"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .pointerInput(Unit) {
                detectTapGestures(
                    onLongPress = { onLongPress() }
                )
            },
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.size(200.dp)) {
            val center = this.center
            val radius = size.minDimension / 2

            // Draw concentric circles
            for (i in 1..3) {
                drawCircle(
                    color = Color.Red.copy(alpha = 0.2f * i),
                    radius = radius * (i / 3f) * pulse,
                    style = Stroke(width = 2.dp.toPx())
                )
                drawCircle(
                    color = Color.Red.copy(alpha = 0.15f),
                    radius = radius * (i / 3f),
                    style = Stroke(width = 1.dp.toPx())
                )
            }

            // Draw radar sweep
            rotate(angle) {
                drawArc(
                    brush = Brush.sweepGradient(
                        0f to Color.Transparent,
                        0.5f to Color.Red.copy(alpha = 0.5f),
                        1f to Color.Red,
                        center = center
                    ),
                    startAngle = 0f,
                    sweepAngle = 90f,
                    useCenter = true,
                    topLeft = center - androidx.compose.ui.geometry.Offset(radius, radius),
                    size = androidx.compose.ui.geometry.Size(radius * 2, radius * 2)
                )
            }
            
            // Draw center point
            drawCircle(
                color = Color.Red,
                radius = 4.dp.toPx()
            )
        }
    }
}

/**
 * Screen for discovering and selecting a Wing mixer on the local network.
 *
 * @param viewModel The application ViewModel.
 * @param onMixerSelected Callback when a mixer is selected from the list or Demo mode is entered.
 * @param onSwipeDown Navigation callback for help.
 * @param onHelpClick Callback for the help button.
 */
@Composable
fun MixerSelectionScreen(
    viewModel: WingViewModel, 
    onMixerSelected: (WingMixer) -> Unit,
    onSwipeDown: () -> Unit,
    onHelpClick: () -> Unit
) {
    val mixers by viewModel.discoveredMixers.collectAsState()
    val selectedMixer by viewModel.selectedMixer.collectAsState()
    
    val configuration = androidx.compose.ui.platform.LocalConfiguration.current
    val screenHeight = configuration.screenHeightDp.dp
    val moduleHeight = screenHeight / 5

    // Scaling params to create a "Magnifying Glass" effect in the center
    val scalingParams = ScalingLazyColumnDefaults.scalingParams(
        edgeAlpha = 0.4f,
        edgeScale = 0.5f,    // Smaller edges
        minElementHeight = 0.1f,
        maxElementHeight = 0.6f  // Much larger center (60% of screen)
    )

    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        ScalingLazyColumn(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            autoCentering = AutoCenteringParams(itemIndex = 0),
            scalingParams = scalingParams
        ) {
            if (mixers.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier
                            .height(moduleHeight * 2.5f) // Further increased for even larger buttons
                            .fillMaxWidth(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            Button(
                                onClick = { viewModel.startDiscovery() },
                                shape = CircleShape,
                                colors = ButtonDefaults.buttonColors(backgroundColor = Color.Red, contentColor = Color.White),
                                modifier = Modifier.size(60.dp).offset(x = (-2).dp, y = 2.dp)
                            ) {
                                Text("SCAN", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                            }

                            Row(
                                horizontalArrangement = Arrangement.spacedBy(2.dp),
                                verticalAlignment = Alignment.Bottom
                            ) {
                                Button(
                                    onClick = {
                                        val demoMixer = WingMixer("DEMO", "0.0.0.0")
                                        viewModel.selectMixer(demoMixer)
                                        onMixerSelected(demoMixer)
                                    },
                                    shape = CircleShape,
                                    colors = ButtonDefaults.buttonColors(backgroundColor = Color.Yellow, contentColor = Color.Black),
                                    modifier = Modifier.size(60.dp).offset(x = 6.dp)
                                ) {
                                    Text("DEMO", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                                }
                                Button(
                                    onClick = onHelpClick,
                                    shape = HelpTriangleShape,
                                    colors = ButtonDefaults.buttonColors(backgroundColor = Color.Blue, contentColor = Color.White),
                                    modifier = Modifier.size(75.dp)
                                ) {
                                    Text(
                                        text = "?", 
                                        fontSize = 27.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.offset(y = (-4).dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
            
            items(mixers) { mixer ->
                val isSelected = selectedMixer?.ip == mixer.ip
                Chip(
                    onClick = {
                        viewModel.selectMixer(mixer)
                        onMixerSelected(mixer)
                    },
                    label = { 
                        Text(
                            mixer.name, 
                            fontSize = 14.sp, 
                            maxLines = 2,
                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                        ) 
                    },
                    secondaryLabel = { Text(mixer.ip, fontSize = 11.sp) },
                    colors = if (isSelected) ChipDefaults.gradientBackgroundChipColors() 
                             else ChipDefaults.secondaryChipColors(),
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp)
                )
            }
        }
    }
}

/**
 * Screen for selecting an FX slot to synchronize the tempo with.
 *
 * @param viewModel The application ViewModel.
 * @param onFxSelected Callback when an FX slot is selected or "Global Only" is chosen.
 * @param onSwipeUp Navigation callback to go back to mixer selection.
 * @param onSwipeDown Navigation callback for help.
 */
@Composable
fun FxSelectionScreen(
    viewModel: WingViewModel,
    onFxSelected: () -> Unit,
    onSwipeUp: () -> Unit,
    onSwipeDown: () -> Unit
) {
    val fxSlots by viewModel.fxSlots.collectAsState()
    val selectedFxSlot by viewModel.selectedFxSlot.collectAsState()
    val selectedMixer by viewModel.selectedMixer.collectAsState()

    val configuration = androidx.compose.ui.platform.LocalConfiguration.current
    val screenHeight = configuration.screenHeightDp.dp
    val moduleHeight = screenHeight / 5

    // Scaling params to make the center item pop like a lens
    val scalingParams = ScalingLazyColumnDefaults.scalingParams(
        edgeAlpha = 0.4f,
        edgeScale = 0.5f,
        minElementHeight = 0.1f,
        maxElementHeight = 0.6f
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                var totalY = 0f
                detectVerticalDragGestures(
                    onDragEnd = {
                        if (totalY < -80f) onSwipeUp()
                        totalY = 0f
                    },
                    onVerticalDrag = { change, dragAmount ->
                        if (dragAmount < -10) totalY += dragAmount
                    }
                )
            }
    ) {
        ScalingLazyColumn(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            autoCentering = AutoCenteringParams(itemIndex = 0),
            scalingParams = scalingParams
        ) {
            item {
                Box(
                    modifier = Modifier.height(moduleHeight).fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "SYNC WITH FX:", 
                        fontSize = 12.sp, 
                        fontWeight = FontWeight.Bold,
                        color = Color.LightGray
                    )
                }
            }

            if (fxSlots.isEmpty() && selectedMixer != null && selectedMixer!!.ip != "0.0.0.0") {
                item {
                    Box(
                        modifier = Modifier.height(moduleHeight).fillMaxWidth(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Querying FX slots...",
                            fontSize = 11.sp,
                            color = Color.Gray
                        )
                    }
                }
            }

            items(fxSlots) { fx ->
                val isSelected = selectedFxSlot?.id == fx.id
                Chip(
                    onClick = { 
                        viewModel.selectFxSlot(fx)
                        onFxSelected()
                    },
                    label = { Text(fx.model, fontSize = 14.sp) },
                    secondaryLabel = { Text("Slot ${fx.id}", fontSize = 11.sp) },
                    colors = if (isSelected) ChipDefaults.gradientBackgroundChipColors() 
                             else ChipDefaults.secondaryChipColors(),
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp)
                )
            }

            item {
                Box(
                    modifier = Modifier.height(moduleHeight).fillMaxWidth().padding(horizontal = 12.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Button(
                        onClick = {
                            viewModel.selectFxSlot(null)
                            onFxSelected()
                        },
                        modifier = Modifier.fillMaxSize()
                    ) {
                        Text("Use Global Only", fontSize = 11.sp)
                    }
                }
            }
            
            item {
                Box(
                    modifier = Modifier.height(moduleHeight).fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "↑ Swipe Up for Mixers",
                        fontSize = 10.sp,
                        color = Color.DarkGray
                    )
                }
            }
        }
    }
}

/**
 * Displays help information including network details, gestures, and command examples.
 *
 * @param viewModel The application ViewModel.
 * @param onBack Callback to return to the previous screen.
 */
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
            Spacer(modifier = Modifier.height(12.dp))
        }
        item {
            Text(text = "Gestures:", fontWeight = FontWeight.Bold, fontSize = 12.sp)
        }
        item {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.fillMaxWidth(0.85f)
            ) {
                Text(text = "↑ Swipe Up:", fontSize = 12.sp, color = Color.LightGray)
                Text(text = "Back to previous menu", fontSize = 11.sp, textAlign = TextAlign.Center)
            }
        }
        item {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.fillMaxWidth(0.85f)
            ) {
                Text(text = "↓ Swipe Down:", fontSize = 12.sp, color = Color.LightGray)
                Text(text = "Open this Help screen", fontSize = 11.sp, textAlign = TextAlign.Center)
            }
        }
        item {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.fillMaxWidth(0.85f)
            ) {
                Text(text = "Tap (Main):", fontSize = 12.sp, color = Color.LightGray)
                Text(text = "Set tempo (Tap Tempo)", fontSize = 11.sp, textAlign = TextAlign.Center)
            }
        }
        item {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.fillMaxWidth(0.85f)
            ) {
                Text(text = "Long Press:", fontSize = 12.sp, color = Color.LightGray)
                Text(text = "Reset selection/Restart", fontSize = 11.sp, textAlign = TextAlign.Center)
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
                Text(text = "Address: /SoundFriend/alerts", fontSize = 10.sp, color = Color.Cyan)
                Text(text = "Value: \"Keys Help\"", fontSize = 10.sp, color = Color.Cyan, textAlign = TextAlign.Center)
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
                Text(text = "Trigger Msg (OSC):", fontSize = 12.sp, color = Color.LightGray)
                Text(text = "Port: 5006", fontSize = 10.sp, color = Color.Cyan)
                Text(text = "Address: /SoundFriend/messages", fontSize = 10.sp, color = Color.Cyan)
                Text(text = "Value: \"Setlist: Song 1, ...\"", fontSize = 10.sp, color = Color.Cyan, textAlign = TextAlign.Center)
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

/**
 * Triggers a vibration on the device.
 *
 * @param context The application context.
 * @param durationMillis The duration of the vibration in milliseconds.
 */
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
