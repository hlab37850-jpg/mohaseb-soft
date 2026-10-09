package com.muhasib.soft.ui.home

import android.app.DatePickerDialog
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.GravityCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.muhasib.soft.App
import com.muhasib.soft.R
import com.muhasib.soft.databinding.ActivityMainBinding
import com.muhasib.soft.databinding.ItemDrawerBinding
import com.muhasib.soft.ui.doc.DocumentActivity
import com.muhasib.soft.ui.list.ListActivity
import com.muhasib.soft.ui.settings.SettingsHomeActivity
import com.muhasib.soft.util.BackupUtil
import com.muhasib.soft.util.nowFullStr
import com.muhasib.soft.util.todayStr
import com.muhasib.soft.util.shareText
import com.muhasib.soft.util.toast
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Calendar

class MainActivity : AppCompatActivity() {

    private lateinit var b: ActivityMainBinding
    private val adapter = HomeAdapter()
    private val expanded = mutableSetOf<String>()

    private data class Section(val titleRes: Int, val children: List<Pair<Int, String>>)

    private val sections by lazy {
        listOf(
            Section(R.string.inventory_ops, listOf(
                R.string.stock_dispatch to "DOC:DISPATCH",
                R.string.stock_supply to "DOC:SUPPLY",
                R.string.stock_transfer to "DOC:TRANSFER",
                R.string.stock_adjust to "DOC:ADJUST",
                R.string.add_warehouse to "LIST:WAREHOUSES",
                R.string.stock_count to "DOC:COUNT"
            )),
            Section(R.string.vouchers_accounts, listOf(
                R.string.journal_entry to "DOC:JOURNAL",
                R.string.opening_entry to "DOC:OPENING",
                R.string.add_account to "LIST:ACCOUNTS",
                R.string.cashbox_movement to "LIST:REP_CASHBOX_MOVE",
                R.string.chart_of_accounts to "LIST:ACCOUNTS",
                R.string.year_close to "ACTION:CLOSE"
            )),
            Section(R.string.items, listOf(
                R.string.items_list to "LIST:ITEMS",
                R.string.sale_prices to "LIST:PRICES",
                R.string.item_units to "LIST:ITEM_UNITS",
                R.string.quote_invoice to "DOC:QUOTE",
                R.string.purchase_order to "LIST:PURCHASE_ORDERS"
            )),
            Section(R.string.currencies, listOf(
                R.string.currencies to "LIST:CURRENCIES",
                R.string.exchange_rate to "LIST:RATES",
                R.string.ceil_debit to "LIST:CEILINGS"
            )),
            Section(R.string.reports, listOf(
                R.string.item_movement to "LIST:REP_MOVEMENT",
                R.string.trial_balance to "LIST:REP_TRIAL",
                R.string.income_statement to "LIST:REP_INCOME",
                R.string.financial_position to "LIST:REP_FINANCIAL",
                R.string.other_reports to "LIST:REP_OTHERS"
            ))
        )
    }

    private val drawerItems by lazy {
        listOf(
            Triple(R.drawable.ic_backup, R.string.backup, "ACTION:BACKUP"),
            Triple(R.drawable.ic_restore, R.string.restore, "ACTION:RESTORE"),
            Triple(R.drawable.ic_drive, R.string.google_drive, "ACTION:DRIVE"),
            Triple(R.drawable.ic_ledger, R.string.chart_of_accounts, "LIST:ACCOUNTS"),
            Triple(R.drawable.ic_settings, R.string.settings, "ACT:SETTINGS"),
            Triple(R.drawable.ic_phone, R.string.support, "ACTION:SUPPORT"),
            Triple(R.drawable.ic_info, R.string.about, "ACTION:ABOUT"),
            Triple(R.drawable.ic_exit, R.string.exit, "ACTION:EXIT")
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = ActivityMainBinding.inflate(layoutInflater)
        setContentView(b.root)
        setSupportActionBar(b.toolbar)
        BackupUtil.bind(this)

        b.toolbar.setNavigationOnClickListener { b.drawerLayout.openDrawer(GravityCompat.START) }
        b.toolbar.inflateMenu(R.menu.menu_main)
        b.toolbar.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                R.id.action_bell -> route("LIST:REMINDERS")
                R.id.action_share -> shareText(getString(R.string.app_name))
            }
            true
        }

        b.recyclerHome.layoutManager = LinearLayoutManager(this)
        b.recyclerHome.adapter = adapter
        adapter.onHeader = { key ->
            if (expanded.contains(key)) expanded.remove(key) else expanded.add(key)
            rebuildRows()
        }
        adapter.onChild = { tag -> route(tag) }

        b.btnSales.setOnClickListener { route("LIST:SALES") }
        b.btnSalesAdd.setOnClickListener { route("DOC:SALE") }
        b.btnPurchases.setOnClickListener { route("LIST:PURCHASES") }
        b.btnPurchasesAdd.setOnClickListener { route("DOC:PURCHASE") }
        b.btnReceipt.setOnClickListener {
            AlertDialog.Builder(this)
                .setTitle(R.string.receipt_payment)
                .setItems(arrayOf(getString(R.string.capture), getString(R.string.payment))) { _, w ->
                    route(if (w == 0) "DOC:RECEIPT" else "DOC:PAYMENT")
                }.show()
        }
        b.btnAccounts.setOnClickListener { route("LIST:ACCOUNTS") }
        b.btnSwitchWarehouse.setOnClickListener { chooseWarehouse() }
        b.btnWarehouseManage.setOnClickListener { route("LIST:WAREHOUSES") }

