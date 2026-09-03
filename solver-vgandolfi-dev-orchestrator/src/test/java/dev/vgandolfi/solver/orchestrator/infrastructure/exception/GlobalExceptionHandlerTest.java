package dev.vgandolfi.solver.orchestrator.infrastructure.exception;

import dev.vgandolfi.solver.orchestrator.infrastructure.security.RateLimitFilter;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Testa o {@link GlobalExceptionHandler}: rotas inexistentes → 404, body
 * malformado → 400, argumento de tipo inválido (id não-UUID) → 400, sempre com
 * o shape consistente {@code {"error": "...", "fields": null}}.
 */
@WebMvcTest(controllers = TestRoutesController.class,
        excludeFilters = @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = RateLimitFilter.class))
@Import(GlobalExceptionHandler.class)
@ActiveProfiles("test")
class GlobalExceptionHandlerTest {

    @Autowired private MockMvc mockMvc;

    @Test
    void unknownRouteReturns404WithConsistentShape() throws Exception {
        mockMvc.perform(get("/api/v1/does-not-exist"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("Resource not found: api/v1/does-not-exist"))
                .andExpect(jsonPath("$.fields").value(nullValue()));
    }

    @Test
    void malformedJsonBodyReturns400() throws Exception {
        mockMvc.perform(post("/api/v1/test/echo")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{not valid json"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Malformed request body"))
                .andExpect(jsonPath("$.fields").value(nullValue()));
    }

    @Test
    void structurallyInvalidJsonBodyReturns400() throws Exception {
        mockMvc.perform(post("/api/v1/test/echo")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\": {\"x\": 1}}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Malformed request body"));
    }

    @Test
    void invalidUuidArgumentReturns400() throws Exception {
        mockMvc.perform(get("/api/v1/test/not-a-uuid"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Invalid value for id: not-a-uuid"))
                .andExpect(jsonPath("$.fields").value(nullValue()));
    }
}