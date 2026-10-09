package com.muhasib.soft.ui.settings

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.muhasib.soft.databinding.ItemSettingsBinding

class SettingsAdapter : RecyclerView.Adapter<SettingsAdapter.VH>() {
    data class Item(val icon: Int, val title: Int, val tag: String)
    private val data = mutableListOf<Item>()
    var onClick: ((String) -> Unit)? = null

    fun submit(list: List<Item>) {
        data.clear()
        data.addAll(list)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH =
        VH(ItemSettingsBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun onBindViewHolder(holder: VH, position: Int) {
        val it = data[position]
        holder.b.imgIcon.setImageResource(it.icon)
        holder.b.txtTitle.setText(it.title)
        holder.itemView.setOnClickListener { onClick?.invoke(it.tag) }
    }

    override fun getItemCount(): Int = data.size
    class VH(val b: ItemSettingsBinding) : RecyclerView.ViewHolder(b.root)
}
