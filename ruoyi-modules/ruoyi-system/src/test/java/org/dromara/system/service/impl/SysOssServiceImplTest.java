package org.dromara.system.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.linpeilie.Converter;
import org.dromara.common.core.utils.SpringUtils;
import org.dromara.common.core.domain.dto.OssDTO;
import org.dromara.common.core.exception.ServiceException;
import org.dromara.common.json.utils.JsonUtils;
import org.dromara.common.oss.core.OssClient;
import org.dromara.common.oss.entity.UploadResult;
import org.dromara.system.domain.SysOss;
import org.dromara.system.domain.SysOssExt;
import org.dromara.system.domain.vo.SysOssVo;
import org.dromara.system.mapper.SysOssMapper;
import org.dromara.system.service.support.ArticleOssUploadPolicy;
import org.dromara.system.service.support.OssClientProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;
import org.springframework.context.support.GenericApplicationContext;
import org.springframework.mock.web.MockMultipartFile;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Date;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@Tag("dev")
class SysOssServiceImplTest {

    @Mock
    private SysOssMapper mapper;

    @Mock
    private OssClientProvider ossClientProvider;

    @Mock
    private OssClient ossClient;

    @InjectMocks
    private SysOssServiceImpl service;

    @Captor
    private ArgumentCaptor<List<SysOss>> ossListCaptor;

    @Captor
    private ArgumentCaptor<SysOss> ossCaptor;

    @BeforeAll
    static void setUpSpringUtilities() {
        GenericApplicationContext context = new GenericApplicationContext();
        Converter converter = mock(Converter.class);
        when(converter.convert(any(SysOss.class), eq(SysOssVo.class))).thenAnswer(invocation -> {
            SysOss source = invocation.getArgument(0);
            SysOssVo target = new SysOssVo();
            target.setOssId(source.getOssId());
            target.setFileName(source.getFileName());
            target.setOriginalName(source.getOriginalName());
            target.setFileSuffix(source.getFileSuffix());
            target.setUrl(source.getUrl());
            target.setExt1(source.getExt1());
            target.setService(source.getService());
            return target;
        });
        context.getBeanFactory().registerSingleton("objectMapper", new ObjectMapper());
        context.getBeanFactory().registerSingleton("cacheManager", new ConcurrentMapCacheManager());
        context.getBeanFactory().registerSingleton("converter", converter);
        context.refresh();
        new SpringUtils().setApplicationContext(context);
    }

    @BeforeEach
    void setUp() {
        service = new SysOssServiceImpl(mapper, ossClientProvider, new ArticleOssUploadPolicy());
    }

    @Test
    void selectByIdsUsesOneBatchQueryParsesMetadataAndPreservesRequestOrder() {
        SysOssVo ten = vo(10L, "{\"fileSize\":52428800,\"contentType\":\"image/png\",\"isTemp\":true}");
        SysOssVo eleven = vo(11L, "{\"fileSize\":12,\"contentType\":\"video/mp4\",\"refType\":\"content_article\",\"refId\":\"100\",\"bizType\":\"cover\",\"isTemp\":false}");
        when(mapper.selectVoByIds(List.of(11L, 10L))).thenReturn(List.of(ten, eleven));

        List<OssDTO> result = service.selectByIds(List.of(11L, 10L, 11L));

        assertThat(result).extracting(OssDTO::getOssId).containsExactly(11L, 10L);
        assertThat(result.get(0))
            .extracting(OssDTO::getFileSize, OssDTO::getContentType, OssDTO::getBizType,
                OssDTO::getRefType, OssDTO::getRefId, OssDTO::getIsTemp)
            .containsExactly(12L, "video/mp4", "cover", "content_article", "100", false);
        assertThat(result.get(1))
            .extracting(OssDTO::getFileSize, OssDTO::getContentType, OssDTO::getIsTemp)
            .containsExactly(52_428_800L, "image/png", true);
        verify(mapper).selectVoByIds(List.of(11L, 10L));
    }

