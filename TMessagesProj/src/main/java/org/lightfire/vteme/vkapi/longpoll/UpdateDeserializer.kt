package org.lightfire.vteme.vkapi.longpoll

import com.google.gson.JsonArray
import com.google.gson.JsonDeserializationContext
import com.google.gson.JsonDeserializer
import com.google.gson.JsonElement
import org.lightfire.vteme.vkapi.longpoll.DTO.*
import java.lang.reflect.Type

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
            return LPServerResponseWrapper(-1, updateList)
        }
        for (elem in obj.getAsJsonArray("updates")) {
            val curArray = elem.asJsonArray
            when (curArray[0].asInt) {
                1 -> updateList.add(
                    MessageFlagsChanged(
                        curArray[1].asInt,
                        curArray[2].asInt,
                        parseMessageExtraFields(3, curArray)
                    )
                )
                2 -> updateList.add(
                    MessageFlagsSet(
                        curArray[1].asInt,
                        curArray[2].asInt,
                        parseMessageExtraFields(3, curArray)
                    )
                )
                3 -> updateList.add(
                    MessageFlagsReset(
                        curArray[1].asInt,
                        curArray[2].asInt,
                        parseMessageExtraFields(3, curArray)
                    )
                )
                4 -> updateList.add(
                    NewMessageAdded(
                        curArray[1].asInt,
                        curArray[2].asInt,
                        curArray[3].asInt,
                        parseMessageExtraFields(3, curArray)
                    )
                )
                5 -> updateList.add(
                    MessageEdited(
                        curArray[1].asInt,
                        curArray[2].asInt,
                        curArray[3].asInt,
                        curArray[4].asInt,
                        curArray[5].asString,
                        null
                    )
                )
                6 -> updateList.add(
                    MessagesReadInbox(
                        curArray[1].asInt,
                        curArray[2].asInt,
                    )
                )
                7 -> updateList.add(
                    MessagesReadOutbox(
                        curArray[1].asInt,
                        curArray[2].asInt,
                    )
                )
                8 -> updateList.add(
                    UserWentOnline(
                        curArray[1].asInt,
                        curArray[2].asInt,
                        curArray[3].asInt,
                    )
                )
                9 -> updateList.add(
                    UserWentOffline(
                        curArray[1].asInt,
                        curArray[2].asInt,
                        curArray[3].asInt,
                    )
                )
                10 -> updateList.add(
                    DialogFlagsReset(
                        curArray[1].asInt,
                        curArray[2].asInt,
                    )
                )
                11 -> updateList.add(
                    DialogFlagsChanged(
                        curArray[1].asInt,
                        curArray[2].asInt,
                    )
                )
                12 -> updateList.add(
                    DialogFlagsSet(
                        curArray[1].asInt,
                        curArray[2].asInt,
                    )
                )
                13 -> updateList.add(
                    MessagesDeleted(
                        curArray[1].asInt,
                        curArray[2].asInt,
                    )
                )
                14 -> updateList.add(
                    MessagesRestored(
                        curArray[1].asInt,
                        curArray[2].asInt,
                    )
                )
                20 -> updateList.add(
                    PeerMajorIDChanged(
                        curArray[1].asInt,
                        curArray[2].asInt,
                    )
                )
                21 -> updateList.add(
                    PeerMinorIDChanged(
                        curArray[1].asInt,
                        curArray[2].asInt,
                    )
                )
                51 -> updateList.add(
                    ChatParametersChanged(
                        curArray[1].asInt,
                        curArray[2].asInt,
                    )
                )
                52 -> updateList.add(
                    ChatInfoChanged(
                        curArray[1].asInt,
                        curArray[2].asInt,
                        curArray[3].asInt,
                    )
                )
                61 -> updateList.add(
                    UserTypesTextDialog(
                        curArray[1].asInt,
                        curArray[2].asInt,
                    )
                )
                62 -> updateList.add(
                    UserTypesTextChat(
                        curArray[1].asInt,
                        curArray[2].asInt,
                    )
                )
                63 -> {//TODO: check the signature
                }
                64 -> {//TODO: check the signature
                }
                70 -> {
                    updateList.add(
                        UserMadeCall(
                            curArray[1].asInt,
                            curArray[2].asInt,
                        )
                    )
                }
                //80 -> ignored
                114 -> {
                    updateList.add(
                        PushSettingsChanged(
                            curArray[1].asInt,
                            curArray[2].asInt,
                            curArray[3].asInt,
                        )
                    )
                }

            }
        }
        return LPServerResponseWrapper(obj.get("ts").asInt, updateList)
    }

    private fun parseMessageExtraFields(startIndex: Int, array: JsonArray): MessageExtraFields {
        return MessageExtraFields(
            array[startIndex].asInt,
            array[startIndex + 1].asInt,
            array[startIndex + 2].asString,
            null,
            array[startIndex + 4].asInt
        )
    }

}