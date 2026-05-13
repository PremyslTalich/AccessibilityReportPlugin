package cz.talich.arp

import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.ui.components.JBPanel
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.content.ContentFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.awt.BorderLayout
import javax.swing.JButton
import javax.swing.JTextArea

class ArpToolWindowFactory : ToolWindowFactory {
    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        val arpToolWindow = ArpToolWindow(project)
        val content = ContentFactory.getInstance().createContent(arpToolWindow.getContent(), "", false)
        toolWindow.contentManager.addContent(content)
    }
}

class ArpToolWindow(private val project: Project) {
    private val panel = JBPanel<JBPanel<*>>(BorderLayout())
    private val textArea = JTextArea()
    private val dumpButton = JButton("Dump UI Automator")

    init {
        textArea.isEditable = false
        val scrollPane = JBScrollPane(textArea)
        
        panel.add(dumpButton, BorderLayout.NORTH)
        panel.add(scrollPane, BorderLayout.CENTER)

        dumpButton.addActionListener {
            val cs = project.service<ProjectCoroutineScopeHolder>().scope
            cs.launch {
                val dumpNode = withContext(Dispatchers.IO) {
                    dumpUiAutomator()?.let { UIAutomatorParser.parse(it) }
                }
                
                withContext(Dispatchers.Main) {
                    if (dumpNode != null) {
                        textArea.text = dumpNode.toString()
                    } else {
                        textArea.text = "Failed to get UI Automator dump."
                    }
                }
            }
        }
    }

    fun getContent() = panel
}
