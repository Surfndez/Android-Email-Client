package com.email.api.models

import org.json.JSONObject

/**
 * Created by gabriel on 4/26/18.
 */
data class Event(val cmd: Int, val params: String) {
    companion object {
        fun fromJSON(jsonEventString: String): Event {
            val json = JSONObject(jsonEventString)
            return Event(cmd = json.getInt("cmd"), params = json.getString("params"))
        }
    }

    class Cmd {
        companion object {
            val newEmail = 1
        }
    }
}