package cz.talich.arp

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class UIAutomatorParserTest {

    @Test
    fun testParseSimpleXml() {
        val xml = """
            <?xml version='1.0' encoding='UTF-8' standalone='yes' ?>
            <hierarchy rotation="0">
              <node index="0" text="" resource-id="android:id/content" class="android.widget.FrameLayout" package="com.android.settings" content-desc="" checkable="false" checked="false" clickable="false" enabled="true" focusable="false" focused="false" scrollable="false" long-clickable="false" password="false" selected="false" bounds="[0,0][1080,2154]">
                <node index="0" text="Settings" resource-id="com.android.settings:id/title" class="android.widget.TextView" package="com.android.settings" content-desc="" checkable="false" checked="false" clickable="false" enabled="true" focusable="false" focused="false" scrollable="false" long-clickable="false" password="false" selected="false" bounds="[42,120][214,175]" />
              </node>
            </hierarchy>
        """.trimIndent()

        val rootNode = UIAutomatorParser.parse(xml)
        assertNotNull(rootNode)
        assertEquals("android:id/content", rootNode.id)
        assertEquals("android.widget.FrameLayout", rootNode.className)
        assertEquals(0, rootNode.bounds.left)
        assertEquals(0, rootNode.bounds.top)
        assertEquals(1080, rootNode.bounds.right)
        assertEquals(2154, rootNode.bounds.bottom)
        
        assertEquals(1, rootNode.children.size)
        val child = rootNode.children[0]
        assertEquals("com.android.settings:id/title", child.id)
        assertEquals("Settings", child.text)
        assertEquals(42, child.bounds.left)
    }

    @Test
    fun testParseXmlWithEmptyIdNodes() {
        val xml = """
            <?xml version='1.0' encoding='UTF-8' standalone='yes' ?>
            <hierarchy rotation="0">
              <node index="0" text="" resource-id="" class="android.widget.FrameLayout" package="com.android.settings" content-desc="" bounds="[0,0][1080,2154]">
                <node index="0" text="Settings" resource-id="com.android.settings:id/title" class="android.widget.TextView" package="com.android.settings" content-desc="" bounds="[42,120][214,175]" />
              </node>
            </hierarchy>
        """.trimIndent()

        val rootNode = UIAutomatorParser.parse(xml)
        assertNotNull(rootNode)
        assertEquals("com.android.settings:id/title", rootNode.id)
        assertEquals("Settings", rootNode.text)
    }
}
