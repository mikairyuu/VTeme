package org.lightfire.vteme.component.upload;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.SharedPreferences;
import android.net.Uri;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.vk.api.sdk.VK;
import com.vk.api.sdk.VKApiCallback;
import com.vk.sdk.api.photos.PhotosService;
import com.vk.sdk.api.photos.dto.PhotosPhoto;
import com.vk.sdk.api.photos.dto.PhotosPhotoUpload;

import org.json.JSONObject;
import org.lightfire.vteme.VTemeConfig;
import org.lightfire.vteme.VTemeController;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.BuildVars;
import org.telegram.messenger.FileLoader;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.Utilities;
import org.telegram.tgnet.TLRPC;

import java.io.File;
import java.io.FileDescriptor;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class VKFileUploadOperation extends FileUploadOperation {
    private int currentAccount;
    private int uploadChunkSize = 64 * 1024;
    private String uploadingFilePath;
    private int state;
    private boolean started;
    private int peerId;
    private int currentType;
    private boolean slowNetwork;
    private static final int initialRequestsSlowNetworkCount = 1;
    private static final int initialRequestsCount = 8;
    private int currentUploadRequetsCount;
    private FileUploadOperationDelegate delegate;
    private String uploadLink;
    private String fallbackUploadLink;

    public VKFileUploadOperation(int instance, String location, int peerId, int type) {
        currentAccount = instance;
        uploadingFilePath = location;
        this.peerId = peerId;
        currentType = type;
    }

    @Override
    public long getTotalFileSize() {
        return 0;
    }

    @Override
    public void setDelegate(FileUploadOperationDelegate fileUploadOperationDelegate) {
        delegate = fileUploadOperationDelegate;
    }

    @Override
    public void start() {
        if (state != 0) {
            return;
        }
        state = 1;
        Utilities.stageQueue.postRunnable(() -> {
            slowNetwork = ApplicationLoader.isConnectionSlow();
            if (BuildVars.LOGS_ENABLED) {
                FileLog.d("start upload on slow network = " + slowNetwork);
            }
            for (int a = 0, count = (slowNetwork ? initialRequestsSlowNetworkCount : initialRequestsCount); a < count; a++) {
                startUploadRequest();
            }
        });
    }

    private void startUploadRequest() {
        if (state != 1) {
            return;
        }
        if (started) return;
        started = true;
        currentUploadRequetsCount++;
        File cacheFile = new File(uploadingFilePath);
        VK.execute(new PhotosService().photosGetMessagesUploadServer(peerId), new VKApiCallback<PhotosPhotoUpload>() {
            @Override
            public void success(PhotosPhotoUpload photosPhotoUpload) {
                uploadLink = photosPhotoUpload.getUploadUrl();
                fallbackUploadLink = photosPhotoUpload.getFallbackUploadUrl();
                ProgressRequestBody requestBody = new ProgressRequestBody(
                        new MultipartBody.Builder().setType(MultipartBody.FORM)
                                .addFormDataPart("photo", cacheFile.getName(),
                                        RequestBody.create(MediaType.parse("multipart/form-data"), cacheFile))
                                .build()
                        , (bytesUploaded, totalBytes) -> delegate.didChangedUploadProgress(VKFileUploadOperation.this, bytesUploaded, totalBytes));
                Request request = new Request.Builder()
                        .url(uploadLink)
                        .post(requestBody)
                        .build();
                VTemeConfig.client.newCall(request).enqueue(new Callback() {
                    @Override
                    public void onFailure(@NonNull Call call, @NonNull IOException e) {
                        cancel();
                    }

                    @Override
                    public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                        if (response.isSuccessful()) {
                            JsonObject result = JsonParser.parseString(response.body().string()).getAsJsonObject();
                            VK.execute(new PhotosService().photosSaveMessagesPhoto(result.get("photo").getAsString(),
                                    result.get("server").getAsInt(), result.get("hash").getAsString()), new VKApiCallback<List<PhotosPhoto>>() {
                                @Override
                                public void success(List<PhotosPhoto> photosPhotos) {
                                    TLRPC.TL_inputVKFile res = new TLRPC.TL_inputVKFile();
                                    PhotosPhoto resultPhoto = photosPhotos.get(0);
                                    res.id = resultPhoto.getId();
                                    res.owner_id = resultPhoto.getOwnerId().getValue();
                                    res.name = uploadingFilePath.substring(uploadingFilePath.lastIndexOf("/") + 1);
                                    delegate.didFinishUploadingFile(VKFileUploadOperation.this, res, null, null, null);
                                }

                                @Override
                                public void fail(@NonNull Exception e) {
                                    cancel();
                                }
                            });
                        } else {
                            cancel();
                        }
                    }
                });
            }

            @Override
            public void fail(@NonNull Exception e) {
                cancel();
            }
        });
    }

    @Override
    public void onNetworkChanged(final boolean slow) {}

    @Override
    public void cancel() {
        delegate.didFailedUploadingFile(this);
    }

    @Override
    public void checkNewDataAvailable(long newAvailableSize, long finalSize) {}

    @Override
    public void setForceSmallFile() {}
}
