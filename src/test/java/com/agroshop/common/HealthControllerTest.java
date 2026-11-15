package com.agroshop.common;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.agroshop.security.SecurityConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@WebMvcTest(HealthController.class)
@Import({SecurityConfiguration.class, HealthControllerTest.ProtectedTestController.class})
class HealthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void healthEndpointIsPublicAndReturnsUpStatus() throws Exception {
        mockMvc.perform(get("/api/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
    }

    @Test
    void protectsBusinessRoutesAndAcceptsConfiguredCredentials() throws Exception {
        mockMvc.perform(get("/test/protected"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(get("/test/protected").with(httpBasic("test-shop-owner", "test-only-password")))
                .andExpect(status().isOk())
                .andExpect(content().string("protected"));
    }

    @RestController
    public static class ProtectedTestController {

        @GetMapping("/test/protected")
        public String protectedRoute() {
            return "protected";
        }
    }
}
