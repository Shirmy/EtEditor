package com.eteditor

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FetchCatalogRuleUtilsTest {

    @Test
    fun parseReadsCategoryEnabledWithDefaults() {
        val json = """
            [
              {"name":"去括号","search":"【.*?】","replacement":"","regex":true,"category":"净化"},
              {"name":"滤单章","search":"单章","category":"章节","enabled":false}
            ]
        """.trimIndent()
        val items = parseFetchCatalogRuleItems(json)
        assertEquals(2, items.size)
        assertEquals("去括号", items[0].name)
        assertEquals(FETCH_CATALOG_RULE_CATEGORY_PURIFY, items[0].category)
        assertTrue(items[0].regex)
        assertTrue(items[0].enabled)
        assertEquals(FETCH_CATALOG_RULE_CATEGORY_CHAPTER, items[1].category)
        assertEquals(false, items[1].enabled)
    }

    @Test
    fun parseReturnsEmptyForNonJsonOrBlank() {
        assertTrue(parseFetchCatalogRuleItems("").isEmpty())
        assertTrue(parseFetchCatalogRuleItems("dropContains:单章").isEmpty())
    }

    @Test
    fun serializeRoundTripsThroughParse() {
        val items = listOf(
            FetchCatalogRuleItem(0, FETCH_CATALOG_RULE_CATEGORY_CHAPTER, "去序号", "^\\d+\\s+", "", true, true),
            FetchCatalogRuleItem(1, FETCH_CATALOG_RULE_CATEGORY_PURIFY, "滤公告", "公告", "", false, false)
        )
        val text = serializeFetchCatalogRuleItems(items)
        val parsed = parseFetchCatalogRuleItems(text)
        assertEquals(items, parsed)
    }

    @Test
    fun addAppendsRuleWithGivenCategory() {
        val text = addFetchCatalogRule("", FETCH_CATALOG_RULE_CATEGORY_PURIFY, "去括号", "【.*?】", "", true)
        val items = parseFetchCatalogRuleItems(text)
        assertEquals(1, items.size)
        assertEquals("去括号", items[0].name)
        assertEquals(FETCH_CATALOG_RULE_CATEGORY_PURIFY, items[0].category)
    }

    @Test
    fun updateReplacesFieldsAtIndex() {
        val text = addFetchCatalogRule("", FETCH_CATALOG_RULE_CATEGORY_PURIFY, "a", "x", "", false)
        val updated = updateFetchCatalogRule(text, 0, FETCH_CATALOG_RULE_CATEGORY_CHAPTER, "b", "y", "z", true)
        val item = parseFetchCatalogRuleItems(updated).single()
        assertEquals(FETCH_CATALOG_RULE_CATEGORY_CHAPTER, item.category)
        assertEquals("b", item.name)
        assertEquals("y", item.search)
        assertEquals("z", item.replacement)
        assertEquals(true, item.regex)
    }

    @Test
    fun setEnabledTogglesFlag() {
        val text = addFetchCatalogRule("", FETCH_CATALOG_RULE_CATEGORY_PURIFY, "a", "x", "", false)
        val disabled = setFetchCatalogRuleEnabled(text, 0, false)
        assertEquals(false, parseFetchCatalogRuleItems(disabled).single().enabled)
    }

    @Test
    fun deleteRemovesRuleAtIndex() {
        var text = addFetchCatalogRule("", FETCH_CATALOG_RULE_CATEGORY_PURIFY, "a", "x", "", false)
        text = addFetchCatalogRule(text, FETCH_CATALOG_RULE_CATEGORY_PURIFY, "b", "y", "", false)
        val after = deleteFetchCatalogRule(text, 0)
        assertEquals(listOf("b"), parseFetchCatalogRuleItems(after).map { it.name })
    }

    @Test
    fun moveReordersWithinList() {
        var text = addFetchCatalogRule("", FETCH_CATALOG_RULE_CATEGORY_PURIFY, "a", "x", "", false)
        text = addFetchCatalogRule(text, FETCH_CATALOG_RULE_CATEGORY_PURIFY, "b", "y", "", false)
        val moved = moveFetchCatalogRule(text, 1, 0)
        assertEquals(listOf("b", "a"), parseFetchCatalogRuleItems(moved).map { it.name })
    }
}
