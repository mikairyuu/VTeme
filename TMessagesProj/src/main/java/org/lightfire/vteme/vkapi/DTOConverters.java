package org.lightfire.vteme.vkapi;

import android.util.Pair;

import androidx.annotation.Nullable;

import com.vk.api.sdk.VK;
import com.vk.sdk.api.messages.dto.MessagesChat;
import com.vk.sdk.api.messages.dto.MessagesConversation;
import com.vk.sdk.api.messages.dto.MessagesConversationWithMessage;
import com.vk.sdk.api.messages.dto.MessagesGetConversationsResponse;
import com.vk.sdk.api.messages.dto.MessagesGetHistoryExtendedResponse;
import com.vk.sdk.api.messages.dto.MessagesGetLongPollHistoryResponse;
import com.vk.sdk.api.messages.dto.MessagesMessage;
import com.vk.sdk.api.users.dto.UsersUserFull;

import org.telegram.tgnet.TLObject;
import org.telegram.tgnet.TLRPC;

public class DTOConverters {
    public static TLRPC.TL_message VKMessageConverter(MessagesMessage message) {
        TLRPC.TL_message resMsg = makeVk(new TLRPC.TL_message());
        boolean isChat = message.getPeerId() >= 2000000000;
        resMsg.message = message.getText();
        resMsg.date = message.getDate();
        resMsg.out = message.getOut().getValue() == 1;
        resMsg.id = message.getId();
        resMsg.peer_id = makeVk(isChat ? new TLRPC.TL_peerChat() : new TLRPC.TL_peerUser());
        resMsg.flags = resMsg.flags | 256;
        resMsg.random_id = message.getRandomId();
        resMsg.dialog_id = isChat ? -message.getPeerId() : message.getPeerId();
        if (isChat)
            resMsg.peer_id.chat_id = message.getPeerId();
        else
            resMsg.peer_id.user_id = message.getPeerId();
        resMsg.from_id = makeVk(new TLRPC.TL_peerUser());
        resMsg.from_id.user_id = message.getFromId().getValue();
        return resMsg;
    }

    // Chat is nullable
    public static Pair<TLRPC.TL_dialog, TLRPC.TL_chat> VKConversationConverter(MessagesConversation conv) {
        TLRPC.TL_dialog ret_dialog = makeVk(new TLRPC.TL_dialog());
        Pair<TLRPC.TL_dialog, TLRPC.TL_chat> retPair;
        ret_dialog.unread_count = conv.getUnreadCount() == null ? 0 : conv.getUnreadCount();
        ret_dialog.top_message = conv.getLastMessageId();
        ret_dialog.id = conv.getPeer().getId();
        ret_dialog.notify_settings = new TLRPC.TL_peerNotifySettings();
        ret_dialog.read_inbox_max_id = conv.getInRead();
        ret_dialog.read_outbox_max_id = conv.getOutRead();
        if (conv.getPushSettings() != null){
            ret_dialog.notify_settings.mute_until = conv.getPushSettings().getDisabledForever() ? Integer.MAX_VALUE : 0;
            ret_dialog.notify_settings.flags = conv.getPushSettings().getDisabledForever() ? ret_dialog.notify_settings.flags | 4 : 0;
        }
        if (conv.getPeer().getType().getValue().equals("user")) {
            ret_dialog.peer = makeVk(new TLRPC.TL_peerUser());
            ret_dialog.peer.user_id = conv.getPeer().getId();
            retPair = new Pair<>(ret_dialog, null);
        } else if (conv.getPeer().getType().getValue().equals("chat")) {
            TLRPC.TL_chat retChat = makeVk(new TLRPC.TL_chat());
            ret_dialog.peer = makeVk(new TLRPC.TL_peerChat());
            ret_dialog.peer.chat_id = conv.getPeer().getId();
            retChat.title = conv.getChatSettings().getTitle();
            retChat.participants_count = conv.getChatSettings().getMembersCount();
            ret_dialog.id = -ret_dialog.id;
            retChat.id = conv.getPeer().getId();
            retChat.default_banned_rights = new TLRPC.TL_chatBannedRights();
            retChat.photo = new TLRPC.TL_chatPhotoEmpty();
            retPair = new Pair<>(ret_dialog, retChat);
        } else {
            retPair = new Pair<>(ret_dialog, null);
        }
        return retPair;
    }

