package org.dromara.common.core.oss;

import org.dromara.common.core.exception.ServiceException;

import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

/**
 * OSS 媒体大小、扩展名和 MIME 的共享技术规则。
 *
 * <p>文件签名及容器结构探测应由读取文件内容的上层策略负责。</p>
 */
public final class TechnicalMediaMetadataPolicy {

    public static final long MAX_IMAGE_BYTES = 50L * 1024 * 1024;
    public static final long MAX_VIDEO_BYTES = 2L * 1024 * 1024 * 1024;

    private static final List<MediaFormat> FORMATS = List.of(
        new MediaFormat(TechnicalMediaType.IMAGE, Set.of("jpg", "jpeg"), "image/jpeg",
            Set.of("image/jpeg", "image/pjpeg")),
        new MediaFormat(TechnicalMediaType.IMAGE, Set.of("png"), "image/png",
            Set.of("image/png", "image/x-png")),
        new MediaFormat(TechnicalMediaType.IMAGE, Set.of("gif"), "image/gif", Set.of("image/gif")),
        new MediaFormat(TechnicalMediaType.IMAGE, Set.of("webp"), "image/webp", Set.of("image/webp")),
        new MediaFormat(TechnicalMediaType.VIDEO, Set.of("mp4"), "video/mp4",
            Set.of("video/mp4", "application/mp4")),
        new MediaFormat(TechnicalMediaType.VIDEO, Set.of("mov"), "video/quicktime", Set.of("video/quicktime")),
        new MediaFormat(TechnicalMediaType.VIDEO, Set.of("avi"), "video/x-msvideo",
            Set.of("video/x-msvideo", "video/avi", "video/msvideo")),
        new MediaFormat(TechnicalMediaType.VIDEO, Set.of("webm"), "video/webm", Set.of("video/webm")),
        new MediaFormat(TechnicalMediaType.VIDEO, Set.of("mkv"), "video/x-matroska",
            Set.of("video/x-matroska", "video/matroska")),
        new MediaFormat(TechnicalMediaType.VIDEO, Set.of("wmv"), "video/x-ms-wmv",
            Set.of("video/x-ms-wmv", "video/x-ms-asf")),
        new MediaFormat(TechnicalMediaType.VIDEO, Set.of("flv"), "video/x-flv",
            Set.of("video/x-flv", "video/flv"))
    );

    private TechnicalMediaMetadataPolicy() {
    }

    /**
     * 校验媒体大小上限。
     */
    public static void validateSize(TechnicalMediaType type, long size) {
        if (size < 0) {
            throw new ServiceException("媒体文件大小不能为负数");
        }
        if (type == TechnicalMediaType.IMAGE && size > MAX_IMAGE_BYTES) {
            throw new ServiceException("单张图片不能超过50MB");
        }
        if (type == TechnicalMediaType.VIDEO && size > MAX_VIDEO_BYTES) {
            throw new ServiceException("单个视频不能超过2GB");
        }
    }

    /**
     * 从完整原始文件名提取标准后缀。文件名必须包含最后一个非空扩展名。
     */
    public static Optional<String> fileSuffixFromOriginalFilename(String originalFilename) {
        if (originalFilename == null) {
            return Optional.empty();
        }
        String value = originalFilename.trim();
        int lastSeparator = Math.max(value.lastIndexOf('/'), value.lastIndexOf('\\'));
        int lastDot = value.lastIndexOf('.');
        if (lastDot <= lastSeparator || lastDot == value.length() - 1) {
            return Optional.empty();
        }
        String extension = normalizeExtensionValue(value.substring(lastDot + 1));
        return extension.isEmpty() ? Optional.empty() : Optional.of('.' + extension);
    }

    /**
     * 按技术类型、已存储后缀和已声明 MIME 返回标准 MIME。
     */
    public static Optional<String> canonicalContentTypeForStoredSuffix(TechnicalMediaType type, String storedSuffix,
                                                                        String contentType) {
        String extension = normalizeStoredSuffix(storedSuffix);
        String normalizedContentType = normalizeContentType(contentType);
        return FORMATS.stream()
            .filter(format -> format.type() == type)
            .filter(format -> format.extensions().contains(extension))
            .filter(format -> format.mimeAliases().contains(normalizedContentType))
            .map(MediaFormat::canonicalMime)
            .findFirst();
    }

    private static String normalizeStoredSuffix(String storedSuffix) {
        if (storedSuffix == null) {
            return "";
        }
        String value = storedSuffix.trim();
        String extension = value.startsWith(".") ? value.substring(1) : value;
        if (extension.indexOf('.') >= 0 || extension.indexOf('/') >= 0 || extension.indexOf('\\') >= 0) {
            return "";
        }
        return normalizeExtensionValue(extension);
    }

    private static String normalizeExtensionValue(String extension) {
        return extension.trim().toLowerCase(Locale.ROOT);
    }

    private static String normalizeContentType(String contentType) {
        if (contentType == null) {
            return "";
        }
        int parameters = contentType.indexOf(';');
        String value = parameters < 0 ? contentType : contentType.substring(0, parameters);
        return value.trim().toLowerCase(Locale.ROOT);
    }

    private record MediaFormat(TechnicalMediaType type, Set<String> extensions, String canonicalMime,
                               Set<String> mimeAliases) {
    }
}
