package cz.talich.arp

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.util.concurrency.annotations.RequiresEdt
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Properties
import java.util.concurrent.TimeUnit

class DumpUiAction : AnAction() {
    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val cs = project.service<ProjectCoroutineScopeHolder>().scope
        
        cs.launch {
            val dump = withContext(Dispatchers.IO) {
                dumpUiAutomator()
            }
            
            withContext(Dispatchers.Main) {
                if (dump != null) {
                    Messages.showInfoMessage(project, dump, "UI Automator Dump")
                } else {
                    Messages.showErrorDialog(project, "Failed to get UI Automator dump.", "Error")
                }
            }
        }
    }
}

@com.intellij.openapi.components.Service(com.intellij.openapi.components.Service.Level.PROJECT)
class ProjectCoroutineScopeHolder(val scope: CoroutineScope)

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
        e.printStackTrace()
        null
    }
}

/**
 * Calls the built-in adb and dump uiautomator, then returns its value as string.
 */
fun dumpUiAutomator(): String? {
    val adb = getAdbPath() ?: return null
    
    // 1. Dump UI to a file on the device
    val dumpResult = runCommand(adb, "shell", "uiautomator", "dump", "/sdcard/view.xml")
    if (dumpResult == null || !dumpResult.contains("UI hierchary dumped to")) {
        // Some devices might not print the exact string or might use a different path
        if (dumpResult?.contains("ERROR") == true) return null
    }
    
    // 2. Read the file from the device
    val content = runCommand(adb, "shell", "cat", "/sdcard/view.xml")
    
    // Optional: cleanup
    runCommand(adb, "shell", "rm", "/sdcard/view.xml")
    
    return content
}