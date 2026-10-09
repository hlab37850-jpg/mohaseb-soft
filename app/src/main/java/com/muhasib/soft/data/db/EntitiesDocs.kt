package com.muhasib.soft.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

object DocType {
    const val SALE = "SALE"
    const val PURCHASE = "PURCHASE"
    const val QUOTE = "QUOTE"
    const val PURCHASE_ORDER = "PURCHASE_ORDER"
    const val RECEIPT = "RECEIPT"
    const val PAYMENT = "PAYMENT"
    const val JOURNAL = "JOURNAL"
    const val OPENING = "OPENING"
    const val DISPATCH = "DISPATCH"
    const val SUPPLY = "SUPPLY"
    const val TRANSFER = "TRANSFER"
    const val ADJUST = "ADJUST"
    const val COUNT = "COUNT"
}

object AdjustKind {
    const val OPENING = "OPENING"
    const val DAMAGED = "DAMAGED"
    const val SURPLUS = "SURPLUS"
    const val SHORTAGE = "SHORTAGE"
}

@Entity(tableName = "documents")
data class DocumentEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val type: String,
    val number: Int,
    val date: String,
    val partyAccountId: Long = 0,
    val partyName: String = "",
    val warehouseId: Long = 1,
    val toWarehouseId: Long = 0,
    val cashboxId: Long = 1,
    val currencyId: Long = 1,
    val rate: Double = 1.0,
    val notes: String = "",
    val isCredit: Boolean = false,
    val isReference: Boolean = false,
    val adjustKind: String = "",
    val discountIsPercent: Boolean = false,
    val discount: Double = 0.0,
    val taxPercent: Double = 0.0,
    val tax: Double = 0.0,
    val otherFees: Double = 0.0,
    val otherFeesNote: String = "",
    val paid: Double = 0.0,
    val total: Double = 0.0,
    val net: Double = 0.0,
    val remaining: Double = 0.0,
    val closed: Boolean = false,
    val createdAt: String = ""
)

@Entity(tableName = "document_items")
data class DocumentItemEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val docId: Long,
    val itemId: Long,
    val name: String,
    val qty: Double,
    val price: Double,
    val cost: Double,
    val total: Double,
    val notes: String = ""
)

@Entity(tableName = "journal_lines")
data class JournalLineEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val docId: Long,
    val accountId: Long,
    val accountName: String,
    val currencyId: Long,
    val debit: Double,
    val credit: Double,
    val note: String = ""
)
