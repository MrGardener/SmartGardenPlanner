package com.example.smartgardenplanner.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

/** The audit log (LLR-AUD-010…030, LLR-AUDS-010…060): hash chain, rotation, key loss, tamper detection. */
class AuditLogTest {

    /** Files in memory. */
    private class MemFiles : AuditFiles {
        val files = sortedMapOf<Int, MutableList<String>>()
        val aside = mutableListOf<Pair<String, Map<Int, List<String>>>>()
        override fun numbers() = files.keys.toList()
        override fun readLines(n: Int) = files[n]?.toList() ?: emptyList()
        override fun appendLine(n: Int, line: String) { files.getOrPut(n) { mutableListOf() } += line }
        override fun sizeBytes(n: Int) = (files[n] ?: emptyList()).sumOf { it.length + 1L }
        override fun setAside(tag: String) { aside += tag to files.mapValues { it.value.toList() }; files.clear() }
    }

    /** A stand-in for AES-GCM: XOR with a key byte plus a check byte, so a line from another key doesn't open. */
    private class FakeCipher(var key: Int) : AuditCipher {
        override fun seal(plain: ByteArray): String {
            val body = plain.map { (it.toInt() xor key).toByte() }.toByteArray()
            return (byteArrayOf(key.toByte()) + body).joinToString("") { (it.toInt() and 0xff).toString(16).padStart(2, '0') }
        }
        override fun open(line: String): ByteArray? {
            if (line.length % 2 != 0 || line.length < 2) return null
            val bytes = try { line.chunked(2).map { it.toInt(16).toByte() } } catch (e: NumberFormatException) { return null }
            if (bytes[0].toInt() and 0xff != key and 0xff) return null
            return bytes.drop(1).map { (it.toInt() xor key).toByte() }.toByteArray()
        }
    }

    private var clock = 1_760_000_000_000L
    private fun log(files: MemFiles, cipher: FakeCipher, rotate: Long = AuditLog.ROTATE_BYTES) = AuditLog(files, cipher, { clock++ }, rotate)

