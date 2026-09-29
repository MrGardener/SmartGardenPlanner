package com.example.smartgardenplanner.core

/** How hot a pepper is. */
enum class Heat(val label: String) {
    SWEET("Sweet (not spicy)"), MILD("Mild"), MEDIUM("Medium hot"), HOT("Hot"), VERY_HOT("Very hot"), EXTREME("Extremely hot")
}

/** Ripe fruit (or bulb) colour, with a colour that reads on the light layout. */
enum class FruitColour(val label: String, val argb: Long) {
    RED("red", 0xFFDC2626), YELLOW("yellow", 0xFFEAB308), ORANGE("orange", 0xFFF97316), GREEN("green", 0xFF16A34A),
    PURPLE("purple", 0xFF7E22CE), BROWN("brown", 0xFF78350F), PINK("pink", 0xFFDB2777), DARK("dark purple-black", 0xFF4C1D95),
    WHITE("white", 0xFF9CA3AF), STRIPED("striped / bi-colour", 0xFFF59E0B)
}

/**
 * What a variety is in everyday terms (FR-031): sweet or spicy pepper and its colour, cherry or large tomato,
 * spring or bulb onion. [kind] is a short plain name ("Sweet bell pepper", "Cherry tomato"), [tag] a few letters for
 * the layout ("Bell red"), [details] one line for lists.
 */
data class VarietyTraits(
    val kind: String,
    val tag: String,
    val heat: Heat? = null,
    val colour: FruitColour? = null,
    val sizeNote: String? = null
) {
    val details: String
        get() = listOfNotNull(kind, heat?.takeIf { it != Heat.SWEET }?.label?.lowercase(), colour?.label, sizeNote).joinToString(" · ")
}

object VarietyCatalogTraits {

    private enum class PepperShape(val label: String, val tag: String) {
        BELL("Sweet bell pepper", "Bell"), HORN("Sweet horn / frying pepper", "Sweet"), BANANA("Banana pepper", "Banana"),
        MILD_CHILE("Mild chile", "Mild"), CHILE("Chile pepper", "Chile"), HOT_CHILE("Hot chile", "Hot"),
        SUPERHOT("Super-hot chile", "Superhot")
    }

    private data class P(val shape: PepperShape, val heat: Heat, val colour: FruitColour)

