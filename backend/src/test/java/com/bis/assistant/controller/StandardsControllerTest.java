package com.bis.assistant.controller;

import com.bis.assistant.security.JwtService;
import com.bis.assistant.service.StandardsService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(StandardsController.class)
class StandardsControllerTest {

    @Autowired MockMvc mockMvc;

    @MockBean StandardsService standardsService;
    @MockBean JwtService jwtService;
    @MockBean UserDetailsService userDetailsService;

    @Test
    void search_publicEndpoint_returns200() throws Exception {
        when(standardsService.search("LED", 10)).thenReturn(List.of(
            Map.of(
                "isNumber",   "IS 16221",
                "title",      "LED Luminaires — Performance Requirements",
                "certScheme", "CRS",
                "isMandatory", true
            )
        ));

        mockMvc.perform(get("/standards/search").param("q", "LED"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].isNumber").value("IS 16221"))
            .andExpect(jsonPath("$[0].certScheme").value("CRS"));
    }

    @Test
    void search_emptyQuery_stillReturns200() throws Exception {
        when(standardsService.search(any(), anyInt())).thenReturn(List.of());
        mockMvc.perform(get("/standards/search").param("q", ""))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$").isArray());
    }

    @Test
    void listSchemes_returns5Schemes() throws Exception {
        when(standardsService.listSchemes()).thenReturn(List.of(
            Map.of("id", "SCHEME_I", "name", "ISI Mark (Scheme-I)"),
            Map.of("id", "CRS",      "name", "Compulsory Registration Scheme (CRS)"),
            Map.of("id", "FMCS",     "name", "Foreign Manufacturer Certification Scheme (FMCS)"),
            Map.of("id", "HALLMARKING","name","Hallmarking Scheme"),
            Map.of("id", "ECO_MARK", "name", "Eco Mark Scheme")
        ));

        mockMvc.perform(get("/standards/schemes"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.length()").value(5))
            .andExpect(jsonPath("$[0].id").value("SCHEME_I"));
    }

    @Test
    void getByIsNumber_knownIS_returns200() throws Exception {
        when(standardsService.getByIsNumber("IS 2902")).thenReturn(Map.of(
            "isNumber", "IS 2902",
            "title",    "Hot Water Bottles — Specification",
            "year",     2006
        ));

        mockMvc.perform(get("/standards/IS 2902"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.isNumber").value("IS 2902"))
            .andExpect(jsonPath("$.year").value(2006));
    }
}
