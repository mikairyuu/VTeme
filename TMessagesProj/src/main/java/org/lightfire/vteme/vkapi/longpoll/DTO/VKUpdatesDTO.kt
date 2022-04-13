package org.lightfire.vteme.vkapi.longpoll.DTO

data class LPServerResponseWrapper(
    var ts: Int,
    var pts: Int,
    var updates : List<Any>
)

data class MessageExtraFields(
    var peer_id: Int,
    var timestamp: Int?,
    var text: String?,
    var attachments : MessageAttachments? = null,
    var random_id: Int?,
)

data class MessageAttachments(
    val from_id: Int?,
    val source_act: String?,
    val reply_to: Int?,
    val has_forwards : Boolean,
    //TODO: fill the rest of attachment fields
)

// 1
data class MessageFlagsChanged(
    var message_id: Int,
    var flags: Int,
    var extraFields: MessageExtraFields?
)
// 2
data class MessageFlagsSet(
    var message_id: Int,
    var mask: Int,
    var extraFields: MessageExtraFields?
)
// 3
data class MessageFlagsReset(
    var message_id: Int,
    var mask: Int,
    var extraFields: MessageExtraFields?
)
// 4
data class NewMessageAdded(
    var message_id: Int,
    var flags: Int,
    var extraFields: MessageExtraFields?
)
// 5
data class MessageEdited(
    var message_id: Int,
    var mask: Int,
    var peer_id: Int,
    var timestamp: Int?,
    var new_text: String?,
    var attachments: Any? = null,
)
// 6
data class MessagesReadInbox(
    var peer_id: Int,
    var local_id: Int,
)
// 7
data class MessagesReadOutbox(
    var peer_id: Int,
    var local_id: Int,
)
// 8
data class UserWentOnline(
    var user_id: Int,
    var extra: Int,
    var timestamp: Int
)
// 9
data class UserWentOffline(
    var user_id: Int,
    var extra: Int,
    var timestamp: Int
)
// 10
data class DialogFlagsReset(
    var peer_id: Int,
    var mask: Int,
)
// 11
data class DialogFlagsChanged(
    var peer_id: Int,
    var mask: Int,
)
// 12
data class DialogFlagsSet(
    var peer_id: Int,
    var mask: Int,
)
// 13
data class MessagesDeleted(
    var peer_id: Int,
    var local_id: Int,
)
// 14
data class MessagesRestored(
    var peer_id: Int,
    var local_id: Int,
)
// 20
data class PeerMajorIDChanged(
    var peer_id: Int,
    var major_id: Int,
)
// 21
data class PeerMinorIDChanged(
    var peer_id: Int,
    var minor_id: Int,
)
// 51
data class ChatParametersChanged(
    var chat_id: Int,
    var self: Int,
)
// 52
data class ChatInfoChanged(
    var type_id: Int,
    var peer_id: Int,
    var info: Int,
)
// 61
data class UserTypesTextDialog(
    var user_id: Int,
    var flags: Int,
)
// 62
data class UserTypesTextChat(
    var user_id: Int,
    var chat_id: Int,
)
// 63
data class UsersTypeTextChat(
    var users_ids: List<Int>,
    var peer_id: Int,
    var total_count: Int,
    var ts: Int
)
// 64
data class UsersVoiceRecChat(
    var users_ids: List<Int>,
    var peer_id: Int,
    var total_count: Int,
    var ts: Int
)
// 70
data class UserMadeCall(
    var user_id: Int,
    var call_id: Int,
)
// 80
data class CounterChanged(
    var count: Int,
)
// 114
data class PushSettingsChanged(
    var peer_id: Int,
    var sound: Int,
    var disabled_until: Int?,
)