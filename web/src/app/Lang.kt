package sgp.web

import com.example.smartgardenplanner.core.I18n
import kotlinx.browser.document
import org.w3c.dom.Element
import org.w3c.dom.HTMLElement
import org.w3c.dom.Node
import org.w3c.dom.asList

/**
 * Interface language for the computer planner (FR-062). All texts are written in US English; after each render the
 * page's texts, hover help and placeholders are looked up in the chosen dictionary (embedded from
 * assets/i18n/<code>.txt), and anything without an entry stays in English.
 */
object Lang {
    fun init() {
        use(Prefs.language)
        // For the translation coverage check (tests/i18n_coverage.mjs): translate texts into [code] without changing the page.
        kotlinx.browser.window.asDynamic().sgpTranslate = { code: String, texts: Array<String> ->
            val was = I18n.language
            use(code)
            val out = texts.map { I18n.tr(it) }.toTypedArray()
            use(was)
            out
        }
    }

    fun use(code: String) {
        val dict = if (code == "en") emptyMap() else try { I18n.parse(Embedded.lines("sgp-i18n-$code")) } catch (e: Throwable) { emptyMap() }
        I18n.use(if (dict.isEmpty()) "en" else code, dict)
        Prefs.language = I18n.language
        document.documentElement?.setAttribute("lang", I18n.language)
    }

    fun tr(text: String) = I18n.tr(text)

    /** Translates every text and title/placeholder/aria-label under [root]. */
    fun apply(root: Element) {
        if (I18n.language == "en") return
        walk(root)
        root.querySelectorAll("[title],[placeholder],[aria-label]").asList().forEach { n ->
            val e = n as? Element ?: return@forEach
            for (a in listOf("title", "placeholder", "aria-label")) e.getAttribute(a)?.let { v -> val t = I18n.tr(v); if (t != v) e.setAttribute(a, t) }
        }
    }

    private fun walk(n: Node) {
        if (n.nodeType == Node.TEXT_NODE) {
            val v = n.nodeValue ?: return
            if (v.isNotBlank()) { val t = I18n.tr(v); if (t != v) n.nodeValue = t }
            return
        }
        val tag = (n as? HTMLElement)?.tagName
        if (tag == "SCRIPT" || tag == "STYLE" || tag == "TEXTAREA") return
        if ((n as? Element)?.getAttribute("translate") == "no") return
        var c = n.firstChild
        while (c != null) { walk(c); c = c.nextSibling }
    }
}
