package org.lightfire.vteme.vkapi.longpoll

import com.google.gson.JsonArray
import com.google.gson.JsonDeserializationContext
import com.google.gson.JsonDeserializer
import com.google.gson.JsonElement
import org.lightfire.vteme.vkapi.longpoll.DTO.LPServerResponseWrapper
import org.lightfire.vteme.vkapi.longpoll.DTO.MessageExtraFields
import org.lightfire.vteme.vkapi.longpoll.DTO.MessageFlagsChangedUpdate
import java.lang.reflect.Type

class UpdateDeserializer : JsonDeserializer<LPServerResponseWrapper> {

    override fun deserialize(
        json: JsonElement?,
        typeOfT: Type?,
        context: JsonDeserializationContext?
    ): LPServerResponseWrapper {
        //TODO: handle errors as well
        val updateList: MutableList<Any> = mutableListOf()
        val result = LPServerResponseWrapper(0, updateList)
        val obj = json!!.asJsonObject
        result.ts = obj.asInt
        for (elem in obj.getAsJsonArray("updates")) {
            val curArray = elem.asJsonArray
            when (curArray[0].asInt) {
                1 -> updateList.add(
                    MessageFlagsChangedUpdate(
                        curArray[1].asInt,
                        curArray[2].asInt,
                        parseMessageExtraFields(3, curArray)
                    )
                )
            }

        }
        return result
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