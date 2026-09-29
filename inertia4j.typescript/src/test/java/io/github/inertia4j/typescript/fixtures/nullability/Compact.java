package io.github.inertia4j.typescript.fixtures.nullability;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.github.inertia4j.annotations.InertiaForm;
import org.jspecify.annotations.Nullable;

@InertiaForm
@JsonInclude(JsonInclude.Include.NON_NULL)
public record Compact(@Nullable String nickname, String name) {}
