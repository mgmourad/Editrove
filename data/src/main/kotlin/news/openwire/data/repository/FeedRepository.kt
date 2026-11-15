// SPDX-License-Identifier: AGPL-3.0-only
package news.openwire.data.repository

import kotlinx.coroutines.flow.Flow
import news.openwire.domain.model.Topic

/** Observes a topic's feed (cache-first, then network). */
interface FeedRepository {
    fun observeTopic(topic: Topic): Flow<FeedResult>
}
