package dev.arkoder.inertia4j.typescript.fixtures.kotlin

import dev.arkoder.inertia4j.annotations.InertiaPage

@InertiaPage("Users/Show")
data class UserProps(val name: String, val nickname: String?, val tags: List<String>, val count: () -> Int, val age: Int?)
