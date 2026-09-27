package com.example.smartgardenplanner.core

import kotlin.math.max
import kotlin.math.roundToInt

enum class CarePreference { ORGANIC, CONVENTIONAL }

/** One fertilizing step (FR-017). */
data class FertilizeEvent(val dueEpochMillis: Long, val species: List<String>, val action: String)

/** Pest-management advice for one pest or disease (FR-018). */
data class PestAdvice(
    val pest: String,
    val affects: List<String>,
    val prevention: String,
    val control: String,
    val scouting: String
)

enum class CareTaskType { WATER, FERTILIZE }

/** A care reminder (FR-019). [rainSkip] is true when recent or forecast rain covers watering. */
data class CareTask(
    val type: CareTaskType,
    val title: String,
    val detail: String,
    val dueEpochMillis: Long,
    val isDue: Boolean,
    val rainSkip: Boolean
)

/** Fertilizing plan, pest-management plan and care reminders. Pure Kotlin. */
object CarePlanner {

    private const val DAY = 86_400_000L

    // ---------------------------------------------------------------- FR-017 fertilizing plan

    fun fertilizingPlan(context: PlotContext, preference: CarePreference): List<FertilizeEvent> {
        val planted = context.nodes.mapNotNull { n -> context.seedLookup(n.seedCode)?.let { n to it } }
        if (planted.isEmpty()) return emptyList()
        val soil = context.soil
        val events = mutableListOf<FertilizeEvent>()
        val balanced = if (preference == CarePreference.ORGANIC) "an organic balanced fertilizer (e.g. 4-4-4, or composted manure)" else "a balanced granular fertilizer (e.g. 10-10-10) at label rate"
        val sideDress = if (preference == CarePreference.ORGANIC) "side-dress with compost or an organic fertilizer (e.g. fish emulsion, feather meal)" else "side-dress with a balanced or nitrogen fertilizer at label rate, watered in"
        val fruiting = if (preference == CarePreference.ORGANIC) "switch to a lower-nitrogen, higher-potassium feed (e.g. seaweed or a tomato feed) once fruit sets" else "switch to a high-potassium feed (e.g. 5-10-15) once fruit sets"

        val earliestPlanting = planted.minOf { it.first.datePlantedEpochMillis }
        val prep = mutableListOf<String>()
        prep += if ((soil.organicPct ?: 0f) >= 5f) "Top up with a thin layer of compost" else "Work 2–3 cm of compost into the beds"
        soil.ph?.let { ph ->
            if (ph < 6.0f) prep += "apply garden lime (soil pH ${(ph).fmt(1)})"
            if (ph > 7.5f) prep += "work in elemental sulfur for acid-loving crops (soil pH ${(ph).fmt(1)})"
        }
        events += FertilizeEvent(earliestPlanting, listOf("All beds"), prep.joinToString("; ") + ".")

        val bySpecies = planted.groupBy { CropReference.speciesName(it.second) }
        for ((name, list) in bySpecies) {
            val seed = list.first().second
            val crop = CropReference.forSeed(seed)
            val planting = list.minOf { it.first.datePlantedEpochMillis }
            val harvest = planting + seed.daysToHarvest * DAY
            if (seed.lifecycle == "PERENNIAL") {
                events += FertilizeEvent(nextSpring(planting), listOf(name), "Each early spring: spread compost around the base and apply $balanced. Keep it away from the stem.")
                continue
            }
            when (crop.feeding) {
                FeedingClass.HEAVY -> {
                    events += FertilizeEvent(planting, listOf(name), "At planting: mix $balanced into the planting hole or row.")
                    var t = planting + 21 * DAY
                    while (t < harvest - 7 * DAY) {
                        events += FertilizeEvent(t, listOf(name), "Heavy feeder: $sideDress.")
                        t += 21 * DAY
                    }
                    if (name.lowercase() in setOf("tomato", "paste tomato", "pepper", "eggplant", "melon", "watermelon", "cucumber", "zucchini", "winter squash", "pumpkin")) {
                        events += FertilizeEvent(planting + seed.daysToHarvest * DAY / 2, listOf(name), "Fruiting crop: $fruiting.")
                    }
                }
                FeedingClass.MEDIUM -> {
                    events += FertilizeEvent(planting, listOf(name), "At planting: a light application of $balanced.")
                    events += FertilizeEvent(planting + seed.daysToHarvest * DAY / 2, listOf(name), "Mid-season: $sideDress.")
                }
                FeedingClass.LIGHT -> {
                    events += FertilizeEvent(planting + seed.daysToHarvest * DAY / 2, listOf(name), "Light feeder: compost is usually enough. Feed once only if leaves turn pale.")
                }
                FeedingClass.NITROGEN_FIXER -> {
                    events += FertilizeEvent(planting, listOf(name), "Nitrogen fixer: no nitrogen needed. Coat seed with rhizobium inoculant if this bed hasn't grown beans or peas before.")
                }
            }
        }
        // Merge identical actions on the same day.
        return events.groupBy { it.dueEpochMillis / DAY to it.action }
            .map { (_, group) -> FertilizeEvent(group.first().dueEpochMillis, group.flatMap { it.species }.distinct(), group.first().action) }
            .sortedBy { it.dueEpochMillis }
    }

