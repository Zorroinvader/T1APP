package com.example.bus // ★★★ Correct package name here ★★★

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
import androidx.core.content.ContextCompat
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.util.UUID

// ★★★ Ensure this class name and file name is MainActivity.kt ★★★
class MainActivity : AppCompatActivity() {

    private lateinit var btnScan: Button
    private lateinit var btnConnect: Button
    private lateinit var btnDisconnect: Button
    private lateinit var btnForward: Button
    private lateinit var btnBackward: Button
    private lateinit var btnLeft: Button
    private lateinit var btnRight: Button
    private lateinit var btnStop: Button
    private lateinit var tvStatus: TextView

    private var bluetoothAdapter: BluetoothAdapter? = null
    private var bluetoothSocket: BluetoothSocket? = null
    private var outputStream: OutputStream? = null
    private var inputStream: InputStream? = null

    private val MY_UUID: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")
    private var esp32Device: BluetoothDevice? = null
    // ★★★ IMPORTANT: Set your ESP32's actual MAC address here for initial testing if not using scanning first ★★★
    // private var esp32MacAddress: String? = "XX:XX:XX:XX:XX:XX"
    private var esp32MacAddress: String? = null // Or leave as null to rely on scanning

    private val TAG = "BluetoothApp"

    private val requestBluetoothPermissions =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
            var allGranted = true
            permissions.entries.forEach {
                if (!it.value) {
                    allGranted = false
                }
            }
            if (allGranted) {
                Log.d(TAG, "All Bluetooth permissions granted.")
                initializeBluetooth()
            } else {
                Log.e(TAG, "Not all Bluetooth permissions were granted.")
                Toast.makeText(this, "Bluetooth permissions are required.", Toast.LENGTH_LONG).show()
            }
        }

    private val requestLocationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted: Boolean ->
            if (isGranted) {
                Log.d(TAG, "Location permission granted for Bluetooth scanning.")
                // You might want to trigger scanning here if it was waiting for this
            } else {
                Log.e(TAG, "Location permission denied for Bluetooth scanning.")
                Toast.makeText(this, "Location permission is recommended for Bluetooth scanning.", Toast.LENGTH_SHORT).show()
            }
        }


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // ★★★ Make sure your layout file is named activity_main.xml or change this line ★★★
        setContentView(R.layout.activity_main)

        btnScan = findViewById(R.id.btnScan)
        btnConnect = findViewById(R.id.btnConnect)
        btnDisconnect = findViewById(R.id.btnDisconnect)
        btnForward = findViewById(R.id.btnForward)
        btnBackward = findViewById(R.id.btnBackward)
        btnLeft = findViewById(R.id.btnLeft)
        btnRight = findViewById(R.id.btnRight)
        btnStop = findViewById(R.id.btnStop)
        tvStatus = findViewById(R.id.tvStatus)

        checkAndRequestPermissions()

        btnScan.setOnClickListener {
            if (esp32MacAddress.isNullOrEmpty()) {
                Toast.makeText(this, "ESP32 MAC address not set. Starting scan...", Toast.LENGTH_SHORT).show()
                startScanning()
            } else {
                if (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED || Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
                    try {
                        esp32Device = bluetoothAdapter?.getRemoteDevice(esp32MacAddress)
                        if (esp32Device != null) {
                            tvStatus.text = "Status: Target ESP32 identified. Ready to connect."
                            btnConnect.isEnabled = true
                        } else {
                            tvStatus.text = "Status: Could not find ESP32 with MAC $esp32MacAddress"
                            Toast.makeText(this, "Could not find ESP32. Check MAC address or scan.", Toast.LENGTH_LONG).show()
                        }
                    } catch (e: IllegalArgumentException) {
                        tvStatus.text = "Status: Invalid MAC Address provided."
                        Toast.makeText(this, "Invalid MAC address format.", Toast.LENGTH_LONG).show()
                    }
                } else {
                    Toast.makeText(this, "Bluetooth Connect permission needed.", Toast.LENGTH_SHORT).show()
                    // Optionally, request BLUETOOTH_CONNECT again here if it makes sense in your flow
                }
            }
        }

        btnConnect.setOnClickListener {
            if (esp32Device != null) {
                connectToDevice(esp32Device!!)
            } else {
                Toast.makeText(this, "No ESP32 device selected/found. Please scan first.", Toast.LENGTH_SHORT).show()
            }
        }

        btnDisconnect.setOnClickListener {
            disconnect()
        }

        btnForward.setOnClickListener { sendCommand("F") }
        btnBackward.setOnClickListener { sendCommand("B") }
        btnLeft.setOnClickListener { sendCommand("L") }
        btnRight.setOnClickListener { sendCommand("R") }
        btnStop.setOnClickListener { sendCommand("S") }

        updateUIState()
    }

    private fun checkAndRequestPermissions() {
        val permissionsToRequest = kotlin.collections.mutableListOf<String>()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_SCAN) != PackageManager.PERMISSION_GRANTED) {
                permissionsToRequest.add(Manifest.permission.BLUETOOTH_SCAN)
            }
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                permissionsToRequest.add(Manifest.permission.BLUETOOTH_CONNECT)
            }
        } else { // For Android versions older than S (12)
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH) != PackageManager.PERMISSION_GRANTED) {
                permissionsToRequest.add(Manifest.permission.BLUETOOTH)
            }
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_ADMIN) != PackageManager.PERMISSION_GRANTED) {
                permissionsToRequest.add(Manifest.permission.BLUETOOTH_ADMIN)
            }
        }

        // Location permission for scanning (especially for older Android versions)
        // You might decide to always ask for fine location if your core app feature relies on scanning.
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            permissionsToRequest.add(Manifest.permission.ACCESS_FINE_LOCATION)
        }
        // ACCESS_COARSE_LOCATION is also often requested with FINE
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            permissionsToRequest.add(Manifest.permission.ACCESS_COARSE_LOCATION)
        }


        if (permissionsToRequest.isNotEmpty()) {
            Log.d(TAG, "Requesting permissions: ${permissionsToRequest.joinToString()}")
            requestBluetoothPermissions.launch(permissionsToRequest.toTypedArray())
        } else {
            Log.d(TAG, "All necessary permissions already granted.")
            initializeBluetooth()
        }
    }

    @SuppressLint("MissingPermission")
    private fun initializeBluetooth() {
        val bluetoothManager = getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager?
        bluetoothAdapter = bluetoothManager?.adapter

        if (bluetoothAdapter == null) {
            Toast.makeText(this, "Bluetooth not supported on this device", Toast.LENGTH_LONG).show()
            // Consider disabling Bluetooth related UI elements or exiting
            // finish()
            return
        }

        if (!bluetoothAdapter!!.isEnabled) {
            Toast.makeText(this, "Please enable Bluetooth", Toast.LENGTH_LONG).show()
            // You could launch an intent to request enabling Bluetooth here if desired
            // val enableBtIntent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
            // requestEnableBluetooth.launch(enableBtIntent) // You'd need another ActivityResultLauncher for this
        }
        Log.d(TAG, "Bluetooth Initialized. Adapter: $bluetoothAdapter, IsEnabled: ${bluetoothAdapter?.isEnabled}")
    }

    private val discoveredDevices = kotlin.collections.mutableListOf<BluetoothDevice>()
    private val receiver = object : BroadcastReceiver() {
        @SuppressLint("MissingPermission")
        override fun onReceive(context: Context, intent: Intent) {
            val action: String? = intent.action
            Log.d(TAG, "Bluetooth Receiver: Action Received: $action")
            when (action) {
                BluetoothDevice.ACTION_FOUND -> {
                    val device: BluetoothDevice? =
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
                        } else {
                            intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
                        }
                    device?.let {
                        val deviceName = it.name
                        val deviceAddress = it.address
                        if (!discoveredDevices.any { d -> d.address == deviceAddress } && deviceName != null) {
                            discoveredDevices.add(it)
                            Log.i(TAG, "Device Found: $deviceName - $deviceAddress")
                            // ★★★ CHANGE "ESP32_T1_BULLI" to your ESP32's actual Bluetooth name ★★★
                            if (deviceName.equals("ESP32_T1_BULLI", ignoreCase = true)) {
                                esp32Device = it
                                esp32MacAddress = it.address // Store the MAC address
                                tvStatus.text = "Status: Found $deviceName. Ready to connect."
                                btnConnect.isEnabled = true
                                bluetoothAdapter?.cancelDiscovery()
                                Toast.makeText(applicationContext, "Found $deviceName", Toast.LENGTH_SHORT).show()
                                Log.i(TAG, "Target ESP32 found: $deviceName. Stopping discovery.")
                            }
                        }
                    }
                }
                BluetoothAdapter.ACTION_DISCOVERY_FINISHED -> {
                    Log.i(TAG, "Discovery Finished. Found ${discoveredDevices.size} devices.")
                    if (esp32Device == null) { // If target not found after full scan
                        Toast.makeText(applicationContext, "ESP32 device not found. Check if it's on and discoverable.", Toast.LENGTH_LONG).show()
                        tvStatus.text = "Status: ESP32 not found."
                    }
                }
                BluetoothAdapter.ACTION_DISCOVERY_STARTED -> {
                    Log.i(TAG, "Discovery Started.")
                    tvStatus.text = "Status: Scanning for devices..."
                }
            }
        }
    }

    @SuppressLint("MissingPermission")
    private fun startScanning() {
        if (!hasRequiredBluetoothPermissions()) {
            Toast.makeText(this, "Required Bluetooth permissions not granted.", Toast.LENGTH_SHORT).show();
            checkAndRequestPermissions() // Re-trigger permission check
            return
        }
        if (bluetoothAdapter == null ) {
            Toast.makeText(this, "Bluetooth Adapter not available.", Toast.LENGTH_SHORT).show();
            initializeBluetooth() // Try to re-initialize
            if(bluetoothAdapter == null) return // if still null, exit
        }
        if (!bluetoothAdapter!!.isEnabled) {
            Toast.makeText(this, "Bluetooth is not enabled.", Toast.LENGTH_SHORT).show();
            // Optionally, prompt user to enable Bluetooth
            return
        }


        if (bluetoothAdapter?.isDiscovering == true) {
            bluetoothAdapter?.cancelDiscovery()
            Log.d(TAG, "Cancelled previous discovery.")
        }
        discoveredDevices.clear()

        val filter = IntentFilter()
        filter.addAction(BluetoothDevice.ACTION_FOUND)
        filter.addAction(BluetoothAdapter.ACTION_DISCOVERY_STARTED)
        filter.addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(receiver, filter, Context.RECEIVER_EXPORTED) // Or RECEIVER_NOT_EXPORTED if appropriate
        } else {
            registerReceiver(receiver, filter)
        }


        val discoveryStarted = bluetoothAdapter?.startDiscovery()
        if (discoveryStarted == true) {
            tvStatus.text = "Status: Scanning for devices..."
            Log.i(TAG, "Bluetooth discovery started successfully.")
            Toast.makeText(applicationContext, "Scanning for ESP32...", Toast.LENGTH_SHORT).show()
        } else {
            tvStatus.text = "Status: Failed to start scan."
            Log.e(TAG, "Failed to start Bluetooth discovery. Check permissions and adapter state.")
            Toast.makeText(applicationContext, "Failed to start scan. Check logs.", Toast.LENGTH_LONG).show()
            // Unregister receiver if discovery didn't start to prevent leaks
            try { unregisterReceiver(receiver) } catch (e: java.lang.Exception) { Log.w(TAG, "Receiver already unregistered or not registered.")}

        }
    }


    @SuppressLint("MissingPermission")
    private fun connectToDevice(device: BluetoothDevice) {
        if (!hasRequiredBluetoothPermissions(connectPermissionOnly = true)) {
            Toast.makeText(this, "Bluetooth Connect permission not granted.", Toast.LENGTH_SHORT).show();
            checkAndRequestPermissions() // Re-trigger
            return
        }
        if (bluetoothAdapter == null || !bluetoothAdapter!!.isEnabled) {
            Toast.makeText(this, "Bluetooth is not enabled.", Toast.LENGTH_SHORT).show();
            return
        }

        bluetoothAdapter?.cancelDiscovery()
        val deviceName = device.name ?: device.address
        tvStatus.text = "Status: Connecting to $deviceName..."
        Log.i(TAG, "Attempting to connect to ${device.address}")

        Thread {
            try {
                bluetoothSocket = device.createRfcommSocketToServiceRecord(MY_UUID)
                bluetoothSocket?.connect()

                outputStream = bluetoothSocket?.outputStream
                inputStream = bluetoothSocket?.inputStream

                runOnUiThread {
                    tvStatus.text = "Status: Connected to $deviceName"
                    Log.i(TAG, "Successfully connected to ${device.address}")
                    Toast.makeText(this, "Connected!", Toast.LENGTH_SHORT).show()
                    updateUIState()
                }

            } catch (e: IOException) {
                Log.e(TAG, "Socket connection failed for device ${device.address}: ${e.message}", e)
                try {
                    bluetoothSocket?.close() // Close the socket on connection failure
                } catch (closeException: IOException) {
                    Log.e(TAG, "Could not close the client socket after connection error", closeException)
                }
                bluetoothSocket = null // Reset socket
                runOnUiThread {
                    tvStatus.text = "Status: Connection Failed"
                    Toast.makeText(this, "Connection Failed: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
                    updateUIState()
                }
            }
        }.start()
    }

    private fun sendCommand(command: String) {
        if (bluetoothSocket?.isConnected == true && outputStream != null) {
            try {
                outputStream?.write(command.toByteArray())
                outputStream?.flush()
                Log.d(TAG, "Sent command: $command")
            } catch (e: IOException) {
                Log.e(TAG, "Error sending command '$command': ${e.message}", e)
                Toast.makeText(this, "Error sending command", Toast.LENGTH_SHORT).show()
                // Consider attempting to disconnect/reconnect or notify user more explicitly
                disconnect() // Example: disconnect on send error
            }
        } else {
            Toast.makeText(this, "Not connected. Cannot send command.", Toast.LENGTH_SHORT).show()
            Log.w(TAG, "SendCommand called but not connected. Socket: $bluetoothSocket, Output: $outputStream")
            updateUIState() // Ensure UI reflects disconnected state
        }
    }

    private fun disconnect() {
        try {
            outputStream?.close()
            inputStream?.close()
            bluetoothSocket?.close()
        } catch (e: IOException) {
            Log.e(TAG, "Error on explicit disconnect: ${e.message}", e)
        } finally { // Ensure these are nulled out even if close throws an error
            outputStream = null
            inputStream = null
            bluetoothSocket = null
            // esp32Device = null; // Optionally clear the selected device
            // esp32MacAddress = null; // Optionally clear mac address
        }
        tvStatus.text = "Status: Disconnected"
        Log.i(TAG, "Bluetooth disconnected.")
        Toast.makeText(this, "Disconnected", Toast.LENGTH_SHORT).show()
        updateUIState()
    }

    private fun updateUIState() {
        val isConnected = bluetoothSocket?.isConnected == true
        Log.d(TAG, "Updating UI state. Connected: $isConnected")
        btnConnect.isEnabled = !isConnected && (esp32Device != null || !esp32MacAddress.isNullOrEmpty())
        btnDisconnect.isEnabled = isConnected
        btnScan.isEnabled = !isConnected // Or always enabled if you prefer

        btnForward.isEnabled = isConnected
        btnBackward.isEnabled = isConnected
        btnLeft.isEnabled = isConnected
        btnRight.isEnabled = isConnected
        btnStop.isEnabled = isConnected
    }

    private fun hasRequiredBluetoothPermissions(connectPermissionOnly: Boolean = false): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val hasConnect = ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED
            if (connectPermissionOnly) return hasConnect
            val hasScan = ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED
            return hasConnect && hasScan
        } else {
            // For older versions, BLUETOOTH and BLUETOOTH_ADMIN are install-time,
            // but ACCESS_FINE_LOCATION is runtime and often needed for discovery.
            val hasFineLocation = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
            // Assuming BLUETOOTH and BLUETOOTH_ADMIN are granted if we reach here on older APIs
            // or they would have been part of the initial permission request.
            return if (connectPermissionOnly) true else hasFineLocation // For connect, old permissions suffice. For scan, location is key.
        }
    }


    @RequiresPermission(Manifest.permission.BLUETOOTH_SCAN)
    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "onDestroy called. Cleaning up Bluetooth resources.")
        bluetoothAdapter?.cancelDiscovery() // Ensure discovery is cancelled
        disconnect() // Disconnect before unregistering the receiver

        try {
            unregisterReceiver(receiver)
        } catch (e: IllegalArgumentException) { // More idiomatic Kotlin exception type
            Log.w(TAG, "Receiver not registered or already unregistered: ${e.message}")
        }
    }
}