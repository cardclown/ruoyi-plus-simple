package org.dromara.system.service.support;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dromara.common.core.constant.CacheNames;
import org.dromara.common.core.exception.ServiceException;
import org.dromara.common.core.utils.StringUtils;
import org.dromara.common.json.utils.JsonUtils;
import org.dromara.common.redis.utils.CacheUtils;
import org.dromara.system.domain.SysOss;
import org.dromara.system.domain.SysOssExt;
import org.dromara.system.mapper.SysOssMapper;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Converts exact business-owned OSS rows into retryable pending deletion state.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OssBusinessDeletionCoordinator {

    public static final String PENDING = "PENDING";
    public static final String PENDING_JSON_MARKER = "\"deleteStatus\":\"PENDING\"";

    private final SysOssMapper mapper;
    private final OssPendingDeletionWorker worker;

    public void schedule(Map<Long, String> ossOwnerRefIds, String refType) {
        if (ossOwnerRefIds == null || ossOwnerRefIds.isEmpty()) {
            return;
        }
        if (!TransactionSynchronizationManager.isActualTransactionActive()
            || !TransactionSynchronizationManager.isSynchronizationActive()) {
            throw new IllegalStateException("业务附件删除必须在活动事务中调度");
        }
        if (StringUtils.isBlank(refType) || ossOwnerRefIds.entrySet().stream()
            .anyMatch(entry -> entry.getKey() == null || StringUtils.isBlank(entry.getValue()))) {
            throw new ServiceException("附件删除归属不能为空");
        }
        List<Long> ids = ossOwnerRefIds.keySet().stream().distinct().sorted().toList();
        LambdaQueryWrapper<SysOss> query = Wrappers.lambdaQuery();
        query.in(SysOss::getOssId, ids).orderByAsc(SysOss::getOssId).last("FOR UPDATE");
        List<SysOss> rows = mapper.selectList(query);
        if (rows.size() != ids.size()) {
            throw new ServiceException("部分附件不存在或不属于当前租户");
        }

        Map<Long, String> tokens = new LinkedHashMap<>();
        for (SysOss row : rows) {
            SysOssExt ext = parseExt(row.getExt1());
            if (!Objects.equals(refType, ext.getRefType())
                || !Objects.equals(ossOwnerRefIds.get(row.getOssId()), ext.getRefId())) {
                throw new ServiceException("附件归属已变化，不能删除");
            }
            if (PENDING.equals(ext.getDeleteStatus())) {
                throw new ServiceException("附件已在等待删除");
            }
            String token = UUID.randomUUID().toString();
            ext.setDeleteStatus(PENDING);
            ext.setDeleteToken(token);
            ext.setDeleteRequestedAt(new Date());
            row.setExt1(JsonUtils.toJsonString(ext));
            if (mapper.updateById(row) != 1) {
                throw new ServiceException("附件待删除状态保存失败");
            }
            tokens.put(row.getOssId(), token);
        }

        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                tokens.forEach((ossId, token) -> {
                    CacheUtils.evict(CacheNames.SYS_OSS, ossId);
                    try {
                        worker.deleteCandidate(ossId, token);
                    } catch (RuntimeException exception) {
                        log.warn("Unable to delete committed OSS object {}: {}", ossId, exception.getMessage());
                    }
                });
            }
        });
    }

    private static SysOssExt parseExt(String ext1) {
        return StringUtils.isBlank(ext1) ? new SysOssExt() : JsonUtils.parseObject(ext1, SysOssExt.class);
    }
}
