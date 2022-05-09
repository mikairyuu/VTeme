package org.lightfire.vteme.component.load;

import androidx.annotation.NonNull;

import org.lightfire.vteme.VTemeController;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.BuildVars;
import org.telegram.messenger.FileLoadOperationStream;
import org.telegram.messenger.FileLoader;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.FileRefController;
import org.telegram.messenger.ImageLoader;
import org.telegram.messenger.ImageLocation;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.StatsController;
import org.telegram.messenger.Utilities;
import org.telegram.messenger.WebFile;
import org.telegram.tgnet.ConnectionsManager;
import org.telegram.tgnet.TLRPC;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.util.ArrayList;
import java.util.Collections;
import java.util.concurrent.CountDownLatch;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.Request;
import okhttp3.Response;

public class VKFileLoadOperation extends FileLoadOperation {

    public static class VKRequestInfo {
        public int requestToken;
        public int offset;
        public Call call;
    }

    public static class Range {
        private int start;
        private int end;

        private Range(int s, int e) {
            start = s;
            end = e;
        }
    }

    private ArrayList<FileLoadOperationStream> streamListeners;

    private final static int stateIdle = 0;
    private final static int stateDownloading = 1;
    private final static int stateFailed = 2;
    private final static int stateFinished = 3;

    private final static int downloadChunkSize = 1024 * 32;
    private final static int downloadChunkSizeBig = 1024 * 128;
    private final static int maxDownloadRequests = BuildVars.DEBUG_PRIVATE_VERSION ? 8 : 4;
    private final static int maxDownloadRequestsBig = BuildVars.DEBUG_PRIVATE_VERSION ? 8 : 4;
    private final static int bigFileSizeFrom = 1024 * 1024;

    private boolean forceBig;

    private String fileName;
    private int currentQueueType;

    protected long lastProgressUpdateTime;

    private ArrayList<VKFileLoadOperation.Range> notLoadedBytesRanges;
    private volatile ArrayList<VKFileLoadOperation.Range> notLoadedBytesRangesCopy;
    private ArrayList<VKFileLoadOperation.Range> notRequestedBytesRanges;
    private int requestedBytesCount;

    private int currentAccount;
    private boolean started;
    protected TLRPC.InputFileLocation location;
    private TLRPC.InputWebFileLocation webLocation;
    private WebFile webFile;
    private volatile int state = stateIdle;
    private volatile boolean paused;
    private int downloadedBytes;
    private int totalBytesCount;
    private int bytesCountPadding;
    private int streamStartOffset;
    private FileLoadOperationDelegate delegate;
    private byte[] key;
    private byte[] iv;
    private int currentDownloadChunkSize;
    private int currentMaxDownloadRequests;
    private int renameRetryCount;

    private boolean allowDisordererFileSave;

    public Object parentObject;

    protected boolean requestingReference;
    private RandomAccessFile fileReadStream;

    private ArrayList<VKRequestInfo> requestInfos;
    private ArrayList<VKRequestInfo> delayedRequestInfos;

    private File cacheFileTemp;
    private File cacheFileFinal;
    private File cacheIvTemp;
    private File cacheFileParts;

    private String ext;
    private RandomAccessFile fileOutputStream;
    private RandomAccessFile fiv;
    private RandomAccessFile filePartsStream;
    private File storePath;
    private File tempPath;
    private boolean isForceRequest;
    private int priority;
    private int currentType;

    private String requestUrl = null;


    public VKFileLoadOperation(ImageLocation imageLocation, Object parent, String extension, int size) {
        forceBig = imageLocation.imageType == FileLoader.IMAGE_TYPE_ANIMATION;
        parentObject = parent;
        requestUrl = ((TLRPC.TL_VKfileLocation) imageLocation.location).url;
        location = new TLRPC.TL_inputFileLocation();
        location.local_id = imageLocation.location.local_id;
        location.volume_id = imageLocation.location.volume_id;
        currentType = ConnectionsManager.FileTypePhoto;
        totalBytesCount = size;
        ext = extension != null ? extension : "jpg";
    }

