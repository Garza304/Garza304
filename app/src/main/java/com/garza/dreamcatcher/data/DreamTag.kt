package com.garza.dreamcatcher.data

import androidx.room.TypeConverter

/**
 * Labels for a journal entry. [DREAM], [NIGHTMARE] and [NO_DREAM] describe what kind of night it
 * was, so at most one of them applies; [LUCID] and [RECURRING] describe a remembered dream.
 * [key] is what gets stored, so it must never change.
 */
enum class DreamTag(val key: String, val label: String, val isKind: Boolean) {
    DREAM("dream", "Dream", isKind = true),
    NIGHTMARE("nightmare", "Nightmare", isKind = true),
    NO_DREAM("no_dream", "No dream", isKind = true),
    LUCID("lucid", "Lucid", isKind = false),
    RECURRING("recurring", "Recurring", isKind = false);

    companion object {
        fun encode(tags: Set<DreamTag>): String = entries.filter { it in tags }.joinToString(",") { it.key }

        fun decode(value: String): Set<DreamTag> {
            val keys = value.split(',')
            return entries.filterTo(LinkedHashSet()) { it.key in keys }
        }

        /** [tags] with [tag] switched on or off, dropping any tags that contradict it. */
        fun toggle(tags: Set<DreamTag>, tag: DreamTag): Set<DreamTag> {
            if (tag in tags) return tags - tag
            val compatible = tags.filterNot { other ->
                (tag.isKind && other.isKind) ||
                    // Nothing remembered means nothing lucid or recurring, and vice versa.
                    (tag == NO_DREAM && !other.isKind) ||
                    (!tag.isKind && other == NO_DREAM)
            }
            return compatible.toSet() + tag
        }
    }
}

class DreamTagConverters {
    @TypeConverter
    fun fromTags(tags: Set<DreamTag>): String = DreamTag.encode(tags)

    @TypeConverter
    fun toTags(value: String): Set<DreamTag> = DreamTag.decode(value)
}
