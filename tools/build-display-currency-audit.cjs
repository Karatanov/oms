// Generate an isolated DTO fixture from the real declaration, not a parallel model.
// Only build/ output is written; production source files are unchanged.
const fs = require('node:fs');
const path = require('node:path');
const source = fs.readFileSync('composeApp/src/webMain/kotlin/oms/data/OmsApiClient.kt', 'utf8');
const model = source.match(/^data class ApiFinancialRecord\([\s\S]*?^\)/m)?.[0];
if (!model) throw Error('Cannot locate the exact ApiFinancialRecord declaration');
const output = 'build/calculation-audit';
fs.mkdirSync(output, {recursive: true});
const nationalSummary = source.match(/data class ApiDashboardNationalSummary\([\s\S]*?^\)/m)?.[0];
if (!nationalSummary) throw Error('Cannot locate exact ApiDashboardNationalSummary declaration');
fs.writeFileSync(path.join(output, 'ApiDashboardNationalSummary.kt'),
  'package oms.data\nimport kotlinx.serialization.Serializable\n@Serializable\n' + nationalSummary + '\n');
const summary = source.match(/^data class ApiFinancialSummary\([\s\S]*?^\)/m)?.[0];
if (!summary) throw Error('Cannot locate the exact ApiFinancialSummary declaration');
fs.writeFileSync(path.join(output, 'ApiFinancialSummary.kt'),
  'package oms.data\nimport kotlinx.serialization.Serializable\n@Serializable\n' + summary + '\n');
fs.writeFileSync(path.join(output, 'ApiFinancialRecord.kt'), 'package oms.data\n\n'+model+'\n');
const monthlyDto = source.match(/^data class ApiMonthlyActPayment\([^\n]+/m)?.[0];
if (!monthlyDto) throw Error('Cannot locate the exact ApiMonthlyActPayment declaration');
fs.writeFileSync(path.join(output, 'ApiMonthlyActPayment.kt'), 'package oms.data\n\n' + monthlyDto + '\n');
const physicalDto = source.match(/data class ApiSubprojectProgress\([\s\S]*?^\)/m)?.[0];
if (!physicalDto) throw Error('Cannot locate the exact ApiSubprojectProgress declaration');
fs.writeFileSync(path.join(output, 'ApiSubprojectProgress.kt'),
  'package oms.data\nimport kotlinx.serialization.Serializable\n@Serializable\n' + physicalDto + '\n');
const progressDto = source.match(/data class ApiFinancialProgressItem\([\s\S]*?^\)/m)?.[0];
if (!progressDto) throw Error('Cannot locate the exact ApiFinancialProgressItem declaration');
fs.writeFileSync(path.join(output, 'ApiFinancialProgressItem.kt'),
  'package oms.data\nimport kotlinx.serialization.Serializable\n@Serializable\n' + progressDto + '\n');
const paymentDtos = ['ApiPaymentMonitoringItem', 'ApiPaymentMonitoringTransaction'].map(name => {
  const declaration = source.match(new RegExp(`data class ${name}\\([\\s\\S]*?^\\)`, 'm'))?.[0];
  if (!declaration) throw Error(`Cannot locate exact ${name} declaration`);
  return declaration;
});
fs.writeFileSync(path.join(output, 'PaymentMonitoringDtos.kt'), 'package oms.data\n\n' + paymentDtos.join('\n'));
const monthly = fs.readFileSync('composeApp/src/webMain/kotlin/oms/screens/MonthlyPaymentsChart.kt', 'utf8');
const definitions = ['FinancialChartRecord', 'MonthlyFinancialAggregation'].map(name => {
  const declaration = monthly.match(new RegExp(`^(?:internal )?data class ${name}\\([\\s\\S]*?^\\)`, 'm'))?.[0];
  if (!declaration) throw Error(`Cannot locate ${name}`);
  return declaration;
});
const start = monthly.indexOf('internal fun aggregateMonthlyPayments(');
if (start < 0 || !monthly.slice(start).includes('internal fun completeFinancialMonths(')) throw Error('Cannot locate monthly pure calculations');
const monthlyMoney = fs.readFileSync('composeApp/src/webMain/kotlin/oms/screens/AnalyticsCharts.kt', 'utf8')
  .match(/^data class MonthlyMoneyAmount\([^\n]+/m)?.[0];
if (!monthlyMoney) throw Error('Cannot locate MonthlyMoneyAmount');
fs.writeFileSync(path.join(output, 'MonthlyAggregation.kt'),
  'package oms.screens\nimport oms.data.ApiFinancialRecord\nimport oms.data.displayAmountCents\nimport oms.localization.LocalizationManager\n'+
  definitions.join('\n')+'\n'+monthlyMoney+'\n'+monthly.slice(start));
// Localization is irrelevant to numerical characterization; this is not a translation test.
fs.writeFileSync(path.join(output, 'LocalizationFixture.kt'),
  'package oms.localization\nenum class Language { UK, EN }\nobject LocalizationManager { var currentLanguage = Language.UK; fun t(key: String): String = key }\n');
fs.writeFileSync(path.join(output, 'ChartModels.kt'), fs.readFileSync('composeApp/src/webMain/kotlin/oms/charts/ChartModels.kt', 'utf8'));
// Geometry/color is outside the sorting audit; retain the actual model signature.
fs.writeFileSync(path.join(output, 'ColorFixture.kt'), 'package androidx.compose.ui.graphics\nclass Color\n');
// JVM checks exercise the real sorter; only its browser-native boundary is adapted.
// The actual Intl factory is tested separately in Node and headless browsers.
fs.writeFileSync(path.join(output, 'ChartCollationFixture.kt'),
  'package oms.charts\ninternal fun compareLocalizedChartLabels(left: String, right: String, locale: String): Int = '+
  'java.text.Collator.getInstance(java.util.Locale.forLanguageTag(locale)).apply { strength = java.text.Collator.SECONDARY }.compare(left, right)\n');
