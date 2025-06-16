package com.example.bus // Ensure this is your correct package name

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothSocket
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
// import androidx.annotation.RequiresPermission // This was unused, can be removed
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.ui.semantics.text
import androidx.core.content.ContextCompat
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.util.UUID
import kotlin.io.path.name

class MainActivity : AppCompatActivity() {

    // UI Elements
    private lateinit var btnScanOrPrepare: Button
    private lateinit var btnConnect: Button
    private lateinit var btnDisconnect: Button
    private lateinit var btnForward: Button
    private lateinit var btnBackward: Button
    private lateinit var btnLeft: Button
    private lateinit var btnRight: Button
    private lateinit var btnStop: Button
    private lateinit var tvStatus: TextView
    private lateinit var btnBlinkerLeft: Button
    private lateinit var btnBlinkerRight: Button


    // Bluetooth components
    private var bluetoothAdapter: BluetoothAdapter? = null
    private var bluetoothSocket: BluetoothSocket? = null
    private var outputStream: OutputStream? = null
    private var inputStream: InputStream? = null

    // Bluetooth constants
    private val MY_UUID: UUID =
        UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")
    private var esp32Device: BluetoothDevice? = null
    private var esp32MacAddress: String? = "E0:5A:1B:E4:DC:CE" // <<<< YOUR ESP32 MAC HERE
    private val TAG = "BluetoothApp"

