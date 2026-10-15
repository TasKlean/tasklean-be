package com.tasklean.api.domain.device;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class UserAgentParserTest {

    @Test
    void parse_chromeOnWindows_desktop() {
        DeviceInfo info = UserAgentParser.parse(
                "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0 Safari/537.36");

        assertThat(info.browser()).isEqualTo("Chrome");
        assertThat(info.os()).isEqualTo("Windows");
        assertThat(info.type()).isEqualTo("desktop");
        assertThat(info.displayName()).isEqualTo("Chrome on Windows");
    }

    @Test
    void parse_safariOnIphone_mobile() {
        DeviceInfo info = UserAgentParser.parse(
                "Mozilla/5.0 (iPhone; CPU iPhone OS 17_0 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.0 Mobile/15E148 Safari/604.1");

        assertThat(info.browser()).isEqualTo("Safari");
        assertThat(info.os()).isEqualTo("iOS");
        assertThat(info.type()).isEqualTo("mobile");
        assertThat(info.displayName()).isEqualTo("Safari on iOS");
    }

    @Test
    void parse_edgeOnWindows_detectedBeforeChrome() {
        DeviceInfo info = UserAgentParser.parse(
                "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 Chrome/120.0 Safari/537.36 Edg/120.0");

        assertThat(info.browser()).isEqualTo("Edge");
    }

    @Test
    void parse_firefoxOnLinux() {
        DeviceInfo info = UserAgentParser.parse(
                "Mozilla/5.0 (X11; Linux x86_64; rv:121.0) Gecko/20100101 Firefox/121.0");

        assertThat(info.browser()).isEqualTo("Firefox");
        assertThat(info.os()).isEqualTo("Linux");
    }

    @Test
    void parse_null_returnsUnknownDevice() {
        DeviceInfo info = UserAgentParser.parse(null);

        assertThat(info.displayName()).isEqualTo("Unknown device");
        assertThat(info.type()).isEqualTo("unknown");
    }
}
