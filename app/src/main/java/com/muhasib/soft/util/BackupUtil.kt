package com.muhasib.soft.util

import android.app.Activity
import android.content.ContentValues
import android.content.Intent
import android.os.Environment
import android.provider.DocumentsContract
import android.provider.MediaStore
import com.google.gson.Gson
import com.muhasib.soft.App
import com.muhasib.soft.R
import com.muhasib.soft.data.db.AccountCeilingEntity
import com.muhasib.soft.data.db.AccountEntity
import com.muhasib.soft.data.db.CashboxEntity
import com.muhasib.soft.data.db.ClassificationEntity
import com.muhasib.soft.data.db.CurrencyEntity
import com.muhasib.soft.data.db.CurrencyRateEntity
import com.muhasib.soft.data.db.DocumentEntity
import com.muhasib.soft.data.db.DocumentItemEntity
import com.muhasib.soft.data.db.ItemEntity
import com.muhasib.soft.data.db.ItemGroupEntity
import com.muhasib.soft.data.db.ItemPriceEntity
import com.muhasib.soft.data.db.ItemUnitEntity
import com.muhasib.soft.data.db.JournalLineEntity
import com.muhasib.soft.data.db.ReminderEntity
import com.muhasib.soft.data.db.SettingEntity
import com.muhasib.soft.data.db.StockEntity
import com.muhasib.soft.data.db.UnitEntity
import com.muhasib.soft.data.db.UserEntity
import com.muhasib.soft.data.db.WarehouseEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader

data class BackupModel(
    val version: Int = 1,
    val createdAt: String = nowFullStr(),
    val accounts: List<AccountEntity> = emptyList(),
    val classifications: List<ClassificationEntity> = emptyList(),
    val currencies: List<CurrencyEntity> = emptyList(),
    val rates: List<CurrencyRateEntity> = emptyList(),
    val warehouses: List<WarehouseEntity> = emptyList(),
    val cashboxes: List<CashboxEntity> = emptyList(),
    val ceilings: List<AccountCeilingEntity> = emptyList(),
    val users: List<UserEntity> = emptyList(),
    val settings: List<SettingEntity> = emptyList(),
    val reminders: List<ReminderEntity> = emptyList(),
    val groups: List<ItemGroupEntity> = emptyList(),
    val units: List<UnitEntity> = emptyList(),
    val items: List<ItemEntity> = emptyList(),
    val itemUnits: List<ItemUnitEntity> = emptyList(),
    val prices: List<ItemPriceEntity> = emptyList(),
    val stocks: List<StockEntity> = emptyList(),
    val documents: List<DocumentEntity> = emptyList(),
    val docItems: List<DocumentItemEntity> = emptyList(),
    val journal: List<JournalLineEntity> = emptyList()
)

object BackupUtil {
    private const val REQ_RESTORE = 2001
    private const val REQ_DRIVE = 2002
    private var activity: Activity? = null
    private val gson = Gson()

    fun bind(a: Activity) { activity = a }

    fun save() {
        val a = activity ?: return
        GlobalScope.launch(Dispatchers.IO) {
            val db = App.instance.db
            val docs = mutableListOf<DocumentEntity>()
            listOf("SALE","PURCHASE","RECEIPT","PAYMENT","JOURNAL","OPENING",
                "DISPATCH","SUPPLY","TRANSFER","ADJUST","COUNT","QUOTE","PURCHASE_ORDER").forEach { t ->
                docs.addAll(db.documentDao().byType(t, "", ""))
            }
            val model = BackupModel(
                accounts = db.accountDao().all(),
                currencies = db.currencyDao().all().map { CurrencyEntity(it.id, it.name, it.category, it.symbol) },
                warehouses = db.warehouseDao().all(),
                cashboxes = db.cashboxDao().all(),
                users = db.userDao().all(),
                settings = db.settingsDao().all(),
                reminders = db.reminderDao().all(),
                groups = db.groupDao().all().map { ItemGroupEntity(it.id, it.name) },
                units = db.unitDao().all(),
                items = db.itemDao().all().map { ItemEntity(it.id, it.name, 0, 0, it.openingQty, it.unitCost) },
                documents = docs
            )
            val json = gson.toJson(model)
            val fileName = "muhasib_backup_${System.currentTimeMillis()}.json"
            val values = ContentValues().apply {
                put(MediaStore.Downloads.DISPLAY_NAME, fileName)
                put(MediaStore.Downloads.MIME_TYPE, "application/json")
                put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
            }
            val uri = a.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
            if (uri == null) {
                withContext(Dispatchers.Main) { a.toast("فشل إنشاء الملف") }
                return@launch
            }
            a.contentResolver.openOutputStream(uri)?.use { it.write(json.toByteArray()) }
            withContext(Dispatchers.Main) { a.toast(a.getString(R.string.backup_saved)) }
        }
    }

