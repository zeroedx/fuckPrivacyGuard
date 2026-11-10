package io.github.vivibibi30.hyperpguard;

import android.util.Log;

final class Logs {

    private static final String TAG = "fuckPrivacyGuard";

    private Logs() {
    }

    static void i(String msg) {
        try {
            Log.i(TAG, msg);
        } catch (Throwable ignored) {
        }
    }
}