        b.drawerList.adapter = DrawerAdapter(this, drawerItems)
        b.drawerList.setOnItemClickListener { _, _, position, _ ->
            b.drawerLayout.closeDrawers()
            route(drawerItems[position].third)
        }

        rebuildRows()
        loadWarehouseName()
    }

    private fun rebuildRows() {
        val rows = mutableListOf<HomeAdapter.Row>()
        sections.forEachIndexed { i, s ->
            val key = "S$i"
            rows.add(HomeAdapter.Row(true, getString(s.titleRes), key))
            if (expanded.contains(key)) {
                s.children.forEach { c -> rows.add(HomeAdapter.Row(false, getString(c.first), c.second)) }
            }
        }
        adapter.submit(rows)
    }

    private fun route(tag: String) {
        val parts = tag.split(":")
        when (parts[0]) {
            "DOC" -> startActivity(Intent(this, DocumentActivity::class.java).putExtra("doc_type", parts[1]))
            "LIST" -> startActivity(Intent(this, ListActivity::class.java).putExtra("list_type", parts[1]))
            "ACT" -> startActivity(Intent(this, SettingsHomeActivity::class.java))
            "ACTION" -> handleAction(parts[1])
        }
    }

    private fun handleAction(a: String) {
        when (a) {
            "BACKUP" -> BackupUtil.save()
            "RESTORE" -> BackupUtil.restore()
            "DRIVE" -> BackupUtil.drive()
            "SUPPORT" -> AlertDialog.Builder(this).setMessage(R.string.support_text)
                .setPositiveButton(R.string.ok, null).show()
            "ABOUT" -> AlertDialog.Builder(this).setMessage(R.string.about_text)
                .setPositiveButton(R.string.ok, null).show()
            "EXIT" -> finishAffinity()
            "CLOSE" -> yearCloseFlow()
        }
    }

    private fun yearCloseFlow() {
        val c = Calendar.getInstance()
        DatePickerDialog(this, { _, y, m, d ->
            val date = String.format("%04d-%02d-%02d", y, m + 1, d)
            AlertDialog.Builder(this)
                .setTitle(getString(R.string.close_until) + " " + date)
                .setMessage(R.string.close_warning)
                .setPositiveButton(R.string.execute_close) { _, _ ->
                    lifecycleScope.launch(Dispatchers.IO) {
                        App.instance.repo.executeYearClose(date, nowFullStr())
                        withContext(Dispatchers.Main) { toast(getString(R.string.saved_successfully)) }
                    }
                }
                .setNegativeButton(R.string.cancel, null)
                .show()
        }, c.get(Calendar.YEAR), c.get(Calendar.MONTH), c.get(Calendar.DAY_OF_MONTH)).show()
    }

    private fun chooseWarehouse() {
        lifecycleScope.launch(Dispatchers.IO) {
            val ws = App.instance.db.warehouseDao().all()
            val names = ws.map { it.name }.toTypedArray()
            withContext(Dispatchers.Main) {
                AlertDialog.Builder(this@MainActivity)
                    .setTitle(R.string.select_warehouse)
                    .setItems(names) { _, w ->
                        lifecycleScope.launch(Dispatchers.IO) {
                            App.instance.repo.setSetting("current_warehouse", ws[w].id.toString())
                            withContext(Dispatchers.Main) { loadWarehouseName() }
                        }
                    }.show()
            }
        }
    }

    private fun loadWarehouseName() {
        lifecycleScope.launch(Dispatchers.IO) {
            val id = App.instance.repo.setting("current_warehouse", "1").toLongOrNull() ?: 1L
            val name = App.instance.db.warehouseDao().byId(id)?.name ?: getString(R.string.main_warehouse)
            withContext(Dispatchers.Main) { b.txtWarehouse.text = name }
        }
    }

    override fun onResume() {
        super.onResume()
        loadWarehouseName()
    }


    override fun onActivityResult(requestCode: Int, resultCode: Int, data: android.content.Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        com.muhasib.soft.util.BackupUtil.onActivityResult(requestCode, resultCode, data)
    }

    private class DrawerAdapter(
        private val ctx: Context,
        private val items: List<Triple<Int, Int, String>>
    ) : BaseAdapter() {
        override fun getCount(): Int = items.size
        override fun getItem(position: Int): Any = items[position]
        override fun getItemId(position: Int): Long = position.toLong()
        override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
            val vb = convertView?.let { ItemDrawerBinding.bind(it) }
                ?: ItemDrawerBinding.inflate(LayoutInflater.from(ctx), parent, false)
            vb.imgIcon.setImageResource(items[position].first)
            vb.txtTitle.setText(items[position].second)
            return vb.root
        }
    }
}
