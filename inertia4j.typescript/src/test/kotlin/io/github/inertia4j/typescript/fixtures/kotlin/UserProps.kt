package io.github.inertia4j.typescript.fixtures.kotlin

import io.github.inertia4j.annotations.InertiaPage

@InertiaPage("Users/Show")
data class UserProps(
    val name: String,
    val nickname: String?,
    val tags: List<String>,
    val count: () -> Int,
    val age: Int?,
)
