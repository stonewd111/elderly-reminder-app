package com.elder.reminder.alarm

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat

/**
 * OPPO/ColorOS 厂商专属“锁屏显示”权限引导。
 *
 * 重要说明：
 *  - “锁屏显示”不是标准 Android 权限，AndroidManifest 中没有任何 uses-permission 可声明，
 *    无法在安装时预授权。它是 ColorOS 根据应用运行时行为动态判定的厂商权限。
 *  - 系统仅在应用第一次尝试在锁屏上显示内容（锁屏可见通知/全屏提醒）时判定并弹出授权提示，
 *    用户点“允许”后，锁屏/前台才能弹出红色全屏大屏；后台弹全屏另需 ColorOS“后台弹出界面”权限。
 *  - 因此这里提供两个能力：
 *      1. 首次启动主动触发一次低干扰的全屏通知，引导系统弹出“需要在锁屏上显示”授权框，
 *         让用户提前授权，而不用等第一次真实提醒。
 *      2. 提供跳转“设置→隐私→权限管理→权限→锁屏显示”的 Intent（仅 OPPO/ColorOS 生效）。
 */
object LockscreenGuide {

    private const val PREFS = "elder_lockscreen_guide"
    private const val KEY_TRIGGERED = "triggered"

    /** 是否为 OPPO / 一加 / realme（ColorOS）机型。 */
    fun isColorOS(context: Context): Boolean {
        val m = context.packageManager
        return try {
            // OPPO/realme/一加 的 ColorOS 都包含 com.coloros / com.oplus 系统包
            m.getPackageInfo("com.coloros.securitypermission", 0)
            true
        } catch (e: PackageManager.NameNotFoundException) {
            try {
                m.getPackageInfo("com.oplus.securitypermission", 0)
                true
            } catch (e2: PackageManager.NameNotFoundException) {
                // 兜底：按厂商名判断
                val brand = Build.BRAND ?: ""
                val manufacturer = Build.MANUFACTURER ?: ""
                (brand + manufacturer).lowercase().let {
                    it.contains("oppo") || it.contains("oneplus") || it.contains("realme")
                }
            }
        }
    }

    /**
     * 首次启动主动触发 ColorOS“锁屏显示”授权判定。
     * 通过发一条带 fullScreenIntent 的高优先级通知，让系统判定本应用需要在锁屏显示，
     * 从而弹出授权框。仅执行一次，避免每次启动都打扰。
     */
    fun requestLockscreenCheck(context: Context) {
        if (!isColorOS(context)) return
        val sp = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (sp.getBoolean(KEY_TRIGGERED, false)) return
        sp.edit().putBoolean(KEY_TRIGGERED, true).apply()

        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channelId = "elder_lockscreen_guide"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId, "锁屏显示引导", NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "触发系统锁屏显示权限授权"
                lockscreenVisibility = android.app.Notification.VISIBILITY_PUBLIC
            }
            nm.createNotificationChannel(channel)
        }
        // 用包名入口 Activity 作为 fullScreenIntent 目标（无实际副作用，仅用于触发授权判定）
        val launch = context.packageManager.getLaunchIntentForPackage(context.packageName)
        val pending = PendingIntent.getActivity(
            context, 0xE1D4, launch ?: Intent(),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notif: Notification = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(com.elder.reminder.R.mipmap.ic_launcher)
            .setContentTitle("老人提醒助手")
            .setContentText("请在弹窗中允许“在锁屏上显示”，以便锁屏/后台也能弹出提醒")
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setFullScreenIntent(pending, true)
            .setContentIntent(pending)
            .setAutoCancel(true)
            .build()
        nm.notify(0xE1D4, notif)
        // 短暂后自动移除，仅作一次触发
        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
            runCatching { nm.cancel(0xE1D4) }
        }, 3000)
    }

    /**
     * 跳转 OPPO“锁屏显示”权限页。ColorOS 12+ 路径：设置→隐私→权限管理→权限→锁屏显示。
     * 尝试多个已知组件，成功返回 true。
     */
    fun openLockscreenSetting(context: Context): Boolean {
        val candidates = listOf(
            // ColorOS 13/14（OPPO 新版安全中心）
            ComponentName("com.coloros.securitypermission", "com.coloros.securitypermission.sdk.lockscreen.LockScreenPermissionActivity"),
            ComponentName("com.oplus.securitypermission", "com.oplus.securitypermission.sdk.lockscreen.LockScreenPermissionActivity"),
            ComponentName("com.coloros.safecenter", "com.coloros.safecenter.permission.lockscreen.LockScreenPermissionActivity"),
            ComponentName("com.coloros.securitypermission", "com.coloros.securitypermission.permission.LockScreenPermissionActivity"),
            // 通用：应用详情页（用户可自行点权限管理）
            ComponentName("com.android.settings", "com.android.settings.Settings\$AppNotificationSettingsActivity")
        )
        for (cn in candidates) {
            val intent = Intent().apply {
                component = cn
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            if (intent.resolveActivity(context.packageManager) != null) {
                runCatching { context.startActivity(intent); return true }
            }
        }
        // 兜底：打开应用详情
        return runCatching {
            context.startActivity(
                Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    android.net.Uri.parse("package:${context.packageName}"))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            ); true
        }.getOrDefault(false)
    }
}
