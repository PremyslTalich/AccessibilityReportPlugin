package cz.talich.arp

import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.ui.OnePixelSplitter
import com.intellij.ui.components.JBPanel
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.content.ContentFactory
import com.intellij.ui.treeStructure.Tree
import java.awt.BorderLayout
import java.awt.Color
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.Image
import java.awt.BasicStroke
import javax.swing.ImageIcon
import javax.swing.DefaultComboBoxModel
import javax.swing.JButton
import javax.swing.JComboBox
import javax.swing.JPanel
import javax.swing.JTextArea
import javax.swing.tree.DefaultMutableTreeNode
import javax.swing.tree.DefaultTreeModel
import javax.swing.tree.TreePath

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
    private val deviceComboBox = JComboBox<String>()
    private val dumpButton = JButton("Dump UI Automator")
    private val clearButton = JButton("Clear")
    private val screenshotLabel = ScaledImagePanel()

    init {
        propertiesArea.isEditable = false
        propertiesArea.componentPopupMenu = javax.swing.JPopupMenu().apply {
            add(javax.swing.JMenuItem("Copy").apply {
                addActionListener { propertiesArea.copy() }
            })
            add(javax.swing.JMenuItem("Select All").apply {
                addActionListener { propertiesArea.selectAll() }
            })
        }
        val treeScrollPane = JBScrollPane(tree)
        val propertiesScrollPane = JBScrollPane(propertiesArea)

        val leftSplitter = OnePixelSplitter(true, 0.75f)
        leftSplitter.firstComponent = treeScrollPane
        leftSplitter.secondComponent = propertiesScrollPane

        val screenshotScrollPane = JBScrollPane(screenshotLabel)

        val mainSplitter = OnePixelSplitter(false, 0.5f)
        mainSplitter.firstComponent = leftSplitter
        mainSplitter.secondComponent = screenshotScrollPane

        refreshDeviceList()

        val expandAllAction = object : AnAction("Expand All", "Expand all tree nodes", AllIcons.Actions.Expandall) {
            override fun actionPerformed(e: AnActionEvent) {
                expandAllNodes(tree)
            }
        }
        val collapseAllAction = object : AnAction("Collapse All", "Collapse all tree nodes", AllIcons.Actions.Collapseall) {
            override fun actionPerformed(e: AnActionEvent) {
                collapseAllNodes(tree)
            }
        }
        val actionGroup = DefaultActionGroup(expandAllAction, collapseAllAction)
        val toolbar = ActionManager.getInstance().createActionToolbar("ArpTreeToolbar", actionGroup, true)
        toolbar.targetComponent = tree

        val buttonPanel = JPanel(BorderLayout())
        val leftButtons = JPanel(java.awt.FlowLayout(java.awt.FlowLayout.LEFT, 5, 5))
        leftButtons.add(deviceComboBox)
        leftButtons.add(dumpButton)
        leftButtons.add(clearButton)
        buttonPanel.add(leftButtons, BorderLayout.WEST)
        buttonPanel.add(toolbar.component, BorderLayout.EAST)

        val leftPanel = JBPanel<JBPanel<*>>(BorderLayout())
        leftPanel.add(buttonPanel, BorderLayout.NORTH)
        leftPanel.add(leftSplitter, BorderLayout.CENTER)

        mainSplitter.firstComponent = leftPanel
        panel.add(mainSplitter, BorderLayout.CENTER)

        tree.addTreeSelectionListener {
            val selectedNode = tree.lastSelectedPathComponent as? DefaultMutableTreeNode
            val node = selectedNode?.userObject as? Node
            if (node != null) {
                propertiesArea.text = formatNodeProperties(node)
                screenshotLabel.setHighlightBounds(node.bounds)
            } else {
                propertiesArea.text = ""
                screenshotLabel.setHighlightBounds(null)
            }
        }

        clearButton.addActionListener {
            tree.model = DefaultTreeModel(DefaultMutableTreeNode("No data"))
            propertiesArea.text = ""
            screenshotLabel.setImage(null)
        }

        deviceComboBox.addPopupMenuListener(object : javax.swing.event.PopupMenuListener {
            override fun popupMenuWillBecomeVisible(e: javax.swing.event.PopupMenuEvent?) {
                refreshDeviceList()
            }
            override fun popupMenuWillBecomeInvisible(e: javax.swing.event.PopupMenuEvent?) {}
            override fun popupMenuCanceled(e: javax.swing.event.PopupMenuEvent?) {}
        })

        dumpButton.addActionListener {
            refreshDeviceList()
            val selectedDevice = deviceComboBox.selectedItem as? String
            val screenshotBytes = takeScreenshot(selectedDevice)
            val raw = dumpUiAutomator(selectedDevice)

            val dumpNode = if (raw != null) UIAutomatorParser.parse(raw) else null

            if (dumpNode != null) {
                val root = createTreeNodes(dumpNode)
                tree.model = DefaultTreeModel(root)
                expandTreeToLevel(tree, TreePath(root), 2)
                propertiesArea.text = raw
            } else {
                tree.model = DefaultTreeModel(DefaultMutableTreeNode("Failed to get UI Automator dump."))
                propertiesArea.text = ""
            }

            if (screenshotBytes != null) {
                val icon = ImageIcon(screenshotBytes)
                screenshotLabel.setImage(icon.image)
            } else {
                screenshotLabel.setImage(null)
            }
        }
    }

    private fun expandTreeToLevel(tree: Tree, path: TreePath, levels: Int) {
        if (levels <= 0) return
        tree.expandPath(path)
        val node = path.lastPathComponent as DefaultMutableTreeNode
        for (i in 0 until node.childCount) {
            expandTreeToLevel(tree, path.pathByAddingChild(node.getChildAt(i)), levels - 1)
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

    private fun refreshDeviceList() {
        val devices = getConnectedDevices()
        val previousSelection = deviceComboBox.selectedItem as? String
        deviceComboBox.model = DefaultComboBoxModel(devices.toTypedArray())
        if (previousSelection != null && devices.contains(previousSelection)) {
            deviceComboBox.selectedItem = previousSelection
        }
        dumpButton.isEnabled = devices.isNotEmpty()
    }

    private fun expandAllNodes(tree: Tree) {
        val root = tree.model.root as? DefaultMutableTreeNode ?: return
        val e = root.depthFirstEnumeration()
        while (e.hasMoreElements()) {
            val node = e.nextElement() as DefaultMutableTreeNode
            if (node.childCount > 0) {
                tree.expandPath(TreePath(node.path))
            }
        }
    }

    private fun collapseAllNodes(tree: Tree) {
        val root = tree.model.root as? DefaultMutableTreeNode ?: return
        val e = root.depthFirstEnumeration()
        while (e.hasMoreElements()) {
            val node = e.nextElement() as DefaultMutableTreeNode
            if (node.childCount > 0 && node != root) {
                tree.collapsePath(TreePath(node.path))
            }
        }
    }

    fun getContent() = panel
}

private class ScaledImagePanel : JPanel(BorderLayout()) {
    private var image: Image? = null
    private var highlightBounds: NodeBounds? = null

    fun setImage(img: Image?) {
        image = img
        highlightBounds = null
        repaint()
    }

    fun setHighlightBounds(bounds: NodeBounds?) {
        highlightBounds = bounds
        repaint()
    }

    override fun paintComponent(g: Graphics) {
        super.paintComponent(g)
        val img = image ?: return
        val imgWidth = img.getWidth(this)
        val imgHeight = img.getHeight(this)
        if (imgWidth <= 0 || imgHeight <= 0) return

        val scale = minOf(width.toDouble() / imgWidth, height.toDouble() / imgHeight)
        val w = (imgWidth * scale).toInt()
        val h = (imgHeight * scale).toInt()
        val x = (width - w) / 2
        val y = (height - h) / 2
        g.drawImage(img, x, y, w, h, this)

        val bounds = highlightBounds ?: return
        val g2 = g as Graphics2D
        val rectX = x + (bounds.left * scale).toInt()
        val rectY = y + (bounds.top * scale).toInt()
        val rectW = ((bounds.right - bounds.left) * scale).toInt()
        val rectH = ((bounds.bottom - bounds.top) * scale).toInt()
        g2.color = Color(255, 0, 0, 80)
        g2.fillRect(rectX, rectY, rectW, rectH)
        g2.color = Color.RED
        g2.stroke = BasicStroke(2f)
        g2.drawRect(rectX, rectY, rectW, rectH)
    }
}
