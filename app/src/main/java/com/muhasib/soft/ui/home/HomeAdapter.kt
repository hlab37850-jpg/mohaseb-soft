package com.muhasib.soft.ui.home

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.muhasib.soft.databinding.ItemSectionChildBinding
import com.muhasib.soft.databinding.ItemSectionHeaderBinding

class HomeAdapter : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    data class Row(val header: Boolean, val title: String, val key: String)

    private val data = mutableListOf<Row>()
    var onHeader: ((String) -> Unit)? = null
    var onChild: ((String) -> Unit)? = null

    fun submit(list: List<Row>) {
        data.clear()
        data.addAll(list)
        notifyDataSetChanged()
    }

    override fun getItemViewType(position: Int): Int = if (data[position].header) 0 else 1

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inf = LayoutInflater.from(parent.context)
        return if (viewType == 0) {
            HeaderVH(ItemSectionHeaderBinding.inflate(inf, parent, false))
        } else {
            ChildVH(ItemSectionChildBinding.inflate(inf, parent, false))
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val row = data[position]
        if (holder is HeaderVH) {
            holder.b.txtTitle.text = row.title
            holder.itemView.setOnClickListener { onHeader?.invoke(row.key) }
        } else {
            holder.b.txtChild.text = row.title
            holder.itemView.setOnClickListener { onChild?.invoke(row.key) }
        }
    }

    override fun getItemCount(): Int = data.size

    class HeaderVH(val b: ItemSectionHeaderBinding) : RecyclerView.ViewHolder(b.root)
    class ChildVH(val b: ItemSectionChildBinding) : RecyclerView.ViewHolder(b.root)
}
