package org.lightfire.vteme

import com.vk.api.sdk.VK.execute
import com.vk.api.sdk.VKApiCallback
import com.vk.sdk.api.base.dto.BaseUserGroupFields
import com.vk.sdk.api.messages.MessagesService
import com.vk.sdk.api.messages.dto.MessagesGetConversationsResponse
import org.lightfire.vteme.utils.UIUtil.runOnIoDispatcher
import org.lightfire.vteme.vkapi.DTOConverters
import org.lightfire.vteme.vkapi.longpoll.VKLongPollController
import org.telegram.messenger.BaseController
import java.util.*

class VTemeController(num: Int) : BaseController(num) {

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
}