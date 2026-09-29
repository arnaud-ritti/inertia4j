package dev.arkoder.inertia4j.typescript;

import dev.arkoder.inertia4j.core.PropertyNaming;
import org.junit.jupiter.api.Test;

import java.util.List;

import static dev.arkoder.inertia4j.typescript.GeneratorTestSupport.assertContains;
import static dev.arkoder.inertia4j.typescript.GeneratorTestSupport.generate;
import static dev.arkoder.inertia4j.typescript.GeneratorTestSupport.options;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TypeMappingTest {
    @Test
    void builtIns() {
        GenerationResult result = generate("builtins");

        assertContains(result.content(), """
            export interface BuiltIns {
              text: string
              letter: string
              id: string
              uri: string
              count: number
              big: number
              ratio: number
              price: number
              active: boolean
              flag: boolean
              createdAt: string
              day: string
              timeout: string
              data: string
              anything: unknown
            }
            """);
        assertTrue(result.warnings().isEmpty(), result.warnings()::toString);
    }

    @Test
    void collections() {
        GenerationResult result = generate("collections");

        assertContains(result.content(), """
            export interface Collections {
              names: string[]
              ids: number[]
              tags: string[]
              grid: number[][]
              counts: { [key: string]: number }
              byId: { [key: number]: string }
              nickname: string | null
              items: Item[]
              anything: unknown[]
            }
            """);
        assertContains(result.content(), """
            export interface Item {
              name: string
            }
            """);
        assertEquals(1, result.warnings().size(), result.warnings()::toString);
        assertTrue(result.warnings().get(0).contains("Collections.anything"));
    }

    @Test
    void enums() {
        GenerationResult result = generate("enums");

        assertContains(result.content(), "export type Priority = 'low' | 'high'\n");
        assertContains(result.content(), "export type Status = 'Active' | 'Archived'\n");
        assertContains(result.content(), """
            export interface Task {
              status: Status
              priority: Priority
              countByStatus: { [key in Status]?: number }
            }
            """);
    }

    @Test
    void generics() {
        GenerationResult result = generate("generics");

        assertContains(result.content(), """
            export interface Page<T> {
              items: T[]
              total: number
            }
            """);
        assertContains(result.content(), """
            export interface ItemsIndex {
              page: Page<Item>
              rawPage: Page<unknown>
            }
            """);
    }

    @Test
    void inertiaWrappers() {
        GenerationResult result = generate("wrappers");

        assertContains(result.content(), """
            export interface StatsProps {
              stats?: Stat[]
              feed?: Stat[]
              total: number
              history?: Stat[]
            }
            """);
    }

    @Test
    void nullability() {
        GenerationResult result = generate("nullability");

        assertContains(result.content(), """
            export interface Profile {
              nickname: string | null
              name: string
              age: number
            }
            """);
        assertContains(result.content(), """
            export interface Compact {
              nickname?: string
              name: string
            }
            """);
    }

    @Test
    void nullableByDefault() {
        GeneratorOptions defaults = options("nullbydefault");
        GenerationResult result = generate(new GeneratorOptions(
            defaults.packages(),
            defaults.propertyNaming(),
            defaults.errorValueType(),
            true
        ));

        assertContains(result.content(), """
            export interface Account {
              email: string | null
              id: string
              age: number
            }
            """);
        assertContains(result.content(), """
            export interface Marked {
              name: string
            }
            """);
    }

    @Test
    void naming() {
        GeneratorOptions defaults = options("naming");
        GenerationResult result = generate(new GeneratorOptions(
            defaults.packages(),
            PropertyNaming.Snake,
            defaults.errorValueType(),
            false
        ));

        assertContains(result.content(), """
            export interface Renamed {
              full_name: string
              first_name: string
            }
            """);
    }

    @Test
    void inheritance_isFlattened() {
        GenerationResult result = generate("inheritance");

        assertContains(result.content(), """
            export interface Child {
              id: number
              name: string
            }
            """);
        assertFalse(result.content().contains("export interface Base"));
    }

    @Test
    void genericSuperclass_resolvesInheritedTypeVariables() {
        GenerationResult result = generate("genericinheritance");

        assertContains(result.content(), """
            export interface ConcreteChild {
              value: string
              name: string
            }
            """);
    }

    @Test
    void rawGenericSuperclass_mapsInheritedTypeVariableToUnknown() {
        GenerationResult result = generate("genericinheritance");

        assertContains(result.content(), """
            export interface RawChild {
              value: unknown
            }
            """);
        assertTrue(result.warnings().stream().anyMatch(warning -> warning.contains("RawChild.value")), result.warnings()::toString);
    }

    @Test
    void typeOutsidePackages_isUnknownWithWarning() {
        GenerationResult result = generate("external");

        assertContains(result.content(), """
            export interface UsesExternal {
              locale: unknown
            }
            """);
        assertEquals(1, result.warnings().size());
        assertTrue(result.warnings().get(0).contains("java.util.Locale"));
        assertTrue(result.warnings().get(0).contains("UsesExternal.locale"));
    }

    @Test
    void duplicateTypeName_isRejected() {
        GenerationException exception = assertThrows(GenerationException.class, () -> generate("collision"));

        assertTrue(exception.getMessage().contains("fixtures.collision.a.Item"));
        assertTrue(exception.getMessage().contains("fixtures.collision.b.Item"));
        assertTrue(exception.getMessage().contains("@TypeScriptName"));
    }

    @Test
    void typeScriptName_resolvesCollision() {
        GenerationResult result = generate("renamed");

        assertContains(result.content(), """
            export interface Both {
              first: Item
              second: OtherItem
            }
            """);
        assertContains(result.content(), "export interface OtherItem {\n  id: number\n}\n");
    }

    @Test
    void reservedName_isRejected() {
        GenerationException exception = assertThrows(GenerationException.class, () -> generate("reserved"));

        assertTrue(exception.getMessage().contains("InertiaPages"));
    }

    @Test
    void unsupportedMapKey_isRejected() {
        GenerationException exception = assertThrows(GenerationException.class, () -> generate("badkey"));

        assertTrue(exception.getMessage().contains("BadKey.weird"));
    }

    @Test
    void emptyPackages_isRejected() {
        GeneratorOptions noPackages = new GeneratorOptions(List.of(), PropertyNaming.Camel, ErrorValueType.String, false);

        assertThrows(GenerationException.class, () -> generate(noPackages));
    }
}
