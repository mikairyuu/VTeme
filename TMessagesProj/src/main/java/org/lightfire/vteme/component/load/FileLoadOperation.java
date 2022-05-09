package org.lightfire.vteme.component.load;

import org.telegram.messenger.FileLoadOperationStream;
import org.telegram.tgnet.TLRPC;

import java.io.File;

public abstract class FileLoadOperation {
    public TLRPC.InputFileLocation location;
    public boolean requestingReference;
    public long lastProgressUpdateTime;

    public abstract void removeStreamListener(final FileLoadOperationStream operation);

    public abstract boolean processRequestResult(RequestInfo requestInfo, TLRPC.TL_error error);

    public abstract boolean isPreloadFinished();

    public abstract void setDelegate(FileLoadOperationDelegate fileLoadOperationDelegate);

    public abstract void setPriority(int priority);

    public abstract File getCurrentFile();

    public abstract void startDownloadRequest();

    public abstract void onFail(boolean b, int i);

    public static class RequestInfo {
        public int requestToken;
        public int offset;
        public TLRPC.TL_upload_file response;
        public TLRPC.TL_upload_webFile responseWeb;
        public TLRPC.TL_upload_cdnFile responseCdn;
    }

    public interface FileLoadOperationDelegate {
        void didFinishLoadingFile(FileLoadOperation operation, File finalFile);
        void didFailedLoadingFile(FileLoadOperation operation, int state);
        void didChangedLoadProgress(FileLoadOperation operation, long uploadedSize, long totalSize);
    }

    public abstract int getPriority();

    public abstract int[] getDownloadedLengthFromOffset(int offset, int readLength);

    public abstract File getCacheFileFinal();

    public abstract boolean isPaused();

    public abstract boolean isForceRequest();

    public abstract boolean start();

    public abstract boolean wasStarted();

    public abstract int getQueueType();

    public abstract int getDatacenterId();

    public abstract void setForceRequest(boolean b);

    public abstract boolean isPreloadVideoOperation();

    public abstract void setIsPreloadVideoOperation(boolean b);

    public abstract void cancel(boolean deleteFile);

    public abstract float getDownloadedLengthFromOffset(float position);

    public abstract void pause();

    public abstract boolean start(FileLoadOperationStream stream, int streamOffset, boolean streamPriority);

    public abstract Object getFileName();

    public abstract void updateProgress();

    public abstract void setEncryptFile(boolean b);

    public abstract void setPaths(int currentAccount, String fileName, int queueType, File storeDir, File tempDir);
}
