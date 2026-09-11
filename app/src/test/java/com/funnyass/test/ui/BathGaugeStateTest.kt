package com.funnyass.test.ui

import com.funnyass.test.ui.BathGaugeView.GaugeState
import org.junit.Assert.assertEquals
import org.junit.Test

class BathGaugeStateTest {

    private fun resolve(
        connected: Boolean = true,
        stopRequestInFlight: Boolean = false,
        settlementInFlight: Boolean = false,
        deviceState: Int = 0,
        justSettled: Boolean = false
    ): GaugeState = BathActivity.resolveGaugeState(
        connected = connected,
        rollbackInFlight = false,
        startRequestInFlight = false,
        stopRequestInFlight = stopRequestInFlight,
        settlementInFlight = settlementInFlight,
        deviceState = deviceState,
        justSettled = justSettled,
        ownActiveSession = false
    )

    @Test
    fun `active close and settlement operations remain loading`() {
        assertEquals(GaugeState.STOPPING, resolve(stopRequestInFlight = true))
        assertEquals(GaugeState.STOPPING, resolve(settlementInFlight = true, deviceState = 3))
    }

    @Test
    fun `leftover settlement data without active operation is retryable error`() {
        assertEquals(GaugeState.ERROR, resolve(deviceState = 3))
    }

    @Test
    fun `successful settlement remains visible after idle device query`() {
        assertEquals(GaugeState.SETTLED, resolve(deviceState = 0, justSettled = true))
    }

    @Test
    fun `disconnection still has priority over settlement presentation`() {
        assertEquals(
            GaugeState.DISCONNECTED,
            resolve(connected = false, settlementInFlight = true, deviceState = 3)
        )
    }
}