    private fun nextSpring(from: Long): Long {
        val offset = PlatformClock.localOffsetMillis(from)
        val year = CivilDate.year(from, offset)
        val thisYear = CivilDate.localMidnight(year, 3, 15, offset)
        return if (thisYear < from) CivilDate.localMidnight(year + 1, 3, 15, offset) else thisYear
    }

    // ---------------------------------------------------------------- FR-018 pest-management plan

    private class PestEntry(val keys: List<String>, val name: String, val prevention: String, val organic: String, val conventional: String, val scouting: String)

    private val PESTS = listOf(
        PestEntry(listOf("aphid"), "Aphids", "Encourage ladybirds and lacewings (plant dill, yarrow, alyssum); avoid excess nitrogen.", "Blast off with water; insecticidal soap or neem oil on the undersides of leaves.", "Insecticidal soap first; if severe, a labelled insecticide such as acetamiprid, following the pre-harvest interval.", "Check new growth and leaf undersides weekly from spring."),
        PestEntry(listOf("powdery mildew"), "Powdery mildew", "Space plants for airflow, water the soil not the leaves, choose resistant varieties.", "Remove affected leaves; spray potassium bicarbonate or a milk solution (1:9) weekly.", "A labelled fungicide (e.g. myclobutanil or sulfur) at first sign.", "Look for white powder on upper leaves in warm, dry weather."),
        PestEntry(listOf("downy mildew"), "Downy mildew", "Water early in the day at the soil; improve airflow.", "Remove infected leaves; copper fungicide approved for organic use.", "A labelled fungicide (e.g. chlorothalonil) as directed.", "Yellow patches on top of leaves with grey fuzz beneath, in cool wet weather."),
        PestEntry(listOf("cabbage worm", "cabbage looper"), "Cabbage worms", "Cover brassicas with fine insect netting from planting.", "Hand-pick; spray Bacillus thuringiensis (Bt) on caterpillars.", "Bt or spinosad; a labelled pyrethroid if heavy.", "Look for holes and green droppings on leaves weekly."),
        PestEntry(listOf("flea beetle"), "Flea beetles", "Row cover on seedlings; sow a trap crop of radish.", "Sticky traps; kaolin clay or neem on seedlings.", "A labelled insecticide (e.g. spinosad or a pyrethroid) on seedlings only if damage is severe.", "Tiny shot-holes in young leaves, especially in spring."),
        PestEntry(listOf("slug", "snail"), "Slugs and snails", "Water in the morning, keep beds free of debris, use copper tape on raised beds.", "Beer traps, hand-picking at dusk, iron phosphate pellets.", "Iron phosphate or metaldehyde-free pellets (keep pets away).", "Slime trails and ragged holes after damp nights."),
        PestEntry(listOf("hornworm", "tomato hornworm"), "Hornworms", "Till in autumn to destroy pupae; plant dill to draw parasitic wasps.", "Hand-pick; Bt on small larvae. Leave any carrying white wasp cocoons.", "Bt, spinosad or carbaryl as labelled.", "Stripped leaves and large dark droppings; check at dusk with a UV torch."),
        PestEntry(listOf("japanese beetle"), "Japanese beetles", "Treat lawns for grubs; avoid traps near the garden (they attract more).", "Hand-pick into soapy water in the morning; neem.", "A labelled insecticide such as carbaryl or a pyrethroid.", "Skeletonised leaves in early summer."),
        PestEntry(listOf("whitefl"), "Whiteflies", "Yellow sticky traps; avoid overcrowding.", "Insecticidal soap, neem oil; vacuum adults off.", "Insecticidal soap; a labelled systemic only on non-flowering ornamentals.", "Clouds of tiny white insects when leaves are disturbed."),
        PestEntry(listOf("leaf miner", "leafminer"), "Leaf miners", "Row cover; remove and destroy mined leaves.", "Remove affected leaves; spinosad.", "Spinosad or a labelled insecticide.", "Winding tunnels inside leaves."),
        PestEntry(listOf("bird"), "Birds", "Netting over fruit and seedlings; reflective tape.", "Netting, scare devices.", "Netting (no chemical control).", "Pecked fruit, pulled seedlings."),
        PestEntry(listOf("codling moth"), "Codling moth", "Pheromone traps from blossom time; pick up fallen fruit.", "Bag fruit; spray granulosis virus (CpGV) or kaolin clay.", "A labelled insecticide timed to egg hatch (use trap counts).", "Traps from petal fall; check fruit for entry holes."),
        PestEntry(listOf("bean beetle"), "Bean beetles", "Rotate beans each year; clean up plant debris.", "Hand-pick adults and yellow larvae; neem.", "Spinosad or a labelled pyrethroid.", "Lacy, skeletonised leaves in midsummer."),
        PestEntry(listOf("spider mite"), "Spider mites", "Keep plants watered; mist in dry spells; spare predatory insects.", "Strong water spray; insecticidal soap; release predatory mites.", "A labelled miticide (e.g. abamectin) as a last resort.", "Stippled yellow leaves and fine webbing in hot, dry weather."),
        PestEntry(listOf("pepper weevil"), "Pepper weevils", "Remove fallen fruit and nightshade weeds nearby.", "Yellow sticky traps; destroy infested fruit.", "A labelled insecticide from flowering.", "Fallen small fruit with holes."),
        PestEntry(listOf("vine borer"), "Squash vine borer", "Row cover until flowering; wrap stem bases in foil.", "Slit stems and remove larvae, bury the vine node; Bt injected into stems.", "A labelled insecticide at stem bases in early summer.", "Sudden wilting and sawdust-like frass at the base."),
        PestEntry(listOf("cane borer"), "Cane borers", "Prune out wilted cane tips below the damage.", "Prune and destroy affected canes.", "Pruning is the main control; insecticides rarely needed.", "Wilting cane tips in early summer."),
        PestEntry(listOf("borer"), "Borers", "Keep trees and shrubs healthy and watered; avoid bark wounds.", "Remove larvae with wire; beneficial nematodes.", "A labelled trunk spray timed to egg-laying.", "Holes with sawdust in stems or trunks."),
        PestEntry(listOf("fire blight"), "Fire blight", "Choose resistant varieties; avoid heavy nitrogen.", "Prune 30 cm below infections in dry weather, sterilising tools; copper at bloom.", "Streptomycin sprays at bloom where permitted.", "Blackened, 'shepherd's crook' shoot tips in spring."),
        PestEntry(listOf("root maggot", "onion maggot", "cabbage maggot"), "Root maggots", "Row cover at planting; rotate crops; avoid fresh manure.", "Collars around stems; beneficial nematodes.", "A labelled soil insecticide at planting.", "Wilting seedlings with tunnelled roots."),
        PestEntry(listOf("carrot rust fly", "carrot fly"), "Carrot rust fly", "Fine mesh cover; sow after the first generation; interplant onions.", "Row cover; lift crops promptly.", "Row cover is the main control.", "Rusty tunnels in roots."),
        PestEntry(listOf("scale"), "Scale insects", "Encourage natural predators.", "Horticultural oil in dormant season; scrub off.", "Horticultural oil; a labelled systemic on ornamentals only.", "Bumps on stems and sticky honeydew."),
        PestEntry(listOf("apple scab"), "Apple scab", "Resistant varieties; rake up fallen leaves in autumn.", "Sulfur or copper sprays from bud break in wet springs.", "A labelled fungicide program from green tip.", "Olive-brown spots on leaves and fruit."),
        PestEntry(listOf("rust"), "Rust", "Airflow; water at soil level; remove infected leaves.", "Remove leaves; sulfur spray.", "A labelled fungicide at first sign.", "Orange pustules under leaves."),
        PestEntry(listOf("mealybug"), "Mealybugs", "Inspect new plants.", "Dab with alcohol; insecticidal soap.", "Insecticidal soap or a labelled systemic on ornamentals.", "White cottony clusters in leaf joints."),
        PestEntry(listOf("bulb rot", "white rot"), "Bulb and root rots", "Well-drained soil; rotate alliums and bulbs; plant healthy stock.", "Remove infected plants; don't replant the same crop there for 4+ years.", "No reliable chemical cure; rotation is the control.", "Yellowing, collapsing plants with rotten bases."),
        PestEntry(listOf("squash bug"), "Squash bugs", "Row cover; clean up debris; trellis vines.", "Hand-pick eggs and adults; board traps overnight.", "A labelled insecticide on nymphs.", "Bronze egg clusters under leaves."),
        PestEntry(listOf("thrip"), "Thrips", "Blue sticky traps; avoid drought stress.", "Insecticidal soap, spinosad.", "Spinosad or a labelled insecticide.", "Silvery streaks on leaves or petals."),
        PestEntry(listOf("peach leaf curl"), "Peach leaf curl", "Resistant varieties.", "Copper spray in late autumn after leaf fall.", "A labelled fungicide (chlorothalonil or copper) in dormancy.", "Red, puckered leaves in spring."),
        PestEntry(listOf("lily beetle"), "Lily beetle", "Inspect from spring.", "Hand-pick bright red adults and larvae.", "Neem or a labelled insecticide.", "Red beetles and chewed leaves."),
        PestEntry(listOf("currant worm", "sawfl"), "Currant worm (sawfly)", "Check bushes from leaf-out.", "Hand-pick; insecticidal soap.", "Spinosad or a labelled insecticide.", "Leaves stripped from the centre of the bush."),
        PestEntry(listOf("cucumber beetle"), "Cucumber beetles", "Row cover until flowering; trap crops of squash.", "Yellow sticky traps; kaolin clay; hand-pick.", "A labelled insecticide at seedling stage.", "Striped or spotted yellow beetles; wilting (bacterial wilt)."),
        PestEntry(listOf("brown rot"), "Brown rot", "Thin fruit; remove mummified fruit.", "Remove infected fruit; sulfur sprays.", "A labelled fungicide at bloom and pre-harvest.", "Brown rotting fruit with grey spores."),
        PestEntry(listOf("black spot"), "Black spot", "Resistant roses; airflow; water at the base.", "Remove leaves; neem or sulfur.", "A labelled fungicide every 7–14 days in wet weather.", "Black spots with yellow halos on leaves."),
        PestEntry(listOf("earwig"), "Earwigs", "Remove hiding places.", "Rolled newspaper traps.", "Traps are usually enough.", "Ragged holes in petals and leaves."),
        PestEntry(listOf("drosophila"), "Spotted wing drosophila", "Harvest often; remove overripe fruit.", "Fine mesh; vinegar traps.", "A labelled insecticide during ripening.", "Soft, collapsing ripe berries."),
        PestEntry(listOf("deer"), "Deer", "2.4 m fencing; plant deer-resistant borders.", "Fencing, repellents.", "Fencing (no chemical control).", "Torn leaves and browsing."),
        PestEntry(listOf("rodent", "vole", "gopher"), "Rodents", "Wire mesh under beds; tree guards.", "Traps; mesh.", "Traps (avoid poisons near food and pets).", "Gnawed roots and bark."),
        PestEntry(listOf("corn earworm"), "Corn earworm", "Choose tight-husked varieties.", "A drop of mineral oil at silk tips; Bt.", "Spinosad or a labelled insecticide at silking.", "Damaged kernel tips."),
        PestEntry(listOf("colorado potato beetle"), "Colorado potato beetle", "Rotate; straw mulch.", "Hand-pick; spinosad.", "Spinosad or a labelled insecticide.", "Striped beetles and orange eggs under leaves."),
        PestEntry(listOf("stink bug"), "Stink bugs", "Remove weeds; trap crops.", "Hand-pick; kaolin clay.", "A labelled insecticide.", "Cloudy spots on fruit."),
        PestEntry(listOf("botrytis"), "Botrytis (grey mould)", "Airflow; avoid wetting flowers.", "Remove affected parts.", "A labelled fungicide.", "Grey fuzzy mould in damp weather."),
        PestEntry(listOf("verticillium", "wilt"), "Wilt diseases", "Resistant varieties; rotate; don't plant nightshades in the same spot for 3 years.", "Remove infected plants.", "No cure; rotation and resistance.", "Yellowing, wilting on one side of the plant."),
        PestEntry(listOf("sooty mold"), "Sooty mould", "Control the sap-sucking insects that cause it.", "Wash leaves; treat aphids or scale.", "Treat the insects causing it.", "Black film on leaves."),
        PestEntry(listOf("bagworm"), "Bagworms", "Inspect conifers in winter.", "Hand-pick bags; Bt when young.", "Bt or a labelled insecticide.", "Hanging bags on branches.")
    )

