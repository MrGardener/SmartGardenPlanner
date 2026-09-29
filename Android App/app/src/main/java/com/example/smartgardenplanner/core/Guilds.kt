package com.example.smartgardenplanner.core

/**
 * Named interplanting guilds (FR-009): groups of plants traditionally grown close together because they
 * help each other (support, shade, pest confusion, nitrogen). Members are species keys, see
 * [CropReference.speciesKey].
 *
 * When guilds are switched on, two members of the same guild may be planted closer than their full
 * spacing circles: their centres only need to be the larger plant's radius apart (the smaller plant sits at
 * the edge of the larger one's circle). Antagonist warnings between members of the same guild are skipped.
 * Everything else, including spacing between non-members, is checked as before.
 */
data class Guild(val name: String, val description: String, val members: Set<String>)

object GuildCatalog {

    val ALL: List<Guild> = listOf(
        Guild(
            "Three Sisters",
            "Corn gives beans a pole to climb, beans add nitrogen, squash leaves shade out weeds and hold moisture.",
            setOf("sweet corn", "pole bean", "bush bean", "winter squash", "pumpkin", "zucchini", "summer squash mix")
        ),
        Guild(
            "Tomato guild",
            "Basil and marigold deter pests around tomatoes; carrots and onions use the soil below; borage draws pollinators.",
            setOf("tomato", "paste tomato", "basil", "marigold", "carrot", "onion", "chives", "borage")
        ),
        Guild(
            "Cucumber guild",
            "Dill and nasturtium attract beneficial insects and distract aphids; radish is sown between as a quick catch crop; sunflowers give light shade and support.",
            setOf("cucumber", "dill", "nasturtium", "radish", "sunflower")
        ),
        Guild(
            "Brassica guild",
            "Aromatic herbs and onions confuse cabbage moths; nasturtium is a trap crop for aphids.",
            setOf("cabbage", "broccoli", "kale", "cauliflower", "brussels sprouts", "collard greens", "dill", "onion", "thyme", "nasturtium")
        ),
        Guild(
            "Fruit tree guild",
            "Comfrey is a dynamic accumulator and mulch plant, chives and daffodils deter pests and rodents, clover fixes nitrogen, yarrow draws predatory insects.",
            setOf("apple", "pear", "plum", "cherry", "comfrey", "chives", "daffodil", "nasturtium", "yarrow", "crimson clover")
        ),
        Guild(
            "Salad and roots",
            "Shallow-rooted lettuce and fast radishes fill the gaps between slower carrots and onions; onion scent masks carrots from carrot fly.",
            setOf("carrot", "onion", "lettuce", "radish", "chives")
        ),
        Guild(
            "Strawberry guild",
            "Borage attracts pollinators, thyme deters worms, spinach and lettuce use the space between runners, bush beans add nitrogen.",
            setOf("strawberry", "borage", "thyme", "spinach", "lettuce", "bush bean")
        )
    )

    /** Guilds that include both species, or an empty list. */
    fun sharedGuilds(a: SeedEntity, b: SeedEntity, guilds: List<Guild> = ALL): List<Guild> {
        val ka = CropReference.speciesKey(a)
        val kb = CropReference.speciesKey(b)
        return guilds.filter { ka in it.members && kb in it.members }
    }

    fun guildsFor(seed: SeedEntity, guilds: List<Guild> = ALL): List<Guild> {
        val k = CropReference.speciesKey(seed)
        return guilds.filter { k in it.members }
    }
}
