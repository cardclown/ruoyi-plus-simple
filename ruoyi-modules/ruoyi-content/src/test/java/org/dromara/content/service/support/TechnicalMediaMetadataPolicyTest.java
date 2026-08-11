package org.dromara.content.service.support;

import org.dromara.common.core.exception.ServiceException;
import org.dromara.common.core.oss.TechnicalMediaMetadataPolicy;
import org.dromara.common.core.oss.TechnicalMediaType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Tag("dev")
class TechnicalMediaMetadataPolicyTest {

    @ParameterizedTest
    @MethodSource("supportedMetadata")
    void canonicalizesEverySupportedSuffixAndMimeAlias(TechnicalMediaType type, String suffix,
                                                        String contentType, String canonicalType) {
        assertThat(TechnicalMediaMetadataPolicy.canonicalContentType(type, suffix, contentType))
            .contains(canonicalType);
    }

    @Test
    void rejectsCrossTypeAndMismatchedMetadata() {
        assertThat(TechnicalMediaMetadataPolicy.canonicalContentType(
            TechnicalMediaType.VIDEO, ".png", "image/png")).isEmpty();
        assertThat(TechnicalMediaMetadataPolicy.canonicalContentType(
            TechnicalMediaType.IMAGE, ".png", "image/jpeg")).isEmpty();
    }

    @Test
    void enforcesTheSharedImageAndVideoSizeLimits() {
        TechnicalMediaMetadataPolicy.validateSize(
            TechnicalMediaType.IMAGE, TechnicalMediaMetadataPolicy.MAX_IMAGE_BYTES);
        TechnicalMediaMetadataPolicy.validateSize(
            TechnicalMediaType.VIDEO, TechnicalMediaMetadataPolicy.MAX_VIDEO_BYTES);

        assertThatThrownBy(() -> TechnicalMediaMetadataPolicy.validateSize(
            TechnicalMediaType.IMAGE, TechnicalMediaMetadataPolicy.MAX_IMAGE_BYTES + 1))
            .isInstanceOf(ServiceException.class).hasMessage("单张图片不能超过50MB");
        assertThatThrownBy(() -> TechnicalMediaMetadataPolicy.validateSize(
            TechnicalMediaType.VIDEO, TechnicalMediaMetadataPolicy.MAX_VIDEO_BYTES + 1))
            .isInstanceOf(ServiceException.class).hasMessage("单个视频不能超过2GB");
    }

    private static Stream<Arguments> supportedMetadata() {
        return Stream.of(
            Arguments.of(TechnicalMediaType.IMAGE, ".jpg", "image/pjpeg; charset=binary", "image/jpeg"),
            Arguments.of(TechnicalMediaType.IMAGE, "jpeg", "image/jpeg", "image/jpeg"),
            Arguments.of(TechnicalMediaType.IMAGE, ".png", "image/x-png", "image/png"),
            Arguments.of(TechnicalMediaType.IMAGE, ".gif", "image/gif", "image/gif"),
            Arguments.of(TechnicalMediaType.IMAGE, ".webp", "image/webp", "image/webp"),
            Arguments.of(TechnicalMediaType.VIDEO, ".mp4", "application/mp4", "video/mp4"),
            Arguments.of(TechnicalMediaType.VIDEO, ".mov", "video/quicktime", "video/quicktime"),
            Arguments.of(TechnicalMediaType.VIDEO, ".avi", "video/avi", "video/x-msvideo"),
            Arguments.of(TechnicalMediaType.VIDEO, ".webm", "video/webm", "video/webm"),
            Arguments.of(TechnicalMediaType.VIDEO, ".mkv", "video/matroska", "video/x-matroska"),
            Arguments.of(TechnicalMediaType.VIDEO, ".wmv", "video/x-ms-asf", "video/x-ms-wmv"),
            Arguments.of(TechnicalMediaType.VIDEO, ".flv", "video/flv", "video/x-flv")
        );
    }
}
