package com.criptext.mail.api.models

import com.criptext.mail.utils.DeviceUtils
import org.json.JSONObject

sealed class DeviceInfo{
    data class TrustedDeviceInfo (val recipientId: String, val domain: String, val deviceId: Int, val deviceFriendlyName: String,
                                  val deviceType: DeviceUtils.DeviceType, val randomId: String, val syncFileVersion: Int): DeviceInfo()
    {
        companion object {

            fun fromJSON(jsonString: String, recipientId: String?): TrustedDeviceInfo {
                val json = JSONObject(jsonString).getJSONObject("requestingDeviceInfo")
                return TrustedDeviceInfo(
                        recipientId = recipientId ?: json.getString("recipientId"),
                        domain = json.getString("domain"),
                        randomId = JSONObject(jsonString).getString("randomId"),
                        deviceId = json.getInt("deviceId"),
                        deviceFriendlyName = json.getString("deviceFriendlyName"),
                        deviceType = DeviceUtils.getDeviceType(json.getInt("deviceType")),
                        syncFileVersion = JSONObject(jsonString).getInt("version")
                )
            }
        }
    }

    data class UntrustedDeviceInfo (val deviceId: String, val recipientId: String, val domain: String, val deviceName: String, val deviceFriendlyName: String,
                                    val deviceType: DeviceUtils.DeviceType, val syncFileVersion: Int): DeviceInfo()
    {
        companion object {

            fun fromJSON(jsonString: String): UntrustedDeviceInfo {
                val json = JSONObject(jsonString)
                        .getJSONObject("newDeviceInfo")
                return UntrustedDeviceInfo(
                        deviceId = json.getJSONObject("session").getString("randomId"),
                        recipientId = json.getString("recipientId"),
                        domain = json.getString("domain"),
                        deviceName = json.getString("deviceName"),
                        deviceFriendlyName = json.getString("deviceFriendlyName"),
                        deviceType = DeviceUtils.getDeviceType(json.getInt("deviceType")),
                        syncFileVersion = JSONObject(jsonString).getInt("version")
                )
            }
        }
    }
}