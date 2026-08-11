package org.dromara.system.service.support;

import org.dromara.common.core.exception.ServiceException;
import org.dromara.system.domain.enums.OssFileType;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@Tag("dev")
class OssMediaUploadPolicyTest {

    private final OssMediaUploadPolicy policy = new OssMediaUploadPolicy();

    @ParameterizedTest
    @MethodSource("supportedMedia")
    void acceptsBoundedSignaturesAndNormalizesSafeMimeAliases(
        OssFileType fileType, String filename, String declaredType, byte[] header, String canonicalType) throws IOException {
        MultipartFile file = mockFile(filename, declaredType, 12L, header);

        OssMediaUploadPolicy.ValidatedMedia detected = policy.validate(file, fileType);

        assertThat(detected.contentType()).isEqualTo(canonicalType);
    }

    @Test
    void rejectsImageAboveFiftyMibBeforeOpeningItsContent() throws IOException {
        MultipartFile file = mockFile("a.png", "image/png", 50L * 1024 * 1024 + 1, png());

        assertThatThrownBy(() -> policy.validate(file, OssFileType.IMAGE))
            .isInstanceOf(ServiceException.class)
            .hasMessage("单张图片不能超过50MB");
        org.mockito.Mockito.verify(file, org.mockito.Mockito.never()).getInputStream();
    }

    @Test
    void rejectsVideoAboveTwoGibBeforeOpeningItsContent() throws IOException {
        MultipartFile file = mockFile("a.mp4", "video/mp4", 2L * 1024 * 1024 * 1024 + 1, mp4());

        assertThatThrownBy(() -> policy.validate(file, OssFileType.VIDEO))
            .isInstanceOf(ServiceException.class)
            .hasMessage("单个视频不能超过2GB");
        org.mockito.Mockito.verify(file, org.mockito.Mockito.never()).getInputStream();
    }

    @Test
    void rejectsAFileWhoseNameAndMimeSpoofAnImage() throws IOException {
        MultipartFile file = mockFile("a.png", "image/png", 4L, new byte[]{'M', 'Z', 0, 0});

        assertThatThrownBy(() -> policy.validate(file, OssFileType.IMAGE))
            .isInstanceOf(ServiceException.class)
            .hasMessage("图片文件内容与格式不匹配");
    }

    @Test
    void rejectsDotlessOriginalFilenameEvenWhenMimeAndSignatureAreValid() throws IOException {
        MultipartFile file = mockFile("png", "image/png", 8L, png());

        assertThatThrownBy(() -> policy.validate(file, OssFileType.IMAGE))
            .isInstanceOf(ServiceException.class)
            .hasMessage("图片文件内容与格式不匹配");
    }

    @Test
    void rejectsAValidImageWhenVideoWasRequested() throws IOException {
        MultipartFile file = mockFile("a.jpg", "image/jpeg", 4L, jpeg());

        assertThatThrownBy(() -> policy.validate(file, OssFileType.VIDEO))
            .isInstanceOf(ServiceException.class)
            .hasMessage("视频文件内容与格式不匹配");
    }

    @Test
    void rejectsANonVideoIsoContainerRenamedToMp4() throws IOException {
        MultipartFile file = mockFile("a.mp4", "video/mp4", 16L, iso("avif"));

        assertThatThrownBy(() -> policy.validate(file, OssFileType.VIDEO))
            .isInstanceOf(ServiceException.class)
            .hasMessage("视频文件内容与格式不匹配");
    }

    @Test
    void rejectsEbmlFreeTextThatIsNotADocTypeElement() throws IOException {
        MultipartFile file = mockFile("a.webm", "video/webm", 32L, ebmlFreeText("webm"));

        assertThatThrownBy(() -> policy.validate(file, OssFileType.VIDEO))
            .isInstanceOf(ServiceException.class)
            .hasMessage("视频文件内容与格式不匹配");
    }

    @Test
    void readsNoMoreThanTheProbeLimit() throws IOException {
        AtomicInteger bytesRead = new AtomicInteger();
        InputStream endless = new InputStream() {
            @Override
            public int read() {
                int index = bytesRead.getAndIncrement();
                byte[] signature = png();
                return index < signature.length ? Byte.toUnsignedInt(signature[index]) : 0;
            }
        };
        MultipartFile file = mock(MultipartFile.class);
        when(file.getOriginalFilename()).thenReturn("a.png");
        when(file.getContentType()).thenReturn("image/png");
        when(file.getSize()).thenReturn(10L);
        when(file.getInputStream()).thenReturn(endless);

        policy.validate(file, OssFileType.IMAGE);

        assertThat(bytesRead.get()).isLessThanOrEqualTo(OssMediaUploadPolicy.MAX_PROBE_BYTES);
    }

