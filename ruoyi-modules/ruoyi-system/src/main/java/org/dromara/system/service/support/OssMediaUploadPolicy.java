package org.dromara.system.service.support;

import org.dromara.common.core.exception.ServiceException;
import org.dromara.system.domain.enums.OssFileType;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Bounded signature validation for technically classified media uploads.
 */
@Component
public class OssMediaUploadPolicy {

    public static final int MAX_PROBE_BYTES = 4 * 1024;
    public static final long MAX_IMAGE_BYTES = 50L * 1024 * 1024;
    public static final long MAX_VIDEO_BYTES = 2L * 1024 * 1024 * 1024;

    private static final Set<String> MP4_BRANDS = Set.of(
        "isom", "iso2", "iso5", "iso6", "mp41", "mp42", "avc1", "M4V ", "MSNV", "3gp4", "3gp5"
    );

    private static final List<MediaFormat> FORMATS = List.of(
        new MediaFormat(OssFileType.IMAGE, Set.of("jpg", "jpeg"), "image/jpeg",
            Set.of("image/jpeg", "image/pjpeg"), OssMediaUploadPolicy::isJpeg),
        new MediaFormat(OssFileType.IMAGE, Set.of("png"), "image/png",
            Set.of("image/png", "image/x-png"), OssMediaUploadPolicy::isPng),
        new MediaFormat(OssFileType.IMAGE, Set.of("gif"), "image/gif",
            Set.of("image/gif"), OssMediaUploadPolicy::isGif),
        new MediaFormat(OssFileType.IMAGE, Set.of("webp"), "image/webp",
            Set.of("image/webp"), bytes -> isRiff(bytes, "WEBP")),
        new MediaFormat(OssFileType.VIDEO, Set.of("mp4"), "video/mp4",
            Set.of("video/mp4", "application/mp4"), OssMediaUploadPolicy::isMp4),
        new MediaFormat(OssFileType.VIDEO, Set.of("mov"), "video/quicktime",
            Set.of("video/quicktime"), OssMediaUploadPolicy::isMov),
        new MediaFormat(OssFileType.VIDEO, Set.of("avi"), "video/x-msvideo",
            Set.of("video/x-msvideo", "video/avi", "video/msvideo"), bytes -> isRiff(bytes, "AVI ")),
        new MediaFormat(OssFileType.VIDEO, Set.of("webm"), "video/webm",
            Set.of("video/webm"), bytes -> isEbml(bytes, "webm")),
        new MediaFormat(OssFileType.VIDEO, Set.of("mkv"), "video/x-matroska",
            Set.of("video/x-matroska", "video/matroska"), bytes -> isEbml(bytes, "matroska")),
        new MediaFormat(OssFileType.VIDEO, Set.of("wmv"), "video/x-ms-wmv",
            Set.of("video/x-ms-wmv", "video/x-ms-asf"), OssMediaUploadPolicy::isWmv),
        new MediaFormat(OssFileType.VIDEO, Set.of("flv"), "video/x-flv",
            Set.of("video/x-flv", "video/flv"), OssMediaUploadPolicy::isFlv)
    );

    public String validate(MultipartFile file, OssFileType fileType) {
        validateSize(file.getSize(), fileType);
        byte[] prefix;
        try (InputStream input = file.getInputStream()) {
            prefix = input.readNBytes(MAX_PROBE_BYTES);
        } catch (IOException exception) {
            ServiceException wrapped = new ServiceException("读取上传文件失败");
            wrapped.initCause(exception);
            throw wrapped;
        }
        String extension = extension(file.getOriginalFilename());
        String declaredType = normalizeContentType(file.getContentType());
        return FORMATS.stream()
            .filter(format -> format.fileType() == fileType)
            .filter(format -> format.extensions().contains(extension))
            .filter(format -> format.mimeAliases().contains(declaredType))
            .filter(format -> format.matcher().matches(prefix))
            .map(MediaFormat::canonicalMime)
            .findFirst()
            .orElseThrow(() -> mismatch(fileType));
    }

    private static void validateSize(long size, OssFileType fileType) {
        if (fileType == OssFileType.IMAGE && size > MAX_IMAGE_BYTES) {
            throw new ServiceException("单张图片不能超过50MB");
        }
        if (fileType == OssFileType.VIDEO && size > MAX_VIDEO_BYTES) {
            throw new ServiceException("单个视频不能超过2GB");
        }
    }

    private static ServiceException mismatch(OssFileType fileType) {
        return new ServiceException(fileType == OssFileType.IMAGE
            ? "图片文件内容与格式不匹配" : "视频文件内容与格式不匹配");
    }

    private static String extension(String filename) {
        if (filename == null) {
            return "";
        }
        int dot = filename.lastIndexOf('.');
        return dot < 0 || dot == filename.length() - 1
            ? "" : filename.substring(dot + 1).toLowerCase(Locale.ROOT);
    }

    private static String normalizeContentType(String contentType) {
        if (contentType == null) {
            return "";
        }
        int parameters = contentType.indexOf(';');
        String value = parameters < 0 ? contentType : contentType.substring(0, parameters);
        return value.trim().toLowerCase(Locale.ROOT);
    }

    private static boolean isJpeg(byte[] bytes) {
        return startsWith(bytes, new byte[]{(byte) 0xff, (byte) 0xd8, (byte) 0xff});
    }

