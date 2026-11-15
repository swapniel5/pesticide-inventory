package com.agroshop.product;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:product-pages;DB_CLOSE_DELAY=-1",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
@AutoConfigureMockMvc
@Transactional
class ProductPagesIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    @WithMockUser
    void rendersProductAndInventoryPagesAndRecordsStock() throws Exception {
        mockMvc.perform(get("/products"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Products")));
        mockMvc.perform(get("/products/new"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Add product")));
        mockMvc.perform(get("/inventory"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Current stock")));

        MvcResult createResult = mockMvc.perform(post("/products")
                        .with(csrf())
                        .param("name", "Garden Spray")
                        .param("brand", "Agro")
                        .param("unit", "bottle")
                        .param("quantity", "5")
                        .param("sellingPrice", "10.50")
                        .param("lowStockThreshold", "2"))
                .andExpect(status().is3xxRedirection())
                .andReturn();

        String detailPath = createResult.getResponse().getRedirectedUrl();
        mockMvc.perform(get(detailPath))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Garden Spray")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("OPENING")));

        String productId = detailPath.substring(detailPath.lastIndexOf('/') + 1);
        mockMvc.perform(post("/api/products/" + productId + "/stock-movements")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"movementType":"PURCHASE","quantity":2,"remarks":"Restock"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.quantity").value(7));

        mockMvc.perform(post("/api/products/" + productId + "/stock-movements")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"movementType":"ADJUSTMENT_OUT","quantity":8,"remarks":"Too much"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value(org.hamcrest.Matchers.containsString("only 7 is available")));

        mockMvc.perform(get("/inventory"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("7 bottle")));
    }

    @Test
    @WithMockUser
    void rejectsProductWithoutNameWithValidationFeedback() throws Exception {
        mockMvc.perform(post("/products")
                        .with(csrf())
                        .param("name", " ")
                        .param("unit", "bottle")
                        .param("quantity", "0")
                        .param("sellingPrice", "10")
                        .param("lowStockThreshold", "0"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Product name is required")));
    }
}