    @Test
    void bindRejectsMissingOssIdsBeforeChangingAnyRecord() {
        when(mapper.selectByIds(List.of(10L, 11L))).thenReturn(List.of(oss(10L, "{\"isTemp\":true}")));

        assertThatThrownBy(() -> service.bindToBusiness(
            List.of(10L, 11L), "content_article", "100"))
            .isInstanceOf(ServiceException.class)
            .hasMessage("部分附件不存在或不属于当前租户");

        verify(mapper, never()).updateBatchById(anyCollection());
    }

    @Test
    void bindWritesReferenceAndMarksEveryAttachmentPermanent() {
        when(mapper.selectByIds(List.of(10L, 11L))).thenReturn(List.of(
            oss(10L, "{\"isTemp\":true}"), oss(11L, null)));
        when(mapper.updateBatchById(anyCollection())).thenReturn(true);

        service.bindToBusiness(List.of(10L, 11L), "content_article", "100");

        verify(mapper).updateBatchById(ossListCaptor.capture());
        assertThat(ossListCaptor.getValue()).allSatisfy(oss -> {
            assertThat(oss.getExt1()).contains("\"refType\":\"content_article\"");
            assertThat(oss.getExt1()).contains("\"refId\":\"100\"");
            assertThat(oss.getExt1()).contains("\"isTemp\":false");
        });
    }

    @Test
    void uploadPersistsNewAttachmentAsTemporary() {
        MockMultipartFile file = multipartFileRejectingGetBytes("cover.png", "image/png", new byte[]{1, 2, 3});
        UploadResult uploadResult = UploadResult.builder()
            .filename("uploads/cover.png")
            .url("https://bucket.example/uploads/cover.png")
            .build();
        when(ossClientProvider.current()).thenReturn(ossClient);
        when(ossClient.uploadSuffix(any(InputStream.class), eq(".png"), eq(3L), eq("image/png")))
            .thenReturn(uploadResult);
        when(ossClient.getConfigKey()).thenReturn("minio");
        when(ossClientProvider.byService("minio")).thenReturn(ossClient);
        when(mapper.insert(any(SysOss.class))).thenReturn(1);

        SysOssVo result = service.upload(file);

        verify(mapper).insert(ossCaptor.capture());
        SysOssExt ext = JsonUtils.parseObject(ossCaptor.getValue().getExt1(), SysOssExt.class);
        assertThat(ext)
            .extracting(SysOssExt::getFileSize, SysOssExt::getContentType, SysOssExt::getIsTemp)
            .containsExactly(3L, "image/png", true);
        assertThat(result)
            .extracting(SysOssVo::getFileName, SysOssVo::getOriginalName, SysOssVo::getUrl)
            .containsExactly("uploads/cover.png", "cover.png", "https://bucket.example/uploads/cover.png");
    }

    @Test
    void articleUploadPersistsItsBusinessTypeAndSource() {
        MockMultipartFile file = multipartFileRejectingGetBytes("cover.png", "image/png", new byte[]{1, 2, 3});
        UploadResult uploadResult = UploadResult.builder()
            .filename("uploads/cover.png")
            .url("https://bucket.example/uploads/cover.png")
            .build();
        when(ossClientProvider.current()).thenReturn(ossClient);
        when(ossClient.uploadSuffix(any(InputStream.class), eq(".png"), eq(3L), eq("image/png")))
            .thenReturn(uploadResult);
        when(ossClient.getConfigKey()).thenReturn("minio");
        when(ossClientProvider.byService("minio")).thenReturn(ossClient);
        when(mapper.insert(any(SysOss.class))).thenReturn(1);

        service.upload(file, "article_attachment");

        verify(mapper).insert(ossCaptor.capture());
        SysOssExt ext = JsonUtils.parseObject(ossCaptor.getValue().getExt1(), SysOssExt.class);
        assertThat(ext)
            .extracting(SysOssExt::getBizType, SysOssExt::getSource, SysOssExt::getIsTemp)
            .containsExactly("article_attachment", "userUpload", true);
    }

