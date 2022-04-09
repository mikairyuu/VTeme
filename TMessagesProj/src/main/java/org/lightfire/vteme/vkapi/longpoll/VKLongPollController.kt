package org.lightfire.vteme.vkapi.longpoll

import androidx.collection.LongSparseArray
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.vk.api.sdk.VK
import com.vk.api.sdk.VKApiCallback
import com.vk.sdk.api.messages.MessagesService
import com.vk.sdk.api.messages.dto.MessagesGetLongPollHistoryResponse
import com.vk.sdk.api.messages.dto.MessagesLongpollParams
import okhttp3.*
import org.lightfire.vteme.VTemeController
import org.lightfire.vteme.vkapi.DTOConverters
import org.lightfire.vteme.vkapi.longpoll.DTO.*
import org.telegram.messenger.*
import org.telegram.tgnet.TLRPC
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap

class VKLongPollController private constructor(num: Int) : BaseController(num) {

    var cache: MessagesGetLongPollHistoryResponse? = null
    private var vkKey: String? = null
    private var vkServer: String? = null
    private var inited = false

    private val okhttpClient: OkHttpClient by lazy { OkHttpClient() }
    private val gson: Gson

    init {
        val gsonBuilder = GsonBuilder()
        gsonBuilder.registerTypeAdapter(LPServerResponseWrapper::class.java, UpdateDeserializer())
        gson = gsonBuilder.create()
    }

    fun initLongPoll(onSuccessInit: Boolean = false, firstTime: Boolean = false) {
        if (VK.getUserId().value == 0L) return
        VK.execute(
            MessagesService().messagesGetLongPollServer(true, null, 3),
            object : VKApiCallback<MessagesLongpollParams?> {
                override fun success(result: MessagesLongpollParams?) {
                    inited = true
                    if (result == null) return
                    vkServer = result.server
                    vkKey = result.key
                    if (firstTime) {
                        messagesStorage.saveVKDiffParams(
                            result.ts,
                            result.pts!!,
                            messagesStorage.vkLastMaxMsgId
                        )
                    }
                    if (messagesStorage.vkLastTs == result.ts) {
                        if (onSuccessInit) startPolling()
                    } else {
                        connectionsManager.setIsUpdating(true)
                        getHistoryDiff(result.pts!!) {
                            connectionsManager.setIsUpdating(false)
                            if (it) {
                                messagesStorage.saveVKDiffParams(
                                    result.ts,
                                    result.pts!!,
                                    messagesStorage.vkLastMaxMsgId
                                )
                                startPolling()
                            }
                        }
                    }
                }

                override fun fail(e: Exception) {}
            })
    }

    fun startPolling() {
        if (!inited) return
        if (okhttpClient.dispatcher.idleCallback == null) {
            okhttpClient.dispatcher.idleCallback = Runnable {
                okhttpClient.newCall(
                    Request.Builder()
                        .url("https://${vkServer}?act=a_check&key=${vkKey}&ts=${messagesStorage.vkLastTs}&wait=25&mode=${32 + 8 + 2}&version=3")
                        .build()
                ).enqueue(object : Callback {
                    override fun onResponse(call: Call, response: Response) {
                        response.use {
                            if (it.body != null) {
                                processLPResult(
                                    gson.fromJson(
                                        it.body!!.charStream(),
                                        LPServerResponseWrapper::class.java
                                    )
                                )
                            }
                        }
                    }

                    override fun onFailure(call: Call, e: IOException) {}
                })
            }
            okhttpClient.dispatcher.idleCallback!!.run()
        }
    }

    fun stopPolling() {
        if (!inited) return
        okhttpClient.dispatcher.idleCallback = null
    }

