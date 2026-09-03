package dev.vgandolfi.solver.orchestrator.infrastructure.exception;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * Controller mínimo usado apenas pelo {@link GlobalExceptionHandlerTest} para
 * exercitar os handlers de erro via {@code @WebMvcTest}.
 */
@RestController
@RequestMapping("/api/v1/test")
public class TestRoutesController {

    public record TestBody(@NotBlank String name) {
    }

    @GetMapping("/{id}")
    public String byId(@PathVariable UUID id) {
        return id.toString();
    }

    @PostMapping("/echo")
    public String echo(@Valid @RequestBody TestBody body) {
        return body.name();
    }
}