package org.dromara.content.service.support;

import lombok.RequiredArgsConstructor;
import org.dromara.common.core.domain.dto.DictDataDTO;
import org.dromara.common.core.exception.ServiceException;
import org.dromara.common.core.service.DictService;
import org.dromara.content.domain.vo.ContentArticleDictOptionVo;
import org.dromara.content.enums.ContentArticleDictType;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 文章字典适配与业务校验。
 */
@Service
@RequiredArgsConstructor
public class ContentArticleDictionaryService {

    private static final int MAX_TAG_COUNT = 10;

    private final DictService dictService;

    /**
     * 从当前租户的文章分类字典读取可选项。
     *
     * @return 当前租户可用的分类选项
     */
    public List<ContentArticleDictOptionVo> categoryOptions() {
        return listOptions(ContentArticleDictType.CATEGORY);
    }

    /**
     * 从当前租户的文章标签字典读取可选项。
     *
     * @return 当前租户可用的标签选项
     */
    public List<ContentArticleDictOptionVo> tagOptions() {
        return listOptions(ContentArticleDictType.TAG);
    }

    /**
     * 将框架字典 DTO 转换为文章模块选项 VO，避免向外暴露文章选择器不需要的字典字段。
     *
     * @param type 文章字典类型
     * @return 当前租户中该类型的文章字典选项
     */
    public List<ContentArticleDictOptionVo> listOptions(ContentArticleDictType type) {
        List<DictDataDTO> data = getDictData(type);
        List<ContentArticleDictOptionVo> result = new ArrayList<>(data.size());
        for (DictDataDTO item : data) {
            ContentArticleDictOptionVo option = new ContentArticleDictOptionVo();
            option.setDictCode(item.getDictCode());
            option.setDictSort(item.getDictSort());
            option.setDictLabel(item.getDictLabel());
            option.setDictValue(item.getDictValue());
            option.setCssClass(item.getCssClass());
            option.setListClass(item.getListClass());
            option.setIsDefault(item.getIsDefault());
            result.add(option);
        }
        return result;
    }

    /**
     * 校验分类属于当前租户，拒绝空标签 ID 和跨租户字典编码，并按输入顺序去重标签且限制最多 10 个。
     *
     * @param categoryDictCode 分类字典编码
     * @param tagIds 标签字典编码列表，可为空列表
     * @return 去重且保持输入顺序的标签字典编码列表
     */
    public List<Long> validateAndNormalize(Long categoryDictCode, List<Long> tagIds) {
        if (!dictCodes(ContentArticleDictType.CATEGORY).contains(categoryDictCode)) {
            throw new ServiceException("文章分类不存在或不属于当前租户");
        }
        if (tagIds == null || tagIds.isEmpty()) {
            return Collections.emptyList();
        }

        LinkedHashSet<Long> normalized = new LinkedHashSet<>(tagIds);
        if (normalized.contains(null)) {
            throw new ServiceException("文章标签ID不能为空");
        }
        if (normalized.size() > MAX_TAG_COUNT) {
            throw new ServiceException("文章标签最多选择10个");
        }

        Set<Long> available = dictCodes(ContentArticleDictType.TAG);
        List<Long> invalid = normalized.stream().filter(id -> !available.contains(id)).toList();
        if (!invalid.isEmpty()) {
            throw new ServiceException("文章标签不存在或不属于当前租户: " + invalid);
        }
        return new ArrayList<>(normalized);
    }

    private Set<Long> dictCodes(ContentArticleDictType type) {
        Set<Long> codes = new HashSet<>();
        for (DictDataDTO item : getDictData(type)) {
            if (item.getDictCode() != null) {
                codes.add(item.getDictCode());
            }
        }
        return codes;
    }

    private List<DictDataDTO> getDictData(ContentArticleDictType type) {
        List<DictDataDTO> data = dictService.getDictData(type.getDictType());
        return data == null ? Collections.emptyList() : data;
    }
}
