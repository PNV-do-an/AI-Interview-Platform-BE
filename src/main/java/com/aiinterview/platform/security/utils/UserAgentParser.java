package com.aiinterview.platform.security.utils;

/**
 * Suy ra nhãn thiết bị đọc được từ User-Agent — BE không có geo-IP nên chỉ mô tả được
 * trình duyệt + hệ điều hành, không bịa thành phố như mockup.
 */
public final class UserAgentParser {

    private UserAgentParser() {
    }

    public static String describe(String userAgent) {
        String ua = userAgent == null ? "" : userAgent;
        String browser = browser(ua);
        String os = os(ua);
        String label = switch (browser + "|" + os) {
            case "Trình duyệt khác|Hệ điều hành khác" -> "Thiết bị không xác định";
            default -> browser + " trên " + os;
        };
        return label.length() > 120 ? label.substring(0, 120) : label;
    }

    private static String browser(String ua) {
        if (ua.contains("Edg/") || ua.contains("Edge/")) return "Edge";
        if (ua.contains("OPR/") || ua.contains("Opera")) return "Opera";
        if (ua.contains("Firefox/")) return "Firefox";
        if (ua.contains("Chrome/") || ua.contains("Chromium/")) return "Chrome";
        if (ua.contains("Safari/")) return "Safari";
        return "Trình duyệt khác";
    }

    private static String os(String ua) {
        if (ua.contains("iPhone")) return "iPhone";
        if (ua.contains("iPad")) return "iPad";
        if (ua.contains("Android")) return ua.contains("Mobile") ? "Android (điện thoại)" : "Android (máy tính bảng)";
        if (ua.contains("Windows NT")) return "Windows";
        if (ua.contains("Mac OS X") || ua.contains("Macintosh")) return "macOS";
        if (ua.contains("Linux")) return "Linux";
        return "Hệ điều hành khác";
    }
}
