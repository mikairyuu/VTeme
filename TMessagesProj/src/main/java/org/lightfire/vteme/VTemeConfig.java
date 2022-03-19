package org.lightfire.vteme;

import static org.lightfire.vteme.config.ConfigItem.configTypeBool;
import static org.lightfire.vteme.config.ConfigItem.configTypeFloat;
import static org.lightfire.vteme.config.ConfigItem.configTypeInt;
import static org.lightfire.vteme.config.ConfigItem.configTypeLong;
import static org.lightfire.vteme.config.ConfigItem.configTypeMapIntInt;
import static org.lightfire.vteme.config.ConfigItem.configTypeSetInt;
import static org.lightfire.vteme.config.ConfigItem.configTypeString;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Base64;

import com.vk.api.sdk.VKKeyValueStorage;
import com.vk.api.sdk.auth.VKAccessToken;

import org.lightfire.vteme.config.ConfigItem;
import org.lightfire.vteme.vkapi.VKKeyValueStorageImpl;
import org.telegram.messenger.ApplicationLoader;

import java.io.ByteArrayInputStream;
import java.io.ObjectInputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;

public class VTemeConfig {

    public static final SharedPreferences preferences = ApplicationLoader.applicationContext.getSharedPreferences("vtemecfg", Context.MODE_PRIVATE);

    public static final Object sync = new Object();

    private static boolean configLoaded = false;
    private static final ArrayList<ConfigItem> configs = new ArrayList<>();
    private static final VKKeyValueStorage vkKeyValueStorage = new VKKeyValueStorageImpl();

    public static VKAccessToken VKToken = null;

    static {
        loadConfig(false);
    }

    public static ConfigItem addConfig(String k, int t, Object d) {
        ConfigItem a = new ConfigItem(k, t, d);
        configs.add(a);
        return a;
    }

    public static void loadConfig(boolean force) {
        synchronized (sync) {
            if (configLoaded && !force) {
                return;
            }
            for (int i = 0; i < configs.size(); i++) {
                ConfigItem o = configs.get(i);

                if (o.type == configTypeBool) {
                    o.value = preferences.getBoolean(o.key, (boolean) o.defaultValue);
                }
                if (o.type == configTypeInt) {
                    o.value = preferences.getInt(o.key, (int) o.defaultValue);
                }
                if (o.type == configTypeLong) {
                    o.value = preferences.getLong(o.key, (Long) o.defaultValue);
                }
                if (o.type == configTypeFloat) {
                    o.value = preferences.getFloat(o.key, (Float) o.defaultValue);
                }
                if (o.type == configTypeString) {
                    o.value = preferences.getString(o.key, (String) o.defaultValue);
                }
                if (o.type == configTypeSetInt) {
                    Set<String> ss = preferences.getStringSet(o.key, new HashSet<>());
                    HashSet<Integer> si = new HashSet<>();
                    for (String s : ss) {
                        si.add(Integer.parseInt(s));
                    }
                    o.value = si;
                }
                if (o.type == configTypeMapIntInt) {
                    String cv = preferences.getString(o.key, "");
                    if (cv.length() == 0) {
                        o.value = new HashMap<Integer, Integer>();
                    } else {
                        try {
                            byte[] data = Base64.decode(cv, Base64.DEFAULT);
                            ObjectInputStream ois = new ObjectInputStream(
                                    new ByteArrayInputStream(data));
                            o.value = (HashMap<Integer, Integer>) ois.readObject();
                            if (o.value == null) {
                                o.value = new HashMap<Integer, Integer>();
                            }
                            ois.close();
                        } catch (Exception e) {
                            o.value = new HashMap<Integer, Integer>();
                        }
                    }
                }
            }
            VKToken = VKAccessToken.Companion.restore(vkKeyValueStorage);
            configLoaded = true;
        }
    }

    public static void setVKToken(VKAccessToken vkAccessToken){
        vkAccessToken.save(vkKeyValueStorage);
    }
}
