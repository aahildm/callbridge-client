package com.callbridge.phoneb

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class SmsThreadActivity : AppCompatActivity() {

    private lateinit var number: String
    private lateinit var adapter: ThreadAdapter
    private lateinit var rv: RecyclerView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_sms_thread)

        number = intent.getStringExtra("number") ?: ""
        findViewById<TextView>(R.id.tvThreadTitle).text = number

        rv = findViewById(R.id.rvThread)
        val etBody = findViewById<EditText>(R.id.etThreadBody)
        val btnSend = findViewById<Button>(R.id.btnThreadSend)

        adapter = ThreadAdapter(SmsStore.getThread(number))
        rv.layoutManager = LinearLayoutManager(this)
        rv.adapter = adapter
        scrollToBottom()

        SmsStore.onNewMessage = { msg ->
            if (PhoneNumberUtils.sameNumber(msg.sender, number)) {
                runOnUiThread {
                    adapter.update(SmsStore.getThread(number))
                    scrollToBottom()
                }
            }
        }

        btnSend.setOnClickListener {
            val body = etBody.text.toString().trim()
            if (body.isEmpty()) return@setOnClickListener
            if (!TransportManager.isConnected()) {
                android.widget.Toast.makeText(this, "Not connected to Phone A", android.widget.Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            TransportManager.sendSms(number, body)
            val sent = SmsStore.Message(number, body, System.currentTimeMillis(), false)
            SmsStore.add(sent)
            adapter.update(SmsStore.getThread(number))
            scrollToBottom()
            etBody.setText("")
        }
    }

    private fun scrollToBottom() {
        if (adapter.itemCount > 0) rv.scrollToPosition(adapter.itemCount - 1)
    }

    override fun onDestroy() {
        super.onDestroy()
        SmsStore.onNewMessage = null
    }
}

class ThreadAdapter(private var messages: List<SmsStore.Message>) :
    RecyclerView.Adapter<ThreadAdapter.ViewHolder>() {

    private val timeFormat = SimpleDateFormat("MMM d, HH:mm", Locale.getDefault())

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val container: View = view
        val tvBody: TextView = view.findViewById(R.id.tvBubbleBody)
        val tvTime: TextView = view.findViewById(R.id.tvBubbleTime)
    }

    override fun onCreateViewHolder(parent: ViewGroup, position: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_sms_bubble, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val msg = messages[position]
        holder.tvBody.text = msg.body
        holder.tvTime.text = timeFormat.format(Date(msg.timestamp))

        if (msg.incoming) {
            holder.tvBody.setBackgroundColor(0xFFE0E0E0.toInt())
            (holder.container as android.widget.LinearLayout).gravity = android.view.Gravity.START
        } else {
            holder.tvBody.setBackgroundColor(0xFFC8E6C9.toInt())
            (holder.container as android.widget.LinearLayout).gravity = android.view.Gravity.END
        }
    }

    override fun getItemCount() = messages.size

    fun update(newList: List<SmsStore.Message>) {
        messages = newList
        notifyDataSetChanged()
    }
}
