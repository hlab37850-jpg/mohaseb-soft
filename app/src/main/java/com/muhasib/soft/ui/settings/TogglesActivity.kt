package com.muhasib.soft.ui.settings

import android.os.Bundle
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.muhasib.soft.App
import com.muhasib.soft.databinding.ActivityListBinding
import com.muhasib.soft.databinding.ItemSettingsToggleBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class TogglesActivity : AppCompatActivity() {

    private lateinit var b: ActivityListBinding
    private var items = listOf<Pair<String, Int>>()
    private val values = mutableMapOf<String, Boolean>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = ActivityListBinding.inflate(layoutInflater)
        setContentView(b.root)
        setSupportActionBar(b.toolbar)
        b.toolbar.setNavigationOnClickListener { finish() }
        b.fabAdd.visibility = android.view.View.GONE
        b.columnsContainer.visibility = android.view.View.GONE
        b.btnFooterAction.visibility = android.view.View.GONE
        b.txtFooterTotal.text = ""
        b.toolbar.title = intent.getStringExtra("title") ?: ""

        val keys = intent.getStringArrayListExtra("keys") ?: emptyList<String>()
        val labels = intent.getIntegerArrayListExtra("labels") ?: emptyList<Int>()
        items = keys.zip(labels)

        b.recycler.layoutManager = LinearLayoutManager(this)
        b.recycler.adapter = Adapter()

        lifecycleScope.launch(Dispatchers.IO) {
            items.forEach { (k, _) -> values[k] = App.instance.repo.setting(k, "0") == "1" }
            withContext(Dispatchers.Main) { b.recycler.adapter?.notifyDataSetChanged() }
        }
    }

    private inner class Adapter : RecyclerView.Adapter<Adapter.VH>() {
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH =
            VH(ItemSettingsToggleBinding.inflate(LayoutInflater.from(parent.context), parent, false))
        override fun getItemCount(): Int = items.size
        override fun onBindViewHolder(holder: VH, position: Int) {
            val (key, label) = items[position]
            holder.b.txtTitle.setText(label)
            holder.b.switchValue.setOnCheckedChangeListener(null)
            holder.b.switchValue.isChecked = values[key] == true
            holder.b.switchValue.setOnCheckedChangeListener { _, v ->
                values[key] = v
                lifecycleScope.launch(Dispatchers.IO) {
                    App.instance.repo.setSetting(key, if (v) "1" else "0")
                }
            }
        }
        inner class VH(val b: ItemSettingsToggleBinding) : RecyclerView.ViewHolder(b.root)
    }
}
