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

    @Test
    void kotlinNullability_ofPropertiesInheritedFromKotlinBase_isReadFromBaseMetadata() {
        GenerationResult result = generate("kotlininheritance");

        assertContains(result.content(), """
            export interface KotlinChild {
              nickname: string | null
              label: string
              age: number | null
            }
            """);
    }

    @Test
    void nullability_ofPropertiesInheritedFromJavaBase_followsJavaAnnotations() {
        GenerationResult result = generate("kotlininheritance");

        assertContains(result.content(), """
            export interface KotlinChildOfJava {
              nickname: string | null
              title: string
              score: number | null
            }
            """);
    }
}
