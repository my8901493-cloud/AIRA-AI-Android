package com.aira.assistant.data

import com.aira.assistant.data.repository.MemoryRepository
import com.aira.assistant.domain.model.MemoryCategory
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MemoryRepositoryTest {

    @Test
    fun deduplicatesAndUpdatesContradictoryMemories() = runTest {
        val repo = MemoryRepository(memoryDao = null)

        repo.saveMemory(MemoryCategory.USER_PREFERENCE, "beverage", "User prefers green tea")
        repo.saveMemory(MemoryCategory.USER_PREFERENCE, "beverage", "User prefers black coffee")

        val all = repo.observeMemories().first()
        assertEquals(1, all.size)
        assertEquals("User prefers black coffee", all.first().content)
        assertEquals(2, all.first().accessCount)
    }

    @Test
    fun blocksSensitivePasswordsAndCardNumbers() = runTest {
        val repo = MemoryRepository(memoryDao = null)

        val result1 = repo.saveMemory(MemoryCategory.IMPORTANT_FACT, "bank_pin", "My password is Secret123")
        val result2 = repo.saveMemory(MemoryCategory.IMPORTANT_FACT, "card", "4532-1122-3344-5566")

        assertTrue(result1.isFailure)
        assertTrue(result2.isFailure)
        assertEquals(0, repo.observeMemories().first().size)
    }

    @Test
    fun extractsImplicitMemoriesFromHindiHinglishAndEnglish() = runTest {
        val repo = MemoryRepository(memoryDao = null)

        repo.extractAndStoreImplicitMemories("Mera naam Aarav hai")
        repo.extractAndStoreImplicitMemories("Remember that dentist appointment is on Monday at 5 PM")

        val relevant = repo.retrieveRelevantMemories("dentist appointment")
        assertTrue(relevant.any { it.content.contains("Aarav") })
        assertTrue(relevant.any { it.content.contains("dentist") })
    }
}
