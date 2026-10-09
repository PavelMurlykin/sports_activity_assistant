package com.pamurlykin.sportsactivityassistant

import com.pamurlykin.sportsactivityassistant.text.AppText
import java.util.Locale
import javax.xml.XMLConstants
import javax.xml.parsers.DocumentBuilderFactory
import org.w3c.dom.Element

/** JVM tests use the canonical Android XML, never a duplicate translation table. */
open class ResourceTextTest {
    init { AppText.install { id, args ->
        val value = checkNotNull(texts[id]) { "Missing resource $id" }
        if (args.isEmpty()) value else String.format(Locale.ROOT, value, *args)
    } }

    companion object {
        private val texts: Map<Int, String> by lazy {
            val ids = R.string::class.java.fields.associate { it.name to it.getInt(null) }
            val factory = DocumentBuilderFactory.newInstance().apply {
                setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true)
                setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
                setAttribute("http://javax.xml.XMLConstants/property/accessExternalDTD", "")
                setAttribute("http://javax.xml.XMLConstants/property/accessExternalSchema", "")
            }
            buildMap {
                for (file in listOf("strings.xml", "messages.xml")) {
                    val input = checkNotNull(ResourceTextTest::class.java.getResourceAsStream("/$file"))
                    val document = input.use { factory.newDocumentBuilder().parse(it) }
                    val nodes = document.getElementsByTagName("string")
                    for (i in 0 until nodes.length) {
                        val element = nodes.item(i) as Element
                        var value = element.textContent
                        if (value.startsWith('"') && value.endsWith('"')) value = value.substring(1, value.length - 1)
                        value = Regex("\\\\(.)").replace(value) { match ->
                            when (match.groupValues[1]) { "n" -> "\n"; "t" -> "\t"; else -> match.groupValues[1] }
                        }
                        put(checkNotNull(ids[element.getAttribute("name")]), value)
                    }
                }
            }
        }
    }
}
