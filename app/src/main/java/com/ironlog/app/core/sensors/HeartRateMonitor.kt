package com.ironlog.app.core.sensors

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.ParcelUuid
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * A Bluetooth heart-rate strap, read over the standard Heart Rate Profile.
 *
 * Any strap or watch that advertises the Heart Rate Service (0x180D) works:
 * the app subscribes to the Heart Rate Measurement characteristic (0x2A37)
 * and parses the beats per minute out of each notification. No vendor SDK,
 * no account, no cloud -- the sensor talks to the phone and nothing else.
 *
 * Runtime permissions differ by OS version: Android 12 and later ask for
 * BLUETOOTH_SCAN and BLUETOOTH_CONNECT; earlier versions need fine location
 * to scan, which the cardio screen already holds. The screen asks for them
 * with a rationale first, like every other permission in the app.
 */
@Singleton
class HeartRateMonitor @Inject constructor(
    @ApplicationContext private val context: Context
) {
    data class Device(val name: String, val address: String)

    sealed interface State {
        data object Off : State
        data object Scanning : State
        data class Connecting(val device: Device) : State
        data class Connected(val device: Device) : State
        data class Failed(val message: String) : State
    }

    private val _state = MutableStateFlow<State>(State.Off)
    val state: StateFlow<State> = _state.asStateFlow()

    private val _found = MutableStateFlow<List<Device>>(emptyList())
    val found: StateFlow<List<Device>> = _found.asStateFlow()

    /** Latest beats per minute, or null when nothing is connected. */
    private val _bpm = MutableStateFlow<Int?>(null)
    val bpm: StateFlow<Int?> = _bpm.asStateFlow()

    private val manager get() = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
    private val adapter: BluetoothAdapter? get() = manager?.adapter
    private var gatt: BluetoothGatt? = null
    private var scanning: ScanCallback? = null

    fun isAvailable(): Boolean =
        context.packageManager.hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE) && adapter != null

    fun isEnabled(): Boolean = adapter?.isEnabled == true

    /** Scans for straps advertising the heart-rate service. Permission is the caller's job. */
    @SuppressLint("MissingPermission")
    fun startScan() {
        val scanner = adapter?.bluetoothLeScanner ?: run {
            _state.value = State.Failed("Bluetooth is off")
            return
        }
        stopScan()
        _found.value = emptyList()
        _state.value = State.Scanning
        val callback = object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult) {
                val device = result.device ?: return
                val name = result.scanRecord?.deviceName ?: device.name ?: "Heart-rate sensor"
                _found.update { list ->
                    if (list.any { it.address == device.address }) list else list + Device(name, device.address)
                }
            }

            override fun onScanFailed(errorCode: Int) {
                _state.value = State.Failed("Scan failed ($errorCode)")
            }
        }
        scanning = callback
        runCatching {
            scanner.startScan(
                listOf(ScanFilter.Builder().setServiceUuid(ParcelUuid(HEART_RATE_SERVICE)).build()),
                ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).build(),
                callback
            )
        }.onFailure { _state.value = State.Failed("Could not start scanning") }
    }

    @SuppressLint("MissingPermission")
    fun stopScan() {
        val callback = scanning ?: return
        runCatching { adapter?.bluetoothLeScanner?.stopScan(callback) }
        scanning = null
        if (_state.value is State.Scanning) _state.value = State.Off
    }

    @SuppressLint("MissingPermission")
    fun connect(device: Device) {
        stopScan()
        disconnect()
        val remote: BluetoothDevice = adapter?.getRemoteDevice(device.address) ?: run {
            _state.value = State.Failed("Bluetooth is off")
            return
        }
        _state.value = State.Connecting(device)
        gatt = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            remote.connectGatt(context, false, callback(device), BluetoothDevice.TRANSPORT_LE)
        } else {
            remote.connectGatt(context, false, callback(device))
        }
    }

    @SuppressLint("MissingPermission")
    fun disconnect() {
        gatt?.let { runCatching { it.disconnect(); it.close() } }
        gatt = null
        _bpm.value = null
        if (_state.value !is State.Scanning) _state.value = State.Off
    }

    private fun callback(device: Device) = object : BluetoothGattCallback() {
        @SuppressLint("MissingPermission")
        override fun onConnectionStateChange(g: BluetoothGatt, status: Int, newState: Int) {
            when (newState) {
                BluetoothProfile.STATE_CONNECTED -> g.discoverServices()
                BluetoothProfile.STATE_DISCONNECTED -> {
                    _bpm.value = null
                    _state.value = if (status == BluetoothGatt.GATT_SUCCESS) State.Off else State.Failed("${device.name} disconnected")
                }
            }
        }

        @SuppressLint("MissingPermission")
        override fun onServicesDiscovered(g: BluetoothGatt, status: Int) {
            val characteristic = g.getService(HEART_RATE_SERVICE)?.getCharacteristic(HEART_RATE_MEASUREMENT)
            if (characteristic == null) {
                _state.value = State.Failed("${device.name} has no heart-rate service")
                return
            }
            g.setCharacteristicNotification(characteristic, true)
            val descriptor = characteristic.getDescriptor(CLIENT_CONFIG) ?: return
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                g.writeDescriptor(descriptor, BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE)
            } else {
                @Suppress("DEPRECATION")
                descriptor.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                @Suppress("DEPRECATION")
                g.writeDescriptor(descriptor)
            }
            _state.value = State.Connected(device)
        }

        override fun onCharacteristicChanged(g: BluetoothGatt, characteristic: BluetoothGattCharacteristic, value: ByteArray) {
            if (characteristic.uuid == HEART_RATE_MEASUREMENT) parse(value)?.let { _bpm.value = it }
        }

        @Deprecated("Pre-33 callback")
        override fun onCharacteristicChanged(g: BluetoothGatt, characteristic: BluetoothGattCharacteristic) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU && characteristic.uuid == HEART_RATE_MEASUREMENT) {
                @Suppress("DEPRECATION")
                characteristic.value?.let { bytes -> parse(bytes)?.let { _bpm.value = it } }
            }
        }
    }

    companion object {
        val HEART_RATE_SERVICE: UUID = UUID.fromString("0000180d-0000-1000-8000-00805f9b34fb")
        val HEART_RATE_MEASUREMENT: UUID = UUID.fromString("00002a37-0000-1000-8000-00805f9b34fb")
        val CLIENT_CONFIG: UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")

        /**
         * Beats per minute from a Heart Rate Measurement value: bit 0 of the
         * flags says whether the rate is one byte or two (little-endian).
         * Pure, so the framing can be tested without a strap.
         */
        fun parse(value: ByteArray): Int? {
            if (value.isEmpty()) return null
            val flags = value[0].toInt() and 0xFF
            return if (flags and 0x01 == 0) {
                value.getOrNull(1)?.let { it.toInt() and 0xFF }
            } else {
                if (value.size < 3) null else (value[1].toInt() and 0xFF) or ((value[2].toInt() and 0xFF) shl 8)
            }?.takeIf { it in 20..250 }
        }

        /** The runtime permissions scanning and connecting need on this OS version. */
        fun permissions(): List<String> = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            listOf(android.Manifest.permission.BLUETOOTH_SCAN, android.Manifest.permission.BLUETOOTH_CONNECT)
        } else {
            listOf(android.Manifest.permission.ACCESS_FINE_LOCATION)
        }
    }
}
