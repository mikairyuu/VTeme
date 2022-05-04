package org.lightfire.vteme

import android.content.ContentProviderOperation
import com.vk.api.sdk.VK.execute
import com.vk.api.sdk.VKApiCallback
import com.vk.sdk.api.base.dto.BaseUserGroupFields
import com.vk.sdk.api.messages.MessagesService
import com.vk.sdk.api.messages.dto.MessagesGetConversationsResponse
import okhttp3.OkHttpClient
import org.lightfire.vteme.utils.UIUtil.runOnIoDispatcher
import org.lightfire.vteme.vkapi.DTOConverters
import org.lightfire.vteme.vkapi.longpoll.VKLongPollController
import org.telegram.messenger.AccountInstance
import org.telegram.messenger.AndroidUtilities
import org.telegram.messenger.BaseController
import org.telegram.messenger.NotificationCenter
import org.telegram.messenger.NotificationCenter.NotificationCenterDelegate
import org.telegram.tgnet.ConnectionsManager.*
import java.util.*

class VTemeController(val num: Int) : BaseController(num), NotificationCenterDelegate {

    var state = AccountInstance.getInstance(num).connectionsManager.connectionState

    init {
        AndroidUtilities.runOnUIThread {
            notificationCenter.addObserver(
                this,
                NotificationCenter.didUpdateConnectionState
            )
        }
    }

    fun initPolling() {
        if (state != ConnectionStateWaitingForNetwork && state != ConnectionStateConnecting) {
            VKLongPollController.getInstance(num)!!.initLongPoll(true)
        }
    }

    fun loadVKMessages(onSuccess: Runnable? = null) {
        execute(MessagesService().messagesGetConversations(
            null,
            5,
            null,
            null,
            Arrays.asList(BaseUserGroupFields.ID, BaseUserGroupFields.NAME),
            null
        ), object : VKApiCallback<MessagesGetConversationsResponse?> {
            override fun success(result: MessagesGetConversationsResponse?) {
                if (result != null) {
                    runOnIoDispatcher {
                        val convRes = DTOConverters.VKDialogsConverter(result)
                        messagesStorage.putDialogs(convRes, 0)
                        messagesController.processDialogsUpdate(convRes, null, false)
                        messagesController.applyDialogsNotificationsSettings(convRes.dialogs)
                        onSuccess!!.run()
                    }
                }
            }

            override fun fail(e: Exception) {}
        })
    }

    companion object {
        @JvmStatic
        val client: OkHttpClient by lazy { OkHttpClient() }

        @Volatile
        private var Instance: VTemeController? = null
        fun getInstance(num: Int): VTemeController? {
            var localInstance = Instance
            if (localInstance == null) {
                synchronized(VTemeController::class.java) {
                    localInstance = Instance
                    if (localInstance == null) {
                        localInstance = VTemeController(num)
                        Instance = localInstance
                    }
                }
            }
            return localInstance
        }
    }

    override fun didReceivedNotification(id: Int, account: Int, vararg args: Any?) {
        when (id) {
            NotificationCenter.didUpdateConnectionState -> {
                state = AccountInstance.getInstance(account).connectionsManager.connectionState
                if (state == ConnectionStateWaitingForNetwork) {
                    VKLongPollController.getInstance(account)!!.stopPolling()
                } else if (state == ConnectionStateConnected) {
                    VKLongPollController.getInstance(account)!!.startPolling()
                }
            }
        }
    }
}