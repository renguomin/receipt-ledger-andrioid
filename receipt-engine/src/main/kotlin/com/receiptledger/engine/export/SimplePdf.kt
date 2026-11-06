package com.receiptledger.engine.export

import java.io.ByteArrayOutputStream
import java.util.Locale

enum class PdfImageEncoding {
    JPEG,
    ONE_BIT_FLATE,
}

data class PackedPdfImage(
    val width: Int,
    val height: Int,
    val bytes: ByteArray,
    val encoding: PdfImageEncoding = PdfImageEncoding.JPEG,
)

class SimplePdf(
    private val pageWidth: Float = 612f,
    private val pageHeight: Float = 792f,
) {
    private val pages = mutableListOf(Page())
    private val current get() = pages.last()

    fun newPage() {
        pages += Page()
    }

    fun text(
        x: Float,
        yFromTop: Float,
        size: Float,
        text: String,
        bold: Boolean = false,
        gray: Float = 0f,
        align: Align = Align.LEFT,
    ) {
        val width = PdfFonts.width(text, size)
        val left = when (align) {
            Align.LEFT -> x
            Align.RIGHT -> x - width
            Align.CENTER -> x - width / 2f
        }
        val y = pageHeight - yFromTop
        val font = if (bold) "/F2" else "/F1"
        if (gray != 0f) current.content.append(fmt(gray)).append(" g\n")
        current.content.append("BT ").append(font).append(' ').append(fmt(size))
            .append(" Tf 1 0 0 1 ").append(fmt(left)).append(' ').append(fmt(y))
            .append(" Tm (").append(pdfEscape(text)).append(") Tj ET\n")
        if (gray != 0f) current.content.append("0 g\n")
    }

    fun strokeRect(left: Float, top: Float, right: Float, bottom: Float, gray: Float = 0.7f) {
        val height = (bottom - top).coerceAtLeast(0f)
        val width = (right - left).coerceAtLeast(0f)
        val y = pageHeight - bottom
        current.content.append(fmt(gray)).append(" G 0.8 w ")
            .append(fmt(left)).append(' ').append(fmt(y)).append(' ')
            .append(fmt(width)).append(' ').append(fmt(height)).append(" re S 0 G\n")
    }

    fun fillRect(left: Float, top: Float, right: Float, bottom: Float, gray: Float = 0.94f) {
        val height = (bottom - top).coerceAtLeast(0f)
        val width = (right - left).coerceAtLeast(0f)
        val y = pageHeight - bottom
        current.content.append(fmt(gray)).append(" g ")
            .append(fmt(left)).append(' ').append(fmt(y)).append(' ')
            .append(fmt(width)).append(' ').append(fmt(height)).append(" re f 0 g\n")
    }

    fun verticalLine(x: Float, top: Float, bottom: Float, gray: Float = 0.7f) {
        val y1 = pageHeight - top
        val y2 = pageHeight - bottom
        current.content.append(fmt(gray)).append(" G 0.8 w ")
            .append(fmt(x)).append(' ').append(fmt(y1)).append(" m ")
            .append(fmt(x)).append(' ').append(fmt(y2)).append(" l S 0 G\n")
    }

    fun image(left: Float, top: Float, destWidth: Float, destHeight: Float, image: PackedPdfImage) {
        val name = "Im${current.images.size}"
        current.images += NamedImage(name, image)
        val y = pageHeight - top - destHeight
        current.content.append("q ").append(fmt(destWidth)).append(" 0 0 ").append(fmt(destHeight))
            .append(' ').append(fmt(left)).append(' ').append(fmt(y))
            .append(" cm /").append(name).append(" Do Q\n")
    }

    fun toByteArray(): ByteArray {
        val out = ByteArrayOutputStream()
        val offsets = HashMap<Int, Int>()
        fun write(text: String) {
            out.write(text.toByteArray(Charsets.US_ASCII))
        }
        fun mark(id: Int) {
            offsets[id] = out.size()
        }

        write("%PDF-1.4\n%")
        out.write(byteArrayOf(0xE2.toByte(), 0xE3.toByte(), 0xCF.toByte(), 0xD3.toByte()))
        write("\n")

        data class Refs(val page: Int, val contents: Int, val images: List<Int>)
        var next = 5
        val refs = pages.map { page ->
            Refs(next++, next++, List(page.images.size) { next++ })
        }
        val lastId = next - 1

        mark(1)
        write("1 0 obj\n<< /Type /Catalog /Pages 2 0 R >>\nendobj\n")
        mark(2)
        val kids = refs.joinToString(" ") { "${it.page} 0 R" }
        write("2 0 obj\n<< /Type /Pages /Count ${pages.size} /Kids [$kids] >>\nendobj\n")
        mark(3)
        write("3 0 obj\n<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica >>\nendobj\n")
        mark(4)
        write("4 0 obj\n<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica-Bold >>\nendobj\n")

        pages.zip(refs).forEach { (page, ref) ->
            val xobjects = page.images.mapIndexed { i, img ->
                "/${img.name} ${ref.images[i]} 0 R"
            }.joinToString(" ")
            val resources = buildString {
                append("<< /Font << /F1 3 0 R /F2 4 0 R >>")
                if (xobjects.isNotEmpty()) {
                    append(" /XObject << ").append(xobjects).append(" >>")
                }
                append(" >>")
            }
            mark(ref.page)
            write(
                "${ref.page} 0 obj\n<< /Type /Page /Parent 2 0 R /MediaBox [0 0 ${fmt(pageWidth)} ${fmt(pageHeight)}] " +
                    "/Contents ${ref.contents} 0 R /Resources $resources >>\nendobj\n",
            )
            val content = page.content.toString().toByteArray(Charsets.US_ASCII)
            mark(ref.contents)
            write("${ref.contents} 0 obj\n<< /Length ${content.size} >>\nstream\n")
            out.write(content)
            write("\nendstream\nendobj\n")
            page.images.zip(ref.images).forEach { (named, id) ->
                mark(id)
                val bytes = named.image.bytes
                write("$id 0 obj\n${named.image.dictionary(bytes.size)}\nstream\n")
                out.write(bytes)
                write("\nendstream\nendobj\n")
            }
        }

        val xrefAt = out.size()
        write("xref\n0 ${lastId + 1}\n")
        write("0000000000 65535 f \n")
        for (id in 1..lastId) {
            val offset = offsets.getValue(id)
            write(String.format(Locale.US, "%010d 00000 n \n", offset))
        }
        write("trailer\n<< /Size ${lastId + 1} /Root 1 0 R >>\nstartxref\n$xrefAt\n%%EOF\n")
        return out.toByteArray()
    }

    enum class Align { LEFT, RIGHT, CENTER }

    private class Page {
        val content = StringBuilder()
        val images = ArrayList<NamedImage>()
    }

    private data class NamedImage(val name: String, val image: PackedPdfImage)
}

private fun PackedPdfImage.dictionary(length: Int): String = when (encoding) {
    PdfImageEncoding.JPEG ->
        "<< /Type /XObject /Subtype /Image /Width $width /Height $height " +
            "/ColorSpace /DeviceRGB /BitsPerComponent 8 /Filter /DCTDecode /Length $length >>"
    PdfImageEncoding.ONE_BIT_FLATE ->
        "<< /Type /XObject /Subtype /Image /Width $width /Height $height " +
            "/ColorSpace /DeviceGray /BitsPerComponent 1 /Filter /FlateDecode /Length $length >>"
}

private fun fmt(value: Float): String = String.format(Locale.US, "%.2f", value)

private fun pdfEscape(text: String): String = buildString {
    for (ch in text) {
        when (ch) {
            '(', ')', '\\' -> {
                append('\\')
                append(ch)
            }
            in ' '..'~' -> append(ch)
            '—' -> append('-')
            else -> append('?')
        }
    }
}
