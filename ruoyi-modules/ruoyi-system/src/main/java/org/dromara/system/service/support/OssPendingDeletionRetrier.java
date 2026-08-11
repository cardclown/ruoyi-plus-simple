package org.dromara.system.service.support;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dromara.common.json.utils.JsonUtils;
import org.dromara.common.tenant.helper.TenantHelper;
import org.dromara.system.domain.SysOss;
import org.dromara.system.domain.SysOssExt;
import org.dromara.system.mapper.SysOssMapper;
import org.springframework.stereotype.Component;

import java.util.List;

/** Runs one bounded retry page for committed pending deletions. */
@Slf4j
@Component
@RequiredArgsConstructor
public class OssPendingDeletionRetrier {

    private static final int BATCH_SIZE = 100;

    private final SysOssMapper mapper;
    private final OssPendingDeletionWorker worker;

    public void retryBatch() {
        List<SysOss> candidates = TenantHelper.ignore(() -> {
            LambdaQueryWrapper<SysOss> query = Wrappers.lambdaQuery();
            query.like(SysOss::getExt1, OssBusinessDeletionCoordinator.PENDING_JSON_MARKER)
                .orderByAsc(SysOss::getOssId)
                .last("LIMIT " + BATCH_SIZE);
            return mapper.selectList(query);
        });
        candidates.stream().limit(BATCH_SIZE).forEach(candidate -> {
            try {
                SysOssExt ext = JsonUtils.parseObject(candidate.getExt1(), SysOssExt.class);
                if (ext != null && OssBusinessDeletionCoordinator.PENDING.equals(ext.getDeleteStatus())) {
                    worker.deleteCandidate(candidate.getOssId(), ext.getDeleteToken());
                }
            } catch (RuntimeException exception) {
                log.warn("Unable to retry pending OSS deletion {}: {}",
                    candidate.getOssId(), exception.getMessage());
            }
        });
    }
}