    public VKFileLoadOperation(int instance, WebFile webDocument) {
        currentAccount = instance;
        webFile = webDocument;
        webLocation = webDocument.location;
        totalBytesCount = webDocument.size;
        String defaultExt = FileLoader.getMimeTypePart(webDocument.mime_type);
        if (webDocument.mime_type.startsWith("image/")) {
            currentType = ConnectionsManager.FileTypePhoto;
        } else if (webDocument.mime_type.equals("audio/ogg")) {
            currentType = ConnectionsManager.FileTypeAudio;
        } else if (webDocument.mime_type.startsWith("video/")) {
            currentType = ConnectionsManager.FileTypeVideo;
        } else {
            currentType = ConnectionsManager.FileTypeFile;
        }
        allowDisordererFileSave = true;
        ext = ImageLoader.getHttpUrlExtension(webDocument.url, defaultExt);
    }

    public VKFileLoadOperation(TLRPC.Document documentLocation, Object parent) {
        try {
            parentObject = parent;
            if (documentLocation instanceof TLRPC.TL_documentEncrypted) {
                location = new TLRPC.TL_inputEncryptedFileLocation();
                location.id = documentLocation.id;
                location.access_hash = documentLocation.access_hash;
                iv = new byte[32];
                System.arraycopy(documentLocation.iv, 0, iv, 0, iv.length);
                key = documentLocation.key;
            } else if (documentLocation instanceof TLRPC.TL_document) {
                location = new TLRPC.TL_inputDocumentFileLocation();
                location.id = documentLocation.id;
                location.access_hash = documentLocation.access_hash;
                location.file_reference = documentLocation.file_reference;
                location.thumb_size = "";
                if (location.file_reference == null) {
                    location.file_reference = new byte[0];
                }
                allowDisordererFileSave = true;
            }
            totalBytesCount = documentLocation.size;
            if (key != null) {
                if (totalBytesCount % 16 != 0) {
                    bytesCountPadding = 16 - totalBytesCount % 16;
                    totalBytesCount += bytesCountPadding;
                }
            }
            ext = FileLoader.getDocumentFileName(documentLocation);
            int idx;
            if (ext == null || (idx = ext.lastIndexOf('.')) == -1) {
                ext = "";
            } else {
                ext = ext.substring(idx);
            }
            if ("audio/ogg".equals(documentLocation.mime_type)) {
                currentType = ConnectionsManager.FileTypeAudio;
            } else if (FileLoader.isVideoMimeType(documentLocation.mime_type)) {
                currentType = ConnectionsManager.FileTypeVideo;
            } else {
                currentType = ConnectionsManager.FileTypeFile;
            }
            if (ext.length() <= 1) {
                ext = FileLoader.getExtensionByMimeType(documentLocation.mime_type);
            }
        } catch (Exception e) {
            FileLog.e(e);
            onFail(true, 0);
        }
    }

    public void setEncryptFile(boolean value) {
    }

    public int getDatacenterId() {
        return -1;
    }

    public void setForceRequest(boolean forceRequest) {
        isForceRequest = forceRequest;
    }

    public boolean isForceRequest() {
        return isForceRequest;
    }

    public void setPriority(int value) {
        priority = value;
    }

    public int getPriority() {
        return priority;
    }

    public void setPaths(int instance, String name, int queueType, File store, File temp) {
        storePath = store;
        tempPath = temp;
        currentAccount = instance;
        fileName = name;
        currentQueueType = queueType;
    }

    public int getQueueType() {
        return currentQueueType;
    }

    public boolean wasStarted() {
        return started && !paused;
    }

    public int getCurrentType() {
        return currentType;
    }

    private void removePart(ArrayList<VKFileLoadOperation.Range> ranges, int start, int end) {
        if (ranges == null || end < start) {
            return;
        }
        int count = ranges.size();
        VKFileLoadOperation.Range range;
        boolean modified = false;
        for (int a = 0; a < count; a++) {
            range = ranges.get(a);
            if (start == range.end) {
                range.end = end;
                modified = true;
                break;
            } else if (end == range.start) {
                range.start = start;
                modified = true;
                break;
            }
        }
        Collections.sort(ranges, (o1, o2) -> {
            if (o1.start > o2.start) {
                return 1;
            } else if (o1.start < o2.start) {
                return -1;
            }
            return 0;
        });
        for (int a = 0; a < ranges.size() - 1; a++) {
            VKFileLoadOperation.Range r1 = ranges.get(a);
            VKFileLoadOperation.Range r2 = ranges.get(a + 1);
            if (r1.end == r2.start) {
                r1.end = r2.end;
                ranges.remove(a + 1);
                a--;
            }
        }
        if (!modified) {
            ranges.add(new VKFileLoadOperation.Range(start, end));
        }
    }