    @Test
    fun sha256_matchesTheFipsVectors() {
        assertEquals("e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855", Sha256.hex(""))
        assertEquals("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad", Sha256.hex("abc"))
        assertEquals("248d6a61d20638b8e5c026930c3e6039a33ce45964ff2167f6ecedd419db06c1",
            Sha256.hex("abcdbcdecdefdefgefghfghighijhijkijkljklmklmnlmnomnopnopq"))
        assertEquals("cdc76e5c9914fb9281a1c7e284d73e67f1809a48a497200e046d39ccc7112cd0", Sha256.hex("a".repeat(1_000_000)))
        // Message lengths around the 55/56/64-byte padding boundaries agree with the JVM's own SHA-256.
        val jvm = java.security.MessageDigest.getInstance("SHA-256")
        val rnd = Random(4)
        for (len in (0..130) + listOf(1000, 4096)) {
            val bytes = ByteArray(len) { rnd.nextInt(256).toByte() }
            assertEquals("length $len", jvm.digest(bytes).toList(), Sha256.digest(bytes).toList())
        }
        assertEquals(Sha256.hex("Ñandú 🌱"), jvm.digest("Ñandú 🌱".toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) })
    }

    @Test
    fun entries_chainFromSixtyFourZeros_withContinuousSequence() {
        val files = MemFiles(); val log = log(files, FakeCipher(0x5A))
        val a = log.append(AuditType.TIER_SWITCH, mapOf("from" to "BASIC", "to" to "PRO", "kept" to 3))
        val b = log.append(AuditType.IMPORT_ACCEPTED, mapOf("plots" to 2))
        assertEquals(1L, a.seq); assertEquals(AuditChain.GENESIS, a.prevHash); assertEquals(64, a.hash.length)
        assertEquals(2L, b.seq); assertEquals(a.hash, b.prevHash)
        assertEquals(AuditChain.hash(a.prevHash, 1, a.time, a.type, a.details), a.hash)
        assertEquals("""{"from":"BASIC","to":"PRO","kept":3}""", a.details)
        assertTrue(a.time, Regex("^\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}\\.\\d{3}Z$").matches(a.time))
        val r = log.read()
        assertEquals(AuditVerdict.Intact, r.verdict); assertEquals(listOf(a, b), r.entries)
        // Lines on storage are not readable text.
        assertTrue(files.readLines(1).none { it.contains("TIER_SWITCH") || it.contains("BASIC") })
        // An empty log reads as intact with no entries.
        assertEquals(AuditReadout(emptyList(), AuditVerdict.Intact), log(MemFiles(), FakeCipher(1)).read())
    }

    @Test
    fun tampering_isFoundAtTheFirstChangedEntry() {
        val cipher = FakeCipher(0x33)
        fun fresh(): MemFiles { val f = MemFiles(); val l = log(f, cipher); repeat(5) { i -> l.append(AuditType.IMPORT_ACCEPTED, mapOf("n" to i)) }; return f }
        fun rewrite(f: MemFiles, index: Int, change: (AuditEntry) -> AuditEntry) {
            val e = AuditChain.decode(cipher.open(f.files[1]!![index])!!.decodeToString())!!
            f.files[1]!![index] = cipher.seal(AuditChain.encode(change(e)).encodeToByteArray())
        }
        // Changed details, type and time: "contents changed" at that entry.
        for (change in listOf<(AuditEntry) -> AuditEntry>({ it.copy(details = """{"n":99}""") }, { it.copy(type = AuditType.DATA_RESET) }, { it.copy(time = "2000-01-01T00:00:00.000Z") })) {
            val f = fresh(); rewrite(f, 2, change)
            assertEquals(AuditVerdict.Broken(3, "contents changed"), log(f, cipher).read().verdict)
        }
        // Re-hashing the changed entry doesn't help: the next entry no longer links to it.
        val f = fresh(); rewrite(f, 2) { e -> val d = """{"n":99}"""; e.copy(details = d, hash = AuditChain.hash(e.prevHash, e.seq, e.time, e.type, d)) }
        assertEquals(AuditVerdict.Broken(4, "not linked to the entry before it"), log(f, cipher).read().verdict)
        // A deleted line, two swapped lines and a duplicated line break the sequence.
        val del = fresh(); del.files[1]!!.removeAt(1)
        assertTrue(log(del, cipher).read().verdict is AuditVerdict.Broken)
        val swap = fresh(); swap.files[1]!!.let { val t = it[1]; it[1] = it[2]; it[2] = t }
        assertEquals(2L, (log(swap, cipher).read().verdict as AuditVerdict.Broken).seq)
        val dup = fresh(); dup.files[1]!!.add(dup.files[1]!![4])
        assertEquals(6L, (log(dup, cipher).read().verdict as AuditVerdict.Broken).seq)
        // A line that can't be decrypted: "unreadable at" the number it should have had.
        val bad = fresh(); bad.files[1]!![3] = "zz-not-a-line"
        assertEquals(AuditVerdict.Unreadable(4), log(bad, cipher).read().verdict)
        // The first entry must start from 64 zeros.
        val first = fresh(); rewrite(first, 0) { e -> e.copy(prevHash = "1".repeat(64), hash = AuditChain.hash("1".repeat(64), 1, e.time, e.type, e.details)) }
        assertEquals(1L, (log(first, cipher).read().verdict as AuditVerdict.Broken).seq)
    }

    @Test
    fun rotation_startsANewFileWithALogRotatedEntry() {
        val files = MemFiles(); val cipher = FakeCipher(7)
        val log = log(files, cipher, rotate = 2_000)
        val entries = (1..60).map { log.append(AuditType.IMPORT_ACCEPTED, mapOf("i" to it)) }
        assertTrue("several files", files.files.size >= 3)
        for ((n, lines) in files.files) {
            if (n > 1) {
                val first = AuditChain.decode(cipher.open(lines.first())!!.decodeToString())!!
                assertEquals(AuditType.LOG_ROTATED, first.type)
                assertTrue(first.details.contains("\"previousFile\":\"audit-${n - 1}.log\""))
                assertTrue(first.details.contains(first.prevHash))
            }
            val longest = files.files.values.flatten().maxOf { it.length + 1L }
            if (n < files.files.lastKey()) assertTrue("file $n rotated once over the limit", files.sizeBytes(n) <= 2_000 + longest)
        }
        val r = log.read()
        assertEquals(AuditVerdict.Intact, r.verdict)
        assertEquals((1L..r.entries.size).toList(), r.entries.map { it.seq })   // seq continues across files
        assertEquals(entries.last(), r.entries.last())
        // Exactly at the limit nothing rotates; one byte over, the next append rotates.
        val f2 = MemFiles(); val l2 = log(f2, cipher, rotate = 10_000)
        l2.append(AuditType.DATA_RESET); val size = f2.sizeBytes(1)
        val l3 = log(f2, cipher, rotate = size); l3.append(AuditType.DATA_RESET); assertEquals(1, f2.files.size)
        val l4 = log(f2, cipher, rotate = f2.sizeBytes(1) - 1); l4.append(AuditType.DATA_RESET); assertEquals(2, f2.files.size)
        // A forged rotation entry that names another hash is caught.
        val forged = AuditChain.next(r.entries.last(), AuditType.LOG_ROTATED, AuditChain.details(mapOf("previousFile" to "x", "lastHash" to "0".repeat(64))), 1L)
        assertTrue(AuditChain.verify(r.entries + forged) is AuditVerdict.Broken)
    }

    @Test
    fun keyLoss_keepsTheOldFilesAndStartsANewChainWithKeyLoss() {
        val files = MemFiles(); val cipher = FakeCipher(1)
        val log = log(files, cipher)
        repeat(3) { log.append(AuditType.IMPORT_ACCEPTED) }
        cipher.key = 2                                    // the keystore key is gone; a new key is in use
        val e = log.append(AuditType.DATA_RESET, mapOf("why" to "start empty"))
        assertEquals(1, files.aside.size); assertEquals(3, files.aside[0].second[1]!!.size)   // old lines kept, set aside
        val r = log.read()
        assertEquals(AuditVerdict.Intact, r.verdict)
        assertEquals(listOf(AuditType.KEY_LOSS, AuditType.DATA_RESET), r.entries.map { it.type })
        assertEquals("""{"log":"previous audit files unreadable"}""", r.entries[0].details)
        assertEquals(AuditChain.GENESIS, r.entries[0].prevHash); assertEquals(2L, e.seq)
        // A torn last line (app killed while writing) is not a key loss: the chain continues and reading reports it.
        val f2 = MemFiles(); val c2 = FakeCipher(9); val l2 = log(f2, c2)
        repeat(3) { l2.append(AuditType.IMPORT_ACCEPTED) }
        f2.files[1]!!.add("09ab")                           // half a line
        val after = l2.append(AuditType.IMPORT_REJECTED)
        assertEquals(4L, after.seq); assertTrue(f2.aside.isEmpty())
        assertEquals(AuditVerdict.Unreadable(4), l2.read().verdict)
    }

    @Test
    fun details_areLimitedTo1024Bytes_andEveryValueKindIsEncoded() {
        val big = AuditChain.details(mapOf("note" to "x".repeat(5000), "emoji" to "🌱".repeat(800)))
        assertTrue(big.encodeToByteArray().size <= AuditChain.MAX_DETAILS_BYTES)
        assertTrue(MiniJson.parse(big) is Map<*, *>)
        val d = AuditChain.details(mapOf("s" to "a\"b\\c\n", "i" to 3, "l" to 4L, "f" to 1.5f, "d" to 2.25, "b" to true, "n" to null, "nan" to Double.NaN))
        val m = MiniJson.parse(d) as Map<*, *>
        assertEquals("a\"b\\c\n", m["s"]); assertEquals(true, m["b"]); assertEquals(null, m["n"]); assertEquals(null, m["nan"])
        assertEquals(1.5, (m["f"] as Number).toDouble(), 0.0)
        assertEquals("{}", AuditChain.details(emptyMap()))
        // An entry with details over the limit (written by something else) fails verification.
        val e = AuditChain.next(null, AuditType.DATA_RESET, "{\"x\":\"" + "y".repeat(1100) + "\"}", 1L)
        assertTrue(AuditChain.verify(listOf(e)) is AuditVerdict.Broken)
        // Malformed lines don't decode.
        for (bad in listOf("", "{}", "[1]", "{\"seq\":\"1\"}", "{\"seq\":1,\"type\":\"NOPE\",\"time\":\"t\",\"details\":\"{}\",\"prevHash\":\"a\",\"hash\":\"b\"}"))
            assertEquals(bad, null, AuditChain.decode(bad))
    }

    @Test
    fun randomAppendsTamperAndKeyChanges_neverThrow_andAnUntouchedLogIsAlwaysIntact() {
        val rnd = Random(77)
        repeat(40) { run ->
            val files = MemFiles(); val cipher = FakeCipher(rnd.nextInt(1, 255))
            val log = log(files, cipher, rotate = rnd.nextLong(200, 5000))
            repeat(rnd.nextInt(1, 80)) {
                if (rnd.nextInt(30) == 0) cipher.key = rnd.nextInt(1, 255)   // key lost before this write
                val type = AuditType.entries[rnd.nextInt(AuditType.entries.size)]
                log.append(type, mapOf("r" to rnd.nextInt(), "t" to "x".repeat(rnd.nextInt(0, 40))))
            }
            assertEquals("run $run", AuditVerdict.Intact, log.read().verdict)
            // Flip one character of one stored line: never intact afterwards, never an exception.
            val n = files.files.keys.random(rnd); val lines = files.files[n]!!; val i = rnd.nextInt(lines.size)
            val line = lines[i]; val p = rnd.nextInt(2, line.length)
            lines[i] = line.substring(0, p) + (if (line[p] == '0') '1' else '0') + line.substring(p + 1)
            assertNotEquals("run $run", AuditVerdict.Intact, log.read().verdict)
        }
    }
}
