package com.muhasib.soft.ui.list

import android.content.Intent
import android.os.Bundle
import android.text.InputType
import android.view.LayoutInflater
import android.widget.EditText
import android.widget.Spinner
import android.widget.ArrayAdapter
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.muhasib.soft.App
import com.muhasib.soft.R
import com.muhasib.soft.data.db.AccountEntity
import com.muhasib.soft.data.db.CashboxEntity
import com.muhasib.soft.data.db.DocType
import com.muhasib.soft.data.db.DocumentEntity
import com.muhasib.soft.data.db.ItemEntity
import com.muhasib.soft.data.db.JournalLineEntity
import com.muhasib.soft.data.db.ReminderEntity
import com.muhasib.soft.data.db.UserEntity
import com.muhasib.soft.data.db.WarehouseEntity
import com.muhasib.soft.databinding.ActivityListBinding
import com.muhasib.soft.databinding.DialogFormBinding
import com.muhasib.soft.ui.doc.DocumentActivity
import com.muhasib.soft.util.PdfUtil
import com.muhasib.soft.util.ReminderScheduler
import com.muhasib.soft.util.fmt
import com.muhasib.soft.util.hide
import com.muhasib.soft.util.parseD
import com.muhasib.soft.util.shareText
import com.muhasib.soft.util.show
import com.muhasib.soft.util.todayStr
import com.muhasib.soft.util.toast
import com.muhasib.soft.util.whatsapp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ListActivity : AppCompatActivity() {

    private lateinit var b: ActivityListBinding
    private lateinit var type: ListType
    private val adapter = ListAdapter()
    private var rows = listOf<List<String>>()
    private val rowIds = mutableListOf<Long>()
    private var searchQ = ""
    private var fromD = ""
    private var toD = ""
    private var accountId = 0L
    private var lastBalance = 0.0
    private val db get() = App.instance.db

    private data class Field(val key: String, val hint: Int, val number: Boolean = false, val options: List<String>? = null, val default: String = "")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = ActivityListBinding.inflate(layoutInflater)
        setContentView(b.root)
        type = try {
            ListType.valueOf(intent.getStringExtra("list_type") ?: "SALES")
        } catch (e: Exception) {
            ListType.SALES
        }
        accountId = intent.getLongExtra("account_id", 0L)
        setSupportActionBar(b.toolbar)
        b.toolbar.setNavigationOnClickListener { finish() }
        b.toolbar.inflateMenu(R.menu.menu_actions)
        b.toolbar.menu.findItem(R.id.action_save).isVisible = false
        b.toolbar.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                R.id.action_search -> searchDialog()
                R.id.action_pdf, R.id.action_print, R.id.action_print_invoices ->
                    PdfUtil.printList(this, getString(type.titleRes), type.cols.map { getString(it) }, rows)
                R.id.action_new -> fabAction()
                R.id.action_share, R.id.action_message -> shareText(exportText())
                R.id.action_whatsapp -> whatsapp(exportText())
                R.id.action_delete -> Unit
            }
            true
        }
        b.toolbar.menu.findItem(R.id.action_delete).isVisible = false
        ListAdapter.buildColumns(b.columnsContainer, type.cols.map { getString(it) })
        b.recycler.layoutManager = LinearLayoutManager(this)
        b.recycler.adapter = adapter
        adapter.onRow = { rowClick(it) }
        b.fabAdd.setOnClickListener { fabAction() }
        b.btnFooterAction.setOnClickListener { payDialog() }
        load()
    }

    private fun typeLabel(t: String): String = getString(when (t) {
        DocType.SALE -> R.string.sales
        DocType.PURCHASE -> R.string.purchases
        DocType.RECEIPT -> R.string.capture
        DocType.PAYMENT -> R.string.payment
        DocType.JOURNAL -> R.string.journal_entry
        DocType.OPENING -> R.string.opening_entry
        DocType.DISPATCH -> R.string.stock_dispatch
        DocType.SUPPLY -> R.string.stock_supply
        DocType.TRANSFER -> R.string.stock_transfer
        DocType.ADJUST -> R.string.stock_adjust
        DocType.COUNT -> R.string.stock_count
        DocType.QUOTE -> R.string.quote_title
        else -> R.string.purchase_order
    })

    private fun load() {
        lifecycleScope.launch(Dispatchers.IO) {
            val res = query()
            rows = res.first
            lastBalance = res.second
            withContext(Dispatchers.Main) {
                supportActionBar?.title = getString(type.titleRes)
                adapter.submit(rows)
                b.txtEmpty.visibility = if (rows.isEmpty()) android.view.View.VISIBLE else android.view.View.GONE
                b.txtFooterTotal.text = fmt(lastBalance)
                val addable = listOf(ListType.SALES, ListType.PURCHASES, ListType.QUOTES, ListType.PURCHASE_ORDERS, ListType.RECEIPTS, ListType.WAREHOUSES, ListType.CASHBOXES, ListType.ACCOUNTS, ListType.ITEMS, ListType.PRICES, ListType.ITEM_UNITS, ListType.CURRENCIES, ListType.RATES, ListType.CEILINGS, ListType.GROUPS, ListType.UNITS, ListType.CLASSES, ListType.USERS, ListType.REMINDERS)
                b.fabAdd.visibility = if (type in addable) android.view.View.VISIBLE else android.view.View.GONE
                b.btnFooterAction.visibility = if (type == ListType.STATEMENT) android.view.View.VISIBLE else android.view.View.GONE
            }
        }
    }

    private suspend fun query(): Pair<List<List<String>>, Double> {
        val out = mutableListOf<List<String>>()
        rowIds.clear()
        var footer = 0.0
        when (type) {
            ListType.SALES, ListType.PURCHASES, ListType.QUOTES, ListType.PURCHASE_ORDERS -> {
                val t = when (type) {
                    ListType.SALES -> DocType.SALE
                    ListType.PURCHASES -> DocType.PURCHASE
                    ListType.QUOTES -> DocType.QUOTE
                    else -> DocType.PURCHASE_ORDER
                }
                val list = db.documentDao().list(t, searchQ, fromD, toD)
                list.forEach { rowIds.add(it.id); out.add(listOf(it.number.toString(), it.date, it.partyName, fmt(it.net))) }
                footer = db.documentDao().sumNet(t, fromD, toD)
            }
            ListType.RECEIPTS -> {
                val a = db.documentDao().list(DocType.RECEIPT, searchQ, fromD, toD)
                val c = db.documentDao().list(DocType.PAYMENT, searchQ, fromD, toD)
                (a + c).sortedByDescending { it.date }.forEach {
                    rowIds.add(it.id)
                    val first = db.journalDao().byDoc(it.id).firstOrNull { l -> l.accountId != 6L }
                    out.add(listOf(it.number.toString(), it.date, fmt(it.net), first?.accountName ?: "", if (db.documentDao().byId(it.id)?.type == DocType.RECEIPT) getString(R.string.capture) else getString(R.string.payment)))
                    footer += it.net
                }
            }
            ListType.WAREHOUSES -> db.warehouseDao().all().forEach { rowIds.add(it.id); out.add(listOf(it.name, it.phone, it.address)) }
            ListType.CASHBOXES -> db.cashboxDao().all().forEach { rowIds.add(it.id); out.add(listOf(it.name, it.phone, it.address, it.taxNumber)) }
            ListType.ACCOUNTS -> db.accountDao().mains().forEach {
                rowIds.add(it.id)
                out.add(listOf(it.name, "رئيسي", db.accountDao().childCount(it.id).toString()))
            }
            ListType.ITEMS -> {
                val list = if (searchQ.isEmpty()) db.itemDao().all() else db.itemDao().search(searchQ)
                list.forEach { rowIds.add(it.id); out.add(listOf(it.name, it.groupName, fmt(it.openingQty), fmt(it.unitCost))) }
                footer = list.size.toDouble()
            }
            ListType.PRICES -> db.itemPriceDao().all().filter { searchQ.isEmpty() || it.itemName.contains(searchQ, true) }.forEach { rowIds.add(it.id); out.add(listOf(it.itemName, fmt(it.price) + " " + it.symbol, fmt(it.cost), it.date)) }
            ListType.ITEM_UNITS -> db.itemUnitDao().all().forEach { rowIds.add(it.id); out.add(listOf(it.itemName, it.unitName, fmt(it.pack), it.baseUnitName)) }
            ListType.CURRENCIES -> db.currencyDao().all().forEach { rowIds.add(it.id); out.add(listOf(it.name, it.category, it.symbol, it.accountsCount.toString())) }
            ListType.RATES -> db.rateDao().all().forEach { rowIds.add(it.id); out.add(listOf(it.currencyName, fmt(it.rate), it.fromDate, it.toDate)) }
            ListType.CEILINGS -> db.ceilingDao().all().forEach { rowIds.add(it.id); out.add(listOf(it.accountName, it.currencyName, fmt(it.debitCeiling), fmt(it.creditCeiling))) }
            ListType.GROUPS -> db.groupDao().all().forEach { rowIds.add(it.id); out.add(listOf(it.name, it.itemCount.toString())) }
            ListType.UNITS -> db.unitDao().all().forEach { rowIds.add(it.id); out.add(listOf(it.name, it.abbrev)) }
            ListType.CLASSES -> db.classificationDao().all().forEach { rowIds.add(it.id); out.add(listOf(it.name, it.accountCount.toString())) }
            ListType.USERS -> db.userDao().all().forEach {
                rowIds.add(it.id)
                out.add(listOf(it.name, db.cashboxDao().byId(it.cashboxId)?.name ?: "", db.warehouseDao().byId(it.warehouseId)?.name ?: ""))
            }
            ListType.REMINDERS -> db.reminderDao().all().forEach { rowIds.add(it.id); out.add(listOf(it.name, it.date, it.time, it.note)) }
            ListType.STATEMENT -> {
                val list = db.journalDao().statement(accountId, 0L)
                var bal = 0.0
                list.forEach {
                    bal += it.debit - it.credit
                    out.add(listOf(it.date, it.note.ifEmpty { it.accountName }, fmt(it.debit), fmt(it.credit), fmt(bal)))
                }
                lastBalance = bal
                footer = bal
                return Pair(out, footer)
            }
            ListType.JOURNAL_LIST, ListType.REP_DAILY_JOURNALS -> {
                val docs = db.documentDao().byType(DocType.JOURNAL, fromD, toD) + db.documentDao().byType(DocType.OPENING, fromD, toD)
                docs.forEach { d ->
                    db.journalDao().byDoc(d.id).forEach { l ->
                        out.add(listOf(d.date, d.number.toString(), l.accountName, fmt(l.debit), fmt(l.credit)))
                        footer += l.debit
                    }
                }
            }
            ListType.REP_MOVEMENT -> db.documentItemDao().movement(0, fromD, toD).forEach {
                out.add(listOf(it.itemName, typeLabel(it.docType), it.warehouseName, fmt(it.qty), fmt(it.price), fmt(it.total)))
                footer += it.total
            }
            ListType.REP_REMAINING -> db.stockDao().remaining().forEach {
                out.add(listOf(it.itemName, it.warehouseName, fmt(it.qty), fmt(it.price), fmt(it.cost), fmt(it.total)))
                footer += it.total
            }
            ListType.REP_BALANCES -> db.journalDao().trial(1L).forEach {
                out.add(listOf(it.accountName, fmt(it.debit - it.credit)))
                footer += it.debit - it.credit
            }
            ListType.REP_TRIAL -> db.journalDao().trial(1L).forEach {
                out.add(listOf(it.accountName, fmt(it.debit), fmt(it.credit)))
                footer += it.debit
            }
            ListType.REP_INCOME -> {
                val t = db.journalDao().trial(1L)
                t.filter { it.parentId == 4L }.forEach { out.add(listOf(it.accountName, fmt(it.credit - it.debit))); footer += it.credit - it.debit }
                t.filter { it.parentId == 5L }.forEach { out.add(listOf(it.accountName, fmt(it.debit - it.credit))); footer -= it.debit - it.credit }
                out.add(listOf(getString(R.string.net_profit), fmt(footer)))
            }
            ListType.REP_FINANCIAL -> {
                val t = db.journalDao().trial(1L)
                val assets = t.filter { it.parentId == 1L }.map { it.accountName to (it.debit - it.credit) }
                val liab = t.filter { it.parentId == 2L || it.parentId == 3L }.map { it.accountName to (it.credit - it.debit) }
                val n = maxOf(assets.size, liab.size)
                for (i in 0 until n) {
                    val l = assets.getOrNull(i)?.let { it.first + ": " + fmt(it.second) } ?: ""
                    val r = liab.getOrNull(i)?.let { it.first + ": " + fmt(it.second) } ?: ""
                    out.add(listOf(l, r))
                }
                footer = assets.sumOf { it.second }
            }
            ListType.REP_OTHERS -> {
                val others = listOf(ListType.REP_REMAINING, ListType.REP_BALANCES, ListType.REP_ITEM_PROFITS, ListType.REP_DAILY_OPS, ListType.REP_DAILY_JOURNALS, ListType.REP_CASHBOX_MOVE, ListType.REP_ACCOUNTS_MOVE, ListType.REP_CLASS_TOTALS, ListType.REP_CURRENCY_DIFF, ListType.REP_WORKING, ListType.REP_CUSTOMER_PROFIT, ListType.REP_SALES_BY_ITEM, ListType.REP_PURCHASES_BY_ITEM, ListType.REP_MOVEMENT_DETAILS)
                others.forEach { rowIds.add(it.ordinal.toLong()); out.add(listOf(getString(it.titleRes))) }
            }
            ListType.REP_DAILY_OPS -> {
                val all = mutableListOf<DocumentEntity>()
                listOf(DocType.SALE, DocType.PURCHASE, DocType.RECEIPT, DocType.PAYMENT, DocType.DISPATCH, DocType.SUPPLY).forEach { t -> all.addAll(db.documentDao().byType(t, fromD, toD)) }
                all.sortedBy { it.date }.forEach { out.add(listOf(it.date, it.number.toString(), typeLabel(it.type), fmt(it.net))); footer += it.net }
            }
            ListType.REP_CASHBOX_MOVE -> {
                val cashAccs = db.cashboxDao().all().map { it.accountId }
                val all = mutableListOf<DocumentEntity>()
                listOf(DocType.SALE, DocType.PURCHASE, DocType.RECEIPT, DocType.PAYMENT, DocType.JOURNAL).forEach { t -> all.addAll(db.documentDao().byType(t, fromD, toD)) }
                all.forEach { d ->
                    db.journalDao().byDoc(d.id).filter { it.accountId in cashAccs }.forEach { l ->
                        out.add(listOf(d.date, d.number.toString(), l.accountName, fmt(l.debit - l.credit)))
                        footer += l.debit - l.credit
                    }
                }
            }
            ListType.REP_ACCOUNTS_MOVE -> {
                val all = mutableListOf<DocumentEntity>()
                listOf(DocType.SALE, DocType.PURCHASE, DocType.RECEIPT, DocType.PAYMENT, DocType.JOURNAL, DocType.OPENING).forEach { t -> all.addAll(db.documentDao().byType(t, fromD, toD)) }
                all.forEach { d -> db.journalDao().byDoc(d.id).forEach { l -> out.add(listOf(d.date, l.accountName, fmt(l.debit), fmt(l.credit))) } }
            }
            ListType.REP_CLASS_TOTALS -> {
                val t = db.journalDao().trial(1L)
                db.accountDao().all().filter { it.kind == "SUB" }.groupBy { it.classification }.forEach { (cls, accs) ->
                    val sum = accs.sumOf { a -> t.firstOrNull { it.accountId == a.id }?.let { it.debit - it.credit } ?: 0.0 }
                    out.add(listOf(cls, fmt(sum)))
                    footer += sum
                }
            }
            ListType.REP_CURRENCY_DIFF -> db.rateDao().all().forEach { out.add(listOf(it.currencyName, it.fromDate, fmt(it.rate))) }
            ListType.REP_WORKING -> {
                val t = db.journalDao().trial(1L)
                listOf(6L, 7L, 8L, 10L).forEach { id ->
                    val a = t.firstOrNull { it.accountId == id }
                    out.add(listOf(a?.accountName ?: "", fmt(a?.let { it.debit - it.credit } ?: 0.0)))
                    footer += a?.let { it.debit - it.credit } ?: 0.0
                }
                val sup = t.firstOrNull { it.accountId == 9L }
                out.add(listOf(sup?.accountName ?: "", fmt(-(sup?.let { it.credit - it.debit } ?: 0.0))))
                footer -= sup?.let { it.credit - it.debit } ?: 0.0
            }
            ListType.REP_CUSTOMER_PROFIT -> {
                val sales = db.documentDao().byType(DocType.SALE, fromD, toD)
                sales.groupBy { it.partyName.ifEmpty { "-" } }.forEach { (name, docs) ->
                    var profit = 0.0
                    docs.forEach { d ->
                        profit += d.net
                        profit -= db.documentItemDao().byDoc(d.id).sumOf { it.qty * it.cost }
                    }
                    out.add(listOf(name, fmt(profit)))
                    footer += profit
                }
            }
            ListType.REP_ITEM_PROFITS -> {
                val mv = db.documentItemDao().movement(0, fromD, toD).filter { it.docType == DocType.SALE }
                mv.groupBy { it.itemName }.forEach { (name, rs) ->
                    val rev = rs.sumOf { it.total }
                    val cost = rs.sumOf { it.qty * it.cost }
                    out.add(listOf(name, fmt(rev - cost), fmt(rev)))
                    footer += rev - cost
                }
            }
            ListType.REP_SALES_BY_ITEM -> db.documentItemDao().movement(0, fromD, toD).filter { it.docType == DocType.SALE }.groupBy { it.itemName }.forEach { (name, rs) -> out.add(listOf(name, fmt(rs.sumOf { it.qty }), fmt(rs.sumOf { it.total }))); footer += rs.sumOf { it.total } }
            ListType.REP_PURCHASES_BY_ITEM -> db.documentItemDao().movement(0, fromD, toD).filter { it.docType == DocType.PURCHASE }.groupBy { it.itemName }.forEach { (name, rs) -> out.add(listOf(name, fmt(rs.sumOf { it.qty }), fmt(rs.sumOf { it.total }))); footer += rs.sumOf { it.total } }
            ListType.REP_MOVEMENT_DETAILS -> db.documentItemDao().movement(0, fromD, toD).forEach { out.add(listOf(it.date, it.itemName, typeLabel(it.docType), fmt(it.qty), fmt(it.total))); footer += it.total }
        }
        return Pair(out, footer)
    }

    private fun exportText(): String {
        val sb = StringBuilder(getString(type.titleRes)).append("\n")
        rows.forEach { r -> sb.append(r.joinToString(" | ")).append("\n") }
        sb.append(getString(R.string.total_sum)).append(": ").append(fmt(lastBalance))
        return sb.toString()
    }

    private fun searchDialog() {
        formDialog(R.string.search, listOf(
            Field("q", R.string.search, default = searchQ),
            Field("from", R.string.from_date, default = fromD),
            Field("to", R.string.to_date, default = toD)
        )) { v ->
            searchQ = v["q"] ?: ""
            fromD = v["from"] ?: ""
            toD = v["to"] ?: ""
            load()
        }
    }

    private fun formDialog(title: Int, fields: List<Field>, onOk: (Map<String, String>) -> Unit) {
        val vb = DialogFormBinding.inflate(layoutInflater)
        val views = mutableMapOf<String, android.view.View>()
        fields.forEach { f ->
            if (f.options != null) {
                val sp = Spinner(this)
                sp.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, f.options)
                vb.formContainer.addView(sp)
                views[f.key] = sp
            } else {
                val et = EditText(this)
                et.setHint(f.hint)
                if (f.number) et.inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
                et.setText(f.default)
                vb.formContainer.addView(et)
                views[f.key] = et
            }
        }
        val dialog = AlertDialog.Builder(this).setTitle(title).setView(vb.root).create()
        vb.btnOk.setOnClickListener {
            val map = mutableMapOf<String, String>()
            fields.forEach { f ->
                map[f.key] = when (val v = views[f.key]) {
                    is Spinner -> v.selectedItem?.toString() ?: ""
                    is EditText -> v.text.toString().trim()
                    else -> ""
                }
            }
            dialog.dismiss()
            onOk(map)
        }
        vb.btnCancel.setOnClickListener { dialog.dismiss() }
        dialog.show()
    }

    private fun rowClick(pos: Int) {
        val id = rowIds.getOrNull(pos) ?: return
        when (type) {
            ListType.SALES, ListType.PURCHASES, ListType.QUOTES, ListType.PURCHASE_ORDERS, ListType.RECEIPTS ->
                lifecycleScope.launch(Dispatchers.IO) {
                    val d = db.documentDao().byId(id) ?: return@launch
                    withContext(Dispatchers.Main) {
                        startActivity(Intent(this@ListActivity, DocumentActivity::class.java).putExtra("doc_type", d.type).putExtra("doc_id", id))
                    }
                }
            ListType.ACCOUNTS -> lifecycleScope.launch(Dispatchers.IO) {
                val subs = db.accountDao().all().filter { it.parentId == id }
                val names = subs.map { it.name }.toTypedArray()
                withContext(Dispatchers.Main) {
                    if (names.isEmpty()) { toast(getString(R.string.no_results)); return@withContext }
                    AlertDialog.Builder(this@ListActivity).setItems(names) { _, w ->
                        startActivity(Intent(this@ListActivity, ListActivity::class.java).putExtra("list_type", ListType.STATEMENT.name).putExtra("account_id", subs[w].id))
                    }.show()
                }
            }
            ListType.REP_OTHERS -> {
                val targets = listOf(ListType.REP_REMAINING, ListType.REP_BALANCES, ListType.REP_ITEM_PROFITS, ListType.REP_DAILY_OPS, ListType.REP_DAILY_JOURNALS, ListType.REP_CASHBOX_MOVE, ListType.REP_ACCOUNTS_MOVE, ListType.REP_CLASS_TOTALS, ListType.REP_CURRENCY_DIFF, ListType.REP_WORKING, ListType.REP_CUSTOMER_PROFIT, ListType.REP_SALES_BY_ITEM, ListType.REP_PURCHASES_BY_ITEM, ListType.REP_MOVEMENT_DETAILS)
                startActivity(Intent(this, ListActivity::class.java).putExtra("list_type", targets[pos].name))
            }
            ListType.WAREHOUSES -> lifecycleScope.launch(Dispatchers.IO) {
                val w = db.warehouseDao().byId(id) ?: return@launch
                withContext(Dispatchers.Main) { warehouseForm(w) }
            }
            ListType.ITEMS -> lifecycleScope.launch(Dispatchers.IO) {
                val it = db.itemDao().byId(id) ?: return@launch
                withContext(Dispatchers.Main) { itemForm(it) }
            }
            ListType.PRICES -> lifecycleScope.launch(Dispatchers.IO) {
                val p = db.itemPriceDao().all().firstOrNull { it.id == id } ?: return@launch
                withContext(Dispatchers.Main) {
                    AlertDialog.Builder(this@ListActivity).setMessage(R.string.confirm_delete)
                        .setPositiveButton(R.string.yes) { _, _ ->
                            lifecycleScope.launch(Dispatchers.IO) {
                                db.itemPriceDao().delete(com.muhasib.soft.data.db.ItemPriceEntity(p.id, p.itemId, p.currencyId, p.price, p.cost, p.date))
                                withContext(Dispatchers.Main) { load() }
                            }
                        }.setNegativeButton(R.string.no, null).show()
                }
            }
            ListType.REMINDERS -> lifecycleScope.launch(Dispatchers.IO) {
                val r = db.reminderDao().byId(id) ?: return@launch
                withContext(Dispatchers.Main) { reminderForm(r) }
            }
            ListType.USERS -> lifecycleScope.launch(Dispatchers.IO) {
                val u = db.userDao().all().firstOrNull { it.id == id } ?: return@launch
                withContext(Dispatchers.Main) { userForm(u) }
            }
            else -> Unit
        }
    }

    private fun fabAction() {
        when (type) {
            ListType.SALES -> openDoc(DocType.SALE)
            ListType.PURCHASES -> openDoc(DocType.PURCHASE)
            ListType.QUOTES -> openDoc(DocType.QUOTE)
            ListType.PURCHASE_ORDERS -> openDoc(DocType.PURCHASE_ORDER)
            ListType.RECEIPTS -> AlertDialog.Builder(this).setItems(arrayOf(getString(R.string.capture), getString(R.string.payment))) { _, w -> openDoc(if (w == 0) DocType.RECEIPT else DocType.PAYMENT) }.show()
            ListType.WAREHOUSES -> warehouseForm(null)
            ListType.CASHBOXES -> cashboxForm()
            ListType.ACCOUNTS -> accountForm()
            ListType.ITEMS -> itemForm(null)
            ListType.PRICES -> priceForm()
            ListType.ITEM_UNITS -> itemUnitForm()
            ListType.CURRENCIES -> lifecycleScope.launch(Dispatchers.IO) {
                if (App.instance.repo.setting("allow_add_currency", "1") != "1") { withContext(Dispatchers.Main) { toast(getString(R.string.required_right)) }; return@launch }
                withContext(Dispatchers.Main) { currencyForm() }
            }
            ListType.RATES -> rateForm()
            ListType.CEILINGS -> ceilingForm()
            ListType.GROUPS -> formDialog(R.string.item_groups, listOf(Field("name", R.string.group_name))) { v -> io { db.groupDao().insert(com.muhasib.soft.data.db.ItemGroupEntity(name = v["name"] ?: "")) } }
            ListType.UNITS -> formDialog(R.string.add_unit, listOf(Field("name", R.string.unit_name), Field("ab", R.string.abbrev))) { v -> io { db.unitDao().insert(com.muhasib.soft.data.db.UnitEntity(name = v["name"] ?: "", abbrev = v["ab"] ?: "")) } }
            ListType.CLASSES -> formDialog(R.string.add_class, listOf(Field("name", R.string.classification))) { v -> io { db.classificationDao().insert(com.muhasib.soft.data.db.ClassificationEntity(name = v["name"] ?: "")) } }
            ListType.USERS -> userForm(null)
            ListType.REMINDERS -> reminderForm(null)
            else -> Unit
        }
    }

    private fun openDoc(t: String) = startActivity(Intent(this, DocumentActivity::class.java).putExtra("doc_type", t))

    private fun io(block: suspend () -> Unit) {
        lifecycleScope.launch(Dispatchers.IO) { block(); withContext(Dispatchers.Main) { load() } }
    }

    private fun warehouseForm(w: WarehouseEntity?) = formDialog(R.string.add_warehouse, listOf(
        Field("name", R.string.warehouse_name, default = w?.name ?: ""),
        Field("phone", R.string.phone, default = w?.phone ?: ""),
        Field("addr", R.string.address, default = w?.address ?: "")
    )) { v ->
        io {
            if (w == null) db.warehouseDao().insert(WarehouseEntity(name = v["name"] ?: "", phone = v["phone"] ?: "", address = v["addr"] ?: ""))
            else db.warehouseDao().update(w.copy(name = v["name"] ?: "", phone = v["phone"] ?: "", address = v["addr"] ?: ""))
        }
    }

    private fun cashboxForm() = formDialog(R.string.add_cashbox, listOf(
        Field("name", R.string.name), Field("phone", R.string.phone),
        Field("addr", R.string.address), Field("tax", R.string.tax_number)
    )) { v ->
        io { db.cashboxDao().insert(CashboxEntity(name = v["name"] ?: "", phone = v["phone"] ?: "", address = v["addr"] ?: "", taxNumber = v["tax"] ?: "", accountId = 6L)) }
    }

    private fun accountForm() = lifecycleScope.launch(Dispatchers.IO) {
        val mains = db.accountDao().mains()
        val names = mains.map { it.name }
        withContext(Dispatchers.Main) {
            formDialog(R.string.add_account, listOf(
                Field("name", R.string.account_name),
                Field("parent", R.string.main_account, options = names),
                Field("cls", R.string.classification, default = "عام"),
                Field("phone", R.string.phone), Field("addr", R.string.address), Field("tax", R.string.tax_number)
            )) { v ->
                io {
                    val parent = mains.firstOrNull { it.name == v["parent"] }?.id ?: 1L
                    db.accountDao().insert(AccountEntity(name = v["name"] ?: "", parentId = parent, classification = v["cls"] ?: "عام", phone = v["phone"] ?: "", address = v["addr"] ?: "", taxNumber = v["tax"] ?: ""))
                }
            }
        }
    }

    private fun itemForm(it: ItemEntity?) = lifecycleScope.launch(Dispatchers.IO) {
        val groups = db.groupDao().all().map { g -> g.name }
        val units = db.unitDao().all().map { u -> u.name }
        withContext(Dispatchers.Main) {
            formDialog(R.string.items_list, listOf(
                Field("name", R.string.item_name, default = it?.name ?: ""),
                Field("group", R.string.group, options = groups.ifEmpty { listOf("عام") }),
                Field("unit", R.string.base_unit, options = units.ifEmpty { listOf("حبة") }),
                Field("oq", R.string.opening_qty, number = true, default = it?.let { x -> fmt(x.openingQty) } ?: ""),
                Field("cost", R.string.unit_cost, number = true, default = it?.let { x -> fmt(x.unitCost) } ?: ""),
                Field("exp", R.string.expiry, default = it?.expiry ?: ""),
                Field("notes", R.string.notes, default = it?.notes ?: "")
            )) { v ->
                io {
                    val gid = db.groupDao().all().firstOrNull { g -> g.name == v["group"] }?.let { x -> x.id } ?: 1L
                    val uid = db.unitDao().all().firstOrNull { u -> u.name == v["unit"] }?.let { x -> x.id } ?: 1L
                    if (it == null) {
                        App.instance.repo.saveItemWithOpening(ItemEntity(name = v["name"] ?: "", groupId = gid, baseUnitId = uid, openingQty = parseD(v["oq"]), unitCost = parseD(v["cost"]), expiry = v["exp"] ?: "", notes = v["notes"] ?: ""), 1L)
                    } else {
                        db.itemDao().update(it.copy(name = v["name"] ?: "", groupId = gid, baseUnitId = uid, unitCost = parseD(v["cost"]), expiry = v["exp"] ?: "", notes = v["notes"] ?: ""))
                    }
                }
            }
        }
    }

    private fun priceForm() = formDialog(R.string.add_price, listOf(
        Field("item", R.string.item_name), Field("price", R.string.sale_price, number = true),
        Field("cost", R.string.cost, number = true), Field("date", R.string.price_date, default = todayStr())
    )) { v ->
        io {
            val item = db.itemDao().all().firstOrNull { x -> x.name == v["item"] } ?: return@io
            db.itemPriceDao().insert(com.muhasib.soft.data.db.ItemPriceEntity(itemId = item.id, currencyId = 1L, price = parseD(v["price"]), cost = parseD(v["cost"]), date = v["date"] ?: todayStr()))
        }
    }

    private fun itemUnitForm() = formDialog(R.string.item_units, listOf(
        Field("item", R.string.item_name), Field("unit", R.string.unit),
        Field("pack", R.string.pack, number = true, default = "1")
    )) { v ->
        io {
            val item = db.itemDao().all().firstOrNull { x -> x.name == v["item"] } ?: return@io
            val unit = db.unitDao().all().firstOrNull { x -> x.name == v["unit"] } ?: return@io
            db.itemUnitDao().insert(com.muhasib.soft.data.db.ItemUnitEntity(itemId = item.id, unitId = unit.id, pack = parseD(v["pack"], 1.0)))
        }
    }

    private fun currencyForm() = formDialog(R.string.add_currency, listOf(
        Field("name", R.string.currency), Field("cat", R.string.category), Field("sym", R.string.symbol)
    )) { v ->
        io { db.currencyDao().insert(com.muhasib.soft.data.db.CurrencyEntity(name = v["name"] ?: "", category = v["cat"] ?: "", symbol = v["sym"] ?: "")) }
    }

    private fun rateForm() = formDialog(R.string.exchange_rate, listOf(
        Field("cur", R.string.currency, options = listOf(getString(R.string.currency_local), getString(R.string.currency_usd))),
        Field("rate", R.string.exchange_rate, number = true),
        Field("from", R.string.from_date, default = todayStr())
    )) { v ->
        io {
            val curId = if (v["cur"] == getString(R.string.currency_usd)) 2L else 1L
            db.rateDao().insert(com.muhasib.soft.data.db.CurrencyRateEntity(currencyId = curId, rate = parseD(v["rate"], 1.0), fromDate = v["from"] ?: todayStr()))
        }
    }

    private fun ceilingForm() = formDialog(R.string.ceil_debit, listOf(
        Field("acc", R.string.account_name), Field("deb", R.string.ceil_debit, number = true), Field("cre", R.string.ceil_credit, number = true)
    )) { v ->
        io {
            val acc = db.accountDao().all().firstOrNull { x -> x.name == v["acc"] } ?: return@io
            db.ceilingDao().insert(com.muhasib.soft.data.db.AccountCeilingEntity(accountId = acc.id, currencyId = 1L, debitCeiling = parseD(v["deb"]), creditCeiling = parseD(v["cre"])))
        }
    }

    private fun userForm(u: UserEntity?) = lifecycleScope.launch(Dispatchers.IO) {
        val boxes = db.cashboxDao().all().map { it.name }
        val whs = db.warehouseDao().all().map { it.name }
        withContext(Dispatchers.Main) {
            formDialog(R.string.add_user, listOf(
                Field("name", R.string.user_name, default = u?.name ?: ""),
                Field("pass", R.string.password, default = u?.password ?: ""),
                Field("box", R.string.cashbox, options = boxes.ifEmpty { listOf(getString(R.string.cashbox)) }),
                Field("wh", R.string.warehouse, options = whs.ifEmpty { listOf(getString(R.string.main_warehouse)) })
            )) { v ->
                io {
                    val boxId = db.cashboxDao().all().firstOrNull { x -> x.name == v["box"] }?.id ?: 1L
                    val whId = db.warehouseDao().all().firstOrNull { x -> x.name == v["wh"] }?.id ?: 1L
                    if (u == null) db.userDao().insert(UserEntity(name = v["name"] ?: "", password = v["pass"] ?: "", cashboxId = boxId, warehouseId = whId, enabled = true))
                    else db.userDao().update(u.copy(name = v["name"] ?: "", password = v["pass"] ?: "", cashboxId = boxId, warehouseId = whId))
                }
            }
        }
    }

    private fun reminderForm(r: ReminderEntity?) = formDialog(R.string.add_reminder, listOf(
        Field("name", R.string.name, default = r?.name ?: ""),
        Field("date", R.string.date, default = r?.date ?: todayStr()),
        Field("time", R.string.time, default = r?.time ?: "09:00"),
        Field("note", R.string.note, default = r?.note ?: "")
    )) { v ->
        io {
            val entity = ReminderEntity(id = r?.id ?: 0L, name = v["name"] ?: "", date = v["date"] ?: todayStr(), time = v["time"] ?: "09:00", note = v["note"] ?: "")
            val id = if (r == null) db.reminderDao().insert(entity) else { db.reminderDao().update(entity); entity.id }
            val saved = db.reminderDao().byId(id)
            if (saved != null) ReminderScheduler.schedule(this@ListActivity, saved)
        }
    }

    private fun payDialog() {
        lifecycleScope.launch(Dispatchers.IO) {
            val boxes = db.cashboxDao().all()
            val names = boxes.map { it.name }
            withContext(Dispatchers.Main) {
                formDialog(R.string.pay_amount, listOf(
                    Field("from", R.string.from_account, options = names.ifEmpty { listOf(getString(R.string.cashbox)) }),
                    Field("amt", R.string.amount, number = true, default = fmt(Math.abs(lastBalance))),
                    Field("date", R.string.date, default = todayStr()),
                    Field("note", R.string.notes)
                )) { v ->
                    io {
                        val amt = parseD(v["amt"])
                        if (amt <= 0) return@io
                        val box = boxes.firstOrNull { x -> x.name == v["from"] } ?: boxes.firstOrNull() ?: return@io
                        val cashAcc = db.cashboxDao().byId(box.id)?.accountId ?: 6L
                        val accName = db.accountDao().byId(accountId)?.name ?: ""
                        val cashName = db.accountDao().byId(cashAcc)?.name ?: ""
                        val isReceipt = lastBalance > 0
                        val t = if (isReceipt) DocType.RECEIPT else DocType.PAYMENT
                        val number = App.instance.repo.nextNumber(t)
                        val lines = if (isReceipt) {
                            listOf(
                                JournalLineEntity(accountId = accountId, accountName = accName, currencyId = 1L, debit = 0.0, credit = amt),
                                JournalLineEntity(accountId = cashAcc, accountName = cashName, currencyId = 1L, debit = amt, credit = 0.0)
                            )
                        } else {
                            listOf(
                                JournalLineEntity(accountId = accountId, accountName = accName, currencyId = 1L, debit = amt, credit = 0.0),
                                JournalLineEntity(accountId = cashAcc, accountName = cashName, currencyId = 1L, debit = 0.0, credit = amt)
                            )
                        }
                        val doc = DocumentEntity(type = t, number = number, date = v["date"] ?: todayStr(), partyAccountId = accountId, partyName = accName, cashboxId = box.id, currencyId = 1L, notes = v["note"] ?: "", total = amt, net = amt, remaining = 0.0)
                        App.instance.repo.saveDocument(doc, emptyList(), lines)
                    }
                }
            }
        }
    }
}
