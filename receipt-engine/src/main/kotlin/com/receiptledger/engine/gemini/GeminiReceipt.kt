package com.receiptledger.engine.gemini

import com.receiptledger.engine.parse.PaymentMethodParser
import com.receiptledger.engine.parse.ReceiptNumberParser
import kotlinx.serialization.Serializable

@Serializable
data class GeminiTax(
    val kind: String? = null,
    val amount: Double? = null,
    val rate: Double? = null,
)

@Serializable
data class GeminiLineItem(
    val name: String? = null,
    val quantity: Double? = null,
    val amount: Double? = null,
)

@Serializable
data class GeminiReceipt(
    val merchant: String? = null,
    val date: String? = null,
    val currency: String? = null,
    val subtotal: Double? = null,
    val taxes: List<GeminiTax> = emptyList(),
    val total: Double? = null,
    val paymentMethod: String? = null,
    val gstHstNumber: String? = null,
    val receiptNumber: String? = null,
    val lineItems: List<GeminiLineItem> = emptyList(),
    val suggestedCategoryId: String? = null,
) {
    companion object {
        val LINE_ITEM_RULE = """
Always give lineItems.quantity a number, never null. A line with no count printed next to it is 1.
Stores mark larger counts differently, so read whichever form appears: "x2", "2X", "QTY 2", "2 EA",
"2 @ 3.99", "2 FOR 5.00". Put the count in quantity and leave it out of name. When only a unit price
is printed, divide the line total by it to get the count. amount is the total for the whole line.
""".trim()

        val CATEGORY_RULE = """
Choose suggestedCategoryId using both the store and what was bought, in that order.
The store settles it when the store only does one trade: a tire and auto centre, a muffler shop or
a dealership service desk is a motor vehicle expense whatever the parts are called.
When the store sells across trades, the line items decide, because the sign over the door says
nothing about the purchase: paint, brushes and lumber from a Canadian Tire or a hardware chain are
repairs and maintenance, while an oil filter, wiper blades, motor oil, a battery or a tire from the
same store are a motor vehicle expense. Fuel is a motor vehicle expense even at a grocery chain.
Where a receipt spans several trades, choose the one holding most of the money.
Use null when nothing in the allowed list fits; never invent an id.
""".trim()

        /** Ids alone are opaque, so each one is sent with the words it stands for. */
        fun categorySection(choices: List<Pair<String, String>>): String {
            if (choices.isEmpty()) {
                return "No categories are available, so return null for suggestedCategoryId."
            }
            return buildString {
                appendLine("Allowed suggestedCategoryId values, each as id = what it means:")
                choices.forEach { (id, label) -> appendLine("$id = $label") }
                append(CATEGORY_RULE)
            }
        }

        val JSON_SCHEMA_HINT = """
Return ONLY JSON with this shape:
{
  "merchant": "string or null",
  "date": "YYYY-MM-DD or null",
  "currency": "CAD or USD or null",
  "subtotal": number or null,
  "taxes": [{"kind": "GST|HST|PST|QST|TPS|TVQ|SALES_TAX", "amount": number, "rate": number or null}],
  "total": number or null,
  "paymentMethod": "visa *1234 or paypal or null",
  "gstHstNumber": "string or null",
  "receiptNumber": "string or null",
  "lineItems": [{"name": "string", "quantity": number, "amount": number or null}],
  "suggestedCategoryId": "string or null"
}
Use Canadian tax line names when the receipt is Canadian. Expand store abbreviations (CDN TIRE → Canadian Tire).
Include every purchased line in lineItems. Use [] if none are readable.
${LINE_ITEM_RULE}
${PaymentMethodParser.GEMINI_RULE}
${ReceiptNumberParser.GEMINI_RULE}
"""
    }
}
