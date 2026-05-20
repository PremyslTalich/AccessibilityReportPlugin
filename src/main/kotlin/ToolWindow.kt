package cz.talich.arp

import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.actionSystem.ToggleAction
import com.intellij.openapi.application.EDT
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.intellij.ui.OnePixelSplitter
import com.intellij.ui.components.JBPanel
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.content.ContentFactory
import com.intellij.ui.table.JBTable
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
import javax.swing.table.DefaultTableModel
import javax.swing.tree.DefaultMutableTreeNode
import javax.swing.tree.DefaultTreeModel
import javax.swing.tree.TreePath
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.awt.Cursor
import java.awt.event.MouseMotionAdapter
import com.intellij.openapi.fileChooser.FileChooserFactory
import com.intellij.openapi.fileChooser.FileSaverDescriptor
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.testFramework.LightVirtualFile
import java.io.StringWriter
import javax.xml.parsers.DocumentBuilderFactory
import javax.xml.transform.OutputKeys
import javax.xml.transform.TransformerFactory
import javax.xml.transform.dom.DOMSource
import javax.xml.transform.stream.StreamResult
import java.awt.Component
import javax.swing.JTree
import javax.swing.tree.DefaultTreeCellRenderer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob

class ArpToolWindowFactory : ToolWindowFactory {
    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        val arpToolWindow = ArpToolWindow(project)
        val content = ContentFactory.getInstance().createContent(arpToolWindow.getContent(), "", false)
        toolWindow.contentManager.addContent(content)
    }
}

class ArpToolWindow(private val project: Project) {
    private val coroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val panel = JBPanel<JBPanel<*>>(BorderLayout())
    private val tree = Tree(DefaultMutableTreeNode("No data"))
    private val propertiesTableModel = object : DefaultTableModel(arrayOf("Property", "Value"), 0) {
        override fun isCellEditable(row: Int, column: Int) = false
    }
    private val propertiesTable = JBTable(propertiesTableModel)
    private val deviceComboBox = JComboBox<String>()
    private val dumpButton = JButton("Generate report")
    private val screenshotLabel = ScaledImagePanel()

    private var rootNode: Node? = null
    private var hoveredNode: Node? = null
    private var rawXml: String? = null
    private var filterMissingDescriptions = false

    private val adbController = AdbController(project)

