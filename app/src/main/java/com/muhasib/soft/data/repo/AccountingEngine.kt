package com.muhasib.soft.data.repo

import com.muhasib.soft.data.db.AdjustKind
import com.muhasib.soft.data.db.AppDatabase
import com.muhasib.soft.data.db.DocType
import com.muhasib.soft.data.db.DocumentEntity
import com.muhasib.soft.data.db.DocumentItemEntity
import com.muhasib.soft.data.db.ItemPriceEntity
import com.muhasib.soft.data.db.JournalLineEntity
import com.muhasib.soft.data.db.StockEntity

object SysAcc {
    const val ASSETS = 1L
    const val LIAB = 2L
    const val EQUITY = 3L
    const val REV = 4L
    const val EXP = 5L
    const val CASH = 6L
    const val BANK = 7L
    const val CUSTOMERS = 8L
    const val SUPPLIERS = 9L
    const val STOCK = 10L
    const val CAPITAL = 11L
    const val SALES = 12L
    const val COGS = 13L
    const val DISCOUNT = 14L
    const val TAX = 15L
    const val FEES = 16L
    const val GEN_EXP = 17L
    const val OTHER_REV = 18L
    const val DAMAGE = 19L
    const val OPENING = 20L
}

object AccountingEngine {

    suspend fun cashboxAccount(db: AppDatabase, cashboxId: Long): Long =
        db.cashboxDao().byId(cashboxId)?.accountId ?: SysAcc.CASH

    private suspend fun name(db: AppDatabase, id: Long): String =
        db.accountDao().byId(id)?.name ?: ""

    private suspend fun add(db: AppDatabase, itemId: Long, wh: Long, delta: Double) {
        val cur = db.stockDao().get(itemId, wh)
        if (cur == null) {
            db.stockDao().upsert(StockEntity(itemId = itemId, warehouseId = wh, qty = delta))
        } else {
            db.stockDao().upsert(cur.copy(qty = cur.qty + delta))
        }
    }

    private suspend fun setQty(db: AppDatabase, itemId: Long, wh: Long, qty: Double) {
        val cur = db.stockDao().get(itemId, wh)
        if (cur == null) {
            db.stockDao().upsert(StockEntity(itemId = itemId, warehouseId = wh, qty = qty))
        } else {
            db.stockDao().upsert(cur.copy(qty = qty))
        }
    }

    // COUNT rows: qty = counted, price = previous system qty, cost = unit cost snapshot
    suspend fun applyStock(db: AppDatabase, doc: DocumentEntity, items: List<DocumentItemEntity>) {
        when (doc.type) {
            DocType.SALE -> items.forEach { add(db, it.itemId, doc.warehouseId, -it.qty) }
            DocType.PURCHASE -> items.forEach { add(db, it.itemId, doc.warehouseId, it.qty) }
            DocType.DISPATCH -> items.forEach { add(db, it.itemId, doc.warehouseId, -it.qty) }
            DocType.SUPPLY -> items.forEach { add(db, it.itemId, doc.warehouseId, it.qty) }
            DocType.TRANSFER -> items.forEach {
                add(db, it.itemId, doc.warehouseId, -it.qty)
                add(db, it.itemId, doc.toWarehouseId, it.qty)
            }
            DocType.ADJUST -> items.forEach {
                when (doc.adjustKind) {
                    AdjustKind.OPENING, AdjustKind.SURPLUS -> add(db, it.itemId, doc.warehouseId, it.qty)
                    else -> add(db, it.itemId, doc.warehouseId, -it.qty)
                }
            }
            DocType.COUNT -> items.forEach { setQty(db, it.itemId, doc.warehouseId, it.qty) }
        }
    }

    suspend fun reverseStock(db: AppDatabase, doc: DocumentEntity, items: List<DocumentItemEntity>) {
        when (doc.type) {
            DocType.SALE -> items.forEach { add(db, it.itemId, doc.warehouseId, it.qty) }
            DocType.PURCHASE -> items.forEach { add(db, it.itemId, doc.warehouseId, -it.qty) }
            DocType.DISPATCH -> items.forEach { add(db, it.itemId, doc.warehouseId, it.qty) }
            DocType.SUPPLY -> items.forEach { add(db, it.itemId, doc.warehouseId, -it.qty) }
            DocType.TRANSFER -> items.forEach {
                add(db, it.itemId, doc.warehouseId, it.qty)
                add(db, it.itemId, doc.toWarehouseId, -it.qty)
            }
            DocType.ADJUST -> items.forEach {
                when (doc.adjustKind) {
                    AdjustKind.OPENING, AdjustKind.SURPLUS -> add(db, it.itemId, doc.warehouseId, -it.qty)
                    else -> add(db, it.itemId, doc.warehouseId, it.qty)
                }
            }
            DocType.COUNT -> items.forEach { setQty(db, it.itemId, doc.warehouseId, it.price) }
        }
    }

