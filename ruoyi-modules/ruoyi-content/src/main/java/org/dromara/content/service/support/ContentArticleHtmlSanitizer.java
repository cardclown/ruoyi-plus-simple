package org.dromara.content.service.support;

import cn.hutool.http.HTMLFilter;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 文章富文本白名单过滤器，保留 Quill 常用格式以及浏览器原生图片、视频。
 * <p>
 * 文章写接口会绕过全局“删除所有 HTML”过滤器，因此这里是正文持久化前的安全边界：
 * 只允许明确列出的标签、属性、URL 协议和展示样式，脚本标签、事件属性及脚本协议仍会被删除。
 * </p>
 */
@Component
public class ContentArticleHtmlSanitizer {

    /** 只定位使用双引号包裹的 {@code style} 属性，供后续逐条过滤 CSS 声明。 */
    private static final Pattern STYLE_ATTRIBUTE = Pattern.compile("\\sstyle=\"([^\"]*)\"", Pattern.CASE_INSENSITIVE);
    /** 在白名单过滤前定位并暂存 Quill 列表使用的 {@code data-list} 属性。 */
    private static final Pattern QUILL_LIST_ATTRIBUTE = Pattern.compile(
        "(?i)\\sdata-list=([\"'])(bullet|ordered|checked|unchecked)\\1"
    );
    /** 在白名单过滤前暂存图片和视频的对齐属性，兼容 HTMLFilter 不识别连字符属性名的限制。 */
    private static final Pattern MEDIA_ALIGN_ATTRIBUTE = Pattern.compile(
        "(?i)\\sdata-align=([\"'])(left|center|right)\\1"
    );
    /** 拒绝 CSS 表达式、任何 {@code url(...)} 引用、导入、脚本协议以及高风险指令和浏览器扩展。 */
    private static final Pattern DANGEROUS_STYLE = Pattern.compile(
        "(?i)(expression\\s*\\(|url\\s*\\(|@import|javascript\\s*:|behavior\\s*:|-moz-binding|!important)"
    );
    /** 允许保留的纯展示 CSS 属性集合。 */
    private static final Set<String> ALLOWED_STYLE_PROPERTIES = Set.of(
        "color", "background-color", "text-align", "text-indent", "font-size", "font-family",
        "font-weight", "font-style", "text-decoration", "line-height", "letter-spacing",
        "white-space", "margin-left", "padding-left", "display", "margin"
    );
    /** 媒体居中需要 display，但仅允许不会脱离文档流的常用展示值。 */
    private static final Set<String> ALLOWED_DISPLAY_VALUES = Set.of("block", "inline", "inline-block");
    /** margin 仅允许由 auto 或零值组成，满足媒体居中且避免负边距等布局劫持。 */
    private static final Pattern SAFE_MARGIN_VALUE = Pattern.compile(
        "(?i)^(?:(?:auto|0(?:px|em|rem|%)?)(?:\\s+|$)){1,4}$"
    );

    /**
     * 过滤文章富文本：先兼容暂存 Quill 属性，再按标签、属性和协议白名单过滤，最后过滤样式并恢复属性。
     *
     * @param html 待过滤的富文本，可为空
     * @return 保留安全白名单内容的富文本；空值和空白文本原样返回
     */
    public String sanitize(String html) {
        if (html == null || html.isBlank()) {
            return html;
        }
        // 临时改用白名单内属性名，避免 HTMLFilter 丢弃 data-*；这不是业务字段改名。
        String normalized = QUILL_LIST_ATTRIBUTE.matcher(html).replaceAll(" datalist=\"$2\"");
        normalized = MEDIA_ALIGN_ATTRIBUTE.matcher(normalized).replaceAll(" dataalign=\"$2\"");
        String filtered = new HTMLFilter(configuration()).filter(normalized);
        // 过滤完成后恢复 Quill 和媒体属性语义；前述转换仅用于穿过 HTMLFilter。
        return sanitizeStyles(filtered)
            .replaceAll("(?i)\\sdatalist=\"", " data-list=\"")
            .replaceAll("(?i)\\sdataalign=\"", " data-align=\"");
    }

