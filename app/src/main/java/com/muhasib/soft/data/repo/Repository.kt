package com.muhasib.soft.data.repo

import androidx.room.withTransaction
import com.muhasib.soft.data.db.AppDatabase
import com.muhasib.soft.data.db.DocType
import com.muhasib.soft.data.db.DocumentEntity
import com.muhasib.soft.data.db.DocumentItemEntity
import com.muhasib.soft.data.db.JournalLineEntity
import com.muhasib.soft.data.db.SettingEntity
import com.muhasib.soft.data.db.StockEntity
import com.muhasib.soft.data.db.YearCloseEntity

class Repository(private val db: AppDatabase) {

    suspend fun setting(key: String, default: String = ""): String =
        db.settingsDao().get(key)?.value ?: default

    suspend fun setSetting(key: String, value: String) =
        db.settingsDao().set(SettingEntity(key, value))

    suspend fun nextNumber(type: String): Int = db.documentDao().maxNumber(type) + 1

    private fun isAutoJournal(type: String): Boolean = when (type) {
        DocType.SALE, DocType.PURCHASE, DocType.DISPATCH,
        DocType.SUPPLY, DocType.ADJUST, DocType.COUNT -> true
        else -> false
    }

    suspend fun saveDocument(
        doc: DocumentEntity,
        items: List<DocumentItemEntity>,
        providedLines: List<JournalLineEntity> = emptyList()
    ): Long = db.withTransaction {
        var d = doc
        if (doc.id == 0L) {
            val id = db.documentDao().insert(doc)
            d = doc.copy(id = id)
        } else {
            val oldItems = db.documentItemDao().byDoc(doc.id)
            AccountingEngine.reverseStock(db, doc, oldItems)
            db.documentItemDao().deleteByDoc(doc.id)
            db.journalDao().deleteByDoc(doc.id)
            db.documentDao().update(doc)
        }
        val rows = items.map { it.copy(docId = d.id) }
        if (rows.isNotEmpty()) db.documentItemDao().insertAll(rows)
        val lines = if (isAutoJournal(d.type)) {
            AccountingEngine.journalFor(db, d, rows)
        } else {
            providedLines.map { it.copy(docId = d.id) }
        }
        if (lines.isNotEmpty()) db.journalDao().insertAll(lines)
        AccountingEngine.applyStock(db, d, rows)
        AccountingEngine.postPurchaseExtras(db, d, rows)
        d.id
    }

    suspend fun deleteDocument(doc: DocumentEntity) = db.withTransaction {
        val oldItems = db.documentItemDao().byDoc(doc.id)
        AccountingEngine.reverseStock(db, doc, oldItems)
        db.documentItemDao().deleteByDoc(doc.id)
        db.journalDao().deleteByDoc(doc.id)
        db.documentDao().delete(doc)
    }

    suspend fun saveItemWithOpening(
        item: com.muhasib.soft.data.db.ItemEntity,
        warehouseId: Long
    ): Long = db.withTransaction {
        val id = db.itemDao().insert(item)
        if (item.openingQty != 0.0) {
            db.stockDao().upsert(
                StockEntity(itemId = id, warehouseId = warehouseId, qty = item.openingQty)
            )
        }
        id
    }

    suspend fun executeYearClose(closeDate: String, executedAt: String) = db.withTransaction {
        db.documentDao().markClosed(closeDate)
        val currencies = db.currencyDao().all()
        for (c in currencies) {
            val balances = db.journalDao().trial(c.id).filter { it.kind == "SUB" }
                .map { Triple(it.accountId, it.debit - it.credit, it.accountName) }
                .filter { Math.abs(it.second) > 0.000001 }
            if (balances.isEmpty()) continue
            val number = db.documentDao().maxNumber(DocType.OPENING) + 1
            val docId = db.documentDao().insert(
                DocumentEntity(
                    type = DocType.OPENING, number = number, date = closeDate,
                    partyName = "إقفال سنوي", currencyId = c.id, closed = true
                )
            )
            val lines = mutableListOf<JournalLineEntity>()
            var sum = 0.0
            for (b in balances) {
                sum += b.second
                lines += JournalLineEntity(
                    docId = docId, accountId = b.first, accountName = b.third,
                    currencyId = c.id,
                    debit = if (b.second > 0) b.second else 0.0,
                    credit = if (b.second < 0) -b.second else 0.0
                )
            }
            if (Math.abs(sum) > 0.000001) {
                lines += JournalLineEntity(
                    docId = docId, accountId = SysAcc.OPENING,
                    accountName = "الحساب الافتتاحي", currencyId = c.id,
                    debit = if (sum < 0) -sum else 0.0,
                    credit = if (sum > 0) sum else 0.0
                )
            }
            db.journalDao().insertAll(lines)
        }
        db.yearCloseDao().insert(YearCloseEntity(closeDate = closeDate, executedAt = executedAt))
        setSetting("close_date", closeDate)
    }
}
