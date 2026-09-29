package dev.arkoder.inertia4j.typescript;

import org.junit.jupiter.api.Test;

import static dev.arkoder.inertia4j.typescript.GeneratorTestSupport.assertContains;
import static dev.arkoder.inertia4j.typescript.GeneratorTestSupport.generate;
import static dev.arkoder.inertia4j.typescript.GeneratorTestSupport.options;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RolesTest {
    @Test
    void pagesAndSharedProps() {
        String content = generate("roles").content();

        assertContains(content, """
            export interface InertiaPages {
              'Home': HomeProps
              'Users/Index': UsersIndexProps
            }
            """);
        assertContains(content, "    sharedPageProps: AppShared & AuthShared\n");
        assertContains(content, "    errorValueType: string\n");
    }

    @Test
    void errorValueType_stringArray() {
        GeneratorOptions defaults = options("roles");
        String content = generate(new GeneratorOptions(
            defaults.packages(),
            defaults.propertyNaming(),
            ErrorValueType.StringArray,
            false
        )).content();

        assertContains(content, "    errorValueType: string[]\n");
    }

    @Test
    void withoutSharedClasses_sharedPagePropsIsOmitted() {
        assertFalse(generate("builtins").content().contains("sharedPageProps"));
    }

    @Test
    void flashClasses_typeFlashDataWithOptionalProperties() {
        String content = generate("flash").content();

        assertContains(content, """
            export interface AppFlash {
              message?: string
              toast?: Toast
            }
            """);
        assertContains(content, """
            export interface Toast {
              type: string
              message: string
            }
            """);
        assertContains(content, "    flashDataType: AppFlash & NotificationFlash\n");
    }

    @Test
    void withoutFlashClasses_flashDataTypeIsOmitted() {
        assertFalse(generate("roles").content().contains("flashDataType"));
    }

    @Test
    void duplicateComponent_isRejected() {
        GenerationException exception = assertThrows(GenerationException.class, () -> generate("duplicatepage"));

        assertTrue(exception.getMessage().contains("FirstHome"));
        assertTrue(exception.getMessage().contains("SecondHome"));
        assertTrue(exception.getMessage().contains("'Home'"));
    }

    @Test
    void genericPage_isRejected() {
        GenerationException exception = assertThrows(GenerationException.class, () -> generate("genericpage"));

        assertTrue(exception.getMessage().contains("dev.arkoder.inertia4j.typescript.fixtures.genericpage.GenericPage"), exception.getMessage());
        assertTrue(exception.getMessage().contains("type parameters"), exception.getMessage());
    }

    @Test
    void blankComponent_isRejected() {
        GenerationException exception = assertThrows(GenerationException.class, () -> generate("blankpage"));

        assertTrue(exception.getMessage().contains("Blank"));
    }
}
