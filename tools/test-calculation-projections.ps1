# A focused fallback when the Windows Gradle daemon cannot open its loopback socket.
# Compiles real pure production sources and exact frontend DTO declarations, then runs JUnit.
$ErrorActionPreference = 'Stop'
$taskCache = Join-Path $env:USERPROFILE '.gradle/caches/modules-2/files-2.1'
function Jars([string[]]$paths) {
    @($paths | ForEach-Object {
        Get-ChildItem (Join-Path $taskCache $_) -Recurse -Filter '*.jar' -ErrorAction SilentlyContinue |
            Where-Object Name -NotMatch 'sources|javadoc' | Select-Object -ExpandProperty FullName
    })
}
$compiler = Jars @('org.jetbrains.kotlin/kotlin-compiler-embeddable/2.3.0', 'org.jetbrains.kotlin/kotlin-stdlib/2.3.0',
    'org.jetbrains.kotlin/kotlin-script-runtime/2.3.0', 'org.jetbrains.kotlin/kotlin-reflect/2.2.21',
    'org.jetbrains.kotlin/kotlin-daemon-embeddable/2.3.0', 'org.jetbrains.kotlinx/kotlinx-coroutines-core-jvm', 'org.jetbrains/annotations')
$testDeps = Jars @('org.jetbrains.kotlin/kotlin-test', 'org.jetbrains.kotlin/kotlin-test-junit', 'junit/junit', 'org.hamcrest/hamcrest-core', 'com.google.auth')
$testOutput = Join-Path $env:TEMP ('oms-calculation-tests-' + [guid]::NewGuid())
New-Item -ItemType Directory -Path $testOutput | Out-Null
$deps = @($testOutput) + @(Jars @('org.jetbrains.kotlin/kotlin-stdlib/2.3.0')) +
    @(Get-ChildItem 'server/build/install/server/lib' -Filter '*.jar' | Select-Object -ExpandProperty FullName) + $testDeps
