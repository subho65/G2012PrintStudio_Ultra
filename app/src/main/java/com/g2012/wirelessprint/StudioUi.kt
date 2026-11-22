package com.g2012.wirelessprint

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Bitmap
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PrintStudioRoot(
    activeScreen: AppScreen,
    onNavigate: (AppScreen) -> Unit,
    settings: PrintSettings,
    onUpdateSettings: (PrintSettings) -> Unit,
    sparePhoneIp: String,
    isScanningWifi: Boolean,
    onSaveConnection: (String, Boolean) -> Unit,
    onAutoDiscover: () -> Unit,
    onPickPdf: () -> Unit,
    onPickImages: () -> Unit,
    onOpenTestPage: () -> Unit,
    onOpenTextNote: (String) -> Unit,
    previewBitmap: Bitmap?,
    resolvedLandscape: Boolean,
    isRenderingPreview: Boolean,
    currentSheetIndex: Int,
    totalSheets: Int,
    onChangeSheet: (Int) -> Unit,
    isPrinting: Boolean,
    printProgress: Float,
    printStatusMessage: String,
    onClearPrintStatus: () -> Unit,
    onStartPrint: () -> Unit,
    serverRunning: Boolean,
    serverIp: String,
    serverStatus: String,
    serverJobsCount: Int,
    onToggleServer: () -> Unit,
    onRefreshServerIp: () -> Unit
) {
    var showConnectionSheet by remember { mutableStateOf(false) }
    var showSettingsSheet by remember { mutableStateOf(false) }
    var showNoteDialog by remember { mutableStateOf(false) }

    BackHandler(enabled = activeScreen != AppScreen.HOME) {
        onClearPrintStatus()
        onNavigate(AppScreen.HOME)
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(StudioColors.BgDeep)
    ) {
        // Subtle Ambient Aurora Glow at top of screen
        Canvas(modifier = Modifier.fillMaxSize()) {
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(StudioColors.CyanGlow.copy(alpha = 0.16f), Color.Transparent),
                    center = Offset(size.width * 0.15f, size.height * 0.06f),
                    radius = size.width * 0.85f
                ),
                radius = size.width * 0.85f,
                center = Offset(size.width * 0.15f, size.height * 0.06f)
            )
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(StudioColors.IndigoGlow.copy(alpha = 0.14f), Color.Transparent),
                    center = Offset(size.width * 0.90f, size.height * 0.18f),
                    radius = size.width * 0.75f
                ),
                radius = size.width * 0.75f,
                center = Offset(size.width * 0.90f, size.height * 0.18f)
            )
        }

        AnimatedContent(
            targetState = activeScreen,
            transitionSpec = {
                fadeIn(tween(220)) togetherWith fadeOut(tween(180))
            },
            label = "ScreenTransition"
        ) { screen ->
            when (screen) {
                AppScreen.HOME -> HomeDashboardScreen(
                    sparePhoneIp = sparePhoneIp,
                    directUsb = settings.directUsb,
                    isScanningWifi = isScanningWifi,
                    onOpenConnection = { showConnectionSheet = true },
                    onPickPdf = onPickPdf,
                    onPickImages = onPickImages,
                    onOpenNote = { showNoteDialog = true },
                    onOpenTestPage = onOpenTestPage,
                    onSwitchToServer = { onNavigate(AppScreen.SERVER) }
                )

                AppScreen.PREVIEW -> PreviewStudioScreen(
                    settings = settings,
                    onUpdateSettings = onUpdateSettings,
                    sparePhoneIp = sparePhoneIp,
                    previewBitmap = previewBitmap,
                    resolvedLandscape = resolvedLandscape,
                    isRenderingPreview = isRenderingPreview,
                    currentSheetIndex = currentSheetIndex,
                    totalSheets = totalSheets,
                    onChangeSheet = onChangeSheet,
                    isPrinting = isPrinting,
                    printProgress = printProgress,
                    printStatusMessage = printStatusMessage,
                    onBack = {
                        onClearPrintStatus()
                        onNavigate(AppScreen.HOME)
                    },
                    onAddImages = onPickImages,
                    onOpenSettings = { showSettingsSheet = true },
                    onOpenConnection = { showConnectionSheet = true },
                    onStartPrint = onStartPrint
                )

                AppScreen.SERVER -> SpareServerScreen(
                    serverRunning = serverRunning,
                    serverIp = serverIp,
                    serverStatus = serverStatus,
                    serverJobsCount = serverJobsCount,
                    onToggleServer = onToggleServer,
                    onRefreshIp = onRefreshServerIp,
                    onSwitchToPrint = { onNavigate(AppScreen.HOME) }
                )
            }
        }
    }

    if (showConnectionSheet) {
        PrinterConnectionBottomSheet(
            currentIp = sparePhoneIp,
            directUsb = settings.directUsb,
            isScanning = isScanningWifi,
            onAutoDiscover = onAutoDiscover,
            onSave = { ip, usb ->
                onSaveConnection(ip, usb)
                showConnectionSheet = false
            },
            onDismiss = { showConnectionSheet = false }
        )
    }

    if (showSettingsSheet) {
        PrintSettingsBottomSheet(
            settings = settings,
            onUpdate = onUpdateSettings,
            onDismiss = { showSettingsSheet = false }
        )
    }

    if (showNoteDialog) {
        QuickNoteModal(
            onDismiss = { showNoteDialog = false },
            onPreview = { text ->
                showNoteDialog = false
                onOpenTextNote(text)
            }
        )
    }
}

