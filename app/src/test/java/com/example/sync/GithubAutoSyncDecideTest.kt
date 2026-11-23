package com.example.sync

import com.example.sync.GithubAutoSync.Action
import org.junit.Assert.assertEquals
import org.junit.Test

class GithubAutoSyncDecideTest {

    private fun decide(local: String?, remote: String?, base: String?) =
        GithubAutoSync.decide(local, remote, base)

    @Test
    fun localDeleted() {
        assertEquals(Action.NONE, decide(null, null, null))
        assertEquals(Action.DELETE, decide(null, "r", "r"))
        assertEquals(Action.CONFLICT, decide(null, "r", null))
        assertEquals(Action.CONFLICT, decide(null, "r", "b"))
    }

    @Test
    fun localExists() {
        assertEquals(Action.NONE, decide("l", "l", "b"))
        assertEquals(Action.PUSH, decide("l", null, null))
        assertEquals(Action.CONFLICT, decide("l", null, "b"))
        assertEquals(Action.PUSH, decide("l", "b", "b"))
        assertEquals(Action.NONE, decide("l", "r", "l"))
        assertEquals(Action.CONFLICT, decide("l", "r", "b"))
    }
}
