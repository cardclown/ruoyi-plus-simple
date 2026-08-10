package org.dromara.system.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.convert.Convert;
import cn.hutool.core.util.ObjectUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.dromara.common.core.constant.CacheNames;
import org.dromara.common.core.domain.dto.OssDTO;
import org.dromara.common.core.exception.ServiceException;
import org.dromara.common.core.service.OssService;
import org.dromara.common.core.utils.MapstructUtils;
import org.dromara.common.core.utils.SpringUtils;
import org.dromara.common.core.utils.StreamUtils;
import org.dromara.common.core.utils.StringUtils;
import org.dromara.common.core.utils.file.FileUtils;
import org.dromara.common.json.utils.JsonUtils;
import org.dromara.common.mybatis.core.page.PageQuery;
import org.dromara.common.mybatis.core.page.TableDataInfo;
import org.dromara.common.redis.utils.CacheUtils;
import org.dromara.common.oss.core.OssClient;
import org.dromara.common.oss.entity.UploadResult;
import org.dromara.common.oss.enums.AccessPolicyType;
import org.dromara.system.domain.SysOss;
import org.dromara.system.domain.SysOssExt;
import org.dromara.system.domain.bo.SysOssBo;
import org.dromara.system.domain.vo.SysOssVo;
import org.dromara.system.mapper.SysOssMapper;
import org.dromara.system.service.ISysOssService;
import org.dromara.system.service.support.OssClientProvider;
import org.jetbrains.annotations.NotNull;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 文件上传 服务层实现
 *
 * @author Lion Li
 */
@RequiredArgsConstructor
@Service
public class SysOssServiceImpl implements ISysOssService, OssService {

    private final SysOssMapper baseMapper;
    private final OssClientProvider ossClientProvider;

    /**
     * 查询OSS对象存储列表
     *
     * @param bo        OSS对象存储分页查询对象
     * @param pageQuery 分页查询实体类
     * @return 结果
     */
    @Override
    public TableDataInfo<SysOssVo> queryPageList(SysOssBo bo, PageQuery pageQuery) {
        LambdaQueryWrapper<SysOss> lqw = buildQueryWrapper(bo);
        Page<SysOssVo> result = baseMapper.selectVoPage(pageQuery.build(), lqw);
        List<SysOssVo> filterResult = StreamUtils.toList(result.getRecords(), this::matchingUrl);
        result.setRecords(filterResult);
        return TableDataInfo.build(result);
    }

    /**
     * 根据一组 ossIds 获取对应的 SysOssVo 列表
     *
     * @param ossIds 一组文件在数据库中的唯一标识集合
     * @return 包含 SysOssVo 对象的列表
     */
    @Override
    public List<SysOssVo> listByIds(Collection<Long> ossIds) {
        List<SysOssVo> list = new ArrayList<>();
        List<Long> ids = distinctIds(ossIds);
        if (ids.isEmpty()) {
            return list;
        }
        Map<Long, SysOssVo> voById = baseMapper.selectVoByIds(ids).stream()
            .collect(Collectors.toMap(SysOssVo::getOssId, Function.identity(), (left, right) -> left));
        for (Long id : ids) {
            SysOssVo vo = voById.get(id);
            if (ObjectUtil.isNotNull(vo)) {
                try {
                    list.add(this.matchingUrl(vo));
                } catch (Exception ignored) {
                    // 如果oss异常无法连接则将数据直接返回
                    list.add(vo);
                }
            }
        }
        return list;
    }

    /**
     * 根据一组 ossIds 获取对应文件的 URL 列表
     *
     * @param ossIds 以逗号分隔的 ossId 字符串
     * @return 以逗号分隔的文件 URL 字符串
     */
    @Override
    public String selectUrlByIds(String ossIds) {
        List<String> list = new ArrayList<>();
        SysOssServiceImpl ossService = SpringUtils.getAopProxy(this);
        for (Long id : StringUtils.splitTo(ossIds, Convert::toLong)) {
            SysOssVo vo = ossService.getById(id);
            if (ObjectUtil.isNotNull(vo)) {
                try {
                    list.add(this.matchingUrl(vo).getUrl());
                } catch (Exception ignored) {
                    // 如果oss异常无法连接则将数据直接返回
                    list.add(vo.getUrl());
                }
            }
        }
        return StringUtils.joinComma(list);
    }

