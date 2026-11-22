package com.g2012.wirelessprint

import android.Manifest
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.hardware.usb.*
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.*
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import java.io.File
import java.io.FileInputStream
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import kotlin.concurrent.thread

enum class AppScreen { HOME, PREVIEW, SERVER }

class MainActivity : ComponentActivity() {

    private val ACTION_USB_PERMISSION = "com.g2012.wirelessprint.USB_PERMISSION"
    private lateinit var usbManager: UsbManager
    private var pendingUsbAction: (() -> Unit)? = null

    // Reactive Compose UI State
    private var activeScreen by mutableStateOf(AppScreen.HOME)
    private var settings by mutableStateOf(PrintSettings())
    private var currentSource by mutableStateOf<PrintSource?>(null)
    private var currentSheetIndex by mutableIntStateOf(0)
    private var totalSheets by mutableIntStateOf(1)
    private var previewBitmap by mutableStateOf<Bitmap?>(null)
    private var resolvedLandscape by mutableStateOf(false)
    private var isRenderingPreview by mutableStateOf(false)

    private var sparePhoneIp by mutableStateOf("")
    private var isScanningWifi by mutableStateOf(false)

    private var isPrinting by mutableStateOf(false)
    private var printProgress by mutableFloatStateOf(0f)
    private var printStatusMessage by mutableStateOf("")

    private var serverRunning by mutableStateOf(PrintBridgeService.isRunning)
    private var serverIp by mutableStateOf(NetworkUtils.getBestLocalIp())
    private var serverStatus by mutableStateOf(PrintBridgeService.lastStatus)
    private var serverJobsCount by mutableIntStateOf(PrintBridgeService.jobsPrinted)

