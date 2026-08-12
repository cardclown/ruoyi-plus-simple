package org.dromara.content.service.support;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("dev")
class ContentArticleHtmlSanitizerTest {

    private final ContentArticleHtmlSanitizer sanitizer = new ContentArticleHtmlSanitizer();

    @Test
    void preservesQuillFormattingAndImages() {
        String html = "<h2>标题</h2><p class=\"ql-align-center\"><span style=\"color: red;\">正文</span>"
            + "<img src=\"https://oss.example.com/a.png\" alt=\"图片\" /></p>"
            + "<ol><li data-list=\"bullet\">列表</li></ol>";

        String sanitized = sanitizer.sanitize(html);

        assertThat(sanitized)
            .contains("<h2>标题</h2>")
            .contains("class=\"ql-align-center\"")
            .contains("style=\"color: red;\"")
            .contains("src=\"https://oss.example.com/a.png\"")
            .contains("data-list=\"bullet\"");
    }

    @Test
    void preservesUploadedImageAndSafeCenterAlignment() {
        String html = "<p><img src=\"http://10.50.3.55:9000/ruoyi/a.png\" width=\"377\" "
            + "height=\"235.22099008116913\" style=\"display: block; margin: auto;\" "
            + "data-align=\"center\"></p>";

        String sanitized = sanitizer.sanitize(html);

        assertThat(sanitized)
            .contains("<img")
            .contains("src=\"http://10.50.3.55:9000/ruoyi/a.png\"")
            .contains("width=\"377\"")
            .contains("height=\"235.22099008116913\"")
            .contains("style=\"display: block;margin: auto;\"")
            .contains("data-align=\"center\"");
    }

    @Test
    void preservesNativeVideoAndSources() {
        String html = "<video src=\"https://oss.example.com/news.mp4\" "
            + "poster=\"https://oss.example.com/poster.jpg\" controls=\"controls\" "
            + "preload=\"metadata\" width=\"640\" data-align=\"center\">"
            + "<source src=\"https://oss.example.com/news.webm\" type=\"video/webm\">"
            + "您的浏览器不支持视频播放</video>";

        String sanitized = sanitizer.sanitize(html);

        assertThat(sanitized)
            .contains("<video")
            .contains("src=\"https://oss.example.com/news.mp4\"")
            .contains("poster=\"https://oss.example.com/poster.jpg\"")
            .contains("controls=\"controls\"")
            .contains("preload=\"metadata\"")
            .contains("data-align=\"center\"")
            .contains("<source src=\"https://oss.example.com/news.webm\" type=\"video/webm\" />")
            .contains("您的浏览器不支持视频播放</video>");
    }

    @Test
    void removesScriptsEventHandlersAndDangerousProtocols() {
        String html = "<p onclick=\"alert(1)\">正文</p><script>alert(1)</script>"
            + "<img src=\"javascript:alert(2)\" onerror=\"alert(3)\" />"
            + "<video src=\"javascript:alert(4)\" poster=\"javascript:alert(5)\" onload=\"alert(6)\">视频</video>"
            + "<source src=\"javascript:alert(7)\" type=\"video/mp4\">"
            + "<iframe src=\"https://evil.example.com\"></iframe>"
            + "<a href=\"javascript:alert(8)\">链接</a>";

        String sanitized = sanitizer.sanitize(html);

        assertThat(sanitized)
            .doesNotContain("<script")
            .doesNotContain("onclick")
            .doesNotContain("onerror")
            .doesNotContain("onload")
            .doesNotContain("<iframe")
            .doesNotContain("javascript:");
    }

    @Test
    void keepsFormattingCssButRemovesLayoutHijackingCss() {
        String html = "<p style=\"position: fixed; top: 0; margin: -999px; display: grid; "
            + "color: red; text-align: center;\">正文</p>";

        String sanitized = sanitizer.sanitize(html);

        assertThat(sanitized)
            .contains("color: red")
            .contains("text-align: center")
            .doesNotContain("position")
            .doesNotContain("top:")
            .doesNotContain("margin")
            .doesNotContain("display");
    }
}
