package com.callbridge.phoneb

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

/** Simple compose screen for starting a new SMS conversation. */
class SmsNewActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_sms_new)

        val etTo = findViewById<EditText>(R.id.etNewTo)
        val etBody = findViewById<EditText>(R.id.etNewBody)
        val btnSend = findViewById<Button>(R.id.btnNewSend)

        btnSend.setOnClickListener {
            val number = etTo.text.toString().trim()
            val body = etBody.text.toString().trim()
            if (number.isEmpty() || body.isEmpty()) {
                Toast.makeText(this, "Enter both number and message", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            if (!TransportManager.isConnected()) {
                Toast.makeText(this, "Not connected to Phone A", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            TransportManager.sendSms(number, body)
            SmsStore.add(SmsStore.Message(number, body, System.currentTimeMillis(), false))
            // Open the thread so the user sees it was sent
            startActivity(Intent(this, SmsThreadActivity::class.java).putExtra("number", number))
            finish()
        }
    }
}
