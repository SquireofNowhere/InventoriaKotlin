package com.inventoria.app.ui.screens.help.model

import androidx.compose.ui.graphics.vector.ImageVector

/**
 * The in-app manual's content types.
 *
 * Written as Kotlin rather than string resources on purpose: this app has no localization and does
 * not use stringResource anywhere, so a resource-based catalog would be the only such island in
 * the codebase -- and it would lose the type-safe cross-referencing between articles that ids give
 * us here.
 */

/** Whether an article documents something the user can actually do yet. */
enum class ArticleStatus {
    Available,

    /**
     * The feature is named in the app (or in its docs) but does not work: a stub with no wiring, a
     * toggle nothing reads, a README claim with no UI behind it. These get an entry so the map of
     * the app stays complete, but they carry a badge and no instructions -- a manual that explains
     * how to use something that does nothing is worse than one that admits the gap.
     */
    ComingSoon
}

/** One step in a numbered list. [diagram] illustrates this step alone, so it highlights rather
 * than numbering -- the step's own text is already its caption. See DiagramSpec. */
data class HelpStep(
    val text: String,
    val diagram: DiagramSpec? = null
)

/** A field or control and what it means -- for documenting dialogs without turning them into
 * paragraphs. */
data class HelpTerm(val term: String, val meaning: String)

enum class CalloutKind { Tip, Caution, Note }

/** The variable middle of an article. The opening and closing sections are fields on [HelpArticle]
 * rather than blocks, so every article is structured the same way whoever writes it. */
sealed interface HelpBlock {
    data class Paragraph(val text: String) : HelpBlock
    data class Steps(val steps: List<HelpStep>) : HelpBlock
    data class Bullets(val items: List<String>) : HelpBlock
    data class Definitions(val terms: List<HelpTerm>) : HelpBlock
    data class Callout(val kind: CalloutKind, val text: String) : HelpBlock

    /**
     * A diagram that stands on its own rather than belonging to a step. [caption] is required, not
     * optional: it is both the visible caption and the accessibility description, and an
     * illustration nobody can summarise in a sentence is not illustrating anything.
     */
    data class Diagram(val caption: String, val spec: DiagramSpec) : HelpBlock
}

/**
 * One feature, one article.
 *
 * [whatItIs] and [whyItMatters] are named fields instead of blocks so that the "steps plus the
 * why" shape is enforced by the type rather than by the author remembering it: every article opens
 * with what the thing is and closes with why it's there, in that order, and an unfinished article
 * is exactly one with a null [whyItMatters].
 *
 * [keywords] carries the words people actually search for but that don't appear in the title --
 * "pomodoro", "clock in", "stopwatch" -- and is the difference between search working and not.
 */
data class HelpArticle(
    val id: String,
    val title: String,
    val summary: String,
    val whatItIs: String,
    val blocks: List<HelpBlock> = emptyList(),
    val whyItMatters: String? = null,
    val related: List<String> = emptyList(),
    val keywords: List<String> = emptyList(),
    val status: ArticleStatus = ArticleStatus.Available
)

/** One area of the app. An [articles]-empty category is one whose guides aren't written yet; the
 * index renders it as visible but not enterable, so the shape of the manual is honest about what
 * is finished. */
data class HelpCategory(
    val id: String,
    val title: String,
    val summary: String,
    val icon: ImageVector,
    val articles: List<HelpArticle> = emptyList()
)

/** An article plus the category it came from -- search results are articles, and a title alone
 * doesn't tell you where in the app you'd be. */
data class HelpSearchResult(
    val article: HelpArticle,
    val category: HelpCategory
)
