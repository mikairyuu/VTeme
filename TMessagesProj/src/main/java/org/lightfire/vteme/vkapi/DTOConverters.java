package org.lightfire.vteme.vkapi;

import android.util.Pair;

import androidx.annotation.Nullable;

import com.vk.api.sdk.VK;
import com.vk.sdk.api.messages.dto.MessagesConversation;
import com.vk.sdk.api.messages.dto.MessagesConversationWithMessage;
import com.vk.sdk.api.messages.dto.MessagesForeignMessage;
import com.vk.sdk.api.messages.dto.MessagesGetByConversationMessageIdExtendedResponse;
import com.vk.sdk.api.messages.dto.MessagesGetConversationsResponse;
import com.vk.sdk.api.messages.dto.MessagesGetHistoryExtendedResponse;
import com.vk.sdk.api.messages.dto.MessagesGetLongPollHistoryResponse;
import com.vk.sdk.api.messages.dto.MessagesMessage;
import com.vk.sdk.api.messages.dto.MessagesMessageAction;
import com.vk.sdk.api.messages.dto.MessagesMessageAttachment;
import com.vk.sdk.api.users.dto.UsersUserFull;

import org.telegram.tgnet.TLObject;
import org.telegram.tgnet.TLRPC;

import java.util.ArrayList;
import java.util.List;

public class DTOConverters {
    private static TLRPC.Message VKMessageConverter(MessagesMessage message) {
        TLRPC.Message resMsg;
        if (message.getAction() != null) {
            resMsg = makeVk(new TLRPC.TL_messageService());
            resMsg.action = VKActionConverter(message.getAction());
        } else {
            resMsg = makeVk(new TLRPC.TL_message());
        }

        boolean isChat = message.getPeerId() >= 2000000000;
        resMsg.message = message.getText();
        resMsg.date = message.getDate();
        resMsg.out = message.getOut().getValue() == 1;
        resMsg.id = message.getId();
        resMsg.peer_id = makeVk(isChat ? new TLRPC.TL_peerChat() : new TLRPC.TL_peerUser());
        resMsg.random_id = message.getRandomId();
        resMsg.dialog_id = isChat ? -message.getPeerId() : message.getPeerId();

        if (message.getReplyMessage() != null && message.getReplyMessage().getId() != null) {
            resMsg.flags = resMsg.flags | TLRPC.MESSAGE_FLAG_REPLY;
            resMsg.reply_to = new TLRPC.TL_messageReplyHeader();
            resMsg.reply_to.reply_to_msg_id = message.getReplyMessage().getId();
            resMsg.replyMessage = VKForeignMessageConverter(message.getReplyMessage(), null);
        }
        if (isChat)
            resMsg.peer_id.chat_id = message.getPeerId();
        else
            resMsg.peer_id.user_id = message.getPeerId();
        resMsg.from_id = makeVk(new TLRPC.TL_peerUser());
        resMsg.from_id.user_id = message.getFromId().getValue();
        resMsg.flags = resMsg.flags | TLRPC.MESSAGE_FLAG_HAS_FROM_ID;
        return resMsg;
    }

    private static TLRPC.MessageAction VKActionConverter(MessagesMessageAction action) {
        TLRPC.MessageAction retAction = null;
        String actionType = action.getType();
        switch (actionType) {
            case "chat_photo_update":
                retAction = new TLRPC.TL_messageActionChatEditPhoto();
                retAction.photo = new TLRPC.TL_photo();
                break;
            case "chat_photo_remove":
                retAction = new TLRPC.TL_messageActionChatDeletePhoto();
                break;
            case "chat_create":
                retAction = new TLRPC.TL_messageActionChatCreate();
                retAction.title = action.getText();
                retAction.users = new ArrayList<>();
                break;
            case "chat_title_update":
                retAction = new TLRPC.TL_messageActionChatEditTitle();
                retAction.title = action.getText();
                break;
            case "chat_invite_user":
                retAction = new TLRPC.TL_messageActionCustomAction();
                retAction.message = action.getMessage();
                break;
            case "chat_kick_user":
                retAction = new TLRPC.TL_messageActionChatDeleteUser();
                retAction.user_id = action.getMemberId().getValue();
                break;
            case "chat_pin_message":
                retAction = new TLRPC.TL_messageActionPinMessage();
                break;
            case "chat_unpin_message":
                retAction = new TLRPC.TL_messageActionCustomAction();
                retAction.message = action.getMessage();
                break;
            case "chat_invite_user_by_link":
                retAction = new TLRPC.TL_messageActionChatJoinedByLink();
                retAction.user_id = action.getMemberId().getValue();
                break;
        }
        return retAction;
    }

