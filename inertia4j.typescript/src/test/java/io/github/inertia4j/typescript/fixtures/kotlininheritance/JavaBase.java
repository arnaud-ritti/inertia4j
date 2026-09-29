package io.github.inertia4j.typescript.fixtures.kotlininheritance;

import org.jspecify.annotations.Nullable;

public class JavaBase {
    private final @Nullable String nickname;
    private final String title;

    public JavaBase(@Nullable String nickname, String title) {
        this.nickname = nickname;
        this.title = title;
    }

    public @Nullable String getNickname() {
        return nickname;
    }

    public String getTitle() {
        return title;
    }
}
