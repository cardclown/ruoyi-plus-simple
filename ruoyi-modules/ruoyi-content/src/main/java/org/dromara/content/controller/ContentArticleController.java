package org.dromara.content.controller;

import com.fasterxml.jackson.annotation.JsonView;
import cn.dev33.satoken.annotation.SaCheckPermission;
import cn.dev33.satoken.annotation.SaMode;
import jakarta.servlet.http.HttpServletResponse;
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
import org.dromara.content.domain.bo.ContentArticleIdsBo;
import org.dromara.content.domain.bo.ContentArticleQuery;
import org.dromara.content.domain.bo.ContentArticleStatusBo;
import org.dromara.content.domain.vo.ContentArticleDictOptionVo;
import org.dromara.content.domain.vo.ContentArticleVo;
import org.dromara.content.service.IContentArticleService;
import org.dromara.content.service.support.ContentArticleDictionaryService;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.validation.annotation.Validated;
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
     * 查询当前租户固定字典中的文章分类选项。
     *
     * @return 文章分类字典选项
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
     * 查询当前租户固定字典中的文章标签选项。
     *
     * @return 文章标签字典选项
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
     * 分页查询当前数据权限范围内的文章。
     *
     * @param query 文章业务筛选条件
     * @param pageQuery 分页与排序参数
     * @return 分页文章数据
     */
    @SaCheckPermission("content:article:list")
    @GetMapping("/list")
    public TableDataInfo<ContentArticleVo> list(@ParameterObject ContentArticleQuery query,
                                                @ParameterObject PageQuery pageQuery) {
        return contentArticleService.queryPageList(query, pageQuery);
    }

    /**
     * 导出当前数据权限范围内的文章，仅使用标题、分类字典编码和状态三个筛选字段。
     *
     * @param query 文章业务筛选条件，仅使用 title、categoryDictCode 和 status
     * @param response 导出文件写入响应
     */
    @SaCheckPermission("content:article:export")
    @Log(title = "文章", businessType = BusinessType.EXPORT)
    @PostMapping("/export")
    public void export(@ParameterObject ContentArticleQuery query, HttpServletResponse response) {
        List<ContentArticleVo> list = contentArticleService.queryList(query);
        ExcelUtil.exportExcel(list, "文章", ContentArticleVo.class, response);
    }

    /**
     * 查询当前租户和数据权限范围内的文章详情。
     *
     * @param articleId 文章 ID
     * @return 文章详情
     */
    @SaCheckPermission("content:article:query")
    @GetMapping("/{articleId}")
    public R<ContentArticleVo> getInfo(@NotNull(message = "文章ID不能为空")
                                       @PathVariable Long articleId) {
        return R.ok(contentArticleService.queryById(articleId));
    }

    /**
     * 新增文章。请求使用 AddView 控制可绑定字段，并使用 AddGroup 执行字段校验；客户端传入的文章 ID 不进入绑定结果。
     *
     * @param bo 新增文章业务数据
     * @return 新增结果
     */
    @SaCheckPermission("content:article:add")
    @Log(title = "文章", businessType = BusinessType.INSERT)
    @RepeatSubmit
    @PostMapping
    // JsonView 控制可绑定字段，Validation Group 控制字段校验。
    public R<Void> add(@JsonView(ContentArticleBo.AddView.class)
                       @Validated(AddGroup.class) @RequestBody ContentArticleBo bo) {
        return toAjax(contentArticleService.insertByBo(bo));
    }

    /**
     * 修改文章。请求使用 EditView 控制可绑定字段，并使用 EditGroup 执行字段校验；文章 ID 必填。
     *
     * @param bo 修改文章业务数据
     * @return 修改结果
     */
    @SaCheckPermission("content:article:edit")
    @Log(title = "文章", businessType = BusinessType.UPDATE)
    @RepeatSubmit
    @PutMapping
    // JsonView 控制可绑定字段，Validation Group 控制字段校验。
    public R<Void> edit(@JsonView(ContentArticleBo.EditView.class)
                        @Validated(EditGroup.class) @RequestBody ContentArticleBo bo) {
        return toAjax(contentArticleService.updateByBo(bo));
    }

    /**
     * 发布或撤回文章，请求仅接收文章 ID 和目标状态。
     *
     * @param bo 文章状态修改请求
     * @return 状态修改结果
     */
    @SaCheckPermission("content:article:edit")
    @Log(title = "文章", businessType = BusinessType.UPDATE)
    @RepeatSubmit
    @PostMapping("/changeStatus")
    public R<Void> changeStatus(@Validated @RequestBody ContentArticleStatusBo bo) {
        return toAjax(contentArticleService.changeStatus(bo.getArticleId(), bo.getStatus()));
    }

    /**
     * 根据 JSON 数组批量逻辑删除文章，并校验数据权限和文章存在性。
     *
     * @param bo 文章 ID 批量请求
     * @return 删除结果
     */
    @SaCheckPermission("content:article:remove")
    @Log(title = "文章", businessType = BusinessType.DELETE)
    @RepeatSubmit
    @PostMapping("/delete")
    public R<Void> remove(@Validated @RequestBody ContentArticleIdsBo bo) {
        return toAjax(contentArticleService.deleteWithValidByIds(bo.getArticleIds(), true));
    }

    /**
     * 物理删除已逻辑删除的文章及其独占媒体。
     *
     * @param bo 文章 ID 批量请求
     * @return 删除结果
     */
    @SaCheckPermission("content:article:remove")
    @Log(title = "文章", businessType = BusinessType.CLEAN)
    @RepeatSubmit
    @PostMapping("/physicalDelete")
    public R<Void> physicalRemove(@Validated @RequestBody ContentArticleIdsBo bo) {
        contentArticleService.physicalDeleteByIds(bo.getArticleIds());
        return R.ok();
    }
}
