package cz.talich.arp

import com.android.ddmlib.AndroidDebugBridge
import com.android.ddmlib.CollectingOutputReceiver
import com.android.ddmlib.IDevice
import com.android.sdklib.devices.DeviceManager
import com.android.sdklib.internal.avd.AvdManager
import com.intellij.openapi.project.Project
import org.jetbrains.android.sdk.StudioAndroidSdkData
import java.util.concurrent.TimeUnit

class AdbController(private val project: Project?) {

    data class DeviceInfo(val serial: String, val displayName: String)

    private val avdManager: AvdManager? by lazy { resolveAvdManager() }

    private fun resolveAvdManager(): AvdManager? {
        return try {
            val sdkHandler = StudioAndroidSdkData.getSdkData(project ?: return null)?.sdkHandler ?: return null
            val logger = object : com.android.utils.ILogger {
                override fun error(t: Throwable?, msgFormat: String?, vararg args: Any?) {}
                override fun warning(msgFormat: String, vararg args: Any?) {}
                override fun info(msgFormat: String, vararg args: Any?) {}
                override fun verbose(msgFormat: String, vararg args: Any?) {}
            }
            val deviceManager = DeviceManager.createInstance(sdkHandler, logger)
            val avdFolder = java.nio.file.Paths.get(System.getProperty("user.home"), ".android", "avd")
            AvdManager.createInstance(sdkHandler, avdFolder, deviceManager, logger)
        } catch (_: Exception) { null }
    }

    private fun resolveDisplayName(avdName: String?): String? {
        if (avdName.isNullOrBlank()) return null
        return try {
            avdManager?.getAvd(avdName, false)?.displayName?.takeIf { it.isNotBlank() } ?: avdName.replace('_', ' ')
        } catch (_: Exception) { avdName.replace('_', ' ') }
    }

    fun getConnectedDevices(): List<DeviceInfo> {
        val bridge = AndroidDebugBridge.getBridge()
        if (bridge != null && bridge.isConnected && bridge.hasInitialDeviceList()) {
            return bridge.devices
                .filter { it.isOnline }
                .map { device ->
                    val avdName = try {
                        device.getAvdData().get(2, TimeUnit.SECONDS)?.name?.takeIf { it.isNotBlank() }
                    } catch (_: Exception) { null }
                    val name = resolveDisplayName(avdName) ?: device.serialNumber
                    DeviceInfo(device.serialNumber, name)
                }
        }
        return emptyList()
    }

    private fun findDevice(serial: String?): IDevice? {
        val bridge = AndroidDebugBridge.getBridge() ?: return null
        if (!bridge.isConnected || !bridge.hasInitialDeviceList()) return null
        return if (serial != null) {
            bridge.devices.firstOrNull { it.serialNumber == serial && it.isOnline }
        } else {
            bridge.devices.firstOrNull { it.isOnline }
        }
    }

    fun dumpUiAutomator(serial: String? = null): String? {
        val device = findDevice(serial) ?: return null
        return try {
            val dumpReceiver = CollectingOutputReceiver()
            device.executeShellCommand("uiautomator dump /sdcard/view.xml", dumpReceiver, 10, TimeUnit.SECONDS)
            val dumpOutput = dumpReceiver.output
            if (!dumpOutput.contains("UI hierarchy dumped to") && !dumpOutput.contains("UI hierchary dumped to")) {
                if (dumpOutput.contains("ERROR")) return null
            }

            val catReceiver = CollectingOutputReceiver()
            device.executeShellCommand("cat /sdcard/view.xml", catReceiver, 10, TimeUnit.SECONDS)
            val content = catReceiver.output

            device.executeShellCommand("rm /sdcard/view.xml", CollectingOutputReceiver(), 5, TimeUnit.SECONDS)

            content.takeIf { it.isNotBlank() }
        } catch (_: Exception) { null }
    }

    fun takeScreenshot(serial: String? = null): ByteArray? {
        val device = findDevice(serial) ?: return null
        return try {
            val remotePath = "/sdcard/screenshot_arp.png"
            val localFile = java.io.File.createTempFile("arp_screenshot", ".png")
            try {
                device.executeShellCommand("screencap -p $remotePath", CollectingOutputReceiver(), 10, TimeUnit.SECONDS)
                device.pullFile(remotePath, localFile.absolutePath)
                device.executeShellCommand("rm $remotePath", CollectingOutputReceiver(), 5, TimeUnit.SECONDS)
                localFile.readBytes().takeIf { it.isNotEmpty() }
            } finally {
                localFile.delete()
            }
        } catch (_: Exception) { null }
    }
}