    private val PEPPERS: Map<String, P> = mapOf(
        "california wonder" to P(PepperShape.BELL, Heat.SWEET, FruitColour.RED),
        "big bertha" to P(PepperShape.BELL, Heat.SWEET, FruitColour.RED),
        "king of the north" to P(PepperShape.BELL, Heat.SWEET, FruitColour.RED),
        "golden california wonder" to P(PepperShape.BELL, Heat.SWEET, FruitColour.YELLOW),
        "purple beauty" to P(PepperShape.BELL, Heat.SWEET, FruitColour.PURPLE),
        "carmen" to P(PepperShape.HORN, Heat.SWEET, FruitColour.RED),
        "corno di toro" to P(PepperShape.HORN, Heat.SWEET, FruitColour.RED),
        "jimmy nardello" to P(PepperShape.HORN, Heat.SWEET, FruitColour.RED),
        "marconi" to P(PepperShape.HORN, Heat.SWEET, FruitColour.RED),
        "cubanelle" to P(PepperShape.HORN, Heat.SWEET, FruitColour.GREEN),
        "cubanelle sweet" to P(PepperShape.HORN, Heat.SWEET, FruitColour.GREEN),
        "lipstick" to P(PepperShape.HORN, Heat.SWEET, FruitColour.RED),
        "pimento" to P(PepperShape.BELL, Heat.SWEET, FruitColour.RED),
        "chervena chushka" to P(PepperShape.HORN, Heat.SWEET, FruitColour.RED),
        "banana pepper" to P(PepperShape.BANANA, Heat.SWEET, FruitColour.YELLOW),
        "anaheim" to P(PepperShape.MILD_CHILE, Heat.MILD, FruitColour.GREEN),
        "poblano" to P(PepperShape.MILD_CHILE, Heat.MILD, FruitColour.GREEN),
        "poblano ancho" to P(PepperShape.MILD_CHILE, Heat.MILD, FruitColour.GREEN),
        "padron" to P(PepperShape.MILD_CHILE, Heat.MILD, FruitColour.GREEN),
        "shishito" to P(PepperShape.MILD_CHILE, Heat.MILD, FruitColour.GREEN),
        "shishito gold" to P(PepperShape.MILD_CHILE, Heat.MILD, FruitColour.YELLOW),
        "hungarian wax" to P(PepperShape.CHILE, Heat.MEDIUM, FruitColour.YELLOW),
        "jalapeno" to P(PepperShape.CHILE, Heat.MEDIUM, FruitColour.GREEN),
        "fresno" to P(PepperShape.CHILE, Heat.MEDIUM, FruitColour.RED),
        "bishop's crown" to P(PepperShape.CHILE, Heat.MEDIUM, FruitColour.RED),
        "pretty in purple" to P(PepperShape.CHILE, Heat.MEDIUM, FruitColour.PURPLE),
        "numex twilight" to P(PepperShape.HOT_CHILE, Heat.HOT, FruitColour.PURPLE),
        "aji amarillo" to P(PepperShape.HOT_CHILE, Heat.HOT, FruitColour.ORANGE),
        "aji charapita" to P(PepperShape.HOT_CHILE, Heat.HOT, FruitColour.YELLOW),
        "buena mulata" to P(PepperShape.HOT_CHILE, Heat.HOT, FruitColour.RED),
        "cayenne" to P(PepperShape.HOT_CHILE, Heat.HOT, FruitColour.RED),
        "fish pepper" to P(PepperShape.HOT_CHILE, Heat.HOT, FruitColour.RED),
        "peter pepper" to P(PepperShape.HOT_CHILE, Heat.HOT, FruitColour.RED),
        "serrano" to P(PepperShape.HOT_CHILE, Heat.HOT, FruitColour.GREEN),
        "thai chili" to P(PepperShape.HOT_CHILE, Heat.HOT, FruitColour.RED),
        "datil" to P(PepperShape.HOT_CHILE, Heat.VERY_HOT, FruitColour.ORANGE),
        "fatalii" to P(PepperShape.HOT_CHILE, Heat.VERY_HOT, FruitColour.YELLOW),
        "habanero" to P(PepperShape.HOT_CHILE, Heat.VERY_HOT, FruitColour.ORANGE),
        "chocolate habanero" to P(PepperShape.HOT_CHILE, Heat.VERY_HOT, FruitColour.BROWN),
        "scotch bonnet" to P(PepperShape.HOT_CHILE, Heat.VERY_HOT, FruitColour.RED),
        "7 pot douglah" to P(PepperShape.SUPERHOT, Heat.EXTREME, FruitColour.BROWN),
        "bhut jolokia" to P(PepperShape.SUPERHOT, Heat.EXTREME, FruitColour.RED),
        "ghost pepper" to P(PepperShape.SUPERHOT, Heat.EXTREME, FruitColour.RED),
        "carolina reaper" to P(PepperShape.SUPERHOT, Heat.EXTREME, FruitColour.RED),
        "trinidad moruga scorpion" to P(PepperShape.SUPERHOT, Heat.EXTREME, FruitColour.RED),
        "trinidad scorpion" to P(PepperShape.SUPERHOT, Heat.EXTREME, FruitColour.RED)
    )

    private enum class TomatoSize(val label: String, val tag: String, val note: String) {
        CHERRY("Cherry tomato", "Cherry", "small, bite-size"), MEDIUM("Salad tomato", "Salad", "medium"),
        LARGE("Slicing tomato", "Slicer", "large"), BEEFSTEAK("Beefsteak tomato", "Beef", "very large"),
        PASTE("Paste / sauce tomato", "Paste", "plum-shaped, for sauce")
    }

    private data class T(val size: TomatoSize, val colour: FruitColour)