// =========================================================================
// SCREEN 1: BENTO DASHBOARD HOME
// =========================================================================
@Composable
fun HomeDashboardScreen(
    sparePhoneIp: String,
    directUsb: Boolean,
    isScanningWifi: Boolean,
    onOpenConnection: () -> Unit,
    onPickPdf: () -> Unit,
    onPickImages: () -> Unit,
    onOpenNote: () -> Unit,
    onOpenTestPage: () -> Unit,
    onSwitchToServer: () -> Unit
) {
    val isConnected = directUsb || sparePhoneIp.isNotBlank()
    val pulseTransition = rememberInfiniteTransition(label = "StatusPulse")
    val pulseAlpha by pulseTransition.animateFloat(
        initialValue = 0.35f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1100, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "PulseAlpha"
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 16.dp)
        ) {
            // Top Studio Title
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "G2012 STUDIO",
                        color = StudioColors.CyanGlow,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 2.sp
                    )
                    Text(
                        text = "Wireless Print Hub",
                        color = StudioColors.TextPrimary,
                        fontSize = 26.sp,
                        fontWeight = FontWeight.ExtraBold
                    )
                }
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(StudioColors.SurfaceElevated)
                        .border(1.dp, StudioColors.BorderSubtle, CircleShape)
                        .clickable { onOpenConnection() },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Rounded.Tune, contentDescription = "Connection", tint = StudioColors.CyanGlow)
                }
            }

            Spacer(modifier = Modifier.height(22.dp))

            // Glowing Printer Status Hero Card
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(24.dp))
                    .background(
                        Brush.linearGradient(
                            colors = listOf(Color(0xFF132038), Color(0xFF0E172A))
                        )
                    )
                    .border(
                        width = 1.dp,
                        brush = Brush.linearGradient(
                            colors = listOf(
                                if (isConnected) StudioColors.CyanGlow.copy(alpha = 0.7f) else StudioColors.AmberGlow.copy(alpha = 0.6f),
                                StudioColors.IndigoGlow.copy(alpha = 0.4f)
                            )
                        ),
                        shape = RoundedCornerShape(24.dp)
                    )
                    .clickable { onOpenConnection() }
                    .padding(20.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(56.dp)
                            .clip(RoundedCornerShape(18.dp))
                            .background(
                                Brush.linearGradient(
                                    colors = listOf(StudioColors.CyanGlow, StudioColors.IndigoGlow)
                                )
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Print,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(30.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(16.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(9.dp)
                                    .clip(CircleShape)
                                    .background(
                                        (if (isConnected) StudioColors.EmeraldGlow else StudioColors.AmberGlow).copy(alpha = pulseAlpha)
                                    )
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = when {
                                    isScanningWifi -> "SCANNING WI-FI..."
                                    directUsb -> "DIRECT USB-OTG"
                                    sparePhoneIp.isNotBlank() -> "WI-FI BRIDGE READY"
                                    else -> "NOT CONNECTED"
                                },
                                color = if (isConnected) StudioColors.EmeraldGlow else StudioColors.AmberGlow,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 1.sp
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Canon PIXMA G2012",
                            color = StudioColors.TextPrimary,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = when {
                                directUsb -> "Plugged directly via OTG cable"
                                sparePhoneIp.isNotBlank() -> "Bridge IP: $sparePhoneIp : 9100"
                                else -> "Tap to auto-find Spare Phone"
                            },
                            color = StudioColors.TextSecondary,
                            fontSize = 13.sp
                        )
                    }
                    Icon(
                        imageVector = Icons.Rounded.ChevronRight,
                        contentDescription = null,
                        tint = StudioColors.TextSecondary
                    )
                }
            }

            Spacer(modifier = Modifier.height(26.dp))

            Text(
                text = "CREATE & PRINT",
                color = StudioColors.TextSecondary,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.6.sp,
                modifier = Modifier.padding(start = 4.dp, bottom = 12.dp)
            )

            // Large Featured Bento Card: PDF Documents
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(24.dp))
                    .background(
                        Brush.linearGradient(
                            colors = listOf(Color(0xFF1E1B4B), Color(0xFF111827))
                        )
                    )
                    .border(1.dp, StudioColors.IndigoGlow.copy(alpha = 0.45f), RoundedCornerShape(24.dp))
                    .clickable { onPickPdf() }
                    .padding(20.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(54.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(StudioColors.IndigoGlow.copy(alpha = 0.22f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Rounded.PictureAsPdf, contentDescription = null, tint = Color(0xFFA5B4FC), modifier = Modifier.size(28.dp))
                    }
                    Spacer(modifier = Modifier.width(16.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text("PDF Documents", color = StudioColors.TextPrimary, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(2.dp))
                        Text("Worksheets, exams, multi-page & 2-up", color = StudioColors.TextSecondary, fontSize = 13.sp)
                    }
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .background(StudioColors.IndigoGlow)
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Text("Open", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Side-by-Side Bento Tiles: Photos + Quick Note
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                BentoSquareTile(
                    modifier = Modifier.weight(1f),
                    icon = Icons.Rounded.PhotoLibrary,
                    accent = StudioColors.CyanGlow,
                    title = "Photos",
                    subtitle = "Single or collage",
                    onClick = onPickImages
                )
                BentoSquareTile(
                    modifier = Modifier.weight(1f),
                    icon = Icons.Rounded.EditNote,
                    accent = StudioColors.AmberGlow,
                    title = "Quick Note",
                    subtitle = "Type & print text",
                    onClick = onOpenNote
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            // CMYK Calibration & Nozzle Test Tile
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(22.dp))
                    .background(StudioColors.SurfaceCard)
                    .border(1.dp, StudioColors.BorderSubtle, RoundedCornerShape(22.dp))
                    .clickable { onOpenTestPage() }
                    .padding(18.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(46.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(StudioColors.EmeraldGlow.copy(alpha = 0.16f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Rounded.Palette, contentDescription = null, tint = StudioColors.EmeraldGlow, modifier = Modifier.size(24.dp))
                    }
                    Spacer(modifier = Modifier.width(14.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text("CMYK Nozzle Test Page", color = StudioColors.TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                        Text("Verify ink flow & wireless link", color = StudioColors.TextSecondary, fontSize = 12.sp)
                    }
                    // Mini CMYK dots
                    Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                        listOf(Color.Cyan, Color.Magenta, Color.Yellow, Color.White).forEach { col ->
                            Box(modifier = Modifier.size(10.dp).clip(CircleShape).background(col))
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))
        }

        // Sleek Floating Bottom Navigation Pill (Print Hub | Bridge Server)
        BottomModeDock(
            isServerSelected = false,
            onSelectPrint = {},
            onSelectServer = onSwitchToServer
        )
    }
}

@Composable
fun BentoSquareTile(
    modifier: Modifier,
    icon: ImageVector,
    accent: Color,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(22.dp))
            .background(StudioColors.SurfaceCard)
            .border(1.dp, StudioColors.BorderSubtle, RoundedCornerShape(22.dp))
            .clickable { onClick() }
            .padding(18.dp)
    ) {
        Box(
            modifier = Modifier
                .size(46.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(accent.copy(alpha = 0.16f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = null, tint = accent, modifier = Modifier.size(24.dp))
        }
        Spacer(modifier = Modifier.height(16.dp))
        Text(title, color = StudioColors.TextPrimary, fontSize = 16.sp, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(2.dp))
        Text(subtitle, color = StudioColors.TextSecondary, fontSize = 12.sp)
    }
}

@Composable
fun BottomModeDock(
    isServerSelected: Boolean,
    onSelectPrint: () -> Unit,
    onSelectServer: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 12.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(32.dp))
                .background(StudioColors.SurfaceElevated)
                .border(1.dp, StudioColors.BorderSubtle, RoundedCornerShape(32.dp))
                .padding(5.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            DockPillTab(
                label = "Print Studio",
                icon = Icons.Rounded.Print,
                selected = !isServerSelected,
                onClick = onSelectPrint
            )
            DockPillTab(
                label = "Bridge Server",
                icon = Icons.Rounded.WifiTethering,
                selected = isServerSelected,
                onClick = onSelectServer
            )
        }
    }
}

@Composable
fun DockPillTab(label: String, icon: ImageVector, selected: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(26.dp))
            .background(
                if (selected) Brush.linearGradient(listOf(StudioColors.CyanGlow, StudioColors.IndigoGlow))
                else Brush.linearGradient(listOf(Color.Transparent, Color.Transparent))
            )
            .clickable { onClick() }
            .padding(horizontal = 18.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = if (selected) Color.White else StudioColors.TextSecondary,
            modifier = Modifier.size(18.dp)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = label,
            color = if (selected) Color.White else StudioColors.TextSecondary,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

// =========================================================================
// SCREEN 2: EXACT-REPLICA LIGHT TABLE PREVIEW STUDIO
// =========================================================================
@Composable
fun PreviewStudioScreen(
    settings: PrintSettings,
    onUpdateSettings: (PrintSettings) -> Unit,
    sparePhoneIp: String,
    previewBitmap: Bitmap?,
    resolvedLandscape: Boolean,
    isRenderingPreview: Boolean,
    currentSheetIndex: Int,
    totalSheets: Int,
    onChangeSheet: (Int) -> Unit,
    isPrinting: Boolean,
    printProgress: Float,
    printStatusMessage: String,
    onBack: () -> Unit,
    onAddImages: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenConnection: () -> Unit,
    onStartPrint: () -> Unit
) {
    var zoomScale by remember { mutableFloatStateOf(1f) }
    var panOffset by remember { mutableStateOf(Offset.Zero) }

    LaunchedEffect(currentSheetIndex, settings) {
        zoomScale = 1f
        panOffset = Offset.Zero
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        // Glass Top Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(CircleShape)
                    .background(StudioColors.SurfaceElevated)
                    .border(1.dp, StudioColors.BorderSubtle, CircleShape)
                    .clickable { onBack() },
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Rounded.Close, contentDescription = "Back", tint = StudioColors.TextPrimary, modifier = Modifier.size(20.dp))
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(
                modifier = Modifier
                    .weight(1f)
                    .clickable { onOpenConnection() }
            ) {
                Text(
                    text = "Exact Output Preview",
                    color = StudioColors.TextPrimary,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold
                )
                val orientLabel = if (resolvedLandscape) "Landscape" else "Portrait"
                val autoTag = if (settings.orientation == OrientationMode.AUTO) "Auto -> $orientLabel" else orientLabel
                Text(
                    text = "${settings.paperSize.label} • $autoTag • ${if (settings.isColor) "Color" else "B&W"} • ${settings.marginMode.shortLabel}",
                    color = StudioColors.CyanGlow,
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(CircleShape)
                    .background(StudioColors.SurfaceElevated)
                    .border(1.dp, StudioColors.BorderSubtle, CircleShape)
                    .clickable { onAddImages() },
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Rounded.AddPhotoAlternate, contentDescription = "Add Photos", tint = StudioColors.TextPrimary, modifier = Modifier.size(20.dp))
            }
        }

        // Architectural Dot-Grid "Light Table" Preview Area (Supports Pinch-to-Zoom!)
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 6.dp)
                .clip(RoundedCornerShape(28.dp))
                .background(StudioColors.DeskBg)
                .border(1.dp, StudioColors.BorderSubtle, RoundedCornerShape(28.dp))
                .pointerInput(Unit) {
                    detectTransformGestures { _, pan, zoom, _ ->
                        val newScale = (zoomScale * zoom).coerceIn(1f, 3.5f)
                        zoomScale = newScale
                        panOffset = if (newScale > 1f) panOffset + pan else Offset.Zero
                    }
                }
                .pointerInput(Unit) {
                    detectTapGestures(
                        onDoubleTap = {
                            if (zoomScale > 1.05f) {
                                zoomScale = 1f
                                panOffset = Offset.Zero
                            } else {
                                zoomScale = 2.2f
                            }
                        }
                    )
                },
            contentAlignment = Alignment.Center
        ) {
            // Subtle architectural dot grid
            Canvas(modifier = Modifier.fillMaxSize()) {
                val step = 28.dp.toPx()
                var x = step
                while (x < size.width) {
                    var y = step
                    while (y < size.height) {
                        drawCircle(Color(0xFF1E293B), radius = 1.6f, center = Offset(x, y))
                        y += step
                    }
                    x += step
                }
            }

            // Realistic Physical Paper Sheet with Shadow
            if (previewBitmap != null) {
                Box(
                    modifier = Modifier
                        .padding(start = 24.dp, end = 24.dp, top = 22.dp, bottom = 64.dp)
                        .graphicsLayer(
                            scaleX = zoomScale,
                            scaleY = zoomScale,
                            translationX = panOffset.x,
                            translationY = panOffset.y
                        )
                        .shadow(22.dp, RoundedCornerShape(6.dp), ambientColor = Color.Black, spotColor = Color.Black)
                        .clip(RoundedCornerShape(6.dp))
                        .background(Color.White)
                ) {
                    Image(
                        bitmap = previewBitmap.asImageBitmap(),
                        contentDescription = "Exact Paper Preview",
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.wrapContentSize()
                    )
                }
            }

            if (isRenderingPreview) {
                CircularProgressIndicator(
                    color = StudioColors.CyanGlow,
                    strokeWidth = 3.dp,
                    modifier = Modifier.size(36.dp)
                )
            }

            // Zoom Hint Pill (Top-Right)
            if (zoomScale > 1.05f) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(14.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(StudioColors.BgDeep.copy(alpha = 0.85f))
                        .clickable { zoomScale = 1f; panOffset = Offset.Zero }
                        .padding(horizontal = 10.dp, vertical = 5.dp)
                ) {
                    Text("${(zoomScale * 100).toInt()}% • Tap to reset", color = StudioColors.CyanGlow, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            }

            // Floating Glass Sheet Stepper Pill (Bottom-Center)
            Row(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 14.dp)
                    .clip(RoundedCornerShape(24.dp))
                    .background(StudioColors.SurfaceElevated.copy(alpha = 0.94f))
                    .border(1.dp, StudioColors.BorderSubtle, RoundedCornerShape(24.dp))
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = { if (currentSheetIndex > 0) onChangeSheet(currentSheetIndex - 1) },
                    enabled = currentSheetIndex > 0,
                    modifier = Modifier.size(34.dp)
                ) {
                    Icon(
                        Icons.Rounded.ChevronLeft,
                        contentDescription = "Previous",
                        tint = if (currentSheetIndex > 0) Color.White else StudioColors.TextSecondary.copy(alpha = 0.35f)
                    )
                }
                Text(
                    text = if (totalSheets > 0) "Sheet ${currentSheetIndex + 1} / $totalSheets" else "0 / 0",
                    color = StudioColors.TextPrimary,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 10.dp)
                )
                IconButton(
                    onClick = { if (currentSheetIndex < totalSheets - 1) onChangeSheet(currentSheetIndex + 1) },
                    enabled = currentSheetIndex < totalSheets - 1,
                    modifier = Modifier.size(34.dp)
                ) {
                    Icon(
                        Icons.Rounded.ChevronRight,
                        contentDescription = "Next",
                        tint = if (currentSheetIndex < totalSheets - 1) Color.White else StudioColors.TextSecondary.copy(alpha = 0.35f)
                    )
                }
            }
        }

        // Quick-Toggle Chips Strip (Instant 1-tap changes that update paper live!)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            QuickChip(
                icon = Icons.Rounded.Palette,
                label = if (settings.isColor) "Color" else "B&W",
                highlighted = settings.isColor,
                onClick = { onUpdateSettings(settings.copy(isColor = !settings.isColor)) }
            )
            QuickChip(
                icon = when (settings.orientation) {
                    OrientationMode.AUTO -> Icons.Rounded.AutoAwesome
                    OrientationMode.PORTRAIT -> Icons.Rounded.CropPortrait
                    OrientationMode.LANDSCAPE -> Icons.Rounded.CropLandscape
                },
                label = settings.orientation.shortLabel,
                highlighted = settings.orientation == OrientationMode.AUTO,
                onClick = {
                    val all = OrientationMode.values()
                    val next = all[(settings.orientation.ordinal + 1) % all.size]
                    onUpdateSettings(settings.copy(orientation = next))
                }
            )
            QuickChip(
                icon = Icons.Rounded.GridView,
                label = settings.multiPageMode.shortLabel,
                highlighted = settings.multiPageMode != MultiPageMode.SINGLY,
                onClick = {
                    val all = MultiPageMode.values()
                    val next = all[(settings.multiPageMode.ordinal + 1) % all.size]
                    onUpdateSettings(settings.copy(multiPageMode = next))
                }
            )
            QuickChip(
                icon = Icons.Rounded.ContentCopy,
                label = "${settings.copies}x",
                highlighted = settings.copies > 1,
                onClick = { onOpenSettings() }
            )
            QuickChip(
                icon = Icons.Rounded.Tune,
                label = "All Options",
                highlighted = false,
                onClick = { onOpenSettings() }
            )
        }

        // Live Print Status Banner (when printing or finished)
        AnimatedVisibility(visible = isPrinting || printStatusMessage.isNotBlank()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(StudioColors.SurfaceElevated)
                    .border(1.dp, StudioColors.BorderSubtle, RoundedCornerShape(16.dp))
                    .padding(12.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = when {
                            printStatusMessage.startsWith("Error") -> Icons.Rounded.ErrorOutline
                            printStatusMessage.contains("successfully") -> Icons.Rounded.CheckCircle
                            else -> Icons.Rounded.Print
                        },
                        contentDescription = null,
                        tint = when {
                            printStatusMessage.startsWith("Error") -> StudioColors.RoseGlow
                            printStatusMessage.contains("successfully") -> StudioColors.EmeraldGlow
                            else -> StudioColors.CyanGlow
                        },
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = printStatusMessage,
                        color = StudioColors.TextPrimary,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
                if (isPrinting) {
                    Spacer(modifier = Modifier.height(8.dp))
                    LinearProgressIndicator(
                        progress = { printProgress },
                        modifier = Modifier.fillMaxWidth().height(5.dp).clip(CircleShape),
                        color = StudioColors.CyanGlow,
                        trackColor = StudioColors.BorderSubtle
                    )
                }
            }
        }

        // Sticky Bottom Action Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .height(54.dp)
                    .clip(RoundedCornerShape(18.dp))
                    .background(StudioColors.SurfaceElevated)
                    .border(1.dp, StudioColors.BorderSubtle, RoundedCornerShape(18.dp))
                    .clickable { onOpenSettings() }
                    .padding(horizontal = 18.dp),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Rounded.Settings, contentDescription = "Settings", tint = StudioColors.TextPrimary)
            }

            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(54.dp)
                    .clip(RoundedCornerShape(18.dp))
                    .background(
                        if (!isPrinting) Brush.linearGradient(listOf(StudioColors.CyanGlow, StudioColors.IndigoGlow))
                        else Brush.linearGradient(listOf(StudioColors.SurfaceElevated, StudioColors.SurfaceElevated))
                    )
                    .clickable(enabled = !isPrinting) { onStartPrint() },
                contentAlignment = Alignment.Center
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Rounded.Print,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = if (isPrinting) "Printing..." else "Print Now",
                        color = Color.White,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