    init {
        propertiesTable.tableHeader.reorderingAllowed = false
        propertiesTable.addMouseListener(object : MouseAdapter() {
            override fun mouseClicked(e: MouseEvent) {
                if (e.clickCount == 2) {
                    val row = propertiesTable.rowAtPoint(e.point)
                    if (row >= 0) {
                        val value = propertiesTableModel.getValueAt(row, 1)?.toString() ?: ""
                        val selection = java.awt.datatransfer.StringSelection(value)
                        java.awt.Toolkit.getDefaultToolkit().systemClipboard.setContents(selection, null)
                    }
                }
            }
        })
        val propertiesPopupMenu = javax.swing.JPopupMenu().apply {
            add(javax.swing.JMenuItem("Copy Value").apply {
                addActionListener {
                    val row = propertiesTable.selectedRow
                    if (row >= 0) {
                        val value = propertiesTableModel.getValueAt(row, 1)?.toString() ?: ""
                        val selection = java.awt.datatransfer.StringSelection(value)
                        java.awt.Toolkit.getDefaultToolkit().systemClipboard.setContents(selection, null)
                    }
                }
            })
            add(javax.swing.JMenuItem("Copy Key: Value").apply {
                addActionListener {
                    val row = propertiesTable.selectedRow
                    if (row >= 0) {
                        val key = propertiesTableModel.getValueAt(row, 0)?.toString() ?: ""
                        val value = propertiesTableModel.getValueAt(row, 1)?.toString() ?: ""
                        val selection = java.awt.datatransfer.StringSelection("$key: $value")
                        java.awt.Toolkit.getDefaultToolkit().systemClipboard.setContents(selection, null)
                    }
                }
            })
        }
        propertiesTable.addMouseListener(object : MouseAdapter() {
            override fun mousePressed(e: MouseEvent) { showPopupIfOnRow(e) }
            override fun mouseReleased(e: MouseEvent) { showPopupIfOnRow(e) }
            private fun showPopupIfOnRow(e: MouseEvent) {
                if (e.isPopupTrigger) {
                    val row = propertiesTable.rowAtPoint(e.point)
                    if (row >= 0) {
                        propertiesTable.setRowSelectionInterval(row, row)
                        propertiesPopupMenu.show(propertiesTable, e.x, e.y)
                    }
                }
            }
        })
        val treeScrollPane = JBScrollPane(tree)
        val propertiesScrollPane = JBScrollPane(propertiesTable)

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
            override fun update(e: AnActionEvent) {
                e.presentation.isEnabled = rootNode != null
            }
        }
        val collapseAllAction = object : AnAction("Collapse All", "Collapse all tree nodes", AllIcons.Actions.Collapseall) {
            override fun actionPerformed(e: AnActionEvent) {
                collapseAllNodes(tree)
            }
            override fun update(e: AnActionEvent) {
                e.presentation.isEnabled = rootNode != null
            }
        }
        fun prettifyXml(xml: String): String = try {
            val factory = DocumentBuilderFactory.newInstance()
            val builder = factory.newDocumentBuilder()
            val document = builder.parse(xml.byteInputStream())
            val transformer = TransformerFactory.newInstance().newTransformer()
            transformer.setOutputProperty(OutputKeys.INDENT, "yes")
            transformer.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "2")
            transformer.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "no")
            val writer = StringWriter()
            transformer.transform(DOMSource(document), StreamResult(writer))
            writer.toString()
        } catch (_: Exception) {
            xml
        }

        val exportAction = object : AnAction("Export Source XML", "Export raw XML dump", AllIcons.ToolbarDecorator.Export) {
            override fun actionPerformed(e: AnActionEvent) {
                val xml = rawXml ?: return
                val descriptor = FileSaverDescriptor("Export UI Dump", "Save raw XML dump", "xml")
                val dialog = FileChooserFactory.getInstance().createSaveFileDialog(descriptor, project)
                val wrapper = dialog.save("ui_dump.xml")
                if (wrapper != null) {
                    val file = wrapper.file
                    val target = if (!file.name.endsWith(".xml", ignoreCase = true)) {
                        java.io.File(file.absolutePath + ".xml")
                    } else {
                        file
                    }
                    target.writeText(prettifyXml(xml))
                }
            }
            override fun update(e: AnActionEvent) {
                e.presentation.isEnabled = rawXml != null
            }
        }
        val viewSourceXmlAction = object : AnAction("View Source XML in Editor", "Open raw XML dump in editor", AllIcons.Actions.Preview) {
            override fun actionPerformed(e: AnActionEvent) {
                val xml = rawXml ?: return
                val virtualFile = LightVirtualFile("ui_dump.xml", prettifyXml(xml))
                FileEditorManager.getInstance(project).openFile(virtualFile, true)
            }
            override fun update(e: AnActionEvent) {
                e.presentation.isEnabled = rawXml != null
            }
        }
        val clearAction = object : AnAction("Clear Data", "Clear all data", AllIcons.Actions.GC) {
            override fun actionPerformed(e: AnActionEvent) {
                rootNode = null
                rawXml = null
                filterMissingDescriptions = false
                screenshotLabel.setRootNode(null)
                screenshotLabel.setMultiHighlightBounds(emptyList())
                tree.model = DefaultTreeModel(DefaultMutableTreeNode("No data"))
                clearPropertiesTable()
                screenshotLabel.setImage(null)
            }
            override fun update(e: AnActionEvent) {
                e.presentation.isEnabled = rootNode != null
            }
        }
        val highlightMissingAccessibilityAction = object : ToggleAction("Show Missing Accessibility", "Highlight nodes with missing accessibility", null) {
            override fun isSelected(e: AnActionEvent): Boolean = filterMissingDescriptions
            override fun setSelected(e: AnActionEvent, state: Boolean) {
                filterMissingDescriptions = state
                if (state) {
                    val xml = rawXml
                    if (xml != null) {
                        val bounds = UIAutomatorParser.findMissingResourceIdTextViewBounds(xml)
                        screenshotLabel.setMultiHighlightBounds(bounds)
                    }
                } else {
                    screenshotLabel.setMultiHighlightBounds(emptyList())
                }
            }
            override fun update(e: AnActionEvent) {
                super.update(e)
                e.presentation.isEnabled = rootNode != null
            }
        }
        val treeActionGroup = DefaultActionGroup().apply {
            add(viewSourceXmlAction)
            add(exportAction)
            addSeparator()
            add(highlightMissingAccessibilityAction)
            addSeparator()
            add(clearAction)
        }
        val moreAction = object : AnAction("More", "More actions", AllIcons.Actions.More) {
            override fun actionPerformed(e: AnActionEvent) {
                val popup = ActionManager.getInstance().createActionPopupMenu("ArpTreePopup", treeActionGroup)
                val component = e.inputEvent?.component ?: return
                popup.component.show(component, 0, component.height)
            }
        }
        val toolbarGroup = DefaultActionGroup().apply {
            add(expandAllAction)
            add(collapseAllAction)
            add(moreAction)
        }
        val toolbar = ActionManager.getInstance().createActionToolbar("ArpTreeToolbar", toolbarGroup, true)
        toolbar.targetComponent = tree

        val buttonPanel = JPanel(BorderLayout())
        val leftButtons = JPanel(java.awt.FlowLayout(java.awt.FlowLayout.LEFT, 5, 5))
        leftButtons.add(deviceComboBox)
        leftButtons.add(dumpButton)
        buttonPanel.add(leftButtons, BorderLayout.WEST)
        buttonPanel.add(toolbar.component, BorderLayout.EAST)

        val leftPanel = JBPanel<JBPanel<*>>(BorderLayout())
        leftPanel.add(buttonPanel, BorderLayout.NORTH)
        leftPanel.add(leftSplitter, BorderLayout.CENTER)

        mainSplitter.firstComponent = leftPanel
        panel.add(mainSplitter, BorderLayout.CENTER)

        tree.cellRenderer = object : DefaultTreeCellRenderer() {
            override fun getTreeCellRendererComponent(
                tree: JTree, value: Any?, selected: Boolean, expanded: Boolean,
                leaf: Boolean, row: Int, hasFocus: Boolean
            ): Component {
                val comp = super.getTreeCellRendererComponent(tree, value, selected, expanded, leaf, row, hasFocus)
                icon = null
                if (!selected) {
                    isOpaque = false
                    background = null
                    backgroundNonSelectionColor = null
                }
                val treeNode = value as? DefaultMutableTreeNode
                val node = treeNode?.userObject as? Node
                if (node != null && node === hoveredNode && !selected) {
                    comp.foreground = Color.RED
                } else if (!selected) {
                    comp.foreground = tree.foreground
                }
                return comp
            }
        }

        tree.addMouseListener(object : MouseAdapter() {
            override fun mouseClicked(e: MouseEvent) {
                if (tree.getPathForLocation(e.x, e.y) == null) {
                    val closestRow = tree.getClosestRowForLocation(e.x, e.y)
                    if (closestRow < 0 || e.y > tree.getRowBounds(closestRow).y + tree.getRowBounds(closestRow).height) {
                        tree.clearSelection()
                    }
                }
            }
        })

        tree.addMouseMotionListener(object : MouseMotionAdapter() {
            override fun mouseMoved(e: MouseEvent) {
                val path = tree.getPathForLocation(e.x, e.y)
                val treeNode = (path?.lastPathComponent as? DefaultMutableTreeNode)
                val node = treeNode?.userObject as? Node
                if (node !== hoveredNode) {
                    hoveredNode = node
                    screenshotLabel.setHoverBounds(node?.bounds)
                    tree.cursor = if (node != null) Cursor.getPredefinedCursor(Cursor.HAND_CURSOR) else Cursor.getDefaultCursor()
                    tree.repaint()
                }
            }
        })
        tree.addMouseListener(object : MouseAdapter() {
            override fun mouseExited(e: MouseEvent) {
                if (hoveredNode != null) {
                    hoveredNode = null
                    screenshotLabel.setHoverBounds(null)
                    tree.cursor = Cursor.getDefaultCursor()
                    tree.repaint()
                }
            }
        })

        tree.addTreeSelectionListener {
            val selectedNode = tree.lastSelectedPathComponent as? DefaultMutableTreeNode
            val node = selectedNode?.userObject as? Node
            if (node != null) {
                updatePropertiesTable(node)
                screenshotLabel.setHighlightBounds(node.bounds)
            } else {
                clearPropertiesTable()
                screenshotLabel.setHighlightBounds(null)
            }
        }

        screenshotLabel.onNodeHovered = { node ->
            hoveredNode = node
            tree.repaint()
        }

        screenshotLabel.onNodeClicked = { node ->
            if (node != null) {
                val treeNode = findTreeNode(tree.model.root as? DefaultMutableTreeNode, node)
                if (treeNode != null) {
                    val path = TreePath(treeNode.path)
                    tree.selectionPath = path
                    tree.scrollPathToVisible(path)
                }
            } else {
                tree.clearSelection()
                clearPropertiesTable()
                screenshotLabel.setHighlightBounds(null)
            }
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
            dumpButton.isEnabled = false
            dumpButton.text = "Generating..."
            rootNode = null
            rawXml = null
            filterMissingDescriptions = false
            screenshotLabel.setRootNode(null)
            screenshotLabel.setMultiHighlightBounds(emptyList())
            tree.model = DefaultTreeModel(DefaultMutableTreeNode("Loading…"))
            clearPropertiesTable()
            screenshotLabel.setImage(null)
            screenshotLabel.setLoading(true)

            coroutineScope.launch(Dispatchers.IO) {
                val screenshotBytes = adbController.takeScreenshot(selectedDevice)
                val raw = adbController.dumpUiAutomator(selectedDevice)
                val dumpNode = if (raw != null) UIAutomatorParser.parse(raw) else null

                withContext(Dispatchers.EDT) {
                    if (dumpNode != null) {
                        rootNode = dumpNode
                        rawXml = raw
                        screenshotLabel.setRootNode(dumpNode)
                        val root = createTreeNodes(dumpNode)
                        tree.model = DefaultTreeModel(root)
                        expandTreeToLevel(tree, TreePath(root), 2)
                        clearPropertiesTable()
                    } else {
                        tree.model = DefaultTreeModel(DefaultMutableTreeNode("Failed to get UI Automator dump."))
                        clearPropertiesTable()
                    }

                    if (screenshotBytes != null) {
                        val icon = ImageIcon(screenshotBytes)
                        screenshotLabel.setImage(icon.image)
                    } else {
                        screenshotLabel.setImage(null)
                    }

                    dumpButton.text = "Generate report"
                    refreshDeviceList()
                }
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

    private fun createFilteredTreeNodes(node: Node): DefaultMutableTreeNode? {
        val filteredChildren = node.children.mapNotNull { createFilteredTreeNodes(it) }
        val isMissing = node.description.isNullOrBlank()
        return if (isMissing || filteredChildren.isNotEmpty()) {
            val treeNode = DefaultMutableTreeNode(node)
            filteredChildren.forEach { treeNode.add(it) }
            treeNode
        } else {
            null
        }
    }

    private fun updatePropertiesTable(node: Node) {
        propertiesTableModel.rowCount = 0
        propertiesTableModel.addRow(arrayOf("ID", node.id))
        propertiesTableModel.addRow(arrayOf("Class", node.className))
        propertiesTableModel.addRow(arrayOf("Text", node.text ?: "null"))
        propertiesTableModel.addRow(arrayOf("Description", node.description ?: "null"))
        propertiesTableModel.addRow(arrayOf("Bounds", "[${node.bounds.left}, ${node.bounds.top}][${node.bounds.right}, ${node.bounds.bottom}]"))
    }

    private fun clearPropertiesTable() {
        propertiesTableModel.rowCount = 0
    }

    private fun refreshDeviceList() {
        val devices = adbController.getConnectedDevices()
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

    private fun findTreeNode(root: DefaultMutableTreeNode?, target: Node): DefaultMutableTreeNode? {
        if (root == null) return null
        if (root.userObject === target) return root
        for (i in 0 until root.childCount) {
            val found = findTreeNode(root.getChildAt(i) as DefaultMutableTreeNode, target)
            if (found != null) return found
        }
        return null
    }

    fun getContent() = panel
}

private class ScaledImagePanel : JPanel(BorderLayout()) {
    private var image: Image? = null
    private var highlightBounds: NodeBounds? = null
    private var hoverBounds: NodeBounds? = null
    private var multiHighlightBounds: List<NodeBounds> = emptyList()
    private var rootNode: Node? = null
    private var loading: Boolean = false
    var onNodeHovered: ((Node?) -> Unit)? = null
    var onNodeClicked: ((Node?) -> Unit)? = null

    init {
        addMouseMotionListener(object : MouseMotionAdapter() {
            override fun mouseMoved(e: MouseEvent) {
                val img = image ?: return
                val imgWidth = img.getWidth(this@ScaledImagePanel)
                val imgHeight = img.getHeight(this@ScaledImagePanel)
                if (imgWidth <= 0 || imgHeight <= 0) return

                val scale = minOf(width.toDouble() / imgWidth, height.toDouble() / imgHeight)
                val offsetX = (width - (imgWidth * scale).toInt()) / 2
                val offsetY = (height - (imgHeight * scale).toInt()) / 2

                val imgX = ((e.x - offsetX) / scale).toInt()
                val imgY = ((e.y - offsetY) / scale).toInt()

                val found = rootNode?.let { findDeepestNode(it, imgX, imgY) }
                val newBounds = found?.bounds
                if (newBounds != hoverBounds) {
                    hoverBounds = newBounds
                    onNodeHovered?.invoke(found)
                    cursor = if (found != null) Cursor.getPredefinedCursor(Cursor.HAND_CURSOR) else Cursor.getDefaultCursor()
                    repaint()
                }
            }
        })
        addMouseListener(object : MouseAdapter() {
            override fun mouseExited(e: MouseEvent) {
                if (hoverBounds != null) {
                    hoverBounds = null
                    onNodeHovered?.invoke(null)
                    cursor = Cursor.getDefaultCursor()
                    repaint()
                }
            }
            override fun mouseClicked(e: MouseEvent) {
                val img = image ?: return
                val imgWidth = img.getWidth(this@ScaledImagePanel)
                val imgHeight = img.getHeight(this@ScaledImagePanel)
                if (imgWidth <= 0 || imgHeight <= 0) return

                val scale = minOf(width.toDouble() / imgWidth, height.toDouble() / imgHeight)
                val offsetX = (width - (imgWidth * scale).toInt()) / 2
                val offsetY = (height - (imgHeight * scale).toInt()) / 2

                val imgX = ((e.x - offsetX) / scale).toInt()
                val imgY = ((e.y - offsetY) / scale).toInt()

                val found = rootNode?.let { findDeepestNode(it, imgX, imgY) }
                onNodeClicked?.invoke(found)
            }
        })
    }

    private fun findDeepestNode(node: Node, x: Int, y: Int): Node? {
        if (x < node.bounds.left || x > node.bounds.right || y < node.bounds.top || y > node.bounds.bottom) return null
        for (child in node.children) {
            val found = findDeepestNode(child, x, y)
            if (found != null) return found
        }
        if (node.bounds.left == 0 && node.bounds.top == 0) return null
        return node
    }

    fun setRootNode(node: Node?) {
        rootNode = node
    }

    fun setImage(img: Image?) {
        image = img
        highlightBounds = null
        hoverBounds = null
        loading = false
        repaint()
    }

    fun setLoading(isLoading: Boolean) {
        loading = isLoading
        repaint()
    }

    fun setHighlightBounds(bounds: NodeBounds?) {
        highlightBounds = bounds
        repaint()
    }

    fun setMultiHighlightBounds(boundsList: List<NodeBounds>) {
        multiHighlightBounds = boundsList
        repaint()
    }

    fun setHoverBounds(bounds: NodeBounds?) {
        hoverBounds = bounds
        repaint()
    }

    override fun paintComponent(g: Graphics) {
        super.paintComponent(g)
        if (loading) {
            val g2 = g as Graphics2D
            g2.setRenderingHint(java.awt.RenderingHints.KEY_ANTIALIASING, java.awt.RenderingHints.VALUE_ANTIALIAS_ON)
            val text = "Loading…"
            g2.font = g2.font.deriveFont(16f)
            val fm = g2.fontMetrics
            val textX = (width - fm.stringWidth(text)) / 2
            val textY = height / 2 + fm.ascent / 2
            g2.color = foreground
            g2.drawString(text, textX, textY)
            return
        }
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

        val g2 = g as Graphics2D

        val hover = hoverBounds
        if (hover != null) {
            val rectX = x + (hover.left * scale).toInt()
            val rectY = y + (hover.top * scale).toInt()
            val rectW = ((hover.right - hover.left) * scale).toInt()
            val rectH = ((hover.bottom - hover.top) * scale).toInt()
            g2.color = Color(255, 0, 0, 80)
            g2.fillRect(rectX, rectY, rectW, rectH)
            g2.color = Color.RED
            g2.stroke = BasicStroke(2f)
            g2.drawRect(rectX, rectY, rectW, rectH)
        }

        for (mb in multiHighlightBounds) {
            val mbX = x + (mb.left * scale).toInt()
            val mbY = y + (mb.top * scale).toInt()
            val mbW = ((mb.right - mb.left) * scale).toInt()
            val mbH = ((mb.bottom - mb.top) * scale).toInt()
            g2.color = Color(255, 165, 0, 50)
            g2.fillRect(mbX, mbY, mbW, mbH)
            g2.color = Color(255, 165, 0)
            g2.stroke = BasicStroke(2f)
            g2.drawRect(mbX, mbY, mbW, mbH)
        }

        val bounds = highlightBounds
        if (bounds != null) {
            val rectX = x + (bounds.left * scale).toInt()
            val rectY = y + (bounds.top * scale).toInt()
            val rectW = ((bounds.right - bounds.left) * scale).toInt()
            val rectH = ((bounds.bottom - bounds.top) * scale).toInt()
            g2.color = Color(0, 120, 255, 50)
            g2.fillRect(rectX, rectY, rectW, rectH)
            g2.color = Color(0, 120, 255)
            g2.stroke = BasicStroke(2f)
            g2.drawRect(rectX, rectY, rectW, rectH)
        }
    }
}
