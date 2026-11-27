@file:Suppress(
    "UNUSED_PARAMETER",
    "UNUSED_VARIABLE",
    "UNNECESSARY_NOT_NULL_ASSERTION",
    "DEPRECATION",
    "NAME_SHADOWING",
    "FunctionName",
    "PrivatePropertyName",
    "SpellCheckingInspection",
    "RedundantUnitReturnType",
    "RemoveRedundantQualifierName",
    "OPT_IN_USAGE",
    "unused",
    "UnusedImport"
)

package com.vincenthzr.locationspoofer.xposed.hooks.network

import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import com.vincenthzr.locationspoofer.xposed.LocationHooker
import com.vincenthzr.locationspoofer.xposed.utils.*
import org.json.JSONObject
import kotlin.random.Random
import io.github.libxposed.api.*

/**
 * 蓝牙 LE 扫描环境伪造模块入口，以及扫描结果的回调派发/定时器。
 */
internal fun LocationHooker.hookBluetoothLE(
    classLoader: ClassLoader,
    isCoreSystemProcess: Boolean = false
) {
    if (isCoreSystemProcess) {
        XposedBridge.log("[LocationSpoofer] Skipping Bluetooth LE hooks in core system process")
        return
    }

    val mainHandler = Handler(Looper.getMainLooper())
    val scanRecordClass = XposedHelpers.findClassIfExists("android.bluetooth.le.ScanRecord", classLoader)


    // 1. Hook BluetoothLeScanner 的所有 startScan / startScanFromSource 重载
    val leScannerClass = XposedHelpers.findClassIfExists("android.bluetooth.le.BluetoothLeScanner", classLoader)
    if (leScannerClass != null) {
        val scanMethodNames = arrayOf("startScan", "startScanFromSource")
        for (scanName in scanMethodNames) {
            try {
                XposedHelpers.hookAllMethods(leScannerClass, scanName) { chain, method ->
                    XposedBridge.log("[LocationSpoofer] 捕获到 BluetoothLeScanner.$scanName: args=${chain.args.map { it?.javaClass?.simpleName }}")
                    val config = readConfig()
                    if (config == null || !config.optBoolean("active", false) || !config.optBoolean("mock_bluetooth", true)) {
                        XposedBridge.log("[LocationSpoofer] BluetoothLeScanner.$scanName 放行原生系统 (active/mock_bt 为 false)")
                        return@hookAllMethods chain.proceed(chain.args.toTypedArray())
                    }
                    val callback = chain.args.firstOrNull { isScanCallback(it) }
                        ?: chain.args.lastOrNull {
                            it != null && it !is List<*> && it !is java.util.Collection<*> &&
                                    !it.javaClass.name.contains("ScanFilter") &&
                                    !it.javaClass.name.contains("ScanSettings") &&
                                    !it.javaClass.name.contains("PendingIntent") &&
                                    !it.javaClass.name.contains("WorkSource")
                        }
                    // Keep the real Bluetooth HAL scan and overlay synthetic results only after success.
                    val result = chain.proceed(chain.args.toTypedArray())
                    if (callback != null && (method !is java.lang.reflect.Method ||
                            method.returnType != Int::class.javaPrimitiveType || result == 0)) {
                        XposedBridge.log("[LocationSpoofer] Native $scanName accepted; overlay synthetic results")
                        startBleTimer(callback, classLoader)
                    } else if (callback == null) {
                        XposedBridge.log("[LocationSpoofer] $scanName callback not identified; native scan left untouched")
                    }
                    return@hookAllMethods result
                }
            } catch (e: Throwable) {
                XposedBridge.log("[LocationSpoofer] Hook BluetoothLeScanner.$scanName 异常: $e")
            }
        }

        // 2. Hook BluetoothLeScanner 的所有 stopScan 重载
        try {
            XposedHelpers.hookAllMethods(leScannerClass, "stopScan") { chain, _ ->
                val callback = chain.args.firstOrNull { isScanCallback(it) }
                    ?: chain.args.lastOrNull {
                        it != null && it !is List<*> && !it.javaClass.name.contains("PendingIntent")
                    }
                if (callback != null) {
                    stopBleTimer(callback)
                }
                return@hookAllMethods chain.proceed(chain.args.toTypedArray())
            }
        } catch (_: Throwable) {}
    }

    // 3. 旧版 startLeScan 分发逻辑

    // 4. Hook BluetoothAdapter 的所有 startLeScan / stopLeScan 重载
    val bluetoothAdapterClass = XposedHelpers.findClassIfExists("android.bluetooth.BluetoothAdapter", classLoader)
    if (bluetoothAdapterClass != null) {
        try {
            XposedHelpers.hookAllMethods(bluetoothAdapterClass, "startLeScan") { chain, _ ->
                XposedBridge.log("[LocationSpoofer] 捕获到 BluetoothAdapter.startLeScan: args=${chain.args.map { it?.javaClass?.simpleName }}")
                val config = readConfig()
                if (config == null || !config.optBoolean("active", false) || !config.optBoolean("mock_bluetooth", true)) {
                    return@hookAllMethods chain.proceed(chain.args.toTypedArray())
                }
                val callback = chain.args.firstOrNull { isLeScanCallback(it) }
                    ?: chain.args.lastOrNull { it != null && it !is Array<*> }
                val result = chain.proceed(chain.args.toTypedArray()) as? Boolean ?: false
                if (result && callback != null) {
                    XposedBridge.log("[LocationSpoofer] Native startLeScan accepted; overlay synthetic results")
                    startOldLeScanTimer(callback, classLoader)
                }
                return@hookAllMethods result
            }
        } catch (_: Throwable) {}

        try {
            XposedHelpers.hookAllMethods(bluetoothAdapterClass, "stopLeScan") { chain, _ ->
                val callback = chain.args.firstOrNull { isLeScanCallback(it) }
                    ?: chain.args.lastOrNull { it != null && it !is Array<*> }
                if (callback != null) {
                    stopBleTimer(callback)
                }
                return@hookAllMethods chain.proceed(chain.args.toTypedArray())
            }
        } catch (_: Throwable) {}

        try {
            XposedHelpers.hookAllMethods(bluetoothAdapterClass, "getRemoteDevice") { chain, _ ->
                val result = chain.proceed(chain.args.toTypedArray())
                if (result != null) return@hookAllMethods result
                val config = readConfig()
                if (config != null && config.optBoolean("active", false) && config.optBoolean("mock_bluetooth", true)) {
                    val mac = chain.args.firstOrNull { it is String } as? String
                    if (mac != null) {
                        val dev = createBluetoothDevice(classLoader, mac)
                        if (dev != null) return@hookAllMethods dev
                    }
                }
                return@hookAllMethods null
            }
        } catch (_: Throwable) {}

        // Do not fabricate adapter power/state or chipset capabilities; retain native hardware state.

        // 6. Hook BluetoothAdapter.getBluetoothLeScanner
        try {
            XposedHelpers.hookAllMethods(bluetoothAdapterClass, "getBluetoothLeScanner") { chain, _ ->
                val result = chain.proceed(chain.args.toTypedArray())
                val config = readConfig()
                if (result == null && config != null && config.optBoolean("active", false) && config.optBoolean("mock_bluetooth", true)) {
                    val adapter = chain.thisObject
                    try {
                        var scanner = XposedHelpers.getObjectField(adapter, "mBluetoothLeScanner")
                        if (scanner == null && leScannerClass != null) {
                            for (c in leScannerClass.declaredConstructors) {
                                try {
                                    c.isAccessible = true
                                    val dummyArgs = arrayOfNulls<Any>(c.parameterCount)
                                    scanner = c.newInstance(*dummyArgs)
                                    if (scanner != null) break
                                } catch (_: Throwable) {}
                            }
                            if (scanner != null) {
                                try {
                                    XposedHelpers.setObjectField(adapter, "mBluetoothLeScanner", scanner)
                                } catch (_: Throwable) {}
                            }
                        }
                        if (scanner != null) return@hookAllMethods scanner
                    } catch (_: Throwable) {}
                }
                return@hookAllMethods result
            }
        } catch (_: Throwable) {}

        // 7. Hook BluetoothAdapter.getBondedDevices
        try {
            XposedHelpers.hookAllMethods(bluetoothAdapterClass, "getBondedDevices") { chain, _ ->
                val config = readConfig() ?: return@hookAllMethods chain.proceed(chain.args.toTypedArray())
                if (!config.optBoolean("active", false) || !config.optBoolean("mock_bluetooth", true)) {
                    return@hookAllMethods chain.proceed(chain.args.toTypedArray())
                }
                val nativeBonded = chain.proceed(chain.args.toTypedArray()) as? Set<*>
                val bondedSet = java.util.HashSet<Any>()
                if (nativeBonded != null) bondedSet.addAll(nativeBonded.filterNotNull())
                try {
                    val bluetoothArray = config.optJSONArray("bluetooth_json")
                    if (bluetoothArray != null && bluetoothArray.length() > 0) {
                        for (i in 0 until bluetoothArray.length()) {
                            val obj = bluetoothArray.getJSONObject(i)
                            if (obj.optBoolean("isConnected", false)) {
                                val address = obj.optString("address", "00:00:00:00:00:00")
                                val dev = createBluetoothDevice(classLoader, address)
                                if (dev != null) bondedSet.add(dev)
                            }
                        }
                    }
                } catch (_: Throwable) {}
                return@hookAllMethods bondedSet
            }
        } catch (_: Throwable) {}

        // 8. Hook BluetoothAdapter.startDiscovery 模拟经典蓝牙扫描并分发 ACTION_FOUND 广播
        try {
            XposedHelpers.hookAllMethods(bluetoothAdapterClass, "startDiscovery") { chain, _ ->
                val config = readConfig() ?: return@hookAllMethods chain.proceed(chain.args.toTypedArray())
                if (!config.optBoolean("active", false) || !config.optBoolean("mock_bluetooth", true)) {
                    return@hookAllMethods chain.proceed(chain.args.toTypedArray())
                }
                val started = chain.proceed(chain.args.toTypedArray()) as? Boolean ?: false
                if (!started) return@hookAllMethods started
                val bluetoothArray = config.optJSONArray("bluetooth_json")
                if (bluetoothArray != null && bluetoothArray.length() > 0) {
                    try {
                        val activityThreadClass = XposedHelpers.findClassIfExists("android.app.ActivityThread", classLoader)
                        val app = if (activityThreadClass != null) {
                            XposedHelpers.callStaticMethod(activityThreadClass, "currentApplication") as? android.content.Context
                        } else null

                        if (app != null) {
                            mainHandler.postDelayed({
                                try {
                                    app.sendBroadcast(android.content.Intent("android.bluetooth.adapter.action.DISCOVERY_STARTED"))
                                } catch (_: Throwable) {}
                            }, 50)
                            for (i in 0 until bluetoothArray.length()) {
                                val obj = bluetoothArray.getJSONObject(i)
                                val address = obj.optString("address", "00:11:22:33:44:55")
                                val name = obj.optString("name", "")
                                val rssi = obj.optInt("rssi", -60)
                                val dev = createBluetoothDevice(classLoader, address) ?: continue
                                mainHandler.postDelayed({
                                    try {
                                        val foundIntent = android.content.Intent("android.bluetooth.device.action.FOUND").apply {
                                            putExtra("android.bluetooth.device.extra.DEVICE", dev as android.os.Parcelable)
                                            putExtra("android.bluetooth.device.extra.NAME", name)
                                            putExtra("android.bluetooth.device.extra.RSSI", rssi.toShort())
                                        }
                                        app.sendBroadcast(foundIntent)
                                    } catch (_: Throwable) {}
                                }, (100 + i * 150).toLong())
                            }
                        }
                    } catch (_: Throwable) {}
                }
                return@hookAllMethods started
            }
        } catch (_: Throwable) {}
    }

    // 9. Hook BluetoothDevice.getName / getAlias / getType / getBondState / getUuids / getAddress
    val bluetoothDeviceClass = XposedHelpers.findClassIfExists("android.bluetooth.BluetoothDevice", classLoader)
    if (bluetoothDeviceClass != null) {
        try {
            XposedHelpers.hookAllMethods(bluetoothDeviceClass, "getAddress") { chain, _ ->
                val result = chain.proceed(chain.args.toTypedArray()) as? String
                if (!result.isNullOrEmpty() && result != "00:00:00:00:00:00") return@hookAllMethods result
                val config = readConfig()
                if (config != null && config.optBoolean("active", false) && config.optBoolean("mock_bluetooth", true)) {
                    val bluetoothArray = config.optJSONArray("bluetooth_json")
                    if (bluetoothArray != null && bluetoothArray.length() > 0) {
                        val addr = bluetoothArray.getJSONObject(0).optString("address", "")
                        if (addr.isNotEmpty()) return@hookAllMethods normalizeMacAddress(addr)
                    }
                }
                return@hookAllMethods result
            }
        } catch (_: Throwable) {}

        try {
            XposedHelpers.hookAllMethods(bluetoothDeviceClass, "getName") { chain, _ ->
                val config = readConfig()
                if (config != null && config.optBoolean("active", false) && config.optBoolean("mock_bluetooth", true)) {
                    val thisDevice = chain.thisObject
                    val address = (XposedHelpers.callMethod(thisDevice, "getAddress") as? String)?.uppercase()
                    val bluetoothArray = config.optJSONArray("bluetooth_json")
                    if (address != null && bluetoothArray != null) {
                        for (i in 0 until bluetoothArray.length()) {
                            val obj = bluetoothArray.getJSONObject(i)
                            if (obj.optString("address").uppercase() == address) {
                                val mockName = obj.optString("name")
                                if (mockName.isNotEmpty()) return@hookAllMethods mockName
                            }
                        }
                    }
                }
                return@hookAllMethods chain.proceed(chain.args.toTypedArray())
            }
        } catch (_: Throwable) {}

        try {
            XposedHelpers.hookAllMethods(bluetoothDeviceClass, "getAlias") { chain, _ ->
                val config = readConfig()
                if (config != null && config.optBoolean("active", false) && config.optBoolean("mock_bluetooth", true)) {
                    val thisDevice = chain.thisObject
                    val address = (XposedHelpers.callMethod(thisDevice, "getAddress") as? String)?.uppercase()
                    val bluetoothArray = config.optJSONArray("bluetooth_json")
                    if (address != null && bluetoothArray != null) {
                        for (i in 0 until bluetoothArray.length()) {
                            val obj = bluetoothArray.getJSONObject(i)
                            if (obj.optString("address").uppercase() == address) {
                                val mockName = obj.optString("name")
                                if (mockName.isNotEmpty()) return@hookAllMethods mockName
                            }
                        }
                    }
                }
                return@hookAllMethods chain.proceed(chain.args.toTypedArray())
            }
        } catch (_: Throwable) {}

        try {
            XposedHelpers.hookAllMethods(bluetoothDeviceClass, "getType") { chain, _ ->
                val config = readConfig()
                if (config != null && config.optBoolean("active", false) && config.optBoolean("mock_bluetooth", true)) {
                    return@hookAllMethods 2 // DEVICE_TYPE_LE
                }
                return@hookAllMethods chain.proceed(chain.args.toTypedArray())
            }
        } catch (_: Throwable) {}

        try {
            XposedHelpers.hookAllMethods(bluetoothDeviceClass, "getBondState") { chain, _ ->
                val config = readConfig()
                if (config != null && config.optBoolean("active", false) && config.optBoolean("mock_bluetooth", true)) {
                    val thisDevice = chain.thisObject
                    val address = (XposedHelpers.callMethod(thisDevice, "getAddress") as? String)?.uppercase()
                    val bluetoothArray = config.optJSONArray("bluetooth_json")
                    if (address != null && bluetoothArray != null) {
                        for (i in 0 until bluetoothArray.length()) {
                            val obj = bluetoothArray.getJSONObject(i)
                            if (obj.optString("address").uppercase() == address) {
                                if (obj.optBoolean("isConnected", false)) {
                                    return@hookAllMethods 12 // BOND_BONDED
                                }
                            }
                        }
                    }
                    return@hookAllMethods 10 // BOND_NONE
                }
                return@hookAllMethods chain.proceed(chain.args.toTypedArray())
            }
        } catch (_: Throwable) {}

        try {
            XposedHelpers.hookAllMethods(bluetoothDeviceClass, "getUuids") { chain, _ ->
                val config = readConfig()
                if (config != null && config.optBoolean("active", false) && config.optBoolean("mock_bluetooth", true)) {
                    val parcelUuidClass = XposedHelpers.findClassIfExists("android.os.ParcelUuid", classLoader)
                    if (parcelUuidClass != null) {
                        try {
                            val uuidObj = XposedHelpers.callStaticMethod(
                                parcelUuidClass,
                                "fromString",
                                "0000fe3c-0000-1000-8000-00805f9b34fb"
                            )
                            if (uuidObj != null) {
                                val array = java.lang.reflect.Array.newInstance(parcelUuidClass, 1)
                                java.lang.reflect.Array.set(array, 0, uuidObj)
                                return@hookAllMethods array
                            }
                        } catch (_: Throwable) {}
                    }
                }
                return@hookAllMethods chain.proceed(chain.args.toTypedArray())
            }
        } catch (_: Throwable) {}

        try {
            XposedHelpers.hookAllMethods(bluetoothDeviceClass, "connectGatt") { chain, _ ->
                val config = readConfig()
                if (config != null && config.optBoolean("active", false) && config.optBoolean("mock_bluetooth", true)) {
                    val gattCallback = chain.args.firstOrNull { it != null && it.javaClass.name.contains("GattCallback") }
                    val result = chain.proceed(chain.args.toTypedArray())
                    if (gattCallback != null && result != null) {
                        mainHandler.postDelayed({
                            try {
                                XposedHelpers.callMethod(gattCallback, "onConnectionStateChange", result, 0, 2 /* STATE_CONNECTED */)
                            } catch (_: Throwable) {}
                        }, 200)
                    }
                    return@hookAllMethods result
                }
                return@hookAllMethods chain.proceed(chain.args.toTypedArray())
            }
        } catch (_: Throwable) {}
    }

    // 10. Hook ScanRecord (getDeviceName, getServiceUuids, getManufacturerSpecificData)
    if (scanRecordClass != null) {
        try {
            XposedHelpers.hookAllMethods(scanRecordClass, "getDeviceName") { chain, _ ->
                val result = chain.proceed(chain.args.toTypedArray())
                if (result != null && (result as? String)?.isNotEmpty() == true) {
                    return@hookAllMethods result
                }
                val config = readConfig()
                if (config != null && config.optBoolean("active", false) && config.optBoolean("mock_bluetooth", true)) {
                    val bluetoothArray = config.optJSONArray("bluetooth_json")
                    if (bluetoothArray != null && bluetoothArray.length() > 0) {
                        val name = bluetoothArray.getJSONObject(0).optString("name", "")
                        if (name.isNotEmpty()) return@hookAllMethods name
                    }
                }
                return@hookAllMethods result
            }
        } catch (_: Throwable) {}

        try {
            XposedHelpers.hookAllMethods(scanRecordClass, "getServiceUuids") { chain, _ ->
                val result = chain.proceed(chain.args.toTypedArray()) as? List<*>
                if (!result.isNullOrEmpty()) return@hookAllMethods result
                val config = readConfig()
                if (config != null && config.optBoolean("active", false) && config.optBoolean("mock_bluetooth", true)) {
                    val parcelUuidClass = XposedHelpers.findClassIfExists("android.os.ParcelUuid", classLoader)
                    if (parcelUuidClass != null) {
                        try {
                            val uuidObj = XposedHelpers.callStaticMethod(parcelUuidClass, "fromString", "0000fe3c-0000-1000-8000-00805f9b34fb")
                            if (uuidObj != null) return@hookAllMethods listOf(uuidObj)
                        } catch (_: Throwable) {}
                    }
                }
                return@hookAllMethods result
            }
        } catch (_: Throwable) {}

        try {
            XposedHelpers.hookAllMethods(scanRecordClass, "getManufacturerSpecificData") { chain, _ ->
                val config = readConfig()
                if (config != null && config.optBoolean("active", false) && config.optBoolean("mock_bluetooth", true)) {
                    val bluetoothArray = config.optJSONArray("bluetooth_json")
                    if (bluetoothArray != null && bluetoothArray.length() > 0) {
                        val hex = bluetoothArray.getJSONObject(0).optString("scanRecordHex", bluetoothArray.getJSONObject(0).optString("rawBytes", ""))
                        val rawBytes = if (hex.isNotEmpty()) hexStringToByteArray(hex) else ByteArray(0)
                        if (rawBytes.isNotEmpty()) {
                            if (chain.args.isEmpty()) {
                                val sparseArray = android.util.SparseArray<ByteArray>()
                                sparseArray.put(0x0100, rawBytes)
                                sparseArray.put(0x0001, rawBytes)
                                sparseArray.put(256, rawBytes)
                                sparseArray.put(1, rawBytes)
                                sparseArray.put(0, rawBytes)
                                return@hookAllMethods sparseArray
                            } else {
                                return@hookAllMethods rawBytes
                            }
                        }
                    }
                }
                return@hookAllMethods chain.proceed(chain.args.toTypedArray())
            }
        } catch (_: Throwable) {}

        try {
            XposedHelpers.hookAllMethods(scanRecordClass, "getBytes") { chain, _ ->
                val config = readConfig()
                if (config != null && config.optBoolean("active", false) && config.optBoolean("mock_bluetooth", true)) {
                    val bluetoothArray = config.optJSONArray("bluetooth_json")
                    if (bluetoothArray != null && bluetoothArray.length() > 0) {
                        val obj = bluetoothArray.getJSONObject(0)
                        val hex = obj.optString("scanRecordHex", obj.optString("rawBytes", ""))
                        val name = obj.optString("name", "")
                        val address = obj.optString("address", "")
                        val rawBytes = buildScanRecordBytes(name, hex, address)
                        if (rawBytes.isNotEmpty()) {
                            return@hookAllMethods rawBytes
                        }
                    }
                }
                return@hookAllMethods chain.proceed(chain.args.toTypedArray())
            }
        } catch (_: Throwable) {}
    }

    // Do not override ScanFilter.matches: real scan results must retain the app's native filters.
}
