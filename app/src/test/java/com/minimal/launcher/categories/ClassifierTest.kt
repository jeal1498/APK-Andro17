package com.minimal.launcher.categories

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ClassifierTest {

    private val targets = BuiltinCategory.entries.mapIndexed { i, c -> Classifier.Target(i + 1L, c.key, emptyList()) }
    private fun idOf(c: BuiltinCategory) = targets.first { it.builtinKey == c.key }.id
    private fun classify(label: String, pkg: String, sys: Int = Classifier.CATEGORY_UNDEFINED, t: List<Classifier.Target> = targets) =
        Classifier.classify(Classifier.AppSignals(label, pkg, sys), t)

    @Test fun knownPackagesAreClassified() {
        assertEquals(idOf(BuiltinCategory.COMMUNICATION), classify("WhatsApp", "com.whatsapp"))
        assertEquals(idOf(BuiltinCategory.SOCIAL), classify("Instagram", "com.instagram.android"))
        assertEquals(idOf(BuiltinCategory.NAVIGATION), classify("Maps", "com.google.android.apps.maps"))
        assertEquals(idOf(BuiltinCategory.ENTERTAINMENT), classify("YouTube", "com.google.android.youtube"))
        assertEquals(idOf(BuiltinCategory.PHOTOGRAPHY), classify("Fotos", "com.google.android.apps.photos"))
        assertEquals(idOf(BuiltinCategory.TOOLS), classify("Ajustes", "com.android.settings"))
        assertEquals(idOf(BuiltinCategory.WORK), classify("Slack", "com.Slack"))
    }

    @Test fun packagePrefixMatchesPublishers() {
        assertEquals(idOf(BuiltinCategory.GAMES), classify("Clash Royale", "com.supercell.clashroyale"))
        assertEquals(idOf(BuiltinCategory.SHOPPING), classify("Mercado Libre", "com.mercadolibre.android"))
    }

    @Test fun keywordsInPackageAndLabel() {
        assertEquals(idOf(BuiltinCategory.FINANCE), classify("Mi Banco", "mx.example.banking"))
        assertEquals(idOf(BuiltinCategory.EDUCATION), classify("Aprender Inglés", "org.example.lingo"))
        assertEquals(idOf(BuiltinCategory.PHOTOGRAPHY), classify("Super Cámara HD", "com.foo.bar"))
        assertEquals(idOf(BuiltinCategory.TOOLS), classify("QR Scanner", "com.acme.qr"))
    }

    @Test fun packageKeywordBeatsLabelKeyword() {
        // Package token "bank" (60) beats label word "music" (50).
        assertEquals(idOf(BuiltinCategory.FINANCE), classify("Music Rewards", "com.acme.bank"))
    }

    @Test fun systemCategoryIsUsedAsFallback() {
        assertEquals(idOf(BuiltinCategory.GAMES), classify("Zorblax", "com.zorblax.app", Classifier.CATEGORY_GAME))
        assertEquals(idOf(BuiltinCategory.NAVIGATION), classify("Zorblax", "com.zorblax.app", Classifier.CATEGORY_MAPS))
    }

    @Test fun unknownAppIsUnclassified() {
        assertNull(classify("Zorblax", "com.zorblax.app"))
    }

    @Test fun userKeywordsTargetCustomCategories() {
        val custom = Classifier.Target(99, null, listOf("zorblax", "com.acme.", "mi club"))
        val all = targets + custom
        assertEquals(99L, classify("Zorblax", "com.zorblax.app", t = all))
        assertEquals(99L, classify("Anything", "com.acme.tool", t = all))
        assertEquals(99L, classify("Mi Club Deportivo", "org.sports", t = all))
        // User keyword (90) beats built-in keyword (60).
        val gamesOverride = Classifier.Target(100, null, listOf("chess"))
        assertEquals(100L, classify("Chess", "com.chess.app", t = targets + gamesOverride))
    }

    @Test fun deletedBuiltinCategoryIsSkipped() {
        val withoutComm = targets.filterNot { it.builtinKey == BuiltinCategory.COMMUNICATION.key }
        assertNull(classify("WhatsApp", "com.whatsapp", t = withoutComm))
    }

    @Test fun normalizeRemovesAccentsAndCase() {
        assertEquals("camara fotografica", Classifier.normalize("Cámara Fotográfica"))
        assertEquals(listOf("super", "camera", "hd"), Classifier.tokenize("superCamera2HD"))
    }

    @Test fun genericTokensDoNotClassify() {
        assertNull(classify("Thing", "com.google.android.apps.thing"))
    }
}
