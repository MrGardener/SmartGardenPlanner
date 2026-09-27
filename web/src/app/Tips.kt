package sgp.web

import org.w3c.dom.Element
import org.w3c.dom.HTMLElement
import org.w3c.dom.asList

/**
 * Hover help for every button (FR-053). Buttons made with a title keep it; any other button gets the text below for
 * its label (exact match first, then the label's start), so no button is left without an explanation.
 */
object Tips {
    private val exact = mapOf(
        "New plot" to "Make a new plot: its size, ZIP code, which way it faces, the pests in your yard and the soil",
        "Open…" to "Open a .sgp.json plan file (from this computer or the phone). Save first: it replaces what's open",
        "Open a plan file…" to "Open a .sgp.json plan file (from this computer or the phone)",
        "Save" to "Save the plan file (Chrome/Edge update the same file; other browsers download it)",
        "Save as…" to "Save the plan under a new file name",
        "?" to "Help: how to use the planner, and the disclaimer",
        "↶" to "Undo the last change (Ctrl+Z)",
        "↷" to "Redo (Ctrl+Y)",
        "＋" to "Zoom in",
        "－" to "Zoom out",
        "+" to "Show",
        "−" to "Hide",
        "✕" to "Remove",
        "Fit" to "Fit the whole plot in the window",
        "Tree" to "Place a tree: click where the trunk is, then give its height and crown size",
        "Fence" to "Draw a fence: click points along it, then Finish (Enter)",
        "Wall" to "Draw a wall: click points along it, then Finish (Enter)",
        "Building" to "Draw a building: click its corners, then Finish (Enter)",
        "Sprinkler" to "Place a sprinkler: click where it stands, then set how far it throws and its pattern. Click again to stop placing",
        "Drip line / soaker hose" to "Draw a drip line or soaker hose: click points along it, then Finish (Enter) or double-click. Click again to stop",
        "Hose tap" to "Place a hose tap: click where it is, then give the hose length. Click again to stop placing",
        "Plant tool" to "Click the layout to plant the chosen variety",
        "Plan an area for me" to "Drag over an area, list what you want, and the planner places it",
        "Fill the whole plot…" to "Plan the whole plot at once from a list, with How many fit?",
        "How many fit?" to "Keep your list's proportions and fill the area with as many as fit",
        "+ Add a plant" to "Add another variety to the list",
        "Edit details…" to "Change the plot's name, size, ZIP, address, direction, pests and soil",
        "Delete plot" to "Delete this plot from the plan (your saved file still has it)",
        "Duplicate…" to "Copy this plot, like duplicating a browser tab, with or without its plants and history",
        "Duplicate plot…" to "Copy this plot, like duplicating a browser tab, with or without its plants and history",
        "Outline (odd shape)" to "Give the plot a custom shape: drag the corners, double-click an edge to add one",
        "Delete outline" to "Remove the outline; the plot becomes the full rectangle (undoable)",
        "Edit" to "Change or delete this item",
        "Edit selected…" to "Change or delete the selected obstacle, area or irrigation item",
        "Edit plant…" to "Change the variety or planting date, or delete the plant",
        "Delete selected" to "Delete what's selected (undoable)",
        "Finish (Enter)" to "Finish the line or shape you are drawing",
        "Cancel (Esc)" to "Stop drawing and drop the points",
        "Keep this plan" to "Plant everything in the proposal (one undo step)",
        "Change selections" to "Go back to your list for the same area",
        "Discard" to "Drop the proposal; your plot and list stay as they were",
        "Plan it" to "Place the plants and show a proposal (nothing is planted yet)",
        "Cancel" to "Close without changing anything",
        "Close" to "Close",
        "OK" to "Close",
        "Save " to "Save the changes",
        "Create plot" to "Create the plot with these details",
        "Add" to "Add it to the plot",
        "Delete" to "Delete it (undoable)",
        "Delete plant" to "Delete this plant (undoable)",
        "Start new season" to "Move this season's plants into the history and start empty",
        "Start a new season (empty)…" to "Move this season's plants into the history and start with an empty plot",
        "Plan next season (rotate)…" to "Re-plan the whole plot for next year with the same crops, rotated",
        "Make the plan" to "Work out the rotation plan for the chosen number of years",
        "◀ Year" to "Previous year of the plan",
        "Year ▶" to "Next year of the plan",
        "Change a variety…" to "Grow a different variety from this year on",
        "Clear all changes" to "Go back to your original list for every year",
        "Change" to "Apply the change and work the plan out again",
        "Duplicate" to "Make the copy",
        "I understand" to "I've read this: the planner is a guide, not a guarantee",
        "Replace…" to "Change all plants of this variety to another variety at once",
        "Add photo…" to "Choose a screenshot or aerial photo of your yard",
        "Open Google Maps (satellite)" to "Open your address in Google Maps in a new tab, to take a satellite screenshot",
        "Open in Google Maps" to "Open this plot's address in Google Maps in a new tab",
        "Set scale…" to "Click two points on the photo whose real distance you know, then enter it",
        "Set scale" to "Rescale the photo so the two points are this far apart",
        "Move photo" to "Drag the photo to line it up with the plot",
        "Hide" to "Hide the photo (it's kept)",
        "Show" to "Show the photo again",
        "Remove photo" to "Delete the photo from this plot (undoable)"
    )
    private val prefixes = listOf(
        "Replace all" to "Change them all to the chosen variety (one undo step)",
        "Water:" to "Show which areas your sprinklers, drip lines and hoses water, and which plants need a watering can",
        "Water map:" to "Show which areas your sprinklers, drip lines and hoses water, and which plants need a watering can",
        "Shade:" to "Show sun and shade over the whole day or at a chosen time",
        "Names:" to "Show what each plant is on the layout",
        "+ " to "Add this",
        "Use " to "Plant this year's plan now: this season's plants move to history (one undo step)",
        "Start next season" to "Start next season with this plan: this season's plants move to history (one undo step)"
    )
    private val compass = mapOf("N" to "north", "NE" to "north-east", "E" to "east", "SE" to "south-east", "S" to "south", "SW" to "south-west", "W" to "west", "NW" to "north-west")

    fun forLabel(label: String): String? {
        val t = label.trim()
        exact[t]?.let { return it }
        compass[t]?.let { return "The plot's top edge faces $it" }
        prefixes.firstOrNull { t.startsWith(it.first) }?.let { return it.second }
        return null
    }

    /** Gives every button under [root] without a title its hover help. */
    fun apply(root: Element) {
        root.querySelectorAll("button:not([title])").asList().forEach { n ->
            val b = n as? HTMLElement ?: return@forEach
            val tip = forLabel(b.textContent ?: "") ?: (b.textContent?.trim()?.takeIf { it.isNotEmpty() })
            if (tip != null) b.title = tip
        }
    }
}
