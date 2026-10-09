package com.moez.QKSMS.manager

import com.moez.QKSMS.model.Message

/**
 * Reads each incoming message once, right after it is stored, so notifications, the smart
 * lists and the tabs all work from the same result. Extraction happens here whether or not
 * the conversation's notifications are on.
 *
 * The reading engine lives in the presentation layer, which binds its implementation through
 * Dagger; the domain only knows this contract.
 */
interface MessageAnalysisProcessor {

    /**
     * Called on a background thread after [message] is persisted and before its notification is
     * updated. Must not throw, and must not wait on the network: anything slow is scheduled.
     */
    fun onMessageStored(message: Message)

}
