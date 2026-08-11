package org.dromara.content.controller;

import org.dromara.common.web.handler.GlobalExceptionHandler;
import org.dromara.content.domain.vo.ContentArticleMediaGroupVo;
import org.dromara.content.service.IContentArticlePublicFacade;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 官网文章批量媒体接口的 JSON 请求与分组响应测试。
 */
@Tag("dev")
class ContentArticlePublicControllerMvcTest {

    private final IContentArticlePublicFacade facade = mock(IContentArticlePublicFacade.class);
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new ContentArticlePublicController(facade))
            .setControllerAdvice(new GlobalExceptionHandler())
            .build();
    }

    @Test
    void batchMediaAcceptsMultipleArticleIdsAndReturnsArticleGroups() throws Exception {
        ContentArticleMediaGroupVo first = new ContentArticleMediaGroupVo();
        first.setArticleId(100L);
        first.setMedia(List.of());
        when(facade.queryMedia(List.of(100L, 101L))).thenReturn(List.of(first));

        mockMvc.perform(post("/content/article/public/media")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"articleIds\":[\"100\",\"101\"]}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(200))
            .andExpect(jsonPath("$.data[0].articleId").value("100"));

        verify(facade).queryMedia(List.of(100L, 101L));
    }

    @Test
    void batchMediaRejectsEmptyArticleIdsWithoutInvokingFacade() throws Exception {
        mockMvc.perform(post("/content/article/public/media")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"articleIds\":[]}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.msg", containsString("文章ID不能为空")));

        verify(facade, never()).queryMedia(org.mockito.ArgumentMatchers.any());
    }
}