    private static Stream<Arguments> supportedMedia() {
        return Stream.of(
            Arguments.of(OssFileType.IMAGE, "a.jpg", "image/pjpeg; charset=binary", jpeg(), "image/jpeg"),
            Arguments.of(OssFileType.IMAGE, "a.jpeg", "image/jpeg", jpeg(), "image/jpeg"),
            Arguments.of(OssFileType.IMAGE, "C:\\incoming.dir\\archive.2026.final.png", "image/x-png",
                png(), "image/png"),
            Arguments.of(OssFileType.IMAGE, "a.gif", "image/gif", ascii("GIF89a"), "image/gif"),
            Arguments.of(OssFileType.IMAGE, "a.webp", "image/webp", riff("WEBP"), "image/webp"),
            Arguments.of(OssFileType.VIDEO, "a.mp4", "application/mp4", mp4(), "video/mp4"),
            Arguments.of(OssFileType.VIDEO, "a.mp4", "video/mp4", iso("iso5"), "video/mp4"),
            Arguments.of(OssFileType.VIDEO, "a.mp4", "video/mp4", iso("iso6"), "video/mp4"),
            Arguments.of(OssFileType.VIDEO, "a.mp4", "video/mp4", iso("zzzz", "iso6"), "video/mp4"),
            Arguments.of(OssFileType.VIDEO, "a.mov", "video/quicktime", mov(), "video/quicktime"),
            Arguments.of(OssFileType.VIDEO, "a.avi", "video/avi", riff("AVI "), "video/x-msvideo"),
            Arguments.of(OssFileType.VIDEO, "a.webm", "video/webm", ebml("webm"), "video/webm"),
            Arguments.of(OssFileType.VIDEO, "a.mkv", "video/matroska", ebml("matroska"), "video/x-matroska"),
            Arguments.of(OssFileType.VIDEO, "a.wmv", "video/x-ms-asf", wmv(), "video/x-ms-wmv"),
            Arguments.of(OssFileType.VIDEO, "a.flv", "video/flv", new byte[]{'F', 'L', 'V', 1, 5}, "video/x-flv")
        );
    }

    private static MultipartFile mockFile(String name, String contentType, long size, byte[] header) throws IOException {
        MultipartFile file = mock(MultipartFile.class);
        when(file.getOriginalFilename()).thenReturn(name);
        when(file.getContentType()).thenReturn(contentType);
        when(file.getSize()).thenReturn(size);
        when(file.getInputStream()).thenAnswer(ignored -> new ByteArrayInputStream(header));
        return file;
    }

    private static byte[] jpeg() {
        return new byte[]{(byte) 0xff, (byte) 0xd8, (byte) 0xff, (byte) 0xe0};
    }

    private static byte[] png() {
        return new byte[]{(byte) 0x89, 'P', 'N', 'G', 13, 10, 26, 10};
    }

    private static byte[] riff(String type) {
        byte[] bytes = new byte[12];
        System.arraycopy(ascii("RIFF"), 0, bytes, 0, 4);
        System.arraycopy(ascii(type), 0, bytes, 8, 4);
        return bytes;
    }

    private static byte[] mp4() {
        return iso("isom");
    }

    private static byte[] mov() {
        return iso("qt  ");
    }

    private static byte[] iso(String brand, String... compatibleBrands) {
        byte[] bytes = new byte[16 + compatibleBrands.length * 4];
        int size = bytes.length;
        bytes[0] = (byte) (size >>> 24);
        bytes[1] = (byte) (size >>> 16);
        bytes[2] = (byte) (size >>> 8);
        bytes[3] = (byte) size;
        System.arraycopy(ascii("ftyp"), 0, bytes, 4, 4);
        System.arraycopy(ascii(brand), 0, bytes, 8, 4);
        for (int index = 0; index < compatibleBrands.length; index++) {
            System.arraycopy(ascii(compatibleBrands[index]), 0, bytes, 16 + index * 4, 4);
        }
        return bytes;
    }

    private static byte[] ebml(String documentType) {
        byte[] type = ascii(documentType);
        byte[] bytes = new byte[8 + type.length];
        bytes[0] = 0x1a;
        bytes[1] = 0x45;
        bytes[2] = (byte) 0xdf;
        bytes[3] = (byte) 0xa3;
        bytes[4] = (byte) (0x80 | (3 + type.length));
        bytes[5] = 0x42;
        bytes[6] = (byte) 0x82;
        bytes[7] = (byte) (0x80 | type.length);
        System.arraycopy(type, 0, bytes, 8, type.length);
        return bytes;
    }

    private static byte[] ebmlFreeText(String text) {
        byte[] value = ascii("unrelated-" + text);
        byte[] bytes = new byte[8 + value.length];
        bytes[0] = 0x1a;
        bytes[1] = 0x45;
        bytes[2] = (byte) 0xdf;
        bytes[3] = (byte) 0xa3;
        bytes[4] = (byte) (0x80 | (3 + value.length));
        bytes[5] = 0x42;
        bytes[6] = (byte) 0x86;
        bytes[7] = (byte) (0x80 | value.length);
        System.arraycopy(value, 0, bytes, 8, value.length);
        return bytes;
    }

    private static byte[] wmv() {
        return new byte[]{0x30, 0x26, (byte) 0xb2, 0x75, (byte) 0x8e, 0x66, (byte) 0xcf, 0x11,
            (byte) 0xa6, (byte) 0xd9, 0x00, (byte) 0xaa, 0x00, 0x62, (byte) 0xce, 0x6c};
    }

    private static byte[] ascii(String value) {
        return value.getBytes(StandardCharsets.US_ASCII);
    }
}
