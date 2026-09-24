package com.inventoria.shared.remote

import com.inventoria.shared.model.InventoryItem
import com.inventoria.shared.model.TaskKind
import com.inventoria.shared.model.Todo
import com.inventoria.shared.model.TodoState
import kotlin.test.Test
import kotlin.test.assertEquals

/** Rows shaped the way the Android app's Firebase mapper writes them. */
class DecodingTest {
    @Test
    fun decodesAnObjectNodeAndIgnoresUnknownFields() {
        val node = InventoriaJson.parseToJsonElement(
            """{"17":{"id":17,"name":"Wrench","quantity":2,"price":12,"imageUrls":["a"],"somethingNew":true}}"""
        )
        val items = RealtimeDatabaseRest.childrenOf(node)
            .map { InventoriaJson.decodeFromJsonElement(InventoryItem.serializer(), it) }
        assertEquals(listOf(InventoryItem(id = 17, name = "Wrench", quantity = 2, price = 12.0, imageUrls = listOf("a"))), items)
    }

    @Test
    fun arrayShapedNodesSkipTheGaps() {
        val node = InventoriaJson.parseToJsonElement("""[null,{"id":1},null,{"id":3}]""")
        assertEquals(2, RealtimeDatabaseRest.childrenOf(node).size)
    }

    @Test
    fun unknownEnumsAndNullsFallBackToDefaults() {
        val todo = InventoriaJson.decodeFromString(
            Todo.serializer(),
            """{"id":"t","title":"Call","kind":"MAUVE","state":null,"priority":"B2"}"""
        )
        assertEquals(TaskKind.GRAPHITE, todo.kind)
        assertEquals(TodoState.INCOMPLETE, todo.state)
    }
}
