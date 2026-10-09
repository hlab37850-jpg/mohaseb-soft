package com.muhasib.soft.ui.doc

import android.app.DatePickerDialog
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.ListPopupWindow
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.muhasib.soft.App
import com.muhasib.soft.R
import com.muhasib.soft.data.db.AdjustKind
import com.muhasib.soft.data.db.CashboxEntity
import com.muhasib.soft.data.db.CurrencyEntity
import com.muhasib.soft.data.db.DocType
import com.muhasib.soft.data.db.DocumentEntity
import com.muhasib.soft.data.db.DocumentItemEntity
import com.muhasib.soft.data.db.ItemEntity
import com.muhasib.soft.data.db.JournalLineEntity
import com.muhasib.soft.data.db.WarehouseEntity
import com.muhasib.soft.databinding.ActivityDocumentBinding
import com.muhasib.soft.databinding.DialogDiscountBinding
import com.muhasib.soft.databinding.DialogItemEntryBinding
import com.muhasib.soft.util.PdfUtil
import com.muhasib.soft.util.fmt
import com.muhasib.soft.util.hide
import com.muhasib.soft.util.nowFullStr
import com.muhasib.soft.util.parseD
import com.muhasib.soft.util.shareText
import com.muhasib.soft.util.show
import com.muhasib.soft.util.todayStr
import com.muhasib.soft.util.toast
import com.muhasib.soft.util.whatsapp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Calendar

class DocumentActivity : AppCompatActivity() {

    private lateinit var b: ActivityDocumentBinding
    private var docType: String = DocType.SALE
    private var docId = 0L
    private val items = mutableListOf<DocumentItemEntity>()
    private val lines = mutableListOf<JournalLineEntity>()
    private val itemsAdapter = DocItemsAdapter()
    private val journalAdapter = JournalRowsAdapter()

    private var warehouses = listOf<WarehouseEntity>()
    private var currencies = listOf<CurrencyEntity>()
    private var cashboxes = listOf<CashboxEntity>()
    private var itemCache = listOf<com.muhasib.soft.data.db.ItemFullRow>()
    private var accountCache = listOf<com.muhasib.soft.data.db.AccountEntity>()

    private var dateStr = todayStr()
    private var number = 1
    private var partyAccountId = 0L
    private var discountIsPercent = false
    private var discountVal = 0.0
    private var taxPercent = 0.0
    private var fees = 0.0
    private var feesNote = ""
    private var paid = 0.0

    private val isJournalFamily: Boolean
        get() = docType == DocType.RECEIPT || docType == DocType.PAYMENT ||
                docType == DocType.JOURNAL || docType == DocType.OPENING

