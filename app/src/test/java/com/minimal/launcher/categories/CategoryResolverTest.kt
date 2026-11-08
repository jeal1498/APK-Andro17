package com.minimal.launcher.categories

import android.content.ComponentName
import android.os.UserHandle
import com.minimal.launcher.applications.AppInfo
import com.minimal.launcher.data.db.AppAssignmentEntity
import com.minimal.launcher.data.db.CategoryEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class CategoryResolverTest {

    private val categories = BuiltinCategory.entries.mapIndexed { i, c ->
        CategoryEntity(id = i + 1L, name = c.displayName, iconKey = c.iconKey, position = i, builtinKey = c.key)
    }
    private fun cat(c: BuiltinCategory) = categories.first { it.builtinKey == c.key }.id

    private fun app(pkg: String, label: String) = AppInfo(
        key = "$pkg/.Main#0", label = label, component = ComponentName(pkg, "$pkg.Main"),
        user = UserHandle.getUserHandleForUid(0), userSerial = 0, systemCategory = -1, firstInstallTime = 0, isWorkProfile = false,
    )

    private val whatsapp = app("com.whatsapp", "WhatsApp")
    private val unknown = app("com.zorblax", "Zorblax")

    @Test fun automaticWhenNoManualAssignment() {
        val r = CategoryResolver.resolve(whatsapp, CategoryResolver.targets(categories), emptySet(), emptyMap())
        assertEquals(AssignmentSource.AUTOMATIC, r.source)
        assertEquals(setOf(cat(BuiltinCategory.COMMUNICATION)), r.categoryIds)
    }

    @Test fun manualAssignmentWinsAndSupportsMultipleCategories() {
        val manual = mapOf(whatsapp.key to setOf(cat(BuiltinCategory.WORK), cat(BuiltinCategory.SOCIAL)))
        val r = CategoryResolver.resolve(whatsapp, CategoryResolver.targets(categories), setOf(whatsapp.key), manual)
        assertEquals(AssignmentSource.MANUAL, r.source)
        assertEquals(setOf(cat(BuiltinCategory.WORK), cat(BuiltinCategory.SOCIAL)), r.categoryIds)
    }

    @Test fun manualEmptyMeansUnclassifiedAndIsNotOverriddenByRules() {
        val r = CategoryResolver.resolve(whatsapp, CategoryResolver.targets(categories), setOf(whatsapp.key), emptyMap())
        assertEquals(AssignmentSource.UNCLASSIFIED, r.source)
        assertTrue(r.categoryIds.isEmpty())
    }

    @Test fun groupPutsUnknownAppsInUnclassified() {
        val groups = CategoryResolver.group(listOf(whatsapp, unknown), categories, emptyList(), emptySet())
        assertEquals(categories.size + 1, groups.size)
        val unclassified = groups.last()
        assertTrue(unclassified.isUnclassified)
        assertEquals(listOf(unknown), unclassified.apps)
        assertEquals(listOf(whatsapp), groups.first { it.id == cat(BuiltinCategory.COMMUNICATION) }.apps)
    }

    @Test fun automaticMultiCategoryAndManualOverridesSpecialRules() {
        val gemini = app("com.google.android.apps.bard", "Gemini")
        val targets = CategoryResolver.targets(categories)
        val auto = CategoryResolver.resolve(gemini, targets, emptySet(), emptyMap())
        assertEquals(listOf(cat(BuiltinCategory.AI), cat(BuiltinCategory.GOOGLE)), auto.categoryIds.toList())
        // A later manual assignment is never overwritten by the special rules.
        val manual = mapOf(gemini.key to setOf(cat(BuiltinCategory.WORK)))
        val r = CategoryResolver.resolve(gemini, targets, setOf(gemini.key), manual)
        assertEquals(AssignmentSource.MANUAL, r.source)
        assertEquals(setOf(cat(BuiltinCategory.WORK)), r.categoryIds)
        val groups = CategoryResolver.group(listOf(gemini), categories, emptyList(), emptySet())
        assertEquals(listOf(gemini), groups.first { it.id == cat(BuiltinCategory.AI) }.apps)
        assertEquals(listOf(gemini), groups.first { it.id == cat(BuiltinCategory.GOOGLE) }.apps)
        assertEquals(2, groups.sumOf { g -> g.apps.count { it == gemini } })
    }

    @Test fun groupAppliesManualMultiAssignment() {
        val assignments = listOf(
            AppAssignmentEntity(unknown.key, cat(BuiltinCategory.HEALTH)),
            AppAssignmentEntity(unknown.key, cat(BuiltinCategory.TOOLS)),
        )
        val groups = CategoryResolver.group(listOf(unknown), categories, assignments, setOf(unknown.key))
        assertEquals(listOf(unknown), groups.first { it.id == cat(BuiltinCategory.HEALTH) }.apps)
        assertEquals(listOf(unknown), groups.first { it.id == cat(BuiltinCategory.TOOLS) }.apps)
        assertTrue(groups.last().apps.isEmpty())
    }

    @Test fun assignmentsToDeletedCategoriesAreIgnored() {
        val remaining = categories.filterNot { it.builtinKey == BuiltinCategory.HEALTH.key }
        val assignments = listOf(AppAssignmentEntity(unknown.key, cat(BuiltinCategory.HEALTH)))
        val groups = CategoryResolver.group(listOf(unknown), remaining, assignments, setOf(unknown.key))
        assertEquals(listOf(unknown), groups.last().apps)
    }
}
