package com.example.smartgardenplanner.core

/**
 * Bundled, offline crop reference data used by the roadmap features:
 *  - nutrition per 100 g of the raw edible part (FR-020; approximate values from the USDA SR Legacy /
 *    FoodData Central tables, bundled as a static snapshot and refreshable online, see NutritionEntity)
 *  - approximate yield per plant, kg per season (FR-022; typical home-garden figures, not guarantees)
 *  - feeding class, soil pH range, sun need, watering interval, flood tolerance (FR-013/014/005/004/017/019)
 *  - homestead roles (FR-016)
 *
 * Entries are keyed by species name as shown in the catalog (the part of commonName before " - "),
 * lower-cased. Species with no entry fall back to defaults for their plant type.
 */
enum class FeedingClass(val label: String) {
    HEAVY("Heavy feeder"), MEDIUM("Medium feeder"), LIGHT("Light feeder"), NITROGEN_FIXER("Nitrogen fixer (feeds itself)")
}

enum class SunNeed(val label: String, val minHours: Float) {
    FULL("Full sun (6+ h)", 6f), PARTIAL("Part shade OK (3+ h)", 3f), SHADE("Shade tolerant (2+ h)", 2f)
}

enum class HomesteadRole(val label: String) {
    CALORIE("Calories / staple"), PROTEIN("Protein"), LEAFY("Leafy greens"), VITC("Vitamin C"),
    VITA("Vitamin A"), ALLIUM("Alliums (flavor, storage)"), FRUIT("Fruit"), HERB("Herbs")
}

data class Nutrients(
    val energyKcal: Float,
    val proteinG: Float,
    val carbsG: Float,
    val fiberG: Float,
    val vitaminAUg: Float,
    val vitaminCMg: Float,
    val potassiumMg: Float,
    val ironMg: Float,
    val calciumMg: Float
)

data class CropInfo(
    val key: String,
    val nutrients: Nutrients?,
    val yieldKgPerPlant: Float,
    val feeding: FeedingClass,
    val phMin: Float,
    val phMax: Float,
    val sun: SunNeed,
    val waterIntervalDays: Int,
    val floodTolerant: Boolean,
    val roles: Set<HomesteadRole>,
    val isFood: Boolean
)

object CropReference {

