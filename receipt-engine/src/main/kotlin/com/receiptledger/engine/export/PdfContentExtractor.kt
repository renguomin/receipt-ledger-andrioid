package com.receiptledger.engine.export

import java.io.ByteArrayOutputStream
import java.util.zip.Inflater

data class PdfPageContent(
    val textTokens: List<String>,
    val images: List<ByteArray>,
)

data class PdfContents(
    val textTokens: List<String>,
    val text: String,
    val images: List<ByteArray>,
    val pages: List<PdfPageContent> = emptyList(),
)

object PdfContentExtractor {
    private val lengthInDict = Regex("""/Length\s+(\d+)(?!\s+\d+\s+R)""")
    private val streamWord = "stream".toByteArray(Charsets.ISO_8859_1)

    fun extract(pdf: ByteArray): PdfContents {
        val streams = extractStreams(pdf)
        val tokens = ArrayList<String>()
        val streamImages = ArrayList<ByteArray>()
        val pages = ArrayList<PdfPageContent>()
        var pageTokens: List<String>? = null
        var pageImages = ArrayList<ByteArray>()
        for (stream in streams) {
            val payload = if (stream.flate) inflate(stream.data) ?: stream.data else stream.data
            if (isImageBytes(payload) || stream.imageXObject) {
                val jpeg = payload.takeIf { isImageBytes(it) } ?: stream.data.takeIf { isImageBytes(it) }
                if (jpeg != null) {
                    streamImages += jpeg
                    pageImages += jpeg
                }
            } else if (stream.binaryResource) {
                continue
            } else {
                val strings = extractStrings(payload)
                tokens += strings
                pageTokens?.let { pages += PdfPageContent(it, pageImages) }
                pageTokens = strings
                pageImages = ArrayList()
            }
        }
        pageTokens?.let { pages += PdfPageContent(it, pageImages) }
        val images = if (streamImages.isNotEmpty()) {
            streamImages
        } else {
            extractJpegsByMagic(pdf)
        }
        val unique = distinctImages(images)
        val allTokens = tokens.ifEmpty {
            streams.filterNot { it.imageXObject || it.binaryResource }
                .flatMap { extractStrings(it.data) }
        }
        return PdfContents(
            textTokens = allTokens,
            text = allTokens.joinToString("\n"),
            images = unique,
            pages = pages,
        )
    }

    /**
     * Logos and icons are small; receipt photos are not. The floor is absolute rather than
     * relative to the largest image, because real receipt photos vary by several times in size
     * and dropping one shifts every pairing after it.
     */
    const val MIN_PHOTO_BYTES = 8_192

    fun keepReceiptPhotos(images: List<ByteArray>): List<ByteArray> {
        if (images.isEmpty()) return emptyList()
        val kept = images.filter { it.size >= MIN_PHOTO_BYTES }
        return kept.ifEmpty { listOf(images.maxBy { it.size }) }
    }

    fun photosForReceipts(images: List<ByteArray>, receiptCount: Int): List<ByteArray?> {
        if (receiptCount <= 0) return emptyList()
        if (images.size == receiptCount) return images.map { it }
        val kept = keepReceiptPhotos(images)
        if (kept.size == receiptCount) return kept.map { it }
        return List(receiptCount) { index -> kept.getOrNull(index) ?: images.getOrNull(index) }
    }

    internal fun extractJpegsByMagic(pdf: ByteArray): List<ByteArray> {
        val found = ArrayList<ByteArray>()
        var i = 0
        while (i < pdf.size - 1) {
            if (pdf[i] == 0xFF.toByte() && pdf[i + 1] == 0xD8.toByte()) {
                val end = jpegEndExclusive(pdf, i)
                if (end != null && end > i + 2) {
                    found += pdf.copyOfRange(i, end)
                    i = end
                    continue
                }
            }
            i += 1
        }
        return found
    }

    internal fun jpegEndExclusive(bytes: ByteArray, start: Int): Int? {
        if (start < 0 || start + 1 >= bytes.size) return null
        if (bytes[start] != 0xFF.toByte() || bytes[start + 1] != 0xD8.toByte()) return null
        return parseJpegEnd(bytes, start) ?: firstEoi(bytes, start + 2)
    }