    @Override
    public List<OssDTO> selectByIds(String ossIds) {
        List<OssDTO> list = new ArrayList<>();
        for (Long id : StringUtils.splitTo(ossIds, Convert::toLong)) {
            SysOssVo vo = SpringUtils.getAopProxy(this).getById(id);
            if (ObjectUtil.isNotNull(vo)) {
                try {
                    vo.setUrl(this.matchingUrl(vo).getUrl());
                    list.add(BeanUtil.toBean(vo, OssDTO.class));
                } catch (Exception ignored) {
                    // 如果oss异常无法连接则将数据直接返回
                    list.add(BeanUtil.toBean(vo, OssDTO.class));
                }
            }
        }
        return list;
    }

    @Override
    public List<OssDTO> selectByIds(Collection<Long> ossIds) {
        List<Long> ids = distinctIds(ossIds);
        if (ids.isEmpty()) {
            return new ArrayList<>();
        }
        Map<Long, SysOssVo> voById = baseMapper.selectVoByIds(ids).stream()
            .collect(Collectors.toMap(SysOssVo::getOssId, Function.identity(), (left, right) -> left));
        List<OssDTO> result = new ArrayList<>();
        for (Long id : ids) {
            SysOssVo vo = voById.get(id);
            if (ObjectUtil.isNotNull(vo)) {
                result.add(toDto(vo));
            }
        }
        return result;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void bindToBusiness(Collection<Long> ossIds, String refType, String refId) {
        List<Long> ids = distinctIds(ossIds);
        if (ids.isEmpty()) {
            return;
        }
        List<SysOss> ossList = baseMapper.selectByIds(ids);
        if (ossList.size() != ids.size()) {
            throw new ServiceException("部分附件不存在或不属于当前租户");
        }
        for (SysOss oss : ossList) {
            SysOssExt ext = parseExt(oss.getExt1());
            ext.setRefType(refType);
            ext.setRefId(refId);
            ext.setIsTemp(false);
            oss.setExt1(JsonUtils.toJsonString(ext));
        }
        if (!baseMapper.updateBatchById(ossList)) {
            throw new ServiceException("附件绑定失败");
        }
        ossList.forEach(oss -> CacheUtils.evict(CacheNames.SYS_OSS, oss.getOssId()));
    }

    private LambdaQueryWrapper<SysOss> buildQueryWrapper(SysOssBo bo) {
        Map<String, Object> params = bo.getParams();
        LambdaQueryWrapper<SysOss> lqw = Wrappers.lambdaQuery();
        lqw.like(StringUtils.isNotBlank(bo.getFileName()), SysOss::getFileName, bo.getFileName());
        lqw.like(StringUtils.isNotBlank(bo.getOriginalName()), SysOss::getOriginalName, bo.getOriginalName());
        lqw.eq(StringUtils.isNotBlank(bo.getFileSuffix()), SysOss::getFileSuffix, bo.getFileSuffix());
        lqw.eq(StringUtils.isNotBlank(bo.getUrl()), SysOss::getUrl, bo.getUrl());
        lqw.between(params.get("beginCreateTime") != null && params.get("endCreateTime") != null,
            SysOss::getCreateTime, params.get("beginCreateTime"), params.get("endCreateTime"));
        lqw.eq(ObjectUtil.isNotNull(bo.getCreateBy()), SysOss::getCreateBy, bo.getCreateBy());
        lqw.eq(StringUtils.isNotBlank(bo.getService()), SysOss::getService, bo.getService());
        lqw.orderByAsc(SysOss::getOssId);
        return lqw;
    }

    /**
     * 根据 ossId 从缓存或数据库中获取 SysOssVo 对象
     *
     * @param ossId 文件在数据库中的唯一标识
     * @return SysOssVo 对象，包含文件信息
     */
    @Cacheable(cacheNames = CacheNames.SYS_OSS, key = "#ossId")
    @Override
    public SysOssVo getById(Long ossId) {
        return baseMapper.selectVoById(ossId);
    }


    /**
     * 文件下载方法，支持一次性下载完整文件
     *
     * @param ossId    OSS对象ID
     * @param response HttpServletResponse对象，用于设置响应头和向客户端发送文件内容
     */
    @Override
    public void download(Long ossId, HttpServletResponse response) throws IOException {
        SysOssVo sysOss = SpringUtils.getAopProxy(this).getById(ossId);
        if (ObjectUtil.isNull(sysOss)) {
            throw new ServiceException("文件数据不存在!");
        }
        FileUtils.setAttachmentResponseHeader(response, sysOss.getOriginalName());
        response.setContentType(MediaType.APPLICATION_OCTET_STREAM_VALUE + "; charset=UTF-8");
        OssClient storage = ossClientProvider.byService(sysOss.getService());
        storage.download(sysOss.getFileName(), response.getOutputStream(), response::setContentLengthLong);
    }

    /**
     * 上传 MultipartFile 到对象存储服务，并保存文件信息到数据库
     *
     * @param file 要上传的 MultipartFile 对象
     * @return 上传成功后的 SysOssVo 对象，包含文件信息
     * @throws ServiceException 如果上传过程中发生异常，则抛出 ServiceException 异常
     */
    @Override
    public SysOssVo upload(MultipartFile file) {
        if (ObjectUtil.isNull(file) || file.isEmpty()) {
            throw new ServiceException("上传文件不能为空");
        }
        String originalfileName = file.getOriginalFilename();
        String suffix = StringUtils.substring(originalfileName, originalfileName.lastIndexOf("."), originalfileName.length());
        OssClient storage = ossClientProvider.current();
        UploadResult uploadResult = null;
        try (InputStream inputStream = file.getInputStream()) {
            uploadResult = storage.uploadSuffix(inputStream, suffix, file.getSize(), file.getContentType());
        } catch (IOException e) {
            if (uploadResult != null) {
                deleteUploadedObject(storage, uploadResult, e);
            }
            ServiceException exception = new ServiceException("读取上传文件失败");
            exception.initCause(e);
            throw exception;
        }
        SysOssExt ext1 = new SysOssExt();
        ext1.setFileSize(file.getSize());
        ext1.setContentType(file.getContentType());
        ext1.setIsTemp(true);
        // 保存文件信息
        return buildResultEntity(originalfileName, suffix, storage, uploadResult, ext1);
    }

    /**
     * 上传文件到对象存储服务，并保存文件信息到数据库
     *
     * @param file 要上传的文件对象
     * @return 上传成功后的 SysOssVo 对象，包含文件信息
     */
    @Override
    public SysOssVo upload(File file) {
        if (ObjectUtil.isNull(file) || !file.isFile() || file.length() <= 0) {
            throw new ServiceException("上传文件不能为空");
        }
        String originalfileName = file.getName();
        String suffix = StringUtils.substring(originalfileName, originalfileName.lastIndexOf("."), originalfileName.length());
        OssClient storage = ossClientProvider.current();
        long length = file.length();
        UploadResult uploadResult = storage.uploadSuffix(file, suffix);
        SysOssExt ext1 = new SysOssExt();
        ext1.setFileSize(length);
        ext1.setIsTemp(true);
        // 保存文件信息
        return buildResultEntity(originalfileName, suffix, storage, uploadResult, ext1);
    }

    @NotNull
    private SysOssVo buildResultEntity(String originalfileName, String suffix, OssClient storage,
                                       UploadResult uploadResult, SysOssExt ext1) {
        SysOss oss = new SysOss();
        try {
            oss.setUrl(uploadResult.getUrl());
            oss.setFileSuffix(suffix);
            oss.setFileName(uploadResult.getFilename());
            oss.setOriginalName(originalfileName);
            oss.setService(storage.getConfigKey());
            oss.setExt1(JsonUtils.toJsonString(ext1));
            persistResultEntity(oss);
        } catch (RuntimeException exception) {
            deleteUploadedObject(storage, uploadResult, exception);
            throw exception;
        }
        SysOssVo sysOssVo = MapstructUtils.convert(oss, SysOssVo.class);
        return this.matchingUrl(sysOssVo);
    }

    private void persistResultEntity(SysOss oss) {
        if (baseMapper.insert(oss) != 1) {
            throw new ServiceException("文件信息保存失败");
        }
    }

    private void deleteUploadedObject(OssClient storage, UploadResult uploadResult, Throwable originalException) {
        try {
            storage.delete(uploadResult.getUrl());
        } catch (RuntimeException deleteException) {
            originalException.addSuppressed(deleteException);
        }
    }

    /**
     * 删除OSS对象存储
     *
     * @param ids     OSS对象ID串
     * @param isValid 判断是否需要校验
     * @return 结果
    */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public Boolean deleteWithValidByIds(Collection<Long> ids, Boolean isValid) {
        List<Long> distinctIds = distinctIds(ids);
        if (distinctIds.isEmpty()) {
            return false;
        }
        if (Boolean.TRUE.equals(isValid)) {
            List<SysOss> ossList = baseMapper.selectByIds(distinctIds);
            for (SysOss oss : ossList) {
                SysOssExt ext = parseExt(oss.getExt1());
                if (Boolean.FALSE.equals(ext.getIsTemp()) && StringUtils.isNotBlank(ext.getRefType())) {
                    throw new ServiceException("附件已被业务使用，不能直接删除");
                }
            }
        }
        deleteByIds(distinctIds);
        return true;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteByIds(Collection<Long> ossIds) {
        List<Long> ids = distinctIds(ossIds);
        if (ids.isEmpty()) {
            return;
        }
        List<SysOss> ossList = baseMapper.selectByIds(ids);
        if (ossList.size() != ids.size()) {
            throw new ServiceException("部分附件不存在或不属于当前租户");
        }
        for (SysOss oss : ossList) {
            ossClientProvider.byService(oss.getService()).delete(oss.getUrl());
        }
        if (baseMapper.deleteByIds(ids) != ids.size()) {
            throw new ServiceException("附件删除失败");
        }
        ossList.forEach(oss -> CacheUtils.evict(CacheNames.SYS_OSS, oss.getOssId()));
    }

    /**
     * 桶类型为 private 的URL 修改为临时URL时长为120s
     *
     * @param oss OSS对象
     * @return oss 匹配Url的OSS对象
     */
    private SysOssVo matchingUrl(SysOssVo oss) {
        OssClient storage = ossClientProvider.byService(oss.getService());
        // 仅修改桶类型为 private 的URL，临时URL时长为120s
        if (AccessPolicyType.PRIVATE == storage.getAccessPolicy()) {
            oss.setUrl(storage.createPresignedGetUrl(oss.getFileName(), Duration.ofSeconds(120)));
        }
        return oss;
    }

    private List<Long> distinctIds(Collection<Long> ossIds) {
        if (ossIds == null || ossIds.isEmpty()) {
            return new ArrayList<>();
        }
        return ossIds.stream().filter(ObjectUtil::isNotNull)
            .collect(Collectors.toCollection(LinkedHashSet::new)).stream().toList();
    }

    private OssDTO toDto(SysOssVo vo) {
        OssDTO dto = BeanUtil.toBean(vo, OssDTO.class);
        SysOssExt ext = parseExt(vo.getExt1());
        dto.setFileSize(ext.getFileSize());
        dto.setContentType(ext.getContentType());
        dto.setBizType(ext.getBizType());
        dto.setRefId(ext.getRefId());
        dto.setRefType(ext.getRefType());
        dto.setIsTemp(ext.getIsTemp());
        return dto;
    }

    private SysOssExt parseExt(String ext1) {
        return StringUtils.isBlank(ext1) ? new SysOssExt() : JsonUtils.parseObject(ext1, SysOssExt.class);
    }
}