    private static TLRPC.TL_message VKForeignMessageConverter(MessagesForeignMessage message, MessagesMessage fwdOwner) {
        TLRPC.TL_message resMsg = makeVk(new TLRPC.TL_message());
        boolean isFwd = fwdOwner != null;
        boolean isChat = (isFwd ? fwdOwner.getPeerId() : message.getPeerId()) >= 2000000000;
        resMsg.message = message.getText();
        resMsg.date = isFwd ? fwdOwner.getDate() : message.getDate();
        resMsg.id = message.getConversationMessageId();
        resMsg.out = isFwd ? fwdOwner.getOut().getValue() == 1 : message.getFromId() == VK.getUserId();
        resMsg.peer_id = makeVk(isChat ? new TLRPC.TL_peerChat() : new TLRPC.TL_peerUser());
        resMsg.dialog_id = isChat ? -(isFwd ? fwdOwner.getPeerId() : message.getPeerId()) : (isFwd ? fwdOwner.getPeerId() : message.getPeerId());
        if (message.getReplyMessage() != null && !isFwd) {
            resMsg.flags = resMsg.flags | TLRPC.MESSAGE_FLAG_REPLY;
            resMsg.reply_to = new TLRPC.TL_messageReplyHeader();
            resMsg.reply_to.reply_to_msg_id = message.getReplyMessage().getId();
            resMsg.replyMessage = VKForeignMessageConverter(message.getReplyMessage(), null);
        }
        if (isFwd) {
            resMsg.flags = resMsg.flags | TLRPC.MESSAGE_FLAG_FWD;
            resMsg.fwd_from = new TLRPC.TL_messageFwdHeader();
            resMsg.fwd_from.flags = 1;
            resMsg.fwd_from.date = message.getDate();
            resMsg.fwd_from.from_id = new TLRPC.TL_peerUser();
            resMsg.fwd_from.from_id.user_id = message.getFromId().getValue();
        }
        if (isChat) resMsg.peer_id.chat_id = -resMsg.dialog_id;
        else resMsg.peer_id.user_id = resMsg.dialog_id;
        resMsg.from_id = makeVk(new TLRPC.TL_peerUser());
        resMsg.from_id.user_id = isFwd ? fwdOwner.getFromId().getValue() : message.getFromId().getValue();
        resMsg.flags = resMsg.flags | TLRPC.MESSAGE_FLAG_HAS_FROM_ID;
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
        if (conv.getPushSettings() != null) {
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
            retChat.participants_count = conv.getChatSettings().getMembersCount() != null ? conv.getChatSettings().getMembersCount() : 0;
            retChat.id = ret_dialog.id;
            ret_dialog.id = -ret_dialog.id;
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

    public static TLRPC.TL_userFull VKFullUserConverter(UsersUserFull user) {
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
            HandleMessage(TGDialogs.messages, msg.getLastMessage(), VKDialogs.getProfiles());
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
            HandleMessage(res.messages, message, messages.getProfiles());
        }
        if (messages.getProfiles() != null)
            for (UsersUserFull user : messages.getProfiles()) {
                res.users.add(VKUserConverter(user));
            }
        if (messages.getConversations() != null)
            for (MessagesConversation conversation : messages.getConversations()) {
                TLRPC.TL_chat chat = VKConversationConverter(new MessagesConversationWithMessage(conversation, null)).second;
                if (chat != null) res.chats.add(chat);
            }
        return res;
    }

    public static TLRPC.messages_Messages VKMessagesResponseConverter(MessagesGetByConversationMessageIdExtendedResponse messages) {
        return DTOConverters.VKMessagesResponseConverter(new MessagesGetHistoryExtendedResponse(messages.getCount(),
                messages.getItems(), messages.getProfiles(), messages.getGroups(), null));
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

    public static void HandleMessage(ArrayList<TLRPC.Message> arrayList, MessagesMessage message, List<UsersUserFull> users) {
        TLRPC.Message msg = handleFwds(message, users);
        arrayList.add(msg);
        handleReplies(arrayList, msg);
    }

    private static void handleReplies(ArrayList<TLRPC.Message> arrayList, TLRPC.Message message) {
        if (message.replyMessage == null) return;
        TLRPC.Message curMsg = message;
        while (curMsg.replyMessage != null) {
            arrayList.add(curMsg.replyMessage);
            curMsg = message.replyMessage;
        }
    }

    // This is quirky. Since TG handles forwards in a whole different way, there's no other way
    // But to squash all the forwards in one the same way as VK does it. Ugly...
    private static TLRPC.Message handleFwds(MessagesMessage message, List<UsersUserFull> users) {
        if (message.getFwdMessages() == null || (message.getFwdMessages() != null && message.getFwdMessages().size() == 0))
            return VKMessageConverter(message);
        ArrayList<MessagesMessageAttachment> attachments = new ArrayList<>();
        ArrayList<TLRPC.MessageEntity> entities = new ArrayList<>();
        StringBuilder textBuilder = new StringBuilder(message.getText());
        if (textBuilder.length() != 0) textBuilder.append('\n');
        if (!message.getText().isEmpty()) {
            addFwdDelimeter(textBuilder, users.stream().filter(x -> x.getId().getValue() == message.getPeerId()).findFirst().get());
        }
        List<MessagesForeignMessage> fwdMsgs = message.getFwdMessages();
        int cnt = message.getFwdMessages().size();

        long lastFromId = -1;
        for (int i = 0; i < cnt; i++) {
            MessagesForeignMessage curMsg = fwdMsgs.get(i);
            long from_id = curMsg.getFromId().getValue();

            if (cnt != 1 && from_id != lastFromId) {
                int s = textBuilder.length();
                addFwdDelimeter(textBuilder, users.stream().filter(x -> x.getId().getValue() == from_id).findFirst().get());
                TLRPC.TL_messageEntityBold entity = new TLRPC.TL_messageEntityBold();
                TLRPC.TL_messageEntityItalic entityItalic = new TLRPC.TL_messageEntityItalic();
                entity.offset = entityItalic.offset = s;
                entity.length = entityItalic.length = textBuilder.length() - s;
                entities.add(entity);
                entities.add(entityItalic);
            }
            lastFromId = from_id;

            textBuilder.append(curMsg.getText());
            if (i != cnt - 1) textBuilder.append("\n");
            if (fwdMsgs.get(i).getAttachments() != null)
                attachments.addAll(fwdMsgs.get(i).getAttachments());
        }
        TLRPC.Message resMsg = VKMessageConverter(new MessagesMessage(message.getDate(), message.getFromId(), message.getId(), message.getOut(),
                message.getPeerId(), textBuilder.toString(), null, null, attachments, null, message.getDeleted(), null, null,
                message.getImportant(), message.isHidden(), message.isCropped(), null, message.getMembersCount(), null, message.getRandomId(), null, null,
                message.getReplyMessage(), message.getUpdateTime(), message.getWasListened(), message.getPinnedAt(), message.isSilent()));

        resMsg.flags = resMsg.flags | TLRPC.MESSAGE_FLAG_FWD;
        resMsg.fwd_from = new TLRPC.TL_messageFwdHeader();
        resMsg.fwd_from.flags = 1;
        resMsg.fwd_from.date = message.getFwdMessages().get(0).getDate();
        resMsg.fwd_from.from_id = new TLRPC.TL_peerUser();
        resMsg.fwd_from.from_id.user_id = message.getFwdMessages().get(0).getFromId().getValue();
        if (!entities.isEmpty()) {
            resMsg.flags = resMsg.flags | TLRPC.MESSAGE_FLAG_HAS_ENTITIES;
            resMsg.entities = entities;
        }
        return resMsg;
    }

    private static void addFwdDelimeter(StringBuilder curBuilder, UsersUserFull nextUser) {
        curBuilder.append(nextUser.getFirstName()).append(' ').append(nextUser.getLastName()).append(" написал(а):\n");
    }

    public static <T extends TLObject> T makeVk(T object) {
        object.isVK = true;
        return object;
    }
}
