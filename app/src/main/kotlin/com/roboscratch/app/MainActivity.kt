package com.roboscratch.app

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.roboscratch.app.ble.BleManager

class MainActivity : AppCompatActivity() {

    private lateinit var webView: WebView
    private lateinit var bleManager: BleManager

    private val bluetoothAdapter: BluetoothAdapter? by lazy {
        (getSystemService(BLUETOOTH_SERVICE) as BluetoothManager).adapter
    }

    private val enableBluetoothLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            bleManager.startScan()
        }
    }

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { grants ->
        if (grants.values.all { it }) {
            checkBluetoothAndScan()
        } else {
            Toast.makeText(this, "BLE permissions required", Toast.LENGTH_LONG).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        webView = findViewById(R.id.webView)
        bleManager = BleManager(this, webView)
        setupWebView()
    }

    private fun setupWebView() {
        WebView.setWebContentsDebuggingEnabled(true)

        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            allowFileAccess = true
            allowContentAccess = true
            cacheMode = WebSettings.LOAD_NO_CACHE
            mediaPlaybackRequiresUserGesture = false
        }

        webView.webViewClient = WebViewClient()
        webView.addJavascriptInterface(BleBridge(bleManager, this), "Android")
        webView.loadUrl("file:///android_asset/index.html")
    }

    fun requestBlePermissionsAndScan() {
        val needed = mutableListOf<String>()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (!isGranted(Manifest.permission.BLUETOOTH_SCAN))
                needed.add(Manifest.permission.BLUETOOTH_SCAN)
            if (!isGranted(Manifest.permission.BLUETOOTH_CONNECT))
                needed.add(Manifest.permission.BLUETOOTH_CONNECT)
        } else {
            if (!isGranted(Manifest.permission.ACCESS_FINE_LOCATION))
                needed.add(Manifest.permission.ACCESS_FINE_LOCATION)
        }
        if (needed.isEmpty()) checkBluetoothAndScan()
        else permissionLauncher.launch(needed.toTypedArray())
    }

    private fun isGranted(permission: String) =
        ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED

    fun checkBluetoothAndScan() {
        val adapter = bluetoothAdapter ?: run {
            Toast.makeText(this, "Bluetooth not supported", Toast.LENGTH_LONG).show()
            return
        }
        if (!adapter.isEnabled) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
                !isGranted(Manifest.permission.BLUETOOTH_CONNECT)) {
                permissionLauncher.launch(arrayOf(Manifest.permission.BLUETOOTH_CONNECT))
                return
            }
            enableBluetoothLauncher.launch(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE))
        } else {
            bleManager.startScan()
        }
    }

    override fun onDestroy() {
        bleManager.disconnect()
        super.onDestroy()
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        if (webView.canGoBack()) webView.goBack()
        else super.onBackPressed()
    }
}
