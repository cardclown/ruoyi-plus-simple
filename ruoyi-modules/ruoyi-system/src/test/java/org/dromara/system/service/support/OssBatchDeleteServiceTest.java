package org.dromara.system.service.support;

import org.dromara.common.core.exception.ServiceException;
import org.dromara.system.domain.vo.SysOssDeleteResultVo;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 公共 OSS 批量删除的部分成功行为测试。
 */
@Tag("dev")
class OssBatchDeleteServiceTest {

    private final OssSingleDeleteService singleDeleteService = mock(OssSingleDeleteService.class);
    private final OssBatchDeleteService service = new OssBatchDeleteService(singleDeleteService);

    @Test
    void continuesDeletingLaterItemsWhenOneItemFails() {
        when(singleDeleteService.deleteOne(10L)).thenReturn("删除成功");
        when(singleDeleteService.deleteOne(20L)).thenThrow(new ServiceException("附件不存在"));
        when(singleDeleteService.deleteOne(30L)).thenReturn("附件正在删除");

        List<SysOssDeleteResultVo> results = service.delete(List.of(10L, 20L, 30L));

        assertThat(results)
            .extracting(SysOssDeleteResultVo::getOssId, SysOssDeleteResultVo::isSuccess,
                SysOssDeleteResultVo::getMessage)
            .containsExactly(
                tuple("10", true, "删除成功"),
                tuple("20", false, "附件不存在"),
                tuple("30", true, "附件正在删除")
            );
        verify(singleDeleteService).deleteOne(10L);
        verify(singleDeleteService).deleteOne(20L);
        verify(singleDeleteService).deleteOne(30L);
    }

    @Test
    void processesDuplicateIdsOnlyOnceAndPreservesFirstOccurrenceOrder() {
        when(singleDeleteService.deleteOne(20L)).thenReturn("删除成功");
        when(singleDeleteService.deleteOne(10L)).thenReturn("删除成功");

        List<SysOssDeleteResultVo> results = service.delete(List.of(20L, 10L, 20L));

        assertThat(results).extracting(SysOssDeleteResultVo::getOssId)
            .containsExactly("20", "10");
        verify(singleDeleteService).deleteOne(20L);
        verify(singleDeleteService).deleteOne(10L);
    }

    @Test
    void rejectsNullIdsBeforeStartingAnyDeletion() {
        assertThatThrownBy(() -> service.delete(Arrays.asList(10L, null)))
            .isInstanceOf(ServiceException.class)
            .hasMessage("OSS ID不能为空");
    }

    private static org.assertj.core.groups.Tuple tuple(Object... values) {
        return org.assertj.core.groups.Tuple.tuple(values);
    }
}
