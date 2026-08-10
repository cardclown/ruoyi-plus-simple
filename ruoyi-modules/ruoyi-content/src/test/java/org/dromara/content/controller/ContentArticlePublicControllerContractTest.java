package org.dromara.content.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import cn.dev33.satoken.annotation.SaIgnore;
import jakarta.validation.constraints.NotNull;
import org.dromara.common.mybatis.core.page.PageQuery;
import org.dromara.content.domain.bo.ContentArticleQuery;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("dev")
class ContentArticlePublicControllerContractTest {

    @Test
    void controllerIsAnonymousAndHasFixedBasePath() {
        assertThat(ContentArticlePublicController.class.getAnnotation(SaIgnore.class)).isNotNull();
        RequestMapping mapping = ContentArticlePublicController.class.getAnnotation(RequestMapping.class);
        assertThat(mapping.value()).containsExactly("/content/article/public");
        assertThat(ContentArticlePublicController.class.getAnnotation(SaCheckPermission.class)).isNull();
    }

    @Test
    void exposesAnonymousPagedList() throws Exception {
        Method method = ContentArticlePublicController.class.getDeclaredMethod(
            "list", ContentArticleQuery.class, PageQuery.class);
        assertThat(method.getAnnotation(GetMapping.class).value()).containsExactly("/list");
        assertThat(method.getAnnotation(SaCheckPermission.class)).isNull();
        assertThat(method.getParameters()[0].getAnnotations())
            .extracting(annotation -> annotation.annotationType().getName())
            .contains("org.springdoc.core.annotations.ParameterObject");
        assertThat(method.getParameters()[1].getAnnotations())
            .extracting(annotation -> annotation.annotationType().getName())
            .contains("org.springdoc.core.annotations.ParameterObject");
    }

    @Test
    void exposesAnonymousDetailWithRequiredId() throws Exception {
        Method method = ContentArticlePublicController.class.getDeclaredMethod(
            "getInfo", Long.class);
        assertThat(method.getAnnotation(GetMapping.class).value()).containsExactly("/{articleId}");
        assertThat(method.getAnnotation(SaCheckPermission.class)).isNull();
        NotNull notNull = method.getParameters()[0].getAnnotation(NotNull.class);
        assertThat(notNull).isNotNull();
        assertThat(notNull.message()).isEqualTo("文章ID不能为空");
    }

    @Test
    void existingManagementReadsRemainPermissionProtected() throws Exception {
        Method list = ContentArticleController.class.getDeclaredMethod(
            "list", ContentArticleQuery.class, PageQuery.class);
        Method detail = ContentArticleController.class.getDeclaredMethod("getInfo", Long.class);

        assertThat(list.getAnnotation(SaCheckPermission.class).value())
            .containsExactly("content:article:list");
        assertThat(detail.getAnnotation(SaCheckPermission.class).value())
            .containsExactly("content:article:query");
    }
}
