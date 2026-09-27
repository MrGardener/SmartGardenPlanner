package com.example.smartgardenplanner.core

/**
 * Garden pests and wildlife (FR-042). The user says which ones they see in their yard (asked when a plot is created,
 * changeable later); the Care section then shows what each one damages, the signs to look for and how to keep it
 * out, with the plants in the plot that are most at risk. General guidance only (see [Disclaimer]).
 */
enum class PestKind(val label: String) { ANIMAL("Animals"), BIRD("Birds"), INSECT("Insects and other small pests") }

enum class Pest(
    val label: String,
    val kind: PestKind,
    /** Species keys ([CropReference.speciesKey]) this pest is known to go for; empty = most soft crops. */
    val targets: Set<String>,
    val signs: String,
    val tips: List<String>
) {
    DEER(
        "Deer", PestKind.ANIMAL,
        setOf("lettuce", "spinach", "swiss chard", "kale", "cabbage", "broccoli", "cauliflower", "brussels sprouts", "collard greens", "bush bean", "pole bean", "pea", "sweet corn", "strawberry", "tomato", "pepper", "sweet potato", "beet", "apple", "pear", "blueberry", "raspberry", "sunflower"),
        "Leaves and shoots torn off (ragged edges, no clean cut), hoof prints, damage up to about 1.8 m (6 ft) high, often overnight.",
        listOf(
            "A fence at least 2.4 m (8 ft) tall is the only reliable barrier; deer jump lower fences easily.",
            "Two shorter fences about 1.2–1.5 m (4–5 ft) apart also work: deer won't jump into a narrow gap they can't see a landing in.",
            "An electric fence baited with a little peanut butter on foil teaches deer to stay away (check local rules first).",
            "Netting over beds, and tree guards on young fruit trees, protect small areas.",
            "Motion-activated sprinklers and repellent sprays help for a while; rotate them, as deer get used to one method.",
            "Plant strongly scented herbs (lavender, rosemary, sage, mint) around the edge; deer tend to avoid them."
        )
    ),
    RABBIT(
        "Rabbits", PestKind.ANIMAL,
        setOf("lettuce", "spinach", "bush bean", "pole bean", "pea", "carrot", "beet", "swiss chard", "kale", "cabbage", "broccoli", "cauliflower", "parsley", "strawberry", "sunflower", "arugula"),
        "Seedlings and young plants clipped off cleanly at an angle close to the ground; small round droppings.",
        listOf(
            "Chicken wire or hardware cloth about 60–90 cm (2–3 ft) tall with holes no bigger than 2.5 cm (1 in).",
            "Bury the bottom 15 cm (6 in), or bend it outward along the ground, so they can't dig under.",
            "Cover seedlings with row cover or wire cloches until they're bigger.",
            "Raised beds at least 60 cm (2 ft) high keep most rabbits out."
        )
    ),
    RACCOON(
        "Raccoons", PestKind.ANIMAL,
        setOf("sweet corn", "melon", "watermelon", "strawberry", "tomato", "pumpkin", "winter squash", "grape", "blueberry", "raspberry", "blackberry"),
        "Corn stalks pulled down and ears half-eaten just before harvest; melons opened; digging for grubs; mess at night.",
        listOf(
            "Raccoons climb ordinary fences. A two-wire electric fence at about 15 cm and 30 cm (6 and 12 in) high works best around corn and melons.",
            "Pick corn and fruit as soon as they're ripe; raccoons raid the night before you planned to harvest.",
            "Secure compost and bins, and don't leave pet food out.",
            "Plant squash or pumpkins around the corn: the prickly vines put raccoons off."
        )
    ),
    SQUIRREL(
        "Squirrels", PestKind.ANIMAL,
        setOf("tomato", "sweet corn", "strawberry", "sunflower", "pumpkin", "winter squash", "pea", "bush bean", "apple", "pear", "peach", "plum", "cherry", "blueberry", "garlic", "onion"),
        "Bites out of ripening tomatoes and fruit, bulbs dug up, seeds taken, small holes dug in beds.",
        listOf(
            "Cover beds with hardware cloth or wire mesh cages; squirrels climb netting and fences.",
            "Lay chicken wire flat over newly planted seeds and bulbs until they sprout.",
            "Pick tomatoes when they first colour and ripen them indoors.",
            "A water dish nearby can reduce tomato damage in dry weather (they bite for moisture)."
        )
    ),
    GROUNDHOG(
        "Groundhogs (woodchucks)", PestKind.ANIMAL,
        setOf("lettuce", "bush bean", "pole bean", "pea", "broccoli", "cabbage", "kale", "carrot", "melon", "squash", "zucchini", "summer squash mix", "sweet potato", "swiss chard", "sunflower"),
        "Whole plants eaten to the ground in daytime; large burrow entrances (25–30 cm) near sheds, fences or wood piles.",
        listOf(
            "A fence 1–1.2 m (3–4 ft) tall, with the top 30 cm left loose and bent outward so it wobbles when they climb.",
            "Bury the bottom 30 cm (1 ft) and bend it outward in an L so they can't dig under.",
            "A single electric wire about 10–15 cm (4–5 in) above the ground outside the fence stops climbing."
        )
    ),
    GOPHER(
        "Gophers", PestKind.ANIMAL,
        setOf("carrot", "potato", "sweet potato", "beet", "onion", "garlic", "lettuce", "artichoke", "strawberry", "apple", "pear"),
        "Plants wilt or vanish downward; fan-shaped mounds of fresh soil with a plugged hole.",
        listOf(
            "Line raised beds or planting holes with galvanised hardware cloth (1.3 cm / ½ in mesh) before filling with soil.",
            "Wire baskets around the roots of trees, shrubs and perennials.",
            "Keep an eye out for fresh mounds and act early; trapping is the most reliable control."
        )
    ),
    VOLE(
        "Voles and mice", PestKind.ANIMAL,
        setOf("potato", "sweet potato", "carrot", "beet", "lettuce", "strawberry", "apple", "pear", "blueberry"),
        "Runways through grass or mulch, gnawed roots and tubers, bark chewed at the base of young trees.",
        listOf(
            "Pull mulch back 30 cm (1 ft) from tree trunks and keep grass short around the garden.",
            "Hardware cloth guards around young trees; hardware cloth under raised beds.",
            "Remove weedy cover near beds, where they hide."
        )
    ),
    CHIPMUNK(
        "Chipmunks", PestKind.ANIMAL,
        setOf("strawberry", "tomato", "sunflower", "pea", "bush bean", "sweet corn", "garlic", "onion"),
        "Seeds and bulbs dug up, bites in ripening fruit, small holes without mounds.",
        listOf(
            "Wire mesh over newly planted seeds and bulbs, or fine-mesh cages over strawberries.",
            "Keep bird seed off the ground near the garden.",
            "Clear wood piles and rock piles next to beds."
        )
    ),
    SKUNK(
        "Skunks and opossums", PestKind.ANIMAL,
        setOf("sweet corn", "strawberry", "melon", "tomato"),
        "Small cone-shaped holes dug in the lawn and beds (hunting grubs), eaten low fruit and ripe corn.",
        listOf(
            "A fence about 90 cm (3 ft) tall with the bottom buried 30 cm (1 ft).",
            "Don't leave fallen fruit, pet food or open compost out overnight.",
            "Motion-activated lights or sprinklers deter night visitors."
        )
    ),
    ARMADILLO(
        "Armadillos", PestKind.ANIMAL,
        setOf("potato", "sweet potato", "carrot", "beet", "strawberry", "melon"),
        "Shallow holes 5–8 cm (2–3 in) wide, plants uprooted while digging for insects, mostly at night.",
        listOf(
            "A fence about 60 cm (2 ft) tall with the bottom buried 45 cm (18 in), angled outward.",
            "Reduce grubs in the lawn and water beds less often; moist soil draws them in."
        )
    ),
    FERAL_HOG(
        "Wild boar / feral hogs", PestKind.ANIMAL,
        emptySet(),
        "Large areas rooted up overnight, trampled beds, wallows.",
        listOf(
            "Only strong fences hold them: heavy welded livestock panels at least 90 cm (3 ft) tall, staked well.",
            "A low electric fence (wires at about 20 and 40 cm) in front of the panels.",
            "Contact your local extension or wildlife office; hogs are often managed regionally."
        )
    ),
    PETS(
        "Dogs and cats (yours or neighbours')", PestKind.ANIMAL,
        emptySet(),
        "Dug-up beds, trampled plants, droppings in soft soil.",
        listOf(
            "A low fence or border of 60 cm (2 ft) wire keeps most dogs out of beds.",
            "Lay chicken wire or prickly cuttings on bare soil until plants fill in; cats avoid digging there.",
            "Don't use manure from cats or dogs in a food garden."
        )
    ),
    BIRDS(
        "Birds (crows, starlings, robins…)", PestKind.BIRD,
        setOf("strawberry", "blueberry", "raspberry", "blackberry", "currant", "gooseberry", "cherry", "sweet corn", "pea", "lettuce", "sunflower", "tomato", "grape"),
        "Seedlings pulled up, pecked fruit, berries gone just before ripe.",
        listOf(
            "Bird netting on a frame over berries and fruit, tucked in at the bottom (check it daily so birds don't get tangled).",
            "Row cover over seedlings until they have a few true leaves.",
            "Reflective tape and scare-eye balloons help for a short time; move them often."
        )
    ),
    SLUGS(
        "Slugs and snails", PestKind.INSECT,
        setOf("lettuce", "spinach", "cabbage", "kale", "strawberry", "bush bean", "pole bean", "basil", "swiss chard", "broccoli", "cauliflower", "arugula"),
        "Irregular holes in leaves, slime trails, seedlings disappearing overnight, worst in damp weather.",
        listOf(
            "Water in the morning, not the evening, so the soil surface is dry at night (drip irrigation helps).",
            "Hand-pick at dusk; boards laid on the soil collect them for easy removal.",
            "Copper tape around raised beds, beer traps, or iron-phosphate bait (safe for pets and wildlife).",
            "Keep mulch thin near seedlings until they're established."
        )
    ),
    APHIDS(
        "Aphids", PestKind.INSECT,
        emptySet(),
        "Clusters of small green, black or grey insects under leaves and on new shoots; curled, sticky leaves.",
        listOf(
            "Knock them off with a strong spray of water; repeat every few days.",
            "Plant flowers that feed ladybirds and hoverflies (sweet alyssum, dill, yarrow, calendula) near crops.",
            "Avoid too much nitrogen fertiliser; soft new growth attracts aphids.",
            "Insecticidal soap for heavy infestations."
        )
    ),
    CABBAGE_WORMS(
        "Cabbage worms and loopers", PestKind.INSECT,
        setOf("cabbage", "broccoli", "cauliflower", "kale", "brussels sprouts", "collard greens", "turnip", "radish", "arugula"),
        "Green caterpillars and holes in leaves of the cabbage family; white butterflies around the bed.",
        listOf(
            "Cover the cabbage family with fine insect netting from planting day, sealed at the edges.",
            "Hand-pick caterpillars and check under leaves for eggs.",
            "Bt (Bacillus thuringiensis) spray targets caterpillars only."
        )
    ),
    HORNWORMS(
        "Tomato hornworms", PestKind.INSECT,
        setOf("tomato", "paste tomato", "pepper", "eggplant", "potato", "tomatillo"),
        "Large green caterpillars with a 'horn'; leaves and green fruit eaten quickly; dark droppings on leaves.",
        listOf(
            "Hand-pick them (look at dusk or with a UV torch; they glow).",
            "Leave any with white cocoons on their back: those are parasitic wasps that control the rest.",
            "Till the soil lightly after harvest to expose overwintering pupae."
        )
    ),
    SQUASH_PESTS(
        "Squash bugs and vine borers", PestKind.INSECT,
        setOf("zucchini", "summer squash mix", "winter squash", "pumpkin", "cucumber", "melon", "watermelon"),
        "Sudden wilting of squash vines, sawdust-like frass at the base of the stem, clusters of bronze eggs under leaves.",
        listOf(
            "Cover young plants with row cover until they flower, then uncover for pollination.",
            "Check leaves for egg clusters and scrape them off.",
            "Rotate squash to a new spot every year (the rotation plan does this) and clear old vines after harvest."
        )
    ),
    BEETLES(
        "Beetles (cucumber, flea, Japanese, potato)", PestKind.INSECT,
        setOf("cucumber", "melon", "zucchini", "summer squash mix", "eggplant", "potato", "radish", "arugula", "turnip", "bush bean", "pole bean", "raspberry", "rose"),
        "Small round holes in leaves (flea beetles), striped or spotted beetles on cucurbits, skeletonised leaves.",
        listOf(
            "Row cover over seedlings and young plants.",
            "Hand-pick larger beetles into soapy water in the early morning, when they're slow.",
            "Yellow sticky traps near cucurbits catch cucumber beetles.",
            "Crop rotation breaks the cycle for beetles that overwinter in the soil."
        )
    ),
    MOLES(
        "Moles", PestKind.ANIMAL,
        emptySet(),
        "Raised tunnels and volcano-shaped mounds; they eat grubs and worms, not plants, but disturb roots.",
        listOf(
            "Hardware cloth under raised beds keeps tunnels out.",
            "Moles follow grubs and earthworms; they usually move on and don't eat the plants themselves."
        )
    );

    /** True when this pest is known to go for the given seed (or goes for most soft crops). */
    fun threatens(seed: SeedEntity): Boolean = targets.isEmpty() || CropReference.speciesKey(seed) in targets

    companion object {
        fun of(name: String?): Pest? = entries.firstOrNull { it.name == name?.trim() }

        /** Pests stored on a plot as "DEER,RABBIT,…"; unknown names are ignored. */
        fun parse(csv: String?): List<Pest> = csv.orEmpty().split(',').mapNotNull { of(it) }.distinct()

        fun encode(pests: Collection<Pest>): String? = pests.distinct().sortedBy { it.ordinal }.joinToString(",") { it.name }.ifBlank { null }

        /** General prevention that suits every garden. */
        val GENERAL_TIPS = listOf(
            "Walk the garden every day or two: catching damage early is the best defence.",
            "Fence before you plant; animals that find food keep coming back.",
            "Keep the area around the garden tidy: no fallen fruit, open compost or pet food, and short grass.",
            "Crop rotation and mixed planting (companions and flowers) keep insect pests from building up.",
            "Check local rules before trapping, poisoning or using electric fences, and choose methods that are safe for pets, children and wildlife."
        )
    }
}

/** Plants in the plot that a chosen pest goes for, grouped by pest. */
data class PestRisk(val pest: Pest, val atRisk: List<String>)

object PestAdvisor {
    fun risks(pests: List<Pest>, seeds: List<SeedEntity>): List<PestRisk> =
        pests.map { p -> PestRisk(p, seeds.filter { p.threatens(it) && p.targets.isNotEmpty() }.map { CropReference.speciesName(it) }.distinct().sorted()) }
}

/** Disclaimer shown before first use, in help and in the manual (FR-044). */
object Disclaimer {
    const val TITLE = "Please read: this planner is a guide, not a guarantee"
    const val TEXT = "Smart Garden Planner is a planning aid. Its layouts, sun, shade, watering, pest and planting advice are general " +
        "guidance based on typical conditions and published gardening references. It does not guarantee any harvest or result: " +
        "weather, soil, local pests, plant health and many other things are outside its control. Use it to help you decide, not " +
        "as mandatory instructions. Check local conditions, planting dates and any local rules (for example about fences, " +
        "trapping, water use or chemicals) yourself, and follow product labels. You are responsible for what you plant and how."
    const val SHORT = "A planning aid only: results aren't guaranteed. Use it to help you decide, not as instructions."
}
