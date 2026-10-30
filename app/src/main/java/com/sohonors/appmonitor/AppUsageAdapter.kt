package com.sohonors.appmonitor

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.TimeUnit

class AppUsageAdapter(
    private var items: List<AppUsageInfo>,
    private val onClick: (AppUsageInfo) -> Unit
) : RecyclerView.Adapter<AppUsageAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val icon: ImageView = view.findViewById(R.id.ivIcon)
        val name: TextView = view.findViewById(R.id.tvAppName)
        val usage: TextView = view.findViewById(R.id.tvUsageInfo)
        val lastUsed: TextView = view.findViewById(R.id.tvLastUsed)
        val badge: TextView = view.findViewById(R.id.tvBadge)
    }

    fun submitList(newItems: List<AppUsageInfo>) {
        items = newItems
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val v = LayoutInflater.from(parent.context).inflate(R.layout.item_app, parent, false)
        return ViewHolder(v)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = items[position]
        holder.icon.setImageDrawable(item.icon)
        holder.name.text = item.appName

        val minutes = TimeUnit.MILLISECONDS.toMinutes(item.totalTimeInForegroundMs)
        val hours = minutes / 60
        val remMinutes = minutes % 60
        val durationText = if (hours > 0) {
            "Aktif ~${hours} jam ${remMinutes} menit (7 hari terakhir)"
        } else {
            "Aktif ~${remMinutes} menit (7 hari terakhir)"
        }
        holder.usage.text = durationText

        if (item.lastTimeUsedMs > 0) {
            val sdf = SimpleDateFormat("dd MMM, HH:mm", Locale("in", "ID"))
            holder.lastUsed.text = "Terakhir dipakai: ${sdf.format(Date(item.lastTimeUsedMs))}"
        } else {
            holder.lastUsed.text = "Terakhir dipakai: tidak ada data"
        }

        // Tandai "BOROS" kalau total waktu aktif dalam 7 hari terakhir cukup tinggi (>3 jam)
        if (hours >= 3) {
            holder.badge.visibility = View.VISIBLE
        } else {
            holder.badge.visibility = View.GONE
        }

        holder.itemView.setOnClickListener { onClick(item) }
    }

    override fun getItemCount(): Int = items.size
}
