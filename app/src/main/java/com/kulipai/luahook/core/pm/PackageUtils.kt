package com.kulipai.luahook.core.pm

import android.content.Context
import android.content.pm.PackageManager
import com.kulipai.luahook.data.model.AppInfo

object PackageUtils {
    fun getAppVersionName(context: Context): String {
        return try {
            val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
            packageInfo.versionName!!
        } catch (_: android.content.pm.PackageManager.NameNotFoundException) {
            "Unknown"
        }
    }


    fun getAppVersionCode(context: Context): Long {
        return try {
            val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
            packageInfo.longVersionCode // 注意这里使用 longVersionCode，在旧版本中是 versionCode (Int)
        } catch (_: PackageManager.NameNotFoundException) {
            -1 // 或者其他表示未找到的数值
        }
    }



    fun getInstalledApps(context: Context): List<AppInfo> {
        val pm = context.packageManager
        val apps = mutableListOf<AppInfo>()
        val packages = pm.getInstalledApplications(PackageManager.GET_META_DATA)

        for (app in packages) {
            // 过滤掉系统应用（有启动项的）
            if (pm.getLaunchIntentForPackage(app.packageName) != null) {
                val appName = pm.getApplicationLabel(app).toString()
                val packageName = app.packageName
                pm.getApplicationIcon(app)

                try {
                    val packageInfo = pm.getPackageInfo(packageName, 0)
                    val versionName = packageInfo.versionName ?: "N/A"
                    val versionCode =
                        packageInfo.longVersionCode

//                apps.add(AppInfo(appName, packageName, icon, versionName, versionCode))
                    apps.add(AppInfo(appName, packageName, versionName, versionCode))
                } catch (_: PackageManager.NameNotFoundException) {
                    // 忽略未找到的包
                }
            }
        }

        return apps.sortedBy { it.appName.lowercase() }
    }
}