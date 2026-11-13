package oms.urp3

data class UkraineRegionName(val ukrainianName: String, val englishName: String)

val ukraineRegionNames = listOf(
    UkraineRegionName("Вінницька область", "Vinnytsia Region"),
    UkraineRegionName("Волинська область", "Volyn Region"),
    UkraineRegionName("Дніпропетровська область", "Dnipropetrovsk Region"),
    UkraineRegionName("Донецька область", "Donetsk Region"),
    UkraineRegionName("Житомирська область", "Zhytomyr Region"),
    UkraineRegionName("Закарпатська область", "Zakarpattia Region"),
    UkraineRegionName("Запорізька область", "Zaporizhzhia Region"),
    UkraineRegionName("Івано-Франківська область", "Ivano-Frankivsk Region"),
    UkraineRegionName("Київська область", "Kyiv Region"),
    UkraineRegionName("Кіровоградська область", "Kirovohrad Region"),
    UkraineRegionName("Луганська область", "Luhansk Region"),
    UkraineRegionName("Львівська область", "Lviv Region"),
    UkraineRegionName("Миколаївська область", "Mykolaiv Region"),
    UkraineRegionName("Одеська область", "Odesa Region"),
    UkraineRegionName("Полтавська область", "Poltava Region"),
    UkraineRegionName("Рівненська область", "Rivne Region"),
    UkraineRegionName("Сумська область", "Sumy Region"),
    UkraineRegionName("Тернопільська область", "Ternopil Region"),
    UkraineRegionName("Харківська область", "Kharkiv Region"),
    UkraineRegionName("Херсонська область", "Kherson Region"),
    UkraineRegionName("Хмельницька область", "Khmelnytskyi Region"),
    UkraineRegionName("Черкаська область", "Cherkasy Region"),
    UkraineRegionName("Чернівецька область", "Chernivtsi Region"),
    UkraineRegionName("Чернігівська область", "Chernihiv Region"),
    UkraineRegionName("місто Київ", "Kyiv City")
)

private val regionPunctuation = Regex("[.,]")
private val regionSuffix = Regex("\\s+(область|обл|oblast|region)$")
private val regionWhitespace = Regex("\\s+")
private fun normalizedRegionName(value: String): String = value.replace('\u00a0', ' ').trim().lowercase()
    .replace(regionPunctuation, "").replace(regionSuffix, "").replace(regionWhitespace, " ").trim()

private val canonicalRegionNames = ukraineRegionNames.flatMap { region ->
    listOf(normalizedRegionName(region.ukrainianName) to region.ukrainianName,
        normalizedRegionName(region.englishName) to region.ukrainianName)
}.toMap()

/** One language-independent identity for aggregation, filtering and translated UI labels. */
fun canonicalUkraineRegion(value: String): String {
    val normalized = normalizedRegionName(value)
    return canonicalRegionNames[normalized] ?: value.replace('\u00a0', ' ').replace(regionWhitespace, " ").trim()
}
