package com.muhasib.soft.data.db

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "item_groups")
data class ItemGroupEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String
)

@Entity(tableName = "units")
data class UnitEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val abbrev: String = ""
)

@Entity(tableName = "items")
data class ItemEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val groupId: Long = 0,
    val baseUnitId: Long = 0,
    val openingQty: Double = 0.0,
    val unitCost: Double = 0.0,
    val notes: String = "",
    val expiry: String = "",
    val barcode: String = ""
)

@Entity(tableName = "item_units")
data class ItemUnitEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val itemId: Long,
    val unitId: Long,
    val pack: Double = 1.0,
    val isBase: Boolean = false
)

@Entity(tableName = "item_prices")
data class ItemPriceEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val itemId: Long,
    val currencyId: Long,
    val price: Double,
    val cost: Double,
    val date: String
)

@Entity(tableName = "stock", indices = [Index(value = ["itemId", "warehouseId"], unique = true)])
data class StockEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val itemId: Long,
    val warehouseId: Long,
    val qty: Double = 0.0
)
