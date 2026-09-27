package com.milanstevic.garanzia.backup

import com.milanstevic.garanzia.data.local.ReceiptEntity
import com.milanstevic.garanzia.data.local.ReceiptProductEntity
import org.json.JSONArray
import org.json.JSONObject

object BackupManifestCodec {

    fun encode(manifest: BackupManifest): String {
        val root = JSONObject()
            .put("format", manifest.format)
            .put("formatVersion", manifest.formatVersion)
            .put("databaseSchemaVersion", manifest.databaseSchemaVersion)
            .put("createdAtEpochMs", manifest.createdAtEpochMs)

        val receipts = JSONArray()
        manifest.receipts.forEach { record ->
            receipts.put(
                JSONObject()
                    .put("receipt", receiptToJson(record.receipt))
                    .put(
                        "products",
                        JSONArray().apply {
                            record.products
                                .sortedBy { it.position }
                                .forEach { put(productToJson(it)) }
                        },
                    )
                    .put(
                        "pages",
                        JSONArray().apply {
                            record.pages
                                .sortedBy { it.pageIndex }
                                .forEach { put(pageToJson(it)) }
                        },
                    )
                    .put(
                        "attachments",
                        JSONArray().apply {
                            record.attachments
                                .sortedBy { it.createdAtEpochMs }
                                .forEach { put(attachmentToJson(it)) }
                        },
                    ),
            )
        }

        root.put("receipts", receipts)
        return root.toString(2)
    }

    fun decode(text: String): BackupManifest {
        val root = JSONObject(text)
        val receiptsJson = root.getJSONArray("receipts")
        val receipts = buildList {
            for (index in 0 until receiptsJson.length()) {
                val item = receiptsJson.getJSONObject(index)
                val receipt = receiptFromJson(item.getJSONObject("receipt"))

                val productsJson = item.getJSONArray("products")
                val products = buildList {
                    for (productIndex in 0 until productsJson.length()) {
                        add(productFromJson(productsJson.getJSONObject(productIndex)))
                    }
                }

                val pagesJson = item.getJSONArray("pages")
                val pages = buildList {
                    for (pageIndex in 0 until pagesJson.length()) {
                        add(pageFromJson(pagesJson.getJSONObject(pageIndex)))
                    }
                }

                val attachmentsJson = item.optJSONArray("attachments")
                val attachments = buildList {
                    if (attachmentsJson != null) {
                        for (attachmentIndex in 0 until attachmentsJson.length()) {
                            add(
                                attachmentFromJson(
                                    attachmentsJson.getJSONObject(attachmentIndex),
                                ),
                            )
                        }
                    }
                }

                add(
                    BackupReceiptRecord(
                        receipt = receipt,
                        products = products,
                        pages = pages,
                        attachments = attachments,
                    ),
                )
            }
        }

        return BackupManifest(
            format = root.getString("format"),
            formatVersion = root.getInt("formatVersion"),
            databaseSchemaVersion = root.getInt("databaseSchemaVersion"),
            createdAtEpochMs = root.getLong("createdAtEpochMs"),
            receipts = receipts,
        )
    }

    private fun receiptToJson(receipt: ReceiptEntity): JSONObject =
        JSONObject()
            .put("id", receipt.id)
            .put("merchant", receipt.merchant)
            .put("purchaseDate", receipt.purchaseDate)
            .putNullable("purchaseTime", receipt.purchaseTime)
            .put("totalAmount", receipt.totalAmount)
            .putNullable("currency", receipt.currency)
            .putNullable("vatNumber", receipt.vatNumber)
            .putNullable("documentNumber", receipt.documentNumber)
            .putNullable("paymentMethod", receipt.paymentMethod)
            .putNullable("rawOcrText", receipt.rawOcrText)
            .put("confirmedAtEpochMs", receipt.confirmedAtEpochMs)

    private fun receiptFromJson(json: JSONObject): ReceiptEntity =
        ReceiptEntity(
            id = json.getString("id"),
            merchant = json.getString("merchant"),
            purchaseDate = json.getString("purchaseDate"),
            purchaseTime = json.nullableString("purchaseTime"),
            totalAmount = json.getString("totalAmount"),
            currency = json.nullableString("currency"),
            vatNumber = json.nullableString("vatNumber"),
            documentNumber = json.nullableString("documentNumber"),
            paymentMethod = json.nullableString("paymentMethod"),
            rawOcrText = json.nullableString("rawOcrText"),
            confirmedAtEpochMs = json.getLong("confirmedAtEpochMs"),
        )

