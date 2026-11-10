package oms.urp3.service

fun main() {
    check(worksContractSourceCode("KH08_07", "KH08_07#Lot2") == "KH08_07#LOT2")
    check(worksContractSourceCode(" VN08_01 ", null) == "VN08_01")
    check(worksContractSourceCode("VN08_01", " ") == "VN08_01")
    check(monitoringApprovedEibUah("""{"eibCost":"750.25","fundingTotal":"1000","constructionAmount":"600"}""") == 750.25)
    check(monitoringApprovedEibUah("""{"eibCost":"0"}""") == 0.0)
    check(monitoringApprovedEibUah("""{"fundingTotal":"1000"}""") == null)
    check(monitoringApprovedEibUah("""{"eibCost":"#VALUE!"}""") == null)
    check(monitoringApprovedEibUah("not json") == null)
    check(monitoringSourceAmount("6,484,316") == 6484316.0)
    check(monitoringSourceAmount("1,234.56") == 1234.56)
    check(monitoringSourceAmount("1 234,56") == 1234.56)
    check(monitoringSourceAmount("750,25") == 750.25)
    val contracts = mapOf("KH08_07#LOT1" to 100.0, "KH08_07#LOT2" to 200.0)
    val lot1 = contracts.getValue(worksContractSourceCode("KH08_07", "KH08_07#Lot1"))
    val lot2 = contracts.getValue(worksContractSourceCode("KH08_07", "KH08_07#Lot2"))
    check((lot1 * 1.0 + lot2 * 0.25) / (lot1 + lot2) * 100.0 == 50.0)
    println("Dashboard funding and lot-contract weighting checks passed")
}
