package com.example.smartfeedandroid.data.repository

import java.io.IOException
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WebSocketRetryPolicyTest {
    @Test
    fun retriesFirstTransportFailureBeforeAnyDelta() {
        assertTrue(
            shouldRetryChatStream(
                attempt = 0,
                receivedDelta = false,
                failure = IOException("connection reset")
            )
        )
    }

    @Test
    fun doesNotRetryAfterReceivingAnswerContent() {
        assertFalse(
            shouldRetryChatStream(
                attempt = 0,
                receivedDelta = true,
                failure = IOException("connection reset")
            )
        )
    }

    @Test
    fun doesNotRetryServerOrProtocolErrors() {
        assertFalse(
            shouldRetryChatStream(
                attempt = 0,
                receivedDelta = false,
                failure = NonRetryableWebSocketException("unauthorized")
            )
        )
    }

    @Test
    fun stopsAfterOneReconnect() {
        assertFalse(
            shouldRetryChatStream(
                attempt = 1,
                receivedDelta = false,
                failure = IOException("still offline")
            )
        )
    }
}
