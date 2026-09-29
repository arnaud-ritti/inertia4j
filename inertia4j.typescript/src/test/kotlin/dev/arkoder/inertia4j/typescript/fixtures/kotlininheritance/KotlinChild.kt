package dev.arkoder.inertia4j.typescript.fixtures.kotlininheritance

import dev.arkoder.inertia4j.annotations.InertiaPage

open class KotlinBase(val nickname: String?, val label: String)

@InertiaPage("Kotlin/Child")
class KotlinChild(nickname: String?, label: String, val age: Int?) : KotlinBase(nickname, label)

@InertiaPage("Java/Child")
class KotlinChildOfJava(nickname: String?, title: String, val score: Int?) : JavaBase(nickname, title)
