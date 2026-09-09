package com.callbridge.phoneb

import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.GridLayout
import android.widget.TextView
import androidx.fragment.app.Fragment

class DialerFragment : Fragment(R.layout.fragment_dialer) {

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val etNumber = view.findViewById<EditText>(R.id.etDialNumber)
        val btnCall = view.findViewById<Button>(R.id.btnCall)
        val btnBackspace = view.findViewById<Button>(R.id.btnBackspace)
        val tvDialStatus = view.findViewById<TextView>(R.id.tvDialStatus)
        val gridKeys = view.findViewById<GridLayout>(R.id.gridKeys)

        // Wire every button inside the keypad grid to append its label
        for (i in 0 until gridKeys.childCount) {
            val child = gridKeys.getChildAt(i)
            if (child is Button) {
                child.setOnClickListener { etNumber.append(child.text) }
            }
        }

        btnBackspace.setOnClickListener {
            val text = etNumber.text
            if (text.isNotEmpty()) etNumber.setText(text.dropLast(1))
        }

        btnCall.setOnClickListener {
            val number = etNumber.text.toString().trim()
            if (number.isEmpty()) {
                tvDialStatus.text = "Enter a number first"
                return@setOnClickListener
            }
            if (!TransportManager.isConnected()) {
                tvDialStatus.text = "⚠️ Not connected to Phone A"
                return@setOnClickListener
            }
            TransportManager.send("DIAL|$number")
            tvDialStatus.text = "📞 Calling $number via Phone A..."
        }
    }

    /** Allows other screens (call log) to pre-fill and switch to this tab. */
    fun setNumber(number: String) {
        view?.findViewById<EditText>(R.id.etDialNumber)?.setText(number)
    }
}
