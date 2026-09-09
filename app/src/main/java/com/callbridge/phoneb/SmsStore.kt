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

    /** All messages to/from a specific number (normalized match), oldest first. */
    fun getThread(contact: String): List<Message> =
        messages.filter { PhoneNumberUtils.sameNumber(it.sender, contact) }
            .sortedBy { it.timestamp }

    /** One entry per normalized number, using their most recent message. */
    fun getConversations(): List<Message> =
        messages
            .groupBy { PhoneNumberUtils.normalize(it.sender) }
            .map { (_, msgs) -> msgs.maxByOrNull { it.timestamp }!! }
            .sortedByDescending { it.timestamp }
}