    public static Pair<TLRPC.TL_dialog, TLRPC.TL_chat> VKConversationConverter(MessagesConversationWithMessage conv) {
        Pair<TLRPC.TL_dialog, TLRPC.TL_chat> resConv = DTOConverters.VKConversationConverter(conv.getConversation());
        if (conv.getLastMessage() != null)
            resConv.first.last_message_date = conv.getLastMessage().getDate();
        return resConv;
    }


    public static TLRPC.TL_chat VKConversationConverter(MessagesChat conv) {
        TLRPC.TL_chat retChat = makeVk(new TLRPC.TL_chat());
        retChat.title = conv.getTitle();
        retChat.participants_count = conv.getMembersCount();
        retChat.id = conv.getId();
        retChat.default_banned_rights = new TLRPC.TL_chatBannedRights();
        retChat.version = 0;
        return retChat;
    }

    public static TLRPC.TL_user VKUserConverter(UsersUserFull user) {
        TLRPC.TL_user retUser = makeVk(new TLRPC.TL_user());
        retUser.id = user.getId().getValue();
        retUser.first_name = user.getFirstName();
        retUser.last_name = user.getLastName();
        retUser.flags = retUser.flags | 6;
        retUser.photo = new TLRPC.TL_userProfilePhotoEmpty();
        retUser.status = new TLRPC.TL_userStatusRecently();
        if (VK.getUserId() == user.getId()) retUser.self = true;
        return retUser;
    }

    public static TLRPC.TL_userFull VKFullUserConverter(UsersUserFull user){
        TLRPC.TL_userFull retUser = new TLRPC.TL_userFull();
        retUser.about = user.getStatus();
        retUser.blocked = user.getBlacklistedByMe().getValue() == 1;
        retUser.flags = 3;
        retUser.user = VKUserConverter(user);
        retUser.user.username = user.getDomain();
        retUser.user.flags = retUser.user.flags | 8;
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
            TGDialogs.messages.add(DTOConverters.VKMessageConverter(msg.getLastMessage()));
        }
        for (UsersUserFull user : VKDialogs.getProfiles()) {
            TGDialogs.users.add(DTOConverters.VKUserConverter(user));
        }
        return TGDialogs;
    }

    public static TLRPC.messages_Messages VKMessagesResponseConverter(MessagesGetHistoryExtendedResponse messages) {
        TLRPC.messages_Messages res = new TLRPC.TL_messages_messagesSlice();
        res.count = messages.getCount();
        for (MessagesMessage message : messages.getItems()) {
            res.messages.add(VKMessageConverter(message));
        }
        if (messages.getProfiles() != null)
            for (UsersUserFull user : messages.getProfiles()) {
                res.users.add(VKUserConverter(user));
            }
        if (messages.getConversations() != null)
            for (MessagesConversation conversation : messages.getConversations()) {
                VKConversationConverter(new MessagesConversationWithMessage(conversation, null));
            }
        return res;
    }

    public static TLRPC.messages_Messages VKMessagesResponseConverter(MessagesGetLongPollHistoryResponse messages) {
        return VKMessagesResponseConverter(new MessagesGetHistoryExtendedResponse(
                messages.getConversations().size(),
                messages.getMessages().getItems(),
                messages.getProfiles(),
                messages.getGroups(),
                messages.getConversations()
        ));
    }

    public static <T extends TLObject> T makeVk(T object) {
        object.isVK = true;
        return object;
    }
}