    private fun parseJpegEnd(bytes: ByteArray, start: Int): Int? {
        var i = start + 2
        while (i + 1 < bytes.size) {
            if (bytes[i] != 0xFF.toByte()) return null
            var markerAt = i + 1
            while (markerAt < bytes.size && bytes[markerAt] == 0xFF.toByte()) markerAt += 1
            if (markerAt >= bytes.size) return null
            val marker = bytes[markerAt].toInt() and 0xFF
            i = markerAt + 1
            when (marker) {
                0xD9 -> return i
                0xD8 -> continue
                in 0xD0..0xD7, 0x01 -> continue
                0xDA -> {
                    val len = u16(bytes, i) ?: return null
                    if (len < 2) return null
                    return scanEoi(bytes, i + len)
                }
                else -> {
                    val len = u16(bytes, i) ?: return null
                    if (len < 2) return null
                    i += len
                }
            }
        }
        return null
    }

    private fun scanEoi(bytes: ByteArray, from: Int): Int? {
        var i = from
        while (i + 1 < bytes.size) {
            if (bytes[i] == 0xFF.toByte()) {
                val next = bytes[i + 1].toInt() and 0xFF
                when (next) {
                    0x00 -> i += 2
                    in 0xD0..0xD7 -> i += 2
                    0xD9 -> return i + 2
                    else -> i += 1
                }
            } else {
                i += 1
            }
        }
        return null
    }

    private fun firstEoi(bytes: ByteArray, from: Int): Int? {
        var i = from
        while (i + 1 < bytes.size) {
            if (bytes[i] == 0xFF.toByte() && bytes[i + 1] == 0xD9.toByte()) return i + 2
            i += 1
        }
        return null
    }

    private fun u16(bytes: ByteArray, at: Int): Int? {
        if (at + 1 >= bytes.size) return null
        return ((bytes[at].toInt() and 0xFF) shl 8) or (bytes[at + 1].toInt() and 0xFF)
    }

    internal fun unescape(text: String): String = buildString {
        var i = 0
        while (i < text.length) {
            if (text[i] == '\\' && i + 1 < text.length) {
                append(text[i + 1])
                i += 2
            } else {
                append(text[i])
                i += 1
            }
        }
    }

    /**
     * Hand-rolled instead of a regex. A repeated alternation group such as `((?:\\.|[^\\)])*)`
     * recurses once per repetition in java.util.regex, so a large stream overflows the stack
     * before it ever fails to match. Scanning is linear and cannot blow up on odd input.
     *
     * Every literal string in a content stream is text to be shown, so all of them are returned
     * rather than only those directly followed by a Tj operator.
     */
    internal fun extractStrings(content: ByteArray): List<String> {
        val text = content.toString(Charsets.ISO_8859_1)
        val strings = ArrayList<String>()
        var i = 0
        while (i < text.length) {
            if (text[i] != '(') {
                i += 1
                continue
            }
            val builder = StringBuilder()
            var depth = 1
            var j = i + 1
            while (j < text.length && depth > 0) {
                val c = text[j]
                when {
                    c == '\\' && j + 1 < text.length -> {
                        builder.append(text[j + 1])
                        j += 2
                    }
                    c == '(' -> {
                        depth += 1
                        builder.append(c)
                        j += 1
                    }
                    c == ')' -> {
                        depth -= 1
                        if (depth > 0) builder.append(c)
                        j += 1
                    }
                    else -> {
                        builder.append(c)
                        j += 1
                    }
                }
            }
            if (depth == 0) {
                strings += builder.toString()
                i = j
            } else {
                // Unbalanced, so this paren was never the start of a string.
                i += 1
            }
        }
        return strings
    }

