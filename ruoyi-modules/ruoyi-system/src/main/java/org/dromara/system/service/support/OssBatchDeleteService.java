package org.dromara.system.service.support;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dromara.common.core.exception.ServiceException;
import org.dromara.common.core.utils.StringUtils;
import org.dromara.system.domain.vo.SysOssDeleteResultVo;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;

/**
 * 公共 OSS 批量删除编排服务。
 *
 * <p>批量请求按 OSS ID 独立处理，单项失败不会回滚其他附件。预期业务异常直接返回后端消息，
 * 未知异常只记录服务端日志，避免向前端暴露内部实现细节。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OssBatchDeleteService {

    private final OssSingleDeleteService singleDeleteService;

    /**
     * 批量删除附件并逐项返回结果。
     *
     * @param ossIds 待删除 OSS ID
     * @return 按首次出现顺序排列的逐项结果
     */
    public List<SysOssDeleteResultVo> delete(Collection<Long> ossIds) {
        if (ossIds == null || ossIds.isEmpty()) {
            throw new ServiceException("OSS ID不能为空");
        }
        if (ossIds.stream().anyMatch(java.util.Objects::isNull)) {
            throw new ServiceException("OSS ID不能为空");
        }
        List<Long> distinctIds = new ArrayList<>(new LinkedHashSet<>(ossIds));
        List<SysOssDeleteResultVo> results = new ArrayList<>(distinctIds.size());
        for (Long ossId : distinctIds) {
            try {
                String message = singleDeleteService.deleteOne(ossId);
                results.add(new SysOssDeleteResultVo(ossId.toString(), true, message));
            } catch (ServiceException exception) {
                results.add(new SysOssDeleteResultVo(ossId.toString(), false,
                    messageOrDefault(exception.getMessage())));
            } catch (RuntimeException exception) {
                log.error("删除 OSS 附件失败，ossId={}", ossId, exception);
                results.add(new SysOssDeleteResultVo(ossId.toString(), false, "删除失败"));
            }
        }
        return results;
    }

    private String messageOrDefault(String message) {
        return StringUtils.isBlank(message) ? "删除失败" : message;
    }
}
