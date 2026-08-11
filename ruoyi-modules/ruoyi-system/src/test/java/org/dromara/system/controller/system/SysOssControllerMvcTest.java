package org.dromara.system.controller.system;

import org.dromara.common.web.handler.GlobalExceptionHandler;
import org.dromara.system.domain.vo.SysOssVo;
import org.dromara.system.service.ISysOssService;
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
 * OSS 批量查询接口的 JSON 请求契约测试。
 */
@Tag("dev")
class SysOssControllerMvcTest {

    private final ISysOssService ossService = mock(ISysOssService.class);
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new SysOssController(ossService))
            .setControllerAdvice(new GlobalExceptionHandler())
            .build();
    }

    @Test
    void batchQueryAcceptsJsonIdsAndInvokesServiceOnce() throws Exception {
        SysOssVo first = new SysOssVo();
        first.setOssId(10L);
        when(ossService.listByIds(List.of(10L, 20L))).thenReturn(List.of(first));

        mockMvc.perform(post("/resource/oss/listByIds")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"ossIds\":[\"10\",\"20\"]}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(200))
            .andExpect(jsonPath("$.data[0].ossId").value("10"));

        verify(ossService).listByIds(List.of(10L, 20L));
    }

    @Test
    void batchQueryRejectsEmptyJsonArrayWithoutInvokingService() throws Exception {
        mockMvc.perform(post("/resource/oss/listByIds")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"ossIds\":[]}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.msg", containsString("OSS ID不能为空")));

        verify(ossService, never()).listByIds(org.mockito.ArgumentMatchers.any());
    }
}
