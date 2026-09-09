package com.callbridge.phoneb

import android.util.Base64
import org.json.JSONArray
import java.util.concurrent.CopyOnWriteArrayList

/** In-memory store for call log synced from Phone A. */
object CallLogStore {

    data class Entry(
        val number: String,
        val type: Int, // CallLog.Calls.INCOMING_TYPE etc.
        val date: Long,
        val duration: Long,
        val name: String
    )

    const val TYPE_INCOMING = 1
    const val TYPE_OUTGOING = 2
    const val TYPE_MISSED = 3

    private val entries = CopyOnWriteArrayList<Entry>()
    var onUpdate: (() -> Unit)? = null

    /** Parses base64-encoded JSON payload from Phone A. */
    fun update(base64Json: String) {
        try {
            val json = String(Base64.decode(base64Json, Base64.NO_WRAP))
            val arr = JSONArray(json)
            val newEntries = mutableListOf<Entry>()
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                newEntries.add(
                    Entry(
                        number = obj.optString("number", "Unknown"),
                        type = obj.optInt("type", 0),
                        date = obj.optLong("date", 0),
                        duration = obj.optLong("duration", 0),
                        name = obj.optString("name", "")
                    )
                )
            }
            entries.clear()
            entries.addAll(newEntries)
            onUpdate?.invoke()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun getAll(): List<Entry> = entries.toList()
}
