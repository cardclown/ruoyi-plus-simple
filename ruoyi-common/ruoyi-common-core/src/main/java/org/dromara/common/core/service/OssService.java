package org.dromara.common.core.service;

import org.dromara.common.core.domain.dto.OssDTO;
import org.dromara.common.core.oss.TechnicalMediaType;

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
     * 通过 ossId 查询按当前 OSS 配置生成的访问地址
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
     * 批量查询文件元数据，并按当前 OSS 配置生成可用 URL。
     *
     * <p>返回地址是临时展示值，不是附件定位依据；调用方不得将其持久化为永久地址。</p>
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
     * 按业务字段声明的媒体类型绑定附件。
     *
     * <p>调用方必须根据图片、视频等业务字段生成映射；实现方在持有 OSS 行锁后再次校验
     * 文件元数据，并把技术类型与业务引用一并写入，避免并发绑定造成类型或归属不一致。</p>
     *
     * @param mediaTypes OSS ID 到业务字段所要求技术类型的映射
     * @param refType    业务引用类型
     * @param refId      业务引用 ID
     * @throws org.dromara.common.core.exception.ServiceException 文件类型、元数据或业务归属不合法时抛出
     */
    void bindMediaToBusiness(Map<Long, TechnicalMediaType> mediaTypes, String refType, String refId);

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
