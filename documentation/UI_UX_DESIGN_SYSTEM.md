# OMS UI/UX design system

This English implementation companion describes the August 2026 interface redesign. The business specification, roles, project scopes, data and existing workflows remain in force. No Ukrainian documentation was modified.

## Design principles

Use a compact enterprise workspace: light surfaces, restrained hierarchy, grouped information and predictable controls. Primary remains exactly `#278DAD`. Background is `#F4F7F9`; dark text is `#20313C`. Small text on solid primary buttons uses `#00151C` (approximately 4.89:1 contrast) rather than white. Informational badges have subtle tinted backgrounds, 4 px corners, no elevation and no button border. Status labels accompany color.

Shared tokens live in `theme/Color.kt` and `theme/Theme.kt`. Common components provide page headings, section headings, badges, loading/error/empty states, sortable headers, icon actions, horizontal scroll controls and deletion confirmation. Body typography is 14/13/12 px; standard controls are at least 44 px high, icons 20 px, table headers 52 px. Material focus/hover/disabled feedback is retained.

Login-specific refinement: filled sign-in/language buttons use white labels on the corporate primary; guest entry uses white on a secondary filled button. This is a local theme override, not a workspace-wide color change. Text links on white surfaces remain colored. The language switch stays in the bottom-left corner, with reserved footer space on narrow screens. The brand heading is 44/52 px, supporting text 20/30 px and feature text 18/28 px; the brand panel scrolls on short viewports rather than overlapping the footer.

## Navigation and responsiveness

The sidebar is 232 px wide or a 72 px icon rail. It starts compact below 1100 px and can be toggled. Current domains remain selected while creating/editing their entities. Finance is visible only to Admin/Project Manager; Administration only to Admin. Anonymous visitors see the public project registry and map. Server authorization remains authoritative.

Analytical pairs use two columns when content width is at least 900 px and stack below it. Dashboard charts precede the latest-inspection photo strip. Long tables remain information-dense and use horizontal scrolling with separate left/right controls and a draggable, keyboard-operable slider; horizontal controls never overlay row actions. Long forms retain scrolling.

## Interaction patterns

- Option pickers and region/city autocomplete share one root, in-canvas overlay. Opening options does not remeasure page content. Menus stay within the viewport and open above a control when space below is insufficient. Escape/outside click dismiss; arrow keys and Enter select. Native browser popups are not used for these controls.
- Dialogs use the existing Wasm-safe in-canvas surface, consistent scrim and bounded width. Cancel, Escape and outside click share dismissal behavior. Save errors are displayed with the dialog instead of only on the underlying page.
- Deleting a project, report, financial record or document requires explicit confirmation. Deleting an SIR source document also warns about its linked report. Existing user/procurement confirmations remain.
- Icons carry localized tooltips; destructive actions have a distinct error color. Informational badges are not interactive buttons.
- Tooltips use the same canvas hierarchy as the application. Native Material popup tooltips caused a reproducible navigation crash in the Wasm browser and were replaced with a shared, delayed hover/focus layer.
- Project search preserves ancestor rows, so hierarchy remains intelligible. Parent rows are bold with subtle grouping; selected/hovered rows are differentiated. Filters can be reset.
- Read-only navigation reuses a successfully loaded project snapshot, including an empty one. Concurrent refreshes share a mutex; mutations still explicitly force refresh. Failed loads expose retry rather than silently displaying an empty registry.

## Screen changes

Dashboard: coherent four-chart layout, readable fixed-baseline plots, exact euro-cent labels, localized month names and centered year groups; photos are positioned below analytics and fetched only for the latest report.

Projects: registry heading/actions, hierarchy/readability, reusable filters, search results, selection, destructive confirmation and horizontal scrolling. Create/edit use shared section headings, Material icons, two-column date groups and currency selection. Project details retain their information tabs with a scrollable tab strip and clearer breadcrumb.

Project registry refinement: filled action buttons use white labels via a page-local theme override. Oblast, sector, construction-type and status filters sit immediately above their own columns in the same horizontal scroll viewport. `ProjectTableColumns` supplies shared widths for filters, sortable headings and rows; the actions cell reserves the same width for guests and managers. Search stays above the table, and the reset control keeps its space when disabled to avoid vertical jumps when filtering.

Map: separate search/filter/control area, visible result count, reset, Ukraine overview and fit-to-results. Markers retain clustering. The native map is anchored to the actual Compose content bounds. Popup project names are inserted as text, not executable HTML. Native map/photo panes temporarily yield to the canvas option layer so they cannot cover dropdowns.

Map scope: a two-way segmented switch shows either Subprojects (default) or Subproject parts, with Ukrainian/English labels and a primary-coloured active segment with white text. Top-level projects are never shown. Search, oblast/status filters, marker counts, clusters and fit-to-results operate only on the selected level with valid coordinates. Switching levels preserves search and filters; resetting filters preserves the selected level. The initial Ukraine overview is unchanged.

Reports/documents: searchable registries, consistent table headers/actions, retry/empty states and local edit/move errors. Report author is the actual username returned by the server, not a fabricated administrator label. Manual SIR retains its XLS-aligned sections and localized health/safety prompts.

Finance: purpose-filtered construction payments, exact cents in chart labels, localized types, compact purpose badges, full-width registry scroll range, anchored purpose selection, local errors and deletion confirmation. Existing EUR equivalents and conversion rules are preserved. The approved-funding chart's legacy source still supplies UAH; it is explicitly labeled UAH rather than incorrectly relabeled EUR. Financial/document author cells show an em dash when their current API does not supply uploader metadata; no username is invented.

Procurement: responsive analytical pair, project/contractor search and status filter, single-language status badges, consistent action alignment, scroll controls and local dialog errors.

Project detail resource failures are scoped to the selected tab; reopening a loaded tab clears stale errors. The financial tab follows the same Admin/Project Manager restriction as financial navigation. Procurement form errors do not replace the registry; load failures expose a separate retry action.

Administration: searchable user registry, informational roles/statuses, corrected failure-count/locked-until/update sorting, consistent header alignment and light forms. Activation/login use bounded, scrollable forms and UK/EN labels.

## API compatibility

Inspection responses add optional `authorUsername`. The repository resolves it with one left join to users; project and UUID predicates are executed in SQL. Existing response fields, permissions, report creation and workflow actions are unchanged. No schema migration is required.

## References

Patterns were informed by [Material 3 interaction states](https://m3.material.io/foundations/interaction/states/overview), [Procore project overview](https://support.procore.com/products/online/user-guide/project-level/project-overview/tutorials/about-the-project-overview), and [Procore navigation guidance](https://en-ca.support.procore.com/getting-started-with-procore/login-and-account-management/tutorials/navigate-procores-tools). These informed hierarchy and task-oriented navigation, not copied branding or layouts.
