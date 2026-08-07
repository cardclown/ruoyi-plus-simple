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
public class ContentArticleBo {

    public interface AddView {
    }

    public interface EditView extends AddView {
    }

    @JsonView(EditView.class)
    @NotNull(message = "文章ID不能为空", groups = EditGroup.class)
    private Long articleId;

    @JsonView(AddView.class)
    @NotBlank(message = "文章标题不能为空", groups = {AddGroup.class, EditGroup.class})
    @Size(max = 200, message = "文章标题长度不能超过{max}个字符", groups = {AddGroup.class, EditGroup.class})
    @Xss(message = "文章标题不能包含脚本内容", groups = {AddGroup.class, EditGroup.class})
    private String title;

    @JsonView(AddView.class)
    @Size(max = 500, message = "文章简介长度不能超过{max}个字符", groups = {AddGroup.class, EditGroup.class})
    @Xss(message = "文章简介不能包含脚本内容", groups = {AddGroup.class, EditGroup.class})
    private String summary;

    @JsonView(AddView.class)
    @NotBlank(message = "文章正文不能为空", groups = {AddGroup.class, EditGroup.class})
    private String content;

    @JsonView(AddView.class)
    @NotNull(message = "文章分类不能为空", groups = {AddGroup.class, EditGroup.class})
    private Long categoryDictCode;

    @JsonView(AddView.class)
    @Size(max = 10, message = "文章标签最多选择{max}个", groups = {AddGroup.class, EditGroup.class})
    private List<@NotNull(message = "文章标签ID不能为空") Long> tagIds;

    @JsonView(AddView.class)
    private Long coverOssId;

    @JsonView(AddView.class)
    @NotBlank(message = "发布状态不能为空", groups = {AddGroup.class, EditGroup.class})
    @Pattern(regexp = "^[01]$", message = "发布状态只能为0或1", groups = {AddGroup.class, EditGroup.class})
    private String status;
}