    // key(s)|kcal|protein|carbs|fiber|vitA µg RAE|vitC mg|K mg|Fe mg|Ca mg|yield kg/plant|feed H/M/L/N|pH min|pH max|sun F/P/S|water days|flood y/n|roles
    // "-" in the kcal column means no nutrition data (not eaten, or not a meaningful food).
    private val TABLE = """
tomato/paste tomato|18|0.9|3.9|1.2|42|13.7|237|0.27|10|4.5|H|6.2|6.8|F|3|n|VITC,VITA
tomatillo|32|1|5.8|1.9|6|11.7|268|0.62|7|1.5|M|6.0|7.0|F|3|n|VITC
pepper|31|1|6|2.1|157|128|211|0.43|7|1.5|M|6.0|6.8|F|3|n|VITC,VITA
shishito type pepper|20|0.9|4.6|1.7|18|80|175|0.34|10|1|M|6.0|6.8|F|3|n|VITC
eggplant|25|1|5.9|3|1|2.2|229|0.23|9|2.5|H|5.5|6.8|F|3|n|
potato|77|2|17.5|2.1|0|19.7|425|0.81|12|1.5|M|5.0|6.5|F|4|n|CALORIE
sweet potato|86|1.6|20.1|3|709|2.4|337|0.61|30|1.5|M|5.5|6.5|F|5|n|CALORIE,VITA
carrot|41|0.9|9.6|2.8|835|5.9|320|0.3|33|0.1|L|6.0|6.8|F|3|n|VITA
beet|43|1.6|9.6|2.8|2|4.9|325|0.8|16|0.15|M|6.0|7.5|F|3|n|
radish|16|0.7|3.4|1.6|0|14.8|233|0.34|25|0.03|L|6.0|7.0|P|2|n|
daikon radish|18|0.6|4.1|1.6|0|22|227|0.4|27|0.5|L|6.0|7.5|P|3|n|VITC
turnip|28|0.9|6.4|1.8|0|21|191|0.3|30|0.2|L|6.0|7.5|P|3|n|VITC
rutabaga|37|1.1|8.6|2.3|0|25|305|0.44|43|0.5|M|6.0|7.5|F|3|n|VITC,CALORIE
parsnip|75|1.2|18|4.9|0|17|375|0.59|36|0.2|M|6.0|7.0|F|4|n|CALORIE
onion|40|1.1|9.3|1.7|0|7.4|146|0.21|23|0.15|M|6.0|7.0|F|3|n|ALLIUM
spring onion|32|1.8|7.3|2.6|50|18.8|276|1.48|72|0.05|L|6.0|7.0|P|2|n|ALLIUM
egyptian walking onion|32|1.8|7.3|2.6|50|18.8|276|1.48|72|0.1|M|6.0|7.0|F|3|n|ALLIUM
garlic|149|6.4|33|2.1|0|31|401|1.7|181|0.05|M|6.0|7.0|F|4|n|ALLIUM
leek|61|1.5|14|1.8|83|12|180|2.1|59|0.3|H|6.0|7.0|F|3|n|ALLIUM
shallot|72|2.5|16.8|3.2|1|8|334|1.2|37|0.2|M|6.0|7.0|F|3|n|ALLIUM
chives|30|3.3|4.4|2.5|218|58|296|1.6|92|0.2|L|6.0|7.0|P|3|n|ALLIUM,HERB
lettuce|15|1.4|2.9|1.3|370|9.2|194|0.86|36|0.3|M|6.0|7.0|P|2|n|LEAFY
spinach|23|2.9|3.6|2.2|469|28|558|2.7|99|0.2|H|6.5|7.5|P|2|n|LEAFY,VITA
swiss chard|19|1.8|3.7|1.6|306|30|379|1.8|51|1|M|6.0|7.5|P|3|n|LEAFY,VITA
kale|35|2.9|4.4|4.1|241|93|348|1.6|254|1|H|6.0|7.5|P|3|n|LEAFY,VITC,VITA
collard greens|32|3|5.4|4|251|35|213|0.47|232|1|H|6.0|7.5|P|3|n|LEAFY,VITA
cabbage|25|1.3|5.8|2.5|5|36.6|170|0.47|40|1.5|H|6.0|7.5|F|3|n|VITC
napa cabbage/chinese cabbage|16|1.2|3.2|1.2|16|27|238|0.31|77|1.2|H|6.0|7.5|P|3|n|LEAFY,VITC
bok choy/pak choi|13|1.5|2.2|1|223|45|252|0.8|105|0.4|M|6.0|7.5|P|2|n|LEAFY,VITC,VITA
tatsoi/komatsuna/mizuna/mustard greens|27|2.9|4.7|3.2|151|70|384|1.64|115|0.3|M|6.0|7.5|P|2|n|LEAFY,VITC
broccoli/broccolini|34|2.8|6.6|2.6|31|89|316|0.73|47|0.5|H|6.0|7.0|F|3|n|VITC
gai lan|22|1.1|3.8|2.5|86|28|274|0.6|100|0.4|M|6.0|7.0|P|3|n|LEAFY,VITC
cauliflower/romanesco|25|1.9|5|2|0|48|299|0.42|22|0.7|H|6.0|7.0|F|3|n|VITC
brussels sprouts|43|3.4|9|3.8|38|85|389|1.4|42|1|H|6.0|7.5|F|3|n|VITC
kohlrabi|27|1.7|6.2|3.6|2|62|350|0.4|24|0.3|M|6.0|7.5|F|3|n|VITC
arugula|25|2.6|3.7|1.6|119|15|369|1.46|160|0.2|L|6.0|7.0|P|2|n|LEAFY
endive|17|1.3|3.4|3.1|108|6.5|314|0.83|52|0.4|M|6.0|7.0|P|2|n|LEAFY
celery|14|0.7|3|1.6|22|3.1|260|0.2|40|0.5|H|6.0|7.0|P|2|n|
celeriac|42|1.5|9.2|1.8|0|8|300|0.7|43|0.5|H|6.0|7.0|P|2|n|
celtuce|18|0.9|3.7|1.7|175|19.5|330|0.55|39|0.3|M|6.0|7.0|P|2|n|LEAFY
cucumber|15|0.7|3.6|0.5|5|2.8|147|0.28|16|3|M|6.0|7.0|F|2|n|
zucchini|17|1.2|3.1|1|10|17.9|261|0.37|16|4|H|6.0|7.5|F|3|n|
summer squash mix|16|1.2|3.4|1.1|10|17|262|0.35|15|4|H|6.0|7.5|F|3|n|
winter squash|45|1|11.7|2|532|21|352|0.7|48|5|H|6.0|7.0|F|4|n|CALORIE,VITA
pumpkin|26|1|6.5|0.5|426|9|340|0.8|21|6|H|6.0|7.0|F|4|n|VITA
melon|34|0.8|8.2|0.9|169|36.7|267|0.21|9|3|H|6.0|6.8|F|3|n|FRUIT,VITC,VITA
watermelon|30|0.6|7.6|0.4|28|8.1|112|0.24|7|8|H|6.0|6.8|F|3|n|FRUIT
winter melon|13|0.4|3|2.9|0|13|6|0.4|19|6|H|6.0|7.0|F|3|n|
bitter melon|17|1|3.7|2.8|24|84|296|0.43|19|1.5|M|6.0|6.7|F|3|n|VITC
bottle gourd|14|0.6|3.4|0.5|0|10|150|0.2|26|4|M|6.0|7.0|F|3|n|
luffa|20|1.2|4.4|1.1|25|12|139|0.36|20|3|M|6.0|7.0|F|3|n|
chayote|19|0.8|4.5|1.7|0|7.7|125|0.34|17|10|M|6.0|6.8|F|3|n|
sweet corn|86|3.3|19|2|9|6.8|270|0.52|2|0.3|H|5.8|7.0|F|3|n|CALORIE
okra|33|1.9|7.5|3.2|36|23|299|0.62|82|0.5|M|6.0|7.0|F|4|n|VITC
bush bean|31|1.8|7|2.7|35|12.2|211|1.03|37|0.25|N|6.0|7.0|F|3|n|PROTEIN
pole bean|31|1.8|7|2.7|35|12.2|211|1.03|37|0.5|N|6.0|7.0|F|3|n|PROTEIN
lima bean|113|6.8|20|4.9|9|23|467|3.1|34|0.25|N|6.0|7.0|F|3|n|PROTEIN,CALORIE
fava bean|88|7.9|17.6|7.5|17|3.7|332|1.5|37|0.3|N|6.0|7.5|F|3|n|PROTEIN
edamame|121|11.9|8.9|5.2|9|6.1|436|2.3|63|0.3|N|6.0|7.0|F|3|n|PROTEIN
cowpea|336|23.5|60|10.6|2|1.5|1112|8.3|110|0.1|N|5.5|6.5|F|4|n|PROTEIN,CALORIE
yard-long bean|47|2.8|8.4|3.6|43|18.8|240|0.47|50|0.5|N|6.0|7.0|F|3|n|PROTEIN
chickpea|378|20.5|63|12.2|3|4|718|4.3|57|0.05|N|6.0|8.0|F|5|n|PROTEIN,CALORIE
lentil|352|24.6|63|10.7|2|4.5|677|6.5|35|0.02|N|6.0|8.0|F|5|n|PROTEIN,CALORIE
pea|81|5.4|14.5|5.1|38|40|244|1.47|25|0.15|N|6.0|7.5|F|3|n|PROTEIN
microgreen pea shoots/microgreen broccoli/microgreen radish/microgreen sunflower/wheatgrass|30|2.5|4|2|150|30|300|1|60|0.02|L|6.0|7.0|P|1|n|LEAFY
alfalfa sprouts|23|4|2.1|1.9|8|8.2|79|0.96|32|0.02|L|6.0|7.0|P|1|n|
mung bean sprouts|30|3|5.9|1.8|1|13.2|149|0.91|13|0.02|L|6.0|7.0|P|1|n|
asparagus|20|2.2|3.9|2.1|38|5.6|202|2.14|24|0.2|M|6.5|7.5|F|4|n|
artichoke|47|3.3|10.5|5.4|1|11.7|370|1.28|44|1|H|6.0|7.5|F|3|n|
cardoon|17|0.7|4.1|1.6|1|2|400|0.7|70|1|M|6.0|7.5|F|3|n|
rhubarb|21|0.9|4.5|1.8|5|8|288|0.22|86|1.5|H|5.5|7.0|F|4|n|FRUIT
strawberry|32|0.7|7.7|2|1|58.8|153|0.41|16|0.5|M|5.5|6.8|F|3|n|FRUIT,VITC
alpine strawberry|32|0.7|7.7|2|1|58.8|153|0.41|16|0.15|M|5.5|6.8|P|3|n|FRUIT,VITC
blueberry|57|0.7|14.5|2.4|3|9.7|77|0.28|6|3|L|4.5|5.5|F|3|n|FRUIT
raspberry|52|1.2|11.9|6.5|2|26.2|151|0.69|25|1.5|M|5.5|6.5|F|3|n|FRUIT,VITC
blackberry/boysenberry/tayberry|43|1.4|9.6|5.3|11|21|162|0.62|29|2|M|5.5|6.5|F|3|n|FRUIT,VITC
gooseberry|44|0.9|10.2|4.3|15|27.7|198|0.31|25|2|M|6.0|6.5|P|3|n|FRUIT,VITC
currant|63|1.4|15.4|4|12|181|322|1.54|55|2.5|M|6.0|6.5|P|3|n|FRUIT,VITC
elderberry|73|0.7|18.4|7|30|36|280|1.6|38|3|M|5.5|6.5|F|3|y|FRUIT,VITC
honeyberry|57|0.7|14.5|2.4|3|30|77|0.28|6|2|L|5.5|7.0|F|3|n|FRUIT
chokeberry (aronia)|47|1.4|12|5.3|40|21|218|0.9|32|7|M|5.5|6.5|F|3|y|FRUIT
goji berry|90|3|20|3|300|40|300|1.9|40|1|L|6.8|8.1|F|4|n|FRUIT,VITA
sea buckthorn|82|1.2|19|7|40|200|133|0.4|10|5|L|6.0|7.5|F|5|n|FRUIT,VITC
serviceberry|85|1.3|18.5|5.9|10|4|160|0.3|20|5|L|6.0|7.5|F|3|n|FRUIT
cranberry|46|0.5|12|3.6|3|14|80|0.23|8|0.5|L|4.0|5.5|F|2|y|FRUIT
grape|69|0.7|18.1|0.9|3|3.2|191|0.36|10|8|L|5.5|7.0|F|5|n|FRUIT
kiwi|61|1.1|14.7|3|4|92.7|312|0.31|34|20|M|5.5|7.0|F|3|n|FRUIT,VITC
apple|52|0.3|13.8|2.4|3|4.6|107|0.12|6|40|M|6.0|7.0|F|5|n|FRUIT
pear|57|0.4|15.2|3.1|1|4.3|116|0.18|9|40|M|6.0|7.0|F|5|n|FRUIT
peach|39|0.9|9.5|1.5|16|6.6|190|0.25|6|30|M|6.0|7.0|F|5|n|FRUIT
nectarine|44|1.1|10.6|1.7|17|5.4|201|0.28|6|30|M|6.0|7.0|F|5|n|FRUIT
apricot|48|1.4|11.1|2|96|10|259|0.39|13|30|M|6.0|7.0|F|5|n|FRUIT,VITA
plum|46|0.7|11.4|1.4|17|9.5|157|0.17|6|25|M|6.0|7.0|F|5|n|FRUIT
beach plum|46|0.7|11.4|1.4|17|9.5|157|0.17|6|5|L|6.0|7.5|F|5|n|FRUIT
cherry|63|1.1|16|2.1|3|7|222|0.36|13|20|M|6.0|7.0|F|5|n|FRUIT
quince/medlar|57|0.4|15.3|1.9|2|15|197|0.7|11|15|M|6.0|7.0|F|5|n|FRUIT
fig|74|0.8|19.2|2.9|7|2|232|0.37|35|15|L|6.0|6.5|F|5|n|FRUIT
persimmon|70|0.6|18.6|3.6|81|7.5|161|0.15|8|30|L|6.0|7.0|F|5|n|FRUIT,VITA
pomegranate|83|1.7|18.7|4|0|10.2|236|0.3|10|15|L|5.5|7.2|F|5|n|FRUIT
pawpaw|80|1.2|18.8|2.6|10|18|345|7|63|15|M|5.5|7.0|P|4|n|FRUIT
jujube|79|1.2|20.2|0|2|69|250|0.48|21|20|L|6.0|8.0|F|6|n|FRUIT,VITC
loquat|47|0.4|12.1|1.7|76|1|266|0.28|16|30|M|6.0|7.0|F|5|n|FRUIT,VITA
mulberry|43|1.4|9.8|1.7|1|36.4|194|1.85|39|15|L|6.0|7.0|F|5|n|FRUIT,VITC
almond|579|21.2|21.6|12.5|0|0|733|3.71|269|8|M|6.0|7.0|F|6|n|PROTEIN,CALORIE
walnut|654|15.2|13.7|6.7|1|1.3|441|2.9|98|30|M|6.0|7.0|F|6|n|PROTEIN,CALORIE
pecan|691|9.2|13.9|9.6|3|1.1|410|2.5|70|25|M|6.0|7.0|F|6|n|CALORIE
hazelnut|628|15|16.7|9.7|1|6.3|680|4.7|114|5|L|6.0|7.0|F|5|n|PROTEIN,CALORIE
chestnut|213|2.4|45.5|8.1|1|40.2|484|0.94|19|20|M|5.5|6.5|F|6|n|CALORIE
ground cherry|53|1.9|11.2|2|36|11|200|1|9|1|M|6.0|7.0|F|3|n|FRUIT
jicama|38|0.7|8.8|4.9|1|20.2|150|0.6|12|1.5|M|6.0|7.0|F|4|n|
salsify/scorzonera|82|3.3|18.6|3.3|0|8|380|0.7|60|0.15|L|6.0|7.0|F|3|n|
malabar spinach|19|1.8|3.4|2|400|102|510|1.2|109|2|M|6.0|7.0|F|2|n|LEAFY,VITC,VITA
new zealand spinach|14|1.5|2.5|1.5|219|30|130|0.8|58|1|L|6.0|7.0|F|3|n|LEAFY
orach|20|2|3|1.5|300|30|400|2|100|0.4|M|6.0|7.5|P|3|n|LEAFY
amaranth greens/huauzontle|23|2.5|4|2|146|43|611|2.3|215|0.5|L|6.0|7.0|F|3|n|LEAFY,VITC
grain amaranth|371|13.6|65|6.7|0|4.2|508|7.6|159|0.1|L|6.0|7.5|F|4|n|PROTEIN,CALORIE
quinoa|368|14.1|64|7|1|0|563|4.6|47|0.1|L|6.0|8.5|F|4|n|PROTEIN,CALORIE
buckwheat|343|13.3|71.5|10|0|0|460|2.2|18|0.03|L|5.0|7.0|F|4|n|CALORIE
oats|389|16.9|66|10.6|0|0|429|4.7|54|0.02|L|6.0|7.0|F|4|n|CALORIE
winter rye|338|10.3|75.9|15.1|1|0|510|2.6|24|0.02|L|5.5|7.0|F|5|n|CALORIE
watercress|11|2.3|1.3|0.5|160|43|330|0.2|120|0.2|M|6.5|7.5|P|1|y|LEAFY,VITC
sorrel/french sorrel|22|2|3.2|2.9|200|48|390|2.4|44|0.3|L|5.5|6.8|P|3|n|LEAFY,VITC,HERB
nettle|42|2.7|7.5|6.9|101|0|334|1.64|481|0.5|H|6.0|7.0|P|3|y|LEAFY,HERB
basil|23|3.2|2.7|1.6|264|18|295|3.17|177|0.3|M|6.0|7.5|F|2|n|HERB
cilantro|23|2.1|3.7|2.8|337|27|521|1.77|67|0.1|L|6.2|6.8|P|2|n|HERB
parsley|36|3|6.3|3.3|421|133|554|6.2|138|0.3|M|6.0|7.0|P|3|n|HERB,VITC,VITA
dill|43|3.5|7|2.1|386|85|738|6.6|208|0.1|L|5.5|6.5|F|3|n|HERB
mint|70|3.8|14.9|8|212|31.8|569|5.08|243|0.3|L|6.0|7.0|P|2|y|HERB
oregano/thyme/sage/rosemary/marjoram/savory/winter savory/tarragon/hyssop/lemon balm/lemon verbena/lovage/chervil/bay laurel|50|3|10|6|150|30|400|5|300|0.2|L|6.0|8.0|F|5|n|HERB
lavender|-|0|0|0|0|0|0|0|0|0.2|L|6.5|8.0|F|6|n|HERB
lemongrass|99|1.8|25.3|0|0|2.6|723|8.2|65|0.5|M|6.0|7.0|F|3|n|HERB
fennel|31|1.2|7.3|3.1|48|12|414|0.73|49|0.3|M|6.0|7.0|F|3|n|HERB
horseradish|48|1.2|11.3|3.3|0|24.9|246|0.42|56|0.3|L|6.0|7.0|F|3|n|HERB
shiso|37|3.9|7.6|7.3|500|26|500|1.7|230|0.2|M|6.0|7.0|P|2|n|HERB
hosta/impatiens/bleeding heart/trillium/wild violet/snowdrop/sweet woodruff|-|0|0|0|0|0|0|0|0|0|L|5.5|7.0|S|3|n|
begonia/foxglove/columbine/hydrangea/dogwood/japanese maple/pothos/snake plant/spider plant/jade plant/fiddle leaf fig|-|0|0|0|0|0|0|0|0|0|M|5.5|7.0|P|4|n|
azalea/rhododendron/camellia|-|0|0|0|0|0|0|0|0|0|L|4.5|6.0|P|4|n|
crimson clover/hairy vetch|-|0|0|0|0|0|0|0|0|0|N|6.0|7.0|F|4|n|
joe pye weed/iris/milkweed/switchgrass/comfrey|-|0|0|0|0|0|0|0|0|0|L|5.5|7.5|F|4|y|
""".trimIndent()

