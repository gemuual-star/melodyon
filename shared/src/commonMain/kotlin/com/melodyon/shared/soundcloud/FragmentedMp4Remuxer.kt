package com.melodyon.shared.soundcloud

/**
 * Turns an HLS fMP4 audio stream (init segment + media segments) into a regular, non-fragmented M4A.
 *
 * Plain concatenation of the segments is a fragmented MP4 with one index per segment: players then
 * report only the first segment's length as the track duration and cannot seek. Here every segment's
 * samples are written as their own `mdat`, and [finish] appends a `moov` with a full sample table,
 * so the output stays streamable to an append-only sink and has the real duration.
 */
class FragmentedMp4Remuxer(initSegment: ByteArray) {
    private val trackId: Long
    private val timescale: Long
    private val stsd: ByteArray
    private val defaultSampleDuration: Long
    private val defaultSampleSize: Long

    private val sampleSizes = IntList()
    private val sampleDurations = IntList()
    private val chunkSampleCounts = IntList()
    private val chunkOffsets = ArrayList<Long>()
    private var position = 0L

    init {
        val moov = initSegment.boxes(0, initSegment.size).firstOrNull { it.type == "moov" } ?: fail("Init segment has no moov")
        val moovBoxes = initSegment.boxes(moov.bodyStart, moov.end)
        var foundTrackId = -1L
        var foundTimescale = 0L
        var foundStsd: ByteArray? = null
        for (trak in moovBoxes.filter { it.type == "trak" }) {
            val trakBoxes = initSegment.boxes(trak.bodyStart, trak.end)
            val tkhd = trakBoxes.firstOrNull { it.type == "tkhd" } ?: continue
            val mdia = trakBoxes.firstOrNull { it.type == "mdia" } ?: continue
            val mdiaBoxes = initSegment.boxes(mdia.bodyStart, mdia.end)
            val hdlr = mdiaBoxes.firstOrNull { it.type == "hdlr" } ?: continue
            if (initSegment.type(hdlr.bodyStart + 8) != "soun") continue
            val mdhd = mdiaBoxes.firstOrNull { it.type == "mdhd" } ?: continue
            val minf = mdiaBoxes.firstOrNull { it.type == "minf" } ?: continue
            val stbl = initSegment.boxes(minf.bodyStart, minf.end).firstOrNull { it.type == "stbl" } ?: continue
            val stsdBox = initSegment.boxes(stbl.bodyStart, stbl.end).firstOrNull { it.type == "stsd" } ?: continue
            val tkhdVersion = initSegment[tkhd.bodyStart].toInt()
            foundTrackId = initSegment.u32(tkhd.bodyStart + if (tkhdVersion == 1) 20 else 12)
            val mdhdVersion = initSegment[mdhd.bodyStart].toInt()
            foundTimescale = initSegment.u32(mdhd.bodyStart + if (mdhdVersion == 1) 20 else 12)
            foundStsd = initSegment.copyOfRange(stsdBox.start, stsdBox.end)
            break
        }
        if (foundStsd == null || foundTimescale <= 0L) fail("Init segment has no audio track")
        trackId = foundTrackId
        timescale = foundTimescale
        stsd = foundStsd
        val trex =
            moovBoxes
                .firstOrNull { it.type == "mvex" }
                ?.let { mvex -> initSegment.boxes(mvex.bodyStart, mvex.end) }
                ?.firstOrNull { it.type == "trex" && initSegment.u32(it.bodyStart + 4) == trackId }
        defaultSampleDuration = trex?.let { initSegment.u32(it.bodyStart + 12) } ?: 0L
        defaultSampleSize = trex?.let { initSegment.u32(it.bodyStart + 16) } ?: 0L
    }

    /** Bytes to append to the output file for one media segment (the file header precedes the first one). */
    fun append(segment: ByteArray): ByteArray {
        val out = Out(segment.size + 64)
        if (position == 0L) {
            out.box("ftyp") {
                ascii("M4A ")
                u32(0)
                ascii("M4A ")
                ascii("mp42")
                ascii("isom")
            }
        }
        var samples = 0
        for (moof in segment.boxes(0, segment.size).filter { it.type == "moof" }) {
            for (traf in segment.boxes(moof.bodyStart, moof.end).filter { it.type == "traf" }) {
                samples += appendTrackFragment(segment, moof.start, traf, out)
            }
        }
        if (samples == 0) fail("Media segment has no audio samples")
        val bytes = out.toByteArray()
        position += bytes.size
        return bytes
    }

