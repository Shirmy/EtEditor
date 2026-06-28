package com.eteditor.core

import org.w3c.dom.Element
import org.w3c.dom.NamedNodeMap
import org.w3c.dom.Node
import org.w3c.dom.NodeList
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.nio.charset.StandardCharsets
import javax.xml.XMLConstants
import javax.xml.parsers.DocumentBuilderFactory
import javax.xml.transform.OutputKeys
import javax.xml.transform.TransformerFactory
import javax.xml.transform.dom.DOMSource
import javax.xml.transform.stream.StreamResult

internal fun parseXml(bytes: ByteArray) = DocumentBuilderFactory.newInstance().apply {
    isNamespaceAware = true
    runCatching { setXIncludeAware(false) }
    runCatching { setExpandEntityReferences(false) }
    setFeatureSafely(XMLConstants.FEATURE_SECURE_PROCESSING, true)
    setFeatureSafely("http://apache.org/xml/features/disallow-doctype-decl", true)
    setFeatureSafely("http://apache.org/xml/features/nonvalidating/load-external-dtd", false)
    setFeatureSafely("http://xml.org/sax/features/external-general-entities", false)
    setFeatureSafely("http://xml.org/sax/features/external-parameter-entities", false)
    setAttributeSafely(XML_ACCESS_EXTERNAL_DTD, "")
    setAttributeSafely(XML_ACCESS_EXTERNAL_SCHEMA, "")
}.newDocumentBuilder().parse(ByteArrayInputStream(stripHarmlessDoctype(bytes)))

// 剥掉"光秃秃"的标准文档类型声明(不含内部定义块),让带这行声明的目录等结构文件能被正常解析与重写。
// 仅剥不含内部定义块（[ ... ]）的声明;一旦声明里夹带内部定义,保持原样、交由 disallow-doctype-decl 继续拒绝,防注入不受影响。
private val harmlessDoctypeRegex = Regex("""<!DOCTYPE[^\[>]*>""", RegexOption.IGNORE_CASE)

private fun stripHarmlessDoctype(bytes: ByteArray): ByteArray {
    val text = String(bytes, StandardCharsets.ISO_8859_1)
    if (!text.contains("<!DOCTYPE", ignoreCase = true)) return bytes
    val stripped = harmlessDoctypeRegex.replace(text, "")
    return if (stripped == text) bytes else stripped.toByteArray(StandardCharsets.ISO_8859_1)
}

internal fun serializeXml(doc: org.w3c.dom.Document): ByteArray {
    val out = ByteArrayOutputStream()
    TransformerFactory.newInstance().newTransformer().apply {
        setOutputProperty(OutputKeys.ENCODING, StandardCharsets.UTF_8.name())
        setOutputProperty(OutputKeys.INDENT, "no")
    }.transform(DOMSource(doc), StreamResult(out))
    return out.toByteArray()
}

internal fun serializeNcxXml(doc: org.w3c.dom.Document): ByteArray {
    return serializeIndentedXml(doc)
}

internal fun serializeOpfXml(doc: org.w3c.dom.Document): ByteArray {
    return serializeIndentedXml(doc)
}

private fun serializeIndentedXml(doc: org.w3c.dom.Document): ByteArray {
    removeBlankTextNodes(doc.documentElement)
    val out = ByteArrayOutputStream()
    TransformerFactory.newInstance().newTransformer().apply {
        setOutputProperty(OutputKeys.ENCODING, StandardCharsets.UTF_8.name())
        setOutputProperty(OutputKeys.INDENT, "yes")
        runCatching { setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "2") }
    }.transform(DOMSource(doc), StreamResult(out))
    return out.toByteArray()
}

private fun removeBlankTextNodes(node: Node?) {
    if (node == null) return
    val children = node.childNodes
    for (index in children.length - 1 downTo 0) {
        val child = children.item(index)
        if (child.nodeType == Node.TEXT_NODE && child.nodeValue.orEmpty().isBlank()) {
            node.removeChild(child)
        } else {
            removeBlankTextNodes(child)
        }
    }
}

private fun DocumentBuilderFactory.setFeatureSafely(feature: String, enabled: Boolean) {
    runCatching { setFeature(feature, enabled) }
}

private fun DocumentBuilderFactory.setAttributeSafely(name: String, value: String) {
    runCatching { setAttribute(name, value) }
}

private const val XML_ACCESS_EXTERNAL_DTD = "http://javax.xml.XMLConstants/property/accessExternalDTD"
private const val XML_ACCESS_EXTERNAL_SCHEMA = "http://javax.xml.XMLConstants/property/accessExternalSchema"

internal fun Element.attr(name: String): String = getAttribute(name).orEmpty()

internal fun Element.attrAny(vararg names: String): String {
    names.forEach { name ->
        getAttribute(name).orEmpty().takeIf(String::isNotBlank)?.let { return it }
    }
    val localNames = names
        .filter { it.contains(':') }
        .map { it.substringAfter(':') }
        .toSet()
    for (index in 0 until attributes.length) {
        val item = attributes.item(index)
        if (item.localName in localNames || item.nodeName in names) {
            item.nodeValue?.takeIf(String::isNotBlank)?.let { return it }
        }
    }
    return ""
}

internal fun org.w3c.dom.Document.elements(name: String): List<Element> {
    val namespaced = getElementsByTagNameNS("*", name).toElements()
    return namespaced.ifEmpty { getElementsByTagName(name).toElements() }
}

internal fun Element.children(name: String): List<Element> {
    return childNodes.toElements().filter { it.localName == name || it.tagName == name }
}

internal fun NodeList.toElements(): List<Element> {
    return (0 until length).mapNotNull { item(it) as? Element }
}

internal fun NamedNodeMap.toAttributeMap(): Map<String, String> {
    return (0 until length).associate { index ->
        val item = item(index)
        item.nodeName to item.nodeValue.orEmpty()
    }
}
