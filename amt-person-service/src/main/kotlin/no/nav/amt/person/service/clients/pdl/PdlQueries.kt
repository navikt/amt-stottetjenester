package no.nav.amt.person.service.clients.pdl

object PdlQueries {
    data class Variables(
        val ident: String,
    )

    data class PdlErrorDetails(
        val type: String? = null,
        val cause: String? = null,
        val policy: String? = null,
    )

    data class HentPersonResult(
        val falskIdentitet: Attribute.FalskIdentitet?,
        val navn: List<Attribute.Navn>,
        val telefonnummer: List<Attribute.Telefonnummer>,
        val adressebeskyttelse: List<Attribute.Adressebeskyttelse>,
        val bostedsadresse: List<Attribute.Bostedsadresse>,
        val oppholdsadresse: List<Attribute.Oppholdsadresse>,
        val kontaktadresse: List<Attribute.Kontaktadresse>,
    )

    data class HentIdenterResult(
        val identer: List<Attribute.Ident>,
    )

    data class HentPersonFoedselsdatoResult(
        val foedselsdato: List<Attribute.Foedselsdato>,
    )

    object Attribute {
        data class Adressebeskyttelse(
            val gradering: String,
        )

        data class Navn(
            val fornavn: String,
            val mellomnavn: String?,
            val etternavn: String,
        )

        data class Foedselsdato(
            val foedselsaar: Int,
        )

        data class FalskIdentitet(
            val erFalsk: Boolean,
        )

        data class Ident(
            val ident: String,
            val historisk: Boolean,
            val gruppe: String,
        )

        data class Telefonnummer(
            val landskode: String,
            val nummer: String,
            val prioritet: Int,
        )

        data class Bostedsadresse(
            val coAdressenavn: String?,
            val vegadresse: Vegadresse?,
            val matrikkeladresse: Matrikkeladresse?,
        )

        data class Oppholdsadresse(
            val coAdressenavn: String?,
            val vegadresse: Vegadresse?,
            val matrikkeladresse: Matrikkeladresse?,
        )

        data class Kontaktadresse(
            val coAdressenavn: String?,
            val vegadresse: Vegadresse?,
            val postboksadresse: Postboksadresse?,
        )

        data class Vegadresse(
            val husnummer: String?,
            val husbokstav: String?,
            val adressenavn: String?,
            val tilleggsnavn: String?,
            val postnummer: String?,
        )

        data class Matrikkeladresse(
            val tilleggsnavn: String?,
            val postnummer: String?,
        )

        data class Postboksadresse(
            val postboks: String,
            val postnummer: String?,
        )
    }
}
