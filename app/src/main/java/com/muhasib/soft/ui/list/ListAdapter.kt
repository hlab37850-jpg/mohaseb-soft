package com.muhasib.soft.ui.list

import android.graphics.Color
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.muhasib.soft.databinding.ItemListRowBinding
import com.muhasib.soft.util.hide
import com.muhasib.soft.util.show

class ListAdapter : RecyclerView.Adapter<ListAdapter.VH>() {

    private val data = mutableListOf<List<String>>()
    var onRow: ((Int) -> Unit)? = null
    var onAction: ((Int) -> Unit)? = null
    var showAction = false

    fun submit(list: List<List<String>>) {
        data.clear()
        data.addAll(list)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH =
        VH(ItemListRowBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun onBindViewHolder(holder: VH, position: Int) {
        val row = data[position]
        val cols = listOf(holder.b.col1, holder.b.col2, holder.b.col3, holder.b.col4, holder.b.col5, holder.b.col6)
        cols.forEachIndexed { i, tv ->
            if (i < row.size) {
                tv.text = row[i]
                tv.show()
            } else {
                tv.hide()
            }
        }
        if (showAction) {
            holder.b.btnRowAction.show()
            holder.b.btnRowAction.setOnClickListener { onAction?.invoke(position) }
        } else {
            holder.b.btnRowAction.hide()
        }
        holder.itemView.setOnClickListener { onRow?.invoke(position) }
    }

    override fun getItemCount(): Int = data.size

    class VH(val b: ItemListRowBinding) : RecyclerView.ViewHolder(b.root)

    companion object {
        fun buildColumns(container: LinearLayout, titles: List<String>) {
            container.removeAllViews()
            titles.forEach { t ->
                val tv = TextView(container.context)
                tv.text = t
                tv.setTextColor(Color.WHITE)
                tv.setPadding(12, 4, 12, 4)
                tv.minWidth = 160
                container.addView(tv)
            }
        }
    }
}
