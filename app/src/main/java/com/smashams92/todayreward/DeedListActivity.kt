package com.smashams92.todayreward.ui

import android.content.Intent
import android.os.Bundle
import android.text.InputFilter
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.smashams92.todayreward.R
import com.smashams92.todayreward.base.BaseActivity
import com.smashams92.todayreward.data.DeedDatabase
import com.smashams92.todayreward.data.GoodDeedEntity
import com.smashams92.todayreward.data.seedInitialData
import com.smashams92.todayreward.databinding.ActivityDeedListBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class DeedListActivity : BaseActivity() {

    private lateinit var binding: ActivityDeedListBinding
    private lateinit var adapter: DeedListAdapter
    private lateinit var database: DeedDatabase
    private lateinit var allDeeds: List<GoodDeedEntity>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityDeedListBinding.inflate(layoutInflater)
        setContentView(binding.root)

        database = DeedDatabase.getDatabase(this)

        binding.recyclerView.layoutManager = LinearLayoutManager(this)

        adapter = DeedListAdapter(
            emptyList(),
            onEdit = { deed, newText ->
                val lang = getSelectedLang()
                val updated = when (lang) {
                    "fa" -> deed.copy(fa = newText)
                    "ar" -> deed.copy(ar = newText)
                    else -> deed.copy(en = newText)
                }
                lifecycleScope.launch {
                    database.deedDao().updateDeed(updated)
                    loadDeeds()
                    // فقط وقتی متن تغییر داده بشه پیام بده
                    if (deed.fa != updated.fa || deed.ar != updated.ar || deed.en != updated.en) {
                        Toast.makeText(this@DeedListActivity, getString(R.string.updated), Toast.LENGTH_SHORT).show()
                    }
                }
            },
            onDelete = { deed ->
                lifecycleScope.launch {
                    database.deedDao().deleteDeed(deed)
                    loadDeeds()
                    Toast.makeText(
                        this@DeedListActivity,
                        getString(R.string.deleted),
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        )

        binding.recyclerView.adapter = adapter

        loadDeeds()

        binding.resetButton.setOnClickListener {
            AlertDialog.Builder(this@DeedListActivity)
                .setTitle(getString(R.string.delete))
                .setMessage(getString(R.string.confirm_delete))
                .setPositiveButton(getString(R.string.yes)) { _, _ ->
                    lifecycleScope.launch {
                        database.deedDao().deleteAll()
                        database.seedInitialData(assets)
                        loadDeeds()
                        Toast.makeText(
                            this@DeedListActivity,
                            getString(R.string.reset_success),
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
                .setNegativeButton(getString(R.string.no), null)
                .show()
        }

        binding.resetMonthButton.setOnClickListener {
            MaterialAlertDialogBuilder(this)
                .setTitle(getString(R.string.reset_month))
                .setMessage(getString(R.string.confirm_reset))
                .setPositiveButton(getString(R.string.yes)) { _, _ ->
                    getSharedPreferences("prefs", MODE_PRIVATE).edit().clear().apply()
                    lifecycleScope.launch {
                        database.deedDao().deleteAll()
                        database.seedInitialData(assets)
                        loadDeeds()
                    }
                }
                .setNegativeButton(getString(R.string.no), null)
                .show()
        }



        binding.addCustomDeedButton.setOnClickListener { showAddCustomDeedDialog() }


    }

    private fun showAddCustomDeedDialog() {
        val input = EditText(this).apply {
            maxLines = 3
            filters = arrayOf(InputFilter.LengthFilter(160))
        }

        MaterialAlertDialogBuilder(this)
            .setTitle(getString(R.string.add_custom))
            .setView(input)
            .setPositiveButton(getString(R.string.save)) { _, _ ->
                val text = input.text.toString()
                if (text.isNotEmpty()) {
                    val lang = getSelectedLang()
                    val deed = GoodDeedEntity(
                        id = (1000..999999).random(),
                        fa = if (lang == "fa") text else "",
                        en = if (lang == "en") text else "",
                        ar = if (lang == "ar") text else "",
                        isCompleted = false
                    )
                    lifecycleScope.launch(Dispatchers.IO) {
                        database.deedDao().insertAll(listOf(deed))
                        loadDeeds()
                    }
                }
            }
            .setNegativeButton(getString(R.string.cancel), null)
            .show()
    }


    private fun loadDeeds() {
        lifecycleScope.launch {
            allDeeds = withContext(Dispatchers.IO) {
                database.deedDao().getAllDeeds()
            }
            adapter.updateList(allDeeds.reversed())
        }
    }
}
