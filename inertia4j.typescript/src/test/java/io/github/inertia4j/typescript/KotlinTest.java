package io.github.inertia4j.typescript;

import org.junit.jupiter.api.Test;

import static io.github.inertia4j.typescript.GeneratorTestSupport.assertContains;
import static io.github.inertia4j.typescript.GeneratorTestSupport.generate;

class KotlinTest {
    @Test
    void kotlinNullability_isReadFromMetadata() {
        GenerationResult result = generate("kotlin");

        assertContains(result.content(), """
            export interface UserProps {
              name: string
              nickname: string | null
              tags: string[]
              count: number
              age: number | null
            }
            """);
    }
}
