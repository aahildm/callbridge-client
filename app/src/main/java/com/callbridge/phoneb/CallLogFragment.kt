package com.callbridge.phoneb

import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

class CallLogFragment : Fragment(R.layout.fragment_call_log) {

    private lateinit var adapter: CallLogAdapter

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val rv = view.findViewById<RecyclerView>(R.id.rvCallLog)
        val tvEmpty = view.findViewById<TextView>(R.id.tvEmptyLog)
        val btnRefresh = view.findViewById<Button>(R.id.btnRefreshLog)

        adapter = CallLogAdapter(CallLogStore.getAll()) { number ->
            // Switch to dialer tab pre-filled with this number
            (activity as? MainActivity)?.goToDialerWithNumber(number)
        }
        rv.layoutManager = LinearLayoutManager(requireContext())
        rv.adapter = adapter

        updateEmptyState(tvEmpty, rv)

        CallLogStore.onUpdate = {
            activity?.runOnUiThread {
                adapter.update(CallLogStore.getAll())
                updateEmptyState(tvEmpty, rv)
            }
        }

        btnRefresh.setOnClickListener {
            if (TransportManager.isConnected()) {
                TransportManager.send("GET_CALLLOG")
            } else {
                tvEmpty.text = "⚠️ Not connected to Phone A"
            }
        }
    }

    private fun updateEmptyState(tvEmpty: TextView, rv: RecyclerView) {
        val hasData = CallLogStore.getAll().isNotEmpty()
        tvEmpty.visibility = if (hasData) View.GONE else View.VISIBLE
        rv.visibility = if (hasData) View.VISIBLE else View.GONE
    }
}