    private fun convertMiscUpdate(update: Any): TLRPC.Update? {
        when (update) {
            is MessagesReadInbox -> {
                return TLRPC.TL_updateReadHistoryInbox().apply {
                    val isChat = update.peer_id >= 2000000000
                    max_id = update.local_id
                    if (isChat) {
                        peer = TLRPC.TL_peerChat()
                        peer.chat_id = update.peer_id.toLong()
                    } else {
                        peer = TLRPC.TL_peerUser()
                        peer.user_id = update.peer_id.toLong()
                    }
                }
            }

            is MessagesReadOutbox -> {
                return TLRPC.TL_updateReadHistoryOutbox().apply {
                    val isChat = update.peer_id >= 2000000000
                    max_id = update.local_id
                    if (isChat) {
                        peer = TLRPC.TL_peerChat()
                        peer.chat_id = update.peer_id.toLong()
                    } else {
                        peer = TLRPC.TL_peerUser()
                        peer.user_id = update.peer_id.toLong()
                    }
                }
            }

            is MessageFlagsSet -> {
                if ((update.mask and 128) != 0) return TLRPC.TL_updateDeleteMessages()
                    .apply { messages.add(update.message_id) }
            }

            is PushSettingsChanged -> {
                return TLRPC.TL_updateNotifySettings().apply {
                    peer = TLRPC.TL_notifyPeer().apply {
                        if (update.peer_id >= 2000000000) {
                            peer = TLRPC.TL_peerChat()
                            peer.chat_id = update.peer_id.toLong()
                        } else {
                            peer = TLRPC.TL_peerUser()
                            peer.user_id = update.peer_id.toLong()
                        }
                    }
                    notify_settings = TLRPC.TL_peerNotifySettings()
                    notify_settings.flags = 6
                    notify_settings.mute_until =
                        if (update.disabled_until == -1) Int.MAX_VALUE else 0
                    notify_settings.silent = update.sound == 1
                }
            }

            is UserTypesTextDialog -> {
                return TLRPC.TL_updateUserTyping().apply {
                    action = TLRPC.TL_sendMessageTypingAction()
                    user_id = update.user_id.toLong()
                }
            }

            is UserTypesTextChat -> {
                return TLRPC.TL_updateChatUserTyping().apply {
                    action = TLRPC.TL_sendMessageTypingAction()
                    chat_id = update.chat_id.toLong()
                    from_id = TLRPC.TL_peerUser()
                    from_id.user_id = update.user_id.toLong()
                }
            }
        }
        return null
    }

    private fun processLPResult(updates: LPServerResponseWrapper) {
        if (updates.ts == -1) {
            initLongPoll(true)
        } else {
            val time = connectionsManager.currentTime
            val updatesRest = arrayListOf<TLRPC.Update>()
            var missingData = false
            outer@ for (update in updates.updates) {
                when (update) {
                    is NewMessageAdded -> {
                        if (update.extraFields == null) return
                        val newMsg = DTOConverters.makeVk(TLRPC.TL_message())
                        val peerId = update.extraFields!!.peer_id.toLong()
                        val isChat = peerId >= 2000000000
                        newMsg.out = (update.flags and 2) != 0
                        val from_id =
                            if (isChat) update.extraFields!!.attachments!!.from_id!!.toLong() else
                                if (newMsg.out) VK.getUserId().value else peerId
                        if (!messagesController.dialogs_dict.containsKey(if (isChat) -peerId else peerId))
                            missingData = true
                        if (isChat && !messagesController.users.containsKey(from_id))
                            missingData = true
                        if (missingData) {
                            break@outer
                        }
                        newMsg.date = update.extraFields!!.timestamp!!
                        newMsg.message = update.extraFields!!.text
                        newMsg.from_id = TLRPC.TL_peerUser().apply { user_id = from_id }
                        newMsg.peer_id = if (isChat) TLRPC.TL_peerChat().apply { chat_id = peerId }
                        else TLRPC.TL_peerUser().apply { user_id = peerId }
                        newMsg.id = update.message_id
                        newMsg.dialog_id = if (isChat) -peerId else peerId
                        newMsg.unread = (update.flags and 1) != 0
                        newMsg.flags = newMsg.flags or 256

                        AndroidUtilities.runOnUIThread {
                            messagesController.updateInterfaceWithVKMessages(
                                newMsg.dialog_id,
                                arrayListOf(MessageObject(currentAccount, newMsg, false, false))
                            )
                            notificationCenter.postNotificationName(NotificationCenter.dialogsNeedReload)
                        }

                        messagesStorage.putMessages(
                            arrayListOf(newMsg),
                            false,
                            true,
                            false,
                            0,
                            false
                        )
                    }

                    is UsersTypeTextChat -> {
                        repeat(update.total_count) {
                            updatesRest.add(TLRPC.TL_updateChatUserTyping().apply {
                                action = TLRPC.TL_sendMessageTypingAction()
                                chat_id = update.peer_id.toLong()
                                from_id = TLRPC.TL_peerUser()
                                from_id.user_id = update.users_ids[it].toLong()
                            })
                        }
                    }
                }
                val miscUpdate = convertMiscUpdate(update)
                if (miscUpdate != null) updatesRest.add(miscUpdate)
            }
            if (!missingData && updatesRest.isNotEmpty()) missingData =
                missingData or !messagesController.processUpdateArray(
                    updatesRest,
                    null,
                    null,
                    false,
                    time
                )
            if (missingData) {
                stopPolling()
                getHistoryDiff(updates.pts) {
                    if (it) {
                        messagesStorage.saveVKDiffParams(
                            updates.ts,
                            updates.pts,
                            messagesStorage.vkLastMaxMsgId
                        )
                        startPolling()
                    }
                }
            } else {
                messagesStorage.saveVKDiffParams(
                    updates.ts,
                    updates.pts,
                    messagesStorage.vkLastMaxMsgId
                )
            }
        }
    }