@Composable
fun QuickChip(icon: ImageVector, label: String, highlighted: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(if (highlighted) StudioColors.CyanGlow.copy(alpha = 0.18f) else StudioColors.SurfaceCard)
            .border(
                1.dp,
                if (highlighted) StudioColors.CyanGlow else StudioColors.BorderSubtle,
                RoundedCornerShape(20.dp)
            )
            .clickable { onClick() }
            .padding(horizontal = 14.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = if (highlighted) StudioColors.CyanGlow else StudioColors.TextSecondary,
            modifier = Modifier.size(16.dp)
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = label,
            color = if (highlighted) StudioColors.CyanGlow else StudioColors.TextPrimary,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold
        )
    }
}

// =========================================================================
// SCREEN 3: SPARE PHONE BRIDGE SERVER (ANIMATED RADAR UI)
// =========================================================================
@Composable
fun SpareServerScreen(
    serverRunning: Boolean,
    serverIp: String,
    serverStatus: String,
    serverJobsCount: Int,
    onToggleServer: () -> Unit,
    onRefreshIp: () -> Unit,
    onSwitchToPrint: () -> Unit
) {
    val context = LocalContext.current
    val radarAnim = rememberInfiniteTransition(label = "Radar")
    val ringScale by radarAnim.animateFloat(
        initialValue = 0.85f,
        targetValue = 1.65f,
        animationSpec = infiniteRepeatable(tween(1900, easing = LinearEasing), RepeatMode.Restart),
        label = "RingScale"
    )
    val ringAlpha by radarAnim.animateFloat(
        initialValue = 0.55f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(tween(1900, easing = LinearEasing), RepeatMode.Restart),
        label = "RingAlpha"
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "WIRELESS BRIDGE HOST",
                color = StudioColors.EmeraldGlow,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 2.sp
            )
            Text(
                text = "Spare Phone Server",
                color = StudioColors.TextPrimary,
                fontSize = 24.sp,
                fontWeight = FontWeight.ExtraBold
            )

            Spacer(modifier = Modifier.height(32.dp))

            // Animated Radar Core
            Box(
                modifier = Modifier.size(170.dp),
                contentAlignment = Alignment.Center
            ) {
                if (serverRunning) {
                    Box(
                        modifier = Modifier
                            .size(110.dp)
                            .graphicsLayer(scaleX = ringScale, scaleY = ringScale, alpha = ringAlpha)
                            .clip(CircleShape)
                            .border(2.dp, StudioColors.EmeraldGlow, CircleShape)
                    )
                }
                Box(
                    modifier = Modifier
                        .size(104.dp)
                        .clip(CircleShape)
                        .background(
                            if (serverRunning) Brush.linearGradient(listOf(StudioColors.EmeraldGlow, StudioColors.CyanGlow))
                            else Brush.linearGradient(listOf(StudioColors.SurfaceElevated, StudioColors.SurfaceCard))
                        )
                        .border(2.dp, if (serverRunning) Color.White.copy(alpha = 0.3f) else StudioColors.BorderSubtle, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Rounded.WifiTethering,
                        contentDescription = null,
                        tint = if (serverRunning) Color.White else StudioColors.TextSecondary,
                        modifier = Modifier.size(46.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Monospace IP & Port Card
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(22.dp))
                    .background(StudioColors.SurfaceCard)
                    .border(1.dp, StudioColors.BorderSubtle, RoundedCornerShape(22.dp))
                    .padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text("BRIDGE ADDRESS", color = StudioColors.TextSecondary, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.5.sp)
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "$serverIp : ${PrintBridgeService.PRINT_PORT}",
                    color = StudioColors.CyanGlow,
                    fontSize = 21.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = serverStatus,
                    color = StudioColors.TextSecondary,
                    fontSize = 13.sp
                )
                Spacer(modifier = Modifier.height(14.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    SmallGlassButton(icon = Icons.Rounded.ContentCopy, label = "Copy IP") {
                        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        cm.setPrimaryClip(ClipData.newPlainText("IP", serverIp))
                        Toast.makeText(context, "IP copied: $serverIp", Toast.LENGTH_SHORT).show()
                    }
                    SmallGlassButton(icon = Icons.Rounded.Refresh, label = "Refresh") { onRefreshIp() }
                    SmallGlassButton(icon = Icons.Rounded.Print, label = "Jobs: $serverJobsCount") {}
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Start / Stop Server Glowing Button
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(58.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(
                        if (serverRunning) Brush.linearGradient(listOf(StudioColors.RoseGlow, Color(0xFFBE123C)))
                        else Brush.linearGradient(listOf(StudioColors.EmeraldGlow, StudioColors.CyanGlow))
                    )
                    .clickable { onToggleServer() },
                contentAlignment = Alignment.Center
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = if (serverRunning) Icons.Rounded.Stop else Icons.Rounded.PlayArrow,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (serverRunning) "Stop Bridge Server" else "Start Bridge Server",
                        color = Color.White,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "Turn ON Hotspot (no SIM/data needed) or join Wi-Fi, plug in Canon G2012 via OTG, and tap Start.",
                color = StudioColors.TextSecondary,
                fontSize = 12.sp
            )
        }

        BottomModeDock(
            isServerSelected = true,
            onSelectPrint = onSwitchToPrint,
            onSelectServer = {}
        )
    }
}

@Composable
fun SmallGlassButton(icon: ImageVector, label: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(14.dp))
            .background(StudioColors.SurfaceElevated)
            .border(1.dp, StudioColors.BorderSubtle, RoundedCornerShape(14.dp))
            .clickable { onClick() }
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = StudioColors.TextSecondary, modifier = Modifier.size(15.dp))
        Spacer(modifier = Modifier.width(6.dp))
        Text(label, color = StudioColors.TextPrimary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
    }
}

