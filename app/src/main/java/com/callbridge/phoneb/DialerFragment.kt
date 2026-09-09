package com.callbridge.phoneb

import android.content.Intent
import android.os.Bundle
import android.view.View
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
        val btnBackspace = view.findViewById<View>(R.id.btnBackspace)
        val tvDialStatus = view.findViewById<TextView>(R.id.tvDialStatus)
        val gridKeys = view.findViewById<GridLayout>(R.id.gridKeys)

        for (i in 0 until gridKeys.childCount) {
            val child = gridKeys.getChildAt(i)
            val key = child.tag as? String
            if (key != null) {
                child.setOnClickListener { etNumber.append(key) }
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
            // Set state BEFORE launching the activity so it's already
            // correct by the time onCreate reads it — no race with the
            // STATE|DIALING broadcast that follows shortly after.
            CallStateStore.setCall(number, outgoing = true)
            CallStateStore.update("DIALING")

            TransportManager.send("DIAL|$number")
            val intent = Intent(requireContext(), IncomingCallActivity::class.java).apply {
                putExtra("caller_number", number)
                putExtra("outgoing", true)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            startActivity(intent)
            tvDialStatus.text = ""
        }
    }

    fun setNumber(number: String) {
        view?.findViewById<EditText>(R.id.etDialNumber)?.setText(number)
    }
}
