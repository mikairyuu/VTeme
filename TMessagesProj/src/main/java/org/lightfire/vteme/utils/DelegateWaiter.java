package org.lightfire.vteme.utils;

public class DelegateWaiter<T, K> {
    private T t = null;
    private boolean tCompleted = false;
    private K k = null;
    private boolean kCompleted = false;
    boolean canceled = false;
    private final CompletionListener<T, K> onCompletionTask;

    public DelegateWaiter(CompletionListener<T, K> onCompletion) {
        onCompletionTask = onCompletion;
    }

    public void firstFinished(T r) {
        t = r;
        tCompleted = true;
        tryComplete();
    }

    public void secondFinished(K r) {
        k = r;
        kCompleted = true;
        tryComplete();
    }

    public void cancel() {
        canceled = true;
    }

    private void tryComplete() {
        if (tCompleted && kCompleted)
            if (!canceled)
                onCompletionTask.onComplete(t, k);
    }
}