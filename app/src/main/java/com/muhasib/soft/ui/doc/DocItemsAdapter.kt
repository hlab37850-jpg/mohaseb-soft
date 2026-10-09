package com.muhasib.soft.ui.doc

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.muhasib.soft.data.db.DocumentItemEntity
import com.muhasib.soft.data.db.JournalLineEntity
import com.muhasib.soft.databinding.ItemDocRowBinding
import com.muhasib.soft.databinding.ItemJournalRowBinding
import com.muhasib.soft.util.fmt

class DocItemsAdapter : RecyclerView.Adapter<DocItemsAdapter.VH>() {
    private val data = mutableListOf<DocumentItemEntity>()
    var onDelete: ((DocumentItemEntity) -> Unit)? = null

    fun submit(list: List<DocumentItemEntity>) {
        data.clear()
        data.addAll(list)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH =
        VH(ItemDocRowBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun onBindViewHolder(holder: VH, position: Int) {
        val it = data[position]
        holder.b.txtName.text = it.name
        holder.b.txtQty.text = fmt(it.qty)
        holder.b.txtPrice.text = fmt(it.price)
        holder.b.txtTotal.text = fmt(it.total)
        holder.b.btnDel.setOnClickListener { _ -> onDelete?.invoke(it) }
    }

    override fun getItemCount(): Int = data.size

    class VH(val b: ItemDocRowBinding) : RecyclerView.ViewHolder(b.root)
}

class JournalRowsAdapter : RecyclerView.Adapter<JournalRowsAdapter.VH>() {
    private val data = mutableListOf<JournalLineEntity>()
    var onDelete: ((JournalLineEntity) -> Unit)? = null

    fun submit(list: List<JournalLineEntity>) {
        data.clear()
        data.addAll(list)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH =
        VH(ItemJournalRowBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun onBindViewHolder(holder: VH, position: Int) {
        val it = data[position]
        holder.b.txtAccount.text = it.accountName
        holder.b.txtDebit.text = fmt(it.debit)
        holder.b.txtCredit.text = fmt(it.credit)
        holder.b.txtNote.text = it.note
        holder.b.btnDel.setOnClickListener { _ -> onDelete?.invoke(it) }
    }

    override fun getItemCount(): Int = data.size

    class VH(val b: ItemJournalRowBinding) : RecyclerView.ViewHolder(b.root)
}
