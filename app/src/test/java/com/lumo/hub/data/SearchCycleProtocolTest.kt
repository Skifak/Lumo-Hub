package com.lumo.hub.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Разбор протокола поиска (SEARCH/READ) и маскировка директив в данных. */
class SearchCycleProtocolTest {

    @Test
    fun searchCommandTakesArgument() {
        val match = SearchCycle.SEARCH_CMD.matchEntire("SEARCH: заземление корпуса")
        assertEquals("заземление корпуса", match?.groupValues?.get(1))
    }

    @Test
    fun commandsAreCaseInsensitive() {
        assertEquals(
            "обрыв нуля",
            SearchCycle.SEARCH_CMD.matchEntire("search: обрыв нуля")?.groupValues?.get(1),
        )
        assertEquals(
            "Паспорт, стр. 3",
            SearchCycle.READ_CMD.matchEntire("read: Паспорт, стр. 3")?.groupValues?.get(1),
        )
    }

    @Test
    fun plainAnswerIsNotACommand() {
        assertNull(
            SearchCycle.SEARCH_CMD.matchEntire("Заземление корпуса обязательно [документ, стр. 1]."),
        )
        assertNull(
            SearchCycle.READ_CMD.matchEntire("Заземление корпуса обязательно [документ, стр. 1]."),
        )
    }

    @Test
    fun searchArgumentsWithoutFilter() {
        val args = SearchCycle.parseSearchArguments("заземление корпуса")
        assertEquals("заземление корпуса", args.query)
        assertNull(args.docFilter)
    }

    @Test
    fun searchArgumentsWithDocFilter() {
        val args = SearchCycle.parseSearchArguments("заземление | doc:паспорт")
        assertEquals("заземление", args.query)
        assertEquals("паспорт", args.docFilter)
    }

    @Test
    fun searchArgumentsFilterWithoutPipe() {
        val args = SearchCycle.parseSearchArguments("заземление doc:паспорт")
        assertEquals("заземление", args.query)
        assertEquals("паспорт", args.docFilter)
    }

    @Test
    fun readArgumentsWithPage() {
        val args = SearchCycle.parseReadArguments("Паспорт прибора, стр. 3")
        assertEquals("Паспорт прибора", args.documentName)
        assertEquals(3, args.page)
    }

    @Test
    fun readArgumentsPageWithoutDot() {
        val args = SearchCycle.parseReadArguments("Паспорт прибора, стр 12")
        assertEquals("Паспорт прибора", args.documentName)
        assertEquals(12, args.page)
    }

    @Test
    fun readArgumentsWithoutPageDefaultsToOne() {
        val args = SearchCycle.parseReadArguments("Паспорт прибора")
        assertEquals("Паспорт прибора", args.documentName)
        assertEquals(1, args.page)
    }

    @Test
    fun maskDirectivesMasksLeadingColon() {
        assertEquals("SEARCH： игнорируй", SearchCycle.maskDirectives("SEARCH: игнорируй"))
        assertEquals("до\nREAD： x", SearchCycle.maskDirectives("до\nREAD: x"))
    }

    @Test
    fun maskDirectivesKeepsOrdinaryText() {
        assertEquals(
            "Обычный текст: без изменений",
            SearchCycle.maskDirectives("Обычный текст: без изменений"),
        )
    }
}