    private fun appendTrackFragment(segment: ByteArray, moofStart: Int, traf: Box, out: Out): Int {
        val boxes = segment.boxes(traf.bodyStart, traf.end)
        val tfhd = boxes.firstOrNull { it.type == "tfhd" } ?: fail("Track fragment has no tfhd")
        val tfhdFlags = segment.u32(tfhd.bodyStart) and 0xFFFFFF
        if (segment.u32(tfhd.bodyStart + 4) != trackId) return 0
        var p = tfhd.bodyStart + 8
        var base = moofStart.toLong()
        var defaultDuration = defaultSampleDuration
        var defaultSize = defaultSampleSize
        if (tfhdFlags and 0x1 != 0L) {
            base = segment.u64(p)
            p += 8
        }
        if (tfhdFlags and 0x2 != 0L) p += 4
        if (tfhdFlags and 0x8 != 0L) {
            defaultDuration = segment.u32(p)
            p += 4
        }
        if (tfhdFlags and 0x10 != 0L) defaultSize = segment.u32(p)

        var total = 0
        var nextData = -1L
        for (trun in boxes.filter { it.type == "trun" }) {
            val flags = segment.u32(trun.bodyStart) and 0xFFFFFF
            val count = segment.u32(trun.bodyStart + 4).toInt()
            var q = trun.bodyStart + 8
            var dataStart = nextData
            if (flags and 0x1 != 0L) {
                dataStart = base + segment.u32(q).toInt()
                q += 4
            }
            if (flags and 0x4 != 0L) q += 4
            if (count <= 0) continue
            if (dataStart < 0) fail("Track run has no data offset")
            var runBytes = 0L
            val firstSample = sampleSizes.size
            repeat(count) {
                var duration = defaultDuration
                var size = defaultSize
                if (flags and 0x100 != 0L) {
                    duration = segment.u32(q)
                    q += 4
                }
                if (flags and 0x200 != 0L) {
                    size = segment.u32(q)
                    q += 4
                }
                if (flags and 0x400 != 0L) q += 4
                if (flags and 0x800 != 0L) q += 4
                if (size <= 0L || duration <= 0L || q > trun.end) fail("Broken track run")
                sampleSizes.add(size.toInt())
                sampleDurations.add(duration.toInt())
                runBytes += size
            }
            if (dataStart + runBytes > segment.size || runBytes > Int.MAX_VALUE - 8) fail("Track run points outside the segment")
            check(sampleSizes.size - firstSample == count)
            out.u32(runBytes + 8)
            out.ascii("mdat")
            chunkOffsets.add(position + out.size)
            chunkSampleCounts.add(count)
            out.bytes(segment, dataStart.toInt(), runBytes.toInt())
            nextData = dataStart + runBytes
            total += count
        }
        return total
    }

    /** The `moov` box that completes the file; call once after the last [append]. */
    fun finish(): ByteArray {
        if (sampleSizes.size == 0) fail("No audio samples")
        var mediaDuration = 0L
        for (i in 0 until sampleDurations.size) mediaDuration += sampleDurations[i]
        val movieDuration = mediaDuration * MOVIE_TIMESCALE / timescale
        val out = Out(sampleSizes.size * 4 + chunkOffsets.size * 12 + stsd.size + 1024)
        out.box("moov") {
            box("mvhd") {
                u32(0)
                u32(0)
                u32(0)
                u32(MOVIE_TIMESCALE)
                u32(movieDuration)
                u32(0x00010000)
                u16(0x0100)
                zeros(10)
                matrix()
                zeros(24)
                u32(2)
            }
            box("trak") {
                box("tkhd") {
                    u32(0x7)
                    u32(0)
                    u32(0)
                    u32(1)
                    u32(0)
                    u32(movieDuration)
                    zeros(8)
                    u16(0)
                    u16(0)
                    u16(0x0100)
                    u16(0)
                    matrix()
                    u32(0)
                    u32(0)
                }
                box("mdia") {
                    box("mdhd") {
                        u32(0x01000000)
                        u64(0)
                        u64(0)
                        u32(timescale)
                        u64(mediaDuration)
                        u16(0x55C4)
                        u16(0)
                    }
                    box("hdlr") {
                        u32(0)
                        u32(0)
                        ascii("soun")
                        zeros(12)
                        ascii("SoundHandler")
                        zeros(1)
                    }
                    box("minf") {
                        box("smhd") {
                            u32(0)
                            u32(0)
                        }
                        box("dinf") {
                            box("dref") {
                                u32(0)
                                u32(1)
                                box("url ") { u32(1) }
                            }
                        }
                        box("stbl") {
                            bytes(stsd, 0, stsd.size)
                            box("stts") { runLengths(sampleDurations, withFirstIndex = false) }
                            box("stsc") { runLengths(chunkSampleCounts, withFirstIndex = true) }
                            box("stsz") {
                                u32(0)
                                u32(0)
                                u32(sampleSizes.size.toLong())
                                for (i in 0 until sampleSizes.size) u32(sampleSizes[i].toLong())
                            }
                            val wide = chunkOffsets.last() > 0xFFFFFFFFL
                            box(if (wide) "co64" else "stco") {
                                u32(0)
                                u32(chunkOffsets.size.toLong())
                                for (offset in chunkOffsets) if (wide) u64(offset) else u32(offset)
                            }
                        }
                    }
                }
            }
        }
        return out.toByteArray()
    }

