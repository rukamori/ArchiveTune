/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.updates

import com.google.common.collect.ImmutableList
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import moe.rukamori.archivetune.utils.Updater
import org.commonmark.node.AbstractVisitor
import org.commonmark.node.Code
import org.commonmark.node.HardLineBreak
import org.commonmark.node.Heading
import org.commonmark.node.Image
import org.commonmark.node.ListItem
import org.commonmark.node.Node
import org.commonmark.node.Paragraph
import org.commonmark.node.SoftLineBreak
import org.commonmark.node.Text
import org.commonmark.parser.Parser
import java.util.Locale
import javax.inject.Inject

enum class WhatsNewCategory { FEATURES, FIXES, IMPROVEMENTS }

data class WhatsNewSection(
    val category: WhatsNewCategory,
    val entries: ImmutableList<String>,
)

data class WhatsNewRelease(
    val url: String,
    val sections: ImmutableList<WhatsNewSection>,
)

class WhatsNewUseCase @Inject constructor(
    private val repository: WhatsNewRepository,
) {
    suspend fun shouldShow(version: String): Boolean = repository.registerVersion(version)

    suspend fun acknowledge(version: String) = repository.acknowledge(version)

    fun releasePageUrl(version: String? = null): String = Updater.getReleasePageUrl(version)

    suspend fun load(version: String): WhatsNewRelease? = withContext(Dispatchers.IO) {
        val release = repository.getRelease(version) ?: return@withContext null
        val url = release.htmlUrl.takeIf { it.startsWith("${releasePageUrl()}/tag/") } ?: releasePageUrl(version)
        withContext(Dispatchers.Default) {
            WhatsNewRelease(url, extractHighlights(release.body.orEmpty()))
        }
    }

    private fun extractHighlights(markdown: String): ImmutableList<WhatsNewSection> {
        val headings = mutableListOf<ReleaseHeading>()
        val changes = WhatsNewCategory.entries.associateWith { linkedMapOf<String, String>() }

        fun addChange(text: String) {
            val heading = headings.lastOrNull()
            if (heading?.excluded == true || text.isBlank() || metadata.containsMatchIn(text)) return
            val category = heading?.category ?: changeCategory(text)
                ?: if (heading?.inHighlights == true) WhatsNewCategory.FEATURES else return
            changes.getValue(category).putIfAbsent(text.lowercase(Locale.ROOT), text)
        }

        Parser.builder().build().parse(markdown).accept(object : AbstractVisitor() {
            override fun visit(heading: Heading) {
                while (headings.isNotEmpty() && headings.last().level >= heading.level) headings.removeAt(headings.lastIndex)
                val title = plainText(heading).lowercase(Locale.ROOT)
                headings.add(
                    ReleaseHeading(
                        level = heading.level,
                        category = headingCategory(title) ?: headings.lastOrNull()?.category,
                        excluded = headings.lastOrNull()?.excluded == true || excludedSection.containsMatchIn(title),
                        inHighlights = highlightsHeading.containsMatchIn(title) || headings.lastOrNull()?.inHighlights == true,
                    ),
                )
            }

            override fun visit(item: ListItem) {
                val paragraphs = mutableListOf<String>()
                var child = item.firstChild
                while (child != null) {
                    if (child is Paragraph) {
                        val text = plainText(child)
                        if (text.isNotBlank() && !metadata.containsMatchIn(text)) paragraphs.add(text)
                    }
                    child = child.next
                }
                addChange(paragraphs.joinToString("\n\n"))
                visitChildren(item)
            }

            override fun visit(paragraph: Paragraph) {
                var ancestor = paragraph.parent
                while (ancestor != null) {
                    if (ancestor is ListItem) return
                    ancestor = ancestor.parent
                }
                addChange(plainText(paragraph))
            }
        })

        return ImmutableList.copyOf(
            WhatsNewCategory.entries.mapNotNull { category ->
                val entries = changes.getValue(category).values
                if (entries.isEmpty()) null else WhatsNewSection(category, ImmutableList.copyOf(entries))
            },
        )
    }

    private fun plainText(node: Node): String {
        val text = StringBuilder()
        node.accept(object : AbstractVisitor() {
            override fun visit(node: Text) { text.append(node.literal) }
            override fun visit(node: Code) { text.append(node.literal) }
            override fun visit(node: SoftLineBreak) { text.append(' ') }
            override fun visit(node: HardLineBreak) { text.append(' ') }
            override fun visit(node: Image) = Unit
        })
        return whitespace.replace(text, " ").trim()
    }

    private fun headingCategory(title: String): WhatsNewCategory? = when {
        fixHeading.containsMatchIn(title) -> WhatsNewCategory.FIXES
        improvementHeading.containsMatchIn(title) -> WhatsNewCategory.IMPROVEMENTS
        featureHeading.containsMatchIn(title) -> WhatsNewCategory.FEATURES
        else -> null
    }

    private fun changeCategory(text: String): WhatsNewCategory? = when {
        fixChange.containsMatchIn(text) -> WhatsNewCategory.FIXES
        improvementChange.containsMatchIn(text) -> WhatsNewCategory.IMPROVEMENTS
        featureChange.containsMatchIn(text) -> WhatsNewCategory.FEATURES
        else -> null
    }

    private data class ReleaseHeading(
        val level: Int,
        val category: WhatsNewCategory?,
        val excluded: Boolean,
        val inHighlights: Boolean,
    )

    private companion object {
        val whitespace = Regex("\\s+")
        val excludedSection = Regex("\\b(contributors?|credits?|acknowledg\\w*|dependencies|internal|refactors?|maintenance|workflows?|assets|pull requests?|referenced issues|resolved and referenced issues)\\b|full changelog|download links?")
        val metadata = Regex("^(contributors?|related|pull requests?|issues?|https?://|#\\d+\\b)", RegexOption.IGNORE_CASE)
        val fixHeading = Regex("\\b(fix(?:es|ed)?|bugs?|resolved)\\b")
        val improvementHeading = Regex("\\b(improvements?|enhancements?|performance|stability|optimizations?)\\b")
        val featureHeading = Regex("\\b(features?|additions?)\\b")
        val highlightsHeading = Regex("\\bhighlights\\b|what[’']s new")
        val fixChange = Regex("^(fix(?:ed|es)?|correct(?:ed|s)?|resolve[ds]?|prevent(?:ed|s)?|eliminate[ds]?|repair(?:ed|s)?|restore[ds]?)\\b", RegexOption.IGNORE_CASE)
        val improvementChange = Regex("^(improve[ds]?|enhance[ds]?|optimize[ds]?|refine[ds]?|update[ds]?|reduce[ds]?|simplif(?:y|ied)|unif(?:y|ied)|harden(?:ed)?|rework(?:ed)?)\\b", RegexOption.IGNORE_CASE)
        val featureChange = Regex("^(add(?:ed|s)?|introduce[ds]?|integrate[ds]?|enable[ds]?|new|support(?:ed|s)?)\\b", RegexOption.IGNORE_CASE)
    }
}
