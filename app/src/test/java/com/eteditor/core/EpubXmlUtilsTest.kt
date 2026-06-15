package com.eteditor.core

import java.nio.charset.StandardCharsets
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class EpubXmlUtilsTest {
    @Test
    fun parseXmlReadsNamespacedDocument() {
        val xml = """
            <?xml version="1.0" encoding="UTF-8"?>
            <package xmlns="urn:oasis:names:tc:opendocument:xmlns:container">
                <metadata>
                    <title>Book</title>
                </metadata>
            </package>
        """.trimIndent()

        val doc = parseXml(xml.toByteArray(StandardCharsets.UTF_8))

        assertEquals("package", doc.documentElement.localName)
        assertEquals(
            "urn:oasis:names:tc:opendocument:xmlns:container",
            doc.documentElement.namespaceURI
        )
    }

    @Test
    fun parseXmlRejectsDoctypeDeclarations() {
        val xml = """
            <?xml version="1.0" encoding="UTF-8"?>
            <!DOCTYPE package [
                <!ENTITY xxe SYSTEM "file:///etc/passwd">
            ]>
            <package>&xxe;</package>
        """.trimIndent()

        assertThrows(Exception::class.java) {
            parseXml(xml.toByteArray(StandardCharsets.UTF_8))
        }
    }

    @Test
    fun xmlElementHelpersReadAttributesAndDirectChildren() {
        val xml = """
            <?xml version="1.0" encoding="UTF-8"?>
            <package xmlns:opf="http://www.idpf.org/2007/opf">
                <manifest>
                    <item id="c1" href="Text/chapter.xhtml" opf:role="body" />
                    <group>
                        <item id="nested" href="Text/nested.xhtml" />
                    </group>
                </manifest>
            </package>
        """.trimIndent()

        val doc = parseXml(xml.toByteArray(StandardCharsets.UTF_8))
        val manifest = doc.elements("manifest").single()
        val directItem = manifest.children("item").single()

        assertEquals(2, doc.elements("item").size)
        assertEquals("c1", directItem.attr("id"))
        assertEquals("Text/chapter.xhtml", directItem.attrAny("href"))
        assertEquals("body", directItem.attrAny("opf:role"))
        assertEquals("body", directItem.attributes.toAttributeMap()["opf:role"])
    }

    @Test
    fun xmlElementHelpersMatchNamespacedElementsByLocalName() {
        val xml = """
            <?xml version="1.0" encoding="UTF-8"?>
            <package xmlns:opf="http://www.idpf.org/2007/opf">
                <manifest>
                    <opf:item id="c1" href="Text/chapter.xhtml" />
                    <group>
                        <opf:item id="nested" href="Text/nested.xhtml" />
                    </group>
                </manifest>
            </package>
        """.trimIndent()

        val doc = parseXml(xml.toByteArray(StandardCharsets.UTF_8))
        val manifest = doc.elements("manifest").single()

        assertEquals(listOf("c1", "nested"), doc.elements("item").map { it.attr("id") })
        assertEquals(listOf("c1"), manifest.children("item").map { it.attr("id") })
    }

    @Test
    fun attrAnyFallsBackToAttributeLocalNameAcrossPrefixes() {
        val xml = """
            <?xml version="1.0" encoding="UTF-8"?>
            <package xmlns:a="urn:a" xmlns:opf="http://www.idpf.org/2007/opf">
                <manifest>
                    <item id="cover" a:role="cover-image" opf:scheme="" />
                </manifest>
            </package>
        """.trimIndent()

        val item = parseXml(xml.toByteArray(StandardCharsets.UTF_8))
            .elements("item")
            .single()

        assertEquals("cover-image", item.attrAny("opf:role"))
        assertEquals("cover-image", item.attrAny("missing", "a:role"))
        assertEquals("", item.attrAny("opf:scheme"))
    }

    @Test
    fun attrAnyPrefersExactQualifiedAttributeBeforeLocalNameFallback() {
        val xml = """
            <?xml version="1.0" encoding="UTF-8"?>
            <package xmlns:a="urn:a" xmlns:opf="http://www.idpf.org/2007/opf">
                <manifest>
                    <item id="cover" a:role="fallback-role" opf:role="exact-role" />
                </manifest>
            </package>
        """.trimIndent()

        val item = parseXml(xml.toByteArray(StandardCharsets.UTF_8))
            .elements("item")
            .single()

        assertEquals("exact-role", item.attrAny("opf:role"))
        assertEquals("fallback-role", item.attrAny("a:role"))
    }

    @Test
    fun serializeXmlWritesUtf8AndIndentedSerializersDropBlankTextNodes() {
        val xml = """
            <?xml version="1.0" encoding="UTF-8"?>
            <package>
                
                <manifest>
                    <item id="c1" href="Text/chapter.xhtml" />
                </manifest>
            </package>
        """.trimIndent()
        val doc = parseXml(xml.toByteArray(StandardCharsets.UTF_8))

        val compact = String(serializeXml(doc), StandardCharsets.UTF_8)
        val opf = String(serializeOpfXml(doc), StandardCharsets.UTF_8)

        assertTrue(compact.contains("encoding=\"UTF-8\""))
        assertTrue(compact.contains("href=\"Text/chapter.xhtml\""))
        assertTrue(opf.contains("<manifest>"))
        assertTrue(opf.contains("<item"))
        assertTrue(opf.contains("id=\"c1\""))
        assertTrue(opf.contains("href=\"Text/chapter.xhtml\""))
    }
}