    suspend fun journalFor(
        db: AppDatabase,
        doc: DocumentEntity,
        items: List<DocumentItemEntity>
    ): List<JournalLineEntity> {
        val lines = mutableListOf<JournalLineEntity>()
        suspend fun line(acc: Long, dr: Double, cr: Double) = JournalLineEntity(
            docId = doc.id, accountId = acc, accountName = name(db, acc),
            currencyId = doc.currencyId, debit = dr, credit = cr
        )
        val total = items.sumOf { it.total }
        val costTotal = items.sumOf { it.qty * it.cost }
        when (doc.type) {
            DocType.SALE -> {
                val drAcc = if (doc.isCredit) doc.partyAccountId else cashboxAccount(db, doc.cashboxId)
                if (drAcc > 0) lines += line(drAcc, doc.net, 0.0)
                if (doc.discount > 0) lines += line(SysAcc.DISCOUNT, doc.discount, 0.0)
                if (total > 0) lines += line(SysAcc.SALES, 0.0, total)
                if (doc.tax > 0) lines += line(SysAcc.TAX, 0.0, doc.tax)
                if (doc.otherFees > 0) lines += line(SysAcc.FEES, 0.0, doc.otherFees)
                if (costTotal > 0) {
                    lines += line(SysAcc.COGS, costTotal, 0.0)
                    lines += line(SysAcc.STOCK, 0.0, costTotal)
                }
            }
            DocType.PURCHASE -> {
                val crAcc = if (doc.isCredit) doc.partyAccountId else cashboxAccount(db, doc.cashboxId)
                if (doc.net > 0) lines += line(SysAcc.STOCK, doc.net, 0.0)
                if (crAcc > 0) lines += line(crAcc, 0.0, doc.net)
            }
            DocType.DISPATCH -> {
                if (doc.partyAccountId > 0 && costTotal > 0) {
                    lines += line(doc.partyAccountId, costTotal, 0.0)
                    lines += line(SysAcc.STOCK, 0.0, costTotal)
                }
            }
            DocType.SUPPLY -> {
                if (doc.partyAccountId > 0 && costTotal > 0) {
                    lines += line(SysAcc.STOCK, costTotal, 0.0)
                    lines += line(doc.partyAccountId, 0.0, costTotal)
                }
            }
            DocType.ADJUST -> {
                if (costTotal > 0) {
                    when (doc.adjustKind) {
                        AdjustKind.OPENING -> {
                            lines += line(SysAcc.STOCK, costTotal, 0.0)
                            lines += line(SysAcc.CAPITAL, 0.0, costTotal)
                        }
                        AdjustKind.SURPLUS -> {
                            lines += line(SysAcc.STOCK, costTotal, 0.0)
                            lines += line(SysAcc.OTHER_REV, 0.0, costTotal)
                        }
                        else -> {
                            lines += line(SysAcc.DAMAGE, costTotal, 0.0)
                            lines += line(SysAcc.STOCK, 0.0, costTotal)
                        }
                    }
                }
            }
            DocType.COUNT -> {
                items.forEach { row ->
                    val delta = row.qty - row.price
                    val amount = Math.abs(delta) * row.cost
                    if (amount > 0) {
                        if (delta > 0) {
                            lines += line(SysAcc.STOCK, amount, 0.0)
                            lines += line(SysAcc.OTHER_REV, 0.0, amount)
                        } else {
                            lines += line(SysAcc.DAMAGE, amount, 0.0)
                            lines += line(SysAcc.STOCK, 0.0, amount)
                        }
                    }
                }
            }
        }
        return lines
    }

    suspend fun postPurchaseExtras(
        db: AppDatabase,
        doc: DocumentEntity,
        items: List<DocumentItemEntity>
    ) {
        if (doc.type != DocType.PURCHASE) return
        val avg = db.settingsDao().get("avg_cost")?.value == "1"
        val autoPrice = db.settingsDao().get("auto_price_on_purchase")?.value == "1"
        items.forEach { row ->
            val item = db.itemDao().byId(row.itemId) ?: return@forEach
            if (avg) {
                val qNow = db.stockDao().totalOf(row.itemId)
                val qPrev = qNow - row.qty
                if (qNow > 0 && qPrev >= 0) {
                    val newCost = (qPrev * item.unitCost + row.qty * row.cost) / qNow
                    db.itemDao().update(item.copy(unitCost = newCost))
                }
            }
            if (autoPrice && row.price > 0) {
                db.itemPriceDao().insert(
                    ItemPriceEntity(
                        itemId = row.itemId, currencyId = doc.currencyId,
                        price = row.price, cost = row.cost, date = doc.date
                    )
                )
            }
        }
    }
}
