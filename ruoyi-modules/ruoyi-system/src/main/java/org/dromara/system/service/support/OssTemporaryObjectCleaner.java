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
import org.dromara.common.tenant.helper.TenantHelper;
import org.dromara.system.domain.SysOss;
import org.dromara.system.domain.SysOssExt;
import org.dromara.system.mapper.SysOssMapper;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Date;
import java.util.Set;

/**
 * 在独立事务中清理单个未绑定的临时 OSS 对象及其元数据。
 *
 * <p>先锁定并复核元数据，再按稳定对象 Key 删除存储对象，避免 OSS 地址变化后遗留垃圾文件。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OssTemporaryObjectCleaner {

    private static final Set<String> CLEANABLE_FILE_TYPES = Set.of("IMAGE", "VIDEO");

    private final SysOssMapper mapper;
    private final OssClientProvider clientProvider;

    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public boolean cleanCandidate(Long ossId, Date cutoff) {
        return TenantHelper.ignore(() -> cleanLocked(ossId, cutoff));
    }

    private boolean cleanLocked(Long ossId, Date cutoff) {
        LambdaQueryWrapper<SysOss> lockQuery = Wrappers.lambdaQuery();
        lockQuery.eq(SysOss::getOssId, ossId)
            .lt(SysOss::getCreateTime, cutoff)
            .last("FOR UPDATE");
        SysOss oss = mapper.selectOne(lockQuery);
        if (oss == null) {
            return false;
        }
        SysOssExt ext;
        try {
            ext = JsonUtils.parseObject(oss.getExt1(), SysOssExt.class);
        } catch (RuntimeException exception) {
            log.warn("Unable to parse temporary OSS metadata: {}", ossId, exception);
            return false;
        }
        if (!isCleanable(ext)) {
            return false;
        }
        clientProvider.byService(oss.getService()).deleteObject(oss.getFileName());
        LambdaQueryWrapper<SysOss> deleteQuery = Wrappers.lambdaQuery();
        deleteQuery.eq(SysOss::getOssId, ossId)
            .eq(SysOss::getExt1, oss.getExt1())
            .lt(SysOss::getCreateTime, cutoff);
        if (mapper.delete(deleteQuery) != 1) {
            throw new ServiceException("临时附件记录删除失败");
        }
        CacheUtils.evict(CacheNames.SYS_OSS, ossId);
        return true;
    }

    private static boolean isCleanable(SysOssExt ext) {
        return ext != null
            && Boolean.TRUE.equals(ext.getIsTemp())
            && "userUpload".equals(ext.getSource())
            && CLEANABLE_FILE_TYPES.contains(ext.getFileType())
            && StringUtils.isBlank(ext.getRefId())
            && StringUtils.isBlank(ext.getRefType());
    }
}