    fun pestPlan(context: PlotContext, preference: CarePreference): List<PestAdvice> {
        val species = context.plantedSeeds().distinctBy { CropReference.speciesName(it) }
        val found = linkedMapOf<String, Pair<PestEntry, MutableSet<String>>>()
        for (seed in species) {
            val tokens = seed.pestNotes.lowercase().split(",", ";", ".").map { it.trim() }.filter { it.isNotEmpty() }
            for (token in tokens) {
                if (token.contains("rarely bothered") || token.contains("beneficial") || token.contains("deterrent") || token.contains("trap crop")) continue
                val entry = PESTS.firstOrNull { e -> e.keys.any { token.contains(it) } } ?: continue
                found.getOrPut(entry.name) { entry to mutableSetOf() }.second += CropReference.speciesName(seed)
            }
        }
        return found.values.map { (entry, affects) ->
            PestAdvice(
                pest = entry.name,
                affects = affects.sorted(),
                prevention = entry.prevention,
                control = if (preference == CarePreference.ORGANIC) entry.organic else entry.conventional + " Always follow the label and its pre-harvest interval.",
                scouting = entry.scouting
            )
        }.sortedByDescending { it.affects.size }
    }

    // ---------------------------------------------------------------- FR-019 reminders

    /** Days between waterings for the plot: the thirstiest crop, adjusted for soil drainage. */
    fun wateringIntervalDays(context: PlotContext): Int {
        val seeds = context.plantedSeeds()
        if (seeds.isEmpty()) return 3
        val base = seeds.minOf { CropReference.forSeed(it).waterIntervalDays }
        return max(1, (base * SoilAnalyzer.wateringFactor(context.soil)).roundToInt())
    }

