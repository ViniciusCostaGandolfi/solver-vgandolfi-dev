package dev.vgandolfi.opt.orchestrator.infrastructure.controller;

import dev.vgandolfi.opt.orchestrator.application.dto.geo.GeocodeResult;
import dev.vgandolfi.opt.orchestrator.application.dto.geo.ReverseGeocodeResult;
import dev.vgandolfi.opt.orchestrator.application.service.GeocodingService;
import dev.vgandolfi.opt.orchestrator.domain.exception.CepNotFoundException;
import dev.vgandolfi.opt.orchestrator.infrastructure.security.RateLimitFilter;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = GeocodingController.class,
        excludeFilters = @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = RateLimitFilter.class))
@ActiveProfiles("test")
class GeocodingControllerTest {

    @Autowired private MockMvc mockMvc;

    @MockitoBean private GeocodingService geocodingService;

    @Test
    void geocodeReturns200WithResults() throws Exception {
        when(geocodingService.geocode("rua augusta")).thenReturn(List.of(new GeocodeResult(
                "Rua Augusta, Consolacao, Sao Paulo, SP, 01310-100, Brasil",
                "Rua Augusta", "Sao Paulo", "SP", "01310-100", -23.5521, -46.6542, "nominatim")));

        mockMvc.perform(get("/api/v1/geo/geocode").param("address", "rua augusta"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].formattedAddress")
                        .value("Rua Augusta, Consolacao, Sao Paulo, SP, 01310-100, Brasil"))
                .andExpect(jsonPath("$[0].streetName").value("Rua Augusta"))
                .andExpect(jsonPath("$[0].city").value("Sao Paulo"))
                .andExpect(jsonPath("$[0].state").value("SP"))
                .andExpect(jsonPath("$[0].postalCode").value("01310-100"))
                .andExpect(jsonPath("$[0].latitude").value(-23.5521))
                .andExpect(jsonPath("$[0].longitude").value(-46.6542))
                .andExpect(jsonPath("$[0].source").value("nominatim"));
    }

    @Test
    void geocodeReturns200WithEmptyList() throws Exception {
        when(geocodingService.geocode("rua inexistente")).thenReturn(List.of());

        mockMvc.perform(get("/api/v1/geo/geocode").param("address", "rua inexistente"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$").isEmpty());
    }

    @Test
    void geocodeWithoutAddressReturns400() throws Exception {
        mockMvc.perform(get("/api/v1/geo/geocode"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Missing required parameter: address"));
    }

    @Test
    void geocodeWithBlankAddressReturns400() throws Exception {
        mockMvc.perform(get("/api/v1/geo/geocode").param("address", "   "))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Validation failed"));
    }

    @Test
    void reverseReturns200WithResult() throws Exception {
        when(geocodingService.reverse(-23.5614, -46.6559)).thenReturn(new ReverseGeocodeResult(
                "Avenida Paulista, Bela Vista, Sao Paulo, SP, 01310-100, Brasil",
                "Avenida Paulista", "Sao Paulo", "SP", "01310-100", -23.5614, -46.6559, "nominatim"));

        mockMvc.perform(get("/api/v1/geo/reverse").param("lat", "-23.5614").param("lng", "-46.6559"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.formattedAddress")
                        .value("Avenida Paulista, Bela Vista, Sao Paulo, SP, 01310-100, Brasil"))
                .andExpect(jsonPath("$.streetName").value("Avenida Paulista"))
                .andExpect(jsonPath("$.latitude").value(-23.5614))
                .andExpect(jsonPath("$.source").value("nominatim"));
    }

    @Test
    void reverseReturns200WithNullBodyWhenNotFound() throws Exception {
        when(geocodingService.reverse(-90.0, -180.0)).thenReturn(null);

        mockMvc.perform(get("/api/v1/geo/reverse").param("lat", "-90").param("lng", "-180"))
                .andExpect(status().isOk());
    }

    @Test
    void lookupCepReturns200WithResult() throws Exception {
        when(geocodingService.lookupCep("01001000")).thenReturn(new GeocodeResult(
                "Praça da Sé, Sé, São Paulo, SP, 01001-000",
                "Praça da Sé", "São Paulo", "SP", "01001-000", null, null, "opencep"));

        mockMvc.perform(get("/api/v1/geo/cep/01001000"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.streetName").value("Praça da Sé"))
                .andExpect(jsonPath("$.city").value("São Paulo"))
                .andExpect(jsonPath("$.state").value("SP"))
                .andExpect(jsonPath("$.postalCode").value("01001-000"))
                .andExpect(jsonPath("$.source").value("opencep"));
    }

    @Test
    void lookupCepWithHyphenReturns200() throws Exception {
        when(geocodingService.lookupCep("01001-000")).thenReturn(new GeocodeResult(
                "Praça da Sé, Sé, São Paulo, SP, 01001-000",
                "Praça da Sé", "São Paulo", "SP", "01001-000", null, null, "opencep"));

        mockMvc.perform(get("/api/v1/geo/cep/01001-000"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.postalCode").value("01001-000"));
    }

    @Test
    void lookupCepReturns404WhenNotFound() throws Exception {
        when(geocodingService.lookupCep("99999999"))
                .thenThrow(new CepNotFoundException("99999999"));

        mockMvc.perform(get("/api/v1/geo/cep/99999999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("CEP not found: 99999999"))
                .andExpect(jsonPath("$.fields").value(org.hamcrest.Matchers.nullValue()));
    }

    @Test
    void lookupCepWithInvalidFormatReturns400() throws Exception {
        mockMvc.perform(get("/api/v1/geo/cep/abc"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Validation failed"));
    }
}