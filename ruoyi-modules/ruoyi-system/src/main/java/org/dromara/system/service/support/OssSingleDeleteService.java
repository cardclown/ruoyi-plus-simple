package org.dromara.system.service.support;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import lombok.RequiredArgsConstructor;
import org.dromara.common.core.exception.ServiceException;
import org.dromara.common.core.service.OssReferenceDeleteHandler;
import org.dromara.common.core.utils.StringUtils;
import org.dromara.common.json.utils.JsonUtils;
import org.dromara.system.domain.SysOss;
import org.dromara.system.domain.SysOssExt;
import org.dromara.system.mapper.SysOssMapper;
import org.dromara.system.service.ISysOssService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 在独立事务中删除单个 OSS 附件。
 *
 * <p>临时附件由公共服务直接删除；已绑定附件由业务处理器先删除关联，再统一进入事务提交后的
 * OSS 清理流程。单项异常必须向外抛出，由批量服务转换为该 OSS ID 的失败结果。</p>
 */
@Service
@RequiredArgsConstructor
public class OssSingleDeleteService {

    private final SysOssMapper mapper;
    private final ISysOssService ossService;
    private final OssReferenceDeleteHandlerRegistry handlerRegistry;
    private final OssBusinessDeletionCoordinator deletionCoordinator;
    private final OssDeleteOperationContext operationContext;

    /**
     * 删除一个附件。
     *
     * @param ossId OSS ID
     * @return 可直接返回给前端的成功消息
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public String deleteOne(Long ossId) {
        // 已绑定附件不能先锁 OSS，否则会与“文章 -> 附件关系 -> OSS”的编辑锁顺序形成死锁。
        SysOss oss = mapper.selectById(ossId);
        if (oss == null) {
            throw new ServiceException("附件不存在");
        }

        SysOssExt metadata = parseMetadata(oss.getExt1());
        boolean referenceTypeBlank = StringUtils.isBlank(metadata.getRefType());
        boolean referenceIdBlank = StringUtils.isBlank(metadata.getRefId());
        if (referenceTypeBlank != referenceIdBlank) {
            throw new ServiceException("附件归属数据不完整，不能删除");
        }

        if (OssBusinessDeletionCoordinator.PENDING.equals(metadata.getDeleteStatus())) {
            validatePendingDeleteAccess(oss, metadata, referenceTypeBlank);
            return "附件正在删除";
        }

        if (referenceTypeBlank) {
            return deleteTemporaryFile(ossId);
        }

        OssReferenceDeleteHandler handler = handlerRegistry.require(metadata.getRefType());
        handler.deleteReference(ossId, metadata.getRefId());
        // 业务关联删除与待删除状态必须处于同一事务；只有提交成功后才允许清理对象存储。
        deletionCoordinator.schedule(Map.of(ossId, metadata.getRefId()), metadata.getRefType());
        return "删除成功";
    }

    private String deleteTemporaryFile(Long ossId) {
        SysOss oss = mapper.selectOne(Wrappers.<SysOss>lambdaQuery()
            .eq(SysOss::getOssId, ossId)
            .last("FOR UPDATE"));
        if (oss == null) {
            throw new ServiceException("附件不存在");
        }
        SysOssExt metadata = parseMetadata(oss.getExt1());
        boolean referenceTypeBlank = StringUtils.isBlank(metadata.getRefType());
        boolean referenceIdBlank = StringUtils.isBlank(metadata.getRefId());
        if (!referenceTypeBlank || !referenceIdBlank) {
            throw new ServiceException("附件归属已变化，请重试");
        }
        if (OssBusinessDeletionCoordinator.PENDING.equals(metadata.getDeleteStatus())) {
            validatePendingDeleteAccess(oss, metadata, true);
            return "附件正在删除";
        }
        if (!Boolean.TRUE.equals(metadata.getIsTemp())) {
            throw new ServiceException("附件不是可删除的临时文件");
        }
        boolean uploadedByCurrentUser = Objects.equals(oss.getCreateBy(), operationContext.currentUserId());
        if (!uploadedByCurrentUser && !operationContext.canManageOss()) {
            throw new ServiceException("无权删除该附件");
        }
        ossService.deleteByIds(List.of(oss.getOssId()));
        return "删除成功";
    }

    private void validatePendingDeleteAccess(SysOss oss, SysOssExt metadata, boolean unbound) {
        if (unbound) {
            boolean uploadedByCurrentUser = Objects.equals(oss.getCreateBy(), operationContext.currentUserId());
            if (!uploadedByCurrentUser && !operationContext.canManageOss()) {
                throw new ServiceException("无权删除该附件");
            }
            return;
        }
        handlerRegistry.require(metadata.getRefType()).validateDeleteAccess(metadata.getRefId());
    }

    private SysOssExt parseMetadata(String ext1) {
        if (StringUtils.isBlank(ext1)) {
            return new SysOssExt();
        }
        try {
            SysOssExt metadata = JsonUtils.parseObject(ext1, SysOssExt.class);
            if (metadata == null) {
                throw new ServiceException("附件元数据格式错误");
            }
            return metadata;
        } catch (ServiceException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw new ServiceException("附件元数据格式错误");
        }
    }
}
