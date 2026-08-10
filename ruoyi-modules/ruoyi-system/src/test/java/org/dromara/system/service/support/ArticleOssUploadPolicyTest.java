package org.dromara.system.service.support;

import org.dromara.common.core.exception.ServiceException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.web.multipart.MultipartFile;

import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@Tag("dev")
class ArticleOssUploadPolicyTest {

    private final ArticleOssUploadPolicy policy = new ArticleOssUploadPolicy();

    @ParameterizedTest
    @MethodSource("configuredArticleFiles")
    void acceptsConfiguredArticleFiles(String bizType, String name, String contentType, long size) {
        MultipartFile file = mockFile(name, contentType, size);

        assertThatCode(() -> policy.validate(file, bizType)).doesNotThrowAnyException();
    }

    @Test
    void rejectsImageAboveFiftyMib() {
        MultipartFile file = mockFile("a.png", "image/png", 50L * 1024 * 1024 + 1);

        assertThatThrownBy(() -> policy.validate(file, "article_attachment"))
            .isInstanceOf(ServiceException.class)
            .hasMessage("单张文章图片不能超过50MB");
    }

    @Test
    void rejectsVideoAboveTwoGib() {
        MultipartFile file = mockFile("a.mp4", "video/mp4", 2L * 1024 * 1024 * 1024 + 1);

        assertThatThrownBy(() -> policy.validate(file, "article_video"))
            .isInstanceOf(ServiceException.class)
            .hasMessage("单个文章视频不能超过2GB");
    }

    @Test
    void rejectsAnExtensionWhoseMimeTypeDoesNotMatch() {
        MultipartFile file = mockFile("a.png", "image/jpeg", 12L);

        assertThatThrownBy(() -> policy.validate(file, "article_attachment"))
            .isInstanceOf(ServiceException.class)
            .hasMessage("文章图片格式不受支持");
    }

    @Test
    void leavesOrdinaryUploadsUnchanged() {
        MultipartFile file = mockFile("archive.exe", "application/octet-stream", Long.MAX_VALUE);

        assertThatCode(() -> policy.validate(file, null)).doesNotThrowAnyException();
    }

    private static Stream<Arguments> configuredArticleFiles() {
        long imageLimit = 50L * 1024 * 1024;
        long videoLimit = 2L * 1024 * 1024 * 1024;
        return Stream.of(
            Arguments.of("article_attachment", "a.jpg", "image/jpeg", imageLimit),
            Arguments.of("article_attachment", "a.jpeg", "image/jpeg", imageLimit),
            Arguments.of("article_attachment", "a.png", "image/png", imageLimit),
            Arguments.of("article_attachment", "a.webp", "image/webp", imageLimit),
            Arguments.of("article_attachment", "a.gif", "image/gif", imageLimit),
            Arguments.of("article_video", "a.mp4", "video/mp4", videoLimit),
            Arguments.of("article_video", "a.avi", "video/x-msvideo", videoLimit),
            Arguments.of("article_video", "a.mov", "video/quicktime", videoLimit),
            Arguments.of("article_video", "a.webm", "video/webm", videoLimit),
            Arguments.of("article_video", "a.mkv", "video/x-matroska", videoLimit),
            Arguments.of("article_video", "a.wmv", "video/x-ms-wmv", videoLimit),
            Arguments.of("article_video", "a.flv", "video/x-flv", videoLimit)
        );
    }

    private static MultipartFile mockFile(String name, String contentType, long size) {
        MultipartFile file = mock(MultipartFile.class);
        when(file.getOriginalFilename()).thenReturn(name);
        when(file.getContentType()).thenReturn(contentType);
        when(file.getSize()).thenReturn(size);
        when(file.isEmpty()).thenReturn(false);
        return file;
    }
}
