package com.callbridge.phoneb

import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup

class SmsActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_sms)

        val recycler = findViewById<RecyclerView>(R.id.recyclerSms)
        val etTo = findViewById<EditText>(R.id.etTo)
        val etBody = findViewById<EditText>(R.id.etBody)
        val btnSend = findViewById<Button>(R.id.btnSend)

        val adapter = SmsAdapter(SmsStore.getAll().toMutableList())
        recycler.layoutManager = LinearLayoutManager(this)
        recycler.adapter = adapter

        SmsStore.onNewMessage = { msg ->
            runOnUiThread {
                adapter.addMessage(msg)
                recycler.scrollToPosition(0)
            }
        }

        btnSend.setOnClickListener {
            val number = etTo.text.toString().trim()
            val body = etBody.text.toString().trim()
            if (number.isNotEmpty() && body.isNotEmpty()) {
                TransportManager.sendSms(number, body)
                SmsStore.add(SmsStore.Message(number, body, System.currentTimeMillis(), false))
                adapter.addMessage(SmsStore.Message(number, body, System.currentTimeMillis(), false))
                etBody.setText("")
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        SmsStore.onNewMessage = null
    }
}

class SmsAdapter(private val messages: MutableList<SmsStore.Message>) :
    RecyclerView.Adapter<SmsAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvSender: TextView = view.findViewById(R.id.tvSender)
        val tvBody: TextView = view.findViewById(R.id.tvBody)
        val tvTime: TextView = view.findViewById(R.id.tvTime)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_sms, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val msg = messages[position]
        holder.tvSender.text = if (msg.incoming) "From: ${msg.sender}" else "To: ${msg.sender}"
        holder.tvBody.text = msg.body
        holder.tvTime.text = java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault())
            .format(java.util.Date(msg.timestamp))
        holder.itemView.setBackgroundColor(
            if (msg.incoming) 0xFFE3F2FD.toInt() else 0xFFE8F5E9.toInt()
        )
    }

    override fun getItemCount() = messages.size

    fun addMessage(msg: SmsStore.Message) {
        messages.add(0, msg)
        notifyItemInserted(0)
    }
}