    fun restore() {
        val a = activity ?: return
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "*/*"
        }
        a.startActivityForResult(intent, REQ_RESTORE)
    }

    fun drive() {
        val a = activity ?: return
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE)
        a.startActivityForResult(intent, REQ_DRIVE)
    }

    fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        val a = activity ?: return
        if (resultCode != Activity.RESULT_OK || data == null) return
        when (requestCode) {
            REQ_RESTORE -> {
                val uri = data.data ?: return
                GlobalScope.launch(Dispatchers.IO) {
                    try {
                        val text = a.contentResolver.openInputStream(uri)?.use {
                            BufferedReader(InputStreamReader(it)).readText()
                        } ?: return@launch
                        val model = gson.fromJson(text, BackupModel::class.java) ?: return@launch
                        val db = App.instance.db
                        db.clearAllTables()
                        importModel(db, model)
                        withContext(Dispatchers.Main) { a.toast(a.getString(R.string.backup_restored)) }
                    } catch (e: Exception) {
                        withContext(Dispatchers.Main) { a.toast("فشل الاسترجاع: ${e.message}") }
                    }
                }
            }
            REQ_DRIVE -> {
                val treeUri = data.data ?: return
                GlobalScope.launch(Dispatchers.IO) {
                    try {
                        val db = App.instance.db
                        val docs = db.documentDao().byType("SALE", "", "")
                        val text = gson.toJson(BackupModel(documents = docs))
                        val docTree = DocumentsContract.buildDocumentUriUsingTree(treeUri, DocumentsContract.getTreeDocumentId(treeUri))
                        val child = DocumentsContract.createDocument(a.contentResolver, docTree, "application/json", "muhasib_drive_${System.currentTimeMillis()}.json")
                        child?.let { a.contentResolver.openOutputStream(it)?.use { os -> os.write(text.toByteArray()) } }
                        withContext(Dispatchers.Main) { a.toast(a.getString(R.string.backup_saved)) }
                    } catch (e: Exception) {
                        withContext(Dispatchers.Main) { a.toast("فشل: ${e.message}") }
                    }
                }
            }
        }
    }

    private suspend fun importModel(db: com.muhasib.soft.data.db.AppDatabase, m: BackupModel) {
        m.accounts.forEach { db.accountDao().insert(it) }
        m.classifications.forEach { db.classificationDao().insert(it) }
        m.currencies.forEach { db.currencyDao().insert(it) }
        m.rates.forEach { db.rateDao().insert(it) }
        m.warehouses.forEach { db.warehouseDao().insert(it) }
        m.cashboxes.forEach { db.cashboxDao().insert(it) }
        m.ceilings.forEach { db.ceilingDao().insert(it) }
        m.users.forEach { db.userDao().insert(it) }
        m.settings.forEach { db.settingsDao().set(it) }
        m.reminders.forEach { db.reminderDao().insert(it) }
        m.groups.forEach { db.groupDao().insert(it) }
        m.units.forEach { db.unitDao().insert(it) }
        m.items.forEach { db.itemDao().insert(it) }
        m.itemUnits.forEach { db.itemUnitDao().insert(it) }
        m.prices.forEach { db.itemPriceDao().insert(it) }
        m.stocks.forEach { db.stockDao().upsert(it) }
        m.documents.forEach { db.documentDao().insert(it) }
        m.docItems.forEach { db.documentItemDao().insertAll(listOf(it)) }
        m.journal.forEach { db.journalDao().insertAll(listOf(it)) }
    }
}
