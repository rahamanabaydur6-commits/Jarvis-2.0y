package com.jarvis.assistant

import com.jarvis.assistant.ai.Planner
import com.jarvis.assistant.executor.ActionType
import com.jarvis.assistant.util.Calc
import com.jarvis.assistant.util.Units
import com.jarvis.assistant.util.speakable
import org.junit.Assert.*
import org.junit.Test

class CoreTest {
    @Test fun calcPrecedence() {
        assertEquals(14.0, Calc.eval("2+3*4"), 1e-9)
        assertEquals(20.0, Calc.eval("(2+3)*4"), 1e-9)
        assertEquals(512.0, Calc.eval("2^3^2"), 1e-9)
        assertEquals(4.0, Calc.eval("sqrt(16)"), 1e-9)
        assertEquals(-3.0, Calc.eval("-3"), 1e-9)
    }

    @Test fun calcErrors() {
        try { Calc.eval("1/0"); fail("expected error") } catch (e: IllegalStateException) { }
        try { Calc.eval("2+"); fail("expected error") } catch (e: IllegalStateException) { }
    }

    @Test fun units() {
        assertEquals(1000.0, Units.convert(1.0, "km", "m")!!, 1e-9)
        assertEquals(212.0, Units.convert(100.0, "c", "f")!!, 1e-9)
        assertNull(Units.convert(1.0, "kg", "m"))
        assertTrue(Units.parse("10 km to m").contains("10000"))
    }

    @Test fun plannerAllowList() {
        val (reply, actions) = Planner.parse(
            """{"reply":"ok","actions":[{"type":"open_app","arg":"YouTube"},{"type":"FORMAT_PHONE","arg":"x"}]}"""
        )
        assertEquals("ok", reply)
        assertEquals(1, actions.size)
        assertEquals(ActionType.OPEN_APP, actions[0].type)
        assertEquals("YouTube", actions[0].arg)
    }

    @Test fun plannerHandlesFencesAndPlainText() {
        val (r1, a1) = Planner.parse("```json\n{\"reply\":\"hi\",\"actions\":[]}\n```")
        assertEquals("hi", r1); assertTrue(a1.isEmpty())
        val (r2, a2) = Planner.parse("just text")
        assertEquals("just text", r2); assertTrue(a2.isEmpty())
    }

    @Test fun plannerLimitsActions() {
        val many = (1..10).joinToString(",") { """{"type":"GO_BACK"}""" }
        assertEquals(Planner.MAX_ACTIONS, Planner.parse("""{"reply":"x","actions":[$many]}""").second.size)
    }

    @Test fun speakableStripsCode() {
        val t = speakable("Hello **world** ```kotlin\nval x=1\n``` see https://a.com")
        assertFalse(t.contains("val x"))
        assertFalse(t.contains("*"))
    }
}
