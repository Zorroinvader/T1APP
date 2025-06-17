package com.example.bus // << YOUR PACKAGE NAME >>

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
import androidx.annotation.RequiresPermission
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.ui.semantics.text
import androidx.core.content.ContextCompat
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.util.UUID
import kotlin.io.path.name

class MainActivity : AppCompatActivity() {

    // --- UI Elements ---
    // Connection
    private lateinit var btnScanOrPrepare: Button
    private lateinit var btnConnect: Button
    private lateinit var btnDisconnect: Button
    private lateinit var tvStatus: TextView

    // Movement
    private lateinit var btnForward: Button
    private lateinit var btnBackward: Button
    private lateinit var btnLeft: Button
    private lateinit var btnRight: Button
    private lateinit var btnStop: Button
    private lateinit var btnCenterSteer: Button

    // Blinkers
    private lateinit var btnBlinkerLeftOn: Button
    private lateinit var btnBlinkerLeftOff: Button
    private lateinit var btnBlinkerRightOn: Button
    private lateinit var btnBlinkerRightOff: Button

    // Other Lights
    private lateinit var btnHeadlightsOn: Button
    private lateinit var btnHeadlightsOff: Button
    private lateinit var btnBacklightsOn: Button
    private lateinit var btnBacklightsOff: Button
    private lateinit var btnLichthupe: Button

    // Misc Controls
    private lateinit var btnAutoStopEnable: Button
    private lateinit var btnAutoStopDisable: Button


    // --- Bluetooth components and constants ---
    private var bluetoothAdapter: BluetoothAdapter? = null
    private var bluetoothSocket: BluetoothSocket? = null
    private var outputStream: OutputStream? = null
    private var inputStream: InputStream? = null

    private val MY_UUID: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB") // Standard SPP UUID
    private var esp32Device: BluetoothDevice? = null
    private var esp32MacAddress: String? = "E0:5A:1B:E4:DC:CE" // <<< !!! REPLACE WITH YOUR ESP32 MAC ADDRESS !!!
    // You can leave this null or empty to force scan mode first.
    private val TAG = "BluetoothApp"

