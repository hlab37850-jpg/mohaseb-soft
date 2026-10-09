package com.muhasib.soft.ui.settings

import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import android.widget.EditText
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.muhasib.soft.App
import com.muhasib.soft.R
import com.muhasib.soft.databinding.ActivityListBinding
import com.muhasib.soft.ui.list.ListActivity
import com.muhasib.soft.ui.list.ListType
import com.muhasib.soft.util.fmt
import com.muhasib.soft.util.hide
import com.muhasib.soft.util.parseD
import com.muhasib.soft.util.toast
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class SettingsHomeActivity : AppCompatActivity() {

    private lateinit var b: ActivityListBinding
    private val adapter = SettingsAdapter()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = ActivityListBinding.inflate(layoutInflater)
        setContentView(b.root)
        setSupportActionBar(b.toolbar)
        b.toolbar.title = getString(R.string.settings)
        b.toolbar.setNavigationOnClickListener { finish() }
        b.fabAdd.hide()
        b.columnsContainer.hide()
        b.btnFooterAction.hide()
        b.txtFooterTotal.text = ""
        b.recycler.layoutManager = LinearLayoutManager(this)
        b.recycler.adapter = adapter

        adapter.submit(listOf(
            SettingsAdapter.Item(R.drawable.ic_person, R.string.personal_data, "personal"),
            SettingsAdapter.Item(R.drawable.ic_print, R.string.print_options, "print"),
            SettingsAdapter.Item(R.drawable.ic_settings, R.string.security_options, "security"),
            SettingsAdapter.Item(R.drawable.ic_person, R.string.users_permissions, "LIST:USERS"),
            SettingsAdapter.Item(R.drawable.ic_ledger, R.string.classifications, "LIST:CLASSES"),
            SettingsAdapter.Item(R.drawable.ic_box, R.string.item_groups, "LIST:GROUPS"),
            SettingsAdapter.Item(R.drawable.ic_box, R.string.units_of_measure, "LIST:UNITS"),
            SettingsAdapter.Item(R.drawable.ic_save, R.string.save_options, "save"),
            SettingsAdapter.Item(R.drawable.ic_chart, R.string.tax, "tax"),
            SettingsAdapter.Item(R.drawable.ic_print, R.string.thermal_printer, "thermal"),
            SettingsAdapter.Item(R.drawable.ic_chart, R.string.barcode_print, "barcode_print"),
            SettingsAdapter.Item(R.drawable.ic_bell, R.string.notifications_options, "LIST:REMINDERS"),
            SettingsAdapter.Item(R.drawable.ic_settings, R.string.other_options, "other")
        ))
        adapter.onClick = { tag -> handle(tag) }
    }

    private fun handle(tag: String) {
        when (tag) {
            "personal" -> personalForm()
            "print" -> toggles("print", listOf(
                "print_copies" to R.string.print_copies,
                "print_personal_data" to R.string.print_personal_data,
                "show_base_item" to R.string.show_base_item,
                "print_all_currencies" to R.string.print_all_currencies,
                "print_electronic" to R.string.print_electronic
            ))
            "security" -> toggles("security", listOf(
                "password_enabled" to R.string.password_on_start,
                "allow_edit_price" to R.string.allow_edit_sale_price,
                "allow_edit_qty" to R.string.allow_edit_qty,
                "allow_price_below_cost" to R.string.allow_price_below,
                "allow_delete_ops" to R.string.allow_delete_ops,
                "allow_edit_date" to R.string.allow_edit_date,
                "allow_add_currency" to R.string.allow_add_currency,
                "allow_add_item" to R.string.allow_add_item,
                "allow_edit_journal_account" to R.string.allow_edit_journal,
                "allow_edit_closed" to R.string.allow_edit_closed,
                "allow_backup" to R.string.allow_backup_operation
            ))
            "save" -> toggles("save", listOf(
                "auto_cashbox" to R.string.auto_cashbox,
                "avg_cost" to R.string.avg_cost,
                "auto_price_on_purchase" to R.string.auto_price_purchase,
                "auto_price_in_invoice" to R.string.auto_price_invoice,
                "show_time_invoice" to R.string.show_time_invoice,
                "show_expiry_invoice" to R.string.show_expiry_invoice,
                "show_tax_purchases" to R.string.show_tax_purchases
            ))
            "tax" -> taxForm()
            "thermal" -> toast(getString(R.string.print_thermal_note))
            "barcode_print" -> toggles("barcode", listOf(
                "barcode_enabled" to R.string.enable_barcode
            ))
            "other" -> toggles("other", listOf(
                "enable_barcode" to R.string.enable_barcode
            ))
            else -> {
                val parts = tag.split(":")
                if (parts.size == 2) {
                    val lt = try { ListType.valueOf(parts[1]) } catch (e: Exception) { null }
                    if (lt != null) startActivity(Intent(this, ListActivity::class.java).putExtra("list_type", lt.name))
                }
            }
        }
    }

    private fun personalForm() {
        lifecycleScope.launch(Dispatchers.IO) {
            val repo = App.instance.repo
            val current = mapOf(
                "personal_name" to repo.setting("personal_name"),
                "personal_phone" to repo.setting("personal_phone"),
                "personal_address" to repo.setting("personal_address"),
                "personal_tax" to repo.setting("personal_tax"),
                "note_invoice" to repo.setting("note_invoice"),
                "note_statement" to repo.setting("note_statement")
            )
            withContext(Dispatchers.Main) {
                val layout = android.widget.LinearLayout(this@SettingsHomeActivity).apply { orientation = android.widget.LinearLayout.VERTICAL; setPadding(32, 16, 32, 0) }
                val edits = mutableMapOf<String, EditText>()
                listOf(
                    "personal_name" to R.string.personal_data,
                    "personal_phone" to R.string.phone,
                    "personal_address" to R.string.address,
                    "personal_tax" to R.string.tax_number,
                    "note_invoice" to R.string.note_below_invoice,
                    "note_statement" to R.string.note_below_statement
                ).forEach { (k, h) ->
                    val et = EditText(this@SettingsHomeActivity)
                    et.setHint(h)
                    et.setText(current[k] ?: "")
                    layout.addView(et)
                    edits[k] = et
                }
                AlertDialog.Builder(this@SettingsHomeActivity)
                    .setTitle(R.string.personal_data)
                    .setView(layout)
                    .setPositiveButton(R.string.save) { _, _ ->
                        lifecycleScope.launch(Dispatchers.IO) {
                            edits.forEach { (k, et) -> repo.setSetting(k, et.text.toString().trim()) }
                            withContext(Dispatchers.Main) { toast(getString(R.string.saved_successfully)) }
                        }
                    }
                    .setNegativeButton(R.string.cancel, null)
                    .show()
            }
        }
    }

    private fun taxForm() {
        lifecycleScope.launch(Dispatchers.IO) {
            val v = App.instance.repo.setting("tax_percent", "0")
            withContext(Dispatchers.Main) {
                val et = EditText(this@SettingsHomeActivity)
                et.inputType = android.text.InputType.TYPE_CLASS_NUMBER or android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL
                et.setText(v)
                AlertDialog.Builder(this@SettingsHomeActivity)
                    .setTitle(R.string.tax_value)
                    .setView(et)
                    .setPositiveButton(R.string.save) { _, _ ->
                        lifecycleScope.launch(Dispatchers.IO) {
                            App.instance.repo.setSetting("tax_percent", fmt(parseD(et.text.toString())))
                            withContext(Dispatchers.Main) { toast(getString(R.string.saved_successfully)) }
                        }
                    }
                    .setNegativeButton(R.string.cancel, null)
                    .show()
            }
        }
    }

    private fun toggles(title: String, items: List<Pair<String, Int>>) {
        startActivity(Intent(this, TogglesActivity::class.java)
            .putStringArrayListExtra("keys", ArrayList(items.map { it.first }))
            .putIntegerArrayListExtra("labels", ArrayList(items.map { it.second }))
            .putExtra("title", title))
    }
}
