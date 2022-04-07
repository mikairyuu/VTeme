package org.lightfire.vteme.ui;


import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Build;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.core.app.ComponentActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.vk.api.sdk.VK;
import com.vk.api.sdk.VKApiCallback;
import com.vk.api.sdk.VKKeyValueStorage;
import com.vk.api.sdk.auth.VKAccessToken;
import com.vk.api.sdk.auth.VKAuthCallback;
import com.vk.api.sdk.auth.VKScope;
import com.vk.api.sdk.exceptions.VKAuthException;
import com.vk.sdk.api.base.dto.BaseUserGroupFields;
import com.vk.sdk.api.messages.MessagesService;
import com.vk.sdk.api.messages.dto.MessagesConversationWithMessage;
import com.vk.sdk.api.messages.dto.MessagesGetConversationsResponse;
import com.vk.sdk.api.users.dto.UsersUserFull;

import org.lightfire.vteme.VTemeConfig;
import org.lightfire.vteme.utils.UIUtil;
import org.lightfire.vteme.vkapi.DTOConverters;
import org.lightfire.vteme.vkapi.longpoll.VKLongPollController;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.R;
import org.telegram.messenger.SendMessagesHelper;
import org.telegram.messenger.browser.Browser;
import org.telegram.tgnet.TLObject;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.ActionBarMenu;
import org.telegram.ui.ActionBar.ActionBarMenuItem;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.ActionBar.ThemeDescription;
import org.telegram.ui.Cells.EmptyCell;
import org.telegram.ui.Cells.HeaderCell;
import org.telegram.ui.Cells.NotificationsCheckCell;
import org.telegram.ui.Cells.ShadowSectionCell;
import org.telegram.ui.Cells.TextCheckCell;
import org.telegram.ui.Cells.TextDetailSettingsCell;
import org.telegram.ui.Cells.TextInfoPrivacyCell;
import org.telegram.ui.Cells.TextCell;
import org.telegram.ui.Cells.TextSettingsCell;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.RecyclerListView;
import org.telegram.ui.LaunchActivity;
import org.telegram.ui.ManageLinksActivity;

import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.Arrays;


public class VTemeSettingsActivity extends BaseFragment {
    private RecyclerListView listView;
    private ListAdapter listAdapter;
    private int rowCount;

    private int SocialNetworksRow;
    private int VkRow;
    private int FaceBookRow;
    private int categories2Row;

    private int aboutRow;
    private int channelRow;
    private int sourceCodeRow;
    private int about2Row;

    @Override
    public boolean onFragmentCreate() {
        super.onFragmentCreate();
        updateRows();
        return true;
    }

