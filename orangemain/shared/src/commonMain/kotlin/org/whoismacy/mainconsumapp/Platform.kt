package org.whoismacy.mainconsumapp

interface Platform {
    val name: String
}

expect fun getPlatform(): Platform