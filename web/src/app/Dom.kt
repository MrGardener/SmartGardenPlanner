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
    // Text boxes hold at most what a plan file keeps (names and addresses: 200 characters).
    if (type == "text") e.maxLength = 200
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

/**
 * Lets [box] (absolutely positioned inside its parent) be dragged by [handle], kept inside the parent. [onDrop] gets the
 * new left/top in pixels; [pos] (if any) is applied now. Clicks on buttons in the handle still work.
 */
fun draggableBy(box: HTMLElement, handle: HTMLElement, onDrop: (Pair<Int, Int>) -> Unit, pos: Pair<Int, Int>?) {
    val parent = box.parentElement as? HTMLElement
    if (pos != null && parent != null) {
        box.style.left = "${pos.first.coerceIn(0, (parent.clientWidth - 120).coerceAtLeast(0))}px"
        box.style.top = "${pos.second.coerceIn(0, (parent.clientHeight - 40).coerceAtLeast(0))}px"
        box.style.right = "auto"; box.style.bottom = "auto"
    }
    var start: DoubleArray? = null
    handle.style.cursor = "move"
    handle.on("pointerdown") { e ->
        val me = e as org.w3c.dom.events.MouseEvent
        if ((me.target as? Element)?.closest("button") != null) return@on
        start = doubleArrayOf(me.clientX.toDouble(), me.clientY.toDouble(), box.offsetLeft.toDouble(), box.offsetTop.toDouble())
        handle.asDynamic().setPointerCapture(me.asDynamic().pointerId)
        me.preventDefault()
    }
    handle.on("pointermove") { e ->
        val s0 = start ?: return@on
        val me = e as org.w3c.dom.events.MouseEvent
        val p = box.parentElement as? HTMLElement ?: return@on
        val x = (s0[2] + me.clientX - s0[0]).coerceIn(0.0, (p.clientWidth - 120).toDouble().coerceAtLeast(0.0))
        val y = (s0[3] + me.clientY - s0[1]).coerceIn(0.0, (p.clientHeight - 40).toDouble().coerceAtLeast(0.0))
        box.style.left = "${x.toInt()}px"; box.style.top = "${y.toInt()}px"; box.style.right = "auto"; box.style.bottom = "auto"
    }
    handle.on("pointerup") { if (start != null) { start = null; onDrop(box.offsetLeft to box.offsetTop) } }
}
