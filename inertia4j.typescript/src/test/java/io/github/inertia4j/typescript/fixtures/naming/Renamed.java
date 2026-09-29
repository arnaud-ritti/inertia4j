package io.github.inertia4j.typescript.fixtures.naming;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.github.inertia4j.annotations.InertiaForm;

@InertiaForm
public record Renamed(@JsonProperty("full_name") String name, @JsonIgnore String secret, String firstName) {}
