package com.octetproof.sample

import java.text.Collator
import java.util.Locale

// The country picker's data — the ISO 3166-1 alpha-2 list, localized to the
// device and sorted by display name. Mirrors the iOS sample's Regions.swift.

/** Display row in the country picker. `iso` is the alpha-2 code. */
data class Country(val iso: String, val name: String)

/**
 * ISO 3166-1 codes for dependent territories / overseas departments — dropped
 * from the picker. Diplomatically-contested edges are kept, not asserted here.
 * Kept in sync with iOS `dependentTerritoryCodes`.
 */
private val DEPENDENT_TERRITORY_CODES: Set<String> = setOf(
    "AQ", "AX", "BV", "CC", "CX", "EH", "FK", "FO", "GF", "GG",
    "GI", "GL", "GP", "GS", "GU", "HK", "HM", "IM", "IO", "JE",
    "KY", "MF", "MO", "MP", "MQ", "MS", "NC", "NF", "PF", "PM",
    "PN", "PR", "RE", "SH", "SJ", "TC", "TF", "TK", "UM", "VG",
    "VI", "WF", "YT",
)

/** Full alpha-2 list minus dependent territories, localized + name-sorted. */
val demoCountries: List<Country> = run {
    val locale = Locale.getDefault()
    val collator = Collator.getInstance(locale).apply { strength = Collator.PRIMARY }
    Locale.getISOCountries()
        .filter { it.length == 2 && it !in DEPENDENT_TERRITORY_CODES }
        .map { iso -> Country(iso, Locale("", iso).getDisplayCountry(locale)) }
        .filter { it.name.isNotEmpty() }
        .sortedWith(compareBy(collator) { it.name })
}

/** Default selection — device region, else US, else first entry. */
val defaultCountry: Country = run {
    val deviceCode = Locale.getDefault().country
    demoCountries.firstOrNull { it.iso == deviceCode }
        ?: demoCountries.firstOrNull { it.iso == "US" }
        ?: demoCountries.first()
}

/** Localized country name for an ISO code, falling back to the code itself. */
fun countryName(iso: String): String {
    val name = Locale("", iso).getDisplayCountry(Locale.getDefault())
    return name.ifEmpty { iso }
}
