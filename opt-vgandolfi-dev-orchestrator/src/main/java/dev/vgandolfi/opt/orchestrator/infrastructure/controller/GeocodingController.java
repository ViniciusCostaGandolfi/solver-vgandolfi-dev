package dev.vgandolfi.opt.orchestrator.infrastructure.controller;

import dev.vgandolfi.opt.orchestrator.application.dto.geo.GeocodeResult;
import dev.vgandolfi.opt.orchestrator.application.dto.geo.ReverseGeocodeResult;
import dev.vgandolfi.opt.orchestrator.application.service.GeocodingService;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * API pública de geocoding (forward, reverse e CEP) com fail-open para
 * Nominatim/OpenCEP.
 *
 * <ul>
 *   <li>{@code GET /api/v1/geo/geocode} → lista (vazia em erro/fail-open)</li>
 *   <li>{@code GET /api/v1/geo/reverse} → 200 com body {@code null} quando nada é encontrado</li>
 *   <li>{@code GET /api/v1/geo/cep/{cep}} → 200 com o CEP normalizado ou 404
 *       (CEP inválido/não encontrado/upstream indisponível em fail-open)</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/v1/geo")
@RequiredArgsConstructor
@Validated
public class GeocodingController {

    private final GeocodingService geocodingService;

    @GetMapping("/geocode")
    public ResponseEntity<List<GeocodeResult>> geocode(@RequestParam @NotBlank String address) {
        return ResponseEntity.ok(geocodingService.geocode(address));
    }

    @GetMapping("/reverse")
    public ResponseEntity<ReverseGeocodeResult> reverse(@RequestParam Double lat, @RequestParam Double lng) {
        return ResponseEntity.ok(geocodingService.reverse(lat, lng));
    }

    @GetMapping("/cep/{cep}")
    public ResponseEntity<GeocodeResult> lookupCep(@PathVariable
            @NotBlank @Pattern(regexp = "\\d{5}-?\\d{3}", message = "CEP inválido") String cep) {
        return ResponseEntity.ok(geocodingService.lookupCep(cep));
    }
}