    private val requestBluetoothPermissions =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
            var allRequiredPermissionsGranted = true
            val requiredPermissionsList = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                listOf(Manifest.permission.BLUETOOTH_CONNECT, Manifest.permission.BLUETOOTH_SCAN)
            } else {
                listOf(Manifest.permission.ACCESS_FINE_LOCATION)
            }

            permissions.entries.forEach { entry ->
                Log.d(TAG, "Permission ${entry.key} = ${entry.value}")
                if (requiredPermissionsList.contains(entry.key) && !entry.value) {
                    allRequiredPermissionsGranted = false
                }
            }

            if (allRequiredPermissionsGranted) {
                Log.d(TAG, "All required Bluetooth runtime permissions granted.")
                initializeBluetooth()
                if (!esp32MacAddress.isNullOrEmpty() && esp32Device == null && bluetoothAdapter?.isEnabled == true) {
                    if (hasRequiredBluetoothPermissions(connectPermissionOnly = true)) {
                        prepareDeviceByMac()
                    } else {
                        Log.w(TAG, "Permissions granted, but connect permission check failed for auto-prepare.")
                    }
                }
            } else {
                Log.e(TAG, "Not all required Bluetooth permissions were granted.")
                Toast.makeText(this, "Bluetooth & Location permissions are required.", Toast.LENGTH_LONG).show()
                tvStatus.text = "Status: Permissions denied."
                updateUIState()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        Log.d(TAG, "onCreate: setContentView(R.layout.activity_main) called.")

        try {
            Log.d(TAG, "onCreate: Initializing UI elements...")
            btnScanOrPrepare = findViewById(R.id.btnScan)
            btnConnect = findViewById(R.id.btnConnect)
            btnDisconnect = findViewById(R.id.btnDisconnect)
            tvStatus = findViewById(R.id.tvStatus)

            btnForward = findViewById(R.id.btnForward)
            btnBackward = findViewById(R.id.btnBackward)
            btnLeft = findViewById(R.id.btnLeft)
            btnRight = findViewById(R.id.btnRight)
            btnStop = findViewById(R.id.btnStop)

            btnBlinkerLeft = findViewById(R.id.btnBlinkerLeft)
            btnBlinkerRight = findViewById(R.id.btnBlinkerRight)

            Log.d(TAG, "onCreate: All findViewById calls completed.")

        } catch (e: Exception) {
            Log.e(TAG, "onCreate: Exception during findViewById: ${e.message}", e)
            Toast.makeText(this, "Error initializing UI. Check Logcat.", Toast.LENGTH_LONG).show()
            return
        }

        checkAndRequestPermissions()

        btnScanOrPrepare.setOnClickListener {
            if (esp32MacAddress.isNullOrEmpty()) {
                if (bluetoothAdapter == null) initializeBluetooth()
                if (bluetoothAdapter?.isEnabled == true) {
                    if (hasRequiredBluetoothPermissions(scanPermissionOnly = true)) {
                        startScanning()
                    } else {
                        Toast.makeText(this, "Scan permissions needed.", Toast.LENGTH_SHORT).show()
                        checkAndRequestPermissions()
                    }
                } else {
                    Toast.makeText(this, "Bluetooth not enabled.", Toast.LENGTH_LONG).show()
                    initializeBluetooth()
                }
            } else {
                if (bluetoothAdapter == null || !bluetoothAdapter!!.isEnabled) {
                    Toast.makeText(this, "Bluetooth not enabled for MAC prepare.", Toast.LENGTH_LONG).show()
                    initializeBluetooth()
                    return@setOnClickListener
                }
                if (hasRequiredBluetoothPermissions(connectPermissionOnly = true)) {
                    prepareDeviceByMac()
                } else {
                    Toast.makeText(this, "Connect permission needed for MAC.", Toast.LENGTH_SHORT).show()
                    checkAndRequestPermissions()
                }
            }
        }

        btnConnect.setOnClickListener {
            if (esp32Device != null) {
                if (hasRequiredBluetoothPermissions(connectPermissionOnly = true)) {
                    connectToDevice(esp32Device!!)
                } else {
                    Toast.makeText(this, "Connect permission missing.", Toast.LENGTH_SHORT).show()
                    checkAndRequestPermissions()
                }
            } else {
                Toast.makeText(this, "No ESP32 device selected/prepared.", Toast.LENGTH_SHORT).show()
            }
        }

        btnDisconnect.setOnClickListener { disconnect() }

        btnForward.setOnClickListener { sendCommand("F") }
        btnBackward.setOnClickListener { sendCommand("B") }
        btnLeft.setOnClickListener { sendCommand("L") }
        btnRight.setOnClickListener { sendCommand("R") }
        btnStop.setOnClickListener { sendCommand("S") }

        btnBlinkerLeft.setOnClickListener {
            Log.d(TAG, "Blinker Left button clicked, sending 'X'")
            sendCommand("X")
        }

        btnBlinkerRight.setOnClickListener {
            Log.d(TAG, "Blinker Right button clicked, sending 'Y'")
            sendCommand("Y")
        }
        updateUIState()
        Log.d(TAG, "onCreate: Finished.")
    }

    private fun checkAndRequestPermissions() {
        val permissionsToRequest = mutableListOf<String>()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_SCAN) != PackageManager.PERMISSION_GRANTED) {
                permissionsToRequest.add(Manifest.permission.BLUETOOTH_SCAN)
            }
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                permissionsToRequest.add(Manifest.permission.BLUETOOTH_CONNECT)
            }
        } else {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                permissionsToRequest.add(Manifest.permission.ACCESS_FINE_LOCATION)
            }
        }

        if (permissionsToRequest.isNotEmpty()) {
            Log.d(TAG, "Requesting permissions: ${permissionsToRequest.joinToString()}")
            requestBluetoothPermissions.launch(permissionsToRequest.toTypedArray())
        } else {
            Log.d(TAG, "All necessary runtime permissions already granted.")
            initializeBluetooth()
            if (!esp32MacAddress.isNullOrEmpty() && esp32Device == null && bluetoothAdapter?.isEnabled == true) {
                if (hasRequiredBluetoothPermissions(connectPermissionOnly = true)) {
                    prepareDeviceByMac()
                }
            }
        }
    }

    @SuppressLint("MissingPermission")
    private fun initializeBluetooth() {
        Log.d(TAG, "Initializing Bluetooth...")
        val bluetoothManager = applicationContext.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager?
        bluetoothAdapter = bluetoothManager?.adapter
        if (bluetoothAdapter == null) {
            Toast.makeText(this, "Bluetooth not supported on this device.", Toast.LENGTH_LONG).show()
            Log.e(TAG, "BluetoothAdapter is null. Device may not support Bluetooth.")
            tvStatus.text = "Status: Bluetooth not supported."
            updateUIState()
            return
        }
        if (!bluetoothAdapter!!.isEnabled) {
            Toast.makeText(this, "Bluetooth is OFF. Please turn it ON.", Toast.LENGTH_LONG).show()
            Log.w(TAG, "Bluetooth is not enabled.")
            tvStatus.text = "Status: Bluetooth OFF. Please enable."
        } else {
            Log.d(TAG, "Bluetooth Initialized and Enabled.")
            if (tvStatus.text.toString().contains("OFF") || tvStatus.text.toString().contains("Permissions denied")) {
                tvStatus.text = "Status: Ready. Scan or Prepare."
            }
        }
        updateUIState()
    }

    @SuppressLint("MissingPermission")
    private fun prepareDeviceByMac() {
        if (esp32MacAddress.isNullOrEmpty()){
            Log.w(TAG, "prepareDeviceByMac called but MAC address is not set.")
            tvStatus.text = "Status: MAC Address not set in code."
            updateUIState()
            return
        }
        if (!hasRequiredBluetoothPermissions(connectPermissionOnly = true)) {
            Log.w(TAG, "Prepare by MAC: Connect permission missing.")
            Toast.makeText(this, "Bluetooth Connect permission required for MAC.", Toast.LENGTH_SHORT).show()
            tvStatus.text = "Status: Connect permission needed."
            updateUIState()
            return
        }
        if (bluetoothAdapter == null || !bluetoothAdapter!!.isEnabled) {
            Toast.makeText(this, "Bluetooth is OFF. Cannot get device by MAC.", Toast.LENGTH_LONG).show()
            tvStatus.text = "Status: Bluetooth OFF for MAC prep."
            initializeBluetooth()
            updateUIState()
            return
        }

        try {
            Log.d(TAG, "Attempting to get remote device with MAC: $esp32MacAddress")
            esp32Device = bluetoothAdapter?.getRemoteDevice(esp32MacAddress)
            if (esp32Device != null) {
                val deviceName = try { esp32Device?.name ?: "Unknown Device (No Name)" } catch (e: SecurityException) {
                    Log.w(TAG, "SecurityException getting device name (CONNECT perm missing for name?): ${e.message}")
                    "Name N/A (Perm)"
                }
                tvStatus.text = "Status: Target '$deviceName' prepared."
                Log.i(TAG, "ESP32 device object created from MAC: ${esp32Device?.address}, Name: $deviceName")
            } else {
                tvStatus.text = "Status: Could not get device with MAC $esp32MacAddress"
                Toast.makeText(this, "Could not find ESP32 by MAC.", Toast.LENGTH_LONG).show()
                Log.w(TAG, "getRemoteDevice returned null for MAC: $esp32MacAddress")
            }
        } catch (e: IllegalArgumentException) {
            tvStatus.text = "Status: Invalid MAC Address format."
            Toast.makeText(this, "Invalid MAC Address: $esp32MacAddress", Toast.LENGTH_LONG).show()
            Log.e(TAG, "Invalid MAC address format: $esp32MacAddress", e)
        } catch (e: SecurityException) {
            Log.e(TAG, "SecurityException on getRemoteDevice/name for $esp32MacAddress: ${e.message}", e)
            tvStatus.text = "Status: Permission error getting device by MAC."
            Toast.makeText(this, "Permission error with MAC device. Check BLUETOOTH_CONNECT.", Toast.LENGTH_LONG).show()
        }
        updateUIState()
    }

    private val discoveredDevices = mutableListOf<BluetoothDevice>()
    private var isReceiverRegistered = false
    private val receiver = object : BroadcastReceiver() {
        @SuppressLint("MissingPermission")
        override fun onReceive(context: Context, intent: Intent) {
            val action = intent.action
            Log.d(TAG, "BroadcastReceiver onReceive: Action = $action")

            when (action) {
                BluetoothDevice.ACTION_FOUND -> {
                    val device: BluetoothDevice? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
                    } else {
                        @Suppress("DEPRECATION")
                        intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
                    }
                    device?.let {
                        val deviceName = try { it.name ?: "Unknown Device" } catch (e: SecurityException) { "Name N/A (Perm)" }
                        val deviceAddress = it.address

                        if (deviceName != "Name N/A (Perm)" && !deviceName.isNullOrBlank() && deviceName != "Unknown Device" &&
                            !discoveredDevices.any { d -> d.address == deviceAddress }) {
                            discoveredDevices.add(it)
                            Log.i(TAG, "Device Discovered: $deviceName - $deviceAddress")
                            if (deviceName.contains("ESP32", ignoreCase = true)) {
                                esp32Device = it
                                tvStatus.text = "Status: Found '$deviceName'. Ready to connect."
                                Toast.makeText(applicationContext, "Found $deviceName", Toast.LENGTH_SHORT).show()
                                Log.i(TAG, "Target ESP32 found by scan: $deviceName ($deviceAddress). Stopping discovery.")
                                bluetoothAdapter?.cancelDiscovery()
                                updateUIState()
                            }
                        }
                    }
                }
                BluetoothAdapter.ACTION_DISCOVERY_FINISHED -> {
                    Log.i(TAG, "Discovery Finished. Found ${discoveredDevices.size} devices.")
                    if (esp32Device == null && esp32MacAddress.isNullOrEmpty()) {
                        Toast.makeText(applicationContext, "ESP32 not found during scan.", Toast.LENGTH_LONG).show()
                        tvStatus.text = "Status: ESP32 not found via scan."
                    } else if (esp32Device == null && !esp32MacAddress.isNullOrEmpty()){
                        tvStatus.text = "Status: Target MAC not found in scan."
                    }
                    if (bluetoothSocket?.isConnected != true && esp32Device == null) {
                        // tvStatus.text = "Status: Discovery finished."
                    }
                    updateUIState()
                }
                BluetoothAdapter.ACTION_DISCOVERY_STARTED -> {
                    Log.i(TAG, "Discovery Started.")
                    tvStatus.text = "Status: Scanning for devices..."
                    discoveredDevices.clear()
                    esp32Device = null
                    updateUIState()
                }
            }
        }
    }

    @SuppressLint("MissingPermission")
    private fun startScanning() {
        if (!hasRequiredBluetoothPermissions(scanPermissionOnly = true)) {
            Log.w(TAG, "Scan attempted without scan/connect permissions.")
            Toast.makeText(this, "Bluetooth Scan & Location/Connect permissions required.", Toast.LENGTH_SHORT).show()
            tvStatus.text = "Status: Scan permission needed."
            updateUIState()
            return
        }
        if (bluetoothAdapter?.isDiscovering == true) {
            Log.d(TAG, "Already discovering. Cancelling previous discovery to restart.")
            bluetoothAdapter?.cancelDiscovery()
        }

        if (!isReceiverRegistered) {
            val filter = IntentFilter().apply {
                addAction(BluetoothDevice.ACTION_FOUND)
                addAction(BluetoothAdapter.ACTION_DISCOVERY_STARTED)
                addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)
            } else {
                registerReceiver(receiver, filter)
            }
            isReceiverRegistered = true
            Log.d(TAG, "Discovery BroadcastReceiver registered.")
        }

        discoveredDevices.clear()
        esp32Device = null
        updateUIState()

        val discoveryStarted = bluetoothAdapter?.startDiscovery()
        if (discoveryStarted == true) {
            Log.i(TAG, "Bluetooth discovery initiated successfully.")
            tvStatus.text = "Status: Scanning..."
        } else {
            tvStatus.text = "Status: Failed to start scan."
            Log.e(TAG, "Failed to start Bluetooth discovery. Check permissions and adapter state.")
            Toast.makeText(this, "Failed to start scan. Check BT/Permissions.", Toast.LENGTH_LONG).show()
        }
    }

    @SuppressLint("MissingPermission")
    private fun connectToDevice(device: BluetoothDevice) {
        if (!hasRequiredBluetoothPermissions(connectPermissionOnly = true)) {
            Log.w(TAG, "Connect attempted without connect permission.")
            Toast.makeText(this, "Bluetooth Connect permission required.", Toast.LENGTH_SHORT).show()
            tvStatus.text = "Status: Connect permission needed."
            updateUIState()
            return
        }

        if (bluetoothAdapter?.isDiscovering == true) {
            Log.d(TAG, "Connection attempt: Discovery active, cancelling it.")
            bluetoothAdapter?.cancelDiscovery()
        }

        val deviceNameStr = try { device.name ?: device.address } catch (e: SecurityException) { device.address }
        tvStatus.text = "Status: Connecting to '$deviceNameStr'..."
        Log.i(TAG, "Attempting to connect to ${device.address}")
        updateUIState()

        Thread {
            var tempSocket: BluetoothSocket? = null
            try {
                tempSocket = device.createRfcommSocketToServiceRecord(MY_UUID)
                tempSocket?.connect()

                bluetoothSocket = tempSocket
                outputStream = bluetoothSocket?.outputStream
                inputStream = bluetoothSocket?.inputStream

                Log.d(TAG, "CONNECTION THREAD: Socket.isConnected = ${bluetoothSocket?.isConnected}")

                runOnUiThread {
                    if (bluetoothSocket?.isConnected == true) {
                        tvStatus.text = "Status: Connected to '$deviceNameStr'"
                        Log.i(TAG, "Successfully connected to ${device.address}")
                        Toast.makeText(this@MainActivity, "Connected!", Toast.LENGTH_SHORT).show()
                    } else {
                        tvStatus.text = "Status: Connection attempt finished, but not connected."
                        Log.w(TAG, "Connection thread finished, but socket is not connected (unexpected).")
                    }
                    updateUIState()
                }
            } catch (e: IOException) {
                Log.e(TAG, "Socket connection failed for ${device.address}: ${e.message}", e)
                try { tempSocket?.close() } catch (closeE: IOException) { Log.e(TAG, "Could not close socket on connection error", closeE) }
                bluetoothSocket = null; outputStream = null; inputStream = null
                runOnUiThread {
                    tvStatus.text = "Status: Connection Failed"
                    Toast.makeText(this@MainActivity, "Connection Failed: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
                    updateUIState()
                }
            } catch (e: SecurityException) {
                Log.e(TAG, "SecurityException during connect for ${device.address}: ${e.message}", e)
                runOnUiThread {
                    tvStatus.text = "Status: Connection Permission Error"
                    Toast.makeText(this@MainActivity, "Connection Permission Error. Check BLUETOOTH_CONNECT.", Toast.LENGTH_LONG).show()
                    updateUIState()
                }
            }
        }.start()
    }

    private fun sendCommand(command: String) {
        if (outputStream == null) {
            Toast.makeText(this, "Not connected. Cannot send command.", Toast.LENGTH_SHORT).show()
            Log.w(TAG, "SendCommand: outputStream is null. Command '$command'.")
            if (bluetoothSocket?.isConnected != true) {
                updateUIState()
            }
            return
        }
        if (bluetoothSocket?.isConnected == true) {
            Thread {
                try {
                    outputStream?.write(command.toByteArray())
                    outputStream?.flush()
                    Log.d(TAG, "Sent command: $command")
                } catch (e: IOException) {
                    Log.e(TAG, "Error sending command '$command' in thread: ${e.message}", e)
                    runOnUiThread {
                        Toast.makeText(this, "Error sending. Disconnecting.", Toast.LENGTH_SHORT).show()
                        disconnect()
                    }
                }
            }.start()
        } else {
            Toast.makeText(this, "Not connected. Cannot send command.", Toast.LENGTH_SHORT).show()
            Log.w(TAG, "SendCommand called but socket not connected. Command: '$command'")
            updateUIState()
        }
    }

    private fun disconnect() {
        Log.d(TAG, "Disconnect called.")
        try {
            outputStream?.close()
            inputStream?.close()
            bluetoothSocket?.close()
        } catch (e: IOException) {
            Log.e(TAG, "Error closing Bluetooth resources: ${e.message}", e)
        } finally {
            outputStream = null
            inputStream = null
            bluetoothSocket = null
        }

        if (!isFinishing && !isDestroyed) {
            runOnUiThread {
                if (tvStatus.text.toString().startsWith("Status: Connected to")) {
                    tvStatus.text = "Status: Disconnected"
                }
                updateUIState()
            }
        }
        Log.i(TAG, "Bluetooth disconnected state updated.")
    }

    private fun updateUIState() {
        val isBtConnected = bluetoothSocket?.isConnected == true
        val isBtEnabled = bluetoothAdapter?.isEnabled == true
        val canScanOrPrepare = !isBtConnected && isBtEnabled && hasRequiredBluetoothPermissions(if (esp32MacAddress.isNullOrEmpty()) "scan" else "connect")
        val canConnect = !isBtConnected && isBtEnabled && esp32Device != null && hasRequiredBluetoothPermissions("connect")

        if (esp32MacAddress.isNullOrEmpty()) {
            btnScanOrPrepare.text = "Scan for ESP32"
        } else {
            val macSuffix = esp32MacAddress?.takeLast(5) ?: "MAC"
            btnScanOrPrepare.text = "Prepare ($macSuffix)"
        }
        btnScanOrPrepare.isEnabled = canScanOrPrepare

        btnConnect.isEnabled = canConnect
        btnDisconnect.isEnabled = isBtConnected

        val commandButtonsEnabled = isBtConnected
        btnForward.isEnabled = commandButtonsEnabled
        btnBackward.isEnabled = commandButtonsEnabled
        btnLeft.isEnabled = commandButtonsEnabled
        btnRight.isEnabled = commandButtonsEnabled
        btnStop.isEnabled = commandButtonsEnabled
        btnBlinkerLeft.isEnabled = commandButtonsEnabled
        btnBlinkerRight.isEnabled = commandButtonsEnabled

        if (isBtConnected) {
            btnForward.text = "FWD (ON)"
            btnBackward.text = "BWD (ON)"
            btnLeft.text = "LEFT (ON)"
            btnRight.text = "RIGHT (ON)"
            btnStop.text = "STOP (RDY)"
            btnBlinkerLeft.text = "Blinker L (ON)"
            btnBlinkerRight.text = "Blinker R (ON)"

            if (!tvStatus.text.toString().startsWith("Status: Connected to") && !tvStatus.text.toString().contains("Error")) {
                val deviceNameStr = try { esp32Device?.name ?: esp32Device?.address ?: "ESP32" }
                catch (e: SecurityException) { esp32Device?.address ?: "ESP32 (Perm)" }
                tvStatus.text = "Status: Connected to $deviceNameStr"
            }
        } else {
            btnForward.text = "Forward"
            btnBackward.text = "Backward"
            btnLeft.text = "Left"
            btnRight.text = "Right"
            btnStop.text = "Stop"
            btnBlinkerLeft.text = "Blinker L"
            btnBlinkerRight.text = "Blinker R"

            if (tvStatus.text.toString().startsWith("Status: Connected to")) {
                tvStatus.text = "Status: Disconnected"
            } else if (tvStatus.text.toString().isEmpty() || tvStatus.text.toString() == "Status:" ||
                (tvStatus.text.toString().contains("prepared", ignoreCase = true) && esp32Device == null) ) {
                tvStatus.text = "Status: Disconnected. Scan/Prepare."
            }
            if (!isBtEnabled && !tvStatus.text.toString().contains("Permissions denied") && !tvStatus.text.toString().contains("not supported")) {
                tvStatus.text = "Status: Bluetooth OFF. Please enable."
            }
        }
    }

    private fun hasRequiredBluetoothPermissions(type: String): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val hasConnect = ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED
            val hasScan = ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED
            return when (type) {
                "scan" -> hasScan && hasConnect
                "connect" -> hasConnect
                else -> hasConnect && hasScan
            }
        } else {
            return if (type == "scan") {
                ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
            } else {
                true
            }
        }
    }

    private fun hasRequiredBluetoothPermissions(scanPermissionOnly: Boolean = false, connectPermissionOnly: Boolean = false): Boolean {
        if (scanPermissionOnly) return hasRequiredBluetoothPermissions("scan")
        if (connectPermissionOnly) return hasRequiredBluetoothPermissions("connect")
        return hasRequiredBluetoothPermissions("scan") && hasRequiredBluetoothPermissions("connect")
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "onDestroy called. Cleaning up Bluetooth resources.")
        if (bluetoothAdapter?.isDiscovering == true) {
            try {
                // Check permission before attempting to cancel discovery
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    if (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED) {
                        bluetoothAdapter?.cancelDiscovery()
                        Log.d(TAG, "Discovery cancelled in onDestroy (API 31+).")
                    } else {
                        Log.w(TAG, "onDestroy: BLUETOOTH_SCAN permission not granted, cannot cancel discovery.")
                    }
                } else {
                    // For older versions, BLUETOOTH_ADMIN is manifest-only, direct call is fine
                    // if ACCESS_FINE_LOCATION was granted (which was needed to start it)
                    if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
                        ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_ADMIN) == PackageManager.PERMISSION_GRANTED /* Redundant but for completeness */) {
                        bluetoothAdapter?.cancelDiscovery()
                        Log.d(TAG, "Discovery cancelled in onDestroy (pre-API 31).")
                    } else {
                        Log.w(TAG, "onDestroy: Required permissions for cancelling discovery might be missing (pre-API 31).")
                    }
                }
            } catch (e: SecurityException) {
                Log.e(TAG, "onDestroy: SecurityException cancelling discovery: ${e.message}", e)
            } catch (e: Exception) { // Catch any other unexpected errors
                Log.e(TAG, "onDestroy: Exception cancelling discovery: ${e.message}", e)
            }
        }
        disconnect() // Close sockets and streams

        if (isReceiverRegistered) {
            try {
                unregisterReceiver(receiver)
                isReceiverRegistered = false
                Log.d(TAG, "BroadcastReceiver unregistered in onDestroy.")
            } catch (e: IllegalArgumentException) {
                // This can happen if the receiver was already unregistered or never registered.
                Log.w(TAG, "onDestroy: Receiver already unregistered or issue during unregister: ${e.message}")
            }
        }
        Log.i(TAG, "onDestroy finished.")
    }
}