package io.github.zeroedx.fuckprivacyguard;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import android.content.SharedPreferences;

/**
 * 离线测试用的内存 SharedPreferences，模拟 LSPosed RemotePreferences 通道与本地偏好。
 *
 * 只实现 Config 用到的方法，行为与 RemotePreferences 一致：commit/apply 后 getAll /
 * getString 立即返回新值（测试用同步实现，便于断言）。
 */
final class MemoryPrefs implements SharedPreferences {

    private final Map<String, Object> map = new HashMap<>();

    @Override
    public synchronized Map<String, ?> getAll() {
        return new HashMap<>(map);
    }

    @Override
    public synchronized String getString(String key, String defValue) {
        Object v = map.get(key);
        return v instanceof String ? (String) v : defValue;
    }

    @Override
    public synchronized Set<String> getStringSet(String key, Set<String> defValues) {
        Object v = map.get(key);
        @SuppressWarnings("unchecked")
        Set<String> s = v instanceof Set ? (Set<String>) v : defValues;
        return s;
    }

    @Override
    public synchronized int getInt(String key, int defValue) {
        Object v = map.get(key);
        return v instanceof Integer ? (Integer) v : defValue;
    }

    @Override
    public synchronized long getLong(String key, long defValue) {
        Object v = map.get(key);
        return v instanceof Long ? (Long) v : defValue;
    }

    @Override
    public synchronized float getFloat(String key, float defValue) {
        Object v = map.get(key);
        return v instanceof Float ? (Float) v : defValue;
    }

    @Override
    public synchronized boolean getBoolean(String key, boolean defValue) {
        Object v = map.get(key);
        return v instanceof Boolean ? (Boolean) v : defValue;
    }

    @Override
    public synchronized boolean contains(String key) {
        return map.containsKey(key);
    }

    @Override
    public Editor edit() {
        return new MemEditor();
    }

    private final class MemEditor implements Editor {
        private final Map<String, Object> put = new HashMap<>();
        private final Set<String> del = new HashSet<>();
        private boolean clear;

        @Override
        public Editor putString(String key, String value) {
            put.put(key, value);
            return this;
        }

        @Override
        public Editor putStringSet(String key, Set<String> values) {
            put.put(key, values);
            return this;
        }

        @Override
        public Editor putInt(String key, int value) {
            put.put(key, value);
            return this;
        }

        @Override
        public Editor putLong(String key, long value) {
            put.put(key, value);
            return this;
        }

        @Override
        public Editor putFloat(String key, float value) {
            put.put(key, value);
            return this;
        }

        @Override
        public Editor putBoolean(String key, boolean value) {
            put.put(key, value);
            return this;
        }

        @Override
        public Editor remove(String key) {
            del.add(key);
            return this;
        }

        @Override
        public Editor clear() {
            clear = true;
            return this;
        }

        @Override
        public boolean commit() {
            apply();
            return true;
        }

        @Override
        public void apply() {
            synchronized (MemoryPrefs.this) {
                if (clear) map.clear();
                for (String k : del) map.remove(k);
                map.putAll(put);
            }
        }
    }
}
