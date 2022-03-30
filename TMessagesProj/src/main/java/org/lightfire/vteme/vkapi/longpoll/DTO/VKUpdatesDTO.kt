package org.lightfire.vteme.vkapi.longpoll.DTO

data class LPServerResponseWrapper(
    var ts: Int,
    var updates : List<Any>
)

data class MessageExtraFields(
    var peer_id: Int,
    var timestamp: Int,
    var text: String,
    var attachments : Any? = null,
    var random_id: Int,
)

// 1
data class MessageFlagsChangedUpdate(
    var message_id: Int,
    var flags: Int,
    var messageExtraFields: MessageExtraFields
)