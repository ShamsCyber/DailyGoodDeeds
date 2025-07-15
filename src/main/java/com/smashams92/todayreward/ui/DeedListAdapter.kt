package com.smashams92.todayreward.ui

import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.EditText
import androidx.appcompat.app.AlertDialog
import androidx.recyclerview.widget.RecyclerView
import com.smashams92.todayreward.R
import com.smashams92.todayreward.data.GoodDeedEntity
import com.smashams92.todayreward.databinding.ItemDeedRowBinding
import java.util.*

class DeedListAdapter(
    private var deeds: List<GoodDeedEntity>,
    private val onEdit: (GoodDeedEntity, String) -> Unit,
    private val onDelete: (GoodDeedEntity) -> Unit
) : RecyclerView.Adapter<DeedListAdapter.ViewHolder>() {

    inner class ViewHolder(val binding: ItemDeedRowBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemDeedRowBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun getItemCount(): Int = deeds.size

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val deed = deeds[position]
        val lang = Locale.getDefault().language

        val text = when (lang) {
            "fa" -> deed.fa
            "ar" -> deed.ar
            else -> deed.en
        }

        with(holder.binding) {

            // نمایش متن مناسب برای زبان دستگاه
            deedText.text = when (Locale.getDefault().language) {
                "fa" -> deed.fa
                "ar" -> deed.ar
                else -> deed.en
            }

            // مقداردهی اولیه دکمه انجام
            tvDone.text = root.context.getString(
                if (deed.isCompleted) R.string.done else R.string.not_done
            )

            // کلیک روی دکمه انجام / لغو انجام
            tvDone.setOnClickListener {
                val updated = deed.copy(isCompleted = !deed.isCompleted)
                onEdit(updated, when (Locale.getDefault().language) {
                    "fa" -> updated.fa
                    "ar" -> updated.ar
                    else -> updated.en
                })

                // به‌روزرسانی ظاهر دکمه در لحظه
                tvDone.text = root.context.getString(
                    if (updated.isCompleted) R.string.done else R.string.not_done
                )
            }




            val isCustom = listOf(deed.fa, deed.en, deed.ar).count { it.isNotBlank() } == 1

            editButton.visibility = if (isCustom) android.view.View.VISIBLE else android.view.View.GONE
            deleteButton.visibility = if (isCustom) android.view.View.VISIBLE else android.view.View.GONE

            editButton.setOnClickListener {
                val input = EditText(holder.itemView.context)
                input.setText(text)

                AlertDialog.Builder(holder.itemView.context)
                    .setTitle(holder.itemView.context.getString(R.string.edit))
                    .setView(input)
                    .setPositiveButton(holder.itemView.context.getString(R.string.save)) { _, _ ->
                        val newText = input.text.toString().trim()
                        if (newText.isNotEmpty()) onEdit(deed, newText)
                    }
                    .setNegativeButton(holder.itemView.context.getString(R.string.cancel), null)
                    .show()
            }

            deleteButton.setOnClickListener {
                AlertDialog.Builder(holder.itemView.context)
                    .setTitle(holder.itemView.context.getString(R.string.delete))
                    .setMessage(holder.itemView.context. getString(R.string.confirm_delete_item, text) )
                    .setPositiveButton(holder.itemView.context.getString(R.string.yes)) { _, _ ->
                        onDelete(deed)
                    }
                    .setNegativeButton(holder.itemView.context.getString(R.string.no), null)
                    .show()
            }

            // فقط در حالت تست یا دیباگ فعال باشه
            root.setOnClickListener {
                // اگه نمی‌خوای، این بخش رو کامل حذف کن
                // Toast.makeText(holder.itemView.context, text, Toast.LENGTH_SHORT).show()
            }
        }
    }

    fun updateList(newList: List<GoodDeedEntity>) {
        deeds = newList
        notifyDataSetChanged()
    }
}
