package com.minimal.launcher.search

import android.content.Intent
import android.provider.Settings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class UniversalSearchTest {
    private val ana = Contact(1, "Ana López", "+52 55 1234 5678")
    private val anabel = Contact(2, "Anabel", "5550001")

    @Test fun settingsAreFoundByNameOrSynonymWithoutAccents() {
        assertEquals(Settings.ACTION_WIFI_SETTINGS, UniversalSearch.matchSettings("wifi").first().action)
        assertEquals(Settings.ACTION_BLUETOOTH_SETTINGS, UniversalSearch.matchSettings("audífonos").first().action)
        assertEquals("Batería", UniversalSearch.matchSettings("bateria").first().title)
        assertTrue(UniversalSearch.matchSettings("x").isEmpty())
    }

    @Test fun callCommandUsesContactsAndNumbers() {
        assertEquals("ana", UniversalSearch.contactQuery("Llamar a ana"))
        val r = UniversalSearch.search("llamar a ana", listOf(ana, anabel))
        val calls = r.filterIsInstance<SearchItem.QuickAction>().filter { it.kind == SearchItem.QuickAction.Kind.CALL }
        assertEquals(listOf("Llamar a Ana López", "Llamar a Anabel"), calls.map { it.title })
        val intent = UniversalSearch.intentFor(calls.first())!!
        assertEquals(Intent.ACTION_DIAL, intent.action) // no CALL_PHONE permission needed
        assertEquals("tel", intent.data!!.scheme)

        val number = UniversalSearch.search("llamar 5551234", emptyList()).first() as SearchItem.QuickAction
        assertEquals("5551234", number.value)
    }

    @Test fun messageNumberContactsFlashlightAndWeb() {
        val msg = UniversalSearch.search("mensaje a anabel", listOf(anabel)).first() as SearchItem.QuickAction
        assertEquals(SearchItem.QuickAction.Kind.MESSAGE, msg.kind)
        assertEquals(Intent.ACTION_SENDTO, UniversalSearch.intentFor(msg)!!.action)

        val plain = UniversalSearch.search("ana", listOf(ana))
        assertTrue(plain.first() is SearchItem.ContactItem)
        assertTrue(UniversalSearch.search("linterna", emptyList()).any { it is SearchItem.QuickAction && it.kind == SearchItem.QuickAction.Kind.FLASHLIGHT })

        val web = UniversalSearch.search("buscar recetas de mole", emptyList()).last() as SearchItem.QuickAction
        assertEquals("recetas de mole", web.value)
        assertEquals(Intent.ACTION_WEB_SEARCH, UniversalSearch.intentFor(web)!!.action)
        assertTrue(UniversalSearch.search("", emptyList()).isEmpty())
    }
}