    private void addPart(ArrayList<VKFileLoadOperation.Range> ranges, int start, int end, boolean save) {
        if (ranges == null || end < start) {
            return;
        }
        boolean modified = false;
        int count = ranges.size();
        VKFileLoadOperation.Range range;
        for (int a = 0; a < count; a++) {
            range = ranges.get(a);
            if (start <= range.start) {
                if (end >= range.end) {
                    ranges.remove(a);
                    modified = true;
                    break;
                } else if (end > range.start) {
                    range.start = end;
                    modified = true;
                    break;
                }
            } else {
                if (end < range.end) {
                    VKFileLoadOperation.Range newRange = new VKFileLoadOperation.Range(range.start, start);
                    ranges.add(0, newRange);
                    modified = true;
                    range.start = end;
                    break;
                } else if (start < range.end) {
                    range.end = start;
                    modified = true;
                    break;
                }
            }
        }
        if (save) {
            if (modified) {
                try {
                    filePartsStream.seek(0);
                    count = ranges.size();
                    filePartsStream.writeInt(count);
                    for (int a = 0; a < count; a++) {
                        range = ranges.get(a);
                        filePartsStream.writeInt(range.start);
                        filePartsStream.writeInt(range.end);
                    }
                } catch (Exception e) {
                    FileLog.e(e);
                }
                if (streamListeners != null) {
                    count = streamListeners.size();
                    for (int a = 0; a < count; a++) {
                        streamListeners.get(a).newDataAvailable();
                    }
                }
            }
        }
    }

    public File getCacheFileFinal() {
        return cacheFileFinal;
    }

    public File getCurrentFile() {
        final CountDownLatch countDownLatch = new CountDownLatch(1);
        final File[] result = new File[1];
        Utilities.stageQueue.postRunnable(() -> {
            if (state == stateFinished) {
                result[0] = cacheFileFinal;
            } else {
                result[0] = cacheFileTemp;
            }
            countDownLatch.countDown();
        });
        try {
            countDownLatch.await();
        } catch (Exception e) {
            FileLog.e(e);
        }
        return result[0];
    }

    private int getDownloadedLengthFromOffsetInternal(ArrayList<VKFileLoadOperation.Range> ranges, final int offset, final int length) {
        if (ranges == null || state == stateFinished || ranges.isEmpty()) {
            if (downloadedBytes == 0) {
                return length;
            } else {
                return Math.min(length, Math.max(downloadedBytes - offset, 0));
            }
        } else {
            int count = ranges.size();
            VKFileLoadOperation.Range range;
            VKFileLoadOperation.Range minRange = null;
            int availableLength = length;
            for (int a = 0; a < count; a++) {
                range = ranges.get(a);
                if (offset <= range.start && (minRange == null || range.start < minRange.start)) {
                    minRange = range;
                }
                if (range.start <= offset && range.end > offset) {
                    availableLength = 0;
                    break;
                }
            }
            if (availableLength == 0) {
                return 0;
            } else if (minRange != null) {
                return Math.min(length, minRange.start - offset);
            } else {
                return Math.min(length, Math.max(totalBytesCount - offset, 0));
            }
        }
    }

    public float getDownloadedLengthFromOffset(final float progress) {
        ArrayList<VKFileLoadOperation.Range> ranges = notLoadedBytesRangesCopy;
        if (totalBytesCount == 0 || ranges == null) {
            return 0;
        }
        return progress + getDownloadedLengthFromOffsetInternal(ranges, (int) (totalBytesCount * progress), totalBytesCount) / (float) totalBytesCount;
    }

