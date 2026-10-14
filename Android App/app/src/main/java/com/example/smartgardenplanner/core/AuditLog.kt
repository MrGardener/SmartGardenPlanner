package com.example.smartgardenplanner.core

/** Events recorded in the audit log (LLR-AUD-010). */
enum class AuditType { DATA_RESET, KEY_LOSS, BATTERY_OVERRIDE, TIER_SWITCH, IMPORT_ACCEPTED, IMPORT_REJECTED, LOG_ROTATED }

/**
 * One audit entry (LLR-AUD-010). [details] is the exact JSON object text that was hashed, so the hash can be recomputed
 * byte for byte when the log is read back.
 */
data class AuditEntry(val seq: Long, val time: String, val type: AuditType, val details: String, val prevHash: String, val hash: String)

/** Result of checking a log (LLR-AUD-030, LLR-AUDS-040). */
sealed class AuditVerdict {
    data object Intact : AuditVerdict()
    /** The first entry whose hash, link or sequence number is wrong. */
    data class Broken(val seq: Long, val reason: String) : AuditVerdict()
    /** A line that can't be decrypted; [seq] is the number it should have had. */
    data class Unreadable(val seq: Long) : AuditVerdict()
}

/** The hash chain itself: pure functions, no storage (LLR-AUD-010…030). */
object AuditChain {
    /** prevHash of the first entry of a chain: 64 zeros (LLR-AUD-020). */
    val GENESIS: String = "0".repeat(64)
    const val MAX_DETAILS_BYTES = 1024

    /** hash = lowercase hex SHA-256 of "prevHash|seq|time|type|details" (LLR-AUD-020). */
    fun hash(prevHash: String, seq: Long, time: String, type: AuditType, details: String): String =
        Sha256.hex("$prevHash|$seq|$time|${type.name}|$details")

    /** UTC ISO-8601 with milliseconds, e.g. "2026-10-14T06:33:18.042Z". */
    fun time(millis: Long): String {
        val ms = ((millis % 1000) + 1000) % 1000
        return CivilDate.isoUtc(millis - ms).dropLast(1) + "." + ms.toString().padStart(3, '0') + "Z"
    }

    /**
     * The details as a compact JSON object of string, number, boolean or null values, in the given key order. If it
     * would exceed [MAX_DETAILS_BYTES] UTF-8 bytes, each text value is shortened until it fits, so an entry is always
     * written (never dropped) and always within the limit.
     */
    fun details(values: Map<String, Any?>): String {
        var limit = 1000
        while (true) {
            val text = values.entries.joinToString(",", "{", "}") { (k, v) -> quote(k.take(64)) + ":" + value(v, limit) }
            if (text.encodeToByteArray().size <= MAX_DETAILS_BYTES || limit == 0) return text
            limit /= 2
        }
    }

    private fun value(v: Any?, limit: Int): String = when (v) {
        null -> "null"
        is Boolean -> v.toString()
        is Int, is Long -> v.toString()
        is Float -> if (v.isFinite()) v.toDouble().fmt(4) else "null"
        is Double -> if (v.isFinite()) v.fmt(4) else "null"
        else -> quote(v.toString().take(limit))
    }

    fun quote(s: String): String = buildString {
        append('"')
        for (c in s) when {
            c == '"' -> append("\\\"")
            c == '\\' -> append("\\\\")
            c == '\n' -> append("\\n")
            c == '\r' -> append("\\r")
            c == '\t' -> append("\\t")
            c.code < 0x20 -> append("\\u" + c.code.toString(16).padStart(4, '0'))
            else -> append(c)
        }
        append('"')
    }

    /** The entry that follows [previous] (or starts a chain when null). */
    fun next(previous: AuditEntry?, type: AuditType, details: String, nowMillis: Long): AuditEntry {
        val seq = (previous?.seq ?: 0L) + 1
        val prev = previous?.hash ?: GENESIS
        val t = time(nowMillis)
        return AuditEntry(seq, t, type, details, prev, hash(prev, seq, t, type, details))
    }

    /** One entry as one line of JSON. */
    fun encode(e: AuditEntry): String =
        "{\"seq\":${e.seq},\"time\":${quote(e.time)},\"type\":${quote(e.type.name)},\"details\":${quote(e.details)},\"prevHash\":${quote(e.prevHash)},\"hash\":${quote(e.hash)}}"

    /** The entry in [line], or null if it isn't a well-formed entry. */
    fun decode(line: String): AuditEntry? {
        val m = MiniJson.parse(line) as? Map<*, *> ?: return null
        val seq = (m["seq"] as? Number)?.toLong() ?: return null
        val type = AuditType.entries.firstOrNull { it.name == m["type"] } ?: return null
        return AuditEntry(seq, m["time"] as? String ?: return null, type, m["details"] as? String ?: return null,
            m["prevHash"] as? String ?: return null, m["hash"] as? String ?: return null)
    }