    private val TOMATOES: Map<String, T> = mapOf(
        "sweet 100" to T(TomatoSize.CHERRY, FruitColour.RED), "sun gold" to T(TomatoSize.CHERRY, FruitColour.ORANGE),
        "black cherry" to T(TomatoSize.CHERRY, FruitColour.DARK), "chocolate cherry" to T(TomatoSize.CHERRY, FruitColour.BROWN),
        "green grape" to T(TomatoSize.CHERRY, FruitColour.GREEN), "yellow pear" to T(TomatoSize.CHERRY, FruitColour.YELLOW),
        "juliet" to T(TomatoSize.CHERRY, FruitColour.RED),
        "amish paste" to T(TomatoSize.PASTE, FruitColour.RED), "amish paste roma" to T(TomatoSize.PASTE, FruitColour.RED),
        "roma" to T(TomatoSize.PASTE, FruitColour.RED), "san marzano" to T(TomatoSize.PASTE, FruitColour.RED),
        "opalka" to T(TomatoSize.PASTE, FruitColour.RED), "federle" to T(TomatoSize.PASTE, FruitColour.RED),
        "speckled roman" to T(TomatoSize.PASTE, FruitColour.STRIPED),
        "beefsteak" to T(TomatoSize.BEEFSTEAK, FruitColour.RED), "brandywine" to T(TomatoSize.BEEFSTEAK, FruitColour.PINK),
        "yellow brandywine" to T(TomatoSize.BEEFSTEAK, FruitColour.YELLOW), "cherokee purple" to T(TomatoSize.BEEFSTEAK, FruitColour.PURPLE),
        "purple cherokee" to T(TomatoSize.BEEFSTEAK, FruitColour.PURPLE), "mortgage lifter" to T(TomatoSize.BEEFSTEAK, FruitColour.PINK),
        "radiator charlie's mortgage lifter" to T(TomatoSize.BEEFSTEAK, FruitColour.PINK),
        "aunt ruby's german green" to T(TomatoSize.BEEFSTEAK, FruitColour.GREEN), "cuostralee" to T(TomatoSize.BEEFSTEAK, FruitColour.RED),
        "dr. wyche's yellow" to T(TomatoSize.BEEFSTEAK, FruitColour.YELLOW), "gold medal" to T(TomatoSize.BEEFSTEAK, FruitColour.STRIPED),
        "hillbilly" to T(TomatoSize.BEEFSTEAK, FruitColour.STRIPED), "kellogg's breakfast" to T(TomatoSize.BEEFSTEAK, FruitColour.ORANGE),
        "old german" to T(TomatoSize.BEEFSTEAK, FruitColour.STRIPED), "pineapple" to T(TomatoSize.BEEFSTEAK, FruitColour.STRIPED),
        "striped german" to T(TomatoSize.BEEFSTEAK, FruitColour.STRIPED),
        "anna russian" to T(TomatoSize.LARGE, FruitColour.PINK), "big boy" to T(TomatoSize.LARGE, FruitColour.RED),
        "better boy" to T(TomatoSize.LARGE, FruitColour.RED), "black krim" to T(TomatoSize.LARGE, FruitColour.DARK),
        "box car willie" to T(TomatoSize.LARGE, FruitColour.RED), "chocolate stripes" to T(TomatoSize.LARGE, FruitColour.STRIPED),
        "costoluto genovese" to T(TomatoSize.LARGE, FruitColour.RED), "mr. stripey" to T(TomatoSize.LARGE, FruitColour.STRIPED),
        "paul robeson" to T(TomatoSize.LARGE, FruitColour.DARK), "persimmon" to T(TomatoSize.LARGE, FruitColour.ORANGE),
        "arkansas traveler" to T(TomatoSize.MEDIUM, FruitColour.PINK), "azoychka" to T(TomatoSize.MEDIUM, FruitColour.YELLOW),
        "berkeley tie dye" to T(TomatoSize.MEDIUM, FruitColour.STRIPED), "pink berkeley tie dye" to T(TomatoSize.MEDIUM, FruitColour.PINK),
        "black prince" to T(TomatoSize.MEDIUM, FruitColour.DARK), "blue beauty" to T(TomatoSize.MEDIUM, FruitColour.PURPLE),
        "celebrity" to T(TomatoSize.MEDIUM, FruitColour.RED), "djena lee's golden girl" to T(TomatoSize.MEDIUM, FruitColour.ORANGE),
        "early girl" to T(TomatoSize.MEDIUM, FruitColour.RED), "green zebra" to T(TomatoSize.MEDIUM, FruitColour.GREEN),
        "homestead" to T(TomatoSize.MEDIUM, FruitColour.RED), "japanese black trifele" to T(TomatoSize.MEDIUM, FruitColour.DARK),
        "malachite box" to T(TomatoSize.MEDIUM, FruitColour.GREEN), "marglobe" to T(TomatoSize.MEDIUM, FruitColour.RED),
        "moskvich" to T(TomatoSize.MEDIUM, FruitColour.RED), "nebraska wedding" to T(TomatoSize.MEDIUM, FruitColour.ORANGE),
        "rutgers" to T(TomatoSize.MEDIUM, FruitColour.RED), "silvery fir tree" to T(TomatoSize.MEDIUM, FruitColour.RED),
        "stupice" to T(TomatoSize.MEDIUM, FruitColour.RED), "white wonder" to T(TomatoSize.MEDIUM, FruitColour.WHITE)
    )

