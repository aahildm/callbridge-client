package com.callbridge.phoneb

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.GridLayout
import android.widget.ImageButton
import android.widget.TextView
import androidx.fragment.app.Fragment

class DialerFragment : Fragment(R.layout.fragment_dialer) {

    private var number = ""
    private lateinit var tvNumber: TextView
    private lateinit var btnBackspace: ImageButton

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        tvNumber     = view.findViewById(R.id.tvDialNumber)
        btnBackspace = view.findViewById(R.id.btnBackspace)
        val btnCall  = view.findViewById<Button>(R.id.btnCall)
        val tvStatus = view.findViewById<TextView>(R.id.tvDialStatus)
        val gridKeys = view.findViewById<GridLayout>(R.id.gridKeys)

        // Keypad digit clicks
        for (i in 0 until gridKeys.childCount) {
            val child = gridKeys.getChildAt(i)
            val key = child.tag as? String ?: continue
            child.setOnClickListener { appendDigit(key) }
            // Long-press 0 → +
            if (key == "0") {
                child.setOnLongClickListener {
                    if (number.endsWith("0")) number = number.dropLast(1)
                    appendDigit("+")
                    true
                }
            }
        }

        // Backspace: tap = delete one, long-press = clear all
        btnBackspace.setOnClickListener {
            if (number.isNotEmpty()) {
                number = number.dropLast(1)
                updateDisplay()
            }
        }
        btnBackspace.setOnLongClickListener {
            number = ""
            updateDisplay()
            true
        }

        // Call button
        btnCall.setOnClickListener {
            val n = number.trim()
            if (n.isEmpty()) {
                tvStatus.text = "Enter a number first"
                return@setOnClickListener
            }
            if (!TransportManager.isConnected()) {
                tvStatus.text = "⚠️ Not connected to Phone A"
                return@setOnClickListener
            }
            CallStateStore.setCall(n, outgoing = true)
            CallStateStore.update("DIALING")
            TransportManager.send("DIAL|$n")
            startActivity(
                Intent(requireContext(), IncomingCallActivity::class.java).apply {
                    putExtra("caller_number", n)
                    putExtra("outgoing", true)
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
            )
            tvStatus.text = ""
        }

        updateDisplay()
    }

    private fun appendDigit(digit: String) {
        if (number.length >= 18) return
        number += digit
        updateDisplay()
    }

    private fun updateDisplay() {
        if (number.isEmpty()) {
            tvNumber.text = ""
            tvNumber.hint = "Enter number"
            btnBackspace.visibility = View.INVISIBLE
        } else {
            tvNumber.text = number
            tvNumber.hint = ""
            btnBackspace.visibility = View.VISIBLE
            tvNumber.textSize = when {
                number.length > 14 -> 24f
                number.length > 10 -> 30f
                else -> 36f
            }
        }
    }

    fun setNumber(n: String) {
        number = n
        if (isAdded) updateDisplay()
    }
}
