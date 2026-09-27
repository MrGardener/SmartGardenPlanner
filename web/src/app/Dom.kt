package sgp.web

import kotlinx.browser.document
import org.w3c.dom.Element
import org.w3c.dom.HTMLElement
import org.w3c.dom.HTMLInputElement
import org.w3c.dom.HTMLSelectElement
import org.w3c.dom.Node
import org.w3c.dom.events.Event

const val SVGNS = "http://www.w3.org/2000/svg"

/** Creates an HTML element with optional class, text, attributes and children. */
fun h(tag: String, cls: String? = null, text: String? = null, attrs: Map<String, String> = emptyMap(), kids: List<Node> = emptyList()): HTMLElement {
    val e = document.createElement(tag) as HTMLElement
    if (cls != null) e.className = cls
    if (text != null) e.textContent = text
    attrs.forEach { (k, v) -> e.setAttribute(k, v) }
    kids.forEach { e.appendChild(it) }
    return e
}

/** Creates an SVG element; numbers are written with up to 4 decimals. */
fun s(tag: String, vararg attrs: Pair<String, Any?>): Element {
    val e = document.createElementNS(SVGNS, tag)
    for ((k, v) in attrs) if (v != null) e.setAttribute(k, if (v is Double) num(v) else if (v is Float) num(v.toDouble()) else v.toString())
    return e
}

fun num(v: Double): String {
    val r = kotlin.math.round(v * 10000.0) / 10000.0
    return if (r == kotlin.math.floor(r)) r.toLong().toString() else r.toString()
}

fun <T : Element> T.on(event: String, handler: (Event) -> Unit): T { addEventListener(event, handler); return this }
fun Element.add(vararg kids: Node): Element { kids.forEach { appendChild(it) }; return this }
/** Removes all children. Removing a focused input can fire blur/change handlers that clear the same element again,
 *  so each child is removed only if it is still attached here. */
fun Element.clear() {
    releaseFocusIn(this)
    while (true) { val c = firstChild ?: break; if (c.parentNode === this) removeChild(c) }
}

/** Blurs the focused element if it is inside [e], so its blur/change handlers run before [e] is emptied or removed. */
fun releaseFocusIn(e: Element) {
    val active = document.activeElement as? HTMLElement ?: return
    if (active !== document.body && e.contains(active)) active.blur()
}
fun byId(id: String): HTMLElement = document.getElementById(id) as HTMLElement

fun button(label: String, cls: String = "btn", title: String? = null, onClick: () -> Unit): HTMLElement =
    h("button", cls, label, if (title != null) mapOf("title" to title, "type" to "button") else mapOf("type" to "button")).on("click") { onClick() }

fun input(value: String = "", type: String = "text", placeholder: String = "", cls: String = "inp"): HTMLInputElement {
    val e = h("input", cls, attrs = mapOf("type" to type, "placeholder" to placeholder)) as HTMLInputElement
    e.value = value
    return e
}

fun select(options: List<Pair<String, String>>, selected: String?, onChange: (String) -> Unit): HTMLSelectElement {
    val e = h("select", "inp") as HTMLSelectElement
    options.forEach { (value, label) ->
        val o = h("option", text = label, attrs = mapOf("value" to value))
        if (value == selected) o.setAttribute("selected", "selected")
        e.appendChild(o)
    }
    e.on("change") { onChange(e.value) }
    return e
}

fun label(text: String, field: Node): HTMLElement = h("label", "field", kids = listOf(h("span", "lbl", text), field))
fun para(text: String, cls: String = "p"): HTMLElement = h("p", cls, text)
fun heading(text: String): HTMLElement = h("h3", "h", text)