    private val entries: Map<String, CropInfo> by lazy { parseTable(TABLE) }

    internal fun parseTable(table: String): Map<String, CropInfo> {
        val map = mutableMapOf<String, CropInfo>()
        for (line in table.lines()) {
            if (line.isBlank()) continue
            val f = line.split("|")
            if (f.size < 18) continue
            val nutrients = if (f[1].trim() == "-") null else Nutrients(
                f[1].toFloat(), f[2].toFloat(), f[3].toFloat(), f[4].toFloat(), f[5].toFloat(),
                f[6].toFloat(), f[7].toFloat(), f[8].toFloat(), f[9].toFloat()
            )
            val feeding = when (f[11].trim()) {
                "H" -> FeedingClass.HEAVY
                "L" -> FeedingClass.LIGHT
                "N" -> FeedingClass.NITROGEN_FIXER
                else -> FeedingClass.MEDIUM
            }
            val sun = when (f[14].trim()) {
                "P" -> SunNeed.PARTIAL
                "S" -> SunNeed.SHADE
                else -> SunNeed.FULL
            }
            val roles = f[17].split(",").mapNotNull { r -> HomesteadRole.entries.firstOrNull { it.name == r.trim() } }.toSet()
            for (key in f[0].split("/")) {
                val k = key.trim().lowercase()
                map[k] = CropInfo(
                    key = k,
                    nutrients = nutrients,
                    yieldKgPerPlant = f[10].toFloat(),
                    feeding = feeding,
                    phMin = f[12].toFloat(),
                    phMax = f[13].toFloat(),
                    sun = sun,
                    waterIntervalDays = f[15].toInt(),
                    floodTolerant = f[16].trim() == "y",
                    roles = roles,
                    isFood = nutrients != null
                )
            }
        }
        return map
    }

