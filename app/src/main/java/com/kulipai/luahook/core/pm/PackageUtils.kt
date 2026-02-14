package com.kulipai.luahook.core.pm

import android.content.Context
import android.content.pm.PackageManager
import com.kulipai.luahook.core.shell.ShellManager
import com.kulipai.luahook.core.shell.ShellResult
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
        val apps = HashMap<String, AppInfo>()

        // 1. 获取 PM 能看到的包
        val packages = try {
            pm.getInstalledApplications(PackageManager.GET_META_DATA)
        } catch (e: Exception) {
            emptyList()
        }

        for (app in packages) {
            // 这里不再过滤 pm.getLaunchIntentForPackage
            try {
                val appName = pm.getApplicationLabel(app).toString()
                val packageName = app.packageName
                val isSystemApp = (app.flags and android.content.pm.ApplicationInfo.FLAG_SYSTEM) != 0

                val packageInfo = pm.getPackageInfo(packageName, 0)
                val versionName = packageInfo.versionName ?: "N/A"
                val versionCode = packageInfo.longVersionCode

                apps[packageName] = AppInfo(
                    appName = appName,
                    packageName = packageName,
                    versionName = versionName,
                    versionCode = versionCode,
                    isSystemApp = isSystemApp
                )
            } catch (_: PackageManager.NameNotFoundException) {
            }
        }

        // 2. 如果有 Shell 权限 (Root/Shizuku)，尝试通过 shell 获取包列表 (解决 Android 11+ 可见性问题)
        if (ShellManager.mode.value != ShellManager.Mode.NONE) {
            val result = ShellManager.shell("pm list packages")
            if (result is ShellResult.Success) {
                val shellPackages = result.stdout.lines().mapNotNull { line ->
                    if (line.startsWith("package:")) line.substring(8).trim() else null
                }

                for (pkgName in shellPackages) {
                    if (!apps.containsKey(pkgName)) {
                        // 尝试通过 pm 获取详情
                        try {
                            val app = pm.getApplicationInfo(pkgName, 0)
                            val appName = pm.getApplicationLabel(app).toString()
                            val isSystemApp = (app.flags and android.content.pm.ApplicationInfo.FLAG_SYSTEM) != 0

                            val packageInfo = pm.getPackageInfo(pkgName, 0)
                            val versionName = packageInfo.versionName ?: "N/A"
                            val versionCode = packageInfo.longVersionCode

                            apps[pkgName] = AppInfo(
                                appName = appName,
                                packageName = pkgName,
                                versionName = versionName,
                                versionCode = versionCode,
                                isSystemApp = isSystemApp
                            )
                        } catch (_: Exception) {
                            // 即使 pm 拿不到 info, 如果只是想列出包名也可以，但这里 AppInfo 需要更多字段
                            // 可以在这里 fallback unknown
                            apps[pkgName] = AppInfo(
                                appName = pkgName, // Use pkg name as app name fallback
                                packageName = pkgName,
                                versionName = "Unknown",
                                versionCode = 0,
                                isSystemApp = false // Assume false or true? hard to know.
                            )
                        }
                    }
                }
            }
        }

        return apps.values.sortedBy { it.appName.lowercase() }
    }
}