    /**
     * Care tasks for a plot now. [rainMm] is the rain measured in the last 24 h plus forecast for the next
     * 24 h (null when unknown, e.g. online features are off). When it reaches [rainThresholdMm], the
     * watering task is marked as skipped for rain.
     */
    fun dueTasks(
        context: PlotContext,
        careLog: List<CareLogEntity>,
        preference: CarePreference,
        nowEpochMillis: Long,
        rainMm: Double?,
        rainThresholdMm: Double
    ): List<CareTask> {
        if (context.nodes.isEmpty()) return emptyList()
        val tasks = mutableListOf<CareTask>()
        val interval = wateringIntervalDays(context)
        val lastWater = careLog.filter { it.taskType == CareTaskType.WATER.name }.maxOfOrNull { it.doneAtEpochMillis }
            ?: context.nodes.minOf { it.datePlantedEpochMillis }
        val waterDue = lastWater + interval * DAY
        val rainSkip = rainMm != null && rainMm >= rainThresholdMm
        tasks += CareTask(
            type = CareTaskType.WATER,
            title = if (rainSkip) "Skip watering — rain" else "Water ${context.plot.name}",
            detail = if (rainSkip) "About ${rainMm?.fmt(0)} mm of rain in the last day and next day. That covers watering."
            else "Deep watering every $interval day${if (interval == 1) "" else "s"} for these crops and this soil. Water at the base in the morning.",
            dueEpochMillis = waterDue,
            isDue = waterDue <= nowEpochMillis && !rainSkip,
            rainSkip = rainSkip
        )
        val lastFertilize = careLog.filter { it.taskType == CareTaskType.FERTILIZE.name }.maxOfOrNull { it.doneAtEpochMillis } ?: 0L
        val next = fertilizingPlan(context, preference).firstOrNull { it.dueEpochMillis > lastFertilize }
        if (next != null) {
            tasks += CareTask(
                type = CareTaskType.FERTILIZE,
                title = "Fertilize: ${next.species.joinToString(", ")}",
                detail = next.action,
                dueEpochMillis = next.dueEpochMillis,
                isDue = next.dueEpochMillis <= nowEpochMillis,
                rainSkip = false
            )
        }
        return tasks
    }
}