    public int[] getDownloadedLengthFromOffset(final int offset, final int length) {
        final CountDownLatch countDownLatch = new CountDownLatch(1);
        final int[] result = new int[2];
        Utilities.stageQueue.postRunnable(() -> {
            result[0] = getDownloadedLengthFromOffsetInternal(notLoadedBytesRanges, offset, length);
            if (state == stateFinished) {
                result[1] = 1;
            }
            countDownLatch.countDown();
        });
        try {
            countDownLatch.await();
        } catch (Exception ignore) {

        }
        return result;
    }

    public String getFileName() {
        return fileName;
    }

    public void removeStreamListener(final FileLoadOperationStream operation) {
        Utilities.stageQueue.postRunnable(() -> {
            if (streamListeners == null) {
                return;
            }
            streamListeners.remove(operation);
        });
    }

    @Override
    public boolean processRequestResult(RequestInfo requestInfo, TLRPC.TL_error error) {
        return false;
    }

    private void copyNotLoadedRanges() {
        if (notLoadedBytesRanges == null) {
            return;
        }
        notLoadedBytesRangesCopy = new ArrayList<>(notLoadedBytesRanges);
    }

    public void pause() {
        if (state != stateDownloading) {
            return;
        }
        paused = true;
    }

    public boolean start() {
        return start(null, 0, false);
    }

