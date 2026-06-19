package com.example.currencyconverter.ui

import android.annotation.SuppressLint
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import com.example.currencyconverter.databinding.ItemCurrencyBinding

class CurrencyAdapter(
    private val onAmountChanged: (currency: String, amount: Double) -> Unit,
    private val onDeleteClick: (currency: String) -> Unit
) : RecyclerView.Adapter<CurrencyAdapter.ViewHolder>() {

    var startDragListener: ((ViewHolder) -> Unit)? = null
    var onOrderChanged: ((List<String>) -> Unit)? = null
    var decimalPlaces: Int = 0

    private var items: MutableList<CurrencyItem> = mutableListOf()
    private var activeCurrency: String = ""
    private var isDragging = false

    init { setHasStableIds(true) }

    override fun getItemId(position: Int) = items[position].code.hashCode().toLong()

    fun submitList(newItems: List<CurrencyItem>, newActive: String) {
        if (isDragging) return
        val diff = DiffUtil.calculateDiff(object : DiffUtil.Callback() {
            override fun getOldListSize() = items.size
            override fun getNewListSize() = newItems.size
            override fun areItemsTheSame(o: Int, n: Int) = items[o].code == newItems[n].code
            override fun areContentsTheSame(o: Int, n: Int) = items[o] == newItems[n]
        })
        val prevActive = activeCurrency
        activeCurrency = newActive
        items = newItems.toMutableList()
        if (prevActive != newActive) notifyDataSetChanged() else diff.dispatchUpdatesTo(this)
    }

    fun onItemMove(from: Int, to: Int) {
        val item = items.removeAt(from)
        items.add(to, item)
        notifyItemMoved(from, to)
    }

    fun commitOrder() {
        onOrderChanged?.invoke(items.map { it.code })
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        ViewHolder(ItemCurrencyBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(items[position], items[position].code == activeCurrency)
    }

    override fun getItemCount() = items.size

    @SuppressLint("ClickableViewAccessibility")
    inner class ViewHolder(private val b: ItemCurrencyBinding) : RecyclerView.ViewHolder(b.root) {

        private var currentCode = ""
        private var currentAmount = 0.0
        private var textWatcher: TextWatcher? = null

        init {
            b.etAmount.setOnFocusChangeListener { _, hasFocus ->
                if (hasFocus && currentCode.isNotEmpty()) {
                    // Используем точное значение из модели, а не отображаемый текст
                    // (ceil-округление отображения иначе накапливает ошибку при смене поля)
                    onAmountChanged(currentCode, currentAmount)
                }
            }

            b.ivDragHandle.setOnTouchListener { _, event ->
                if (event.actionMasked == MotionEvent.ACTION_DOWN) {
                    isDragging = true
                    startDragListener?.invoke(this)
                }
                false
            }
        }

        fun onDragEnd() {
            isDragging = false
            commitOrder()
        }

        fun bind(item: CurrencyItem, isActive: Boolean) {
            currentCode = item.code
            currentAmount = item.amount

            b.tvFlag.text   = item.flag
            b.tvCode.text   = item.code
            b.tvName.text   = item.name
            b.tvSymbol.text = item.symbol
            b.tvRate.text   = item.rateText

            b.card.strokeWidth = if (isActive) 3 else 0
            b.card.strokeColor = if (isActive) 0xFF1565C0.toInt() else 0x00000000

            if (!isActive) setAmountSilently(fmtAmount(item.amount))

            textWatcher?.let { b.etAmount.removeTextChangedListener(it) }
            textWatcher = object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, st: Int, c: Int, a: Int) {}
                override fun onTextChanged(s: CharSequence?, st: Int, b2: Int, c: Int) {}
                override fun afterTextChanged(s: Editable?) {
                    if (b.etAmount.hasFocus())
                        onAmountChanged(currentCode, s.toString().parseAmount())
                }
            }
            b.etAmount.addTextChangedListener(textWatcher)
            b.btnDelete.setOnClickListener { onDeleteClick(item.code) }
        }

        private fun setAmountSilently(text: String) {
            textWatcher?.let { b.etAmount.removeTextChangedListener(it) }
            if (b.etAmount.text.toString() != text) b.etAmount.setText(text)
            textWatcher?.let { b.etAmount.addTextChangedListener(it) }
        }

        private fun fmtAmount(v: Double): String {
            val n = decimalPlaces
            if (n == 0) return Math.ceil(v).toLong().toString()
            val factor = Math.pow(10.0, n.toDouble())
            val ceiled = Math.ceil(v * factor) / factor
            return "%.${n}f".format(ceiled)
        }

        private fun String.parseAmount(): Double =
            this.replace(",", ".").toDoubleOrNull() ?: 0.0
    }
}