    fun getHistoryDiff(
        newPts: Int = 0,
        onComplete: ((success: Boolean) -> Unit)? = null,
    ) {
        val limit = if (ApplicationLoader.isConnectedOrConnectingToWiFi()) 3000 else 700
        if (newPts - messagesStorage.vkLastPts > limit && newPts != 0) {
            VTemeController.getInstance(currentAccount)!!.loadVKMessages {
                getInstance(0)!!.initLongPoll(true, true)
            }
        } else {
            VK.execute(MessagesService().messagesGetLongPollHistory(
                ts = messagesStorage.vkLastTs,
                pts = messagesStorage.vkLastPts,
                maxMsgId = if (messagesStorage.vkLastMaxMsgId != 0) messagesStorage.vkLastMaxMsgId else null,
                lpVersion = 3,
                extended = true
            ),
                object : VKApiCallback<MessagesGetLongPollHistoryResponse?> {
                    override fun success(result: MessagesGetLongPollHistoryResponse?) {
                        if (result == null) onComplete?.invoke(false)
                        val usersArray = arrayListOf<TLRPC.User>()
                        val usersDict = LongSparseArray<TLRPC.User>()
                        val chatsArray = arrayListOf<TLRPC.Chat>()
                        val chatsDict = LongSparseArray<TLRPC.Chat>()
                        val newDialogArray = arrayListOf<TLRPC.Dialog>()

                        if (result!!.profiles != null)
                            for (a in result.profiles!!)
                                DTOConverters.VKUserConverter(a).apply {
                                    usersArray.add(this)
                                    usersDict.put(a.id.value, this)
                                }
                        if (result.conversations != null)
                            for (a in result.conversations!!) {
                                if (a.peer.type.value == "chat") {
                                    val convRes = DTOConverters.VKConversationConverter(a)
                                    chatsDict.put(convRes.second.id, convRes.second)
                                    chatsArray.add(convRes.second)
                                    if (messagesController.dialogs_dict[-convRes.second.id] == null)
                                        newDialogArray.add(convRes.first)
                                } else {
                                    if (messagesController.dialogs_dict[a.peer.id.toLong()] == null)
                                        newDialogArray.add(DTOConverters.VKConversationConverter(a).first)
                                }
                            }

                        AndroidUtilities.runOnUIThread {
                            messagesController.clearFullUsers()
                            messagesController.putUsers(usersArray, false)
                            messagesController.putChats(chatsArray, false)
                        }

                        messagesStorage.storageQueue.postRunnable {
                            messagesStorage.putUsersAndChats(usersArray, chatsArray, true, false)
                            Utilities.stageQueue.postRunnable {
                                if (newDialogArray.isNotEmpty()) messagesController.applyDialogsNotificationsSettings(
                                    newDialogArray
                                )

                                if (result.messages?.items?.isEmpty() == false) {
                                    val tg_msg = arrayListOf<TLRPC.Message>()
                                    for (msg in result.messages!!.items!!)
                                        if (msg.deleted?.value != 1) tg_msg.add(
                                            DTOConverters.VKMessageConverter(msg)
                                        )

                                    val messages = LongSparseArray<ArrayList<MessageObject>>()
                                    ImageLoader.saveMessagesThumbs(tg_msg)
                                    val pushMessages = ArrayList<MessageObject>()
                                    val clientUserId = userConfig.getClientUserId()
                                    for (a in tg_msg.indices) {
                                        val message: TLRPC.Message = tg_msg.get(a)
                                        if (message is TLRPC.TL_messageEmpty) {
                                            continue
                                        }
                                        MessageObject.getDialogId(message)
                                        if (message.action is TLRPC.TL_messageActionChatDeleteUser) {
                                            val user = usersDict[message.action.user_id]
                                            if (user != null && user.bot) {
                                                message.reply_markup =
                                                    TLRPC.TL_replyKeyboardHide()
                                                message.flags = message.flags or 64
                                            }
                                        }
                                        if (message.action is TLRPC.TL_messageActionChatMigrateTo || message.action is TLRPC.TL_messageActionChannelCreate) {
                                            message.unread = false
                                            message.media_unread = false
                                        } else {
                                            val read_max: ConcurrentHashMap<Long, Int> =
                                                if (message.out) messagesController.dialogs_read_outbox_max else messagesController.dialogs_read_inbox_max
                                            var value = read_max[message.dialog_id]
                                            if (value == null) {
                                                value = messagesStorage.getDialogReadMax(
                                                    message.out,
                                                    message.dialog_id
                                                )
                                                read_max[message.dialog_id] = value
                                            }
                                            message.unread = value < message.id
                                        }

                                        if (message.dialog_id == clientUserId) {
                                            message.unread = false
                                            message.media_unread = false
                                            message.out = true
                                        }
                                        val isDialogCreated: Boolean =
                                            messagesController.createdDialogIds.contains(message.dialog_id)
                                        val obj = MessageObject(
                                            currentAccount,
                                            message,
                                            usersDict,
                                            chatsDict,
                                            isDialogCreated,
                                            isDialogCreated
                                        )
                                        if ((!obj.isOut || obj.messageOwner.from_scheduled) && obj.isUnread) {
                                            pushMessages.add(obj)
                                        }
                                        var arr = messages[message.dialog_id]
                                        if (arr == null) {
                                            arr = ArrayList()
                                            messages.put(message.dialog_id, arr)
                                        }
                                        arr.add(obj)
                                    }
                                    AndroidUtilities.runOnUIThread {
                                        for (a in 0 until messages.size()) {
                                            val key = messages.keyAt(a)
                                            val value = messages.valueAt(a)
                                            messagesController.updateInterfaceWithVKMessages(
                                                key,
                                                value
                                            )
                                        }
                                        notificationCenter.postNotificationName(NotificationCenter.dialogsNeedReload)
                                    }
                                    messagesStorage.storageQueue.postRunnable {
                                        messagesStorage.putMessages(
                                            tg_msg,
                                            true,
                                            false,
                                            false,
                                            downloadController.autodownloadMask,
                                            false
                                        )
                                    }
                                }
                                if (result.history?.isEmpty() == false) {
                                    val updatesArray =
                                        UpdateDeserializeUtils.decode(result.history!!)
                                    val tgUpdatesArray = arrayListOf<TLRPC.Update>()
                                    for (update in updatesArray) {
                                        val convRes = convertMiscUpdate(update)
                                        if (convRes != null) tgUpdatesArray.add(convRes)
                                    }
                                    if (tgUpdatesArray.isNotEmpty()) {
                                        messagesController.processUpdateArray(
                                            tgUpdatesArray,
                                            null,
                                            null,
                                            true,
                                            connectionsManager.currentTime
                                        )
                                    }
                                }
                                onComplete?.invoke(true)
                            }
                        }
                    }

                    override fun fail(e: Exception) {
                        onComplete?.invoke(false)
                    }
                })
        }
    }

    companion object {
        @Volatile
        private var Instance: VKLongPollController? = null
        fun getInstance(num: Int): VKLongPollController? {
            var localInstance = Instance
            if (localInstance == null) {
                synchronized(VKLongPollController::class.java) {
                    localInstance = Instance
                    if (localInstance == null) {
                        localInstance = VKLongPollController(num)
                        Instance = localInstance
                    }
                }
            }
            return localInstance
        }
    }
}