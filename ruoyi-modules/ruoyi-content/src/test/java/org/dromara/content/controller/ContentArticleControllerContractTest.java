package org.dromara.content.controller;

import com.fasterxml.jackson.annotation.JsonView;
import cn.dev33.satoken.annotation.SaCheckPermission;
import cn.dev33.satoken.annotation.SaMode;
import jakarta.servlet.http.HttpServletResponse;
import org.dromara.common.mybatis.core.page.PageQuery;
import org.dromara.content.domain.bo.ContentArticleBo;
import org.dromara.content.domain.bo.ContentArticleQuery;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.web.bind.annotation.GetMapping;

import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("dev")
class ContentArticleControllerContractTest {

    @Test
    void usesAddViewForCreateRequest() throws Exception {
        Method method = ContentArticleController.class.getDeclaredMethod("add", ContentArticleBo.class);
        JsonView jsonView = method.getParameters()[0].getAnnotation(JsonView.class);

        assertThat(jsonView).isNotNull();
        assertThat(jsonView.value()).containsExactly(ContentArticleBo.AddView.class);
    }

    @Test
    void usesEditViewForUpdateRequest() throws Exception {
        Method method = ContentArticleController.class.getDeclaredMethod("edit", ContentArticleBo.class);
        JsonView jsonView = method.getParameters()[0].getAnnotation(JsonView.class);

        assertThat(jsonView).isNotNull();
        assertThat(jsonView.value()).containsExactly(ContentArticleBo.EditView.class);
    }

    @Test
    void usesExplicitQueryModelForList() throws Exception {
        Method method = ContentArticleController.class.getDeclaredMethod(
            "list", ContentArticleQuery.class, PageQuery.class);

        assertThat(method).isNotNull();
    }

    @Test
    void flattensListQueryAndPaginationParametersForOpenApi() throws Exception {
        Method method = ContentArticleController.class.getDeclaredMethod(
            "list", ContentArticleQuery.class, PageQuery.class);

        assertParameterObject(method, 0);
        assertParameterObject(method, 1);
    }

    @Test
    void flattensExportQueryParameterForOpenApi() throws Exception {
        Method method = ContentArticleController.class.getDeclaredMethod(
            "export", ContentArticleQuery.class, HttpServletResponse.class);

        assertParameterObject(method, 0);
    }

    @Test
    void exposesFixedCategoryOptionsEndpoint() throws Exception {
        assertDictionaryEndpoint("categoryOptions", "/category-options");
    }

    @Test
    void exposesFixedTagOptionsEndpoint() throws Exception {
        assertDictionaryEndpoint("tagOptions", "/tag-options");
    }

    private void assertDictionaryEndpoint(String methodName, String path) throws Exception {
        Method method = ContentArticleController.class.getDeclaredMethod(methodName);
        GetMapping mapping = method.getAnnotation(GetMapping.class);
        assertThat(mapping).isNotNull();
        assertThat(mapping.value()).containsExactly(path);

        SaCheckPermission permission = method.getAnnotation(SaCheckPermission.class);
        assertThat(permission).isNotNull();
        assertThat(permission.mode()).isEqualTo(SaMode.OR);
        assertThat(permission.value()).containsExactlyInAnyOrder(
            "content:article:list",
            "content:article:query",
            "content:article:add",
            "content:article:edit"
        );
    }

    private void assertParameterObject(Method method, int parameterIndex) {
        assertThat(method.getParameters()[parameterIndex].getAnnotations())
            .extracting(annotation -> annotation.annotationType().getName())
            .contains("org.springdoc.core.annotations.ParameterObject");
    }
}
