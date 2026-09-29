package com.example.smartgardenplanner.core

import kotlin.math.ceil

/** One line of the homestead starter list (FR-016). */
data class HomesteadItem(val role: HomesteadRole, val seed: SeedEntity, val plants: Int, val expectedKg: Float, val note: String)

/** Expected harvest for one species on the plot (FR-022). */
data class YieldLine(val species: String, val plants: Int, val kgPerPlant: Float, val totalKg: Float)

/** Season totals of what the plot could produce (FR-020 + FR-022). */
data class NutritionTotals(
    val energyKcal: Double,
    val proteinG: Double,
    val vitaminCMg: Double,
    val vitaminAUg: Double,
    val fiberG: Double,
    val ironMg: Double
) {
    /** Days of one adult's needs covered (2000 kcal, 50 g protein, 90 mg vit C, 900 µg vit A). */
    val kcalDays get() = energyKcal / 2000.0
    val proteinDays get() = proteinG / 50.0
    val vitaminCDays get() = vitaminCMg / 90.0
    val vitaminADays get() = vitaminAUg / 900.0
}

data class Recipe(val name: String, val gardenIngredients: List<String>, val pantry: String, val method: String)

data class RecipeMatch(val recipe: Recipe, val grown: List<String>, val missing: List<String>)

/** Homestead list, yield, nutrition totals and recipe suggestions. Pure Kotlin. */
object FoodPlanner {

    private val ROLE_TARGET_KG_PER_PERSON = mapOf(
        HomesteadRole.CALORIE to 40f,
        HomesteadRole.PROTEIN to 6f,
        HomesteadRole.LEAFY to 10f,
        HomesteadRole.VITC to 10f,
        HomesteadRole.VITA to 8f,
        HomesteadRole.ALLIUM to 5f,
        HomesteadRole.FRUIT to 12f,
        HomesteadRole.HERB to 0f
    )

    private val ROLE_PREFERENCES = mapOf(
        HomesteadRole.CALORIE to listOf("potato", "sweet potato", "winter squash", "sweet corn", "parsnip", "rutabaga"),
        HomesteadRole.PROTEIN to listOf("bush bean", "pole bean", "pea", "edamame", "lima bean", "fava bean", "cowpea"),
        HomesteadRole.LEAFY to listOf("kale", "swiss chard", "lettuce", "spinach", "collard greens"),
        HomesteadRole.VITC to listOf("pepper", "tomato", "broccoli", "cabbage", "kohlrabi"),
        HomesteadRole.VITA to listOf("carrot", "sweet potato", "winter squash", "pumpkin"),
        HomesteadRole.ALLIUM to listOf("onion", "garlic", "leek"),
        HomesteadRole.FRUIT to listOf("strawberry", "raspberry", "blueberry", "apple"),
        HomesteadRole.HERB to listOf("parsley", "basil", "chives", "dill")
    )

    private val SPECIES_PER_ROLE = mapOf(HomesteadRole.CALORIE to 2, HomesteadRole.LEAFY to 2, HomesteadRole.HERB to 2)

