package com.example.currencyconverter

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.BaseAdapter
import android.widget.EditText
import android.widget.ListView
import android.widget.TextView
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.currencyconverter.databinding.ActivityMainBinding
import com.example.currencyconverter.ui.CurrencyAdapter
import com.example.currencyconverter.ui.CurrencyInfo
import com.example.currencyconverter.ui.CurrencyViewModel
import com.example.currencyconverter.ui.CurrencyViewModel.Companion.currencyFlag
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private val vm: CurrencyViewModel by viewModels()
    private lateinit var adapter: CurrencyAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        adapter = CurrencyAdapter(
            onAmountChanged = { code, amount -> vm.setActiveAmount(code, amount) },
            onDeleteClick   = { code -> vm.removeCurrency(code) }
        )

        val dragCallback = object : ItemTouchHelper.SimpleCallback(
            ItemTouchHelper.UP or ItemTouchHelper.DOWN, 0
        ) {
            override fun isLongPressDragEnabled() = false

            override fun onMove(
                rv: RecyclerView,
                viewHolder: RecyclerView.ViewHolder,
                target: RecyclerView.ViewHolder
            ): Boolean {
                adapter.onItemMove(viewHolder.bindingAdapterPosition, target.bindingAdapterPosition)
                return true
            }

            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {}

            override fun clearView(rv: RecyclerView, viewHolder: RecyclerView.ViewHolder) {
                super.clearView(rv, viewHolder)
                (viewHolder as? CurrencyAdapter.ViewHolder)?.onDragEnd()
            }
        }

        val touchHelper = ItemTouchHelper(dragCallback)
        touchHelper.attachToRecyclerView(binding.recyclerView)

        adapter.startDragListener = { vh -> touchHelper.startDrag(vh) }
        adapter.onOrderChanged = { order -> vm.reorderCurrencies(order) }

        binding.recyclerView.layoutManager = LinearLayoutManager(this)
        binding.recyclerView.adapter = adapter
        binding.recyclerView.itemAnimator = null

        lifecycleScope.launch {
            vm.state.collect { s ->
                binding.progressBar.visibility = if (s.isLoading) View.VISIBLE else View.GONE
                if (s.totalCurrencies > 0) binding.tvCount.text = s.totalCurrencies.toString()
                binding.tvStatus.text = when {
                    s.error != null         -> "⚠ ${s.error}"
                    s.lastUpdated.isEmpty() -> "Loading…"
                    else                    -> "Last updated: ${s.lastUpdated}"
                }
                adapter.decimalPlaces = s.decimalPlaces
                adapter.submitList(s.currencyItems, s.activeCurrency)
            }
        }

        binding.btnRefresh.setOnClickListener { vm.refresh() }
        binding.btnClear.setOnClickListener { vm.clearAll() }
        binding.btnAddCurrency.setOnClickListener { showAddDialog() }
        binding.btnSettings.setOnClickListener { showSettingsDialog() }
    }

    override fun onResume() {
        super.onResume()
        vm.refresh()
    }

    private fun showSettingsDialog() {
        val options = arrayOf("0 — целые числа", "1 знак", "2 знака", "4 знака")
        val values  = intArrayOf(0, 1, 2, 4)
        val current = vm.getDecimalPlaces()
        val checked = values.indexOfFirst { it == current }.coerceAtLeast(0)

        AlertDialog.Builder(this)
            .setTitle("Точность отображения")
            .setSingleChoiceItems(options, checked) { dialog, which ->
                vm.setDecimalPlaces(values[which])
                dialog.dismiss()
            }
            .setNegativeButton("Закрыть", null)
            .show()
    }

    private fun showAddDialog() {
        val all = vm.getAvailableCurrencies()
        if (all.isEmpty()) return

        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_add_currency, null)
        val etSearch = dialogView.findViewById<EditText>(R.id.etSearch)
        val listView = dialogView.findViewById<ListView>(R.id.listView)

        var filtered = all.toMutableList()

        val rowAdapter = object : BaseAdapter() {
            override fun getCount() = filtered.size
            override fun getItem(pos: Int): CurrencyInfo = filtered[pos]
            override fun getItemId(pos: Int) = pos.toLong()

            override fun getView(pos: Int, convertView: View?, parent: ViewGroup): View {
                val view = convertView ?: LayoutInflater.from(parent.context)
                    .inflate(R.layout.dialog_currency_row, parent, false)
                val item = filtered[pos]
                view.findViewById<TextView>(R.id.tvFlag).text = currencyFlag(item.code)
                view.findViewById<TextView>(R.id.tvCode).text = item.code
                view.findViewById<TextView>(R.id.tvName).text = item.name
                return view
            }
        }

        listView.adapter = rowAdapter

        var dialog: AlertDialog? = null

        listView.setOnItemClickListener { _, _, pos, _ ->
            vm.addCurrency(filtered[pos].code)
            dialog?.dismiss()
        }

        etSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, st: Int, c: Int, a: Int) {}
            override fun onTextChanged(s: CharSequence?, st: Int, b: Int, c: Int) {}
            override fun afterTextChanged(s: Editable?) {
                val q = s?.toString()?.trim()?.lowercase() ?: ""
                filtered = if (q.isEmpty()) all.toMutableList()
                else all.filter {
                    it.code.lowercase().contains(q) ||
                    it.name.lowercase().contains(q)
                }.toMutableList()
                rowAdapter.notifyDataSetChanged()
            }
        })

        dialog = AlertDialog.Builder(this)
            .setTitle("Add currency")
            .setView(dialogView)
            .setNegativeButton("Cancel", null)
            .create()

        dialog.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE)
        dialog.show()
        etSearch.requestFocus()
    }
}
