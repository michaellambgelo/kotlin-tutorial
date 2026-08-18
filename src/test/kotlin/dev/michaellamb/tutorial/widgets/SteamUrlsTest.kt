package dev.michaellamb.tutorial.widgets

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class SteamUrlsTest {

    @Test
    fun `art url uses library art, the only path that serves across the whole catalogue`() {
        assertEquals(
            "https://cdn.cloudflare.steamstatic.com/steam/apps/440/library_600x900.jpg",
            steamArtUrl(440),
        )
    }

    @Test
    fun `store url points at the public app page`() {
        assertEquals("https://store.steampowered.com/app/440", steamStoreUrl(440))
    }

    @Test
    fun `icon url is built from the appid and the img_icon_url hash`() {
        assertEquals(
            "https://media.steampowered.com/steamcommunity/public/images/apps/440/abc123.jpg",
            steamIconUrl(440, "abc123"),
        )
    }

    @Test
    fun `icon url is null when the API omitted the hash`() {
        assertNull(steamIconUrl(440, null))
    }
}
