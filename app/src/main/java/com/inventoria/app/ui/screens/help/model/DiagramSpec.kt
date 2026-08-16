package com.inventoria.app.ui.screens.help.model

import androidx.compose.ui.graphics.vector.ImageVector
import com.inventoria.app.data.model.TaskKind

/**
 * A step illustration, described as data rather than drawn as a bespoke composable per article.
 *
 * The reason is consistency at scale: these diagrams have to read as pictures of one app across
 * ~140 articles written over a long stretch. A closed vocabulary makes drift structurally
 * impossible -- padding, corner radius and highlight treatment live in one renderer, so changing
 * them changes every diagram. It also means an article file is prose and data with no layout code,
 * no Modifier chains and no opt-ins, which is what makes article number ninety bearable.
 *
 * The rule that keeps it honest: **a spec can never name a Color.** Only [DiagramAccent], resolved
 * against MaterialTheme at draw time, so every diagram follows the Dark Mode toggle for free.
 *
 * Elements stack vertically in order. There is deliberately no nesting and no general layout tree
 * -- the moment a diagram needs one, it is trying to be a screenshot.
 */
data class DiagramSpec(
    val elements: List<DiagramElement>,
    /**
     * Captions for numbered markers, rendered beneath the frame. Index 0 is callout 1.
     *
     * Numbering vs highlighting is one rule: a diagram attached to a step ([HelpStep.diagram])
     * highlights and leaves this empty, because the step text is already the caption. A standalone
     * [HelpBlock.Diagram] numbers its marks and captions them here.
     */
    val callouts: List<String> = emptyList()
)

/** Semantic colour roles. Resolved to real colours at render time -- see DiagramPrimitives. */
sealed interface DiagramAccent {
    data object Primary : DiagramAccent
    data object Success : DiagramAccent
    data object Warning : DiagramAccent
    data object Danger : DiagramAccent
    data object Neutral : DiagramAccent

    /** A task Kind's own colour, for diagrams about Kinds. Rendered with a border as well as a
     * fill, because several Kind colours (Banana especially) are near-invisible as a pale wash. */
    data class Kind(val kind: TaskKind) : DiagramAccent
}

/** Annotation for a gesture that isn't visible in a still picture. Attached to the element it
 * applies to rather than floating free, so it can't drift away from its target. */
enum class DiagramGesture { Tap, LongPress, Drag, SwipeLeft, SwipeRight }

data class DiagramIcon(
    val icon: ImageVector,
    val highlight: Boolean = false,
    val callout: Int? = null
)

data class DiagramChip(
    val label: String,
    val accent: DiagramAccent = DiagramAccent.Neutral,
    val leadingDot: Boolean = false,
    val highlight: Boolean = false,
    val callout: Int? = null
)

enum class FieldKind { Text, Dropdown, Toggle, Stepper }

data class DiagramField(
    val label: String,
    val value: String = "",
    val kind: FieldKind = FieldKind.Text,
    val highlight: Boolean = false,
    val callout: Int? = null
)

enum class PopupStyle { Dialog, BottomSheet }

enum class TopBarStyle { Centered, Contextual }

/**
 * The vocabulary.
 *
 * Highlighting exists at two levels -- on the element, and on the icons/chips/fields inside it --
 * because the commonest case in this app is "this one action out of four in the top bar", which a
 * whole-element wrapper cannot express.
 */
sealed interface DiagramElement {

    data class TopBar(
        val title: String,
        val hasBackArrow: Boolean = false,
        val actions: List<DiagramIcon> = emptyList(),
        val style: TopBarStyle = TopBarStyle.Centered,
        val highlight: Boolean = false,
        val callout: Int? = null
    ) : DiagramElement

    /** The workhorse: every card and list row in the app is this shape. */
    data class Row(
        val title: String,
        val subtitle: String? = null,
        val meta: String? = null,
        val leadingBar: DiagramAccent? = null,
        val leadingIcon: ImageVector? = null,
        val chips: List<DiagramChip> = emptyList(),
        val trailing: List<DiagramIcon> = emptyList(),
        val selected: Boolean = false,
        val indent: Int = 0,
        val gesture: DiagramGesture? = null,
        val highlight: Boolean = false,
        val callout: Int? = null
    ) : DiagramElement

    data class SectionHeader(val text: String) : DiagramElement

    /** Settings toggle and drill-down rows, which a large share of the app's features live in. */
    data class SettingsRow(
        val title: String,
        val subtitle: String? = null,
        val icon: ImageVector? = null,
        val control: SettingsControl = SettingsControl.Chevron,
        val highlight: Boolean = false,
        val callout: Int? = null
    ) : DiagramElement

    data class ChipRow(val chips: List<DiagramChip>) : DiagramElement

    data class Fab(
        val icon: ImageVector,
        val label: String? = null,
        val highlight: Boolean = false,
        val callout: Int? = null
    ) : DiagramElement

    /** Dialogs and bottom sheets are the same drawing with a different frame, so they are one
     * element with a style rather than two that would drift apart. */
    data class Popup(
        val title: String,
        val body: String? = null,
        val fields: List<DiagramField> = emptyList(),
        val style: PopupStyle = PopupStyle.Dialog,
        val confirmLabel: String? = null,
        val dismissLabel: String? = null,
        val highlight: Boolean = false,
        val callout: Int? = null
    ) : DiagramElement

    data class BottomNav(
        val items: List<DiagramIcon>,
        val selectedIndex: Int = 0
    ) : DiagramElement

    /** A plain line of explanatory text inside the frame, for labelling what the picture is of. */
    data class Note(val text: String) : DiagramElement
}

enum class SettingsControl { Switch, Chevron, None }