    /**
     * 构造 Hutool {@link HTMLFilter} 使用的标签、属性、协议和实体白名单。
     *
     * @return HTMLFilter 配置项
     */
    private Map<String, Object> configuration() {
        HashMap<String, List<String>> allowed = new HashMap<>();
        List<String> commonAttributes = List.of("class", "style");
        for (String tag : List.of(
            "p", "h1", "h2", "h3", "h4", "h5", "h6", "blockquote", "pre", "code",
            "ol", "ul", "span", "strong", "b", "em", "i", "u", "s", "sub", "sup"
        )) {
            allowed.put(tag, commonAttributes);
        }
        allowed.put("br", List.of());
        allowed.put("li", attributes(commonAttributes, "datalist"));
        allowed.put("a", attributes(commonAttributes, "href", "target", "rel", "title"));
        allowed.put("img", attributes(
            commonAttributes, "src", "width", "height", "alt", "title", "dataalign"));
        // 仅开放浏览器原生媒体标签；iframe/object/embed 继续禁止，避免引入任意第三方可执行页面。
        allowed.put("video", attributes(
            commonAttributes, "src", "poster", "width", "height", "controls", "preload",
            "autoplay", "muted", "loop", "playsinline", "dataalign"));
        allowed.put("source", List.of("src", "type"));

        Map<String, Object> config = new HashMap<>();
        config.put("vAllowed", allowed);
        config.put("vSelfClosingTags", new String[]{"br", "img", "source"});
        config.put("vNeedClosingTags", new String[]{
            "p", "h1", "h2", "h3", "h4", "h5", "h6", "blockquote", "pre", "code",
            "ol", "ul", "li", "span", "strong", "b", "em", "i", "u", "s", "sub", "sup", "a", "video"
        });
        config.put("vDisallowed", new String[]{"script", "iframe", "object", "embed", "form"});
        config.put("vAllowedProtocols", new String[]{"http", "https", "mailto"});
        config.put("vProtocolAtts", new String[]{"src", "href", "poster"});
        config.put("vRemoveBlanks", new String[]{});
        config.put("vAllowedEntities", new String[]{"amp", "gt", "lt", "quot", "apos", "nbsp"});
        config.put("stripComment", true);
        config.put("encodeQuotes", true);
        config.put("alwaysMakeTags", true);
        return config;
    }

    private List<String> attributes(List<String> base, String... additions) {
        List<String> result = new ArrayList<>(base);
        result.addAll(List.of(additions));
        return result;
    }

    /**
     * 逐条检查样式声明，仅保留属性在展示白名单内、值非空且不含危险片段的 CSS。
     *
     * @param html 已经过 HTMLFilter 处理的富文本
     * @return 样式声明进一步收敛后的富文本
     */
    private String sanitizeStyles(String html) {
        Matcher matcher = STYLE_ATTRIBUTE.matcher(html);
        StringBuffer output = new StringBuffer();
        while (matcher.find()) {
            StringBuilder safeStyle = new StringBuilder();
            for (String declaration : matcher.group(1).split(";")) {
                int separator = declaration.indexOf(':');
                if (separator < 1) {
                    continue;
                }
                String property = declaration.substring(0, separator).trim().toLowerCase();
                String value = declaration.substring(separator + 1).trim();
                if (isAllowedStyle(property, value)) {
                    safeStyle.append(property).append(": ").append(value).append(';');
                }
            }
            String replacement = safeStyle.isEmpty() ? "" : " style=\"" + safeStyle + "\"";
            matcher.appendReplacement(output, Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(output);
        return output.toString();
    }

    /**
     * 判断单条 CSS 声明是否可持久化。媒体布局属性采用比普通展示属性更严格的值白名单，
     * 以保留居中效果，同时拒绝脱离文档流或使用负边距干扰页面布局。
     *
     * @param property 已规范为小写的 CSS 属性名
     * @param value CSS 属性值
     * @return 属性和值均满足文章展示白名单时返回 {@code true}
     */
    private boolean isAllowedStyle(String property, String value) {
        if (!ALLOWED_STYLE_PROPERTIES.contains(property)
            || value.isEmpty()
            || DANGEROUS_STYLE.matcher(value).find()) {
            return false;
        }
        if ("display".equals(property)) {
            return ALLOWED_DISPLAY_VALUES.contains(value.toLowerCase());
        }
        if ("margin".equals(property)) {
            return SAFE_MARGIN_VALUE.matcher(value).matches();
        }
        return true;
    }
}
