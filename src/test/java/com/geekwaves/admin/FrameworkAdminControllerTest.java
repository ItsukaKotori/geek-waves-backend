package com.geekwaves.admin;

import com.geekwaves.aggregation.domain.FrameworkWatch;
import com.geekwaves.aggregation.framework.FrameworkFetchService;
import org.itsuka.core.exception.ServiceException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ActiveProfiles("test")
@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:frameworkadmintestdb;MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE;CASE_INSENSITIVE_IDENTIFIERS=TRUE")
@AutoConfigureMockMvc
class FrameworkAdminControllerTest {
    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private FrameworkFetchService frameworkFetchService;

    @Test
    void refreshExistingFrameworkReturnsUpdatedEntity() throws Exception {
        FrameworkWatch watch = new FrameworkWatch();
        watch.setId(1L);
        watch.setName("Spring Boot");
        watch.setGithubRepo("spring-projects/spring-boot");
        watch.setLatestVersion("v3.4.1");
        when(frameworkFetchService.refresh(1L)).thenReturn(watch);

        mockMvc.perform(post("/api/admin/frameworks/1/refresh"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").value(1))
                .andExpect(jsonPath("$.data.latestVersion").value("v3.4.1"));
    }

    @Test
    void refreshMissingFrameworkReturns404() throws Exception {
        when(frameworkFetchService.refresh(999999L))
                .thenThrow(ServiceException.create(HttpStatus.NOT_FOUND, "framework 不存在"));

        mockMvc.perform(post("/api/admin/frameworks/999999/refresh"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").exists());
    }
}
