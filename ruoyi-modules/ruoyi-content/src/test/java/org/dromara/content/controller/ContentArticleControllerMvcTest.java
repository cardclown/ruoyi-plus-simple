package org.dromara.content.controller;

import cn.dev33.satoken.SaManager;
import cn.dev33.satoken.filter.SaTokenContextFilterForJakartaServlet;
import cn.dev33.satoken.interceptor.SaInterceptor;
import cn.dev33.satoken.stp.StpInterface;
import cn.dev33.satoken.stp.StpUtil;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.dromara.common.satoken.handler.SaTokenExceptionHandler;
import org.dromara.common.web.handler.GlobalExceptionHandler;
import org.dromara.content.service.IContentArticleService;
import org.dromara.content.service.support.ContentArticleDictionaryService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Tag("dev")
class ContentArticleControllerMvcTest {

    private final IContentArticleService articleService = mock(IContentArticleService.class);
    private final AtomicReference<List<String>> permissions = new AtomicReference<>(List.of());
    private StpInterface originalStpInterface;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        originalStpInterface = SaManager.getStpInterface();
        SaManager.setStpInterface(new StpInterface() {
            @Override
            public List<String> getPermissionList(Object loginId, String loginType) {
                return permissions.get();
            }

            @Override
            public List<String> getRoleList(Object loginId, String loginType) {
                return List.of();
            }
        });
        HandlerInterceptor authenticatedRequest = new HandlerInterceptor() {
            @Override
            public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
                StpUtil.login("article-mvc-user");
                return true;
            }
        };
        ContentArticleController controller = new ContentArticleController(
            articleService, mock(ContentArticleDictionaryService.class));
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
            .addFilter(new SaTokenContextFilterForJakartaServlet())
            .addInterceptors(authenticatedRequest, new SaInterceptor())
            .setControllerAdvice(new SaTokenExceptionHandler(), new GlobalExceptionHandler())
            .build();
    }

    @AfterEach
    void restoreStpInterface() {
        SaManager.setStpInterface(originalStpInterface);
    }

    @Test
    void physicalDeleteRoutesBindsIdsAndInvokesService() throws Exception {
        permissions.set(List.of("content:article:remove"));

        mockMvc.perform(delete("/content/article/physical/100,101"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(200));

        verify(articleService).physicalDeleteByIds(List.of(100L, 101L));
    }

    @Test
    void physicalDeleteRejectsInvalidIdWithoutInvokingService() throws Exception {
        permissions.set(List.of("content:article:remove"));

        mockMvc.perform(delete("/content/article/physical/not-a-number"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.msg", containsString("参数[articleIds]要求类型")));

        verify(articleService, never()).physicalDeleteByIds(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void physicalDeleteEmptyPathDoesNotInvokeService() throws Exception {
        permissions.set(List.of("content:article:remove"));

        mockMvc.perform(delete("/content/article/physical/"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(404));

        verify(articleService, never()).physicalDeleteByIds(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void physicalDeleteDeniesUserWithoutRemovePermission() throws Exception {
        permissions.set(List.of("content:article:list"));

        mockMvc.perform(delete("/content/article/physical/100"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(403));

        verify(articleService, never()).physicalDeleteByIds(org.mockito.ArgumentMatchers.any());
    }
}
