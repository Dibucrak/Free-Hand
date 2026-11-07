package com.example.hakatron

interface Platform {
    val name: String
}

expect fun getPlatform(): Platform