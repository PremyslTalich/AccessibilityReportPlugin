package cz.talich.arp

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import java.io.File
import java.util.Properties
import java.util.concurrent.TimeUnit

class DumpUiAction : AnAction() {
    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val dump = dumpUiAutomator()
        if (dump != null) {
            Messages.showInfoMessage(project, dump, "UI Automator Dump")
        } else {
            Messages.showErrorDialog(project, "Failed to get UI Automator dump.", "Error")
        }
    }
}

fun getAdbPath(): String? {
    val localPropertiesFile = File("local.properties")
    if (!localPropertiesFile.exists()) return "adb"

    val props = Properties()
    localPropertiesFile.inputStream().use { props.load(it) }
    val sdkDir = props.getProperty("sdk.dir") ?: return "adb"

    val adbFile = File(sdkDir, "platform-tools/adb.exe")
    return if (adbFile.exists()) adbFile.absolutePath else "adb"
}

fun runCommand(vararg command: String): String? {
    return try {
        val process = ProcessBuilder(*command)
            .redirectErrorStream(true)
            .start()

        val output = process.inputStream.bufferedReader().readText()
        process.waitFor(10, TimeUnit.SECONDS)

        if (process.exitValue() == 0) output else null
    } catch (e: Exception) {
        null
    }
}

fun getConnectedDevices(): List<String> {
    val adb = getAdbPath() ?: return emptyList()
    val output = runCommand(adb, "devices") ?: return emptyList()
    return output.lines()
        .drop(1)
        .map { it.trim() }
        .filter { it.endsWith("device") }
        .map { it.split("\t").first() }
}

fun dumpUiAutomator(serial: String? = null): String? {
    val adb = getAdbPath() ?: return null
    val prefix = if (serial != null) arrayOf(adb, "-s", serial) else arrayOf(adb)

    val dumpResult = runCommand(*prefix, "shell", "uiautomator", "dump", "/sdcard/view.xml")
    if (dumpResult == null || (!dumpResult.contains("UI hierarchy dumped to") && !dumpResult.contains("UI hierchary dumped to"))) {
        if (dumpResult?.contains("ERROR") == true) return null
    }

    val content = runCommand(*prefix, "shell", "cat", "/sdcard/view.xml")

    runCommand(*prefix, "shell", "rm", "/sdcard/view.xml")

    return content
}

fun takeScreenshot(serial: String? = null): ByteArray? {
    val adb = getAdbPath() ?: return null
    val cmd = if (serial != null) listOf(adb, "-s", serial, "exec-out", "screencap", "-p") else listOf(adb, "exec-out", "screencap", "-p")
    return try {
        val process = ProcessBuilder(cmd)
            .redirectErrorStream(false)
            .start()
        val bytes = process.inputStream.readBytes()
        process.waitFor(10, TimeUnit.SECONDS)
        if (process.exitValue() == 0 && bytes.isNotEmpty()) bytes else null
    } catch (e: Exception) {
        null
    }
}
