package com.muhasib.soft.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "accounts")
data class AccountEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val parentId: Long = 0,
    val kind: String = "SUB",
    val classification: String = "عام",
    val phone: String = "",
    val address: String = "",
    val taxNumber: String = ""
)

@Entity(tableName = "classifications")
data class ClassificationEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String
)

@Entity(tableName = "currencies")
data class CurrencyEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val category: String,
    val symbol: String
)

@Entity(tableName = "currency_rates")
data class CurrencyRateEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val currencyId: Long,
    val rate: Double,
    val fromDate: String,
    val toDate: String = ""
)

@Entity(tableName = "warehouses")
data class WarehouseEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val phone: String = "",
    val address: String = ""
)

@Entity(tableName = "cashboxes")
data class CashboxEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val phone: String = "",
    val address: String = "",
    val taxNumber: String = "",
    val notifySms: Boolean = false,
    val notifyWhatsapp: Boolean = false,
    val accountId: Long = 0
)

@Entity(tableName = "account_ceilings")
data class AccountCeilingEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val accountId: Long,
    val currencyId: Long,
    val debitCeiling: Double = 0.0,
    val creditCeiling: Double = 0.0
)

@Entity(tableName = "users")
data class UserEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val password: String = "",
    val cashboxId: Long = 0,
    val warehouseId: Long = 0,
    val enabled: Boolean = false,
    val isAdmin: Boolean = false
)

@Entity(tableName = "settings")
data class SettingEntity(
    @PrimaryKey val key: String,
    val value: String = ""
)

@Entity(tableName = "reminders")
data class ReminderEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val date: String,
    val time: String,
    val note: String = ""
)

@Entity(tableName = "year_close")
data class YearCloseEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val closeDate: String,
    val executedAt: String
)
