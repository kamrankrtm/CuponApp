package com.cuponapp.smsdiscounts

import android.Manifest
import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.provider.Telephony
import android.telephony.SubscriptionInfo
import android.telephony.SubscriptionManager
import androidx.core.content.ContextCompat
import android.content.pm.PackageManager
import com.getcapacitor.JSArray
import com.getcapacitor.JSObject
import com.getcapacitor.PermissionState
import com.getcapacitor.Plugin
import com.getcapacitor.PluginCall
import com.getcapacitor.PluginMethod
import com.getcapacitor.annotation.CapacitorPlugin
import com.getcapacitor.annotation.Permission
import com.getcapacitor.annotation.PermissionCallback

/**
 * پلاگین نیتیو خواندن پیامک‌های دستگاه.
 *
 * فقط صندوق ورودی (inbox) خوانده می‌شود و هیچ پیامکی از اینجا به بیرون ارسال
 * نمی‌شود؛ ارسال به سرویس هوش مصنوعی کاملاً در لایه‌ی وب و پس از فیلتر محلی
 * انجام می‌گیرد.
 */
@CapacitorPlugin(
    name = "SmsReader",
    permissions = [
        Permission(
            alias = SmsReaderPlugin.SMS_PERMISSION_ALIAS,
            strings = [Manifest.permission.READ_SMS]
        )
    ]
)
class SmsReaderPlugin : Plugin() {

    companion object {
        const val SMS_PERMISSION_ALIAS = "sms"
        private const val DEFAULT_LIMIT = 500
    }

    /** آیا اپ روی اندروید واقعی اجرا می‌شود و دسترسی خواندن پیامک دارد؟ */
    @PluginMethod
    fun isAvailable(call: PluginCall) {
        val granted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.READ_SMS
        ) == PackageManager.PERMISSION_GRANTED