    private val discountAmt: Double
        get() = if (discountIsPercent) itemsTotal() * discountVal / 100.0 else discountVal
    private val taxAmt: Double
        get() = itemsTotal() * taxPercent / 100.0
    private fun itemsTotal(): Double =
        if (isJournalFamily) lines.sumOf { it.debit + it.credit } / 2.0 else items.sumOf { it.total }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = ActivityDocumentBinding.inflate(layoutInflater)
        setContentView(b.root)
        docType = intent.getStringExtra("doc_type") ?: DocType.SALE
        docId = intent.getLongExtra("doc_id", 0L)
        setSupportActionBar(b.toolbar)
        b.toolbar.setNavigationOnClickListener { finish() }
        b.toolbar.inflateMenu(R.menu.menu_actions)
        b.toolbar.menu.findItem(R.id.action_search).isVisible = false
        b.toolbar.menu.findItem(R.id.action_print_invoices).isVisible = false
        b.toolbar.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                R.id.action_save -> save()
                R.id.action_pdf, R.id.action_print -> PdfUtil.printDoc(this, currentDoc(), items.toList(), lines.toList())
                R.id.action_new -> {
                    finish()
                    startActivity(intent)
                }
                R.id.action_delete -> deleteDoc()
                R.id.action_share, R.id.action_message -> shareText(summary())
                R.id.action_whatsapp -> whatsapp(summary())
            }
            true
        }

        b.recyclerItems.layoutManager = LinearLayoutManager(this)
        b.recyclerItems.adapter = itemsAdapter
        b.recyclerItems.adapter = journalAdapter
        itemsAdapter.onDelete = { it -> items.remove(it); refresh() }
        journalAdapter.onDelete = { it -> lines.remove(it); refresh() }

        b.txtDate.setOnClickListener { pickDate() }
        b.footerBox.setOnClickListener { discountDialog() }
        b.btnAddItem.setOnClickListener {
            if (isJournalFamily) journalRowDialog() else addItemFromInput()
        }
        b.switchCredit.setOnCheckedChangeListener { _, _ -> updateHeader() }
        b.chkReference.setOnCheckedChangeListener { _, _ -> updateHeader() }

        val radios = listOf(b.radioOpening, b.radioDamaged, b.radioSurplus, b.radioShortage)
        radios.forEach { r ->
            r.setOnCheckedChangeListener { _, checked ->
                if (checked) radios.filter { it != r }.forEach { it.isChecked = false }
            }
        }
        b.radioOpening.isChecked = true

        setupItemPopup()
        setupPartyPopup()

        lifecycleScope.launch(Dispatchers.IO) {
            val db = App.instance.db
            warehouses = db.warehouseDao().all()
            currencies = db.currencyDao().all().map { com.muhasib.soft.data.db.CurrencyEntity(it.id, it.name, it.category, it.symbol) }
            cashboxes = db.cashboxDao().all()
            itemCache = db.itemDao().all()
            accountCache = db.accountDao().all()
            number = if (docId > 0) db.documentDao().byId(docId)?.number ?: 1 else App.instance.repo.nextNumber(docType)
            if (docId > 0) loadExisting()
            withContext(Dispatchers.Main) {
                bindSpinners()
                updateHeader()
                refresh()
                if (docId == 0L && docType == DocType.COUNT) countPrefill()
            }
        }
    }

    private suspend fun loadExisting() {
        val d = App.instance.db.documentDao().byId(docId) ?: return
        dateStr = d.date
        number = d.number
        partyAccountId = d.partyAccountId
        discountIsPercent = d.discountIsPercent
        discountVal = d.discount
        taxPercent = d.taxPercent
        fees = d.otherFees
        feesNote = d.otherFeesNote
        paid = d.paid
        items.clear()
        items.addAll(App.instance.db.documentItemDao().byDoc(docId))
        lines.clear()
        lines.addAll(App.instance.db.journalDao().byDoc(docId))
        withContext(Dispatchers.Main) {
            b.editParty.setText(d.partyName)
            b.editNotes.setText(d.notes)
            b.switchCredit.isChecked = d.isCredit
            b.chkReference.isChecked = d.isReference
            when (d.adjustKind) {
                AdjustKind.DAMAGED -> b.radioDamaged.isChecked = true
                AdjustKind.SURPLUS -> b.radioSurplus.isChecked = true
                AdjustKind.SHORTAGE -> b.radioShortage.isChecked = true
                else -> b.radioOpening.isChecked = true
            }
        }
    }

    private fun bindSpinners() {
        val whNames = warehouses.map { it.name }
        b.spinWarehouse.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, whNames)
        b.spinToWarehouse.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, whNames)
        b.spinCurrency.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, currencies.map { it.name })
        b.spinCashbox.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, cashboxes.map { it.name })
        if (docId > 0) lifecycleScope.launch(Dispatchers.IO) {
            val d = App.instance.db.documentDao().byId(docId) ?: return@launch
            withContext(Dispatchers.Main) {
                warehouses.indexOfFirst { it.id == d.warehouseId }.let { if (it >= 0) b.spinWarehouse.setSelection(it) }
                warehouses.indexOfFirst { it.id == d.toWarehouseId }.let { if (it >= 0) b.spinToWarehouse.setSelection(it) }
                currencies.indexOfFirst { it.id == d.currencyId }.let { if (it >= 0) b.spinCurrency.setSelection(it) }
                cashboxes.indexOfFirst { it.id == d.cashboxId }.let { if (it >= 0) b.spinCashbox.setSelection(it) }
            }
        }
    }

    private fun updateHeader() {
        val credit = b.switchCredit.isChecked
        val ref = if (b.chkReference.isChecked) getString(R.string.ref_prefix) + " " else ""
        val title = when (docType) {
            DocType.SALE -> ref + getString(if (credit) R.string.sale_credit else R.string.sale_cash)
            DocType.PURCHASE -> ref + getString(if (credit) R.string.purchase_credit else R.string.purchase_cash)
            DocType.QUOTE -> getString(R.string.quote_title)
            DocType.PURCHASE_ORDER -> getString(R.string.purchase_order)
            DocType.RECEIPT -> getString(R.string.receipt_voucher)
            DocType.PAYMENT -> getString(R.string.payment_voucher)
            DocType.JOURNAL -> getString(R.string.journal_voucher)
            DocType.OPENING -> getString(R.string.opening_entry)
            DocType.DISPATCH -> getString(R.string.stock_dispatch)
            DocType.SUPPLY -> getString(R.string.stock_supply)
            DocType.TRANSFER -> getString(R.string.stock_transfer)
            DocType.ADJUST -> getString(R.string.stock_adjust)
            DocType.COUNT -> getString(R.string.stock_count)
            else -> getString(R.string.app_name)
        }
        b.toolbar.title = title
        supportActionBar?.title = title
        b.txtNumber.text = getString(R.string.number) + "#" + number
        b.txtDate.text = dateStr
        val cashVisible = (docType == DocType.SALE || docType == DocType.PURCHASE) && !credit
        b.spinCashbox.visibility = if (cashVisible) android.view.View.VISIBLE else android.view.View.GONE
        b.spinToWarehouse.visibility = if (docType == DocType.TRANSFER) android.view.View.VISIBLE else android.view.View.GONE
        b.rowAdjust.visibility = if (docType == DocType.ADJUST) android.view.View.VISIBLE else android.view.View.GONE
        val partyVisible = !isJournalFamily && docType != DocType.ADJUST && docType != DocType.COUNT && docType != DocType.TRANSFER
        b.txtPartyLabel.visibility = if (partyVisible) android.view.View.VISIBLE else android.view.View.GONE
        b.editParty.visibility = if (partyVisible) android.view.View.VISIBLE else android.view.View.GONE
        b.txtPartyLabel.setText(when (docType) {
            DocType.PURCHASE, DocType.PURCHASE_ORDER -> R.string.supplier
            DocType.DISPATCH, DocType.SUPPLY -> R.string.account
            else -> R.string.customer
        })
        if (isJournalFamily) b.editItemInput.setHint(R.string.entry) else b.editItemInput.setHint(R.string.write_item_name)
    }

    private var itemPopup: ListPopupWindow? = null
    private var selectedItemId = 0L

    private fun setupItemPopup() {
        val popup = ListPopupWindow(this)
        val adapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, mutableListOf<String>())
        popup.setAdapter(adapter)
        popup.anchorView = b.editItemInput
        popup.setOnItemClickListener { _, pos, _, _ ->
            val name = adapter.getItem(pos) ?: return@setOnItemClickListener
            b.editItemInput.setText(name)
            selectedItemId = itemCache.firstOrNull { it.name == name }?.id ?: 0L
            popup.dismiss()
        }
        itemPopup = popup
        b.editItemInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun afterTextChanged(s: Editable?) {
                if (isJournalFamily) return
                val q = s?.toString()?.trim() ?: ""
                selectedItemId = 0L
                if (q.isEmpty()) { popup.dismiss(); return }
                val matches = itemCache.filter { it.name.contains(q, true) }.map { it.name }
                adapter.clear()
                adapter.addAll(matches)
                adapter.notifyDataSetChanged()
                if (matches.isNotEmpty() && b.editItemInput.hasFocus() && !popup.isShowing) popup.show()
            }
        })
    }

    private var partyPopup: ListPopupWindow? = null

    private fun setupPartyPopup() {
        val popup = ListPopupWindow(this)
        val adapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, mutableListOf<String>())
        popup.setAdapter(adapter)
        popup.anchorView = b.editParty
        popup.setOnItemClickListener { _, pos, _, _ ->
            val name = adapter.getItem(pos) ?: return@setOnItemClickListener
            b.editParty.setText(name)
            partyAccountId = accountCache.firstOrNull { it.name == name }?.id ?: 0L
            popup.dismiss()
        }
        partyPopup = popup
        b.editParty.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun afterTextChanged(s: Editable?) {
                val q = s?.toString()?.trim() ?: ""
                partyAccountId = 0L
                if (q.isEmpty()) { popup.dismiss(); return }
                val pool = accountCache.filter {
                    when (docType) {
                        DocType.SALE, DocType.QUOTE -> it.parentId == 8L
                        DocType.PURCHASE, DocType.PURCHASE_ORDER -> it.parentId == 9L
                        else -> true
                    }
                }
                val matches = pool.filter { it.name.contains(q, true) }.map { it.name }
                adapter.clear()
                adapter.addAll(matches)
                adapter.notifyDataSetChanged()
                if (matches.isNotEmpty() && b.editParty.hasFocus() && !popup.isShowing) popup.show()
            }
        })
    }

    private fun addItemFromInput() {
        val name = b.editItemInput.text.toString().trim()
        if (name.isEmpty()) return
        val existing = itemCache.firstOrNull { it.name.equals(name, true) }
        if (existing != null) {
            openItemDialog(existing.id, existing.name, existing)
            return
        }
        lifecycleScope.launch(Dispatchers.IO) {
            if (App.instance.repo.setting("allow_add_item", "1") != "1") {
                withContext(Dispatchers.Main) { toast(getString(R.string.required_right)) }
                return@launch
            }
            val id = App.instance.db.itemDao().insert(ItemEntity(name = name, groupId = 1L))
            val row = com.muhasib.soft.data.db.ItemFullRow(id, name, "", 0.0, 0.0, 0.0)
            itemCache = itemCache + row
            withContext(Dispatchers.Main) { openItemDialog(id, name, row) }
        }
    }

    private fun openItemDialog(itemId: Long, name: String, info: com.muhasib.soft.data.db.ItemFullRow) {
        val vb = DialogItemEntryBinding.inflate(layoutInflater)
        val dialog = AlertDialog.Builder(this).setView(vb.root).create()
        val isPurchase = docType == DocType.PURCHASE || docType == DocType.PURCHASE_ORDER
        val isCostOnly = docType == DocType.DISPATCH || docType == DocType.SUPPLY || docType == DocType.ADJUST
        val isCount = docType == DocType.COUNT
        val isTransfer = docType == DocType.TRANSFER
        var cost = info.unitCost
        var prevQty = 0.0
        if (isCount) {
            lifecycleScope.launch(Dispatchers.IO) {
                prevQty = App.instance.db.stockDao().get(itemId, currentWarehouseId())?.qty ?: 0.0
                withContext(Dispatchers.Main) { vb.editQty.setText(fmt(prevQty)); vb.editPrice.setText(fmt(cost)) }
            }
        }
        vb.editQty.setText("1")
        if (isPurchase) vb.editPrice.setText("0") else if (!isCostOnly && !isCount) {
            lifecycleScope.launch(Dispatchers.IO) {
                val p = App.instance.db.itemPriceDao().latest(itemId)
                withContext(Dispatchers.Main) { vb.editPrice.setText(fmt(p?.price ?: 0.0)) }
            }
        }
        if (isCostOnly) vb.editPrice.setHint(R.string.cost)
        if (isTransfer) { vb.editPrice.hide(); vb.editTotal.hide(); vb.btnOther.hide() }
        fun recompute(fromTotal: Boolean) {
            val q = parseD(vb.editQty.text.toString(), 0.0)
            if (fromTotal) {
                val t = parseD(vb.editTotal.text.toString(), 0.0)
                if (q > 0) vb.editPrice.setText(fmt(t / q))
            } else {
                val p = parseD(vb.editPrice.text.toString(), 0.0)
                vb.editTotal.setText(fmt(q * p))
            }
        }
        vb.editPrice.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun afterTextChanged(s: Editable?) { recompute(false) }
        })
        vb.editTotal.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun afterTextChanged(s: Editable?) { recompute(true) }
        })
        vb.btnOther.setOnClickListener {
            val extra = EditText(this)
            extra.setHint(R.string.notes)
            AlertDialog.Builder(this).setView(extra)
                .setPositiveButton(R.string.ok, null).setNegativeButton(R.string.cancel, null).show()
        }
        vb.btnAdd.setOnClickListener {
            val q = parseD(vb.editQty.text.toString(), 0.0)
            val p = parseD(vb.editPrice.text.toString(), 0.0)
            val rowCost = when {
                isPurchase -> p
                isCostOnly -> p
                isCount -> cost
                else -> cost
            }
            val row = DocumentItemEntity(
                docId = docId, itemId = itemId, name = name, qty = q,
                price = if (isCount) prevQty else p, cost = rowCost,
                total = if (isCount || isTransfer) 0.0 else q * p
            )
            items.add(row)
            refresh()
            dialog.dismiss()
            b.editItemInput.setText("")
        }
        vb.btnCancel.setOnClickListener { dialog.dismiss() }
        vb.btnClose.setOnClickListener { dialog.dismiss(); b.editItemInput.setText("") }
        dialog.show()
    }

    private fun journalRowDialog() {
        val container = androidx.appcompat.widget.AlertDialogLayout(this)
        val layout = android.widget.LinearLayout(this)
        layout.orientation = android.widget.LinearLayout.VERTICAL
        layout.setPadding(32, 16, 32, 0)
        val editAccount = EditText(this)
        editAccount.setHint(R.string.account)
        val editAmount = EditText(this)
        editAmount.setHint(R.string.amount)
        editAmount.inputType = android.text.InputType.TYPE_CLASS_NUMBER or android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL
        val editNote = EditText(this)
        editNote.setHint(R.string.notes)
        layout.addView(editAccount)
        layout.addView(editAmount)
        layout.addView(editNote)
        container.addView(layout)
        var accId = 0L
        editAccount.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun afterTextChanged(s: Editable?) {
                accId = accountCache.firstOrNull { it.name == s?.toString()?.trim() }?.id ?: 0L
            }
        })
        AlertDialog.Builder(this)
            .setView(container)
            .setPositiveButton(R.string.add) { _, _ ->
                val amt = parseD(editAmount.text.toString(), 0.0)
                val accName = editAccount.text.toString().trim()
                val note = editNote.text.toString().trim()
                if (accId == 0L || amt == 0.0) { toast(getString(R.string.no_results)); return@setPositiveButton }
                when (docType) {
                    DocType.RECEIPT -> lines.add(JournalLineEntity(docId = docId, accountId = accId, accountName = accName, currencyId = currentCurrencyId(), debit = 0.0, credit = amt, note = note))
                    DocType.PAYMENT -> lines.add(JournalLineEntity(docId = docId, accountId = accId, accountName = accName, currencyId = currentCurrencyId(), debit = amt, credit = 0.0, note = note))
                    else -> lines.add(JournalLineEntity(docId = docId, accountId = accId, accountName = accName, currencyId = currentCurrencyId(), debit = amt, credit = 0.0, note = note))
                }
                refresh()
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun countPrefill() {
        val whId = currentWarehouseId()
        val whName = warehouses.firstOrNull { it.id == whId }?.name ?: ""
        AlertDialog.Builder(this)
            .setMessage(getString(R.string.count_confirm_msg, whName, dateStr))
            .setPositiveButton(R.string.yes) { _, _ ->
                lifecycleScope.launch(Dispatchers.IO) {
                    val rows = App.instance.db.stockDao().ofWarehouse(whId)
                    withContext(Dispatchers.Main) {
                        rows.forEach { r ->
                            items.add(DocumentItemEntity(docId = docId, itemId = r.itemId, name = r.itemName, qty = r.qty, price = r.qty, cost = r.cost, total = 0.0))
                        }
                        refresh()
                    }
                }
            }
            .setNegativeButton(R.string.no, null)
            .show()
    }

    private fun discountDialog() {
        val vb = DialogDiscountBinding.inflate(layoutInflater)
        vb.radioPercent.isChecked = discountIsPercent
        vb.editDiscount.setText(fmt(discountVal))
        vb.editTaxPercent.setText(fmt(taxPercent))
        vb.editFees.setText(fmt(fees))
        vb.editFeesNote.setText(feesNote)
        vb.editPaid.setText(fmt(paid))
        AlertDialog.Builder(this).setView(vb.root)
            .setOnCancelListener { }
            .create().also { d ->
                vb.btnOk.setOnClickListener {
                    discountIsPercent = vb.radioPercent.isChecked
                    discountVal = parseD(vb.editDiscount.text.toString())
                    taxPercent = parseD(vb.editTaxPercent.text.toString())
                    fees = parseD(vb.editFees.text.toString())
                    feesNote = vb.editFeesNote.text.toString().trim()
                    paid = parseD(vb.editPaid.text.toString())
                    refresh()
                    d.dismiss()
                }
                vb.btnCancel.setOnClickListener { d.dismiss() }
                d.show()
            }
    }

    private fun currentWarehouseId(): Long =
        warehouses.getOrNull(b.spinWarehouse.selectedItemPosition)?.id ?: 1L

    private fun currentCurrencyId(): Long =
        currencies.getOrNull(b.spinCurrency.selectedItemPosition)?.id ?: 1L

    private fun currentCashboxId(): Long =
        cashboxes.getOrNull(b.spinCashbox.selectedItemPosition)?.id ?: 1L

    private fun adjustKind(): String = when {
        b.radioDamaged.isChecked -> AdjustKind.DAMAGED
        b.radioSurplus.isChecked -> AdjustKind.SURPLUS
        b.radioShortage.isChecked -> AdjustKind.SHORTAGE
        else -> AdjustKind.OPENING
    }

    private fun refresh() {
        if (isJournalFamily) {
            journalAdapter.submit(lines.toList())
            b.txtEmpty.visibility = if (lines.isEmpty()) android.view.View.VISIBLE else android.view.View.GONE
        } else {
            itemsAdapter.submit(items.toList())
            b.txtEmpty.visibility = if (items.isEmpty()) android.view.View.VISIBLE else android.view.View.GONE
        }
        b.btnClearItem.visibility = if (b.editItemInput.text.isNotEmpty()) android.view.View.VISIBLE else android.view.View.GONE
        val total = itemsTotal()
        b.txtSum.text = fmt(total)
        b.txtDiscount.text = fmt(discountAmt)
        b.txtTax.text = fmt(taxAmt)
        val net = total - discountAmt + taxAmt + fees
        b.txtNet.text = fmt(net)
        b.txtRemaining.text = fmt(net - paid)
    }

    private fun currentDoc(): DocumentEntity {
        val total = itemsTotal()
        val net = total - discountAmt + taxAmt + fees
        return DocumentEntity(
            id = docId, type = docType, number = number, date = dateStr,
            partyAccountId = partyAccountId,
            partyName = b.editParty.text.toString().trim(),
            warehouseId = currentWarehouseId(),
            toWarehouseId = if (docType == DocType.TRANSFER) warehouses.getOrNull(b.spinToWarehouse.selectedItemPosition)?.id ?: 0L else 0L,
            cashboxId = currentCashboxId(),
            currencyId = currentCurrencyId(),
            notes = b.editNotes.text.toString().trim(),
            isCredit = b.switchCredit.isChecked,
            isReference = b.chkReference.isChecked,
            adjustKind = if (docType == DocType.ADJUST) adjustKind() else if (docType == DocType.COUNT) AdjustKind.SURPLUS else "",
            discountIsPercent = discountIsPercent,
            discount = discountAmt,
            taxPercent = taxPercent,
            tax = taxAmt,
            otherFees = fees,
            otherFeesNote = feesNote,
            paid = paid,
            total = total,
            net = net,
            remaining = net - paid,
            createdAt = nowFullStr()
        )
    }

    private fun save() {
        if (isJournalFamily && lines.isEmpty()) { toast(getString(R.string.no_results)); return }
        if (!isJournalFamily && items.isEmpty()) { toast(getString(R.string.no_results)); return }
        if ((docType == DocType.JOURNAL || docType == DocType.OPENING) &&
            Math.abs(lines.sumOf { it.debit } - lines.sumOf { it.credit }) > 0.0001
        ) { toast(getString(R.string.unbalanced)); return }
        AlertDialog.Builder(this)
            .setMessage(R.string.save_q)
            .setPositiveButton(R.string.yes) { _, _ ->
                lifecycleScope.launch(Dispatchers.IO) {
                    val doc = currentDoc()
                    val provided = if (isJournalFamily) buildJournalLines() else emptyList()
                    App.instance.repo.saveDocument(doc, items.toList(), provided)
                    withContext(Dispatchers.Main) {
                        toast(getString(R.string.saved_successfully))
                        finish()
                    }
                }
            }
            .setNegativeButton(R.string.no, null)
            .show()
    }

    private fun buildJournalLines(): List<JournalLineEntity> {
        val cur = currentCurrencyId()
        if (docType == DocType.RECEIPT || docType == DocType.PAYMENT) {
            val sum = lines.sumOf { it.debit + it.credit }
            val cashAcc = App.instance.db.cashboxDao().byId(currentCashboxId())?.accountId ?: 6L
            val cashName = App.instance.db.accountDao().byId(cashAcc)?.name ?: ""
            val out = lines.toMutableList()
            if (docType == DocType.RECEIPT) out.add(JournalLineEntity(docId = docId, accountId = cashAcc, accountName = cashName, currencyId = cur, debit = sum, credit = 0.0))
            else out.add(JournalLineEntity(docId = docId, accountId = cashAcc, accountName = cashName, currencyId = cur, debit = 0.0, credit = sum))
            return out
        }
        return lines.toList()
    }

    private fun deleteDoc() {
        if (docId == 0L) return
        lifecycleScope.launch(Dispatchers.IO) {
            if (App.instance.repo.setting("allow_delete_ops", "1") != "1") {
                withContext(Dispatchers.Main) { toast(getString(R.string.required_right)) }
                return@launch
            }
            val d = App.instance.db.documentDao().byId(docId) ?: return@launch
            withContext(Dispatchers.Main) {
                AlertDialog.Builder(this@DocumentActivity)
                    .setMessage(R.string.confirm_delete)
                    .setPositiveButton(R.string.yes) { _, _ ->
                        lifecycleScope.launch(Dispatchers.IO) {
                            App.instance.repo.deleteDocument(d)
                            withContext(Dispatchers.Main) {
                                toast(getString(R.string.deleted_successfully))
                                finish()
                            }
                        }
                    }
                    .setNegativeButton(R.string.no, null).show()
            }
        }
    }

    private fun summary(): String {
        val d = currentDoc()
        val sb = StringBuilder()
        sb.append(getString(R.string.app_name)).append("\n")
        sb.append(b.toolbar.title).append(" ")
        sb.append(getString(R.string.number)).append("#").append(d.number).append("\n")
        sb.append(getString(R.string.date)).append(": ").append(d.date).append("\n")
        if (d.partyName.isNotEmpty()) sb.append(getString(R.string.account)).append(": ").append(d.partyName).append("\n")
        items.forEach { sb.append(it.name).append(" x ").append(fmt(it.qty)).append(" = ").append(fmt(it.total)).append("\n") }
        lines.forEach { sb.append(it.accountName).append(": ").append(fmt(it.debit + it.credit)).append("\n") }
        sb.append(getString(R.string.net)).append(": ").append(fmt(d.net)).append("\n")
        sb.append(getString(R.string.remaining)).append(": ").append(fmt(d.remaining))
        return sb.toString()
    }

    private fun pickDate() {
        val parts = dateStr.split("-")
        val y = parts.getOrNull(0)?.toIntOrNull() ?: 2026
        val m = (parts.getOrNull(1)?.toIntOrNull() ?: 1) - 1
        val dd = parts.getOrNull(2)?.toIntOrNull() ?: 1
        DatePickerDialog(this, { _, yy, mm, d ->
            dateStr = String.format("%04d-%02d-%02d", yy, mm + 1, d)
            b.txtDate.text = dateStr
        }, y, m, dd).show()
    }
}
