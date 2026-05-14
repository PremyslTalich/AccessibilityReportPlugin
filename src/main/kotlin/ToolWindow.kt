package cz.talich.arp

import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.ui.OnePixelSplitter
import com.intellij.ui.components.JBPanel
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.content.ContentFactory
import com.intellij.ui.treeStructure.Tree
import java.awt.BorderLayout
import javax.swing.JButton
import javax.swing.JTextArea
import javax.swing.tree.DefaultMutableTreeNode
import javax.swing.tree.DefaultTreeModel

class ArpToolWindowFactory : ToolWindowFactory {
    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        val arpToolWindow = ArpToolWindow(project)
        val content = ContentFactory.getInstance().createContent(arpToolWindow.getContent(), "", false)
        toolWindow.contentManager.addContent(content)
    }
}

class ArpToolWindow(private val project: Project) {
    private val panel = JBPanel<JBPanel<*>>(BorderLayout())
    private val tree = Tree(DefaultMutableTreeNode("No data"))
    private val propertiesArea = JTextArea()
    private val dumpButton = JButton("Dump UI Automator")

    init {
        propertiesArea.isEditable = false
        val treeScrollPane = JBScrollPane(tree)
        val propertiesScrollPane = JBScrollPane(propertiesArea)

        val splitter = OnePixelSplitter(true, 0.5f)
        splitter.firstComponent = treeScrollPane
        splitter.secondComponent = propertiesScrollPane

        panel.add(dumpButton, BorderLayout.NORTH)
        panel.add(splitter, BorderLayout.CENTER)

        tree.addTreeSelectionListener {
            val selectedNode = tree.lastSelectedPathComponent as? DefaultMutableTreeNode
            val node = selectedNode?.userObject as? Node
            if (node != null) {
                propertiesArea.text = formatNodeProperties(node)
            } else {
                propertiesArea.text = ""
            }
        }

        dumpButton.addActionListener {
            val raw = dumpUiAutomator()
            val dumpNode = if (raw != null) UIAutomatorParser.parse(raw) else null

            if (dumpNode != null) {
                val root = createTreeNodes(dumpNode)
                tree.model = DefaultTreeModel(root)
            } else {
                tree.model = DefaultTreeModel(DefaultMutableTreeNode("Failed to get UI Automator dump."))
                propertiesArea.text = ""
            }
        }
    }

    private fun createTreeNodes(node: Node): DefaultMutableTreeNode {
        val treeNode = DefaultMutableTreeNode(node)
        for (child in node.children) {
            treeNode.add(createTreeNodes(child))
        }
        return treeNode
    }

    private fun formatNodeProperties(node: Node): String {
        return buildString {
            appendLine("ID: ${node.id}")
            appendLine("Class: ${node.className}")
            appendLine("Text: ${node.text ?: "null"}")
            appendLine("Description: ${node.description ?: "null"}")
            appendLine("Bounds: [${node.bounds.left}, ${node.bounds.top}][${node.bounds.right}, ${node.bounds.bottom}]")
        }
    }

    fun getContent() = panel
}
