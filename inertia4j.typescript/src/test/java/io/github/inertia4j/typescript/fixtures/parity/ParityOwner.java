package io.github.inertia4j.typescript.fixtures.parity;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.jspecify.annotations.Nullable;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ParityOwner(
    String streetName,
    @JsonProperty("zip_code_value") String zipCode,
    String pageURL,
    @Nullable String nickName
) {}
