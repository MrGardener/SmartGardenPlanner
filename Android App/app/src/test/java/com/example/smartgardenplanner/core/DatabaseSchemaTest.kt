package com.example.smartgardenplanner.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Schema 12 (LLR-DB-010/020/040, LLR-MIG-010): the migrations, replayed on paper from the version-2 tables, must
 * produce exactly the columns, types and nullability of the Room entities. Room refuses to open a database whose
 * migrated schema differs from the entities, so a new entity field without a migration would lose the user's data.
 * The check runs on the JVM from the source files; the same schema is checked on a device by AppDatabaseTest.
 */
class DatabaseSchemaTest {

    private val root = listOf("app/src/main/java/com/example/smartgardenplanner", "Android App/app/src/main/java/com/example/smartgardenplanner",
        "src/main/java/com/example/smartgardenplanner").map(::File).first { it.exists() }
    private fun src(path: String) = File(root, path).readText()

    /** Tables and columns that existed before the first migration (schema 2): name to (SQL type, NOT NULL). */
    private val v2Base = mapOf(
        "plots" to mapOf("id" to ("INTEGER" to true), "name" to ("TEXT" to true), "lengthM" to ("REAL" to true),
            "widthM" to ("REAL" to true), "description" to ("TEXT" to true)),
        "app_configurations" to mapOf("configKey" to ("TEXT" to true), "configValue" to ("TEXT" to true),
            "lastUpdatedTimestamp" to ("INTEGER" to true)),
        "planted_nodes" to mapOf("id" to ("INTEGER" to true), "plotId" to ("INTEGER" to true), "seedCode" to ("TEXT" to true),
            "coordinateXM" to ("REAL" to true), "coordinateYM" to ("REAL" to true))
    )

    private val entityFiles = listOf("core/PlotEntity.kt", "core/PlantedNodeEntity.kt", "core/SeedEntity.kt", "core/ClimateZoneEntity.kt",
        "core/PathZoneEntity.kt", "core/SiteFeatureEntity.kt", "core/CareLogEntity.kt", "core/NutritionEntity.kt",
        "core/Seasons.kt", "data/AppConfig.kt")

