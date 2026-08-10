package org.dromara.content.domain.bo;

import com.fasterxml.jackson.annotation.JsonView;
import io.github.linpeilie.annotations.AutoMapper;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;
import org.dromara.common.core.validate.AddGroup;
import org.dromara.common.core.validate.EditGroup;
import org.dromara.common.core.xss.Xss;
import org.dromara.content.domain.ContentArticle;

import java.util.List;

@Data
@AutoMapper(target = ContentArticle.class, reverseConvertGenerate = false)
/**
 * 文章新增和修改共用的请求对象。
 */
public class ContentArticleBo {

    /** 新增请求可见字段视图，不包含由后台生成的文章 ID。 */
    public interface AddView {
    }

    /** 修改请求可见字段视图，在新增字段基础上额外接收文章 ID。 */
    public interface EditView extends AddView {
    }

    /** 文章 ID；仅修改请求接收，新增时由后台雪花 ID 策略生成。 */
    @JsonView(EditView.class)
    @NotNull(message = "文章ID不能为空", groups = EditGroup.class)
    private Long articleId;

    /** 文章标题，新增和修改均必填。 */
    @JsonView(AddView.class)
    @NotBlank(message = "文章标题不能为空", groups = {AddGroup.class, EditGroup.class})
    @Size(max = 200, message = "文章标题长度不能超过{max}个字符", groups = {AddGroup.class, EditGroup.class})
    @Xss(message = "文章标题不能包含脚本内容", groups = {AddGroup.class, EditGroup.class})
    private String title;

    /** 文章简介，可选，最长 500 个字符。 */
    @JsonView(AddView.class)
    @Size(max = 500, message = "文章简介长度不能超过{max}个字符", groups = {AddGroup.class, EditGroup.class})
    @Xss(message = "文章简介不能包含脚本内容", groups = {AddGroup.class, EditGroup.class})
    private String summary;

    /** Quill 富文本 HTML 正文，保存前由后台执行白名单过滤。 */
    @JsonView(AddView.class)
    @NotBlank(message = "文章正文不能为空", groups = {AddGroup.class, EditGroup.class})
    private String content;

    /** 分类字典编码，必须属于当前租户的文章分类字典。 */
    @JsonView(AddView.class)
    @NotNull(message = "文章分类不能为空", groups = {AddGroup.class, EditGroup.class})
    private Long categoryDictCode;

    /** 标签字典编码集合，后台去重并限制最多 10 个。 */
    @JsonView(AddView.class)
    @Size(max = 10, message = "文章标签最多选择{max}个", groups = {AddGroup.class, EditGroup.class})
    private List<@NotNull(message = "文章标签ID不能为空") Long> tagIds;

    /** 封面 OSS 文件 ID，后台校验文件存在且属于当前租户。 */
    @JsonView(AddView.class)
    private Long coverOssId;

    /** 发布状态：0 表示草稿，1 表示已发布。 */
    @JsonView(AddView.class)
    @NotBlank(message = "发布状态不能为空", groups = {AddGroup.class, EditGroup.class})
    @Pattern(regexp = "^[01]$", message = "发布状态只能为0或1", groups = {AddGroup.class, EditGroup.class})
    private String status;
}
