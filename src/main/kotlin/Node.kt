package cz.talich.arp

data class Node(
    val id: String,
    val className: String,
    val bounds: NodeBounds,
    val text: String?,
    val description: String?,
    val children: List<Node> = emptyList()
)

data class NodeBounds(
    val left: Int,
    val top: Int,
    val right: Int,
    val bottom: Int
)
