package org.dromara.system.service.support;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import lombok.RequiredArgsConstructor;
import org.dromara.common.core.constant.CacheNames;
import org.dromara.common.core.exception.ServiceException;
import org.dromara.common.core.utils.StringUtils;
import org.dromara.common.json.utils.JsonUtils;
import org.dromara.common.redis.utils.CacheUtils;
import org.dromara.common.tenant.helper.TenantHelper;
import org.dromara.system.domain.SysOss;
import org.dromara.system.domain.SysOssExt;
import org.dromara.system.mapper.SysOssMapper;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;

/**
 * 锁定并复核删除令牌后，清理一条已提交的待删除附件。
 *
 * <p>对象删除只使用 {@code fileName}，不能依赖可能因 OSS 迁移而失效的历史完整 URL。</p>
 */
@Component
@RequiredArgsConstructor
public class OssPendingDeletionWorker {

    private final SysOssMapper mapper;
    private final OssClientProvider clientProvider;

    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public boolean deleteCandidate(Long ossId, String expectedToken) {
        return TenantHelper.ignore(() -> deleteLocked(ossId, expectedToken));
    }

    private boolean deleteLocked(Long ossId, String expectedToken) {
        LambdaQueryWrapper<SysOss> lock = Wrappers.lambdaQuery();
        lock.eq(SysOss::getOssId, ossId).last("FOR UPDATE");
        SysOss row = mapper.selectOne(lock);
        if (row == null) {
            return false;
        }
        SysOssExt ext = parseExt(row.getExt1());
        if (!OssBusinessDeletionCoordinator.PENDING.equals(ext.getDeleteStatus())
            || !Objects.equals(expectedToken, ext.getDeleteToken())) {
            return false;
        }

        clientProvider.byService(row.getService()).deleteObject(row.getFileName());
        LambdaQueryWrapper<SysOss> exactDelete = Wrappers.lambdaQuery();
        exactDelete.eq(SysOss::getOssId, ossId).eq(SysOss::getExt1, row.getExt1());
        if (mapper.delete(exactDelete) != 1) {
            throw new ServiceException("待删除附件记录删除失败");
        }
        CacheUtils.evict(CacheNames.SYS_OSS, ossId);
        return true;
    }

    private static SysOssExt parseExt(String ext1) {
        return StringUtils.isBlank(ext1) ? new SysOssExt() : JsonUtils.parseObject(ext1, SysOssExt.class);
    }
}
