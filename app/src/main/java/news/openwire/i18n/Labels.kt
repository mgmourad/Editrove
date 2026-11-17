// SPDX-License-Identifier: AGPL-3.0-only
package news.openwire.i18n

import news.openwire.R
import news.openwire.domain.model.BriefingDetail
import news.openwire.domain.model.BriefingSlot
import news.openwire.domain.model.SortMode
import news.openwire.domain.model.UnfoldedLayout

/** The app's words for the domain's choices (the domain keeps English names for storage and tests). */
val BriefingDetail.text: String get() = tr(when (this) {
    BriefingDetail.HEADLINES -> R.string.detail_headlines
    BriefingDetail.SUMMARIES -> R.string.detail_summaries
    BriefingDetail.FULL -> R.string.detail_full
})

val SortMode.text: String get() = tr(when (this) {
    SortMode.CURATED -> R.string.sort_curated
    SortMode.NEWEST -> R.string.sort_newest
    SortMode.OLDEST -> R.string.sort_oldest
})

val UnfoldedLayout.text: String get() = tr(when (this) {
    UnfoldedLayout.SPLIT -> R.string.layout_split
    UnfoldedLayout.FULL -> R.string.layout_full
})

val BriefingSlot.Kind.text: String get() = tr(when (this) {
    BriefingSlot.Kind.MORNING -> R.string.slot_morning
    BriefingSlot.Kind.AFTERNOON -> R.string.slot_afternoon
    BriefingSlot.Kind.EVENING -> R.string.slot_evening
})
