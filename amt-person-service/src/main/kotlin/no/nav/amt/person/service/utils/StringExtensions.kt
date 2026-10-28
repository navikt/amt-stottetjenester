package no.nav.amt.person.service.utils

fun String.titlecase(): String = this.split(Regex("(?<=[\\s\\-'])")).joinToString("") { word ->
    if (word.isNotEmpty() && word.first().isLetter()) {
        word.lowercase().replaceFirstChar { it.uppercase() }
    } else {
        word
    }
}
