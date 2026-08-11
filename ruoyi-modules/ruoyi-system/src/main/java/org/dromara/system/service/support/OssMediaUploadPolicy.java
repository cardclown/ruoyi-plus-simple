package org.dromara.system.service.support;

import org.dromara.common.core.exception.ServiceException;
import org.dromara.common.core.oss.TechnicalMediaMetadataPolicy;
import org.dromara.common.core.oss.TechnicalMediaType;
import org.dromara.system.domain.enums.OssFileType;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Map;
import java.util.Set;

/**
 * Bounded signature validation for technically classified media uploads.
 */
@Component
public class OssMediaUploadPolicy {

    public static final int MAX_PROBE_BYTES = 4 * 1024;

    private static final Set<String> MP4_BRANDS = Set.of(
        "isom", "iso2", "iso5", "iso6", "mp41", "mp42", "avc1", "M4V ", "MSNV", "3gp4", "3gp5"
    );

    private static final Map<String, SignatureMatcher> SIGNATURE_MATCHERS = Map.ofEntries(
        Map.entry("image/jpeg", OssMediaUploadPolicy::isJpeg),
        Map.entry("image/png", OssMediaUploadPolicy::isPng),
        Map.entry("image/gif", OssMediaUploadPolicy::isGif),
        Map.entry("image/webp", bytes -> isRiff(bytes, "WEBP")),
        Map.entry("video/mp4", OssMediaUploadPolicy::isMp4),
        Map.entry("video/quicktime", OssMediaUploadPolicy::isMov),
        Map.entry("video/x-msvideo", bytes -> isRiff(bytes, "AVI ")),
        Map.entry("video/webm", bytes -> isEbml(bytes, "webm")),
        Map.entry("video/x-matroska", bytes -> isEbml(bytes, "matroska")),
        Map.entry("video/x-ms-wmv", OssMediaUploadPolicy::isWmv),
        Map.entry("video/x-flv", OssMediaUploadPolicy::isFlv)
    );

    public String validate(MultipartFile file, OssFileType fileType) {
        TechnicalMediaType technicalType = TechnicalMediaType.valueOf(fileType.name());
        TechnicalMediaMetadataPolicy.validateSize(technicalType, file.getSize());
        String canonicalType = TechnicalMediaMetadataPolicy.canonicalContentType(
            technicalType, file.getOriginalFilename(), file.getContentType()).orElseThrow(() -> mismatch(fileType));
        byte[] prefix;
        try (InputStream input = file.getInputStream()) {
            prefix = input.readNBytes(MAX_PROBE_BYTES);
        } catch (IOException exception) {
            ServiceException wrapped = new ServiceException("读取上传文件失败");
            wrapped.initCause(exception);
            throw wrapped;
        }
        SignatureMatcher matcher = SIGNATURE_MATCHERS.get(canonicalType);
        if (matcher == null || !matcher.matches(prefix)) {
            throw mismatch(fileType);
        }
        return canonicalType;
    }

    private static ServiceException mismatch(OssFileType fileType) {
        return new ServiceException(fileType == OssFileType.IMAGE
            ? "图片文件内容与格式不匹配" : "视频文件内容与格式不匹配");
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

    private record Vint(int length, long value) {
    }

    @FunctionalInterface
    private interface SignatureMatcher {
        boolean matches(byte[] prefix);
    }
}