    private static boolean isPng(byte[] bytes) {
        return startsWith(bytes, new byte[]{(byte) 0x89, 'P', 'N', 'G', 13, 10, 26, 10});
    }

    private static boolean isGif(byte[] bytes) {
        return startsWith(bytes, ascii("GIF87a")) || startsWith(bytes, ascii("GIF89a"));
    }

    private static boolean isRiff(byte[] bytes, String type) {
        return startsWith(bytes, ascii("RIFF")) && equalsAt(bytes, 8, ascii(type));
    }

    private static boolean isMp4(byte[] bytes) {
        int boxSize = isoBoxSize(bytes);
        if (boxSize < 16 || "qt  ".equals(asciiAt(bytes, 8))) {
            return false;
        }
        if (MP4_BRANDS.contains(asciiAt(bytes, 8))) {
            return true;
        }
        for (int offset = 16; offset + 4 <= boxSize; offset += 4) {
            if (MP4_BRANDS.contains(asciiAt(bytes, offset))) {
                return true;
            }
        }
        return false;
    }

    private static boolean isMov(byte[] bytes) {
        return isoBoxSize(bytes) >= 16 && equalsAt(bytes, 8, ascii("qt  "));
    }

    private static boolean isEbml(byte[] bytes, String documentType) {
        if (!startsWith(bytes, new byte[]{0x1a, 0x45, (byte) 0xdf, (byte) 0xa3})) {
            return false;
        }
        Vint headerSize = readVint(bytes, 4, false);
        if (headerSize == null) {
            return false;
        }
        int cursor = 4 + headerSize.length();
        long declaredEnd = cursor + headerSize.value();
        int headerEnd = (int) Math.min(bytes.length, declaredEnd);
        while (cursor < headerEnd) {
            Vint elementId = readVint(bytes, cursor, true);
            if (elementId == null) {
                return false;
            }
            int sizeOffset = cursor + elementId.length();
            Vint elementSize = readVint(bytes, sizeOffset, false);
            if (elementSize == null || elementSize.value() > Integer.MAX_VALUE) {
                return false;
            }
            int valueOffset = sizeOffset + elementSize.length();
            long valueEnd = (long) valueOffset + elementSize.value();
            if (valueEnd > headerEnd) {
                return false;
            }
            if (elementId.value() == 0x4282L) {
                int length = (int) elementSize.value();
                return documentType.equals(new String(bytes, valueOffset, length, StandardCharsets.US_ASCII));
            }
            cursor = (int) valueEnd;
        }
        return false;
    }

    private static int isoBoxSize(byte[] bytes) {
        if (bytes.length < 16 || !equalsAt(bytes, 4, ascii("ftyp"))) {
            return -1;
        }
        long size = ((long) Byte.toUnsignedInt(bytes[0]) << 24)
            | ((long) Byte.toUnsignedInt(bytes[1]) << 16)
            | ((long) Byte.toUnsignedInt(bytes[2]) << 8)
            | Byte.toUnsignedInt(bytes[3]);
        return size >= 16 && size <= bytes.length && (size - 16) % 4 == 0 ? (int) size : -1;
    }

    private static String asciiAt(byte[] bytes, int offset) {
        return new String(bytes, offset, 4, StandardCharsets.US_ASCII);
    }

    private static Vint readVint(byte[] bytes, int offset, boolean preserveMarker) {
        if (offset < 0 || offset >= bytes.length) {
            return null;
        }
        int first = Byte.toUnsignedInt(bytes[offset]);
        int marker = 0x80;
        int length = 1;
        while (length <= 8 && (first & marker) == 0) {
            marker >>>= 1;
            length++;
        }
        if (length > 8 || offset + length > bytes.length) {
            return null;
        }
        long value = preserveMarker ? first : first & (marker - 1);
        for (int index = 1; index < length; index++) {
            value = (value << 8) | Byte.toUnsignedInt(bytes[offset + index]);
        }
        return new Vint(length, value);
    }

    private static boolean isWmv(byte[] bytes) {
        return startsWith(bytes, new byte[]{0x30, 0x26, (byte) 0xb2, 0x75, (byte) 0x8e, 0x66, (byte) 0xcf, 0x11,
            (byte) 0xa6, (byte) 0xd9, 0x00, (byte) 0xaa, 0x00, 0x62, (byte) 0xce, 0x6c});
    }

    private static boolean isFlv(byte[] bytes) {
        return startsWith(bytes, ascii("FLV")) && bytes.length >= 5 && bytes[3] == 1;
    }

    private static boolean startsWith(byte[] bytes, byte[] prefix) {
        return equalsAt(bytes, 0, prefix);
    }

    private static boolean equalsAt(byte[] bytes, int offset, byte[] expected) {
        return offset >= 0 && bytes.length >= offset + expected.length
            && Arrays.equals(bytes, offset, offset + expected.length, expected, 0, expected.length);
    }

    private static byte[] ascii(String value) {
        return value.getBytes(StandardCharsets.US_ASCII);
    }

    private record MediaFormat(OssFileType fileType, Set<String> extensions, String canonicalMime,
                               Set<String> mimeAliases, SignatureMatcher matcher) {
    }

    private record Vint(int length, long value) {
    }

    @FunctionalInterface
    private interface SignatureMatcher {
        boolean matches(byte[] prefix);
    }
}
