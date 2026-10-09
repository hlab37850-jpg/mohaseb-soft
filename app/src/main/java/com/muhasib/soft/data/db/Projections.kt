package com.muhasib.soft.data.db

data class DocRow(
    val id: Long, val type: String, val number: Int, val date: String,
    val partyName: String, val net: Double, val paid: Double,
    val remaining: Double, val symbol: String
)

data class ItemFullRow(
    val id: Long, val name: String, val groupName: String,
    val openingQty: Double, val unitCost: Double, val stockQty: Double
)

data class PriceRow(
    val id: Long, val itemId: Long, val itemName: String, val price: Double,
    val cost: Double, val date: String, val symbol: String
)

data class StockRow(
    val itemId: Long, val itemName: String, val warehouseName: String,
    val qty: Double, val price: Double, val cost: Double, val total: Double
)

data class MovementRow(
    val date: String, val number: Int, val itemName: String, val docType: String,
    val warehouseName: String, val qty: Double, val price: Double, val total: Double
)

data class JournalRow(
    val id: Long, val docId: Long, val date: String, val number: Int, val type: String,
    val accountName: String, val debit: Double, val credit: Double,
    val note: String, val currencyId: Long
)

data class BalanceRow(
    val accountId: Long, val accountName: String, val parentId: Long,
    val kind: String, val debit: Double, val credit: Double
)

data class CurrencyRow(
    val id: Long, val name: String, val category: String,
    val symbol: String, val accountsCount: Int
)

data class GroupRow(val id: Long, val name: String, val itemCount: Int)

data class ClassRow(val id: Long, val name: String, val accountCount: Int)

data class ItemUnitRow(
    val id: Long, val itemId: Long, val itemName: String,
    val unitName: String, val pack: Double, val baseUnitName: String
)

data class CeilingRow(
    val id: Long, val accountName: String, val currencyName: String,
    val debitCeiling: Double, val creditCeiling: Double
)

data class RateRow(
    val id: Long, val currencyName: String, val rate: Double,
    val fromDate: String, val toDate: String
)
