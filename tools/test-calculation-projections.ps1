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
node tools/build-display-currency-audit.cjs
if ($LASTEXITCODE -ne 0) { throw 'DTO fixture generation failed' }
$sources = @(
    'server/src/main/kotlin/oms/urp3/service/PaymentMonitoringWorkbookSource.kt',
    'server/src/main/kotlin/oms/urp3/service/PaymentMonitoringTableSelection.kt',
    'server/src/main/kotlin/oms/urp3/service/FinancialMoney.kt',
    'server/src/main/kotlin/oms/urp3/service/ProjectDisbursements.kt',
    'server/src/main/kotlin/oms/urp3/domain/FinancialRecord.kt',
    'server/src/main/kotlin/oms/urp3/domain/FinancialRecordType.kt',
    'shared/src/commonMain/kotlin/oms/urp3/PaymentSourceValues.kt',
    'shared/src/commonMain/kotlin/oms/urp3/UkraineRegions.kt',
    'shared/src/commonMain/kotlin/oms/urp3/ContractDuration.kt',
    'shared/src/commonTest/kotlin/oms/urp3/ContractDurationTest.kt',
    'composeApp/src/webMain/kotlin/oms/screens/PaymentMonitoringScope.kt',
    'build/calculation-audit/PaymentMonitoringDtos.kt',
    'server/src/test/kotlin/oms/urp3/service/ProjectDisbursementsTest.kt',
    'server/src/test/kotlin/oms/urp3/service/PaymentMonitoringTableSelectionTest.kt',
    'composeApp/src/webTest/kotlin/oms/screens/PaymentMonitoringScopeTest.kt'
)
& $java -cp ($compiler -join ';') org.jetbrains.kotlin.cli.jvm.K2JVMCompiler -no-stdlib -no-reflect -classpath ($deps -join ';') -d $testOutput @sources
if ($LASTEXITCODE -ne 0) { throw 'Focused Kotlin compilation failed' }
& $java -cp ($deps -join ';') org.junit.runner.JUnitCore oms.urp3.service.ProjectDisbursementsTest oms.urp3.service.PaymentMonitoringTableSelectionTest oms.screens.PaymentMonitoringScopeTest oms.urp3.ContractDurationTest
if ($LASTEXITCODE -ne 0) { throw 'Focused tests failed' }