    private val pickPdfLauncher = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            currentSource = PrintSource.Pdf(uri, uri.lastPathSegment ?: "Document.pdf")
            currentSheetIndex = 0
            activeScreen = AppScreen.PREVIEW
            refreshPreview()
        }
    }

    private val pickImagesLauncher = registerForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        if (!uris.isNullOrEmpty()) {
            val existing = currentSource as? PrintSource.Images
            if (activeScreen == AppScreen.PREVIEW && existing != null) {
                currentSource = PrintSource.Images(existing.uris + uris)
            } else {
                currentSource = PrintSource.Images(uris)
                currentSheetIndex = 0
            }
            activeScreen = AppScreen.PREVIEW
            refreshPreview()
        }
    }

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                ACTION_USB_PERMISSION -> {
                    val granted = intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)
                    if (granted) pendingUsbAction?.invoke()
                    else Toast.makeText(this@MainActivity, "USB permission denied", Toast.LENGTH_SHORT).show()
                    pendingUsbAction = null
                }
                PrintBridgeService.ACTION_STATUS -> {
                    serverStatus = intent.getStringExtra(PrintBridgeService.EXTRA_STATUS) ?: serverStatus
                    serverRunning = PrintBridgeService.isRunning
                    serverJobsCount = PrintBridgeService.jobsPrinted
                    serverIp = NetworkUtils.getBestLocalIp()
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
        )
        usbManager = getSystemService(Context.USB_SERVICE) as UsbManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1)
            }
        }

        sparePhoneIp = getSharedPreferences("g2012_prefs", Context.MODE_PRIVATE).getString("last_ip", "") ?: ""
        serverRunning = PrintBridgeService.isRunning
        if (serverRunning) activeScreen = AppScreen.SERVER

        val filter = IntentFilter().apply {
            addAction(ACTION_USB_PERMISSION)
            addAction(PrintBridgeService.ACTION_STATUS)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(receiver, filter)
        }

        handleIncomingIntent(intent)
        if (!serverRunning) autoDiscoverSparePhone(silent = true)

        setContent {
            G2012StudioTheme {
                PrintStudioRoot(
                    activeScreen = activeScreen,
                    onNavigate = { screen ->
                        if (screen == AppScreen.SERVER) {
                            serverIp = NetworkUtils.getBestLocalIp()
                            serverRunning = PrintBridgeService.isRunning
                            serverStatus = PrintBridgeService.lastStatus
                        }
                        activeScreen = screen
                    },
                    settings = settings,
                    onUpdateSettings = { newSettings ->
                        val resetSheet = newSettings.multiPageMode != settings.multiPageMode ||
                                newSettings.pageFilter != settings.pageFilter ||
                                newSettings.customRange != settings.customRange
                        settings = newSettings
                        if (resetSheet) currentSheetIndex = 0
                        if (currentSource != null) refreshPreview()
                    },
                    sparePhoneIp = sparePhoneIp,
                    isScanningWifi = isScanningWifi,
                    onSaveConnection = { ip, directUsb ->
                        sparePhoneIp = ip.trim()
                        settings = settings.copy(directUsb = directUsb)
                        getSharedPreferences("g2012_prefs", Context.MODE_PRIVATE).edit().putString("last_ip", sparePhoneIp).apply()
                    },
                    onAutoDiscover = { autoDiscoverSparePhone(silent = false) },
                    onPickPdf = { pickPdfLauncher.launch(arrayOf("application/pdf")) },
                    onPickImages = { pickImagesLauncher.launch(arrayOf("image/*")) },
                    onOpenTestPage = {
                        currentSource = PrintSource.TestPage
                        currentSheetIndex = 0
                        activeScreen = AppScreen.PREVIEW
                        refreshPreview()
                    },
                    onOpenTextNote = { text ->
                        currentSource = PrintSource.PlainText(text, "Quick Note")
                        currentSheetIndex = 0
                        activeScreen = AppScreen.PREVIEW
                        refreshPreview()
                    },
                    previewBitmap = previewBitmap,
                    resolvedLandscape = resolvedLandscape,
                    isRenderingPreview = isRenderingPreview,
                    currentSheetIndex = currentSheetIndex,
                    totalSheets = totalSheets,
                    onChangeSheet = { idx ->
                        currentSheetIndex = idx.coerceIn(0, (totalSheets - 1).coerceAtLeast(0))
                        refreshPreview()
                    },
                    isPrinting = isPrinting,
                    printProgress = printProgress,
                    printStatusMessage = printStatusMessage,
                    onClearPrintStatus = { printStatusMessage = "" },
                    onStartPrint = { startPrintJob() },
                    serverRunning = serverRunning,
                    serverIp = serverIp,
                    serverStatus = serverStatus,
                    serverJobsCount = serverJobsCount,
                    onToggleServer = {
                        if (PrintBridgeService.isRunning) {
                            stopService(Intent(this, PrintBridgeService::class.java))
                            serverRunning = false
                            serverStatus = "Server stopped"
                        } else {
                            requestUsbPermissionAndRun { startBridgeService() }
                        }
                    },
                    onRefreshServerIp = { serverIp = NetworkUtils.getBestLocalIp() }
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIncomingIntent(intent)
    }

    private fun handleIncomingIntent(intent: Intent?) {
        if (intent == null) return
        when (intent.action) {
            Intent.ACTION_SEND -> {
                val type = intent.type ?: ""
                if (type == "text/plain") {
                    val text = intent.getStringExtra(Intent.EXTRA_TEXT)
                    if (!text.isNullOrBlank()) {
                        currentSource = PrintSource.PlainText(text, "Shared Text")
                        currentSheetIndex = 0
                        activeScreen = AppScreen.PREVIEW
                        refreshPreview()
                    }
                } else {
                    @Suppress("DEPRECATION")
                    val uri = intent.getParcelableExtra<Uri>(Intent.EXTRA_STREAM)
                    if (uri != null) {
                        currentSource = if (type.contains("pdf")) PrintSource.Pdf(uri, "Shared PDF") else PrintSource.Images(listOf(uri))
                        currentSheetIndex = 0
                        activeScreen = AppScreen.PREVIEW
                        refreshPreview()
                    }
                }
            }
            Intent.ACTION_SEND_MULTIPLE -> {
                @Suppress("DEPRECATION")
                val uris = intent.getParcelableArrayListExtra<Uri>(Intent.EXTRA_STREAM)
                if (!uris.isNullOrEmpty()) {
                    currentSource = PrintSource.Images(uris)
                    currentSheetIndex = 0
                    activeScreen = AppScreen.PREVIEW
                    refreshPreview()
                }
            }
            UsbManager.ACTION_USB_DEVICE_ATTACHED -> {
                activeScreen = AppScreen.SERVER
                requestUsbPermissionAndRun { startBridgeService() }
            }
        }
    }

    private fun refreshPreview() {
        val src = currentSource ?: return
        val snapSettings = settings
        val snapIndex = currentSheetIndex
        isRenderingPreview = true
        thread {
            try {
                val sheets = PageComposer.getTotalSheetCount(this, src, snapSettings)
                val safeIdx = if (sheets > 0) snapIndex.coerceIn(0, sheets - 1) else 0
                val res = if (sheets > 0) {
                    PageComposer.composeSheet(this, src, snapSettings, safeIdx, dpi = 120, forPrinting = false)
                } else null
                runOnUiThread {
                    totalSheets = sheets
                    currentSheetIndex = safeIdx
                    previewBitmap = res?.bitmap
                    resolvedLandscape = res?.resolvedLandscape ?: false
                    isRenderingPreview = false
                }
            } catch (_: Exception) {
                runOnUiThread { isRenderingPreview = false }
            }
        }
    }

    private fun autoDiscoverSparePhone(silent: Boolean) {
        isScanningWifi = true
        thread {
            var foundIp: String? = null
            var sock: DatagramSocket? = null
            try {
                sock = DatagramSocket().apply { broadcast = true; soTimeout = 2800 }
                val probe = "G2012_DISCOVER".toByteArray()
                sock.send(DatagramPacket(probe, probe.size, InetAddress.getByName("255.255.255.255"), PrintBridgeService.DISCOVERY_PORT))
                val buf = ByteArray(256)
                val resp = DatagramPacket(buf, buf.size)
                sock.receive(resp)
                if (String(resp.data, 0, resp.length).startsWith("G2012_PRINTER_READY:")) {
                    foundIp = resp.address.hostAddress
                }
            } catch (_: Exception) {
            } finally {
                try { sock?.close() } catch (_: Exception) {}
            }
            runOnUiThread {
                isScanningWifi = false
                if (foundIp != null) {
                    sparePhoneIp = foundIp!!
                    getSharedPreferences("g2012_prefs", Context.MODE_PRIVATE).edit().putString("last_ip", sparePhoneIp).apply()
                    if (!silent) Toast.makeText(this, "Found Canon G2012 at $foundIp", Toast.LENGTH_SHORT).show()
                } else if (!silent) {
                    Toast.makeText(this, "Spare Phone not found on Wi-Fi", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun startPrintJob() {
        val src = currentSource ?: return
        val snapSettings = settings
        val targetIp = sparePhoneIp.trim()
        if (!snapSettings.directUsb && targetIp.isEmpty()) {
            Toast.makeText(this, "Connect to Spare Phone or enable Direct USB first", Toast.LENGTH_SHORT).show()
            return
        }

        val runJob: () -> Unit = {
            isPrinting = true
            printProgress = 0.05f
            printStatusMessage = "Initializing Canon G2012 driver..."
            thread {
                try {
                    val initRes = GutenprintDriver.ensureInitialized(this)
                    if (initRes != "OK") throw IllegalStateException(initRes)

                    val sheetCount = PageComposer.getTotalSheetCount(this, src, snapSettings)
                    if (sheetCount <= 0) throw IllegalStateException("No pages selected to print.")

                    val totalWork = sheetCount * snapSettings.copies
                    var done = 0
                    val outFile = File(cacheDir, "g2012_job.prn")

                    for (copy in 1..snapSettings.copies) {
                        for (sheetIdx in 0 until sheetCount) {
                            val progress = 0.12f + (0.82f * done / totalWork)
                            runOnUiThread {
                                printProgress = progress
                                printStatusMessage = "Rendering sheet ${sheetIdx + 1}/$sheetCount (Copy $copy/${snapSettings.copies})..."
                            }
                            val resObj = PageComposer.composeSheet(this, src, snapSettings, sheetIdx, dpi = snapSettings.quality.dpi, forPrinting = true)
                            val res = GutenprintDriver.renderBitmapToCanonFile(
                                resObj.bitmap, snapSettings.isColor, snapSettings.paperSize.gutenprintName, snapSettings.mediaType, outFile
                            )
                            resObj.bitmap.recycle()
                            if (!res.startsWith("OK:")) throw IllegalStateException(res)

                            runOnUiThread {
                                printStatusMessage = if (snapSettings.directUsb) "Sending sheet ${sheetIdx + 1} via USB..." else "Beaming sheet ${sheetIdx + 1} to G2012..."
                            }
                            if (snapSettings.directUsb) sendFileDirectUsb(outFile)
                            else sendFileOverWifi(targetIp, outFile)
                            done++
                        }
                    }
                    outFile.delete()
                    runOnUiThread {
                        printProgress = 1f
                        printStatusMessage = "Printed $done sheet(s) successfully!"
                        isPrinting = false
                    }
                } catch (e: Exception) {
                    runOnUiThread {
                        printProgress = 0f
                        printStatusMessage = "Error: ${e.message}"
                        isPrinting = false
                    }
                }
            }
        }

        if (snapSettings.directUsb) requestUsbPermissionAndRun(runJob) else runJob()
    }

    private fun sendFileOverWifi(ip: String, file: File) {
        NetworkUtils.connectToServer(this, ip, PrintBridgeService.PRINT_PORT).use { socket ->
            val out = socket.getOutputStream()
            FileInputStream(file).use { fis -> fis.copyTo(out, 16384) }
            out.flush()
        }
    }

    private fun sendFileDirectUsb(file: File) {
        val device = findUsbPrinter() ?: throw IllegalStateException("No USB printer found.")
        var usbInterface: UsbInterface? = null
        var outEndpoint: UsbEndpoint? = null
        for (i in 0 until device.interfaceCount) {
            val intf = device.getInterface(i)
            if (intf.interfaceClass == UsbConstants.USB_CLASS_PRINTER) {
                for (j in 0 until intf.endpointCount) {
                    val ep = intf.getEndpoint(j)
                    if (ep.type == UsbConstants.USB_ENDPOINT_XFER_BULK && ep.direction == UsbConstants.USB_DIR_OUT) {
                        usbInterface = intf; outEndpoint = ep; break
                    }
                }
            }
            if (outEndpoint != null) break
        }
        if (usbInterface == null || outEndpoint == null) throw IllegalStateException("USB printer endpoint not found.")
        val conn = usbManager.openDevice(device) ?: throw IllegalStateException("Cannot open USB printer.")
        try {
            if (!conn.claimInterface(usbInterface, true)) throw IllegalStateException("Cannot claim USB interface.")
            val buf = ByteArray(16384)
            FileInputStream(file).use { fis ->
                var n: Int
                while (fis.read(buf).also { n = it } != -1) {
                    val chunk = if (n == buf.size) buf else buf.copyOf(n)
                    if (conn.bulkTransfer(outEndpoint, chunk, chunk.size, 15000) < 0) throw IllegalStateException("USB transfer error.")
                }
            }
        } finally {
            try { conn.releaseInterface(usbInterface) } catch (_: Exception) {}
            try { conn.close() } catch (_: Exception) {}
        }
    }

    private fun requestUsbPermissionAndRun(action: () -> Unit) {
        val printer = findUsbPrinter()
        if (printer == null) {
            serverStatus = "Canon G2012 not detected — turn printer ON & check OTG"
            Toast.makeText(this, "Canon G2012 not found via OTG", Toast.LENGTH_SHORT).show()
            return
        }
        if (usbManager.hasPermission(printer)) action()
        else {
            pendingUsbAction = action
            val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) PendingIntent.FLAG_MUTABLE else 0
            usbManager.requestPermission(printer, PendingIntent.getBroadcast(this, 0, Intent(ACTION_USB_PERMISSION), flags))
        }
    }

    private fun startBridgeService() {
        val intent = Intent(this, PrintBridgeService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) startForegroundService(intent) else startService(intent)
        serverRunning = true
        serverIp = NetworkUtils.getBestLocalIp()
        serverStatus = "Starting bridge..."
    }

    private fun findUsbPrinter(): UsbDevice? {
        return usbManager.deviceList.values.firstOrNull { device ->
            device.vendorId == 0x04A9 || (0 until device.interfaceCount).any { idx ->
                device.getInterface(idx).interfaceClass == UsbConstants.USB_CLASS_PRINTER
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        try { unregisterReceiver(receiver) } catch (_: Exception) {}
    }
}
