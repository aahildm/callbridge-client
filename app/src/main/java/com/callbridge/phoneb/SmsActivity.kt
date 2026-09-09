package com.callbridge.phoneb

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Shows one row per contact with their latest message — the conversation list. */
class SmsActivity : AppCompatActivity() {

    private lateinit var adapter: ConversationAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_sms)

        val rv = findViewById<RecyclerView>(R.id.rvConversations)
        val tvEmpty = findViewById<TextView>(R.id.tvEmptySms)
        val btnNew = findViewById<Button>(R.id.btnNewSms)

        adapter = ConversationAdapter(SmsStore.getConversations()) { number ->
            startActivity(Intent(this, SmsThreadActivity::class.java).putExtra("number", number))
        }
        rv.layoutManager = LinearLayoutManager(this)
        rv.adapter = adapter

        updateEmptyState(tvEmpty, rv)

        SmsStore.onNewMessage = {
            runOnUiThread {
                adapter.update(SmsStore.getConversations())
                updateEmptyState(tvEmpty, rv)
            }
        }

        btnNew.setOnClickListener {
            startActivity(Intent(this, SmsNewActivity::class.java))
        }
    }

    override fun onResume() {
        super.onResume()
        adapter.update(SmsStore.getConversations())
        updateEmptyState(findViewById(R.id.tvEmptySms), findViewById(R.id.rvConversations))
    }

    private fun updateEmptyState(tvEmpty: TextView, rv: RecyclerView) {
        val hasData = SmsStore.getConversations().isNotEmpty()
        tvEmpty.visibility = if (hasData) View.GONE else View.VISIBLE
        rv.visibility = if (hasData) View.VISIBLE else View.GONE
    }

    override fun onDestroy() {
        super.onDestroy()
        SmsStore.onNewMessage = null
    }
}

class ConversationAdapter(
    private var conversations: List<SmsStore.Message>,
    private val onClick: (String) -> Unit
) : RecyclerView.Adapter<ConversationAdapter.ViewHolder>() {

    private val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvNumber: TextView = view.findViewById(R.id.tvConvoNumber)
        val tvPreview: TextView = view.findViewById(R.id.tvConvoPreview)
        val tvTime: TextView = view.findViewById(R.id.tvConvoTime)
    }

    override fun onCreateViewHolder(parent: ViewGroup, position: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_conversation, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val msg = conversations[position]
        holder.tvNumber.text = msg.sender
        val prefix = if (msg.incoming) "" else "You: "
        holder.tvPreview.text = "$prefix${msg.body}"
        holder.tvTime.text = timeFormat.format(Date(msg.timestamp))
        holder.itemView.setOnClickListener { onClick(msg.sender) }
    }

    override fun getItemCount() = conversations.size

    fun update(newList: List<SmsStore.Message>) {
        conversations = newList
        notifyDataSetChanged()
    }
}
