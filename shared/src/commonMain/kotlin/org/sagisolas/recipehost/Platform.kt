package org.sagisolas.recipehost

interface Platform {
    val name: String
}

expect fun getPlatform(): Platform