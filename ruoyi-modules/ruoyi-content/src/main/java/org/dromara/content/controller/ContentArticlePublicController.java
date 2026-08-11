package org.dromara.content.controller;

import cn.dev33.satoken.annotation.SaIgnore;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.dromara.common.core.domain.R;
import org.dromara.common.mybatis.core.page.PageQuery;
import org.dromara.common.mybatis.core.page.TableDataInfo;
import org.dromara.content.domain.bo.ContentArticleIdsBo;
import org.dromara.content.domain.bo.ContentArticleQuery;
import org.dromara.content.domain.vo.ContentArticleMediaGroupVo;
import org.dromara.content.domain.vo.ContentArticlePublicVo;
import org.dromara.content.service.IContentArticlePublicFacade;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 中安建设官网匿名文章查询接口。
 */
@SaIgnore
@Validated
@RequiredArgsConstructor
@RestController
@RequestMapping("/content/article/public")
public class ContentArticlePublicController {

    private final IContentArticlePublicFacade articlePublicFacade;

    /**
     * 分页查询中安建设已发布文章；请求中的状态参数不会参与查询。
     */
    @GetMapping("/list")
    public TableDataInfo<ContentArticlePublicVo> list(
            @ParameterObject ContentArticleQuery query,
            @ParameterObject PageQuery pageQuery) {
        return articlePublicFacade.queryPage(query, pageQuery);
    }

    /**
     * 查询中安建设单篇已发布文章。
     */
    @GetMapping("/{articleId}")
    public R<ContentArticlePublicVo> getInfo(
            @NotNull(message = "文章ID不能为空") @PathVariable Long articleId) {
        return R.ok(articlePublicFacade.queryById(articleId));
    }

    /**
     * 批量解析已发布文章当前关联的全部媒体 URL。
     *
     * <p>一个请求可以同时解析列表页或详情页需要的文章，返回结果按文章分组，避免 N+1 请求。</p>
     *
     * @param bo 文章 ID 批量请求
     * @return 按文章分组的当前媒体元数据
     */
    @PostMapping("/media")
    public R<List<ContentArticleMediaGroupVo>> media(
            @Validated @RequestBody ContentArticleIdsBo bo) {
        return R.ok(articlePublicFacade.queryMedia(bo.getArticleIds()));
    }
}
