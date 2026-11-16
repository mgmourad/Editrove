// SPDX-License-Identifier: AGPL-3.0-only
package news.openwire.data.repository

import news.openwire.data.local.ArticleEntity
import news.openwire.data.remote.ArticleDto
import news.openwire.domain.engine.NewsProcessor
import news.openwire.domain.model.Article

fun ArticleDto.toArticle(rank: Int? = null): Article = Article(
    id = id,
    title = title,
    source = source,
    domain = domain,
    originalUrl = originalUrl,
    publishedAt = NewsProcessor.parseDate(pubDate),
    status = status,
    badge = badge,
    targetUrl = targetUrl,
    archiveUrl = archiveUrl,
    imageUrl = imageUrl,
    summary = summary?.takeIf { it.length > 20 },
    editorialRank = rank,
)

fun Article.toEntity(topicId: String, position: Int = 0): ArticleEntity = ArticleEntity(
    id = id,
    topicId = topicId,
    title = title,
    source = source,
    domain = domain,
    originalUrl = originalUrl,
    publishedAt = publishedAt,
    status = status,
    badge = badge,
    targetUrl = targetUrl,
    archiveUrl = archiveUrl,
    imageUrl = imageUrl,
    summary = summary,
    feedUrl = feedUrl,
    position = position,
)

fun ArticleEntity.toArticle(): Article = Article(
    id = id,
    title = title,
    source = source,
    domain = domain,
    originalUrl = originalUrl,
    publishedAt = publishedAt,
    status = status,
    badge = badge,
    targetUrl = targetUrl,
    archiveUrl = archiveUrl,
    imageUrl = imageUrl,
    summary = summary,
    feedUrl = feedUrl,
)
