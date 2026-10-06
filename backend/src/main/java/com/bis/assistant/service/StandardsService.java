package com.bis.assistant.service;

import com.bis.assistant.model.IsCatalogue;
import com.bis.assistant.repository.IsCatalogueRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

@Service
@RequiredArgsConstructor
@Slf4j
public class StandardsService {

    private final IsCatalogueRepository isCatalogueRepo;
    private final RagClientService ragClient;

    // ── Search IS catalogue (trigram + ILIKE) ────────────────

    @Transactional(readOnly = true)
    @Cacheable(value = "standards-search", key = "#q + '-' + #limit")
    public List<Map<String, Object>> search(String q, int limit) {
        int safeLimit = Math.max(1, Math.min(limit, 50));
        String likeQ = "%" + q + "%";
        List<IsCatalogue> results = isCatalogueRepo.searchByTrigram(likeQ, q, safeLimit);
        return results.stream().map(this::toMap).toList();
    }

    // ── Get single IS by number ──────────────────────────────

    @Transactional(readOnly = true)
    @Cacheable(value = "standards-detail", key = "#isNumber")
    public Map<String, Object> getByIsNumber(String isNumber) {
        IsCatalogue is = isCatalogueRepo.findByIsNumber(isNumber.toUpperCase().strip())
            .orElseThrow(() -> new com.bis.assistant.exception.ResourceNotFoundException(
                "IS number not found: " + isNumber));
        return toMap(is);
    }

    // ── List certification schemes ───────────────────────────

    public List<Map<String, Object>> listSchemes() {
        return List.of(
            scheme("SCHEME_I", "ISI Mark (Scheme-I)",
                "Mandatory certification for industrial products. Requires factory inspection, "
                + "third-party lab testing, and ongoing surveillance. Apply at services.bis.gov.in.",
                "https://www.bis.gov.in/index.php/schemes/product-certification-schemes/scheme-i/"),

            scheme("CRS", "Compulsory Registration Scheme (CRS)",
                "Mandatory self-declaration for electronics and IT products (e.g. LED lights, "
                + "laptops, routers, mobile phones). No factory inspection required; "
                + "test report from BIS-recognised lab is sufficient.",
                "https://www.bis.gov.in/index.php/schemes/product-certification-schemes/crs/"),

            scheme("FMCS", "Foreign Manufacturer Certification Scheme (FMCS)",
                "Allows foreign manufacturers to get BIS certification for export to India. "
                + "Inspection by BIS officers abroad. Products bear the ISI mark.",
                "https://www.bis.gov.in/index.php/schemes/product-certification-schemes/fmcs/"),

            scheme("HALLMARKING", "Hallmarking Scheme",
                "Official certification of gold and silver purity. "
                + "Jewellers register free on BIS portal. "
                + "Hallmarking Centres (AHCs) are accredited under IS 15820.",
                "https://www.bis.gov.in/index.php/hallmarking/"),

            scheme("ECO_MARK", "Eco Mark Scheme",
                "Voluntary environmental label for products with lower environmental impact. "
                + "Covers categories like soaps, paper, textiles, food items, etc.",
                "https://www.bis.gov.in/index.php/schemes/product-certification-schemes/eco-mark/")
        );
    }

    // ── AI-powered product → IS suggestion ──────────────────

    public List<Map<String, Object>> suggestForProduct(String product) {
        // First try fast catalogue search
        List<Map<String, Object>> catalogueHits = search(product, 5);
        if (!catalogueHits.isEmpty()) return catalogueHits;

        // Fallback to RAG for less common products
        try {
            var ragResponse = ragClient.query(
                com.bis.assistant.dto.ChatDtos.RagRequest.builder()
                    .query("Which Indian Standard number (IS) is applicable for: " + product)
                    .lang("en")
                    .history(List.of())
                    .maxResults(5)
                    .includeHindi(false)
                    .build()
            );

            return ragResponse.chunks().stream()
                .filter(c -> c.isNumber() != null)
                .map(c -> Map.<String, Object>of(
                    "isNumber",    c.isNumber(),
                    "clauseRef",   c.clauseRef() != null ? c.clauseRef() : "",
                    "excerpt",     c.excerpt(),
                    "score",       c.score(),
                    "source",      "rag"
                ))
                .toList();
        } catch (Exception e) {
            log.warn("RAG suggestion failed for product '{}': {}", product, e.getMessage());
            return List.of();
        }
    }

    // ── Helpers ──────────────────────────────────────────────

    private Map<String, Object> toMap(IsCatalogue is) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("isNumber",    is.getIsNumber());
        m.put("title",       is.getTitle());
        m.put("titleHi",     is.getTitleHi());
        m.put("year",        is.getYear());
        m.put("division",    is.getDivision());
        m.put("certScheme",  is.getCertScheme());
        m.put("isMandatory", is.isMandatory());
        m.put("status",      is.getStatus());
        m.put("replaces",    is.getReplaces());
        m.put("replacedBy",  is.getReplacedBy());
        return m;
    }

    private Map<String, Object> scheme(String id, String name, String description, String url) {
        return Map.of("id", id, "name", name, "description", description, "url", url);
    }
}