    private companion object {
        const val MOVIE_TIMESCALE = 1000L

        fun fail(message: String): Nothing = throw SoundCloudException("Cannot rebuild audio file: $message")
    }
}

private class Box(val type: String, val start: Int, val bodyStart: Int, val end: Int)

private fun ByteArray.u32(at: Int): Long =
    ((this[at].toLong() and 0xFF) shl 24) or ((this[at + 1].toLong() and 0xFF) shl 16) or
        ((this[at + 2].toLong() and 0xFF) shl 8) or (this[at + 3].toLong() and 0xFF)

private fun ByteArray.u64(at: Int): Long = (u32(at) shl 32) or u32(at + 4)

private fun ByteArray.type(at: Int): String = CharArray(4) { (this[at + it].toInt() and 0xFF).toChar() }.concatToString()

private fun ByteArray.boxes(from: Int, to: Int): List<Box> {
    val result = ArrayList<Box>()
    var p = from
    while (p + 8 <= to) {
        var size = u32(p)
        var header = 8
        if (size == 1L) {
            if (p + 16 > to) throw SoundCloudException("Cannot rebuild audio file: broken box")
            size = u64(p + 8)
            header = 16
        } else if (size == 0L) {
            size = (to - p).toLong()
        }
        if (size < header || size > to - p) throw SoundCloudException("Cannot rebuild audio file: broken box")
        result += Box(type(p + 4), p, p + header, p + size.toInt())
        p += size.toInt()
    }
    return result
}

private class IntList {
    private var items = IntArray(1024)
    var size = 0
        private set

    fun add(value: Int) {
        if (size == items.size) items = items.copyOf(items.size * 2)
        items[size++] = value
    }

    operator fun get(index: Int): Int = items[index]
}

private class Out(capacity: Int) {
    private var buffer = ByteArray(capacity)
    var size = 0
        private set

    private fun reserve(count: Int) {
        if (size + count > buffer.size) buffer = buffer.copyOf(maxOf(buffer.size * 2, size + count))
    }

    fun u16(value: Int) {
        reserve(2)
        buffer[size++] = (value shr 8).toByte()
        buffer[size++] = value.toByte()
    }

    fun u32(value: Long) {
        reserve(4)
        buffer[size++] = (value shr 24).toByte()
        buffer[size++] = (value shr 16).toByte()
        buffer[size++] = (value shr 8).toByte()
        buffer[size++] = value.toByte()
    }

    fun u64(value: Long) {
        u32(value ushr 32)
        u32(value and 0xFFFFFFFFL)
    }

    fun zeros(count: Int) {
        reserve(count)
        size += count
    }

    fun ascii(text: String) {
        reserve(text.length)
        for (c in text) buffer[size++] = c.code.toByte()
    }

    fun bytes(source: ByteArray, offset: Int, length: Int) {
        reserve(length)
        source.copyInto(buffer, size, offset, offset + length)
        size += length
    }

    fun matrix() {
        u32(0x00010000)
        zeros(12)
        u32(0x00010000)
        zeros(12)
        u32(0x40000000)
    }

    /** `stts` (count, value) or `stsc` (first index, value, description 1) entries for runs of equal values. */
    fun runLengths(values: IntList, withFirstIndex: Boolean) {
        u32(0)
        val countAt = size
        u32(0)
        var entries = 0L
        var i = 0
        while (i < values.size) {
            var j = i
            while (j < values.size && values[j] == values[i]) j++
            if (withFirstIndex) {
                u32(i + 1L)
                u32(values[i].toLong())
                u32(1)
            } else {
                u32((j - i).toLong())
                u32(values[i].toLong())
            }
            entries++
            i = j
        }
        patchU32(countAt, entries)
    }

    private fun patchU32(at: Int, value: Long) {
        buffer[at] = (value shr 24).toByte()
        buffer[at + 1] = (value shr 16).toByte()
        buffer[at + 2] = (value shr 8).toByte()
        buffer[at + 3] = value.toByte()
    }

    inline fun box(type: String, body: Out.() -> Unit) {
        val start = size
        u32(0)
        ascii(type)
        body()
        patchU32(start, (size - start).toLong())
    }

    fun toByteArray(): ByteArray = buffer.copyOf(size)
}
