package org.lightfire.vteme.vkapi;

import android.content.SharedPreferences;

import androidx.annotation.NonNull;
import androidx.security.crypto.EncryptedSharedPreferences;
import androidx.security.crypto.MasterKeys;

import com.vk.api.sdk.VKKeyValueStorage;

import org.telegram.messenger.ApplicationLoader;

import java.io.IOException;
import java.security.GeneralSecurityException;

public class VKKeyValueStorageImpl implements VKKeyValueStorage {

    public static SharedPreferences secPreferences = null;

    public VKKeyValueStorageImpl() {
        try {
            secPreferences = EncryptedSharedPreferences.create(
                    "vtemesec",
                    MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC),
                    ApplicationLoader.applicationContext,
                    EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                    EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            );
        } catch (GeneralSecurityException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }


    @Override
    public String get(@NonNull String s) {
        return secPreferences.getString(s, "");
    }

    @Override
    public void put(@NonNull String s, @NonNull String s1) {
        secPreferences.edit().putString(s, s1).apply();
    }

    @Override
    public void putOrRemove(@NonNull String s, String s1) {
        secPreferences.edit().putString(s, s1).apply();
    }

    @Override
    public void remove(@NonNull String s) {
        secPreferences.edit().remove(s).apply();
    }
}