    public boolean start(final FileLoadOperationStream stream, final int streamOffset, final boolean steamPriority) {
        if (currentDownloadChunkSize == 0) {
            currentDownloadChunkSize = totalBytesCount >= bigFileSizeFrom || forceBig ? downloadChunkSizeBig : downloadChunkSize;
            currentMaxDownloadRequests = totalBytesCount >= bigFileSizeFrom || forceBig ? maxDownloadRequestsBig : maxDownloadRequests;
        }
        final boolean alreadyStarted = state != stateIdle;
        final boolean wasPaused = paused;
        paused = false;
        if (stream != null) {
            Utilities.stageQueue.postRunnable(() -> {
                if (streamListeners == null) {
                    streamListeners = new ArrayList<>();
                }
                streamStartOffset = streamOffset / currentDownloadChunkSize * currentDownloadChunkSize;
                streamListeners.add(stream);
                if (alreadyStarted) {
                    startDownloadRequest();
                }
            });
        } else if (wasPaused && alreadyStarted) {
            Utilities.stageQueue.postRunnable(this::startDownloadRequest);
        }
        if (alreadyStarted) {
            return wasPaused;
        }

        streamStartOffset = streamOffset / currentDownloadChunkSize * currentDownloadChunkSize;

        if (allowDisordererFileSave && totalBytesCount > 0 && totalBytesCount > currentDownloadChunkSize) {
            notLoadedBytesRanges = new ArrayList<>();
            notRequestedBytesRanges = new ArrayList<>();
        }
        String fileNameFinal;
        String fileNameTemp;
        String fileNameParts = null;
        String fileNameIv = null;

        fileNameTemp = location.volume_id + "_" + location.local_id + ".temp";
        fileNameFinal = location.volume_id + "_" + location.local_id + "." + ext;
        if (key != null) {
            fileNameIv = location.volume_id + "_" + location.local_id + ".iv";
        }
        if (notLoadedBytesRanges != null) {
            fileNameParts = location.volume_id + "_" + location.local_id + ".pt";
        }

        requestInfos = new ArrayList<>(currentMaxDownloadRequests);
        delayedRequestInfos = new ArrayList<>(currentMaxDownloadRequests - 1);
        state = stateDownloading;

        cacheFileFinal = new File(storePath, fileNameFinal);
        boolean finalFileExist = cacheFileFinal.exists();
        if (finalFileExist && (totalBytesCount != 0 && totalBytesCount != cacheFileFinal.length())) {
            cacheFileFinal.delete();
            finalFileExist = false;
        }

        if (!finalFileExist) {
            cacheFileTemp = new File(tempPath, fileNameTemp);

            if (fileNameParts != null) {
                cacheFileParts = new File(tempPath, fileNameParts);
                try {
                    filePartsStream = new RandomAccessFile(cacheFileParts, "rws");
                    long len = filePartsStream.length();
                    if (len % 8 == 4) {
                        len -= 4;
                        int count = filePartsStream.readInt();
                        if (count <= len / 2) {
                            for (int a = 0; a < count; a++) {
                                int start = filePartsStream.readInt();
                                int end = filePartsStream.readInt();
                                notLoadedBytesRanges.add(new VKFileLoadOperation.Range(start, end));
                                notRequestedBytesRanges.add(new VKFileLoadOperation.Range(start, end));
                            }
                        }
                    }
                } catch (Exception e) {
                    FileLog.e(e);
                }
            }

            if (cacheFileTemp.exists()) {
                long totalDownloadedLen = cacheFileTemp.length();
                if (fileNameIv != null && (totalDownloadedLen % currentDownloadChunkSize) != 0) {
                    requestedBytesCount = downloadedBytes = 0;
                } else {
                    requestedBytesCount = downloadedBytes = ((int) cacheFileTemp.length()) / currentDownloadChunkSize * currentDownloadChunkSize;
                }
                if (notLoadedBytesRanges != null && notLoadedBytesRanges.isEmpty()) {
                    notLoadedBytesRanges.add(new Range(downloadedBytes, totalBytesCount));
                    notRequestedBytesRanges.add(new Range(downloadedBytes, totalBytesCount));
                }
            } else if (notLoadedBytesRanges != null && notLoadedBytesRanges.isEmpty()) {
                notLoadedBytesRanges.add(new VKFileLoadOperation.Range(0, totalBytesCount));
                notRequestedBytesRanges.add(new VKFileLoadOperation.Range(0, totalBytesCount));
            }
            if (notLoadedBytesRanges != null) {
                downloadedBytes = totalBytesCount;
                int size = notLoadedBytesRanges.size();
                VKFileLoadOperation.Range range;
                for (int a = 0; a < size; a++) {
                    range = notLoadedBytesRanges.get(a);
                    downloadedBytes -= (range.end - range.start);
                }
                requestedBytesCount = downloadedBytes;
            }

            if (fileNameIv != null) {
                cacheIvTemp = new File(tempPath, fileNameIv);
                try {
                    fiv = new RandomAccessFile(cacheIvTemp, "rws");
                    if (downloadedBytes != 0) {
                        long len = cacheIvTemp.length();
                        if (len > 0 && len % 32 == 0) {
                            fiv.read(iv, 0, 32);
                        } else {
                            requestedBytesCount = downloadedBytes = 0;
                        }
                    }
                } catch (Exception e) {
                    FileLog.e(e);
                    requestedBytesCount = downloadedBytes = 0;
                }
            }
            if (downloadedBytes != 0 && totalBytesCount > 0) {
                copyNotLoadedRanges();
            }
            updateProgress();
            try {
                fileOutputStream = new RandomAccessFile(cacheFileTemp, "rws");
                if (downloadedBytes != 0) {
                    fileOutputStream.seek(downloadedBytes);
                }
            } catch (Exception e) {
                FileLog.e(e);
            }
            if (fileOutputStream == null) {
                onFail(true, 0);
                return false;
            }
            started = true;
            Utilities.stageQueue.postRunnable(() -> {
                if (totalBytesCount != 0 && downloadedBytes == totalBytesCount) {
                    try {
                        onFinishLoadingFile(false);
                    } catch (Exception e) {
                        onFail(true, 0);
                    }
                } else {
                    startDownloadRequest();
                }
            });
        } else {
            started = true;
            try {
                onFinishLoadingFile(false);
            } catch (Exception e) {
                onFail(true, 0);
            }
        }
        return true;
    }

    public void updateProgress() {
        if (delegate != null && downloadedBytes != totalBytesCount && totalBytesCount > 0) {
            delegate.didChangedLoadProgress(VKFileLoadOperation.this, downloadedBytes, totalBytesCount);
        }
    }

    public boolean isPaused() {
        return paused;
    }

    public void setIsPreloadVideoOperation(boolean value) {
    }

    public boolean isPreloadVideoOperation() {
        return false;
    }

    public boolean isPreloadFinished() {
        return false;
    }

    public void cancel() {
        cancel(false);
    }

