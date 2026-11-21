package com.melodyon.shared.soundcloud

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class FragmentedMp4RemuxerTest {
    @Test
    fun rebuildsOneSeekableFileWithFullDuration() {
        val first = listOf(ByteArray(3) { 1 }, ByteArray(5) { 2 })
        val second = listOf(ByteArray(4) { 3 }, ByteArray(2) { 4 }, ByteArray(6) { 5 })
        val remuxer = FragmentedMp4Remuxer(initSegment())
        val file = remuxer.append(mediaSegment(first)) + remuxer.append(mediaSegment(second)) + remuxer.finish()

        assertEquals("ftyp", file.typeAt(4))
        val top = file.children(0, file.size)
        assertEquals(listOf("ftyp", "mdat", "mdat", "moov"), top.map { it.first })
        // No fragment boxes survive: the player must see an ordinary MP4.
        assertEquals(-1, file.indexOfType("moof"))
        assertEquals(-1, file.indexOfType("sidx"))

        val moov = top.last().second
        val mvhd = file.find(moov, "mvhd")
        assertEquals(1000, file.int(mvhd.first + 12))
        assertEquals(5 * 1024 * 1000 / 44100, file.int(mvhd.first + 16))
        val mdhd = file.find(moov, "trak", "mdia", "mdhd")
        assertEquals(44100, file.int(mdhd.first + 20))
        assertEquals(5 * 1024, file.int(mdhd.first + 28))

        val stbl = file.find(moov, "trak", "mdia", "minf", "stbl")
        val stsd = file.find(stbl, "stsd")
        assertContentEquals(STSD_BODY, file.copyOfRange(stsd.first, stsd.second))
        val stts = file.find(stbl, "stts").first
        assertEquals(listOf(1, 5, 1024), listOf(file.int(stts + 4), file.int(stts + 8), file.int(stts + 12)))
        val stsz = file.find(stbl, "stsz").first
        assertEquals(5, file.int(stsz + 8))
        val sizes = List(5) { file.int(stsz + 12 + it * 4) }
        assertEquals(listOf(3, 5, 4, 2, 6), sizes)
        val stsc = file.find(stbl, "stsc").first
        assertEquals(listOf(2, 1, 2, 1, 2, 3, 1), List(7) { file.int(stsc + 4 + it * 4) })
        val stco = file.find(stbl, "stco").first
        assertEquals(2, file.int(stco + 4))
        var sample = 0
        listOf(first, second).forEachIndexed { chunk, payloads ->
            var offset = file.int(stco + 8 + chunk * 4)
            for (payload in payloads) {
                assertContentEquals(payload, file.copyOfRange(offset, offset + sizes[sample]))
                offset += sizes[sample++]
            }
        }
    }

    @Test
    fun rejectsBrokenInput() {
        assertFailsWith<SoundCloudException> { FragmentedMp4Remuxer(box("ftyp", "isom".encodeToByteArray())) }
        val remuxer = FragmentedMp4Remuxer(initSegment())
        assertFailsWith<SoundCloudException> { remuxer.append(box("styp", "msdh".encodeToByteArray())) }
        assertFailsWith<SoundCloudException> { remuxer.append(mediaSegment(listOf(ByteArray(4))).copyOf(60)) }
        assertFailsWith<SoundCloudException> { FragmentedMp4Remuxer(initSegment()).finish() }
    }

    @Test
    fun playlistWithMapIsRecognised() {
        assertEquals(true, hlsHasInitSegment("#EXTM3U\n#EXT-X-MAP:URI=\"init.mp4\"\n#EXTINF:10,\ns0.m4s"))
        assertEquals(false, hlsHasInitSegment("#EXTM3U\n#EXTINF:10,\ns0.mp3"))
    }

    private fun initSegment(): ByteArray {
        val tkhd = box("tkhd", int(0) + int(0) + int(0) + int(1) + ByteArray(72))
        val mdhd = box("mdhd", int(0) + int(0) + int(0) + int(44100) + int(0) + int(0))
        val hdlr = box("hdlr", int(0) + int(0) + "soun".encodeToByteArray() + ByteArray(13))
        val stbl = box("stbl", box("stsd", STSD_BODY))
        val trak = box("trak", tkhd + box("mdia", mdhd + hdlr + box("minf", stbl)))
        val mvex = box("mvex", box("trex", int(0) + int(1) + int(1) + int(1024) + int(0) + int(0)))
        return box("ftyp", "iso5".encodeToByteArray()) + box("moov", box("mvhd", ByteArray(100)) + trak + mvex)
    }

    /** One `moof` (default-base-is-moof, sizes in `trun`) followed by its `mdat`, as SoundCloud serves it. */
    private fun mediaSegment(samples: List<ByteArray>): ByteArray {
        fun moof(dataOffset: Int): ByteArray {
            val tfhd = box("tfhd", int(0x020000) + int(1))
            val tfdt = box("tfdt", int(0) + int(0))
            val trun = box("trun", int(0x201) + int(samples.size) + int(dataOffset) + samples.fold(ByteArray(0)) { all, s -> all + int(s.size) })
            return box("moof", box("mfhd", int(0) + int(1)) + box("traf", tfhd + tfdt + trun))
        }
        val moof = moof(moof(0).size + 8)
        return box("styp", "msdh".encodeToByteArray()) + moof + box("mdat", samples.fold(ByteArray(0)) { all, s -> all + s })
    }

    private fun box(type: String, body: ByteArray): ByteArray = int(body.size + 8) + type.encodeToByteArray() + body

    private fun int(value: Int): ByteArray = byteArrayOf((value shr 24).toByte(), (value shr 16).toByte(), (value shr 8).toByte(), value.toByte())

    private fun ByteArray.int(at: Int): Int =
        ((this[at].toInt() and 0xFF) shl 24) or ((this[at + 1].toInt() and 0xFF) shl 16) or ((this[at + 2].toInt() and 0xFF) shl 8) or (this[at + 3].toInt() and 0xFF)

    private fun ByteArray.typeAt(at: Int): String = decodeToString(at, at + 4)

    /** (type, body range) of the boxes between [from] and [to]. */
    private fun ByteArray.children(from: Int, to: Int): List<Pair<String, Pair<Int, Int>>> {
        val result = ArrayList<Pair<String, Pair<Int, Int>>>()
        var p = from
        while (p + 8 <= to) {
            val size = int(p)
            result += typeAt(p + 4) to (p + 8 to p + size)
            p += size
        }
        return result
    }

    private fun ByteArray.find(parent: Pair<Int, Int>, vararg path: String): Pair<Int, Int> =
        path.fold(parent) { range, type -> children(range.first, range.second).first { it.first == type }.second }

    private fun ByteArray.indexOfType(type: String): Int = (0..size - 4).firstOrNull { typeAt(it) == type } ?: -1

    private companion object {
        val STSD_BODY = ByteArray(24) { (it + 1).toByte() }
    }
}
