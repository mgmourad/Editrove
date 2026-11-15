package news.openwire.di

import android.content.Context
import news.openwire.data.local.OpenWireDatabase
import news.openwire.data.remote.AssetNewsDataSource
import news.openwire.data.remote.FallbackNewsRemote
import news.openwire.data.remote.NewsRemoteDataSource
import news.openwire.data.repository.FeedRepository
import news.openwire.data.repository.FeedRepositoryImpl

/**
 * Manual dependency container (no Hilt in v1 to keep the AGP 9 scaffold lean).
 * Builds the Room database and the feed repository.
 */
class AppContainer(context: Context) {

    private val database: OpenWireDatabase by lazy {
        OpenWireDatabase.create(context)
    }

    val feedRepository: FeedRepository by lazy {
        val remote = FallbackNewsRemote(
            primary = NewsRemoteDataSource(),
            fallback = AssetNewsDataSource(context),
        )
        FeedRepositoryImpl(remote, database.articleDao())
    }
}
