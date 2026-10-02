package com.callbridge.phoneb

import org.json.JSONArray
import android.util.Base64
import java.util.concurrent.CopyOnWriteArrayList

object ContactsStore {
    data class Contact(val name: String, val number: String)

    private val contacts = CopyOnWriteArrayList<Contact>()
    var onUpdate: (() -> Unit)? = null

    fun update(base64Json: String) {
        try {
            val json = String(Base64.decode(base64Json, Base64.NO_WRAP))
            val arr = JSONArray(json)
            val list = mutableListOf<Contact>()
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                list.add(Contact(
                    name = obj.optString("name", ""),
                    number = obj.optString("number", "")
                ))
            }
            contacts.clear()
            contacts.addAll(list.sortedBy { it.name })
            onUpdate?.invoke()
        } catch (e: Exception) { e.printStackTrace() }
    }

    fun getAll(): List<Contact> = contacts.toList()
}
