package com.muhasib.soft.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        AccountEntity::class, ClassificationEntity::class, CurrencyEntity::class,
        CurrencyRateEntity::class, WarehouseEntity::class, CashboxEntity::class,
        AccountCeilingEntity::class, UserEntity::class, SettingEntity::class,
        ReminderEntity::class, YearCloseEntity::class, ItemGroupEntity::class,
        UnitEntity::class, ItemEntity::class, ItemUnitEntity::class,
        ItemPriceEntity::class, StockEntity::class, DocumentEntity::class,
        DocumentItemEntity::class, JournalLineEntity::class
    ],
    version = 1,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun accountDao(): AccountDao
    abstract fun classificationDao(): ClassificationDao
    abstract fun currencyDao(): CurrencyDao
    abstract fun rateDao(): RateDao
    abstract fun warehouseDao(): WarehouseDao
    abstract fun cashboxDao(): CashboxDao
    abstract fun ceilingDao(): CeilingDao
    abstract fun userDao(): UserDao
    abstract fun settingsDao(): SettingsDao
    abstract fun reminderDao(): ReminderDao
    abstract fun yearCloseDao(): YearCloseDao
    abstract fun groupDao(): GroupDao
    abstract fun unitDao(): UnitDao
    abstract fun itemDao(): ItemDao
    abstract fun itemUnitDao(): ItemUnitDao
    abstract fun itemPriceDao(): ItemPriceDao
    abstract fun stockDao(): StockDao
    abstract fun documentDao(): DocumentDao
    abstract fun documentItemDao(): DocumentItemDao
    abstract fun journalDao(): JournalDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "muhasib.db"
                ).addCallback(SEED).build().also { INSTANCE = it }
            }

        private val SEED = object : RoomDatabase.Callback() {
            override fun onCreate(db: SupportSQLiteDatabase) {
                super.onCreate(db)
                seed(db)
            }
        }

        private fun seed(db: SupportSQLiteDatabase) {
            db.execSQL(
                "INSERT INTO accounts (id,name,parentId,kind,classification,phone,address,taxNumber) VALUES " +
                "(1,'الأصول',0,'MAIN','عام','','','')," +
                "(2,'الالتزامات',0,'MAIN','عام','','','')," +
                "(3,'حقوق الملكية',0,'MAIN','عام','','','')," +
                "(4,'الإيرادات',0,'MAIN','عام','','','')," +
                "(5,'المصروفات',0,'MAIN','عام','','','')," +
                "(6,'الصندوق',1,'SUB','عام','','','')," +
                "(7,'البنك',1,'SUB','عام','','','')," +
                "(8,'العملاء',1,'SUB','عام','','','')," +
                "(9,'الموردون',2,'SUB','عام','','','')," +
                "(10,'المخزون',1,'SUB','عام','','','')," +
                "(11,'رأس المال',3,'SUB','عام','','','')," +
                "(12,'المبيعات',4,'SUB','عام','','','')," +
                "(13,'تكلفة البضاعة المباعة',5,'SUB','عام','','','')," +
                "(14,'الخصم التجاري',5,'SUB','عام','','','')," +
                "(15,'الضريبة',2,'SUB','عام','','','')," +
                "(16,'رسوم أخرى',5,'SUB','عام','','','')," +
                "(17,'مصروفات عامة',5,'SUB','عام','','','')," +
                "(18,'إيرادات أخرى',4,'SUB','عام','','','')," +
                "(19,'تلف ونقص مخزني',5,'SUB','عام','','','')," +
                "(20,'الحساب الافتتاحي',3,'SUB','عام','','','')"
            )
            db.execSQL("INSERT INTO currencies (id,name,category,symbol) VALUES (1,'محلي','فلس','YR'),(2,'دولار','سنت','USD')")
            db.execSQL("INSERT INTO warehouses (id,name,phone,address) VALUES (1,'المخزن الرئيسي','','')")
            db.execSQL("INSERT INTO cashboxes (id,name,phone,address,taxNumber,notifySms,notifyWhatsapp,accountId) VALUES (1,'الصندوق','','','',0,0,6)")
            db.execSQL("INSERT INTO item_groups (id,name) VALUES (1,'عام')")
            db.execSQL("INSERT INTO units (id,name,abbrev) VALUES (1,'حبة','حبة'),(2,'كيلو','ك'),(3,'كرتون','كرتون'),(4,'كيس','كيس')")
            db.execSQL("INSERT INTO classifications (id,name) VALUES (1,'عام')")
            db.execSQL("INSERT INTO users (id,name,password,cashboxId,warehouseId,enabled,isAdmin) VALUES (1,'مدير النظام','',1,1,0,1)")
            db.execSQL(
                "INSERT INTO settings (key,value) VALUES " +
                "('tax_percent','0'),('password_enabled','0'),('app_password',''),('lock_minutes','5')," +
                "('allow_edit_price','1'),('allow_edit_qty','1'),('allow_price_below_cost','0')," +
                "('allow_delete_ops','1'),('allow_edit_date','1'),('allow_add_currency','1')," +
                "('allow_add_item','1'),('allow_edit_journal_account','1'),('allow_edit_closed','0')," +
                "('allow_backup','1'),('auto_cashbox','1'),('avg_cost','1'),('auto_price_on_purchase','1')," +
                "('auto_price_in_invoice','0'),('show_time_invoice','0'),('show_expiry_invoice','0')," +
                "('show_tax_purchases','0'),('barcode_enabled','0'),('barcode_type','EAN13')," +
                "('print_copies','1'),('print_personal','1'),('print_base_name','0'),('print_all_currencies','0')," +
                "('note_invoice',''),('note_statement',''),('print_electronic','0'),('close_date','')," +
                "('personal_name',''),('personal_phone',''),('personal_address',''),('personal_tax','')," +
                "('base_currency','1'),('current_warehouse','1'),('current_cashbox','1')"
            )
        }
    }
}
