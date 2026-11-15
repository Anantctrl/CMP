package com.copmap.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@DisplayName("API Documentation & OpenAPI v3 Specification Test Suite")
public class ApiDocumentationConfigTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    @DisplayName("Verify /v3/api-docs Returns 200 OK and Valid OpenAPI 3.0 Document")
    void shouldReturnOpenApiV3Specification() throws Exception {
        mockMvc.perform(get("/v3/api-docs")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.openapi").exists())
                .andExpect(jsonPath("$.info.title").value(containsString("COPMAP APPLICATION")))
                .andExpect(jsonPath("$.info.contact.name").value("Anant Gupta"))
                .andExpect(jsonPath("$.paths['/api/v1/auth/login']").exists());
    }

    @Test
    @DisplayName("Verify Swagger UI Assets are Publicly Accessible Without Authentication")
    void shouldAllowPublicAccessToSwaggerUi() throws Exception {
        mockMvc.perform(get("/swagger-ui/index.html"))
                .andExpect(status().isOk());

        mockMvc.perform(get("/v3/api-docs/swagger-config"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON));
    }
}