    /** table -> column -> (SQL type, NOT NULL) from the entity's primary constructor. */
    private fun entities(): Map<String, Map<String, Pair<String, Boolean>>> = entityFiles.associate { f ->
        val text = src(f).let { it.substring(it.indexOf("@Entity")) }
        val table = Regex("""@Entity\(\s*(?:tableName\s*=\s*)?"(\w+)"""").find(text)?.groupValues?.get(1)
            ?: Regex("""tableName\s*=\s*"(\w+)"""").find(text)!!.groupValues[1]
        // The primary constructor: from "data class X(" to its matching ")", with line comments removed first.
        val code = text.lines().joinToString("\n") { it.substringBefore("//") }
        val open = code.indexOf('(', code.indexOf("data class"))
        var depth = 0; var end = open
        for (i in open until code.length) { if (code[i] == '(') depth++; if (code[i] == ')') depth--; if (depth == 0) { end = i; break } }
        val ctor = code.substring(open, end + 1)
        val cols = Regex("""val (\w+):\s*([\w.]+)(\?)?""").findAll(ctor).associate { m ->
            val type = when (m.groupValues[2]) {
                "Long", "Int", "Boolean" -> "INTEGER"; "Float", "Double" -> "REAL"; "String" -> "TEXT"; else -> "?" + m.groupValues[2]
            }
            m.groupValues[1] to (type to (m.groupValues[3] != "?"))
        }
        table to cols
    }

    /** Replays CREATE TABLE, ADD COLUMN, DROP TABLE and RENAME from Migrations.kt, in file order. */
    private fun migrated(): Map<String, Map<String, Pair<String, Boolean>>> {
        val tables = v2Base.mapValues { it.value.toMutableMap() }.toMutableMap()
        // Join Kotlin string pieces ("a " + "b") and raw strings into plain SQL statements.
        val sql = Regex("""execSQL\(\s*((?:"(?:[^"\\]|\\.)*"\s*\+?\s*)+|""${'"'}[\s\S]*?""${'"'}(?:\.trimIndent\(\))?)\s*\)""")
            .findAll(src("data/Migrations.kt")).map { m ->
                val raw = m.groupValues[1]
                if (raw.startsWith("\"\"\"")) raw.removeSuffix(".trimIndent()").trim('"')
                else Regex(""""((?:[^"\\]|\\.)*)"""").findAll(raw).joinToString("") { it.groupValues[1] }
            }.map { it.replace("`", "").replace(Regex("\\s+"), " ").trim() }
        for (s in sql) {
            Regex("""^CREATE TABLE IF NOT EXISTS (\w+) \((.*)\)$""", RegexOption.IGNORE_CASE).find(s)?.let { m ->
                val cols = mutableMapOf<String, Pair<String, Boolean>>()
                splitTopLevel(m.groupValues[2]).map { it.trim() }.filter { !it.startsWith("FOREIGN") && !it.startsWith("PRIMARY KEY") }.forEach { c ->
                    val p = c.split(" ")
                    cols[p[0]] = p[1] to (c.contains("NOT NULL") || c.contains("PRIMARY KEY"))
                }
                tables[m.groupValues[1]] = cols
                return@let
            }
            Regex("""^ALTER TABLE (\w+) ADD COLUMN (\w+) (\w+)(.*)$""").find(s)?.let { m ->
                val rest = m.groupValues[4]
                assertTrue("NOT NULL column needs a DEFAULT: $s", !rest.contains("NOT NULL") || rest.contains("DEFAULT"))
                tables.getValue(m.groupValues[1])[m.groupValues[2]] = m.groupValues[3] to rest.contains("NOT NULL")
            }
            Regex("""^DROP TABLE (\w+)$""").find(s)?.let { tables.remove(it.groupValues[1]) }
            Regex("""^ALTER TABLE (\w+) RENAME TO (\w+)$""").find(s)?.let { tables[it.groupValues[2]] = tables.remove(it.groupValues[1])!! }
        }
        return tables
    }

    private fun splitTopLevel(s: String): List<String> {
        val out = mutableListOf<String>(); var depth = 0; val cur = StringBuilder()
        for (ch in s) {
            if (ch == '(') depth++; if (ch == ')') depth--
            if (ch == ',' && depth == 0) { out += cur.toString(); cur.clear() } else cur.append(ch)
        }
        return out + cur.toString()
    }

    @Test
    fun schemaVersionIs12_andEveryEntityIsRegistered() {
        val db = src("data/AppDatabase.kt")
        assertTrue("schema version", Regex("""version\s*=\s*12\b""").containsMatchIn(db))
        val registered = Regex("""(\w+)::class""").findAll(db.substringAfter("entities").substringBefore("]")).map { it.groupValues[1] }.toSet()
        val declared = entityFiles.map { f -> Regex("""data class (\w+)""").find(src(f).let { it.substring(it.indexOf("@Entity")) })!!.groupValues[1] }.toSet()
        assertEquals(declared, registered)
    }

    @Test
    fun migrationsFormOneUnbrokenChainTo12_andAreAllRegistered() {
        val m = src("data/Migrations.kt")
        val steps = Regex("""val (MIGRATION_(\d+)_(\d+))\s*=\s*object\s*:\s*Migration\((\d+),\s*(\d+)\)""").findAll(m).map { it.groupValues }.toList()
        steps.forEach { g ->
            assertEquals("name matches versions: ${g[1]}", g[2] to g[3], g[4] to g[5])
            assertEquals("${g[1]} is one step", g[4].toInt() + 1, g[5].toInt())
        }
        assertEquals((2..11).toList(), steps.map { it[4].toInt() }.sorted())
        val all = m.substringAfter("val ALL_MIGRATIONS").substringBefore(")")
        steps.forEach { assertTrue("${it[1]} registered", all.contains(it[1])) }
    }

    @Test
    fun migratedTablesMatchTheEntitiesColumnForColumn() {
        val want = entities()
        val got = migrated()
        assertEquals("tables", want.keys.sorted(), got.keys.sorted())
        for ((table, cols) in want) {
            val have = got.getValue(table)
            assertEquals("$table columns", cols.keys.sorted(), have.keys.sorted())
            for ((c, spec) in cols) {
                assertEquals("$table.$c type", spec.first, have.getValue(c).first)
                assertEquals("$table.$c NOT NULL", spec.second, have.getValue(c).second)
            }
        }
    }

    @Test
    fun theSchemaCheckItselfCatchesAMissingMigration() {
        // Robustness of the check: a field added to an entity without a migration must show up as a difference.
        val want = entities().toMutableMap()
        want["plots"] = want.getValue("plots") + ("newField" to ("TEXT" to false))
        val got = migrated()
        assertTrue(want.getValue("plots").keys != got.getValue("plots").keys)
        // And a column added by a migration with the wrong nullability is caught.
        val flipped = got.getValue("plots").toMutableMap().also { it["address"] = "TEXT" to true }
        assertTrue(flipped.getValue("address") != entities().getValue("plots").getValue("address"))
    }
}
