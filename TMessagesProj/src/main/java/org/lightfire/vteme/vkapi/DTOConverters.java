package org.lightfire.vteme.vkapi;

import androidx.annotation.Nullable;

import com.vk.sdk.api.messages.dto.MessagesConversation;
import com.vk.sdk.api.messages.dto.MessagesConversationWithMessage;
import com.vk.sdk.api.messages.dto.MessagesMessage;

import org.telegram.tgnet.TLObject;
import org.telegram.tgnet.TLRPC;

public class DTOConverters {

    public static TLRPC.TL_message VKMessageConverter(MessagesMessage message){
        TLRPC.TL_message resMsg = new TLRPC.TL_message();
        resMsg.isVK = true;
        resMsg.message = message.getText();
        resMsg.date = message.getDate();
        resMsg.views = 0;
        resMsg.id = message.getId();
        resMsg.from_id.user_id = message.getFromId().getValue();
        return resMsg;
    }

    // Returning object can represent both Chat and Dialogue, or null if it's of an unsupported type
    @Nullable
    public static TLObject VKConversationConverter(MessagesConversationWithMessage conversation) {
        MessagesConversation conv = conversation.getConversation();
        if (conv.getPeer().getType().getValue().equals("user")) {
            TLRPC.TL_dialog ret = new TLRPC.TL_dialog();
            ret.isVK = true;
            ret.unread_count = conv.getUnreadCount();
            ret.peer.user_id = conv.getPeer().getId();
            ret.id = conv.getSortId().getMajorId();
            ret.last_message_date = conversation.getLastMessage().getDate();
            return ret;
        } else if (conv.getPeer().getType().getValue().equals("chat")) {
            TLRPC.TL_chat ret = new TLRPC.TL_chat();
            ret.isVK = true;
            ret.title = conv.getChatSettings().getTitle();
            ret.id = conv.getSortId().getMajorId();
            ret.date = conversation.getLastMessage().getDate();
            return ret;
        } else {
            return null;
        }
    }
}
