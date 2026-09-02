package com.inventoria.app.ui.screens.help.catalog

import com.inventoria.app.ui.screens.help.model.HelpArticle
import com.inventoria.app.ui.screens.help.model.HelpCategory
import com.inventoria.app.ui.screens.help.model.HelpSearchResult

/**
 * The manual's index.
 *
 * Everything is `by lazy` so none of it is constructed until the user first opens Help -- the
 * Settings row itself must not touch this object, or every launch pays for a catalog nobody asked
 * to read.
 */
object HelpCatalog {

    // Ordered to match the app's information architecture: the home screen, then the tabs that
    // carry written articles, then everything else.
    val categories: List<HelpCategory> by lazy {
        listOf(todayCategory, taskTrackingCategory, todosCategory) + stubCategories
    }

    private val articlesById: Map<String, HelpArticle> by lazy {
        categories.flatMap { it.articles }.associateBy { it.id }
    }

    private val categoryByArticleId: Map<String, HelpCategory> by lazy {
        categories.flatMap { category -> category.articles.map { it.id to category } }.toMap()
    }

    /** Precomputed lowercase haystack per article: title, summary and keywords. Body text is
     * deliberately excluded -- a hit inside a paragraph produces a result row whose relevance
     * isn't visible from the row itself. */
    private val searchIndex: List<Triple<HelpArticle, HelpCategory, String>> by lazy {
        categories.flatMap { category ->
            category.articles.map { article ->
                Triple(
                    article,
                    category,
                    (article.title + " " + article.summary + " " + article.keywords.joinToString(" ")).lowercase()
                )
            }
        }
    }

    fun category(id: String): HelpCategory? = categories.firstOrNull { it.id == id }

    fun article(id: String): HelpArticle? = articlesById[id]

    fun categoryOf(articleId: String): HelpCategory? = categoryByArticleId[articleId]

    /** All query words must appear somewhere in the haystack, so extra words narrow rather than
     * widen -- typing more should never give you more results. */
    fun search(query: String): List<HelpSearchResult> {
        val tokens = query.trim().lowercase().split(" ").filter { it.isNotBlank() }
        if (tokens.isEmpty()) return emptyList()
        return searchIndex
            .filter { (_, _, haystack) -> tokens.all { haystack.contains(it) } }
            .map { (article, category, _) -> HelpSearchResult(article, category) }
    }
}
