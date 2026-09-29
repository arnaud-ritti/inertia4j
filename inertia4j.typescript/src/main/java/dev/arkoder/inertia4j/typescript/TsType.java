package dev.arkoder.inertia4j.typescript;

import java.util.List;
import java.util.stream.Collectors;

/**
 * A TypeScript type expression.
 */
sealed interface TsType {
    TsType StringKeyword = new Keyword("string");
    TsType NumberKeyword = new Keyword("number");
    TsType BooleanKeyword = new Keyword("boolean");
    TsType UnknownKeyword = new Keyword("unknown");

    String render();

    static TsType nullable(TsType type) {
        if (type instanceof Nullable || type == UnknownKeyword) {
            return type;
        }

        return new Nullable(type);
    }

    record Keyword(String keyword) implements TsType {
        @Override
        public String render() {
            return keyword;
        }
    }

    record Array(TsType element) implements TsType {
        @Override
        public String render() {
            if (element instanceof Nullable) {
                return "(" + element.render() + ")[]";
            }

            return element.render() + "[]";
        }
    }

    record StringMap(TsType value) implements TsType {
        @Override
        public String render() {
            return "{ [key: string]: " + value.render() + " }";
        }
    }

    record NumberMap(TsType value) implements TsType {
        @Override
        public String render() {
            return "{ [key: number]: " + value.render() + " }";
        }
    }

    record EnumMap(TsType key, TsType value) implements TsType {
        @Override
        public String render() {
            return "{ [key in " + key.render() + "]?: " + value.render() + " }";
        }
    }

    record Nullable(TsType inner) implements TsType {
        @Override
        public String render() {
            return inner.render() + " | null";
        }
    }

    record Reference(String name, List<TsType> arguments) implements TsType {
        @Override
        public String render() {
            if (arguments.isEmpty()) {
                return name;
            }

            return name + "<" + arguments.stream().map(TsType::render).collect(Collectors.joining(", ")) + ">";
        }
    }

    record Variable(String name) implements TsType {
        @Override
        public String render() {
            return name;
        }
    }
}
