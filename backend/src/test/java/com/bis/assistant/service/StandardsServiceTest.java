package com.bis.assistant.service;

import com.bis.assistant.exception.ResourceNotFoundException;
import com.bis.assistant.model.IsCatalogue;
import com.bis.assistant.repository.IsCatalogueRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class StandardsServiceTest {

    @Mock IsCatalogueRepository isCatalogueRepo;
    @Mock RagClientService ragClient;

    @InjectMocks StandardsService standardsService;

    private IsCatalogue makeCatalogue(String isNumber, String title, String scheme) {
        return IsCatalogue.builder()
            .isNumber(isNumber)
            .title(title)
            .year((short) 2023)
            .certScheme(scheme)
            .mandatory(true)
            .status("CURRENT")
            .build();
    }

    @Test
    void search_returnsMappedResults() {
        when(isCatalogueRepo.searchByTrigram(anyString(), anyString(), anyInt()))
            .thenReturn(List.of(
                makeCatalogue("IS 2902", "Hot Water Bottles", "SCHEME_I"),
                makeCatalogue("IS 3196", "LPG Cylinders",     "SCHEME_I")
            ));

        List<Map<String, Object>> results = standardsService.search("pressure", 10);

        assertThat(results).hasSize(2);
        assertThat(results.get(0).get("isNumber")).isEqualTo("IS 2902");
        assertThat(results.get(1).get("certScheme")).isEqualTo("SCHEME_I");
    }

    @Test
    void search_noResults_returnsEmptyList() {
        when(isCatalogueRepo.searchByTrigram(any(), any(), anyInt()))
            .thenReturn(List.of());

        List<Map<String, Object>> results = standardsService.search("xyzunknown", 10);
        assertThat(results).isEmpty();
    }

    @Test
    void getByIsNumber_found_returnsMap() {
        when(isCatalogueRepo.findByIsNumber("IS 2902"))
            .thenReturn(Optional.of(makeCatalogue("IS 2902", "Hot Water Bottles", "SCHEME_I")));

        Map<String, Object> result = standardsService.getByIsNumber("IS 2902");

        assertThat(result.get("isNumber")).isEqualTo("IS 2902");
        assertThat(result.get("isMandatory")).isEqualTo(true);
    }

    @Test
    void getByIsNumber_notFound_throwsException() {
        when(isCatalogueRepo.findByIsNumber(any())).thenReturn(Optional.empty());
        assertThatThrownBy(() -> standardsService.getByIsNumber("IS 9999"))
            .isInstanceOf(ResourceNotFoundException.class)
            .hasMessageContaining("IS 9999");
    }

    @Test
    void listSchemes_returnsAllFiveSchemes() {
        List<Map<String, Object>> schemes = standardsService.listSchemes();
        assertThat(schemes).hasSize(5);
        assertThat(schemes.stream().map(s -> s.get("id")).toList())
            .containsExactlyInAnyOrder("SCHEME_I", "CRS", "FMCS", "HALLMARKING", "ECO_MARK");
    }

    @Test
    void suggestForProduct_catalogueHit_noRagCall() {
        when(isCatalogueRepo.searchByTrigram(any(), any(), anyInt()))
            .thenReturn(List.of(makeCatalogue("IS 16221", "LED Luminaires", "CRS")));

        List<Map<String, Object>> result = standardsService.suggestForProduct("LED bulb");

        assertThat(result).isNotEmpty();
        assertThat(result.get(0).get("isNumber")).isEqualTo("IS 16221");
        verifyNoInteractions(ragClient); // catalogue hit — RAG not called
    }
}
