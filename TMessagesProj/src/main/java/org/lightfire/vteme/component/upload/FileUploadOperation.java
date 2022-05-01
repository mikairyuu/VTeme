package org.lightfire.vteme.component.upload;

import org.telegram.tgnet.TLRPC;

public abstract class FileUploadOperation {
    public interface FileUploadOperationDelegate {
        void didFinishUploadingFile(FileUploadOperation operation, TLRPC.InputFile inputFile, TLRPC.InputEncryptedFile inputEncryptedFile, byte[] key, byte[] iv);
        void didFailedUploadingFile(FileUploadOperation operation);
        void didChangedUploadProgress(FileUploadOperation operation, long uploadedSize, long totalSize);
    }

    public long lastProgressUpdateTime;

    public abstract long getTotalFileSize();
    public abstract void setDelegate(FileUploadOperationDelegate fileUploadOperationDelegate);
    public abstract void start();
    public abstract void onNetworkChanged(final boolean slow);
    public abstract void cancel();
    public abstract void checkNewDataAvailable(final long newAvailableSize, final long finalSize);
    public abstract void setForceSmallFile();
}
