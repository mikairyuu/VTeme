package org.lightfire.vteme.vkapi;

import android.util.Pair;

import androidx.annotation.Nullable;

import com.vk.api.sdk.VK;
import com.vk.sdk.api.messages.dto.MessagesConversation;
import com.vk.sdk.api.messages.dto.MessagesConversationWithMessage;
import com.vk.sdk.api.messages.dto.MessagesGetConversationsResponse;
import com.vk.sdk.api.messages.dto.MessagesMessage;
import com.vk.sdk.api.users.dto.UsersUserFull;

import org.telegram.tgnet.TLObject;
import org.telegram.tgnet.TLRPC;

public class DTOConverters {
    public static TLRPC.TL_message VKMessageConverter(MessagesMessage message, boolean isChat) {
        TLRPC.TL_message resMsg = makeVk(new TLRPC.TL_message());
        resMsg.message = message.getText();
        resMsg.date = message.getDate();
        resMsg.views = 0;
        resMsg.id = message.getId();
        resMsg.peer_id = makeVk(new TLRPC.TL_peerUser());
        if (isChat)
            resMsg.peer_id.chat_id = message.getPeerId();
        else
            resMsg.peer_id.user_id = message.getPeerId();
        resMsg.from_id = makeVk(new TLRPC.TL_peerUser());
        resMsg.from_id.user_id = message.getFromId().getValue();
        return resMsg;
    }

    // Chat is nullable
    public static Pair<TLRPC.TL_dialog, TLRPC.TL_chat> VKConversationConverter(MessagesConversationWithMessage conversation) {
        MessagesConversation conv = conversation.getConversation();
        TLRPC.TL_dialog ret_dialog = makeVk(new TLRPC.TL_dialog());
        Pair<TLRPC.TL_dialog, TLRPC.TL_chat> retPair;
        ret_dialog.unread_count = conv.getUnreadCount() == null ? 0 : conv.getUnreadCount();
        ret_dialog.peer = makeVk(new TLRPC.TL_peerUser());
        ret_dialog.last_message_date = conversation.getLastMessage().getDate();
        ret_dialog.top_message = conv.getLastMessageId();
        if (conv.getPeer().getType().getValue().equals("user")) {
            ret_dialog.peer.user_id = conv.getPeer().getId();
            retPair = new Pair<>(ret_dialog, null);
        } else if (conv.getPeer().getType().getValue().equals("chat")) {
            TLRPC.TL_chat retChat = makeVk(new TLRPC.TL_chat());
            retChat.title = conv.getChatSettings().getTitle();
            retChat.participants_count = conv.getChatSettings().getMembersCount();
            retChat.id = conv.getSortId().getMinorId();
            ret_dialog.peer.chat_id = retChat.id;
            retChat.version = 0;
            retPair = new Pair<>(ret_dialog, retChat);
        } else {
            retPair = new Pair<>(ret_dialog, null);
        }
        return retPair;
    }

    public static TLRPC.TL_user VKUserConverter(UsersUserFull user) {
        TLRPC.TL_user retUser = makeVk(new TLRPC.TL_user());
        retUser.id = user.getId().getValue();
        retUser.first_name = user.getFirstName();
        retUser.last_name = user.getLastName();
        if (VK.getUserId() == user.getId()) retUser.self = true;
        return retUser;
    }

    public static TLRPC.messages_Dialogs VKDialogsConverter(MessagesGetConversationsResponse VKDialogs) {
        TLRPC.messages_Dialogs TGDialogs = new TLRPC.TL_messages_dialogsSlice();
        TGDialogs.count = VKDialogs.getCount();
        for (MessagesConversationWithMessage msg : VKDialogs.getItems()) {
            Pair<TLRPC.TL_dialog, TLRPC.TL_chat> res = DTOConverters.VKConversationConverter(msg);
            TGDialogs.dialogs.add(res.first);
            if (res.second != null) {
                TGDialogs.chats.add(res.second);
            }
            TGDialogs.messages.add(DTOConverters.VKMessageConverter(msg.getLastMessage(), res.second != null));
        }
        for (UsersUserFull user : VKDialogs.getProfiles()) {
            TGDialogs.users.add(DTOConverters.VKUserConverter(user));
        }
        return TGDialogs;
    }

    public static <T extends TLObject> T makeVk(T object) {
        object.isVK = true;
        return object;
    }
}
