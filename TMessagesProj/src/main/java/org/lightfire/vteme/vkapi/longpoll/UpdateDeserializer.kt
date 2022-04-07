package org.lightfire.vteme.vkapi.longpoll

import com.google.gson.*
import org.lightfire.vteme.vkapi.longpoll.DTO.*
import java.lang.reflect.Type

object UpdateDeserializeUtils {
    private fun Iterable<*>.getInt(index: Int, jsonMode: Boolean): Int {
        return if (jsonMode) (this as JsonArray)[index].asInt else (this as List<Int>)[index]
    }

    //Any represents List<List<int>> or JSONArray
    fun decode(updates: Iterable<Any>): List<Any> {
        val jsonMode = updates is JsonArray
        val updateList = mutableListOf<Any>()
        for (elem in updates) {
            val array = if (jsonMode) (elem as JsonArray) else elem as List<Int>
            when (array.getInt(0, jsonMode)) {
                1 -> updateList.add(
                    MessageFlagsChanged(
                        array.getInt(1, jsonMode),
                        array.getInt(2, jsonMode),
                        if (jsonMode) parseMessageExtraFields(3, array as JsonArray) else null
                    )
                )
                2 -> updateList.add(
                    MessageFlagsSet(
                        array.getInt(1, jsonMode),
                        array.getInt(2, jsonMode),
                        if (jsonMode) parseMessageExtraFields(3, array as JsonArray) else null
                    )
                )
                3 -> updateList.add(
                    MessageFlagsReset(
                        array.getInt(1, jsonMode),
                        array.getInt(2, jsonMode),
                        if (jsonMode) parseMessageExtraFields(3, array as JsonArray) else null
                    )
                )
                4 -> updateList.add(
                    NewMessageAdded(
                        array.getInt(1, jsonMode),
                        array.getInt(2, jsonMode),
                        if (jsonMode) parseMessageExtraFields(3, array as JsonArray) else null
                    )
                )
                5 -> updateList.add(
                    MessageEdited(
                        array.getInt(1, jsonMode),
                        array.getInt(2, jsonMode),
                        array.getInt(3, jsonMode),
                        if (jsonMode) array.getInt(4, jsonMode) else null,
                        if (jsonMode) (array as JsonArray)[5].asString else null,
                        null
                    )
                )
                6 -> updateList.add(
                    MessagesReadInbox(
                        array.getInt(1, jsonMode),
                        array.getInt(2, jsonMode),
                    )
                )
                7 -> updateList.add(
                    MessagesReadOutbox(
                        array.getInt(1, jsonMode),
                        array.getInt(2, jsonMode),
                    )
                )
                8 -> updateList.add(
                    UserWentOnline(
                        array.getInt(1, jsonMode),
                        array.getInt(2, jsonMode),
                        array.getInt(3, jsonMode),
                    )
                )
                9 -> updateList.add(
                    UserWentOffline(
                        array.getInt(1, jsonMode),
                        array.getInt(2, jsonMode),
                        array.getInt(3, jsonMode),
                    )
                )
                10 -> updateList.add(
                    DialogFlagsReset(
                        array.getInt(1, jsonMode),
                        array.getInt(2, jsonMode),
                    )
                )
                11 -> updateList.add(
                    DialogFlagsChanged(
                        array.getInt(1, jsonMode),
                        array.getInt(2, jsonMode),
                    )
                )
                12 -> updateList.add(
                    DialogFlagsSet(
                        array.getInt(1, jsonMode),
                        array.getInt(2, jsonMode),
                    )
                )
                13 -> updateList.add(
                    MessagesDeleted(
                        array.getInt(1, jsonMode),
                        array.getInt(2, jsonMode),
                    )
                )
                14 -> updateList.add(
                    MessagesRestored(
                        array.getInt(1, jsonMode),
                        array.getInt(2, jsonMode),
                    )
                )
                20 -> updateList.add(
                    PeerMajorIDChanged(
                        array.getInt(1, jsonMode),
                        array.getInt(2, jsonMode),
                    )
                )
                21 -> updateList.add(
                    PeerMinorIDChanged(
                        array.getInt(1, jsonMode),
                        array.getInt(2, jsonMode),
                    )
                )
                51 -> updateList.add(
                    ChatParametersChanged(
                        array.getInt(1, jsonMode),
                        if (jsonMode) if ((array as JsonArray).size() > 2) 1 else 0 else if ((array as List<Int>).size > 2) 1 else 0
                    )
                )
                52 -> updateList.add(
                    ChatInfoChanged(
                        array.getInt(1, jsonMode),
                        array.getInt(2, jsonMode),
                        array.getInt(3, jsonMode),
                    )
                )
                61 -> updateList.add(
                    UserTypesTextDialog(
                        array.getInt(1, jsonMode),
                        array.getInt(2, jsonMode),
                    )
                )
                62 -> updateList.add(
                    UserTypesTextChat(
                        array.getInt(1, jsonMode),
                        array.getInt(2, jsonMode),
                    )
                )
                63 -> {//TODO: check the signature
                }
                64 -> {//TODO: check the signature
                }
                70 -> {
                    updateList.add(
                        UserMadeCall(
                            array.getInt(1, jsonMode),
                            array.getInt(2, jsonMode),
                        )
                    )
                }
                //80 -> ignored
                114 -> {
                    updateList.add(
                        PushSettingsChanged(
                            array.getInt(1, jsonMode),
                            array.getInt(2, jsonMode),
                            array.getInt(3, jsonMode),
                        )
                    )
                }

            }
        }
        return updateList
    }

    private fun parseMessageExtraFields(startIndex: Int, array: JsonArray): MessageExtraFields? {
        val fieldCount = array.size() - startIndex
        if (fieldCount != 0) {
            if (fieldCount > 1) {
                val attachments = array[startIndex + 3].asJsonObject
                return MessageExtraFields(
                    array[startIndex].asInt,
                    array[startIndex + 1].asInt,
                    array[startIndex + 2].asString,
                    MessageAttachments(if (attachments.has("from")) attachments.get("from").asInt else null),
                    0
                )
            } else {
                return MessageExtraFields(
                    array[startIndex].asInt,
                    null,
                    null,
                    null,
                    null
                )
            }
        } else {
            return null
        }
    }
}

class UpdateDeserializer : JsonDeserializer<LPServerResponseWrapper> {

    override fun deserialize(
        json: JsonElement?,
        typeOfT: Type?,
        context: JsonDeserializationContext?
    ): LPServerResponseWrapper {
        val obj = json!!.asJsonObject
        val updateList: MutableList<Any> = mutableListOf()
        if (obj.has("failed")) {
            updateList.add(obj.get("failed").asInt)
            if (obj.has("ts")) updateList.add(obj.get("ts").asInt)
            return LPServerResponseWrapper(-1, -1, updateList)
        }

        return LPServerResponseWrapper(obj.get("ts").asInt, obj.get("pts").asInt, UpdateDeserializeUtils.decode(obj.getAsJsonArray("updates")))
    }
}