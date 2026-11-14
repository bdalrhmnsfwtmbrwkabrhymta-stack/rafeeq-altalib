package com.example.domain.pdf

enum class PenType(val displayNameAr: String, val defaultWidth: Float) {
    BALLPOINT("قلم جاف", 5f),
    FOUNTAIN("قلم حبر", 7f),
    HIGHLIGHTER("قلم تظليل", 22f)
}