    private val requestBluetoothPermissions =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
            var allRequiredPermissionsGranted = true
            // Determine required permissions based on SDK version
            val requiredPermissionsList = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                listOf(Manifest.permission.BLUETOOTH_CONNECT, Manifest.permission.BLUETOOTH_SCAN)
            } else {
                listOf(Manifest.permission.ACCESS_FINE_LOCATION) // For discovery on older versions
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
                // Auto-prepare if MAC is set and permissions are now granted
                if (!esp32MacAddress.isNullOrEmpty() && esp32Device == null && bluetoothAdapter?.isEnabled == true) {
                    if (hasRequiredBluetoothPermissions("connect")) { // Simpler check for connect
                        prepareDeviceByMac()
                    } else {
                        Log.w(TAG, "Permissions granted, but connect permission check failed for auto-prepare.")
                    }
                }
            } else {
                Log.e(TAG, "Not all required Bluetooth permissions were granted.")
                Toast.makeText(this, "Bluetooth & relevant Location permissions are required.", Toast.LENGTH_LONG).show()
                tvStatus.text = "Status: Permissions denied."
                updateUIState()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        Log.d(TAG, "onCreate: setContentView called.")

        try {
            Log.d(TAG, "onCreate: Initializing UI elements...")
            // Connection
            btnScanOrPrepare = findViewById(R.id.btnScan) // Combined button ID
            btnConnect = findViewById(R.id.btnConnect)
            btnDisconnect = findViewById(R.id.btnDisconnect)
            tvStatus = findViewById(R.id.tvStatus)

            // Movement
            btnForward = findViewById(R.id.btnForward)
            btnBackward = findViewById(R.id.btnBackward)
            btnLeft = findViewById(R.id.btnLeft)
            btnRight = findViewById(R.id.btnRight)
            btnStop = findViewById(R.id.btnStop)
            btnCenterSteer = findViewById(R.id.btnCenterSteer)

            // Blinkers
            btnBlinkerLeftOn = findViewById(R.id.btnBlinkerLeftOn)
            btnBlinkerLeftOff = findViewById(R.id.btnBlinkerLeftOff)
            btnBlinkerRightOn = findViewById(R.id.btnBlinkerRightOn)
            btnBlinkerRightOff = findViewById(R.id.btnBlinkerRightOff)

            // Other Lights
            btnHeadlightsOn = findViewById(R.id.btnHeadlightsOn)
            btnHeadlightsOff = findViewById(R.id.btnHeadlightsOff)
            btnBacklightsOn = findViewById(R.id.btnBacklightsOn)
            btnBacklightsOff = findViewById(R.id.btnBacklightsOff)
            btnLichthupe = findViewById(R.id.btnLichthupe)

            // Misc
            btnAutoStopEnable = findViewById(R.id.btnAutoStopEnable)
            btnAutoStopDisable = findViewById(R.id.btnAutoStopDisable)

            Log.d(TAG, "onCreate: All findViewById calls completed.")

        } catch (e: Exception) {
            Log.e(TAG, "onCreate: Exception during findViewById: ${e.message}", e)
            Toast.makeText(this, "Error initializing UI. Check Logcat.", Toast.LENGTH_LONG).show()
            return // Exit onCreate if UI elements are not found
        }

        checkAndRequestPermissions()

        // --- Setup Button Listeners ---
        btnScanOrPrepare.setOnClickListener {
            if (esp32MacAddress.isNullOrEmpty()) { // Scan mode
                if (bluetoothAdapter == null) initializeBluetooth()
                if (bluetoothAdapter?.isEnabled == true) {
                    if (hasRequiredBluetoothPermissions("scan")) {
                        startScanning()
                    } else {
                        Toast.makeText(this, "Scan permissions needed.", Toast.LENGTH_SHORT).show()
                        checkAndRequestPermissions()
                    }
                } else {
                    Toast.makeText(this, "Bluetooth not enabled.", Toast.LENGTH_LONG).show()
                    initializeBluetooth()
                }
            } else { // Prepare by MAC mode
                if (bluetoothAdapter == null || !bluetoothAdapter!!.isEnabled) {
                    Toast.makeText(this, "Bluetooth not enabled for MAC prepare.", Toast.LENGTH_LONG).show()
                    initializeBluetooth()
                    return@setOnClickListener
                }
                if (hasRequiredBluetoothPermissions("connect")) {
                    prepareDeviceByMac()
                } else {
                    Toast.makeText(this, "Connect permission needed for MAC.", Toast.LENGTH_SHORT).show()
                    checkAndRequestPermissions()
                }
            }
        }

        btnConnect.setOnClickListener {
            if (esp32Device != null) {
                if (hasRequiredBluetoothPermissions("connect")) {
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

        // Movement commands
        btnForward.setOnClickListener { sendCommand("F") }
        btnBackward.setOnClickListener { sendCommand("B") }
        btnLeft.setOnClickListener { sendCommand("L") }
        btnRight.setOnClickListener { sendCommand("R") }
        btnStop.setOnClickListener { sendCommand("S") }
        btnCenterSteer.setOnClickListener { sendCommand("C") }

        // Blinker commands
        btnBlinkerLeftOn.setOnClickListener { sendCommand("1") }
        btnBlinkerLeftOff.setOnClickListener { sendCommand("2") }
        btnBlinkerRightOn.setOnClickListener { sendCommand("3") }
        btnBlinkerRightOff.setOnClickListener { sendCommand("4") }

        // Headlight, Backlight, Lichthupe commands
        btnHeadlightsOn.setOnClickListener { sendCommand("5") }
        btnHeadlightsOff.setOnClickListener { sendCommand("6") }
        btnBacklightsOn.setOnClickListener { sendCommand("7") }
        btnBacklightsOff.setOnClickListener { sendCommand("8") }
        btnLichthupe.setOnClickListener { sendCommand("H") }

        // Auto-Stop Override commands (mapped to ESP32 'A' and 'D' as per your ESP code)
        btnAutoStopEnable.setOnClickListener { sendCommand("A") } // ESP: appGasErlaubt = true (sensor controls motor)
        btnAutoStopDisable.setOnClickListener { sendCommand("D") } // ESP: appGasErlaubt = false (app forces motor stop)

        updateUIState() // Initial UI state update
        Log.d(TAG, "onCreate: Finished.")
    }

    private fun checkAndRequestPermissions() {
        val permissionsToRequest = mutableListOf<String>()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) { // Android 12+
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_SCAN) != PackageManager.PERMISSION_GRANTED) {
                permissionsToRequest.add(Manifest.permission.BLUETOOTH_SCAN)
            }
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                permissionsToRequest.add(Manifest.permission.BLUETOOTH_CONNECT)
            }
        } else { // Android 6-11
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                // ACCESS_COARSE_LOCATION might be enough for discovery in some cases, but FINE is more robust
                permissionsToRequest.add(Manifest.permission.ACCESS_FINE_LOCATION)
            }
            // For older Android versions, BLUETOOTH and BLUETOOTH_ADMIN are manifest permissions, not runtime.
        }

        if (permissionsToRequest.isNotEmpty()) {
            Log.d(TAG, "Requesting permissions: ${permissionsToRequest.joinToString()}")
            requestBluetoothPermissions.launch(permissionsToRequest.toTypedArray())
        } else {
            Log.d(TAG, "All necessary runtime permissions already granted.")
            initializeBluetooth()
            // Auto-prepare if MAC is set, permissions are granted, and not yet prepared
            if (!esp32MacAddress.isNullOrEmpty() && esp32Device == null && bluetoothAdapter?.isEnabled == true) {
                if (hasRequiredBluetoothPermissions("connect")) {
                    prepareDeviceByMac()
                }
            }
        }
    }

    @SuppressLint("MissingPermission") // Permissions are checked by hasRequiredBluetoothPermissions or requested
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
            // Consider prompting to enable Bluetooth:
            // Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE).also { startActivityForResult(it, YOUR_REQUEST_CODE_FOR_BT_ENABLE) }
        } else {
            Log.d(TAG, "Bluetooth Initialized and Enabled.")
            if (tvStatus.text.toString().contains("OFF") || tvStatus.text.toString().contains("not supported")) {
                tvStatus.text = "Status: Ready. Scan or Prepare."
            }
        }
        updateUIState()
    }

    @SuppressLint("MissingPermission") // Permissions checked by hasRequiredBluetoothPermissions
    private fun prepareDeviceByMac() {
        if (esp32MacAddress.isNullOrEmpty()){
            Log.w(TAG, "prepareDeviceByMac called but MAC address is not set.")
            tvStatus.text = "Status: MAC Address not set."
            updateUIState()
            return
        }
        if (!hasRequiredBluetoothPermissions("connect")) {
            Log.w(TAG, "Prepare by MAC: Connect permission missing.")
            Toast.makeText(this, "Bluetooth Connect permission required for MAC.", Toast.LENGTH_SHORT).show()
            tvStatus.text = "Status: Connect permission needed."
            checkAndRequestPermissions() // Ask again
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
            esp32Device = bluetoothAdapter?.getRemoteDevice(esp32MacAddress) // Needs BLUETOOTH_CONNECT on API 31+
            if (esp32Device != null) {
                // Getting name also needs BLUETOOTH_CONNECT on API 31+
                val deviceName = try { esp32Device?.name ?: "Unknown (No Name)" } catch (e: SecurityException) { "Name N/A (Perm)" }
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
        } catch (e: SecurityException) { // Catch SecurityException for getRemoteDevice or getName
            Log.e(TAG, "SecurityException on getRemoteDevice/name for $esp32MacAddress: ${e.message}", e)
            tvStatus.text = "Status: Permission error for MAC device."
            Toast.makeText(this, "Permission error with MAC device. Check BLUETOOTH_CONNECT.", Toast.LENGTH_LONG).show()
        }
        updateUIState()
    }

    private val discoveredDevices = mutableListOf<BluetoothDevice>()
    private var isReceiverRegistered = false
    private val receiver = object : BroadcastReceiver() {
        @SuppressLint("MissingPermission") // Permissions for name/address checked by hasRequiredBluetoothPermissions before scan/connect
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
                        // Getting name requires BLUETOOTH_CONNECT on API 31+ even after discovery
                        // For discovery, BLUETOOTH_SCAN is primary.
                        val deviceName = try { it.name ?: "Unknown Device" } catch (e: SecurityException) { "Name N/A (Perm)" }
                        val deviceAddress = it.address // Address is usually available

                        if (deviceName != "Name N/A (Perm)" && !deviceName.isNullOrBlank() && deviceName != "Unknown Device" &&
                            !discoveredDevices.any { d -> d.address == deviceAddress }) {
                            discoveredDevices.add(it)
                            Log.i(TAG, "Device Discovered: $deviceName - $deviceAddress")
                            if (deviceName.contains("ESP32_RC_Car", ignoreCase = true) || deviceName.contains("ESP32", ignoreCase = true) ) {
                                esp32Device = it
                                val foundName = try { it.name ?: it.address } catch (e:SecurityException) { it.address }
                                tvStatus.text = "Status: Found '$foundName'. Ready to connect."
                                Toast.makeText(applicationContext, "Found $foundName", Toast.LENGTH_SHORT).show()
                                Log.i(TAG, "Target ESP32 found by scan: $foundName ($deviceAddress). Stopping discovery.")
                                bluetoothAdapter?.cancelDiscovery() // Needs BLUETOOTH_SCAN on API 31+
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

    @SuppressLint("MissingPermission") // Permissions are checked by hasRequiredBluetoothPermissions
    private fun startScanning() {
        if (!hasRequiredBluetoothPermissions("scan")) {
            Log.w(TAG, "Scan attempted without required permissions.")
            Toast.makeText(this, "Bluetooth Scan/Connect permissions required.", Toast.LENGTH_SHORT).show()
            tvStatus.text = "Status: Scan permission needed."
            checkAndRequestPermissions() // Ask again
            updateUIState()
            return
        }
        if (bluetoothAdapter?.isDiscovering == true) { // Needs BLUETOOTH_SCAN on API 31+
            Log.d(TAG, "Already discovering. Cancelling previous discovery to restart.")
            bluetoothAdapter?.cancelDiscovery() // Needs BLUETOOTH_SCAN on API 31+
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
        updateUIState() // Show scanning state

        val discoveryStarted = bluetoothAdapter?.startDiscovery() // Needs BLUETOOTH_SCAN on API 31+
        if (discoveryStarted == true) {
            Log.i(TAG, "Bluetooth discovery initiated successfully.")
            tvStatus.text = "Status: Scanning..."
        } else {
            tvStatus.text = "Status: Failed to start scan."
            Log.e(TAG, "Failed to start Bluetooth discovery. Check permissions and adapter state.")
            Toast.makeText(this, "Failed to start scan. Check BT/Permissions.", Toast.LENGTH_LONG).show()
        }
        updateUIState() // Reflect scanning state
    }

    @SuppressLint("MissingPermission") // Permissions are checked by hasRequiredBluetoothPermissions
    private fun connectToDevice(device: BluetoothDevice) {
        if (!hasRequiredBluetoothPermissions("connect")) {
            Log.w(TAG, "Connect attempted without connect permission.")
            Toast.makeText(this, "Bluetooth Connect permission required.", Toast.LENGTH_SHORT).show()
            tvStatus.text = "Status: Connect permission needed."
            checkAndRequestPermissions() // Ask again
            updateUIState()
            return
        }

        if (bluetoothAdapter?.isDiscovering == true) { // Needs BLUETOOTH_SCAN on API 31+
            Log.d(TAG, "Connection attempt: Discovery active, cancelling it.")
            bluetoothAdapter?.cancelDiscovery() // Needs BLUETOOTH_SCAN on API 31+
        }

        val deviceNameStr = try { device.name ?: device.address } catch (e: SecurityException) { device.address } // Name needs BLUETOOTH_CONNECT
        tvStatus.text = "Status: Connecting to '$deviceNameStr'..."
        Log.i(TAG, "Attempting to connect to ${device.address}")
        updateUIState()

        Thread {
            var tempSocket: BluetoothSocket? = null
            try {
                // Needs BLUETOOTH_CONNECT on API 31+
                tempSocket = device.createRfcommSocketToServiceRecord(MY_UUID)
                tempSocket?.connect() // Blocking call, needs BLUETOOTH_CONNECT

                bluetoothSocket = tempSocket
                outputStream = bluetoothSocket?.outputStream
                inputStream = bluetoothSocket?.inputStream

                Log.d(TAG, "CONNECTION THREAD: Socket.isConnected = ${bluetoothSocket?.isConnected}")

                runOnUiThread {
                    if (bluetoothSocket?.isConnected == true) {
                        val connectedName = try { device.name ?: device.address } catch (e: SecurityException) { device.address }
                        tvStatus.text = "Status: Connected to '$connectedName'"
                        Log.i(TAG, "Successfully connected to ${device.address}")
                        Toast.makeText(this@MainActivity, "Connected!", Toast.LENGTH_SHORT).show()
                    } else {
                        tvStatus.text = "Status: Connection failed (socket not connected)."
                        Log.w(TAG, "Connection thread finished, but socket is not connected.")
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
            } catch (e: SecurityException) { // Catch SecurityException for createRfcommSocket or connect
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
        if (outputStream == null || bluetoothSocket?.isConnected != true) {
            Toast.makeText(this, "Not connected. Cannot send command.", Toast.LENGTH_SHORT).show()
            Log.w(TAG, "SendCommand: Not connected. Command '$command'.")
            if (bluetoothSocket?.isConnected != true) {
                updateUIState() // Ensure UI reflects disconnected state
            }
            return
        }
        Thread {
            try {
                outputStream?.write(command.toByteArray())
                outputStream?.flush()
                Log.d(TAG, "Sent command: $command")
            } catch (e: IOException) {
                Log.e(TAG, "Error sending command '$command': ${e.message}", e)
                runOnUiThread {
                    Toast.makeText(this, "Error sending. Disconnecting.", Toast.LENGTH_SHORT).show()
                    disconnect() // Disconnect on send error
                }
            }
        }.start()
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
            // Consider if esp32Device should be nulled here or only on new scan/prepare
            // esp32Device = null;
        }

        if (!isFinishing && !isDestroyed) { // Avoid UI updates if activity is finishing
            runOnUiThread {
                if (tvStatus.text.toString().startsWith("Status: Connected to")) {
                    tvStatus.text = "Status: Disconnected"
                }
                updateUIState()
            }
        }
        Log.i(TAG, "Bluetooth disconnected state updated.")
    }

    @RequiresPermission(Manifest.permission.BLUETOOTH_SCAN)
    private fun updateUIState() {
        val isBtConnected = bluetoothSocket?.isConnected == true
        val isBtEnabled = bluetoothAdapter?.isEnabled == true

        // Determine if scan or prepare by MAC should be enabled
        val canScan = esp32MacAddress.isNullOrEmpty() && isBtEnabled && hasRequiredBluetoothPermissions("scan") && !isBtConnected
        val canPrepareMac = !esp32MacAddress.isNullOrEmpty() && isBtEnabled && hasRequiredBluetoothPermissions("connect") && !isBtConnected
        btnScanOrPrepare.isEnabled = canScan || canPrepareMac

        if (esp32MacAddress.isNullOrEmpty()) {
            btnScanOrPrepare.text = "Scan for ESP32"
        } else {
            val macSuffix = esp32MacAddress?.takeLast(5) ?: "MAC"
            btnScanOrPrepare.text = "Prepare ($macSuffix)"
        }

        btnConnect.isEnabled = !isBtConnected && isBtEnabled && esp32Device != null && hasRequiredBluetoothPermissions("connect")
        btnDisconnect.isEnabled = isBtConnected

        // Enable/disable all command buttons based on connection state
        val commandButtonsEnabled = isBtConnected
        btnForward.isEnabled = commandButtonsEnabled
        btnBackward.isEnabled = commandButtonsEnabled
        btnLeft.isEnabled = commandButtonsEnabled
        btnRight.isEnabled = commandButtonsEnabled
        btnStop.isEnabled = commandButtonsEnabled
        btnCenterSteer.isEnabled = commandButtonsEnabled

        btnBlinkerLeftOn.isEnabled = commandButtonsEnabled
        btnBlinkerLeftOff.isEnabled = commandButtonsEnabled
        btnBlinkerRightOn.isEnabled = commandButtonsEnabled
        btnBlinkerRightOff.isEnabled = commandButtonsEnabled

        btnHeadlightsOn.isEnabled = commandButtonsEnabled
        btnHeadlightsOff.isEnabled = commandButtonsEnabled
        btnBacklightsOn.isEnabled = commandButtonsEnabled
        btnBacklightsOff.isEnabled = commandButtonsEnabled
        btnLichthupe.isEnabled = commandButtonsEnabled

        btnAutoStopEnable.isEnabled = commandButtonsEnabled
        btnAutoStopDisable.isEnabled = commandButtonsEnabled

        // Update status text
        if (isBtConnected) {
            if (!tvStatus.text.toString().startsWith("Status: Connected to") && !tvStatus.text.toString().contains("Error")) {
                val deviceNameStr = try { esp32Device?.name ?: esp32Device?.address ?: "ESP32" }
                catch (e: SecurityException) { esp32Device?.address ?: "ESP32 (Perm)" }
                tvStatus.text = "Status: Connected to $deviceNameStr"
            }
        } else {
            if (tvStatus.text.toString().startsWith("Status: Connected to")) {
                tvStatus.text = "Status: Disconnected"
            } else if (tvStatus.text.toString().isEmpty() || tvStatus.text.toString() == "Status:" ||
                (tvStatus.text.toString().contains("prepared", ignoreCase = true) && esp32Device == null && !tvStatus.text.toString().contains("Scanning")) ) {
                tvStatus.text = "Status: Disconnected. Scan/Prepare."
            }
            if (!isBtEnabled && !tvStatus.text.toString().contains("Permissions denied") && !tvStatus.text.toString().contains("not supported") && !tvStatus.text.toString().contains("Scanning")) {
                tvStatus.text = "Status: Bluetooth OFF. Please enable."
            }
            if (bluetoothAdapter?.isDiscovering == true){
                tvStatus.text = "Status: Scanning for devices..."
            }
        }
    }

    /**
     * Helper to check for required Bluetooth permissions.
     * @param type "scan" for discovery-related actions, "connect" for connection-related actions.
     */
    private fun hasRequiredBluetoothPermissions(type: String): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) { // Android 12+ (API 31+)
            val hasConnect = ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED
            val hasScan = ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED
            return when (type.lowercase()) {
                "scan" -> hasScan // For starting discovery, getting name during discovery (sometimes requires connect too)
                "connect" -> hasConnect // For getRemoteDevice, createRfcommSocket, connect, getName (outside discovery)
                else -> hasConnect && hasScan // Default conservative check
            }
        } else { // Android 6 (API 23) to Android 11 (API 30)
            // BLUETOOTH and BLUETOOTH_ADMIN are manifest permissions, not runtime.
            // ACCESS_FINE_LOCATION is needed for Bluetooth discovery.
            return ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "onDestroy: Cleaning up resources.")
        if (isReceiverRegistered) {
            try {
                unregisterReceiver(receiver)
                isReceiverRegistered = false
                Log.d(TAG, "Discovery BroadcastReceiver unregistered.")
            } catch (e: IllegalArgumentException) {
                Log.e(TAG, "Error unregistering receiver: ${e.message}", e)
            }
        }
        disconnect() // Ensure Bluetooth resources are released
    }
}