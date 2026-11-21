package com.melodyon.shared.backup

import com.melodyon.shared.constants.ArtistNameAliasesJsonKey
import com.melodyon.shared.settings.ArtistNameAliases
import com.melodyon.shared.settings.PortableSetting
import com.melodyon.shared.settings.PortableSettings
import com.melodyon.shared.settings.SettingType
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

fun PortableSettings?.withLegacyArtistNameAliases(serialized: String): PortableSettings {
    val aliases = ArtistNameAliases.deserialize(serialized)
    val settings = this ?: PortableSettings(entries = emptyList())
    return settings.copy(
        entries =
            settings.entries.filterNot { it.key == ArtistNameAliasesJsonKey } +
                PortableSetting(ArtistNameAliasesJsonKey, SettingType.STRING, Json.encodeToString(aliases)),
    )
}
