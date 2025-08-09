package com.yvesds.voicetally3.ui.bluetooth

import android.app.Application
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.content.pm.PackageManager
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData


class BluetoothViewModel(application: Application) : AndroidViewModel(application) {

    private val _hidDevices = MutableLiveData<List<BluetoothDeviceWrapper>>()
    val hidDevices: LiveData<List<BluetoothDeviceWrapper>> get() = _hidDevices

    private val bluetoothAdapter: BluetoothAdapter by lazy {
        val bluetoothManager = application.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        bluetoothManager.adapter
    }

    private val scanner by lazy { bluetoothAdapter.bluetoothLeScanner }

    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult?) {
            result?.device?.let { device ->
                val wrapper = BluetoothDeviceWrapper(device, result.rssi, "BLE")
                addDevice(wrapper)
            }
        }

        override fun onBatchScanResults(results: MutableList<ScanResult>?) {
            results?.forEach { onScanResult(ScanSettings.CALLBACK_TYPE_ALL_MATCHES, it) }
        }

        override fun onScanFailed(errorCode: Int) {
            Log.e(TAG, "❌ BLE scan failed: $errorCode")
        }
    }

    fun refreshHidDevices() {
        val context = getApplication<Application>()

        // ✅ Check BLUETOOTH_CONNECT permission
        if (ContextCompat.checkSelfPermission(context, android.Manifest.permission.BLUETOOTH_CONNECT)
            != PackageManager.PERMISSION_GRANTED) {
            Log.w(TAG, "❌ Missing BLUETOOTH_CONNECT permission")
            _hidDevices.postValue(emptyList())
            return
        }

        val bondedDevices = bluetoothAdapter.bondedDevices?.map { device ->
            BluetoothDeviceWrapper(device, null, "Classic")
        } ?: emptyList()

        _hidDevices.postValue(bondedDevices.toMutableList())

        // ✅ Check BLUETOOTH_SCAN permission before starting scan
        if (ContextCompat.checkSelfPermission(context, android.Manifest.permission.BLUETOOTH_SCAN)
            == PackageManager.PERMISSION_GRANTED) {
            startBleScan()
        } else {
            Log.w(TAG, "❌ Missing BLUETOOTH_SCAN permission")
        }
    }

    private fun startBleScan() {
        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .build()

        scanner.startScan(null, settings, scanCallback)
        Log.d(TAG, "🚀 BLE scanning gestart")
    }

    private fun addDevice(wrapper: BluetoothDeviceWrapper) {
        val currentList = _hidDevices.value?.toMutableList() ?: mutableListOf()
        if (!currentList.any { it.device.address == wrapper.device.address }) {
            currentList.add(wrapper)
            _hidDevices.postValue(currentList)
            Log.d(TAG, "🔍 Nieuw device toegevoegd: ${wrapper.device.name ?: wrapper.device.address}")
        }
    }

    override fun onCleared() {
        super.onCleared()
        try {
            scanner.stopScan(scanCallback)
            Log.d(TAG, "🛑 BLE scanning gestopt")
        } catch (e: SecurityException) {
            Log.e(TAG, "❌ Geen permissie om BLE scanning te stoppen", e)
        }
    }

    companion object {
        private const val TAG = "BluetoothViewModel"
    }
}

data class BluetoothDeviceWrapper(
    val device: BluetoothDevice,
    val rssi: Int?,
    val type: String
)