// =========================================================================
// MATERIAL 3 BOTTOM SHEET: FULL PRINT SETTINGS
// =========================================================================
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PrintSettingsBottomSheet(
    settings: PrintSettings,
    onUpdate: (PrintSettings) -> Unit,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = StudioColors.SurfaceCard,
        contentColor = StudioColors.TextPrimary,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 22.dp)
                .padding(bottom = 36.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            Text("Print Settings", fontSize = 20.sp, fontWeight = FontWeight.ExtraBold, color = StudioColors.TextPrimary)

            // Copies Stepper + Collate
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(18.dp))
                    .background(StudioColors.SurfaceElevated)
                    .padding(14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text("Copies", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    Text(if (settings.collate) "Collated" else "Uncollated", fontSize = 12.sp, color = StudioColors.TextSecondary)
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    IconButton(
                        onClick = { if (settings.copies > 1) onUpdate(settings.copy(copies = settings.copies - 1)) },
                        modifier = Modifier.size(36.dp).clip(CircleShape).background(StudioColors.SurfaceCard)
                    ) { Icon(Icons.Rounded.Remove, contentDescription = null, tint = Color.White) }
                    Text("${settings.copies}", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    IconButton(
                        onClick = { if (settings.copies < 99) onUpdate(settings.copy(copies = settings.copies + 1)) },
                        modifier = Modifier.size(36.dp).clip(CircleShape).background(StudioColors.CyanGlow)
                    ) { Icon(Icons.Rounded.Add, contentDescription = null, tint = Color.Black) }
                }
            }

            // Color Mode
            SettingSection("Color Mode") {
                ChipSelectRow(
                    options = listOf("Black & White", "Full Color"),
                    selectedIndex = if (settings.isColor) 1 else 0,
                    onSelect = { onUpdate(settings.copy(isColor = it == 1)) }
                )
            }

            // Orientation (Auto / Portrait / Landscape)
            SettingSection("Orientation") {
                ChipSelectRow(
                    options = OrientationMode.values().map { it.label },
                    selectedIndex = settings.orientation.ordinal,
                    onSelect = { onUpdate(settings.copy(orientation = OrientationMode.values()[it])) }
                )
            }

            // Pages Per Sheet (N-up)
            SettingSection("Pages Per Sheet") {
                ChipSelectRow(
                    options = MultiPageMode.values().map { it.label },
                    selectedIndex = settings.multiPageMode.ordinal,
                    onSelect = { onUpdate(settings.copy(multiPageMode = MultiPageMode.values()[it])) }
                )
            }

            // Page Range & Reverse Order
            SettingSection("Pages to Print") {
                ChipSelectRow(
                    options = PageFilter.values().map { it.label },
                    selectedIndex = settings.pageFilter.ordinal,
                    onSelect = { onUpdate(settings.copy(pageFilter = PageFilter.values()[it])) }
                )
                if (settings.pageFilter == PageFilter.RANGE) {
                    Spacer(modifier = Modifier.height(10.dp))
                    OutlinedTextField(
                        value = settings.customRange,
                        onValueChange = { onUpdate(settings.copy(customRange = it)) },
                        label = { Text("Custom Range (e.g. 1-3, 5, 7)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Reverse page order", fontSize = 14.sp, color = StudioColors.TextSecondary)
                    Switch(
                        checked = settings.reverseOrder,
                        onCheckedChange = { onUpdate(settings.copy(reverseOrder = it)) }
                    )
                }
            }

            // Margins
            SettingSection("Margins") {
                ChipSelectRow(
                    options = MarginMode.values().map { it.label },
                    selectedIndex = settings.marginMode.ordinal,
                    onSelect = { onUpdate(settings.copy(marginMode = MarginMode.values()[it])) }
                )
            }

            // Scaling & Position
            SettingSection("Scaling & Alignment") {
                ChipSelectRow(
                    options = ScaleMode.values().map { it.label },
                    selectedIndex = settings.scaleMode.ordinal,
                    onSelect = { onUpdate(settings.copy(scaleMode = ScaleMode.values()[it])) }
                )
                Spacer(modifier = Modifier.height(8.dp))
                ChipSelectRow(
                    options = PositionMode.values().map { it.label },
                    selectedIndex = settings.positionMode.ordinal,
                    onSelect = { onUpdate(settings.copy(positionMode = PositionMode.values()[it])) }
                )
            }

            // Paper Size
            SettingSection("Paper Size") {
                ChipSelectRow(
                    options = PaperSize.values().map { it.label },
                    selectedIndex = settings.paperSize.ordinal,
                    onSelect = { onUpdate(settings.copy(paperSize = PaperSize.values()[it])) }
                )
            }

            // Print Quality & Media Type
            SettingSection("Quality & Paper Type") {
                ChipSelectRow(
                    options = PrintQuality.values().map { it.shortLabel },
                    selectedIndex = settings.quality.ordinal,
                    onSelect = { onUpdate(settings.copy(quality = PrintQuality.values()[it])) }
                )
                Spacer(modifier = Modifier.height(8.dp))
                ChipSelectRow(
                    options = listOf("Plain Paper", "Glossy Photo"),
                    selectedIndex = if (settings.mediaType == "Glossy") 1 else 0,
                    onSelect = { onUpdate(settings.copy(mediaType = if (it == 1) "Glossy" else "Plain")) }
                )
            }
        }
    }
}