    @Override
    public View createView(Context context) {
        actionBar.setBackButtonImage(R.drawable.ic_ab_back);
        actionBar.setTitle(LocaleController.getString("VTemeSettings", R.string.VTemeSettings));

        listAdapter = new ListAdapter(context);
        fragmentView = new FrameLayout(context);
        fragmentView.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundGray));
        FrameLayout frameLayout = (FrameLayout) fragmentView;

        listView = new RecyclerListView(context);
        listView.setVerticalScrollBarEnabled(false);
        listView.setLayoutManager(new LinearLayoutManager(context, LinearLayoutManager.VERTICAL, false));
        frameLayout.addView(listView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, Gravity.TOP | Gravity.LEFT));
        listView.setAdapter(listAdapter);
        listView.setOnItemClickListener((view, position, x, y) -> {
            if (position == VkRow) {
                ArrayList<VKScope> VkStuff = new ArrayList<>();
                VkStuff.add(VKScope.MESSAGES);
                VK.login(getParentActivity(), VkStuff);
            } else if (position == FaceBookRow) {
            } else if (position == channelRow) {
                MessagesController.getInstance(currentAccount).openByUserName("VTeme", this, 1);
            } else if (position == sourceCodeRow) {
                Browser.openUrl(getParentActivity(), "https://github.com/mikairyuu/VTeme");
            }
        });
        return fragmentView;
    }

    @Override
    public void onResume() {
        super.onResume();
        if (listAdapter != null) {
            listAdapter.notifyDataSetChanged();
        }
    }

    private void loadVKMessages() {
        VK.execute(new MessagesService().messagesGetConversations(null, 5, null, null, Arrays.asList(BaseUserGroupFields.ID, BaseUserGroupFields.NAME), null), new VKApiCallback<MessagesGetConversationsResponse>() {
            @Override
            public void success(MessagesGetConversationsResponse vkMsg) {
                if (vkMsg != null) {
                    UIUtil.runOnIoDispatcher(() -> {
                        TLRPC.messages_Dialogs convRes = DTOConverters.VKDialogsConverter(vkMsg);
                        getMessagesStorage().putDialogs(convRes, 0);
                        getMessagesController().processDialogsUpdate(convRes, null, false);
                        getMessagesController().applyDialogsNotificationsSettings(convRes.dialogs);
                        VKLongPollController.Companion.getInstance(0).initLongPoll(true, true);
                    });
                }
            }

            @Override
            public void fail(@NonNull Exception e) {

            }
        });
    }

    @Override
    public void onActivityResultFragment(int requestCode, int resultCode, Intent data) {
        VKAuthCallback callback = new VKAuthCallback() {

            @Override
            public void onLoginFailed(@NonNull VKAuthException e) {
                Toast.makeText(getParentActivity(), "Ошибка входа...", Toast.LENGTH_LONG).show();
                loadVKMessages(); //TEMP
            }

            @Override
            public void onLogin(@NonNull VKAccessToken vkAccessToken) {
                VTemeConfig.setVKToken(vkAccessToken);
                loadVKMessages();
            }
        };
        VK.onActivityResult(requestCode, resultCode, data, callback);
        super.onActivityResultFragment(requestCode, resultCode, data);
    }

    private void updateRows() {
        rowCount = 0;
        SocialNetworksRow = rowCount++;
        VkRow = rowCount++;
        FaceBookRow = rowCount++;
        categories2Row = rowCount++;

        aboutRow = rowCount++;
        channelRow = rowCount++;
        sourceCodeRow = rowCount++;
        about2Row = rowCount++;

        if (listAdapter != null) {
            listAdapter.notifyDataSetChanged();
        }
    }

    private class ListAdapter extends RecyclerListView.SelectionAdapter {

        private Context mContext;

        public ListAdapter(Context context) {
            mContext = context;
        }

        @Override
        public int getItemCount() {
            return rowCount;
        }

        @Override
        public void onBindViewHolder(RecyclerView.ViewHolder holder, int position) {
            switch (holder.getItemViewType()) {
                case 1: {
                    if (position == about2Row) {
                        holder.itemView.setBackground(Theme.getThemedDrawable(mContext, R.drawable.greydivider_bottom, Theme.key_windowBackgroundGrayShadow));
                    } else {
                        holder.itemView.setBackground(Theme.getThemedDrawable(mContext, R.drawable.greydivider, Theme.key_windowBackgroundGrayShadow));
                    }
                    break;
                }
                case 2: {
                    ManageLinksActivity.TextCell textCell = (ManageLinksActivity.TextCell) holder.itemView;
                    if (position == FaceBookRow) {
                        textCell.setTextAndIcon(LocaleController.getString("FaceBook", R.string.FaceBook), mContext.getResources().getDrawable(R.drawable.ab_progress), true);
                    }
                    break;
                }
                case 3: {
                    TextSettingsCell textCell = (TextSettingsCell) holder.itemView;
                    if (position == channelRow) {
                        textCell.setTextAndValue(LocaleController.getString("OfficialChannel", R.string.OfficialChannel), "@LightFire", true);
                    } else if (position == sourceCodeRow) {
                        textCell.setText(LocaleController.getString("SourceCode", R.string.SourceCode), true);
                    }
                    break;
                }
                case 4: {
                    HeaderCell headerCell = (HeaderCell) holder.itemView;
                    if (position == SocialNetworksRow) {
                        headerCell.setText(LocaleController.getString("SocialNetworks", R.string.SocialNetworks));
                    } else if (position == aboutRow) {
                        headerCell.setText(LocaleController.getString("About", R.string.About));
                    }
                    break;
                }
                case 6: {
                    TextDetailSettingsCell textDetailSettingsCell = (TextDetailSettingsCell) holder.itemView;
                    if (position == VkRow) {
                        if (VTemeConfig.VKToken == null)
                            textDetailSettingsCell.setTextAndValueAndIcon(LocaleController.getString("VK", R.string.VK), null, R.drawable.device_phone_android, true);
                        else
                            textDetailSettingsCell.setTextAndValueAndIcon(LocaleController.getString("VK", R.string.VK), LocaleController.getString("SignedIn", R.string.SignedIn) + " "
                                    + LocaleController.getString("UserId", R.string.UserId) + VTemeConfig.VKToken.getUserId(), R.drawable.device_phone_android, true);

                    }
                    break;
                }
            }
        }

        @Override
        public boolean isEnabled(RecyclerView.ViewHolder holder) {
            int type = holder.getItemViewType();
            return type == 2 || type == 3 || type == 6;
        }

        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            View view = null;
            switch (viewType) {
                case 1:
                    view = new ShadowSectionCell(mContext);
                    break;
                case 2:
                    view = new ManageLinksActivity.TextCell(mContext);
                    view.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
                    break;
                case 3:
                    view = new TextSettingsCell(mContext);
                    view.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
                    break;
                case 4:
                    view = new HeaderCell(mContext);
                    view.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
                    break;
                case 5:
                    view = new NotificationsCheckCell(mContext);
                    view.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
                    break;
                case 6:
                    view = new TextDetailSettingsCell(mContext);
                    view.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
                    break;
                case 7:
                    view = new TextInfoPrivacyCell(mContext);
                    view.setBackground(Theme.getThemedDrawable(mContext, R.drawable.greydivider, Theme.key_windowBackgroundGrayShadow));
                    break;
            }
            view.setLayoutParams(new RecyclerView.LayoutParams(RecyclerView.LayoutParams.MATCH_PARENT, RecyclerView.LayoutParams.WRAP_CONTENT));
            return new RecyclerListView.Holder(view);
        }

        @Override
        public int getItemViewType(int position) {
            if (position == categories2Row || position == about2Row) {
                return 1;
            } else if (position == FaceBookRow) {
                return 2;
            } else if (position == SocialNetworksRow || position == aboutRow) {
                return 4;
            } else if (position == VkRow) {
                return 6;
            }
            return 3;
        }
    }

}

