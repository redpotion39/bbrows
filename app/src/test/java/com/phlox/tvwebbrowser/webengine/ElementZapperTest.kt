package com.phlox.tvwebbrowser.webengine

import android.content.Context
import com.phlox.tvwebbrowser.AppContext
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ElementZapperTest {
    private val context: Context get() = AppContext.get()

    @Before
    fun setUp() {
        context.getSharedPreferences("element_zapper", Context.MODE_PRIVATE).edit().clear().commit()
    }

    @Test
    fun storesAndClearsSelectorsPerHost() {
        assertFalse(ElementZapper.hasSelectors(context, "Example.com"))
        ElementZapper.addSelector(context, "Example.com", "#floating-banner", incognito = false)
        ElementZapper.addSelector(context, "example.com", ".sticky-popup", incognito = false)
        assertTrue(ElementZapper.hasSelectors(context, "example.com"))
        assertEquals(listOf("#floating-banner", ".sticky-popup"), ElementZapper.selectorsFor(context, "example.com"))
        assertTrue(ElementZapper.cssFor(context, "example.com").contains("#floating-banner, .sticky-popup { display: none !important; }"))

        ElementZapper.clearHost(context, "example.com")
        assertFalse(ElementZapper.hasSelectors(context, "example.com"))
        assertEquals("", ElementZapper.cssFor(context, "example.com"))
    }
}