    public void cancel(boolean deleteFiles) {
        Utilities.stageQueue.postRunnable(() -> {
            if (state != stateFinished && state != stateFailed) {
                if (requestInfos != null) {
                    for (int a = 0; a < requestInfos.size(); a++) {
                        VKRequestInfo requestInfo = requestInfos.get(a);
                        if (requestInfo.call != null) requestInfo.call.cancel();
                    }
                }
                onFail(false, 1);
            }
            if (deleteFiles) {
                if (cacheFileFinal != null) {
                    try {
                        if (!cacheFileFinal.delete()) {
                            cacheFileFinal.deleteOnExit();
                        }
                    } catch (Exception e) {
                        FileLog.e(e);
                    }
                }
                if (cacheFileTemp != null) {
                    try {
                        if (!cacheFileTemp.delete()) {
                            cacheFileTemp.deleteOnExit();
                        }
                    } catch (Exception e) {
                        FileLog.e(e);
                    }
                }
                if (cacheFileParts != null) {
                    try {
                        if (!cacheFileParts.delete()) {
                            cacheFileParts.deleteOnExit();
                        }
                    } catch (Exception e) {
                        FileLog.e(e);
                    }
                }
                if (cacheIvTemp != null) {
                    try {
                        if (!cacheIvTemp.delete()) {
                            cacheIvTemp.deleteOnExit();
                        }
                    } catch (Exception e) {
                        FileLog.e(e);
                    }
                }
            }
        });
    }

    private void cleanup() {
        try {
            if (fileOutputStream != null) {
                try {
                    fileOutputStream.getChannel().close();
                } catch (Exception e) {
                    FileLog.e(e);
                }
                fileOutputStream.close();
                fileOutputStream = null;
            }
        } catch (Exception e) {
            FileLog.e(e);
        }
        try {
            if (fileReadStream != null) {
                try {
                    fileReadStream.getChannel().close();
                } catch (Exception e) {
                    FileLog.e(e);
                }
                fileReadStream.close();
                fileReadStream = null;
            }
        } catch (Exception e) {
            FileLog.e(e);
        }
        try {
            if (filePartsStream != null) {
                try {
                    filePartsStream.getChannel().close();
                } catch (Exception e) {
                    FileLog.e(e);
                }
                filePartsStream.close();
                filePartsStream = null;
            }
        } catch (Exception e) {
            FileLog.e(e);
        }

        try {
            if (fiv != null) {
                fiv.close();
                fiv = null;
            }
        } catch (Exception e) {
            FileLog.e(e);
        }
        if (delayedRequestInfos != null) {
            delayedRequestInfos.clear();
        }
    }

    private void onFinishLoadingFile(final boolean increment) {
        if (state != stateDownloading) {
            return;
        }
        state = stateFinished;
        cleanup();
        if (cacheIvTemp != null) {
            cacheIvTemp.delete();
            cacheIvTemp = null;
        }
        if (cacheFileParts != null) {
            cacheFileParts.delete();
            cacheFileParts = null;
        }
        if (cacheFileTemp != null) {
            boolean renameResult;
            try {
                renameResult = cacheFileTemp.renameTo(cacheFileFinal);
            } catch (Exception e) {
                renameResult = false;
                FileLog.e(e);
            }
            if (!renameResult) {
                renameRetryCount++;
                if (renameRetryCount < 3) {
                    state = stateDownloading;
                    Utilities.stageQueue.postRunnable(() -> {
                        try {
                            onFinishLoadingFile(increment);
                        } catch (Exception e) {
                            onFail(false, 0);
                        }
                    }, 200);
                    return;
                }
                cacheFileFinal = cacheFileTemp;
            }

        }
        if (increment) {
            if (currentType == ConnectionsManager.FileTypeAudio) {
                StatsController.getInstance(currentAccount).incrementReceivedItemsCount(ApplicationLoader.getCurrentNetworkType(), StatsController.TYPE_AUDIOS, 1);
            } else if (currentType == ConnectionsManager.FileTypeVideo) {
                StatsController.getInstance(currentAccount).incrementReceivedItemsCount(ApplicationLoader.getCurrentNetworkType(), StatsController.TYPE_VIDEOS, 1);
            } else if (currentType == ConnectionsManager.FileTypePhoto) {
                StatsController.getInstance(currentAccount).incrementReceivedItemsCount(ApplicationLoader.getCurrentNetworkType(), StatsController.TYPE_PHOTOS, 1);
            } else if (currentType == ConnectionsManager.FileTypeFile) {
                StatsController.getInstance(currentAccount).incrementReceivedItemsCount(ApplicationLoader.getCurrentNetworkType(), StatsController.TYPE_FILES, 1);
            }
        }

        delegate.didFinishLoadingFile(VKFileLoadOperation.this, cacheFileFinal);
    }

