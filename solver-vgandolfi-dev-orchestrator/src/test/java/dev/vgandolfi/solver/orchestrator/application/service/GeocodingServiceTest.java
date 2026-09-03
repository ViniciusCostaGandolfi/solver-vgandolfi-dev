package dev.vgandolfi.solver.orchestrator.application.service;

import dev.vgandolfi.solver.orchestrator.application.dto.geo.GeocodeResult;
import dev.vgandolfi.solver.orchestrator.application.dto.geo.ReverseGeocodeResult;
import dev.vgandolfi.solver.orchestrator.domain.exception.CepNotFoundException;
import dev.vgandolfi.solver.orchestrator.infrastructure.client.NominatimClient;
import dev.vgandolfi.solver.orchestrator.infrastructure.client.OpenCepClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GeocodingServiceTest {

    @Mock private NominatimClient nominatimClient;
    @Mock private OpenCepClient openCepClient;

    private GeocodingService service;

    @BeforeEach
    void setUp() {
        service = new GeocodingService(nominatimClient, openCepClient);
    }

    @Test
    void geocodeDelegatesToClient() {
        List<GeocodeResult> expected = List.of(new GeocodeResult(
                "Rua Augusta, Consolacao, Sao Paulo, SP, 01310-100, Brasil",
                "Rua Augusta", "Sao Paulo", "SP", "01310-100", -23.5521, -46.6542, "nominatim"));
        when(nominatimClient.search("rua augusta")).thenReturn(expected);

        assertThat(service.geocode("rua augusta")).isEqualTo(expected);
        verify(nominatimClient).search("rua augusta");
    }

    @Test
    void geocodePropagatesEmptyResultWhenClientFailsOpen() {
        when(nominatimClient.search("endereco inexistente")).thenReturn(List.of());

        assertThat(service.geocode("endereco inexistente")).isEmpty();
    }

    @Test
    void reverseDelegatesToClient() {
        ReverseGeocodeResult expected = new ReverseGeocodeResult(
                "Avenida Paulista, Bela Vista, Sao Paulo, SP, 01310-100, Brasil",
                "Avenida Paulista", "Sao Paulo", "SP", "01310-100", -23.5614, -46.6559, "nominatim");
        when(nominatimClient.reverse(-23.5614, -46.6559)).thenReturn(expected);

        assertThat(service.reverse(-23.5614, -46.6559)).isEqualTo(expected);
        verify(nominatimClient).reverse(-23.5614, -46.6559);
    }

    @Test
    void reversePropagatesNullWhenClientFailsOpen() {
        when(nominatimClient.reverse(-90.0, -180.0)).thenReturn(null);

        assertThat(service.reverse(-90.0, -180.0)).isNull();
    }

    @Test
    void lookupCepDelegatesToClient() {
        GeocodeResult expected = new GeocodeResult(
                "Praça da Sé, Sé, São Paulo, SP, 01001-000",
                "Praça da Sé", "São Paulo", "SP", "01001-000", null, null, "opencep");
        when(openCepClient.lookupCep("01001000")).thenReturn(Optional.of(expected));

        assertThat(service.lookupCep("01001000")).isEqualTo(expected);
        verify(openCepClient).lookupCep("01001000");
    }

    @Test
    void lookupCepThrowsCepNotFoundWhenClientReturnsEmpty() {
        when(openCepClient.lookupCep("99999999")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.lookupCep("99999999"))
                .isInstanceOf(CepNotFoundException.class)
                .hasMessageContaining("99999999");
    }
}