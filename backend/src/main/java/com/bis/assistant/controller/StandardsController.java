package com.bis.assistant.controller;

import com.bis.assistant.service.StandardsService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * Lightweight IS catalogue endpoints — fast lookup without RAG.
 *
 * GET /standards/search?q=pressure+cooker
 * GET /standards/{isNumber}
 * GET /standards/schemes
 * GET /standards/suggest?product=LPG+cylinder
 */
@RestController
@RequestMapping("/standards")
@RequiredArgsConstructor
public class StandardsController {

    private final StandardsService standardsService;

    /** Full-text + trigram search over IS catalogue */
    @GetMapping("/search")
    public ResponseEntity<List<Map<String, Object>>> search(
            @RequestParam String q,
            @RequestParam(defaultValue = "10") int limit) {
        return ResponseEntity.ok(standardsService.search(q, limit));
    }

    /** Get metadata for a single IS number */
    @GetMapping("/{isNumber}")
    public ResponseEntity<Map<String, Object>> getByIsNumber(
            @PathVariable String isNumber) {
        return ResponseEntity.ok(standardsService.getByIsNumber(isNumber));
    }

    /** List all certification schemes with descriptions */
    @GetMapping("/schemes")
    public ResponseEntity<List<Map<String, Object>>> listSchemes() {
        return ResponseEntity.ok(standardsService.listSchemes());
    }

    /** AI-powered: given a product description, suggest applicable IS numbers */
    @GetMapping("/suggest")
    public ResponseEntity<List<Map<String, Object>>> suggestForProduct(
            @RequestParam String product) {
        return ResponseEntity.ok(standardsService.suggestForProduct(product));
    }
}
