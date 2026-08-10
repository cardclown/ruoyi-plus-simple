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
    void removesScriptsEventHandlersAndDangerousProtocols() {
        String html = "<p onclick=\"alert(1)\">正文</p><script>alert(1)</script>"
            + "<img src=\"javascript:alert(2)\" onerror=\"alert(3)\" />"
            + "<a href=\"javascript:alert(4)\">链接</a>";

        String sanitized = sanitizer.sanitize(html);

        assertThat(sanitized)
            .doesNotContain("<script")
            .doesNotContain("onclick")
            .doesNotContain("onerror")
            .doesNotContain("javascript:");
    }

    @Test
    void keepsFormattingCssButRemovesLayoutHijackingCss() {
        String html = "<p style=\"position: fixed; top: 0; color: red; text-align: center;\">正文</p>";

        String sanitized = sanitizer.sanitize(html);

        assertThat(sanitized)
            .contains("color: red")
            .contains("text-align: center")
            .doesNotContain("position")
            .doesNotContain("top:");
    }
}
