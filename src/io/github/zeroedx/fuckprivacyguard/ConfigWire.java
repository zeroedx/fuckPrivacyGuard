package io.github.zeroedx.fuckprivacyguard;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 配置的序列化协议（与 {@link Config} 共用，独立成类以便离线测试）。
 *
 * 线上格式仍是老版本用过的 `k=v|k=v`：hook 侧读到的历史配置、以及升级前
 * 写入 Settings.Global 的旧值都能继续解析，协议不破坏。区别只在于承载方式——
 * 现在写进 LSPosed 的 RemotePreferences（无 root），不再需要 `su` 调 `settings put`。
 *
 * 自定义规则 {@link Config#K_CUSTOM} 里可能含 `|`、`=`，按老约定做 URL 编码。
 */
final class ConfigWire {

    private ConfigWire() {
    }

    /** 把一组开关编码成一行 `k=v|k=v`；空值规则跳过，结果为空时返回 null */
    static String encode(Map<String, ?> values) {
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, ?> e : values.entrySet()) {
            Object raw = e.getValue();
            if (raw == null) continue;
            String val;
            if (raw instanceof String) {
                val = (String) raw;
            } else if (raw instanceof Boolean) {
                val = String.valueOf(raw);
            } else {
                continue;
            }
            if (Config.K_CUSTOM.equals(e.getKey())) {
                if (val.isEmpty()) continue;
                val = urlEncode(val);
            }
            if (sb.length() > 0) sb.append('|');
            sb.append(e.getKey()).append('=').append(val);
        }
        return sb.length() == 0 ? null : sb.toString();
    }

    /** 解析一行 `k=v|k=v`；损坏的片段直接跳过，不抛异常 */
    static Map<String, String> decode(String wire) {
        Map<String, String> out = new LinkedHashMap<>();
        if (wire == null || wire.isEmpty()) return out;
        for (String kv : wire.split("\\|")) {
            int i = kv.indexOf('=');
            if (i <= 0) continue;
            String k = kv.substring(0, i).trim();
            String v = kv.substring(i + 1).trim();
            out.put(k, Config.K_CUSTOM.equals(k) ? urlDecode(v) : v);
        }
        return out;
    }

    static String urlEncode(String s) {
        try {
            return java.net.URLEncoder.encode(s, "UTF-8");
        } catch (Throwable t) {
            return s;
        }
    }

    static String urlDecode(String s) {
        try {
            return java.net.URLDecoder.decode(s, "UTF-8");
        } catch (Throwable t) {
            return s;
        }
    }
}