    private fun productToJson(product: ReceiptProductEntity): JSONObject =
        JSONObject()
            .put("id", product.id)
            .put("receiptId", product.receiptId)
            .put("position", product.position)
            .put("name", product.name)
            .putNullable("quantity", product.quantity)
            .putNullable("unitPrice", product.unitPrice)
            .putNullable("lineTotal", product.lineTotal)
            .putNullable("sourceConfidence", product.sourceConfidence)
            .putNullable("warrantyMonths", product.warrantyMonths)
            .put("warrantyReminderDays", product.warrantyReminderDays)
            .put("warrantyNotificationsEnabled", product.warrantyNotificationsEnabled)
            .putNullable("warrantyLastNotificationKey", product.warrantyLastNotificationKey)

    private fun productFromJson(json: JSONObject): ReceiptProductEntity =
        ReceiptProductEntity(
            id = json.getLong("id"),
            receiptId = json.getString("receiptId"),
            position = json.getInt("position"),
            name = json.getString("name"),
            quantity = json.nullableString("quantity"),
            unitPrice = json.nullableString("unitPrice"),
            lineTotal = json.nullableString("lineTotal"),
            sourceConfidence =
                if (json.isNull("sourceConfidence")) {
                    null
                } else {
                    json.getDouble("sourceConfidence").toFloat()
                },
            warrantyMonths =
                if (json.isNull("warrantyMonths")) {
                    null
                } else {
                    json.getInt("warrantyMonths")
                },
            warrantyReminderDays = json.optInt("warrantyReminderDays", 30),
            warrantyNotificationsEnabled =
                json.optBoolean("warrantyNotificationsEnabled", true),
            warrantyLastNotificationKey = json.nullableString("warrantyLastNotificationKey"),
        )

    private fun pageToJson(page: BackupPageRecord): JSONObject =
        JSONObject()
            .put("id", page.id)
            .put("receiptId", page.receiptId)
            .put("pageIndex", page.pageIndex)
            .put("entryName", page.entryName)
            .put("sizeBytes", page.sizeBytes)
            .put("sha256", page.sha256)

    private fun pageFromJson(json: JSONObject): BackupPageRecord =
        BackupPageRecord(
            id = json.getLong("id"),
            receiptId = json.getString("receiptId"),
            pageIndex = json.getInt("pageIndex"),
            entryName = json.getString("entryName"),
            sizeBytes = json.getLong("sizeBytes"),
            sha256 = json.getString("sha256"),
        )

    private fun attachmentToJson(attachment: BackupAttachmentRecord): JSONObject =
        JSONObject()
            .put("id", attachment.id)
            .put("receiptId", attachment.receiptId)
            .put("productId", attachment.productId)
            .put("category", attachment.category)
            .put("mimeType", attachment.mimeType)
            .putNullable("originalName", attachment.originalName)
            .putNullable("note", attachment.note)
            .put("createdAtEpochMs", attachment.createdAtEpochMs)
            .put("entryName", attachment.entryName)
            .put("sizeBytes", attachment.sizeBytes)
            .put("sha256", attachment.sha256)

    private fun attachmentFromJson(json: JSONObject): BackupAttachmentRecord =
        BackupAttachmentRecord(
            id = json.getLong("id"),
            receiptId = json.getString("receiptId"),
            productId = json.getLong("productId"),
            category = json.getString("category"),
            mimeType = json.getString("mimeType"),
            originalName = json.nullableString("originalName"),
            note = json.nullableString("note"),
            createdAtEpochMs = json.getLong("createdAtEpochMs"),
            entryName = json.getString("entryName"),
            sizeBytes = json.getLong("sizeBytes"),
            sha256 = json.getString("sha256"),
        )

    private fun JSONObject.putNullable(
        key: String,
        value: Any?,
    ): JSONObject = put(key, value ?: JSONObject.NULL)

    private fun JSONObject.nullableString(key: String): String? =
        if (!has(key) || isNull(key)) null else getString(key)
}