    @Test
    void uploadDeletesObjectWhenAttachmentRecordCannotBeSaved() {
        MockMultipartFile file = multipartFileRejectingGetBytes("cover.png", "image/png", new byte[]{1, 2, 3});
        UploadResult uploadResult = UploadResult.builder()
            .filename("uploads/cover.png")
            .url("https://bucket.example/uploads/cover.png")
            .build();
        when(ossClientProvider.current()).thenReturn(ossClient);
        when(ossClient.uploadSuffix(any(InputStream.class), eq(".png"), eq(3L), eq("image/png")))
            .thenReturn(uploadResult);
        when(ossClient.getConfigKey()).thenReturn("minio");
        when(mapper.insert(any(SysOss.class))).thenReturn(0);

        assertThatThrownBy(() -> service.upload(file))
            .isInstanceOf(ServiceException.class)
            .hasMessage("文件信息保存失败");

        verify(ossClient).delete("https://bucket.example/uploads/cover.png");
    }

    @Test
    void uploadDeletesObjectWhenSourceCloseFailsAfterObjectStoreCompletion() {
        byte[] content = {1, 2, 3};
        MockMultipartFile file = new MockMultipartFile("file", "cover.png", "image/png", content) {
            @Override
            public byte[] getBytes() {
                throw new AssertionError("MultipartFile.getBytes() must not be used for uploads");
            }

            @Override
            public InputStream getInputStream() {
                return new ByteArrayInputStream(content) {
                    @Override
                    public void close() throws IOException {
                        throw new IOException("close failed");
                    }
                };
            }
        };
        UploadResult uploadResult = UploadResult.builder()
            .filename("uploads/cover.png")
            .url("https://bucket.example/uploads/cover.png")
            .build();
        when(ossClientProvider.current()).thenReturn(ossClient);
        when(ossClient.uploadSuffix(any(InputStream.class), eq(".png"), eq(3L), eq("image/png")))
            .thenReturn(uploadResult);

        assertThatThrownBy(() -> service.upload(file))
            .isInstanceOf(ServiceException.class)
            .hasMessage("读取上传文件失败")
            .hasCauseInstanceOf(IOException.class)
            .hasRootCauseMessage("close failed");

        verify(ossClient).delete("https://bucket.example/uploads/cover.png");
        verify(mapper, never()).insert(any(SysOss.class));
    }

    @Test
    void uploadKeepsObjectWhenPostInsertUrlMappingFails() {
        MockMultipartFile file = multipartFileRejectingGetBytes("cover.png", "image/png", new byte[]{1, 2, 3});
        UploadResult uploadResult = UploadResult.builder()
            .filename("uploads/cover.png")
            .url("https://bucket.example/uploads/cover.png")
            .build();
        when(ossClientProvider.current()).thenReturn(ossClient);
        when(ossClient.uploadSuffix(any(InputStream.class), eq(".png"), eq(3L), eq("image/png")))
            .thenReturn(uploadResult);
        when(ossClient.getConfigKey()).thenReturn("minio");
        when(mapper.insert(any(SysOss.class))).thenReturn(1);
        when(ossClientProvider.byService("minio"))
            .thenThrow(new IllegalStateException("post-insert mapping failed"));

        assertThatThrownBy(() -> service.upload(file))
            .isInstanceOf(IllegalStateException.class)
            .hasMessage("post-insert mapping failed");

        verify(mapper).insert(any(SysOss.class));
        verify(ossClient, never()).delete("https://bucket.example/uploads/cover.png");
    }

    @Test
    void uploadDeletesObjectWhenMetadataCreationFailsBeforeInsert() {
        MockMultipartFile file = multipartFileRejectingGetBytes("cover.png", "image/png", new byte[]{1, 2, 3});
        UploadResult uploadResult = UploadResult.builder()
            .filename("uploads/cover.png")
            .url("https://bucket.example/uploads/cover.png")
            .build();
        when(ossClientProvider.current()).thenReturn(ossClient);
        when(ossClient.uploadSuffix(any(InputStream.class), eq(".png"), eq(3L), eq("image/png")))
            .thenReturn(uploadResult);
        when(ossClient.getConfigKey()).thenThrow(new IllegalStateException("metadata creation failed"));

        assertThatThrownBy(() -> service.upload(file))
            .isInstanceOf(IllegalStateException.class)
            .hasMessage("metadata creation failed");

        verify(mapper, never()).insert(any(SysOss.class));
        verify(ossClient).delete("https://bucket.example/uploads/cover.png");
    }

