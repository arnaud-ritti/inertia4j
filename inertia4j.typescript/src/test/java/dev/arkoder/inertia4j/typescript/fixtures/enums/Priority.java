package dev.arkoder.inertia4j.typescript.fixtures.enums;

import com.fasterxml.jackson.annotation.JsonValue;

public enum Priority {
    LOW,
    HIGH;

    @JsonValue
    public String json() {
        return name().toLowerCase();
    }
}