    private void delayRequestInfo(VKRequestInfo requestInfo) {
        delayedRequestInfos.add(requestInfo);
    }

    public boolean processRequestResult(VKRequestInfo requestInfo, byte[] bytes) {
        if (state != stateDownloading) {
            return false;
        }
        requestInfos.remove(requestInfo);
        try {
            if (notLoadedBytesRanges == null && downloadedBytes != requestInfo.offset) {
                delayRequestInfo(requestInfo);
                return false;
            }
            if (bytes == null || bytes.length == 0) {
                onFinishLoadingFile(true);
                return false;
            }
            int currentBytesSize = bytes.length;

            boolean finishedDownloading;
            downloadedBytes += currentBytesSize;
            if (totalBytesCount > 0) {
                finishedDownloading = downloadedBytes >= totalBytesCount;
            } else {
                finishedDownloading = currentBytesSize != currentDownloadChunkSize || (totalBytesCount == downloadedBytes || downloadedBytes % currentDownloadChunkSize != 0) && (totalBytesCount <= 0 || totalBytesCount <= downloadedBytes);
            }

            if (notLoadedBytesRanges != null) {
                fileOutputStream.seek(requestInfo.offset);
                if (BuildVars.DEBUG_VERSION) {
                    FileLog.d("save file part " + cacheFileFinal + " offset " + requestInfo.offset);
                }
            }
            FileChannel channel = fileOutputStream.getChannel();
            channel.write(ByteBuffer.wrap(bytes));
            addPart(notLoadedBytesRanges, requestInfo.offset, requestInfo.offset + currentBytesSize, true);
            if (fiv != null) {
                fiv.seek(0);
                fiv.write(iv);
            }
            if (totalBytesCount > 0 && state == stateDownloading) {
                copyNotLoadedRanges();
                delegate.didChangedLoadProgress(VKFileLoadOperation.this, downloadedBytes, totalBytesCount);
            }

            if (finishedDownloading) {
                onFinishLoadingFile(true);
            } else {
                startDownloadRequest();
            }
        } catch (Exception e) {
            onFail(false, 0);
            FileLog.e(e);
        }
        return false;
    }

    public void onFail(boolean thread, final int reason) {
        cleanup();
        state = stateFailed;
        if (delegate != null) {
            if (thread) {
                Utilities.stageQueue.postRunnable(() -> delegate.didFailedLoadingFile(VKFileLoadOperation.this, reason));
            } else {
                delegate.didFailedLoadingFile(VKFileLoadOperation.this, reason);
            }
        }
    }

    private void clearOperaion(VKRequestInfo currentInfo, boolean preloadChanged) {
        int minOffset = Integer.MAX_VALUE;
        for (int a = 0; a < requestInfos.size(); a++) {
            VKRequestInfo info = requestInfos.get(a);
            minOffset = Math.min(info.offset, minOffset);
            removePart(notRequestedBytesRanges, info.offset, info.offset + currentDownloadChunkSize);
            if (currentInfo == info) {
                continue;
            }
            info.call.cancel();
        }
        requestInfos.clear();
        for (int a = 0; a < delayedRequestInfos.size(); a++) {
            VKRequestInfo info = delayedRequestInfos.get(a);
            removePart(notRequestedBytesRanges, info.offset, info.offset + currentDownloadChunkSize);
            minOffset = Math.min(info.offset, minOffset);
        }
        delayedRequestInfos.clear();
        if (notLoadedBytesRanges == null) {
            requestedBytesCount = downloadedBytes = minOffset;
        }
    }