    /** Species name of a catalog seed, e.g. "Tomato - Brandywine" -> "tomato". */
    fun speciesKey(seed: SeedEntity): String = seed.commonName.substringBefore(" - ").trim().lowercase()

    /** Display name of the species, e.g. "Tomato". */
    fun speciesName(seed: SeedEntity): String = seed.commonName.substringBefore(" - ").trim()

    fun lookup(speciesKey: String): CropInfo? = entries[speciesKey.lowercase()]

    /** Reference data for a seed, falling back to defaults for its plant type when the species isn't listed. */
    fun forSeed(seed: SeedEntity): CropInfo = lookup(speciesKey(seed)) ?: defaultFor(seed)

    fun hasEntry(seed: SeedEntity): Boolean = lookup(speciesKey(seed)) != null

    fun allKeys(): Set<String> = entries.keys

    private fun defaultFor(seed: SeedEntity): CropInfo {
        val isHerb = seed.plantType == "HERB"
        return CropInfo(
            key = speciesKey(seed),
            nutrients = null,
            yieldKgPerPlant = if (seed.plantType == "FRUIT") 1f else 0f,
            feeding = if (seed.plantType == "VEGETABLE") FeedingClass.MEDIUM else FeedingClass.LIGHT,
            phMin = 6.0f,
            phMax = 7.0f,
            sun = if (seed.plantType == "ORNAMENTAL") SunNeed.PARTIAL else SunNeed.FULL,
            waterIntervalDays = if (seed.lifecycle == "PERENNIAL") 5 else 3,
            floodTolerant = false,
            roles = if (isHerb) setOf(HomesteadRole.HERB) else emptySet(),
            isFood = false
        )
    }
}
