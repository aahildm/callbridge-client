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
        messages.add(0, msg)
        if (messages.size > 500) messages.removeAt(messages.size - 1)
        onNewMessage?.invoke(msg)
    }

    fun getAll(): List<Message> = messages.toList()

    /** All messages to/from a specific number, oldest first for thread display. */
    fun getThread(contact: String): List<Message> =
        messages.filter { it.sender == contact }.sortedBy { it.timestamp }

    /** One entry per contact, with their most recent message, newest conversation first. */
    fun getConversations(): List<Message> =
        messages
            .groupBy { it.sender }
            .map { (_, msgs) -> msgs.maxByOrNull { it.timestamp }!! }
            .sortedByDescending { it.timestamp }
}