@Composable
fun SettingSection(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column {
        Text(
            text = title.uppercase(),
            color = StudioColors.TextSecondary,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.2.sp,
            modifier = Modifier.padding(bottom = 8.dp)
        )
        content()
    }
}

@Composable
fun ChipSelectRow(options: List<String>, selectedIndex: Int, onSelect: (Int) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        options.forEachIndexed { idx, label ->
            val selected = idx == selectedIndex
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(14.dp))
                    .background(if (selected) StudioColors.CyanGlow else StudioColors.SurfaceElevated)
                    .border(1.dp, if (selected) StudioColors.CyanGlow else StudioColors.BorderSubtle, RoundedCornerShape(14.dp))
                    .clickable { onSelect(idx) }
                    .padding(horizontal = 14.dp, vertical = 10.dp)
            ) {
                Text(
                    text = label,
                    color = if (selected) Color(0xFF042F2E) else StudioColors.TextPrimary,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

// =========================================================================
// MATERIAL 3 BOTTOM SHEET: PRINTER CONNECTION
// =========================================================================
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PrinterConnectionBottomSheet(
    currentIp: String,
    directUsb: Boolean,
    isScanning: Boolean,
    onAutoDiscover: () -> Unit,
    onSave: (String, Boolean) -> Unit,
    onDismiss: () -> Unit
) {
    var ipText by remember(currentIp) { mutableStateOf(currentIp) }
    var useUsb by remember(directUsb) { mutableStateOf(directUsb) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = StudioColors.SurfaceCard,
        contentColor = StudioColors.TextPrimary,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 36.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text("Canon G2012 Connection", fontSize = 20.sp, fontWeight = FontWeight.ExtraBold)

            OutlinedTextField(
                value = ipText,
                onValueChange = { ipText = it },
                label = { Text("Spare Phone IP Address") },
                placeholder = { Text("e.g. 192.168.43.1") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            OutlinedButton(
                onClick = onAutoDiscover,
                modifier = Modifier.fillMaxWidth().height(48.dp),
                shape = RoundedCornerShape(14.dp)
            ) {
                Icon(Icons.Rounded.Router, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(if (isScanning) "Scanning Wi-Fi..." else "Auto-Find Spare Phone on Wi-Fi")
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(StudioColors.SurfaceElevated)
                    .padding(14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.Usb, contentDescription = null, tint = StudioColors.CyanGlow)
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text("Direct USB-OTG Mode", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        Text("Skip Wi-Fi & plug G2012 into this phone", fontSize = 11.sp, color = StudioColors.TextSecondary)
                    }
                }
                Switch(checked = useUsb, onCheckedChange = { useUsb = it })
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(Brush.linearGradient(listOf(StudioColors.CyanGlow, StudioColors.IndigoGlow)))
                    .clickable { onSave(ipText, useUsb) },
                contentAlignment = Alignment.Center
            ) {
                Text("Save Connection", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
fun QuickNoteModal(onDismiss: () -> Unit, onPreview: (String) -> Unit) {
    var noteText by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = StudioColors.SurfaceCard,
        titleContentColor = StudioColors.TextPrimary,
        textContentColor = StudioColors.TextPrimary,
        title = { Text("Quick Note to Paper", fontWeight = FontWeight.Bold) },
        text = {
            OutlinedTextField(
                value = noteText,
                onValueChange = { noteText = it },
                placeholder = { Text("Type or paste text to print...") },
                minLines = 5,
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            Button(
                onClick = { if (noteText.isNotBlank()) onPreview(noteText) },
                colors = ButtonDefaults.buttonColors(containerColor = StudioColors.CyanGlow, contentColor = Color.Black)
            ) { Text("Preview", fontWeight = FontWeight.Bold) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel", color = StudioColors.TextSecondary) }
        }
    )
}
