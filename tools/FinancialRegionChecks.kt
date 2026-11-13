import oms.urp3.canonicalUkraineRegion
import oms.urp3.ukraineRegionNames

/** Read-only checks against the TSV output of deploy/vps/audit-financial-regions.sh. */
fun main(args: Array<String>) {
    ukraineRegionNames.forEach {
        check(canonicalUkraineRegion(it.englishName) == it.ukrainianName)
        check(canonicalUkraineRegion(it.ukrainianName.removeSuffix(" область")) == it.ukrainianName)
    }
    check(canonicalUkraineRegion("Kharkiv") == canonicalUkraineRegion("Харківська обл."))
    if (args.isNotEmpty()) {
        val rows = java.io.File(args.single()).readLines().drop(1).filter(String::isNotBlank).map { line ->
            val columns = line.split('\t'); columns[0] to columns[1].toLong()
        }
        val groups = rows.groupBy { canonicalUkraineRegion(it.first) }
        check(groups.values.sumOf { group -> group.sumOf { it.second } } == rows.sumOf { it.second })
        check(groups.keys.all { key -> ukraineRegionNames.any { it.ukrainianName == key } })
        println("${rows.size} source region labels -> ${groups.size} canonical regions; ${rows.sumOf { it.second }} subprojects preserved")
    }
}
