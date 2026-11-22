package com.aiinterview.platform.security.utils;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The only source of "which device is this" in the session list — there is no geo-IP, so the label
 * is browser + OS. Order inside the parser matters: Edge/Opera also contain "Chrome/", and Chrome
 * also contains "Safari/".
 *
 * <p>Mỗi test chia 3 khối comment: <b>Arrange</b> → <b>Act</b> → <b>Assert</b>.</p>
 */
class UserAgentParserTest {

    private static final String WINDOWS_CHROME =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) "
                    + "Chrome/120.0.0.0 Safari/537.36";
    private static final String MAC_SAFARI =
            "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/605.1.15 (KHTML, like Gecko) "
                    + "Version/17.0 Safari/605.1.15";
    private static final String IPHONE_SAFARI =
            "Mozilla/5.0 (iPhone; CPU iPhone OS 17_0 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) "
                    + "Version/17.0 Mobile/15E148 Safari/604.1";
    private static final String ANDROID_PHONE =
            "Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 (KHTML, like Gecko) "
                    + "Chrome/120.0.0.0 Mobile Safari/537.36";
    private static final String ANDROID_TABLET =
            "Mozilla/5.0 (Linux; Android 14; SM-X700) AppleWebKit/537.36 (KHTML, like Gecko) "
                    + "Chrome/120.0.0.0 Safari/537.36";
    private static final String WINDOWS_EDGE =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) "
                    + "Chrome/120.0.0.0 Safari/537.36 Edg/120.0.2210.91";
    private static final String LINUX_FIREFOX =
            "Mozilla/5.0 (X11; Linux x86_64; rv:121.0) Gecko/20100101 Firefox/121.0";
    private static final String WINDOWS_OPERA =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) "
                    + "Chrome/119.0.0.0 Safari/537.36 OPR/105.0.0.0";

    @Test
    @DisplayName("Chrome tren Windows")
    void describe_chromeOnWindows() {
        // Arrange: UA Chrome/120 tren Windows (UA pho bien nhat)
        // Act & Assert: nhan = trinh duyet + "tren" + he dieu hanh
        assertThat(UserAgentParser.describe(WINDOWS_CHROME)).isEqualTo("Chrome trên Windows");
    }

    @Test
    @DisplayName("Edge phai duoc nhan dien truoc Chrome vi UA cung chua Chrome/")
    void describe_edgeWinsOverChrome() {
        // Arrange: UA Edge chua ca "Chrome/" va "Edg/" — thu tu if quyet dinh ket qua
        // Act & Assert: phai ra Edge, neu check Chrome truoc se ra nhan sai
        assertThat(UserAgentParser.describe(WINDOWS_EDGE)).isEqualTo("Edge trên Windows");
    }

    @Test
    @DisplayName("Opera phai duoc nhan dien truoc Chrome vi UA cung chua Chrome/")
    void describe_operaWinsOverChrome() {
        // Arrange: UA Opera chua ca "Chrome/" va "OPR/"
        // Act & Assert: "OPR/" phai duoc kiem tra truoc "Chrome/"
        assertThat(UserAgentParser.describe(WINDOWS_OPERA)).isEqualTo("Opera trên Windows");
    }

    @Test
    @DisplayName("Safari tren macOS, khong bi nham thanh Chrome")
    void describe_safariOnMac() {
        // Arrange: UA Safari that — co "Safari/" nhung KHONG co "Chrome/"
        // Act & Assert: nhan Safari, va he dieu hanh la macOS chu khong phai "Mac OS X"
        assertThat(UserAgentParser.describe(MAC_SAFARI)).isEqualTo("Safari trên macOS");
    }

    @Test
    @DisplayName("Safari tren iPhone")
    void describe_safariOnIphone() {
        // Arrange: UA iOS (dau hieu "iPhone")
        // Act & Assert: he dieu hanh duoc doi thanh iPhone, khong phai macOS
        assertThat(UserAgentParser.describe(IPHONE_SAFARI)).isEqualTo("Safari trên iPhone");
    }

    @Test
    @DisplayName("Android co chu Mobile la dien thoai, khong co la may tinh bang")
    void describe_androidPhoneVersusTablet() {
        // Arrange: 2 UA Android cung trinh duyet, chi khac token "Mobile"
        // Act & Assert: co "Mobile" => dien thoai
        assertThat(UserAgentParser.describe(ANDROID_PHONE)).isEqualTo("Chrome trên Android (điện thoại)");
        // Assert: khong co "Mobile" => may tinh bang
        assertThat(UserAgentParser.describe(ANDROID_TABLET)).isEqualTo("Chrome trên Android (máy tính bảng)");
    }

    @Test
    @DisplayName("Firefox tren Linux")
    void describe_firefoxOnLinux() {
        // Arrange: UA Firefox (token "Firefox/", khong co Chrome/Safari)
        // Act & Assert: nhan Firefox + Linux
        assertThat(UserAgentParser.describe(LINUX_FIREFOX)).isEqualTo("Firefox trên Linux");
    }

    @Test
    @DisplayName("Khong ro ca trinh duyet lan he dieu hanh thi gop thanh mot nhan")
    void describe_unknownBrowserAndOs_collapsesToSingleLabel() {
        // Arrange: UA cua CLI (curl) — khong khop trinh duyet / he dieu hanh nao
        // Act & Assert: gop thanh 1 nhan duy nhat, khong tra "khong ro tren khong ro"
        assertThat(UserAgentParser.describe("curl/8.4.0")).isEqualTo("Thiết bị không xác định");
    }

    @Test
    @DisplayName("User-Agent null hoac rong khong lam vo luong dang nhap")
    void describe_nullOrBlankUserAgent() {
        // Arrange: client khong gui User-Agent
        // Act & Assert: null va "" deu phai ra nhan an toan (khong nem NPE luc tao session)
        assertThat(UserAgentParser.describe(null)).isEqualTo("Thiết bị không xác định");
        assertThat(UserAgentParser.describe("")).isEqualTo("Thiết bị không xác định");
    }

    @ParameterizedTest(name = "{0} => {1}")
    @CsvSource({
            "Mozilla/5.0 (iPad; CPU OS 17_0 like Mac OS X) Version/17.0 Safari/604.1, Safari trên iPad",
            "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) Firefox/121.0, Firefox trên macOS",
            "Mozilla/5.0 (Linux; Android 14) Edg/120.0, Edge trên Android (máy tính bảng)"
    })
    @DisplayName("Cac to hop trinh duyet/he dieu hanh con lai")
    void describe_remainingCombinations(String userAgent, String expected) {
        // Arrange: (userAgent, expected) lay tu CsvSource — moi dong la 1 to hop
        // Act
        String label = UserAgentParser.describe(userAgent);

        // Assert: iPad (khong co "Mobile") -> may tinh bang; Firefox + Mac; Edge + Android
        assertThat(label).isEqualTo(expected);
    }
}
