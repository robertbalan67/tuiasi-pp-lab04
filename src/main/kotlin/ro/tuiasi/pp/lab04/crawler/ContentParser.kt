package ro.tuiasi.pp.lab04.crawler

import okhttp3.OkHttpClient
import okhttp3.Request
import org.jsoup.Jsoup
import org.jsoup.parser.Parser
import java.util.concurrent.TimeUnit

// ─── Interfață (DIP — Dependency Inversion Principle) ─────────────────────────

interface ContentParser {
    fun parse(content: String): Map<String, Any>
}

// ─── JsonParser ───────────────────────────────────────────────────────────────

/**
 * Parsează JSON flat {"key": value, ...} fără dependențe externe.
 * Suportă valori: string, long, double, boolean.
 *
 * Regex: "cheie" : "valoare_string" | true/false | numar
 * Folosim m.groups[n] != null ca să distingem grupul care a participat la match.
 */
class JsonParser : ContentParser {

    private val entryRegex = Regex(
        """"([^"]+)"\s*:\s*(?:"([^"]*)"|(true|false|null)|([-\d.]+))"""
    )

    override fun parse(content: String): Map<String, Any> {
        val result = mutableMapOf<String, Any>()
        entryRegex.findAll(content).forEach { m ->
            val key = m.groupValues[1]
            result[key] = when {
                m.groups[2] != null -> m.groupValues[2]             // string (poate fi "")
                m.groups[3] != null -> m.groupValues[3].toBoolean() // true / false
                m.groups[4] != null -> {
                    val n = m.groupValues[4]
                    if ('.' in n) n.toDouble() else n.toLong()
                }
                else -> ""
            }
        }
        return result
    }
}

// ─── XmlParser ────────────────────────────────────────────────────────────────

/**
 * Parsează XML simplu și returnează copiii direcți ai elementului rădăcină
 * ca Map<tagName, textContent>.
 *
 * Exemplu: <root><title>Test</title><count>3</count></root>
 *          -> {"title": "Test", "count": "3"}
 */
class XmlParser : ContentParser {

    override fun parse(content: String): Map<String, Any> {
        val result = mutableMapOf<String, Any>()
        val doc = Jsoup.parse(content, "", Parser.xmlParser())
        // Primul child al documentului jsoup = elementul rădăcină
        val root = doc.children().firstOrNull() ?: return result
        for (child in root.children()) {
            result[child.tagName()] = child.text()
        }
        return result
    }
}

// ─── YamlParser ───────────────────────────────────────────────────────────────

/**
 * Parsează YAML simplu în format "cheie: valoare" (un entry per linie).
 * Ignoră liniile care încep cu '#' (comentarii) și liniile goale.
 * Suportă valori: string, long, double, boolean.
 * Valori string pot fi opțional între ghilimele simple sau duble.
 */
class YamlParser : ContentParser {

    override fun parse(content: String): Map<String, Any> {
        val result = mutableMapOf<String, Any>()
        content.lines().forEach { line ->
            val trimmed = line.trim()
            if (trimmed.isEmpty() || trimmed.startsWith('#')) return@forEach
            val colonIdx = trimmed.indexOf(':')
            if (colonIdx <= 0) return@forEach

            val key = trimmed.substring(0, colonIdx).trim()
            val raw = trimmed.substring(colonIdx + 1).trim()

            result[key] = when {
                raw.equals("true", ignoreCase = true)  -> true
                raw.equals("false", ignoreCase = true) -> false
                raw.toLongOrNull() != null             -> raw.toLong()
                raw.toDoubleOrNull() != null           -> raw.toDouble()
                else -> raw.removePrefix("\"").removeSuffix("\"")
                           .removePrefix("'").removeSuffix("'")
            }
        }
        return result
    }
}

// ─── Crawler cu injecție de dependențe (DIP) ──────────────────────────────────

/**
 * Crawler care descarcă conținut de la un URL și îl parsează
 * cu parserul injectat prin constructor — nu știe și nu-i pasă
 * dacă parserul e JSON, XML, YAML sau altceva (Open/Closed + DIP).
 */
class Crawler(private val parser: ContentParser) {

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()

    fun fetch(url: String): Map<String, Any> {
        val request = Request.Builder().url(url).build()
        val content = client.newCall(request).execute().use { response ->
            response.body?.string() ?: ""
        }
        return parser.parse(content)
    }
}
