package com.receiptledger.engine

import com.receiptledger.engine.model.BoundingBox
import com.receiptledger.engine.model.OcrLine
import com.receiptledger.engine.model.OcrPage
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

fun ocrPage(vararg lines: String): OcrPage = ocrPage(lines.toList())

fun ocrPage(lines: List<String>): OcrPage {
    val ocrLines = lines.mapIndexed { index, text ->
        OcrLine(
            text = text,
            box = BoundingBox(0f, index * 20f, 300f, index * 20f + 16f),
        )
    }
    return OcrPage(
        lines = ocrLines,
        width = 300f,
        height = ocrLines.size * 20f + 20f,
    )
}

fun loadOcrFixture(name: String): OcrPage {
    val text = resourceText("ocr/$name.txt")
    return ocrPage(text.split('\n'))
}

fun loadOcrExpected(name: String): OcrExpected? {
    val path = "ocr/$name.expected.json"
    val stream = Thread.currentThread().contextClassLoader.getResourceAsStream(path) ?: return null
    val json = stream.bufferedReader().use { it.readText() }
    return Json.decodeFromString(OcrExpected.serializer(), json)
}

fun listOcrFixtureNames(): List<String> {
    val marker = Thread.currentThread().contextClassLoader.getResource("ocr/README.txt")
        ?: Thread.currentThread().contextClassLoader.getResource("ocr/canadian-tire-labeled.txt")
        ?: return emptyList()
    val dir = java.io.File(marker.toURI()).parentFile
    if (!dir.isDirectory) return emptyList()
    return dir.listFiles()
        .orEmpty()
        .filter { it.extension == "txt" && it.name != "README.txt" }
        .map { it.nameWithoutExtension }
        .sorted()
}

fun resourceText(path: String): String {
    val stream = checkNotNull(
        Thread.currentThread().contextClassLoader.getResourceAsStream(path),
    ) { "Missing test resource $path" }
    return stream.bufferedReader().use { it.readText() }
}

@Serializable
data class OcrExpected(
    val merchant: String? = null,
    val date: String? = null,
    val currency: String? = null,
    val subtotal: Double? = null,
    val total: Double? = null,
    val taxTotal: Double? = null,
    val gstHstNumber: String? = null,
    val paymentMethod: String? = null,
    val receiptNumber: String? = null,
)