    /**
     * A basic, balanced starter set for a household (FR-016), limited to species in the catalog that suit
     * the zone (perennials must be hardy there) and, if known, the soil pH.
     */
    fun homesteadList(catalog: List<SeedEntity>, zone: String?, soil: SoilProfile, householdSize: Int): List<HomesteadItem> {
        val people = householdSize.coerceIn(1, 20)
        val bySpecies = catalog.groupBy { CropReference.speciesKey(it) }
        val chosen = mutableSetOf<String>()
        val items = mutableListOf<HomesteadItem>()
        for (role in HomesteadRole.entries) {
            val wanted = SPECIES_PER_ROLE[role] ?: 1
            val picks = ROLE_PREFERENCES[role].orEmpty().filter { key ->
                val seed = bySpecies[key]?.firstOrNull() ?: return@filter false
                key !in chosen && !HardinessZones.blocksPlacement(seed, zone) && !SoilAnalyzer.phMismatch(soil, CropReference.forSeed(seed))
            }.take(wanted)
            for (key in picks) {
                chosen += key
                val seed = bySpecies.getValue(key).first()
                val crop = CropReference.forSeed(seed)
                val target = (ROLE_TARGET_KG_PER_PERSON[role] ?: 0f) * people / picks.size
                val plants = if (role == HomesteadRole.HERB || crop.yieldKgPerPlant <= 0f) {
                    (1 + people / 2).coerceAtMost(4)
                } else {
                    ceil(target / crop.yieldKgPerPlant).toInt().coerceIn(1, 300)
                }
                val note = when {
                    seed.lifecycle == "PERENNIAL" -> "Perennial: plant once, harvests for years."
                    crop.feeding == FeedingClass.NITROGEN_FIXER -> "Adds nitrogen to the soil."
                    role == HomesteadRole.CALORIE -> "Stores well through winter."
                    else -> "Sow in succession for a steady supply."
                }
                items += HomesteadItem(role, seed, plants, plants * crop.yieldKgPerPlant, note)
            }
        }
        return items
    }

    fun yieldLines(context: PlotContext): List<YieldLine> =
        context.plantedSeeds().groupBy { CropReference.speciesName(it) }.map { (name, seeds) ->
            val crop = CropReference.forSeed(seeds.first())
            YieldLine(name, seeds.size, crop.yieldKgPerPlant, crop.yieldKgPerPlant * seeds.size)
        }.filter { it.kgPerPlant > 0f }.sortedByDescending { it.totalKg }

    /**
     * Season nutrition totals for the plot. [overrides] holds refreshed values by species key (from the
     * nutrition_facts table); otherwise the bundled snapshot is used.
     */
    fun nutritionTotals(context: PlotContext, overrides: Map<String, Nutrients> = emptyMap()): NutritionTotals {
        var kcal = 0.0; var protein = 0.0; var vitC = 0.0; var vitA = 0.0; var fiber = 0.0; var iron = 0.0
        for (seed in context.plantedSeeds()) {
            val crop = CropReference.forSeed(seed)
            val n = overrides[crop.key] ?: crop.nutrients ?: continue
            val hundredGrams = crop.yieldKgPerPlant * 10.0
            kcal += n.energyKcal * hundredGrams
            protein += n.proteinG * hundredGrams
            vitC += n.vitaminCMg * hundredGrams
            vitA += n.vitaminAUg * hundredGrams
            fiber += n.fiberG * hundredGrams
            iron += n.ironMg * hundredGrams
        }
        return NutritionTotals(kcal, protein, vitC, vitA, fiber, iron)
    }

