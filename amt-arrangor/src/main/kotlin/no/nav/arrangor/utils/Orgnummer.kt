package no.nav.arrangor.utils

object Orgnummer {
    /**
     * Norske organisasjonsnummer starter på 8 eller 9, men andre systemer sender oss
     * noen ganger utenlandske organisasjoner med fiktive nummer som starter på 1.
     */
    private val REGEX = Regex("""[189]\d{8}""")

    fun erGyldig(value: String): Boolean = REGEX.matches(value)
}
