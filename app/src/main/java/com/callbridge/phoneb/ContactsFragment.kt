package com.callbridge.phoneb

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.fragment.app.Fragment

class ContactsFragment : Fragment() {

    private lateinit var searchBox: EditText
    private lateinit var contactList: LinearLayout
    private var allContacts: List<ContactsStore.Contact> = emptyList()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        val ctx = requireContext()

        val root = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        }

        // Search bar
        searchBox = EditText(ctx).apply {
            hint = "Search contacts..."
            setPadding(40, 24, 40, 24)
            textSize = 15f
            background = null
            setBackgroundColor(0xFFFFFFFF.toInt())
        }
        root.addView(searchBox, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))

        // Divider
        View(ctx).also {
            it.setBackgroundColor(0xFFE0E0E0.toInt())
            root.addView(it, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 1))
        }

        // Scrollable contact list
        val scroll = ScrollView(ctx)
        contactList = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL }
        scroll.addView(contactList)
        root.addView(scroll, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))

        searchBox.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) { filterContacts(s?.toString() ?: "") }
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        })

        ContactsStore.onUpdate = { requireActivity().runOnUiThread { loadContacts() } }
        loadContacts()

        // Request contacts from server
        TransportManager.send("GET_CONTACTS")

        return root
    }

    private fun loadContacts() {
        allContacts = ContactsStore.getAll()
        filterContacts(searchBox.text?.toString() ?: "")
    }

    private fun filterContacts(query: String) {
        contactList.removeAllViews()
        val ctx = requireContext()
        val filtered = if (query.isBlank()) allContacts
            else allContacts.filter { it.name.contains(query, ignoreCase = true) || it.number.contains(query) }

        if (filtered.isEmpty()) {
            val empty = TextView(ctx).apply {
                text = if (ContactsStore.getAll().isEmpty()) "Syncing contacts from Phone A..." else "No contacts found"
                textSize = 14f
                setTextColor(0xFF9E9E9E.toInt())
                gravity = android.view.Gravity.CENTER
                setPadding(0, 80, 0, 0)
            }
            contactList.addView(empty, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
            return
        }

        for (contact in filtered) {
            val row = LinearLayout(ctx).apply {
                orientation = LinearLayout.HORIZONTAL
                setPadding(48, 28, 48, 28)
                val attrs = intArrayOf(android.R.attr.selectableItemBackground)
                val ta = ctx.obtainStyledAttributes(attrs)
                val d = ta.getDrawable(0)
                ta.recycle()
                background = d
            }

            // Avatar circle with initial
            val avatar = TextView(ctx).apply {
                val initial = contact.name.firstOrNull()?.uppercaseChar()?.toString() ?: "#"
                text = initial
                textSize = 18f
                setTextColor(0xFFFFFFFF.toInt())
                gravity = android.view.Gravity.CENTER
                val size = (48 * ctx.resources.displayMetrics.density).toInt()
                val bg = android.graphics.drawable.GradientDrawable().apply {
                    shape = android.graphics.drawable.GradientDrawable.OVAL
                    setColor(avatarColor(contact.name))
                }
                background = bg
                val lp = LinearLayout.LayoutParams(size, size)
                lp.marginEnd = (16 * ctx.resources.displayMetrics.density).toInt()
                layoutParams = lp
            }

            val textCol = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL }
            val tvName = TextView(ctx).apply {
                text = contact.name
                textSize = 15f
                setTextColor(0xFF212121.toInt())
            }
            val tvNum = TextView(ctx).apply {
                text = contact.number
                textSize = 12f
                setTextColor(0xFF757575.toInt())
            }
            textCol.addView(tvName)
            textCol.addView(tvNum)

            row.addView(avatar)
            row.addView(textCol, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))

            row.setOnClickListener {
                (activity as? MainActivity)?.goToDialerWithNumber(contact.number)
            }

            contactList.addView(row)
            // Divider
            View(ctx).also {
                it.setBackgroundColor(0xFFF0F0F0.toInt())
                contactList.addView(it, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 1))
            }
        }
    }

    private fun avatarColor(name: String): Int {
        val colors = intArrayOf(
            0xFF1976D2.toInt(), 0xFF388E3C.toInt(), 0xFFF57C00.toInt(),
            0xFF7B1FA2.toInt(), 0xFFC62828.toInt(), 0xFF00838F.toInt()
        )
        return colors[Math.abs(name.hashCode()) % colors.size]
    }

    override fun onDestroyView() {
        super.onDestroyView()
        ContactsStore.onUpdate = null
    }
}
