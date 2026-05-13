package cz.talich.arp

import com.intellij.openapi.util.JDOMUtil
import org.jdom.Element
import java.io.StringReader

object UIAutomatorParser {
    fun parse(xmlContent: String): Node? {
        val root = JDOMUtil.load(StringReader(xmlContent))
        
        // UI Automator XML usually starts with <hierarchy>
        if (root.name == "hierarchy") {
            val children = root.children.filterIsInstance<Element>().map { parseElement(it) }
            return filterEmptyIdNodes(children).firstOrNull()
        }
        
        return parseElement(root)
    }

    private fun parseElement(element: Element): Node {
        val id = element.getAttributeValue("resource-id") ?: ""
        val className = element.getAttributeValue("class") ?: ""
        val boundsStr = element.getAttributeValue("bounds") ?: "[0,0][0,0]"
        val text = element.getAttributeValue("text")
        val description = element.getAttributeValue("content-desc")
        
        val bounds = parseBounds(boundsStr)
        
        val children = element.children.filterIsInstance<Element>().map { parseElement(it) }
        
        return Node(
            id = id,
            className = className,
            bounds = bounds,
            text = text,
            description = description,
            children = children
        )
    }

    private fun parseBounds(boundsStr: String): NodeBounds {
        // Format: [left,top][right,bottom]  e.g., [0,0][1080,2154]
        val regex = Regex("\\[(\\d+),(\\d+)\\]\\[(\\d+),(\\d+)\\]")
        val match = regex.find(boundsStr)
        return if (match != null) {
            val (left, top, right, bottom) = match.destructured
            NodeBounds(left.toInt(), top.toInt(), right.toInt(), bottom.toInt())
        } else {
            NodeBounds(0, 0, 0, 0)
        }
    }

    private fun filterEmptyIdNodes(nodes: List<Node>): List<Node> {
        val result = mutableListOf<Node>()
        for (node in nodes) {
            if (node.id.isNotEmpty()) {
                result.add(node.copy(children = filterEmptyIdNodes(node.children)))
            } else {
                result.addAll(filterEmptyIdNodes(node.children))
            }
        }
        return result
    }
}
