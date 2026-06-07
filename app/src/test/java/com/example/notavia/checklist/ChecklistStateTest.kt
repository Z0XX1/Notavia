package com.example.notavia.checklist

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ChecklistStateTest {
    @Test
    fun snapshotSplitsIncompleteAndCompletedItems() {
        val state = ChecklistState()
        state.add("Alpha")
        state.add("Beta")
        state.toggleDone(1)

        val snapshot = state.snapshot()

        assertEquals(listOf("Alpha"), snapshot.incompleteItems.map { it.item.text })
        assertEquals(listOf("Beta"), snapshot.completedItems.map { it.item.text })
        assertEquals(2, snapshot.size)
    }

    @Test
    fun moveByKeyKeepsSelectionOnMovedItem() {
        val state = ChecklistState()
        state.add("Alpha")
        state.add("Beta")
        state.add("Gamma")
        state.enterSelection(0)
        val alphaKey = state.keyAt(0)!!

        assertTrue(state.moveByKey(alphaKey, 2))

        val snapshot = state.snapshot()
        assertEquals(listOf("Beta", "Gamma", "Alpha"), snapshot.incompleteItems.map { it.item.text })
        assertTrue(snapshot.incompleteItems.last().isSelected)
        assertEquals(1, snapshot.selectedCount)
    }

    @Test
    fun removeSelectedClearsSelectionAndCompactsItems() {
        val state = ChecklistState()
        state.add("Alpha")
        state.add("Beta")
        state.add("Gamma")
        state.enterSelection(1)

        assertTrue(state.removeSelected())

        val snapshot = state.snapshot()
        assertEquals(listOf("Alpha", "Gamma"), snapshot.incompleteItems.map { it.item.text })
        assertFalse(snapshot.hasSelection)
    }
}
