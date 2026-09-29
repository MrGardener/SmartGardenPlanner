package com.example.smartgardenplanner.core

/** The bundled Pro catalog, read from the assets folder, for tests that need real varieties. */
object TestCatalog {
    val seeds: List<SeedEntity> by lazy {
        val file = listOf("app/src/main/assets/seed_catalog_pro.txt", "Android App/app/src/main/assets/seed_catalog_pro.txt", "src/main/assets/seed_catalog_pro.txt")
            .map { java.io.File(it) }.first { it.exists() }
        file.readLines().mapNotNull { l ->
            val p = l.split("|"); if (p.size < 14) null else SeedEntity(
                botanicalCode = p[0], commonName = p[1], botanicalFamily = p[2], plantType = p[3], lifecycle = p[4],
                hardinessZoneMin = p[5].toInt(), hardinessZoneMax = p[6].toInt(), exclusionRadiusM = p[7].toFloat(),
                germinationDays = p[8].toInt(), daysToHarvest = p[9].toInt(), companionCodes = p[10], antagonistCodes = p[11],
                pestNotes = p[12], careNotes = p[13])
        }
    }
    val byCode: Map<String, SeedEntity> by lazy { seeds.associateBy { it.botanicalCode } }
    val lookup: (String) -> SeedEntity? = { byCode[it] }
    fun named(name: String) = seeds.first { it.commonName == name }
    val vegetables: List<SeedEntity> by lazy { seeds.filter { it.plantType == "VEGETABLE" || it.plantType == "HERB" } }
}
