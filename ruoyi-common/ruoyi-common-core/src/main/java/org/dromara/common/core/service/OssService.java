package org.dromara.common.core.service;

import org.dromara.common.core.domain.dto.OssDTO;

import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * 通用 OSS服务
 *
 * @author Lion Li
 */
public interface OssService {

    /**
     * 通过ossId查询对应的url
     *
     * @param ossIds ossId串逗号分隔
     * @return url串逗号分隔
     */
    String selectUrlByIds(String ossIds);

    /**
     * 通过ossId查询列表
     *
     * @param ossIds ossId串逗号分隔
     * @return 列表
     */
    List<OssDTO> selectByIds(String ossIds);

    /**
     * 通过 ossId 批量查询文件元数据
     *
     * @param ossIds ossId 集合
     * @return 文件元数据列表
     */
    List<OssDTO> selectByIds(Collection<Long> ossIds);

    /**
     * 批量查询文件元数据，并为私有桶生成当前可用 URL。
     *
     * @param ossIds ossId 集合
     * @return 当前可展示的文件元数据列表
     */
    List<OssDTO> resolveByIds(Collection<Long> ossIds);

    /**
     * 将临时附件绑定到业务记录
     *
     * @param ossIds  附件 ID 集合
     * @param refType 业务引用类型
     * @param refId   业务引用 ID
     */
    void bindToBusiness(Collection<Long> ossIds, String refType, String refId);

    /**
     * 强制删除附件
     *
     * @param ossIds 附件 ID 集合
     */
    void deleteByIds(Collection<Long> ossIds);

    /**
     * 在当前业务事务中标记精确归属的附件待删除，并仅在事务提交后删除对象和元数据。
     *
     * @param ossOwnerRefIds OSS ID 到预期业务引用 ID 的映射
     * @param refType 预期业务引用类型
     */
    void scheduleBusinessDeletion(Map<Long, String> ossOwnerRefIds, String refType);
}
