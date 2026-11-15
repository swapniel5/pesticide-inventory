package com.agroshop.customer;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.net.URI;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:customer-pages;DB_CLOSE_DELAY=-1",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
@AutoConfigureMockMvc
@Transactional
class CustomerPagesIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    @WithMockUser
    void rendersCustomerPagesAndSupportsCreateUpdateAndDeactivation() throws Exception {
        mockMvc.perform(get("/customers"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Customers")));
        mockMvc.perform(get("/customers/new"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Add customer")));

        MvcResult createResult = mockMvc.perform(post("/customers")
                        .with(csrf())
                        .param("name", "  Asha Devi ")
                        .param("phone", "9876543210")
                        .param("village", " Rampur "))
                .andExpect(status().is3xxRedirection())
                .andReturn();

        String detailPath = createResult.getResponse().getRedirectedUrl();
        mockMvc.perform(get(detailPath))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Asha Devi")))
                .andExpect(content().string(containsString("This customer has no purchases yet")))
                .andExpect(content().string(containsString("Total purchases")))
                .andExpect(content().string(containsString("₹0.00")));

        String customerId = detailPath.substring(detailPath.lastIndexOf('/') + 1);
        mockMvc.perform(get("/customers/" + customerId + "/edit"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Edit customer")));
        mockMvc.perform(post("/customers/" + customerId + "/edit")
                        .with(csrf())
                        .param("name", "Asha Kumari")
                        .param("phone", "9876543210")
                        .param("village", "Rampur"))
                .andExpect(status().is3xxRedirection());

        mockMvc.perform(post("/customers/" + customerId + "/deactivate").with(csrf()))
                .andExpect(status().is3xxRedirection());
        mockMvc.perform(get("/customers/" + customerId))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Inactive")));
    }

    @Test
    @WithMockUser
    void providesCustomerRestCrudSearchAndValidation() throws Exception {
        String json = """
                {"name":"Asha Devi","phone":"9876543210","village":"Rampur","address":"Main Road","notes":"New farmer"}
                """;
        MvcResult create = mockMvc.perform(post("/api/customers")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isCreated())
                .andExpect(header().exists("Location"))
                .andExpect(jsonPath("$.active").value(true))
                .andReturn();

        String customerId = URI.create(create.getResponse().getHeader("Location")).getPath()
                .substring(("/api/customers/").length());
        mockMvc.perform(get("/api/customers?search=Rampur"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("Asha Devi"));
        mockMvc.perform(get("/api/customers/" + customerId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.phone").value("9876543210"));

        mockMvc.perform(put("/api/customers/" + customerId)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Asha Kumari","phone":"9876543210","village":"Rampur"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Asha Kumari"));

        mockMvc.perform(patch("/api/customers/" + customerId + "/deactivate").with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(false));

        mockMvc.perform(post("/api/customers")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Invalid","phone":"1234567890","village":"Rampur"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value(containsString("valid Indian mobile number")));
    }
}
