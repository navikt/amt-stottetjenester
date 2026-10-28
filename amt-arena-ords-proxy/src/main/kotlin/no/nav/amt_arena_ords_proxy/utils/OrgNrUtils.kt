package no.nav.amt_arena_ords_proxy.utils

object OrgNrUtils {
	fun orgNrtoStr(orgNr: Int?): String {
		val strBuilder = StringBuilder(orgNr.toString())

		// Dette går utifra at vi kun håndterer norske orgnr som alltid er 9 tegn
		while (strBuilder.length < 9) {
			strBuilder.insert(0, '0')
		}

		return strBuilder.toString()
	}
}
