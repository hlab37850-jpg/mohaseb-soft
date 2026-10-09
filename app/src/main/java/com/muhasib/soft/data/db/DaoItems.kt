package com.muhasib.soft.data.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update

@Dao
interface GroupDao {
    @Insert suspend fun insert(g: ItemGroupEntity): Long
    @Delete suspend fun delete(g: ItemGroupEntity)
    @Query("SELECT g.id AS id, g.name AS name, (SELECT COUNT(*) FROM items i WHERE i.groupId=g.id) AS itemCount FROM item_groups g ORDER BY g.name") suspend fun all(): List<GroupRow>
}

@Dao
interface UnitDao {
    @Insert suspend fun insert(u: UnitEntity): Long
    @Update suspend fun update(u: UnitEntity)
    @Delete suspend fun delete(u: UnitEntity)
    @Query("SELECT * FROM units ORDER BY id") suspend fun all(): List<UnitEntity>
    @Query("SELECT * FROM units WHERE id=:id") suspend fun byId(id: Long): UnitEntity?
}

@Dao
interface ItemDao {
    @Insert suspend fun insert(i: ItemEntity): Long
    @Update suspend fun update(i: ItemEntity)
    @Delete suspend fun delete(i: ItemEntity)
    @Query("SELECT i.id AS id, i.name AS name, IFNULL(g.name,'') AS groupName, i.openingQty AS openingQty, i.unitCost AS unitCost, (SELECT IFNULL(SUM(s.qty),0) FROM stock s WHERE s.itemId=i.id) AS stockQty FROM items i LEFT JOIN item_groups g ON g.id=i.groupId ORDER BY i.name") suspend fun all(): List<ItemFullRow>
    @Query("SELECT i.id AS id, i.name AS name, IFNULL(g.name,'') AS groupName, i.openingQty AS openingQty, i.unitCost AS unitCost, (SELECT IFNULL(SUM(s.qty),0) FROM stock s WHERE s.itemId=i.id) AS stockQty FROM items i LEFT JOIN item_groups g ON g.id=i.groupId WHERE i.name LIKE '%'||:q||'%' ORDER BY i.name") suspend fun search(q: String): List<ItemFullRow>
    @Query("SELECT * FROM items WHERE id=:id") suspend fun byId(id: Long): ItemEntity?
}

@Dao
interface ItemUnitDao {
    @Insert suspend fun insert(u: ItemUnitEntity): Long
    @Delete suspend fun delete(u: ItemUnitEntity)
    @Query("SELECT u.id AS id, u.itemId AS itemId, i.name AS itemName, un.name AS unitName, u.pack AS pack, IFNULL(b.name,'') AS baseUnitName FROM item_units u JOIN items i ON i.id=u.itemId JOIN units un ON un.id=u.unitId LEFT JOIN units b ON b.id=i.baseUnitId ORDER BY i.name") suspend fun all(): List<ItemUnitRow>
    @Query("SELECT * FROM item_units WHERE itemId=:item") suspend fun byItem(item: Long): List<ItemUnitEntity>
}

@Dao
interface ItemPriceDao {
    @Insert suspend fun insert(p: ItemPriceEntity): Long
    @Delete suspend fun delete(p: ItemPriceEntity)
    @Query("SELECT p.id AS id, p.itemId AS itemId, i.name AS itemName, p.price AS price, p.cost AS cost, p.date AS date, c.symbol AS symbol FROM item_prices p JOIN items i ON i.id=p.itemId JOIN currencies c ON c.id=p.currencyId ORDER BY p.date DESC") suspend fun all(): List<PriceRow>
    @Query("SELECT * FROM item_prices WHERE itemId=:item ORDER BY date DESC LIMIT 1") suspend fun latest(item: Long): ItemPriceEntity?
}

@Dao
interface StockDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsert(s: StockEntity)
    @Query("SELECT * FROM stock WHERE itemId=:item AND warehouseId=:wh LIMIT 1") suspend fun get(item: Long, wh: Long): StockEntity?
    @Query("SELECT IFNULL(SUM(qty),0) FROM stock WHERE itemId=:item") suspend fun totalOf(item: Long): Double
    @Query("SELECT s.itemId AS itemId, i.name AS itemName, w.name AS warehouseName, s.qty AS qty, (SELECT IFNULL(p.price,0) FROM item_prices p WHERE p.itemId=i.id ORDER BY p.date DESC LIMIT 1) AS price, i.unitCost AS cost, s.qty*i.unitCost AS total FROM stock s JOIN items i ON i.id=s.itemId JOIN warehouses w ON w.id=s.warehouseId WHERE s.qty<>0 ORDER BY i.name") suspend fun remaining(): List<StockRow>
    @Query("SELECT s.itemId AS itemId, i.name AS itemName, w.name AS warehouseName, s.qty AS qty, 0 AS price, i.unitCost AS cost, s.qty*i.unitCost AS total FROM stock s JOIN items i ON i.id=s.itemId JOIN warehouses w ON w.id=s.warehouseId WHERE s.warehouseId=:wh ORDER BY i.name") suspend fun ofWarehouse(wh: Long): List<StockRow>
}