    private val ONION_COLOURS = mapOf(
        "yellow sweet spanish" to FruitColour.YELLOW, "red burgundy" to FruitColour.RED, "walla walla" to FruitColour.YELLOW,
        "white sweet spanish" to FruitColour.WHITE, "texas early grano" to FruitColour.YELLOW, "candy" to FruitColour.YELLOW,
        "patterson" to FruitColour.YELLOW, "cippolini" to FruitColour.YELLOW, "early season" to FruitColour.YELLOW
    )

    private fun cultivar(seed: SeedEntity) = seed.commonName.substringAfter(" - ", "").trim().lowercase()

    /** Traits for a variety, or null when it needs no extra explanation. */
    fun of(seed: SeedEntity): VarietyTraits? {
        val species = CropReference.speciesKey(seed)
        val name = cultivar(seed)
        return when (species) {
            "pepper" -> PEPPERS[name]?.let { p ->
                VarietyTraits(if (p.heat == Heat.SWEET) p.shape.label else "${p.shape.label} (spicy)", "${p.shape.tag} ${p.colour.label.substringBefore(' ')}", p.heat, p.colour)
            } ?: pepperFromNotes(seed)
            "shishito type pepper" -> VarietyTraits("Mild frying pepper", "Mild", Heat.MILD, FruitColour.GREEN)
            "tomato" -> (TOMATOES[name] ?: tomatoFromName(name))?.let { t ->
                VarietyTraits(t.size.label, "${t.size.tag} ${t.colour.label.substringBefore(' ')}", colour = t.colour, sizeNote = t.size.note)
            }
            "paste tomato" -> VarietyTraits(TomatoSize.PASTE.label, "Paste", colour = FruitColour.RED, sizeNote = TomatoSize.PASTE.note)
            "onion" -> { val c = ONION_COLOURS[name] ?: FruitColour.YELLOW; VarietyTraits("Bulb onion", "Bulb ${c.label}", colour = c, sizeNote = "dug up as a round bulb") }
            "spring onion" -> VarietyTraits("Spring onion (green onion, scallion)", "Spring", colour = if ("red" in name) FruitColour.RED else FruitColour.GREEN, sizeNote = "pulled young for the green stems")
            "egyptian walking onion" -> VarietyTraits("Walking onion (perennial)", "Walking", colour = FruitColour.PURPLE, sizeNote = "grows small bulbs on its stems")
            else -> null
        }
    }

    private fun pepperFromNotes(seed: SeedEntity): VarietyTraits? {
        val n = (seed.commonName + " " + seed.careNotes).lowercase()
        return when {
            "bell" in n -> VarietyTraits(PepperShape.BELL.label, "Bell", Heat.SWEET)
            "sweet" in n -> VarietyTraits("Sweet pepper", "Sweet", Heat.SWEET)
            "hot" in n || "chil" in n -> VarietyTraits("Chile pepper (spicy)", "Hot", Heat.HOT)
            else -> null
        }
    }

    private fun tomatoFromName(name: String): T? = when {
        listOf("cherry", "grape", "currant", "pear").any { it in name } -> T(TomatoSize.CHERRY, FruitColour.RED)
        listOf("paste", "roma", "plum", "marzano").any { it in name } -> T(TomatoSize.PASTE, FruitColour.RED)
        "beefsteak" in name -> T(TomatoSize.BEEFSTEAK, FruitColour.RED)
        else -> null
    }

    /** "Pepper - California Wonder (sweet bell pepper, red)"; unchanged when there are no traits. */
    fun displayName(seed: SeedEntity): String = of(seed)?.let { "${seed.commonName} (${it.details.lowercase()})" } ?: seed.commonName

    /** Colour for the centre dot on the layout: the ripe fruit colour when known. */
    fun dotArgb(seed: SeedEntity): Long? = of(seed)?.colour?.argb
}
