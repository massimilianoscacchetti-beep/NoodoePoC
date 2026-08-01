package com.noodoe.poc

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.Observer
import com.noodoe.poc.ble.BleManager
import com.noodoe.poc.utils.NoodoePacketHelper

/**
 * MainActivity: UI minimalista per PoC Noodoe.
 * 
 * Layout:
 * 1. Sezione SCAN: button per avviare scan, lista device trovati
 * 2. Sezione CONNESSIONE: mostra stato connessione
 * 3. Sezione COMANDI: input per inviare pacchetti test
 * 4. Sezione OUTPUT: log delle operazioni
 */
class MainActivity : AppCompatActivity() {
    
    private companion object {
        private const val TAG = "MainActivity"
        private const val REQUEST_CODE_PERMISSIONS = 1001
    }
    
    private lateinit var bleManager: BleManager
    private lateinit var logOutput: TextView
    private lateinit var statusTextView: TextView
    private lateinit var deviceListView: ListView
    private lateinit var commandInput: EditText
    private val deviceAddresses = mutableListOf<String>()
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        
        Log.i(TAG, "MainActivity creata")
        
        // Inizializza BleManager
        bleManager = BleManager(this)
        
        // Bind UI components
        setupUi()
        
        // Richiedi permessi (Android 12+)
        checkAndRequestPermissions()
        
