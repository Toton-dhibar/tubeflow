package io.github.aedev.flow.innertube.utils

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class YouTubeCookieAuthTest {
    @Test
    fun `parses a cookie header and requires all account cookies`() {
        val result =
            YouTubeCookieParser.parse(
                "Cookie: SID=sid-value; SAPISID=sapi-value; __Secure-3PSID=secure-value",
                nowEpochSeconds = 100,
            )

        assertTrue(result is CookieParseResult.Valid)
        assertEquals(
            "SID=sid-value; SAPISID=sapi-value; __Secure-3PSID=secure-value",
            (result as CookieParseResult.Valid).cookieHeader,
        )
    }

    @Test
    fun `parses unexpired YouTube cookies from Netscape export`() {
        val result =
            YouTubeCookieParser.parse(
                """
                # Netscape HTTP Cookie File
                .youtube.com	TRUE	/	TRUE	200	SID	sid-value
                .youtube.com	TRUE	/	TRUE	0	SAPISID	sapi-value
                #HttpOnly_.youtube.com	TRUE	/	TRUE	300	__Secure-3PSID	secure-value
                .example.com	TRUE	/	TRUE	300	ignored	other
                """.trimIndent(),
                nowEpochSeconds = 100,
            )

        assertTrue(result is CookieParseResult.Valid)
        assertEquals(
            setOf("SID", "SAPISID", "__Secure-3PSID"),
            (result as CookieParseResult.Valid).cookies.keys,
        )
    }

    @Test
    fun `rejects expired required cookies and missing required cookies`() {
        val expired =
            YouTubeCookieParser.parse(
                ".youtube.com\tTRUE\t/\tTRUE\t99\tSID\tsid-value\n" +
                    ".youtube.com\tTRUE\t/\tTRUE\t200\tSAPISID\tsapi-value\n" +
                    ".youtube.com\tTRUE\t/\tTRUE\t200\t__Secure-3PSID\tsecure-value",
                nowEpochSeconds = 100,
            )
        val missing = YouTubeCookieParser.parse("SID=sid-value; SAPISID=sapi-value")

        assertEquals(CookieParseResult.RequiredCookieExpired, expired)
        assertEquals(CookieParseResult.MissingRequiredCookies, missing)
    }

    @Test
    fun `builds signed headers using the SAPISIDHASH input format`() {
        val headers =
            YouTubeCookieAuthHeaders.build(
                cookieHeader = "SID=sid; SAPISID=abc; __Secure-3PSID=secure",
                origin = "https://www.youtube.com",
                timestampSeconds = 1_700_000_000,
            )

        assertEquals(
            "SAPISIDHASH 1700000000_27b236f59d4ec583d7530f2c7055d2f9c6aecf92",
            headers["Authorization"],
        )
        assertEquals("https://www.youtube.com", headers["X-Origin"])
        assertEquals("0", headers["X-Goog-AuthUser"])
        assertEquals("SID=sid; SAPISID=abc; __Secure-3PSID=secure", headers["Cookie"])
    }

    @Test
    fun `builds no signed headers without a usable SAPISID`() {
        assertTrue(YouTubeCookieAuthHeaders.build(null, "https://www.youtube.com", 1).isEmpty())
        assertTrue(YouTubeCookieAuthHeaders.build("SID=sid", "https://www.youtube.com", 1).isEmpty())
    }
}
