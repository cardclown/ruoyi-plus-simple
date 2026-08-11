package org.dromara.content.controller;

import com.fasterxml.jackson.annotation.JsonView;
import cn.dev33.satoken.annotation.SaCheckPermission;
import cn.dev33.satoken.annotation.SaMode;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.constraints.NotEmpty;
import org.dromara.common.idempotent.annotation.RepeatSubmit;
import org.dromara.common.log.annotation.Log;
import org.dromara.common.log.enums.BusinessType;
import org.dromara.common.core.validate.AddGroup;
import org.dromara.common.core.validate.EditGroup;
import org.dromara.common.mybatis.core.page.PageQuery;
import org.dromara.content.domain.bo.ContentArticleBo;
import org.dromara.content.domain.bo.ContentArticleQuery;
import org.dromara.content.domain.bo.ContentArticleStatusBo;
import org.dromara.content.service.IContentArticleService;
import org.dromara.content.service.support.ContentArticleDictionaryService;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import java.lang.reflect.Method;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

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
    void rejectsRegressionWhenAddValidationGroupIsRemovedOrChanged() throws Exception {
        Method method = ContentArticleController.class.getDeclaredMethod("add", ContentArticleBo.class);
        Validated validated = method.getParameters()[0].getAnnotation(Validated.class);

        assertThat(validated).isNotNull();
        assertThat(validated.value()).containsExactly(AddGroup.class);
    }

    @Test
    void rejectsRegressionWhenEditValidationGroupIsRemovedOrChanged() throws Exception {
        Method method = ContentArticleController.class.getDeclaredMethod("edit", ContentArticleBo.class);
        Validated validated = method.getParameters()[0].getAnnotation(Validated.class);

        assertThat(validated).isNotNull();
        assertThat(validated.value()).containsExactly(EditGroup.class);
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

    @Test
    void exposesValidatedPostStatusChangeEndpoint() throws Exception {
        Method method = ContentArticleController.class.getDeclaredMethod(
            "changeStatus", ContentArticleStatusBo.class);

        PostMapping mapping = method.getAnnotation(PostMapping.class);
        assertThat(mapping).isNotNull();
        assertThat(mapping.value()).containsExactly("/changeStatus");

        SaCheckPermission permission = method.getAnnotation(SaCheckPermission.class);
        assertThat(permission).isNotNull();
        assertThat(permission.value()).containsExactly("content:article:edit");

        Log log = method.getAnnotation(Log.class);
        assertThat(log).isNotNull();
        assertThat(log.businessType()).isEqualTo(BusinessType.UPDATE);
        assertThat(method.getAnnotation(RepeatSubmit.class)).isNotNull();

        assertThat(method.getParameters()[0].getAnnotation(RequestBody.class)).isNotNull();
        Validated validated = method.getParameters()[0].getAnnotation(Validated.class);
        assertThat(validated).isNotNull();
        assertThat(validated.value()).isEmpty();
    }

    @Test
    void physicalDeleteEndpointDelegatesValidatedIdsWithRemovePermission() throws Exception {
        IContentArticleService service = mock(IContentArticleService.class);
        ContentArticleController controller = new ContentArticleController(
            service, mock(ContentArticleDictionaryService.class));

        controller.physicalRemove(new Long[]{100L, 101L});

        verify(service).physicalDeleteByIds(List.of(100L, 101L));
        Method method = ContentArticleController.class.getDeclaredMethod("physicalRemove", Long[].class);
        assertThat(method.getAnnotation(DeleteMapping.class).value())
            .containsExactly("/physical/{articleIds}");
        assertThat(method.getAnnotation(SaCheckPermission.class).value())
            .containsExactly("content:article:remove");
        assertThat(method.getAnnotation(Log.class).businessType()).isEqualTo(BusinessType.CLEAN);
        assertThat(method.getParameters()[0].getAnnotation(NotEmpty.class)).isNotNull();
        assertThat(method.getParameters()[0].getAnnotation(PathVariable.class)).isNotNull();
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