    val RECIPES: List<Recipe> = listOf(
        Recipe("Fresh tomato and basil salad", listOf("tomato", "basil", "onion"), "olive oil, salt, pepper", "Slice tomatoes and onion thinly, tear basil over, dress with oil and salt."),
        Recipe("Garden ratatouille", listOf("eggplant", "zucchini", "pepper", "tomato", "onion", "garlic"), "olive oil, thyme or herbs", "Soften onion and garlic, add diced vegetables in turn, simmer 40 minutes until tender."),
        Recipe("Three Sisters stew", listOf("sweet corn", "bush bean", "winter squash", "onion"), "stock, chilli, salt", "Simmer cubed squash and beans in stock with onion, add corn for the last 10 minutes."),
        Recipe("Kale and potato soup", listOf("kale", "potato", "onion", "garlic"), "stock, olive oil", "Cook onion and garlic, add diced potato and stock, simmer 20 min, add sliced kale for 5 min."),
        Recipe("Roasted root vegetables", listOf("carrot", "beet", "parsnip", "potato", "onion"), "olive oil, salt, rosemary", "Cut into chunks, toss with oil and salt, roast at 200 °C for 40 minutes."),
        Recipe("Garden salsa", listOf("tomato", "pepper", "onion", "cilantro", "tomatillo"), "lime, salt", "Dice everything finely, mix with lime juice and salt, rest 15 minutes."),
        Recipe("Stir-fried greens", listOf("bok choy", "pak choi", "garlic", "broccoli"), "soy sauce, oil, ginger", "Stir-fry garlic and ginger, add chopped greens and a splash of soy, cook 3 minutes."),
        Recipe("Cucumber and dill yoghurt salad", listOf("cucumber", "dill", "garlic"), "yoghurt, salt", "Slice cucumber, salt lightly, drain, mix with yoghurt, dill and crushed garlic."),
        Recipe("Pesto", listOf("basil", "garlic"), "olive oil, nuts, hard cheese", "Blend basil, garlic, nuts and cheese, stream in oil until smooth."),
        Recipe("Minestrone", listOf("tomato", "carrot", "celery", "onion", "zucchini", "bush bean", "swiss chard"), "pasta or rice, stock", "Soften onion, carrot and celery, add the rest and stock, simmer 30 minutes, add pasta."),
        Recipe("Coleslaw", listOf("cabbage", "carrot", "onion"), "mayonnaise or vinaigrette", "Shred cabbage and carrot, slice onion thinly, toss with dressing."),
        Recipe("Stuffed peppers", listOf("pepper", "tomato", "onion", "garlic"), "rice, cheese, spices", "Fill halved peppers with cooked rice, chopped tomato and onion, bake 30 minutes at 190 °C."),
        Recipe("Pea and mint soup", listOf("pea", "mint", "onion"), "stock, butter", "Soften onion in butter, add peas and stock, simmer 5 minutes, add mint and blend."),
        Recipe("Spinach and garlic sauté", listOf("spinach", "garlic"), "olive oil, lemon", "Warm sliced garlic in oil, add spinach until just wilted, finish with lemon."),
        Recipe("Winter squash soup", listOf("winter squash", "pumpkin", "onion", "garlic"), "stock, nutmeg", "Roast squash, blend with softened onion, garlic and stock, season with nutmeg."),
        Recipe("Strawberry spinach salad", listOf("strawberry", "spinach", "lettuce"), "nuts, balsamic vinegar", "Toss leaves with sliced strawberries, nuts and a balsamic dressing."),
        Recipe("Zucchini fritters", listOf("zucchini", "onion", "dill", "parsley"), "egg, flour, salt", "Grate and squeeze zucchini, mix with egg, flour and herbs, fry spoonfuls until golden."),
        Recipe("Green beans with garlic", listOf("bush bean", "pole bean", "garlic"), "butter or oil, salt", "Blanch beans 3 minutes, toss in a pan with garlic and butter."),
        Recipe("Leek and potato soup", listOf("leek", "potato", "onion"), "stock, cream (optional)", "Sweat sliced leek and onion, add potato and stock, simmer 20 minutes, blend."),
        Recipe("Berry crumble", listOf("raspberry", "blackberry", "blueberry", "apple", "rhubarb"), "oats, butter, sugar, flour", "Put fruit in a dish, top with rubbed-in crumble, bake 35 minutes at 180 °C."),
        Recipe("Sweet potato and black bean tacos", listOf("sweet potato", "onion", "cilantro", "pepper"), "tortillas, beans, lime", "Roast cubed sweet potato, fill tortillas with beans, onion, pepper and cilantro."),
        Recipe("Herb omelette", listOf("chives", "parsley", "dill", "basil"), "eggs, butter", "Beat eggs with chopped herbs, cook in butter, fold.")
    )

    /** Recipes that use at least one species grown on the plot (FR-021), best matches first. */
    fun recipeMatches(grownSpeciesKeys: Set<String>): List<RecipeMatch> =
        RECIPES.map { r ->
            val grown = r.gardenIngredients.filter { it in grownSpeciesKeys }
            RecipeMatch(r, grown, r.gardenIngredients - grown.toSet())
        }.filter { it.grown.isNotEmpty() }
            .sortedWith(compareByDescending<RecipeMatch> { it.grown.size.toDouble() / it.recipe.gardenIngredients.size }.thenByDescending { it.grown.size })
}
