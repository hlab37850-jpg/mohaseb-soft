package com.muhasib.soft.data.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update

@Dao
interface DocumentDao {
    @Insert suspend fun insert(d: DocumentEntity): Long
    @Update suspend fun update(d: DocumentEntity)
    @Delete suspend fun delete(d: DocumentEntity)
    @Query("SELECT * FROM documents WHERE id=:id") suspend fun byId(id: Long): DocumentEntity?
    @Query("SELECT IFNULL(MAX(number),0) FROM documents WHERE type=:t") suspend fun maxNumber(t: String): Int
    @Query("SELECT d.id AS id, d.type AS type, d.number AS number, d.date AS date, d.partyName AS partyName, d.net AS net, d.paid AS paid, d.remaining AS remaining, c.symbol AS symbol FROM documents d JOIN currencies c ON c.id=d.currencyId WHERE d.type=:t AND (:q='' OR d.partyName LIKE '%'||:q||'%' OR CAST(d.number AS TEXT) LIKE '%'||:q||'%') AND (:from='' OR d.date>=:from) AND (:to='' OR d.date<=:to) ORDER BY d.date DESC, d.id DESC") suspend fun list(t: String, q: String, from: String, to: String): List<DocRow>
    @Query("SELECT IFNULL(SUM(net),0) FROM documents WHERE type=:t AND (:from='' OR date>=:from) AND (:to='' OR date<=:to)") suspend fun sumNet(t: String, from: String, to: String): Double
    @Query("SELECT * FROM documents WHERE type=:t AND (:from='' OR date>=:from) AND (:to='' OR date<=:to) ORDER BY date, id") suspend fun byType(t: String, from: String, to: String): List<DocumentEntity>
    @Query("UPDATE documents SET closed=1 WHERE date<=:d") suspend fun markClosed(d: String)
}

@Dao
interface DocumentItemDao {
    @Insert suspend fun insertAll(list: List<DocumentItemEntity>)
    @Query("SELECT * FROM document_items WHERE docId=:doc ORDER BY id") suspend fun byDoc(doc: Long): List<DocumentItemEntity>
    @Query("DELETE FROM document_items WHERE docId=:doc") suspend fun deleteByDoc(doc: Long)
    @Query("SELECT d.date AS date, d.number AS number, i.name AS itemName, d.type AS docType, IFNULL(w.name,'') AS warehouseName, di.qty AS qty, di.price AS price, di.total AS total FROM document_items di JOIN documents d ON d.id=di.docId JOIN items i ON i.id=di.itemId LEFT JOIN warehouses w ON w.id=d.warehouseId WHERE (:item=0 OR i.id=:item) AND (:from='' OR d.date>=:from) AND (:to='' OR d.date<=:to) ORDER BY d.date, d.id") suspend fun movement(item: Long, from: String, to: String): List<MovementRow>
}

@Dao
interface JournalDao {
    @Insert suspend fun insertAll(list: List<JournalLineEntity>)
    @Query("SELECT * FROM journal_lines WHERE docId=:doc ORDER BY id") suspend fun byDoc(doc: Long): List<JournalLineEntity>
    @Query("DELETE FROM journal_lines WHERE docId=:doc") suspend fun deleteByDoc(doc: Long)
    @Query("SELECT j.id AS id, j.docId AS docId, IFNULL(d.date,'') AS date, IFNULL(d.number,0) AS number, IFNULL(d.type,'') AS type, j.accountName AS accountName, j.debit AS debit, j.credit AS credit, j.note AS note, j.currencyId AS currencyId FROM journal_lines j LEFT JOIN documents d ON d.id=j.docId WHERE j.accountId=:acc AND (:cur=0 OR j.currencyId=:cur) ORDER BY d.date, d.id, j.id") suspend fun statement(acc: Long, cur: Long): List<JournalRow>
    @Query("SELECT j.accountId AS accountId, a.name AS accountName, a.parentId AS parentId, a.kind AS kind, SUM(j.debit) AS debit, SUM(j.credit) AS credit FROM journal_lines j JOIN accounts a ON a.id=j.accountId WHERE j.currencyId=:cur GROUP BY j.accountId") suspend fun trial(cur: Long): List<BalanceRow>
    @Query("SELECT IFNULL(SUM(debit),0) FROM journal_lines WHERE accountId=:acc AND currencyId=:cur") suspend fun sumDebit(acc: Long, cur: Long): Double
    @Query("SELECT IFNULL(SUM(credit),0) FROM journal_lines WHERE accountId=:acc AND currencyId=:cur") suspend fun sumCredit(acc: Long, cur: Long): Double
}
