package org.lightfire.vteme.vkapi.longpoll

import com.vk.api.sdk.VK
import com.vk.api.sdk.VKApiCallback
import com.vk.sdk.api.GsonHolder
import com.vk.sdk.api.messages.MessagesService
import com.vk.sdk.api.messages.dto.MessagesGetLongPollHistoryResponse
import com.vk.sdk.api.messages.dto.MessagesLongpollParams
import okhttp3.*
import org.lightfire.vteme.vkapi.longpoll.DTO.LPServerResponseWrapper
import org.telegram.messenger.BaseController
import java.io.IOException

class VKLongPollController private constructor(num: Int) : BaseController(num) {

    var cache: MessagesGetLongPollHistoryResponse? = null
    private var vkKey: String? = null
    private var vkServer: String? = null

    private var ts: Int? = 0
    private var pts = 0

    private var okhttpClient: OkHttpClient = OkHttpClient()
    private var pendingResponse: Response? = null

    fun updateServerValues() {
        VK.execute(
            MessagesService().messagesGetLongPollServer(true, null, 3),
            object : VKApiCallback<MessagesLongpollParams?> {
                override fun success(result: MessagesLongpollParams?) {
                    vkServer = result?.server
                    vkKey = result?.key
                    //getMessagesStorage().saveVKDiffParams(messagesLongpollParams.getTs(),messagesLongpollParams.getPts());
                    ts = result?.ts
                    pts = result?.pts!!
                }

                override fun fail(e: Exception) {}
            })
    }

    fun initLP() {
        if (okhttpClient.dispatcher.runningCallsCount() > 0) return
        okhttpClient.newCall(
            Request.Builder()
                .url("https://${vkServer}?act=a_check&key=${vkKey}&ts=${ts}&wait=25&mode=${32 + 8}&version=3")
                .build()
        ).enqueue(object : Callback {
            override fun onResponse(call: Call, response: Response) {
                response.use {
                    GsonHolder.gson.fromJson(
                        it.body!!.charStream(),
                        LPServerResponseWrapper::class.java
                    )
                }
            }

            override fun onFailure(call: Call, e: IOException) {
                e.printStackTrace()
            }
        })
    }

    fun getHistoryDiff() {
        VK.execute(MessagesService().messagesGetLongPollHistory(
            ts, pts,
            null, true, null, null, null, null, null, 3, null, true
        ),
            object : VKApiCallback<MessagesGetLongPollHistoryResponse?> {
                override fun success(result: MessagesGetLongPollHistoryResponse?) {
                    cache = result
                }

                override fun fail(e: Exception) {}
            })
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