    private void requestReference(VKRequestInfo requestInfo) {
        if (requestingReference) {
            return;
        }
        clearOperaion(requestInfo, false);
        requestingReference = true;
        if (parentObject instanceof MessageObject) {
            MessageObject messageObject = (MessageObject) parentObject;
            if (messageObject.getId() < 0 && messageObject.messageOwner.media.webpage != null) {
                parentObject = messageObject.messageOwner.media.webpage;
            }
        }
        FileRefController.getInstance(currentAccount).requestReference(parentObject, location, this, requestInfo);
    }

    public void startDownloadRequest() {
        if (paused ||
                state != stateDownloading ||
                ((requestInfos.size() + delayedRequestInfos.size() >= currentMaxDownloadRequests))) {
            return;
        }
        int count = 1;
        if (totalBytesCount > 0) {
            count = Math.max(0, currentMaxDownloadRequests - requestInfos.size());
        }

        if (requestUrl == null) {
            onFail(true, -1);
            return;
        }

        for (int a = 0; a < count; a++) {
            int downloadOffset;
            if (notRequestedBytesRanges != null) {
                int sreamOffset = streamStartOffset;
                int size = notRequestedBytesRanges.size();
                int minStart = Integer.MAX_VALUE;
                int minStreamStart = Integer.MAX_VALUE;
                for (int b = 0; b < size; b++) {
                    VKFileLoadOperation.Range range = notRequestedBytesRanges.get(b);
                    if (sreamOffset != 0) {
                        if (range.start <= sreamOffset && range.end > sreamOffset) {
                            minStreamStart = sreamOffset;
                            minStart = Integer.MAX_VALUE;
                            break;
                        }
                        if (sreamOffset < range.start && range.start < minStreamStart) {
                            minStreamStart = range.start;
                        }
                    }
                    minStart = Math.min(minStart, range.start);
                }
                if (minStreamStart != Integer.MAX_VALUE) {
                    downloadOffset = minStreamStart;
                } else if (minStart != Integer.MAX_VALUE) {
                    downloadOffset = minStart;
                } else {
                    break;
                }
            } else {
                downloadOffset = requestedBytesCount;
            }
            if (notRequestedBytesRanges != null) {
                addPart(notRequestedBytesRanges, downloadOffset, downloadOffset + currentDownloadChunkSize, false);
            }

            if (totalBytesCount > 0 && downloadOffset >= totalBytesCount) {
                break;
            }
            requestedBytesCount += currentDownloadChunkSize;
            final VKRequestInfo requestInfo = new VKRequestInfo();
            requestInfos.add(requestInfo);
            requestInfo.offset = downloadOffset;

            if (location instanceof TLRPC.TL_inputPeerPhotoFileLocation) {
                TLRPC.TL_inputPeerPhotoFileLocation inputPeerPhotoFileLocation = (TLRPC.TL_inputPeerPhotoFileLocation) location;
                if (inputPeerPhotoFileLocation.photo_id == 0) {
                    requestReference(requestInfo);
                    continue;
                }
            }


            requestInfo.call = VTemeController.Companion.getClient().newCall(new Request.Builder()
                    .addHeader("Range", "bytes=" + downloadOffset + "-" + (requestedBytesCount - 1))
                    .url(requestUrl)
                    .build());
            requestInfo.call.enqueue(new Callback() {
                @Override
                public void onFailure(@NonNull Call call, @NonNull IOException e) {
                    onFail(false, -1);
                }

                @Override
                public void onResponse(@NonNull Call call, @NonNull Response response) {
                    try {
                        if (response.isSuccessful()) {
                            if (totalBytesCount == 0) {
                                String rangeHeader = response.header("content-range");
                                totalBytesCount = Integer.parseInt(rangeHeader.substring(rangeHeader.indexOf('/') + 1));
                            }
                            processRequestResult(requestInfo, response.body().source().readByteArray());
                        } else {
                            throw new Exception();
                        }
                    } catch (Exception e) {
                        onFail(false, -1);
                    }
                }
            });
        }
    }

    public void setDelegate(FileLoadOperationDelegate delegate) {
        this.delegate = delegate;
    }
}