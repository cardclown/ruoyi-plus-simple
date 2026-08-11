package org.dromara.content.domain.vo;

import cn.idev.excel.annotation.ExcelIgnoreUnannotated;
import cn.idev.excel.annotation.ExcelProperty;
import io.github.linpeilie.annotations.AutoMapper;
import lombok.Data;
import org.dromara.common.excel.annotation.ExcelDictFormat;
import org.dromara.common.excel.convert.ExcelDictConvert;
import org.dromara.content.domain.ContentArticle;

import java.io.Serial;
import java.io.Serializable;
import java.util.Date;
import java.util.List;

/**
 * 文章视图对象 content_article
 *
 * @author Lion Li
 * @date 2026-08-07
 */
@Data
@ExcelIgnoreUnannotated
@AutoMapper(target = ContentArticle.class)
public class ContentArticleVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 文章 ID，作为响应标识并导出为 Excel 列。 */
    @ExcelProperty(value = "文章ID")
    private Long articleId;

    /** 文章标题，供前端展示并导出为 Excel 列。 */
    @ExcelProperty(value = "文章标题")
    private String title;

    /** 文章简介，供前端展示并导出为 Excel 列。 */
    @ExcelProperty(value = "文章简介")
    private String summary;

    /** Quill 富文本 HTML 正文，供前端详情展示并导出为 Excel 列。 */
    @ExcelProperty(value = "Quill富文本HTML正文")
    private String content;

    /** 分类字典编码，供前端回显并导出为 Excel 列。 */
    @ExcelProperty(value = "分类字典编码")
    private Long categoryDictCode;

    /** 标签字典编码集合，用于前端回显，不直接作为 Excel 列导出。 */
    private List<Long> tagIds;

    /** 文章图片 OSS 附件 ID 集合，按展示顺序排列。 */
    private List<Long> attachmentOssIds;

    /** 文章视频 OSS 附件 ID 集合，按展示顺序排列。 */
    private List<Long> videoOssIds;

    /** 发布状态，供前端展示并按字典转换后导出为 Excel 列。 */
    @ExcelProperty(value = "发布状态", converter = ExcelDictConvert.class)
    @ExcelDictFormat(readConverterExp = "0=草稿,1=已发布")
    private String status;

    /** 发布人用户 ID，由后台在首次发布时记录。 */
    @ExcelProperty(value = "发布人")
    private Long publishBy;

    /** 首次发布时间；已发布文章重复编辑时保持原值。 */
    @ExcelProperty(value = "发布时间")
    private Date publishTime;

    /** 创建时间，由后台审计机制填充。 */
    private Date createTime;

    /** 最后更新时间，由后台审计机制填充。 */
    private Date updateTime;
}