        // Osserva LiveData da BleManager
        observeBleState()
    }
    
    /**
     * Setup dei componenti UI.
     */
    private fun setupUi() {
        // TextViews
        statusTextView = findViewById(R.id.statusTextView)
        logOutput = findViewById(R.id.logOutput)
        commandInput = findViewById(R.id.commandInput)
        deviceListView = findViewById(R.id.deviceListView)
        
        // Button SCAN
        findViewById<Button>(R.id.scanButton).setOnClickListener {
            logToUI(">>> Avvio scan BLE...")
            bleManager.startScan()
        }
        
        // Button STOP SCAN
        findViewById<Button>(R.id.stopScanButton).setOnClickListener {
            logToUI(">>> Arresto scan BLE...")
            bleManager.stopScan()
        }
        
        // Button INVIA COMANDO (su COMMAND_WRITE 0000155F)
        findViewById<Button>(R.id.sendCommandButton).setOnClickListener {
            val payload = commandInput.text.toString().trim()
            if (payload.isEmpty()) {
                logToUI("✗ Errore: inserisci un payload")
                return@setOnClickListener
            }
            
            logToUI(">>> Invio comando: '$payload'")
            val packet = NoodoePacketHelper.formatPacket(
                messageType = NoodoePacketHelper.MessageType.COMMAND,
                payload = payload
            )
            NoodoePacketHelper.logPacket(packet, "Test Command Packet")
            bleManager.sendCommand(packet)
        }
        
        // Button ASSET COMMIT (simula comando di commit)
        findViewById<Button>(R.id.assetCommitButton).setOnClickListener {
            logToUI(">>> Invio Asset Commit Command...")
            val packet = NoodoePacketHelper.formatAssetCommitCommand(slotId = 0x01)
            NoodoePacketHelper.logPacket(packet, "Asset Commit Packet")
            bleManager.sendCommand(packet)
        }
        
        // Button LEGGI AUTH CHALLENGE
        findViewById<Button>(R.id.readChallengeButton).setOnClickListener {
            logToUI(">>> Lettura Auth Challenge...")
            bleManager.readAuthChallenge()
        }
        
        // ListView per device selezionabili
        deviceListView.setOnItemClickListener { _, _, position, _ ->
            if (position < deviceAddresses.size) {
                val selectedAddress = deviceAddresses[position]
                logToUI(">>> Connessione a $selectedAddress...")
                bleManager.connectToDevice(selectedAddress)
            }
        }
    }
    
    /**
     * Osserva i LiveData da BleManager e aggiorna la UI.
     */
    private fun observeBleState() {
        // Stato scan
        bleManager.scanState.observe(this, Observer { state ->
            val stateStr = when (state) {
                BleManager.ScanState.IDLE -> "Idle"
                BleManager.ScanState.SCANNING -> "⏳ Scanning..."
                BleManager.ScanState.STOPPED -> "Scan Stopped"
            }
            statusTextView.text = stateStr
        })
        
        // Device trovati
        bleManager.discoveredDevices.observe(this, Observer { devices ->
            deviceAddresses.clear()
            val deviceNames = mutableListOf<String>()
            
            for (device in devices) {
                val displayName = "${device.name ?: "(Unknown)"} [${device.address}] RSSI: ${device.rssi}"
                deviceNames.add(displayName)
                deviceAddresses.add(device.address)
                logToUI("  Found: $displayName")
            }
            
            val adapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, deviceNames)
            deviceListView.adapter = adapter
        })
        
        // Stato connessione
        bleManager.connectionState.observe(this, Observer { state ->
            val stateStr = when (state) {
                BleManager.ConnectionState.DISCONNECTED -> "❌ Disconnected"
                BleManager.ConnectionState.CONNECTING -> "⏳ Connecting..."
                BleManager.ConnectionState.CONNECTED -> "✓ Connected"
                BleManager.ConnectionState.DISCONNECTING -> "🔌 Disconnecting..."
            }
            statusTextView.text = stateStr
        })
        
        // Servizi scoperti
        bleManager.servicesDiscovered.observe(this, Observer { services ->
            if (services.isNotEmpty()) {
                logToUI("\n=== Discovered Services (${services.size}) ===")
                for (service in services) {
                    logToUI("Service: ${service.uuid}")
                    for (char in service.characteristics) {
                        logToUI("  └─ ${char.uuid}")
                    }
                }
            }
        })
        
        // Risultati comandi
        bleManager.commandResult.observe(this, Observer { result ->
            logToUI(">>> $result")
        })
    }
    
    /**
     * Richiedi permessi Bluetooth (Android 12+) e location (Android 6+).
     */
    private fun checkAndRequestPermissions() {
        val permissionsToRequest = mutableListOf<String>()
        
        // Android 12+ richiede BLUETOOTH_SCAN e BLUETOOTH_CONNECT
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_SCAN)
                != PackageManager.PERMISSION_GRANTED
            ) {
                permissionsToRequest.add(Manifest.permission.BLUETOOTH_SCAN)
            }
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT)
                != PackageManager.PERMISSION_GRANTED
            ) {
                permissionsToRequest.add(Manifest.permission.BLUETOOTH_CONNECT)
            }
        }
        
        // Location richiesto per BLE scan (anche pre-Android 12)
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
            != PackageManager.PERMISSION_GRANTED
        ) {
            permissionsToRequest.add(Manifest.permission.ACCESS_FINE_LOCATION)
        }
        
        if (permissionsToRequest.isNotEmpty()) {
            Log.i(TAG, "Richiesta permessi: ${permissionsToRequest.joinToString(", ")}")
            ActivityCompat.requestPermissions(
                this,
                permissionsToRequest.toTypedArray(),
                REQUEST_CODE_PERMISSIONS
            )
        } else {
            Log.i(TAG, "Tutti i permessi già concessi")
        }
    }
    
    /**
     * Callback per runtime permissions (Android 6+).
     */
    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        
        if (requestCode == REQUEST_CODE_PERMISSIONS) {
            val allGranted = grantResults.all { it == PackageManager.PERMISSION_GRANTED }
            
            if (allGranted) {
                Log.i(TAG, "✓ Tutti i permessi concessi")
                logToUI("✓ Permessi BLE concessi. Pronto per scan.")
            } else {
                Log.w(TAG, "✗ Alcuni permessi negati")
                logToUI("✗ Permessi BLE negati. L'app non potrà eseguire scan.")
            }
        }
    }
    
    /**
     * Utility: aggiunge una linea al log UI.
     */
    private fun logToUI(message: String) {
        Log.d(TAG, message)
        runOnUiThread {
            val current = logOutput.text.toString()
            logOutput.text = if (current.isEmpty()) {
                message
            } else {
                "$current\n$message"
            }
            
            // Auto-scroll in fondo
            val scrollView = findViewById<ScrollView>(R.id.scrollView)
            scrollView.post {
                scrollView.fullScroll(ScrollView.FOCUS_DOWN)
            }
        }
    }
    
    override fun onDestroy() {
        super.onDestroy()
        bleManager.disconnect()
    }
}
