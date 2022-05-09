package org.lightfire.vteme.component.load;

import androidx.annotation.NonNull;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.vk.api.sdk.VK;
import com.vk.api.sdk.VKApiCallback;
import com.vk.sdk.api.base.dto.BaseUploadServer;
import com.vk.sdk.api.docs.DocsService;
import com.vk.sdk.api.docs.dto.DocsDoc;
import com.vk.sdk.api.docs.dto.DocsGetMessagesUploadServerType;
import com.vk.sdk.api.docs.dto.DocsSaveResponse;
import com.vk.sdk.api.photos.PhotosService;
import com.vk.sdk.api.photos.dto.PhotosPhoto;
import com.vk.sdk.api.photos.dto.PhotosPhotoUpload;

import org.lightfire.vteme.VTemeController;
import org.telegram.messenger.Utilities;
import org.telegram.tgnet.TLRPC;

import java.io.File;
import java.io.IOException;
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
    private FileUploadOperationDelegate delegate;
    private Call currentCall = null;

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
        Utilities.stageQueue.postRunnable(this::startUploadRequest);
    }

    private void startUploadRequest() {
        if (state != 1) {
            return;
        }
        if (started) return;
        started = true;
        File cacheFile = new File(uploadingFilePath);
        if ((currentType & 16) != 0) {
            VK.execute(new DocsService().docsGetMessagesUploadServer(DocsGetMessagesUploadServerType.DOC, peerId), new VKApiCallback<BaseUploadServer>() {
                @Override
                public void success(BaseUploadServer baseUploadServer) {
                    String uploadLink = baseUploadServer.getUploadUrl();
                    currentCall = VTemeController.Companion.getClient().newCall(new Request.Builder()
                            .url(uploadLink)
                            .post(new ProgressRequestBody(
                                    new MultipartBody.Builder().setType(MultipartBody.FORM)
                                            .addFormDataPart("file", cacheFile.getName(),
                                                    RequestBody.create(MediaType.parse("multipart/form-data"), cacheFile))
                                            .build()
                                    , (bytesUploaded, totalBytes) -> delegate.didChangedUploadProgress(VKFileUploadOperation.this, bytesUploaded, totalBytes)))
                            .build());
                    currentCall.enqueue(new Callback() {
                        @Override
                        public void onFailure(@NonNull Call call, @NonNull IOException e) {
                            cancel();
                        }

                        @Override
                        public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                            if (response.isSuccessful()) {
                                JsonObject result = JsonParser.parseString(response.body().string()).getAsJsonObject();
                                VK.execute(new DocsService().docsSave(result.get("file").getAsString(), null, null, null), new VKApiCallback<DocsSaveResponse>() {
                                    @Override
                                    public void success(DocsSaveResponse docs) {
                                        TLRPC.TL_inputVKFile res = new TLRPC.TL_inputVKFile();
                                        DocsDoc resultDoc = docs.getDoc();
                                        res.id = resultDoc.getId();
                                        res.owner_id = resultDoc.getOwnerId().getValue();
                                        res.name = uploadingFilePath.substring(uploadingFilePath.lastIndexOf("/") + 1);
                                        res.isVK = true;
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
        } else {
            VK.execute(new PhotosService().photosGetMessagesUploadServer(peerId), new VKApiCallback<PhotosPhotoUpload>() {
                @Override
                public void success(PhotosPhotoUpload photosPhotoUpload) {
                    String uploadLink = photosPhotoUpload.getUploadUrl();
                    currentCall = VTemeController.Companion.getClient().newCall(new Request.Builder()
                            .url(uploadLink)
                            .post(new ProgressRequestBody(
                                    new MultipartBody.Builder().setType(MultipartBody.FORM)
                                            .addFormDataPart("photo", cacheFile.getName(),
                                                    RequestBody.create(MediaType.parse("multipart/form-data"), cacheFile))
                                            .build()
                                    , (bytesUploaded, totalBytes) -> delegate.didChangedUploadProgress(VKFileUploadOperation.this, bytesUploaded, totalBytes)))
                            .build());
                    currentCall.enqueue(new Callback() {
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
                                        res.isVK = true;
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
    }

    @Override
    public void onNetworkChanged(final boolean slow) {
    }

    @Override
    public void cancel() {
        if (currentCall != null) currentCall.cancel();
        delegate.didFailedUploadingFile(this);
    }

    @Override
    public void checkNewDataAvailable(long newAvailableSize, long finalSize) {
    }

    @Override
    public void setForceSmallFile() {
    }
}
