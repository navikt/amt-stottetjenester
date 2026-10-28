package no.nav.amt_arena_ords_proxy.utils

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class OrgNrUtilsTest {

	@Test
	fun `orgNrtoStr() should add correct amount of '0'`() {
		assertEquals("000001234", OrgNrUtils.orgNrtoStr(1234))
		assertEquals("012345678", OrgNrUtils.orgNrtoStr(12345678))
		assertEquals("123456789", OrgNrUtils.orgNrtoStr(123456789))
		assertEquals("1234567890", OrgNrUtils.orgNrtoStr(1234567890))
	}
}
