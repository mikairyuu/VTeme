package org.lightfire.vteme.utils;

public interface CompletionListener<T, K> {
    void onComplete(T r1, K r2);
}