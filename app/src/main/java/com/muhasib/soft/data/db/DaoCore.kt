package com.muhasib.soft.data.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update

@Dao
interface AccountDao {
    @Insert suspend fun insert(a: AccountEntity): Long
    @Update suspend fun update(a: AccountEntity)
    @Delete suspend fun delete(a: AccountEntity)
    @Query("SELECT * FROM accounts ORDER BY kind DESC, id") suspend fun all(): List<AccountEntity>
    @Query("SELECT * FROM accounts WHERE id=:id") suspend fun byId(id: Long): AccountEntity?
    @Query("SELECT * FROM accounts WHERE kind='MAIN' ORDER BY id") suspend fun mains(): List<AccountEntity>
    @Query("SELECT * FROM accounts WHERE name LIKE '%'||:q||'%' ORDER BY name") suspend fun search(q: String): List<AccountEntity>
    @Query("SELECT COUNT(*) FROM accounts WHERE parentId=:id") suspend fun childCount(id: Long): Int
}

@Dao
interface ClassificationDao {
    @Insert suspend fun insert(c: ClassificationEntity): Long
    @Delete suspend fun delete(c: ClassificationEntity)
    @Query("SELECT cf.id AS id, cf.name AS name, (SELECT COUNT(*) FROM accounts a WHERE a.classification=cf.name) AS accountCount FROM classifications cf ORDER BY cf.name") suspend fun all(): List<ClassRow>
}

@Dao
interface CurrencyDao {
    @Insert suspend fun insert(c: CurrencyEntity): Long
    @Update suspend fun update(c: CurrencyEntity)
    @Delete suspend fun delete(c: CurrencyEntity)
    @Query("SELECT c.id AS id, c.name AS name, c.category AS category, c.symbol AS symbol, (SELECT COUNT(DISTINCT accountId) FROM journal_lines WHERE currencyId=c.id) AS accountsCount FROM currencies c ORDER BY c.id") suspend fun all(): List<CurrencyRow>
    @Query("SELECT * FROM currencies WHERE id=:id") suspend fun byId(id: Long): CurrencyEntity?
}

@Dao
interface RateDao {
    @Insert suspend fun insert(r: CurrencyRateEntity): Long
    @Delete suspend fun delete(r: CurrencyRateEntity)
    @Query("SELECT r.id AS id, c.name AS currencyName, r.rate AS rate, r.fromDate AS fromDate, r.toDate AS toDate FROM currency_rates r JOIN currencies c ON c.id=r.currencyId ORDER BY r.fromDate DESC") suspend fun all(): List<RateRow>
    @Query("SELECT * FROM currency_rates WHERE currencyId=:c AND fromDate<=:d ORDER BY fromDate DESC LIMIT 1") suspend fun active(c: Long, d: String): CurrencyRateEntity?
}

@Dao
interface WarehouseDao {
    @Insert suspend fun insert(w: WarehouseEntity): Long
    @Update suspend fun update(w: WarehouseEntity)
    @Delete suspend fun delete(w: WarehouseEntity)
    @Query("SELECT * FROM warehouses ORDER BY id") suspend fun all(): List<WarehouseEntity>
    @Query("SELECT * FROM warehouses WHERE id=:id") suspend fun byId(id: Long): WarehouseEntity?
}

@Dao
interface CashboxDao {
    @Insert suspend fun insert(c: CashboxEntity): Long
    @Update suspend fun update(c: CashboxEntity)
    @Delete suspend fun delete(c: CashboxEntity)
    @Query("SELECT * FROM cashboxes ORDER BY id") suspend fun all(): List<CashboxEntity>
    @Query("SELECT * FROM cashboxes WHERE id=:id") suspend fun byId(id: Long): CashboxEntity?
}

@Dao
interface CeilingDao {
    @Insert suspend fun insert(c: AccountCeilingEntity): Long
    @Update suspend fun update(c: AccountCeilingEntity)
    @Delete suspend fun delete(c: AccountCeilingEntity)
    @Query("SELECT c.id AS id, a.name AS accountName, cu.name AS currencyName, c.debitCeiling AS debitCeiling, c.creditCeiling AS creditCeiling FROM account_ceilings c JOIN accounts a ON a.id=c.accountId JOIN currencies cu ON cu.id=c.currencyId ORDER BY c.id") suspend fun all(): List<CeilingRow>
}

@Dao
interface UserDao {
    @Insert suspend fun insert(u: UserEntity): Long
    @Update suspend fun update(u: UserEntity)
    @Delete suspend fun delete(u: UserEntity)
    @Query("SELECT * FROM users ORDER BY id") suspend fun all(): List<UserEntity>
    @Query("SELECT * FROM users WHERE name=:n LIMIT 1") suspend fun byName(n: String): UserEntity?
}

@Dao
interface SettingsDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun set(s: SettingEntity)
    @Query("SELECT * FROM settings WHERE key=:k") suspend fun get(k: String): SettingEntity?
    @Query("SELECT * FROM settings") suspend fun all(): List<SettingEntity>
}

@Dao
interface ReminderDao {
    @Insert suspend fun insert(r: ReminderEntity): Long
    @Update suspend fun update(r: ReminderEntity)
    @Delete suspend fun delete(r: ReminderEntity)
    @Query("SELECT * FROM reminders ORDER BY date, time") suspend fun all(): List<ReminderEntity>
    @Query("SELECT * FROM reminders WHERE id=:id") suspend fun byId(id: Long): ReminderEntity?
}

@Dao
interface YearCloseDao {
    @Insert suspend fun insert(y: YearCloseEntity): Long
    @Query("SELECT * FROM year_close ORDER BY id DESC LIMIT 1") suspend fun last(): YearCloseEntity?
    @Query("SELECT * FROM year_close ORDER BY id") suspend fun all(): List<YearCloseEntity>
}
