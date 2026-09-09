package com.callbridge.phoneb

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class CallLogAdapter(
    private var entries: List<CallLogStore.Entry>,
    private val onCallBack: (String) -> Unit
) : RecyclerView.Adapter<CallLogAdapter.ViewHolder>() {

    private val dateFormat = SimpleDateFormat("MMM d, h:mm a", Locale.getDefault())

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvIcon: TextView = view.findViewById(R.id.tvCallIcon)
        val tvNumber: TextView = view.findViewById(R.id.tvLogNumber)
        val tvDate: TextView = view.findViewById(R.id.tvLogDate)
        val btnCallBack: Button = view.findViewById(R.id.btnCallBack)
    }

    override fun onCreateViewHolder(parent: ViewGroup, position: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_call_log, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val entry = entries[position]
        holder.tvIcon.text = when (entry.type) {
            CallLogStore.TYPE_INCOMING -> "📥"
            CallLogStore.TYPE_OUTGOING -> "📤"
            CallLogStore.TYPE_MISSED -> "❌"
            else -> "📞"
        }
        val label = if (entry.name.isNotEmpty()) entry.name else entry.number
        holder.tvNumber.text = label
        holder.tvDate.text = dateFormat.format(Date(entry.date))
        holder.btnCallBack.setOnClickListener { onCallBack(entry.number) }
    }

    override fun getItemCount() = entries.size

    fun update(newEntries: List<CallLogStore.Entry>) {
        entries = newEntries
        notifyDataSetChanged()
    }
}
