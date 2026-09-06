package com.callbridge.phoneb

object SmsStore {
    data class Message(
        val sender: String,
        val body: String,
        val timestamp: Long,
        val incoming: Boolean
    )

    private val messages = mutableListOf<Message>()
    var onNewMessage: ((Message) -> Unit)? = null

    fun add(msg: Message) {
        messages.add(0, msg) // newest first
        if (messages.size > 200) messages.removeAt(messages.size - 1) // cap at 200
        onNewMessage?.invoke(msg)
    }

    fun getAll(): List<Message> = messages.toList()

    fun getThread(contact: String): List<Message> =
        messages.filter { it.sender == contact || (!it.incoming && it.sender == contact) }

    fun getContacts(): List<String> =
        messages.map { it.sender }.distinct()
}