    @Test
    void deleteByIdsDoesNotRemoveDatabaseRowsWhenObjectDeletionFails() {
        SysOss stored = oss(10L, null);
        stored.setService("minio");
        stored.setUrl("https://bucket.example/uploads/a.png");
        when(mapper.selectByIds(List.of(10L))).thenReturn(List.of(stored));
        when(ossClientProvider.byService("minio")).thenReturn(ossClient);
        doThrow(new IllegalStateException("object store unavailable")).when(ossClient)
            .delete("https://bucket.example/uploads/a.png");

        assertThatThrownBy(() -> service.deleteByIds(List.of(10L)))
            .isInstanceOf(IllegalStateException.class)
            .hasMessage("object store unavailable");

        verify(mapper, never()).deleteByIds(anyCollection());
    }

    @Test
    void expiredCleanupDeletesOnlyUnboundTemporaryArticleObjectsBeforeTheirRows() {
        SysOss eligible = storedOss(10L, "{\"bizType\":\"article_attachment\",\"isTemp\":true}");
        SysOss bound = storedOss(11L,
            "{\"bizType\":\"article_video\",\"isTemp\":true,\"refId\":\"article-1\"}");
        SysOss permanent = storedOss(12L,
            "{\"bizType\":\"article_attachment\",\"isTemp\":false}");
        when(mapper.selectList(any())).thenReturn(List.of(eligible, bound, permanent));
        when(ossClientProvider.byService("minio")).thenReturn(ossClient);
        when(mapper.deleteById(10L)).thenReturn(1);

        service.deleteExpiredArticleTemps(new Date(1_786_291_200_000L));

        InOrder deletionOrder = inOrder(ossClient, mapper);
        deletionOrder.verify(ossClient).delete("https://bucket.example/uploads/10");
        deletionOrder.verify(mapper).deleteById(10L);
        verify(mapper, never()).deleteById(11L);
        verify(mapper, never()).deleteById(12L);
    }

    @Test
    void expiredCleanupKeepsDatabaseRowWhenObjectDeletionFails() {
        SysOss eligible = storedOss(10L, "{\"bizType\":\"article_video\",\"isTemp\":true}");
        when(mapper.selectList(any())).thenReturn(List.of(eligible));
        when(ossClientProvider.byService("minio")).thenReturn(ossClient);
        doThrow(new IllegalStateException("object store unavailable")).when(ossClient)
            .delete("https://bucket.example/uploads/10");

        assertThatThrownBy(() -> service.deleteExpiredArticleTemps(new Date(1_786_291_200_000L)))
            .isInstanceOf(IllegalStateException.class)
            .hasMessage("object store unavailable");

        verify(mapper, never()).deleteById(10L);
    }

    @Test
    void directDeleteRejectsAnAttachmentAlreadyBoundToBusiness() {
        when(mapper.selectByIds(List.of(10L))).thenReturn(List.of(
            oss(10L, "{\"isTemp\":false,\"refType\":\"content_article\"}")));

        assertThatThrownBy(() -> service.deleteWithValidByIds(List.of(10L), true))
            .isInstanceOf(ServiceException.class)
            .hasMessage("附件已被业务使用，不能直接删除");
    }

    private static SysOssVo vo(Long ossId, String ext1) {
        SysOssVo vo = new SysOssVo();
        vo.setOssId(ossId);
        vo.setExt1(ext1);
        return vo;
    }

    private static SysOss oss(Long ossId, String ext1) {
        SysOss oss = new SysOss();
        oss.setOssId(ossId);
        oss.setExt1(ext1);
        return oss;
    }

    private static SysOss storedOss(Long ossId, String ext1) {
        SysOss oss = oss(ossId, ext1);
        oss.setService("minio");
        oss.setUrl("https://bucket.example/uploads/" + ossId);
        return oss;
    }

    private static MockMultipartFile multipartFileRejectingGetBytes(String filename, String contentType, byte[] content) {
        return new MockMultipartFile("file", filename, contentType, content) {
            @Override
            public byte[] getBytes() {
                throw new AssertionError("MultipartFile.getBytes() must not be used for uploads");
            }

            @Override
            public InputStream getInputStream() throws IOException {
                return new ByteArrayInputStream(content);
            }
        };
    }
}
