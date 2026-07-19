package com.example.currencyconverter

import android.content.Intent
import android.net.Uri
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
import androidx.core.os.LocaleListCompat
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

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
            onDeleteClick   = { code -> confirmDelete(code) }
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
                binding.tvCount.text = getString(R.string.selected, s.currencyItems.size)
                binding.tvStatus.text = when {
                    s.error != null         -> getString(R.string.error_prefix, s.error)
                    s.lastUpdated.isEmpty() -> getString(R.string.loading)
                    else                    -> getString(R.string.updated, s.lastUpdated, s.source)
                }
                adapter.decimalPlaces = s.decimalPlaces
                adapter.submitList(s.currencyItems, s.activeCurrency)
            }
        }

        binding.tvVersion.text = getString(R.string.version, BuildConfig.VERSION_NAME)
        binding.btnSourceInfo.setOnClickListener { showSourceInfoDialog() }
        binding.btnAttribution.setOnClickListener { openUrl("https://www.exchangerate-api.com") }

        binding.btnRefresh.setOnClickListener { vm.refresh() }
        binding.btnClear.setOnClickListener {
            // Снимаем фокус, иначе поле с курсором пропускается при ребинде и не обнуляется
            currentFocus?.clearFocus()
            vm.clearAll()
        }
        binding.btnAddCurrency.setOnClickListener { showAddDialog() }
        binding.btnSettings.setOnClickListener { showSettingsDialog() }

        checkForUpdate()
    }

    override fun onResume() {
        super.onResume()
        vm.refresh()
    }

    private fun confirmDelete(code: String) {
        val name = CurrencyViewModel.currencyName(code)
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.delete_currency_title))
            .setMessage(getString(R.string.delete_currency_msg, code, name))
            .setPositiveButton(getString(R.string.delete)) { _, _ -> vm.removeCurrency(code) }
            .setNegativeButton(getString(R.string.cancel), null)
            .show()
    }

    private fun openUrl(url: String) {
        try {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        } catch (_: Exception) { /* нет браузера — игнорируем */ }
    }

    private fun showSourceInfoDialog() {
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.source_info_title))
            .setMessage(getString(R.string.source_info_message))
            .setNeutralButton("exchangerate-api.com") { _, _ ->
                openUrl("https://www.exchangerate-api.com")
            }
            .setPositiveButton(getString(R.string.ok), null)
            .show()
    }

    private fun checkForUpdate() {
        lifecycleScope.launch {
            val release = UpdateChecker.getLatestRelease() ?: return@launch
            val latestCode = UpdateChecker.versionCodeFromTag(release.tagName)
            if (latestCode <= BuildConfig.VERSION_CODE) return@launch
            val apkAsset = release.assets.firstOrNull { it.name.endsWith(".apk") } ?: return@launch

            AlertDialog.Builder(this@MainActivity)
                .setTitle(getString(R.string.update_title))
                .setMessage(getString(R.string.update_msg, UpdateChecker.displayVersion(release)))
                .setPositiveButton(getString(R.string.update_now)) { _, _ ->
                    lifecycleScope.launch {
                        var progressDialog: AlertDialog? = AlertDialog.Builder(this@MainActivity)
                            .setTitle(getString(R.string.downloading))
                            .setMessage("0%")
                            .setCancelable(false)
                            .show()
                        UpdateChecker.downloadAndInstall(
                            this@MainActivity,
                            apkAsset.downloadUrl
                        ) { pct ->
                            runOnUiThread { progressDialog?.setMessage("$pct%") }
                        }
                        progressDialog?.dismiss()
                        progressDialog = null
                    }
                }
                .setNegativeButton(getString(R.string.update_later), null)
                .show()
        }
    }

    // Названия источников: индекс 0 = Авто (локализуется), дальше — бренды (не переводятся)
    private fun sourceOptions() = arrayOf(
        getString(R.string.source_auto), "ExchangeRate-API", "F.A.", "Frankfurter (ECB)"
    )

    private fun showSettingsDialog() {
        val decimals = vm.getDecimalPlaces()
        val decimalLabel = if (decimals == 0) getString(R.string.precision_value_int)
                           else getString(R.string.precision_value_n, decimals)
        val srcName = sourceOptions()[vm.getSourceMode().coerceIn(0, 3)]

        val items = arrayOf(
            getString(R.string.setting_precision, decimalLabel),
            getString(R.string.setting_source, srcName),
            getString(R.string.setting_language, currentLanguageLabel())
        )

        AlertDialog.Builder(this)
            .setTitle(getString(R.string.settings))
            .setItems(items) { _, which ->
                when (which) {
                    0 -> showDecimalDialog()
                    1 -> showSourceDialog()
                    2 -> showLanguageDialog()
                }
            }
            .setNegativeButton(getString(R.string.close), null)
            .show()
    }

    private fun showDecimalDialog() {
        val options = arrayOf(
            getString(R.string.prec_0), getString(R.string.prec_1),
            getString(R.string.prec_2), getString(R.string.prec_4)
        )
        val values  = intArrayOf(0, 1, 2, 4)
        val checked = values.indexOfFirst { it == vm.getDecimalPlaces() }.coerceAtLeast(0)

        AlertDialog.Builder(this)
            .setTitle(getString(R.string.precision_title))
            .setSingleChoiceItems(options, checked) { dialog, which ->
                vm.setDecimalPlaces(values[which])
                dialog.dismiss()
            }
            .setNegativeButton(getString(R.string.back), null)
            .show()
    }

    private fun showSourceDialog() {
        val checked = vm.getSourceMode().coerceIn(0, 3)

        AlertDialog.Builder(this)
            .setTitle(getString(R.string.source_title))
            .setSingleChoiceItems(sourceOptions(), checked) { dialog, which ->
                vm.setSourceMode(which)
                dialog.dismiss()
            }
            .setNegativeButton(getString(R.string.back), null)
            .show()
    }

    private fun currentLanguageLabel(): String {
        val locales = AppCompatDelegate.getApplicationLocales()
        if (locales.isEmpty) return getString(R.string.language_system)
        val tag = locales[0]?.language ?: return getString(R.string.language_system)
        return LANGUAGES.firstOrNull { it.first == tag }?.second ?: tag
    }

    private fun showLanguageDialog() {
        // Первый пункт — «системный», далее 40 языков в их родном написании
        val labels = arrayOf(getString(R.string.language_system)) + LANGUAGES.map { it.second }
        val current = AppCompatDelegate.getApplicationLocales()
        val currentTag = if (current.isEmpty) null else current[0]?.language
        val checked = if (currentTag == null) 0
                      else (LANGUAGES.indexOfFirst { it.first == currentTag }.let { if (it >= 0) it + 1 else 0 })

        AlertDialog.Builder(this)
            .setTitle(getString(R.string.language_title))
            .setSingleChoiceItems(labels, checked) { dialog, which ->
                val tag = if (which == 0) null else LANGUAGES[which - 1].first
                val locales = if (tag == null) LocaleListCompat.getEmptyLocaleList()
                              else LocaleListCompat.forLanguageTags(tag)
                AppCompatDelegate.setApplicationLocales(locales)  // применяется + сохраняется автоматически
                dialog.dismiss()
            }
            .setNegativeButton(getString(R.string.back), null)
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
            .setTitle(getString(R.string.add_currency_title, all.size))
            .setView(dialogView)
            .setNegativeButton(getString(R.string.cancel), null)
            .create()

        dialog.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE)
        dialog.show()
        etSearch.requestFocus()
    }

    companion object {
        // 40 языков: тег (совпадает с папкой ресурсов) → родное название
        val LANGUAGES = listOf(
            "en" to "English",
            "sr" to "Srpski",
            "hr" to "Hrvatski",
            "bs" to "Bosanski",
            "mk" to "Македонски",
            "sq" to "Shqip",
            "sl" to "Slovenščina",
            "bg" to "Български",
            "el" to "Ελληνικά",
            "ro" to "Română",
            "de" to "Deutsch",
            "fr" to "Français",
            "es" to "Español",
            "it" to "Italiano",
            "pt" to "Português",
            "nl" to "Nederlands",
            "pl" to "Polski",
            "cs" to "Čeština",
            "sk" to "Slovenčina",
            "hu" to "Magyar",
            "sv" to "Svenska",
            "da" to "Dansk",
            "nb" to "Norsk",
            "fi" to "Suomi",
            "lt" to "Lietuvių",
            "lv" to "Latviešu",
            "et" to "Eesti",
            "ru" to "Русский",
            "ka" to "ქართული",
            "zh" to "中文",
            "ja" to "日本語",
            "ko" to "한국어",
            "th" to "ไทย",
            "vi" to "Tiếng Việt",
            "id" to "Bahasa Indonesia",
            "hi" to "हिन्दी",
            "ar" to "العربية",
            "he" to "עברית",
            "fa" to "فارسی",
            "tr" to "Türkçe"
        )
    }
}
