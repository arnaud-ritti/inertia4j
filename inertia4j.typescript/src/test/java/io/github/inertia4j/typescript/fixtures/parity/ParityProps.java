package io.github.inertia4j.typescript.fixtures.parity;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.github.inertia4j.annotations.InertiaPage;
import org.jspecify.annotations.Nullable;

@InertiaPage("Parity/Show")
public record ParityProps(
    String firstName,
    ParityOwner homeOwner,
    @JsonInclude(JsonInclude.Include.NON_NULL) @Nullable String middleName,
    @Nullable String lastName
) {}
