package org.dromara.content.controller;

import com.fasterxml.jackson.annotation.JsonView;
import cn.dev33.satoken.annotation.SaCheckPermission;
import cn.dev33.satoken.annotation.SaMode;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.dromara.common.core.domain.R;
import org.dromara.common.core.validate.AddGroup;
import org.dromara.common.core.validate.EditGroup;
import org.dromara.common.excel.utils.ExcelUtil;
import org.dromara.common.idempotent.annotation.RepeatSubmit;
import org.dromara.common.log.annotation.Log;
import org.dromara.common.log.enums.BusinessType;
import org.dromara.common.mybatis.core.page.PageQuery;
import org.dromara.common.mybatis.core.page.TableDataInfo;
import org.dromara.common.web.core.BaseController;
import org.dromara.content.domain.bo.ContentArticleBo;
import org.dromara.content.domain.bo.ContentArticleQuery;
import org.dromara.content.domain.vo.ContentArticleDictOptionVo;
import org.dromara.content.domain.vo.ContentArticleVo;
import org.dromara.content.service.IContentArticleService;
import org.dromara.content.service.support.ContentArticleDictionaryService;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 文章管理
 *
 * @author Lion Li
 * @date 2026-08-07
 */
@Validated
@RequiredArgsConstructor
@RestController
@RequestMapping("/content/article")
public class ContentArticleController extends BaseController {

    private final IContentArticleService contentArticleService;
    private final ContentArticleDictionaryService dictionaryService;

    /**
     * 查询当前租户的文章分类字典选项。
     */
    @SaCheckPermission(value = {
        "content:article:list",
        "content:article:query",
        "content:article:add",
        "content:article:edit"
    }, mode = SaMode.OR)
    @GetMapping("/category-options")
    public R<List<ContentArticleDictOptionVo>> categoryOptions() {
        return R.ok(dictionaryService.categoryOptions());
    }

    /**
     * 查询当前租户的文章标签字典选项。
     */
    @SaCheckPermission(value = {
        "content:article:list",
        "content:article:query",
        "content:article:add",
        "content:article:edit"
    }, mode = SaMode.OR)
    @GetMapping("/tag-options")
    public R<List<ContentArticleDictOptionVo>> tagOptions() {
        return R.ok(dictionaryService.tagOptions());
    }

    /**
     * 查询文章列表
     */
    @SaCheckPermission("content:article:list")
    @GetMapping("/list")
    public TableDataInfo<ContentArticleVo> list(@ParameterObject ContentArticleQuery query,
                                                @ParameterObject PageQuery pageQuery) {
        return contentArticleService.queryPageList(query, pageQuery);
    }

    /**
     * 导出文章列表
     */
    @SaCheckPermission("content:article:export")
    @Log(title = "文章", businessType = BusinessType.EXPORT)
    @PostMapping("/export")
    public void export(@ParameterObject ContentArticleQuery query, HttpServletResponse response) {
        List<ContentArticleVo> list = contentArticleService.queryList(query);
        ExcelUtil.exportExcel(list, "文章", ContentArticleVo.class, response);
    }

    /**
     * 获取文章详细信息
     *
     * @param articleId 文章ID
     */
    @SaCheckPermission("content:article:query")
    @GetMapping("/{articleId}")
    public R<ContentArticleVo> getInfo(@NotNull(message = "文章ID不能为空")
                                       @PathVariable Long articleId) {
        return R.ok(contentArticleService.queryById(articleId));
    }

    /**
     * 新增文章
     */
    @SaCheckPermission("content:article:add")
    @Log(title = "文章", businessType = BusinessType.INSERT)
    @RepeatSubmit
    @PostMapping
    public R<Void> add(@JsonView(ContentArticleBo.AddView.class)
                       @Validated(AddGroup.class) @RequestBody ContentArticleBo bo) {
        return toAjax(contentArticleService.insertByBo(bo));
    }

    /**
     * 修改文章
     */
    @SaCheckPermission("content:article:edit")
    @Log(title = "文章", businessType = BusinessType.UPDATE)
    @RepeatSubmit
    @PutMapping
    public R<Void> edit(@JsonView(ContentArticleBo.EditView.class)
                        @Validated(EditGroup.class) @RequestBody ContentArticleBo bo) {
        return toAjax(contentArticleService.updateByBo(bo));
    }

    /**
     * 逻辑删除文章
     *
     * @param articleIds 文章ID集合
     */
    @SaCheckPermission("content:article:remove")
    @Log(title = "文章", businessType = BusinessType.DELETE)
    @DeleteMapping("/{articleIds}")
    public R<Void> remove(@NotEmpty(message = "文章ID不能为空")
                          @PathVariable Long[] articleIds) {
        return toAjax(contentArticleService.deleteWithValidByIds(List.of(articleIds), true));
    }
}
