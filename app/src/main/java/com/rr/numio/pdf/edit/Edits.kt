package com.rr.numio.pdf.edit

/** One change made to the marks since the PDF was opened or last saved. */
sealed interface Edit {
    data class Add(val mark: Mark) : Edit
    data class Remove(val mark: Mark) : Edit
    data class Recolor(val mark: Mark, val color: Int) : Edit
    /** Anything else changed, like a text box's words, size or place. Same [Mark.id]. */
    data class Change(val mark: Mark, val new: Mark) : Edit
    /** A form field filled in: [field] is its full name, [value] the text / choice / "Off". */
    data class Fill(val field: String, val value: String) : Edit
}

/** What saving has to do to the file, worked out from the list of edits. */
data class SavePlan(
    /** New marks, in their final colour. */
    val added: List<Mark>,
    /** Marks already in the file that were deleted. */
    val removed: List<Mark>,
    /** Marks already in the file with a new colour (in [Mark.color]). */
    val recolored: List<Mark>,
    /** Form fields to fill: full name to value. */
    val fills: Map<String, String> = emptyMap(),
) {
    val isEmpty: Boolean get() = added.isEmpty() && removed.isEmpty() && recolored.isEmpty() && fills.isEmpty()
}

/**
 * Edits are kept as a list so Undo is just "drop the last one", and the marks
 * on screen are always the saved marks with the edits replayed on top.
 * Pure Kotlin, unit-tested.
 */
object Edits {

    /** The marks to show on [page]: [saved] ones from the file, with [edits] applied in order. */
    fun apply(saved: List<Mark>, edits: List<Edit>, page: Int): List<Mark> {
        val marks = saved.filter { it.page == page }.toMutableList()
        for (e in edits) {
            when (e) {
                is Edit.Add -> if (e.mark.page == page) marks += e.mark
                is Edit.Remove -> marks.removeAll { it.id == e.mark.id }
                is Edit.Recolor -> {
                    val i = marks.indexOfFirst { it.id == e.mark.id }
                    if (i >= 0) marks[i] = marks[i].copy(color = e.color)
                }
                is Edit.Change -> {
                    val i = marks.indexOfFirst { it.id == e.mark.id }
                    if (i >= 0) marks[i] = e.new.copy(id = e.mark.id, ref = marks[i].ref)
                }
                is Edit.Fill -> Unit
            }
        }
        return marks
    }

    fun plan(edits: List<Edit>): SavePlan {
        val added = LinkedHashMap<Long, Mark>()
        val removed = LinkedHashMap<Long, Mark>()
        val recolored = LinkedHashMap<Long, Mark>()
        val fills = LinkedHashMap<String, String>()
        for (e in edits) {
            when (e) {
                is Edit.Add -> added[e.mark.id] = e.mark
                is Edit.Remove -> {
                    val id = e.mark.id
                    if (added.remove(id) == null && e.mark.saved) {
                        removed[id] = e.mark
                        recolored.remove(id)
                    }
                }
                is Edit.Recolor -> {
                    val id = e.mark.id
                    val new = added[id]
                    when {
                        new != null -> added[id] = new.copy(color = e.color)
                        e.mark.saved && id !in removed -> recolored[id] = e.mark.copy(color = e.color)
                    }
                }
                is Edit.Fill -> fills[e.field] = e.value
                // A changed saved mark is saved as: remove the old one, add the new one
                is Edit.Change -> {
                    val id = e.mark.id
                    val replacement = e.new.copy(id = id, ref = -1)
                    when {
                        id in added -> added[id] = replacement
                        e.mark.saved -> {
                            removed[id] = e.mark
                            recolored.remove(id)
                            added[id] = replacement
                        }
                    }
                }
            }
        }
        return SavePlan(added.values.toList(), removed.values.toList(), recolored.values.toList(), fills)
    }

    /** Form values filled since the last save (latest wins), to show over the file's own. */
    fun fills(edits: List<Edit>): Map<String, String> {
        val out = HashMap<String, String>()
        edits.forEach { if (it is Edit.Fill) out[it.field] = it.value }
        return out
    }
}
