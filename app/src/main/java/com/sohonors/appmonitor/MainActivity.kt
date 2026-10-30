package com.sohonors.appmonitor

import android.app.ActivityManager
import android.app.AppOpsManager
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Process
import android.provider.Settings
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.sohonors.appmonitor.databinding.ActivityMainBinding
import java.util.*
import java.util.concurrent.TimeUnit

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var adapter: AppUsageAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        adapter = AppUsageAdapter(emptyList()) { appInfo ->
            openAppInfoSettings(appInfo.packageName)
        }
        binding.recyclerView.layoutManager = LinearLayoutManager(this)
        binding.recyclerView.adapter = adapter

        binding.btnUsageAccess.setOnClickListener {
            openUsageAccessSettings()
        }

        binding.swipeRefresh.setOnRefreshListener {
            loadData()
            binding.swipeRefresh.isRefreshing = false
        }
    }

    override fun onResume() {
        super.onResume()
        // Setiap kembali ke app (misalnya setelah aktifkan izin atau force-stop app lain), data di-refresh
        loadData()
    }

    private fun loadData() {
        updateRamInfo()

        if (!hasUsageAccessPermission()) {
            binding.layoutPermissionBanner.visibility = android.view.View.VISIBLE
            adapter.submitList(emptyList())
            return
        }
        binding.layoutPermissionBanner.visibility = android.view.View.GONE

        val usageList = queryUsageStats()
        adapter.submitList(usageList)
    }

    // ---------- RAM INFO ----------

    private fun updateRamInfo() {
        val am = getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val memInfo = ActivityManager.MemoryInfo()
        am.getMemoryInfo(memInfo)

        val totalMb = memInfo.totalMem / (1024 * 1024)
        val availMb = memInfo.availMem / (1024 * 1024)
        val usedMb = totalMb - availMb
        val percentUsed = if (totalMb > 0) ((usedMb * 100) / totalMb).toInt() else 0

        binding.tvRamInfo.text = getString(
            R.string.ram_info_format,
            "${usedMb} MB",
            "${totalMb} MB",
            percentUsed
        )
        binding.progressRam.progress = percentUsed
    }

    // ---------- USAGE ACCESS PERMISSION ----------

    private fun hasUsageAccessPermission(): Boolean {
        val appOps = getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            appOps.unsafeCheckOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                Process.myUid(),
                packageName
            )
        } else {
            @Suppress("DEPRECATION")
            appOps.checkOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                Process.myUid(),
                packageName
            )
        }
        return mode == AppOpsManager.MODE_ALLOWED
    }

    private fun openUsageAccessSettings() {
        try {
            startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
        } catch (e: Exception) {
            Toast.makeText(this, "Tidak bisa membuka pengaturan Usage Access", Toast.LENGTH_SHORT).show()
        }
    }

    // ---------- USAGE STATS QUERY ----------

    private fun queryUsageStats(): List<AppUsageInfo> {
        val usm = getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        val endTime = System.currentTimeMillis()
        val startTime = endTime - TimeUnit.DAYS.toMillis(7)

        val statsList = usm.queryUsageStats(
            UsageStatsManager.INTERVAL_BEST,
            startTime,
            endTime
        ) ?: emptyList()

        val pm = packageManager
        val result = mutableListOf<AppUsageInfo>()

        // Gabungkan jika ada duplikat package (queryUsageStats kadang mengembalikan beberapa entri per app)
        val mergedByPackage = HashMap<String, AppUsageInfo>()

        for (stats in statsList) {
            if (stats.totalTimeInForeground <= 0) continue // skip app yang tidak aktif sama sekali
            val pkg = stats.packageName

            val appInfo: ApplicationInfo = try {
                pm.getApplicationInfo(pkg, 0)
            } catch (e: Exception) {
                continue // app sudah di-uninstall tapi masih tercatat histori-nya
            }

            // Lewati aplikasi kita sendiri
            if (pkg == packageName) continue

            val isSystemApp = (appInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0

            val appName = pm.getApplicationLabel(appInfo).toString()
            val icon = try { pm.getApplicationIcon(appInfo) } catch (e: Exception) { null }

            val existing = mergedByPackage[pkg]
            if (existing == null) {
                mergedByPackage[pkg] = AppUsageInfo(
                    packageName = pkg,
                    appName = appName,
                    icon = icon,
                    totalTimeInForegroundMs = stats.totalTimeInForeground,
                    lastTimeUsedMs = stats.lastTimeUsed,
                    isSystemApp = isSystemApp
                )
            } else {
                mergedByPackage[pkg] = existing.copy(
                    totalTimeInForegroundMs = existing.totalTimeInForegroundMs + stats.totalTimeInForeground,
                    lastTimeUsedMs = maxOf(existing.lastTimeUsedMs, stats.lastTimeUsed)
                )
            }
        }

        result.addAll(mergedByPackage.values)
        // Urutkan dari yang paling lama aktif di foreground/background (paling "boros")
        result.sortByDescending { it.totalTimeInForegroundMs }

        return result
    }

    // ---------- NAVIGASI KE APP INFO (untuk force stop / battery restriction manual) ----------

    private fun openAppInfoSettings(packageName: String) {
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.parse("package:$packageName")
        }
        try {
            startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(this, "Tidak bisa membuka Info Aplikasi", Toast.LENGTH_SHORT).show()
        }
    }
}