    /**
     * Recomputes each hash in order and checks that the first entry starts the chain (seq 1, prevHash of 64 zeros),
     * that every later entry links to the one before it with seq + 1, and that a LOG_ROTATED entry names the hash it
     * continues from (LLR-AUD-030). After a key loss the old files are set aside, so a log is always one chain.
     */
    fun verify(entries: List<AuditEntry>): AuditVerdict {
        var previous: AuditEntry? = null
        for (e in entries) {
            val wantSeq = (previous?.seq ?: 0L) + 1
            if (e.seq != wantSeq) return AuditVerdict.Broken(wantSeq, "sequence number ${e.seq} where $wantSeq was expected")
            if (e.prevHash != (previous?.hash ?: GENESIS)) return AuditVerdict.Broken(e.seq, "not linked to the entry before it")
            if (e.hash != hash(e.prevHash, e.seq, e.time, e.type, e.details)) return AuditVerdict.Broken(e.seq, "contents changed")
            if (e.details.encodeToByteArray().size > MAX_DETAILS_BYTES) return AuditVerdict.Broken(e.seq, "details over $MAX_DETAILS_BYTES bytes")
            if (e.type == AuditType.LOG_ROTATED && !e.details.contains(quote(e.prevHash)))
                return AuditVerdict.Broken(e.seq, "rotation entry doesn't name the hash it continues from")
            previous = e
        }
        return AuditVerdict.Intact
    }
}

/** Where the numbered audit files live (LLR-AUDS-010). The phone implements it on files/audit/audit-<n>.log. */
interface AuditFiles {
    /** The file numbers that exist, in any order. */
    fun numbers(): List<Int>
    fun readLines(n: Int): List<String>
    /** Appends one line and flushes it to storage before returning (LLR-AUDS-020). */
    fun appendLine(n: Int, line: String)
    fun sizeBytes(n: Int): Long
    /** Moves every audit file aside (kept, renamed with [tag]) so a new chain can start (LLR-AUDS-050). */
    fun setAside(tag: String)
}

/** Encrypts one entry per line (LLR-AUDS-010). [open] returns null when the line can't be decrypted. */
interface AuditCipher {
    fun seal(plain: ByteArray): String
    fun open(line: String): ByteArray?
}

/** What reading the whole log gives: the entries in order, and the verdict (LLR-AUDS-040). */
data class AuditReadout(val entries: List<AuditEntry>, val verdict: AuditVerdict)

/**
 * The audit log on its numbered, encrypted files: append with rotation (LLR-AUDS-020/030), a new chain after the key is
 * lost (LLR-AUDS-050), and reading with verification (LLR-AUDS-040). Storage and cipher are injected, so the rules are
 * tested on the JVM; the phone supplies keystore AES-GCM and real files.
 */
class AuditLog(
    private val files: AuditFiles,
    private val cipher: AuditCipher,
    private val now: () -> Long = { PlatformClock.nowMillis() },
    private val rotateBytes: Long = ROTATE_BYTES
) {
    companion object {
        const val ROTATE_BYTES = 1_048_576L
        const val KEY_LOSS_NOTE = "previous audit files unreadable"
        fun fileName(n: Int) = "audit-$n.log"
    }

    private fun sealed(e: AuditEntry) = cipher.seal(AuditChain.encode(e).encodeToByteArray())

    private fun readable(line: String): AuditEntry? = cipher.open(line)?.let { AuditChain.decode(it.decodeToString()) }

    /** Appends an event and returns its entry. */
    fun append(type: AuditType, details: Map<String, Any?> = emptyMap()): AuditEntry {
        var n = files.numbers().maxOrNull()
        var last: AuditEntry? = null
        if (n != null) {
            val lines = files.readLines(n).filter { it.isNotBlank() }
            last = lines.asReversed().firstNotNullOfOrNull { readable(it) }
            if (last == null && lines.isNotEmpty()) {
                // Nothing in the newest file can be decrypted: the key is lost. Keep the old files, start a new chain.
                files.setAside("unreadable-" + AuditChain.time(now()).replace(":", "-"))
                val first = AuditChain.next(null, AuditType.KEY_LOSS, AuditChain.details(mapOf("log" to KEY_LOSS_NOTE)), now())
                files.appendLine(1, sealed(first))
                n = 1; last = first
            }
        }
        if (n == null) n = 1
        if (last != null && files.sizeBytes(n) > rotateBytes) {
            val rotated = AuditChain.next(last, AuditType.LOG_ROTATED,
                AuditChain.details(mapOf("previousFile" to fileName(n), "lastHash" to last.hash)), now())
            n += 1
            files.appendLine(n, sealed(rotated))
            last = rotated
        }
        // A LOG_ROTATED entry always names the hash it continues from, whoever asks for it.
        val withLink = if (type == AuditType.LOG_ROTATED) details + ("lastHash" to (last?.hash ?: AuditChain.GENESIS)) else details
        val entry = AuditChain.next(last, type, AuditChain.details(withLink), now())
        files.appendLine(n, sealed(entry))
        return entry
    }

    /** Decrypts every file in order and checks the chain. */
    fun read(): AuditReadout {
        val out = mutableListOf<AuditEntry>()
        for (n in files.numbers().sorted()) for (line in files.readLines(n)) {
            if (line.isBlank()) continue
            val e = readable(line) ?: return AuditReadout(out, AuditVerdict.Unreadable((out.lastOrNull()?.seq ?: 0L) + 1))
            out += e
        }
        return AuditReadout(out, AuditChain.verify(out))
    }
}