    private fun extractStreams(pdf: ByteArray): List<PdfStream> {
        val streams = ArrayList<PdfStream>()
        var from = 0
        while (true) {
            val streamAt = indexOf(pdf, streamWord, from) ?: break
            if (!isStreamKeyword(pdf, streamAt)) {
                from = streamAt + 6
                continue
            }
            val lookbackStart = (streamAt - 500).coerceAtLeast(0)
            val lookback = pdf.copyOfRange(lookbackStart, streamAt).toString(Charsets.ISO_8859_1)
            val dictStart = lookback.lastIndexOf("<<")
            val dict = if (dictStart >= 0) lookback.substring(dictStart) else lookback
            val length = lengthInDict.findAll(dict).lastOrNull()?.groupValues?.get(1)?.toIntOrNull()
            if (length == null || length < 0) {
                from = streamAt + 6
                continue
            }
            var dataStart = streamAt + 6
            if (dataStart < pdf.size && pdf[dataStart] == '\r'.code.toByte()) dataStart += 1
            if (dataStart < pdf.size && pdf[dataStart] == '\n'.code.toByte()) dataStart += 1
            if (dataStart + length > pdf.size) {
                from = streamAt + 6
                continue
            }
            val data = pdf.copyOfRange(dataStart, dataStart + length)
            val dct = dict.contains("/DCTDecode") || dict.contains("/DCT")
            val flate = dict.contains("/FlateDecode") || dict.contains("/Flate")
            val image = dict.contains("/Subtype /Image") || dict.contains("/Subtype/Image")
            // Any image XObject counts, not just DCT. A Flate-compressed image inflates to raw
            // pixels, which is not a JPEG, and would otherwise be scanned as if it were text.
            streams += PdfStream(dct || image, flate, isBinaryResource(dict), data)
            from = dataStart + length
        }
        return streams
    }

    private fun isStreamKeyword(pdf: ByteArray, at: Int): Boolean {
        if (at > 0) {
            val prev = pdf[at - 1].toInt().toChar()
            if (prev.isLetter() || prev.isDigit()) return false
        }
        val after = at + streamWord.size
        if (after < pdf.size) {
            val next = pdf[after].toInt().toChar()
            if (next.isLetter() || next.isDigit()) return false
        }
        return true
    }

    private fun inflate(data: ByteArray): ByteArray? {
        val inflater = Inflater()
        return try {
            inflater.setInput(data)
            val out = ByteArrayOutputStream()
            val buf = ByteArray(4096)
            while (!inflater.finished()) {
                val n = inflater.inflate(buf)
                if (n == 0) {
                    if (inflater.needsInput() || inflater.needsDictionary()) break
                    if (inflater.finished()) break
                    break
                }
                out.write(buf, 0, n)
            }
            out.toByteArray().takeIf { it.isNotEmpty() }
        } catch (_: Exception) {
            null
        } finally {
            inflater.end()
        }
    }

    private fun isImageBytes(bytes: ByteArray): Boolean =
        PdfExportLogic.isJpeg(bytes) || PdfExportLogic.isPng(bytes)

    private fun distinctImages(images: List<ByteArray>): List<ByteArray> {
        val seen = HashSet<String>()
        return images.filter { image ->
            val key = "${image.size}:${image.contentHashCode()}"
            seen.add(key)
        }
    }

    private fun indexOf(data: ByteArray, needle: ByteArray, from: Int): Int? {
        if (needle.isEmpty() || from >= data.size) return null
        val lastStart = data.size - needle.size
        var i = from
        while (i <= lastStart) {
            var match = true
            var j = 0
            while (j < needle.size) {
                if (data[i + j] != needle[j]) {
                    match = false
                    break
                }
                j += 1
            }
            if (match) return i
            i += 1
        }
        return null
    }

    /**
     * Embedded fonts and metadata inflate to binary that holds no page text, but does hold
     * parenthesised runs that would otherwise be read as words on the page.
     */
    private fun isBinaryResource(dict: String): Boolean = binaryResourceKeys.any { dict.contains(it) }

    private val binaryResourceKeys = listOf(
        "/Length1",
        "/FontFile",
        "/Type1C",
        "/CIDFontType0C",
        "/OpenType",
        "/Type /Metadata",
        "/Type/Metadata",
        "/Type /ObjStm",
        "/Type/ObjStm",
        "/Type /XRef",
        "/Type/XRef",
        "/Type /CMap",
        "/Type/CMap",
    )

    private data class PdfStream(
        val imageXObject: Boolean,
        val flate: Boolean,
        val binaryResource: Boolean,
        val data: ByteArray,
    )
}
