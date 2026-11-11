package com.fitifysync.app

import com.fitifysync.app.data.parser.FirestoreProtobufParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.time.Instant

class FirestoreParserTest {

    @Test
    fun testParseFirestoreSessionProtobuf() {
        val resourceFile = File("src/test/resources/sample_session.bin")
        assertTrue("Sample session file should exist", resourceFile.exists())

        val bytes = resourceFile.readBytes()
        val fields = FirestoreProtobufParser.parseMaybeDocument(bytes)

        assertNotNull(fields)
        assertEquals("Full Body Stretching", fields["title"])
        assertEquals("plan_recovery", fields["type"])
        assertEquals(875L, fields["duration"])
        assertEquals(41L, fields["calories"])

        val timestamp = fields["timestamp"]
        assertTrue("Timestamp should be Instant", timestamp is Instant)
        assertEquals(1730603769L, (timestamp as Instant).epochSecond)

        val exercises = fields["exercises"] as? List<*>
        assertNotNull(exercises)
        assertEquals(22, exercises!!.size)

        val firstEx = exercises[0] as? Map<*, *>
        assertNotNull(firstEx)
        assertEquals("bo091_front_neck_stretch", firstEx!!["code"])
        assertEquals(40L, firstEx["duration"])
        assertEquals(0L, firstEx["reps"])
    }
}