        val result = JSObject()
        result.put("available", true)
        result.put("granted", granted)
        call.resolve(result)
    }

    /** وضعیت فعلی مجوز خواندن پیامک */
    @PluginMethod
    override fun checkPermissions(call: PluginCall) {
        val result = JSObject()
        result.put(SMS_PERMISSION_ALIAS, getPermissionState(SMS_PERMISSION_ALIAS).toString())
        call.resolve(result)
    }

    /** درخواست مجوز خواندن پیامک از کاربر */
    @PluginMethod
    override fun requestPermissions(call: PluginCall) {
        if (getPermissionState(SMS_PERMISSION_ALIAS) == PermissionState.GRANTED) {
            val result = JSObject()
            result.put(SMS_PERMISSION_ALIAS, PermissionState.GRANTED.toString())
            call.resolve(result)
            return
        }
        requestPermissionForAlias(SMS_PERMISSION_ALIAS, call, "smsPermissionCallback")
    }

    @PermissionCallback
    private fun smsPermissionCallback(call: PluginCall) {
        val state = getPermissionState(SMS_PERMISSION_ALIAS)
        val result = JSObject()
        result.put(SMS_PERMISSION_ALIAS, state.toString())
        call.resolve(result)
    }

    /**
     * خواندن پیامک‌های صندوق ورودی.
     *
     * پارامترهای ورودی:
     *  - `sinceDays` (اختیاری): فقط پیامک‌های N روز اخیر
     *  - `limit` (اختیاری): حداکثر تعداد پیامک (پیش‌فرض ۵۰۰)
     */
    @PluginMethod
    fun readInbox(call: PluginCall) {
        if (getPermissionState(SMS_PERMISSION_ALIAS) != PermissionState.GRANTED) {
            call.reject("PERMISSION_DENIED", "مجوز خواندن پیامک داده نشده است")
            return
        }

        val sinceDays = call.getInt("sinceDays")
        val limit = call.getInt("limit") ?: DEFAULT_LIMIT

        try {
            val messages = queryInbox(sinceDays, limit)
            val result = JSObject()
            result.put("messages", messages)
            result.put("count", messages.length())
            call.resolve(result)
        } catch (e: SecurityException) {
            call.reject("PERMISSION_DENIED", e.message, e)
        } catch (e: Exception) {
            call.reject("READ_FAILED", e.message, e)
        }
    }

    /** فهرست سیم‌کارت‌های فعال دستگاه، برای نمایش نام اپراتور در رابط کاربری */
    @PluginMethod
    fun getSimInfo(call: PluginCall) {
        val sims = JSArray()
        try {
            if (ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.READ_PHONE_STATE
                ) == PackageManager.PERMISSION_GRANTED
            ) {
                val sm = context.getSystemService(Context.TELEPHONY_SUBSCRIPTION_SERVICE)
                    as? SubscriptionManager
                val list: List<SubscriptionInfo>? = sm?.activeSubscriptionInfoList
                list?.forEach { info ->
                    val obj = JSObject()
                    obj.put("subscriptionId", info.subscriptionId)
                    obj.put("slotIndex", info.simSlotIndex)
                    obj.put("carrierName", info.carrierName?.toString() ?: "")
                    obj.put("displayName", info.displayName?.toString() ?: "")
                    sims.put(obj)
                }
            }
        } catch (e: Exception) {
            // اطلاعات سیم‌کارت اختیاری است؛ نبودش اپ را متوقف نمی‌کند
        }
        val result = JSObject()
        result.put("sims", sims)
        call.resolve(result)
    }

    private fun queryInbox(sinceDays: Int?, limit: Int): JSArray {
        val out = JSArray()
        val uri: Uri = Telephony.Sms.Inbox.CONTENT_URI

        val projection = arrayOf(
            Telephony.Sms._ID,
            Telephony.Sms.ADDRESS,
            Telephony.Sms.BODY,
            Telephony.Sms.DATE,
            Telephony.Sms.SUBSCRIPTION_ID
        )

        var selection: String? = null
        var selectionArgs: Array<String>? = null
        if (sinceDays != null && sinceDays > 0) {
            val cutoff = System.currentTimeMillis() - sinceDays.toLong() * 24L * 60L * 60L * 1000L
            selection = "${Telephony.Sms.DATE} >= ?"
            selectionArgs = arrayOf(cutoff.toString())
        }

        val sortOrder = "${Telephony.Sms.DATE} DESC LIMIT $limit"

        val cursor: Cursor? = context.contentResolver.query(
            uri, projection, selection, selectionArgs, sortOrder
        )

        cursor?.use { c ->
            val idIdx = c.getColumnIndex(Telephony.Sms._ID)
            val addrIdx = c.getColumnIndex(Telephony.Sms.ADDRESS)
            val bodyIdx = c.getColumnIndex(Telephony.Sms.BODY)
            val dateIdx = c.getColumnIndex(Telephony.Sms.DATE)
            val subIdx = c.getColumnIndex(Telephony.Sms.SUBSCRIPTION_ID)

            val simNames = buildSimSlotMap()

            while (c.moveToNext()) {
                val obj = JSObject()
                val subId = if (subIdx >= 0) c.getInt(subIdx) else -1
                obj.put("id", if (idIdx >= 0) c.getString(idIdx) else "")
                obj.put("sender", if (addrIdx >= 0) c.getString(addrIdx) ?: "" else "")
                obj.put("body", if (bodyIdx >= 0) c.getString(bodyIdx) ?: "" else "")
                obj.put("date", if (dateIdx >= 0) c.getLong(dateIdx) else 0L)
                obj.put("subscriptionId", subId)
                obj.put("simLabel", simNames[subId] ?: "SIM")
                out.put(obj)
            }
        }

        return out
    }

    /** نگاشت subscriptionId به برچسب قابل نمایش سیم‌کارت */
    private fun buildSimSlotMap(): Map<Int, String> {
        val map = mutableMapOf<Int, String>()
        try {
            if (ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.READ_PHONE_STATE
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                return map
            }
            val sm = context.getSystemService(Context.TELEPHONY_SUBSCRIPTION_SERVICE)
                as? SubscriptionManager
            sm?.activeSubscriptionInfoList?.forEach { info ->
                val carrier = info.carrierName?.toString().orEmpty()
                val slot = info.simSlotIndex + 1
                map[info.subscriptionId] =
                    if (carrier.isNotBlank()) "SIM $slot ($carrier)" else "SIM $slot"
            }
        } catch (e: Exception) {
            // بی‌خطر: در صورت خطا فقط برچسب پیش‌فرض استفاده می‌شود
        }
        return map
    }
}