$java = Join-Path $env:USERPROFILE '.jdks/jbr-21.0.11/bin/java.exe'
$textEncodingArgs = @('-Dstdout.encoding=UTF-8', '-Dstderr.encoding=UTF-8')
node tools/build-display-currency-audit.cjs
if ($LASTEXITCODE -ne 0) { throw 'DTO fixture generation failed' }
$sources = @(
    'server/src/main/kotlin/oms/urp3/service/DashboardAmountTotal.kt',
    'server/src/test/kotlin/oms/urp3/service/DashboardAmountTotalTest.kt',
    'server/src/main/kotlin/oms/urp3/service/PaymentMonitoringTotals.kt',
    'server/src/test/kotlin/oms/urp3/service/PaymentMonitoringTotalsTest.kt',
    'server/src/main/kotlin/oms/urp3/service/MonitoringBudgetSnapshot.kt',
    'server/src/test/kotlin/oms/urp3/service/MonitoringBudgetSnapshotTest.kt',
    'server/src/main/kotlin/oms/urp3/service/PaymentMonitoringWorkbookSource.kt',
    'server/src/main/kotlin/oms/urp3/service/PaymentMonitoringTableSelection.kt',
    'server/src/main/kotlin/oms/urp3/service/FinancialMoney.kt',
    'server/src/test/kotlin/oms/urp3/service/FinancialMoneyTest.kt',
    'tools/FinancialConversionParityTest.kt',
    'server/src/main/kotlin/oms/urp3/service/NormalizedFinancialSummary.kt',
    'server/src/main/kotlin/oms/urp3/dto/FinancialSummaryResponse.kt',
    'server/src/test/kotlin/oms/urp3/service/NormalizedFinancialSummaryTest.kt',
    'server/src/main/kotlin/oms/urp3/service/DashboardMonthlyPayments.kt',
    'server/src/test/kotlin/oms/urp3/service/DashboardMonthlyPaymentsTest.kt',
    'server/src/main/kotlin/oms/urp3/service/ProjectDisbursements.kt',
    'server/src/main/kotlin/oms/urp3/service/ProjectFinancialExecution.kt',
    'server/src/main/kotlin/oms/urp3/service/FinancialProgressPayments.kt',
    'server/src/test/kotlin/oms/urp3/service/FinancialProgressPaymentsTest.kt',
    'server/src/main/kotlin/oms/urp3/service/DashboardFundingRules.kt',
    'server/src/main/kotlin/oms/urp3/service/MonitoringCompletion.kt',
    'server/src/test/kotlin/oms/urp3/service/MonitoringCompletionTest.kt',
    'server/src/main/kotlin/oms/urp3/domain/Project.kt',
    'server/src/main/kotlin/oms/urp3/domain/ProjectType.kt',
    'server/src/main/kotlin/oms/urp3/domain/ProjectStatus.kt',
    'server/src/main/kotlin/oms/urp3/domain/ProjectAmount.kt',
    'server/src/main/kotlin/oms/urp3/domain/ProjectSourceDetails.kt',
    'server/src/test/kotlin/oms/urp3/service/ProjectFinancialExecutionTest.kt',
    'server/src/main/kotlin/oms/urp3/domain/FinancialRecord.kt',
    'server/src/main/kotlin/oms/urp3/domain/FinancialRecordType.kt',
    'shared/src/commonMain/kotlin/oms/urp3/PaymentSourceValues.kt',
    'shared/src/commonMain/kotlin/oms/urp3/UkraineRegions.kt',
    'shared/src/commonMain/kotlin/oms/urp3/ContractDuration.kt',
    'shared/src/commonTest/kotlin/oms/urp3/ContractDurationTest.kt',
    'shared/src/commonMain/kotlin/oms/urp3/DecimalMoney.kt',
    'shared/src/commonTest/kotlin/oms/urp3/DecimalMoneyTest.kt',
    'composeApp/src/webMain/kotlin/oms/data/FinancialDisplayCurrency.kt',
    'composeApp/src/webTest/kotlin/oms/urp3/FinancialDecimalRoundingTest.kt',
    'tools/DisplayCurrencyAudit.kt',
    'composeApp/src/webMain/kotlin/oms/screens/PaymentMonitoringScope.kt',
    'composeApp/src/webMain/kotlin/oms/screens/RegionalFinancing.kt',
    'composeApp/src/webTest/kotlin/oms/screens/RegionalFinancingTest.kt',
    'build/calculation-audit/PaymentMonitoringDtos.kt',
    'build/calculation-audit/ApiFinancialRecord.kt',
    'build/calculation-audit/ApiFinancialSummary.kt',
    'build/calculation-audit/ApiMonthlyActPayment.kt',
    'build/calculation-audit/ApiSubprojectProgress.kt',
    'build/calculation-audit/ApiFinancialProgressItem.kt',
    'composeApp/src/webTest/kotlin/oms/urp3/FinancialProgressAvailabilityTest.kt',
    'composeApp/src/webMain/kotlin/oms/screens/PhysicalCompletionScope.kt',
    'composeApp/src/webTest/kotlin/oms/urp3/PhysicalCompletionScopeTest.kt',
    'composeApp/src/webMain/kotlin/oms/screens/MonthlyAmountProjection.kt',
    'composeApp/src/webTest/kotlin/oms/urp3/MonthlyAmountProjectionTest.kt',
    'composeApp/src/webTest/kotlin/oms/urp3/FinancialSummaryCompatibilityTest.kt',
    'build/calculation-audit/MonthlyAggregation.kt',
    'build/calculation-audit/LocalizationFixture.kt',
    'composeApp/src/webTest/kotlin/oms/urp3/MonthlyFinancialCompletenessTest.kt',
    'tools/MonthlyAggregationAudit.kt',
    'composeApp/src/webMain/kotlin/oms/screens/MonthlyRateLoading.kt',
    'tools/MonthlyRateLoadingTest.kt',
    'build/calculation-audit/ChartModels.kt',
    'build/calculation-audit/ColorFixture.kt',
    'build/calculation-audit/ChartCollationFixture.kt',
    'composeApp/src/webMain/kotlin/oms/charts/ChartSorting.kt',
    'tools/ChartSortingAudit.kt',
    'server/src/test/kotlin/oms/urp3/service/ProjectDisbursementsTest.kt',
    'server/src/test/kotlin/oms/urp3/service/PaymentMonitoringTableSelectionTest.kt',
    'composeApp/src/webTest/kotlin/oms/screens/PaymentMonitoringScopeTest.kt'
)
$serializationPlugin = @(Jars @('org.jetbrains.kotlin/kotlin-serialization-compiler-plugin-embeddable/2.3.0'))[0]
& $java @textEncodingArgs -cp ($compiler -join ';') org.jetbrains.kotlin.cli.jvm.K2JVMCompiler -no-stdlib -no-reflect "-Xplugin=$serializationPlugin" -classpath ($deps -join ';') -d $testOutput @sources
if ($LASTEXITCODE -ne 0) { throw 'Focused Kotlin compilation failed' }
& $java @textEncodingArgs -cp ($deps -join ';') org.junit.runner.JUnitCore oms.urp3.service.DashboardAmountTotalTest oms.urp3.service.PaymentMonitoringTotalsTest
if ($LASTEXITCODE -ne 0) { throw 'Dashboard decimal monetary totals failed' }
& $java @textEncodingArgs -cp ($deps -join ';') org.junit.runner.JUnitCore oms.urp3.service.MonitoringBudgetSnapshotTest
if ($LASTEXITCODE -ne 0) { throw 'Monitoring budget snapshot synchronization failed' }
& $java @textEncodingArgs -cp ($deps -join ';') org.junit.runner.JUnitCore oms.urp3.service.ProjectDisbursementsTest oms.urp3.service.PaymentMonitoringTableSelectionTest oms.screens.PaymentMonitoringScopeTest oms.urp3.ContractDurationTest oms.urp3.service.FinancialMoneyTest oms.urp3.DecimalMoneyTest oms.urp3.FinancialDecimalRoundingTest oms.urp3.MonthlyFinancialCompletenessTest oms.screens.MonthlyRateLoadingTest oms.urp3.service.NormalizedFinancialSummaryTest
if ($LASTEXITCODE -ne 0) { throw 'Focused tests failed' }
& $java @textEncodingArgs -cp ($deps -join ';') org.junit.runner.JUnitCore oms.urp3.FinancialSummaryCompatibilityTest
if ($LASTEXITCODE -ne 0) { throw 'Financial summary serialization compatibility failed' }
& $java @textEncodingArgs -cp ($deps -join ';') org.junit.runner.JUnitCore oms.urp3.service.DashboardMonthlyPaymentsTest oms.urp3.MonthlyAmountProjectionTest
if ($LASTEXITCODE -ne 0) { throw 'Dashboard monthly currency completeness failed' }
& $java @textEncodingArgs -cp ($deps -join ';') org.junit.runner.JUnitCore oms.urp3.service.FinancialConversionParityTest
if ($LASTEXITCODE -ne 0) { throw 'Client/server frozen conversion parity failed' }
& $java @textEncodingArgs -cp ($deps -join ';') org.junit.runner.JUnitCore oms.urp3.service.ProjectFinancialExecutionTest
if ($LASTEXITCODE -ne 0) { throw 'Project/lot financial execution failed' }
& $java @textEncodingArgs -cp ($deps -join ';') org.junit.runner.JUnitCore oms.urp3.service.MonitoringCompletionTest oms.urp3.PhysicalCompletionScopeTest
if ($LASTEXITCODE -ne 0) { throw 'Physical completion source validation failed' }
& $java @textEncodingArgs -cp ($deps -join ';') org.junit.runner.JUnitCore oms.screens.RegionalFinancingTest
if ($LASTEXITCODE -ne 0) { throw 'Regional planned/actual financing failed' }
& $java @textEncodingArgs -cp ($deps -join ';') org.junit.runner.JUnitCore oms.urp3.service.FinancialProgressPaymentsTest oms.urp3.FinancialProgressAvailabilityTest
if ($LASTEXITCODE -ne 0) { throw 'Financial progress completeness failed' }
& $java @textEncodingArgs -cp ($deps -join ';') oms.urp3.service.DisplayCurrencyAuditKt
if ($LASTEXITCODE -ne 0) { throw 'Frontend decimal reconciliation failed' }
& $java @textEncodingArgs -cp ($deps -join ';') oms.screens.MonthlyAggregationAuditKt
if ($LASTEXITCODE -ne 0) { throw 'Monthly completeness reconciliation failed' }
& $java @textEncodingArgs -cp ($deps -join ';') oms.charts.ChartSortingAuditKt
if ($LASTEXITCODE -ne 0) { throw 'Chart sorting reconciliation failed' }
