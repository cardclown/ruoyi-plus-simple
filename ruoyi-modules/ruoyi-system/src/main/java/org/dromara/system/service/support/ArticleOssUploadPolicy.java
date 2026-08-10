package org.dromara.system.service.support;

import org.dromara.common.core.exception.ServiceException;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Validation policy for article image and video uploads.
 */
@Component
public class ArticleOssUploadPolicy {

    public static final String ARTICLE_ATTACHMENT = "article_attachment";
    public static final String ARTICLE_VIDEO = "article_video";
    public static final long MAX_IMAGE_BYTES = 50L * 1024 * 1024;
    public static final long MAX_VIDEO_BYTES = 2L * 1024 * 1024 * 1024;
    public static final Set<String> IMAGE_EXTENSIONS = Set.of("jpg", "jpeg", "png", "webp", "gif");
    public static final Set<String> VIDEO_EXTENSIONS = Set.of("mp4", "avi", "mov", "webm", "mkv", "wmv", "flv");

    private static final Map<String, Set<String>> IMAGE_MIME_TYPES = Map.of(
        "jpg", Set.of("image/jpeg"),
        "jpeg", Set.of("image/jpeg"),
        "png", Set.of("image/png"),
        "webp", Set.of("image/webp"),
        "gif", Set.of("image/gif")
    );
    private static final Map<String, Set<String>> VIDEO_MIME_TYPES = Map.of(
        "mp4", Set.of("video/mp4"),
        "avi", Set.of("video/x-msvideo"),
        "mov", Set.of("video/quicktime"),
        "webm", Set.of("video/webm"),
        "mkv", Set.of("video/x-matroska"),
        "wmv", Set.of("video/x-ms-wmv"),
        "flv", Set.of("video/x-flv")
    );

    public void validate(MultipartFile file, String bizType) {
        if (!isArticleBizType(bizType)) {
            return;
        }
        String extension = extension(file.getOriginalFilename());
        String contentType = normalize(file.getContentType());
        if (ARTICLE_ATTACHMENT.equals(bizType)) {
            validateFormat(extension, contentType, IMAGE_EXTENSIONS, IMAGE_MIME_TYPES, "文章图片格式不受支持");
            if (file.getSize() > MAX_IMAGE_BYTES) {
                throw new ServiceException("单张文章图片不能超过50MB");
            }
            return;
        }
        validateFormat(extension, contentType, VIDEO_EXTENSIONS, VIDEO_MIME_TYPES, "文章视频格式不受支持");
        if (file.getSize() > MAX_VIDEO_BYTES) {
            throw new ServiceException("单个文章视频不能超过2GB");
        }
    }

    public boolean isArticleBizType(String bizType) {
        return ARTICLE_ATTACHMENT.equals(bizType) || ARTICLE_VIDEO.equals(bizType);
    }

    private static void validateFormat(String extension, String contentType, Set<String> extensions,
                                       Map<String, Set<String>> mimeTypes, String message) {
        if (!extensions.contains(extension) || !mimeTypes.getOrDefault(extension, Set.of()).contains(contentType)) {
            throw new ServiceException(message);
        }
    }

    private static String extension(String filename) {
        if (filename == null) {
            return "";
        }
        int separator = filename.lastIndexOf('.');
        if (separator < 0 || separator == filename.length() - 1) {
            return "";
        }
        return normalize(filename.substring(separator + 1));
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }
}
