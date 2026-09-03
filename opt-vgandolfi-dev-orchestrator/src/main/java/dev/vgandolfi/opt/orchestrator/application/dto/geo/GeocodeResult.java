package dev.vgandolfi.opt.orchestrator.application.dto.geo;

/**
 * Resultado de geocoding (forward) normalizado a partir do Nominatim.
 *
 * @param formattedAddress endereço completo (display_name do Nominatim)
 * @param streetName       via/nome de rua (campo {@code address.road})
 * @param city             cidade ({@code address.city})
 * @param state            estado/UF ({@code address.state})
 * @param postalCode       CEP ({@code address.postcode})
 * @param latitude         latitude convertida de string
 * @param longitude        longitude convertida de string
 * @param source           origem dos dados (sempre {@code "nominatim"})
 */
public record GeocodeResult(String formattedAddress, String streetName, String city, String state,
                            String postalCode, Double latitude, Double longitude, String source) {
}