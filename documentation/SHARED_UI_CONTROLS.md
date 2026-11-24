# Shared interface styling

All workspace pages use the same theme and controls. Apply visual changes to shared components so dashboards, maps, registries, detail pages, forms and dialogs stay consistent.

- Use `PageHeading` for workspace page titles and actions; detail pages may include record metadata.
- Use `workspacePadding()` and `OmsDimensions` for page spacing. Standard page padding is 12 dp; page sections use 8 dp gaps.
- Use `WorkspaceButton`, `WorkspaceOutlinedButton`, `WorkspaceTextButton` and `WorkspaceFilterChip` instead of local Material look-alikes. Defaults share corners, padding and theme colors; preserve semantic status colors.
- Use `WorkspaceSearchField` for registry searches, `CompactChoiceSelector` for short exclusive choices, and `TrancheSelector` / `CurrencySelector` for their respective selections.
- Use existing option pickers for long lists and forms. Keep keyboard focus, disabled states and labels accessible.
- The map uses a deliberately compact toolbar and collapsible filters to leave room for the map. Its colors and selection controls still come from shared components.
- Keep chart-specific compact navigation and layouts. Avoid changing chart sizing through general-purpose control replacements.

Do not introduce page-specific copies of shared control styles. When adjusting a shared component, consider desktop wrapping, mobile stacking, dialogs and authentication forms together.

## Narrow layouts

- Use `WrappingActions` for dialog footers, attachment actions and short chip groups. Do not place multiple unrestricted buttons beside a weighted title.
- Use `ResponsiveFormRow` for repeated inspection fields. It stacks fields below 900 dp of actual available width, including narrow desktop dialogs.
- Use `ResponsiveDetailRow` for record labels and values. Below 600 dp, the label sits above its value instead of reserving a 210 dp label column.
- Search, PDF export and pagination require separate wrapping rows below 1000 dp. Keep the data table inside `ScrollableTable`; do not shrink its columns to fit a phone.
- Review both languages at 320, 360, 390, 430 and 768 px, plus landscape with the on-screen keyboard. Check long names, empty results, filtered lists, all dialog actions, and horizontal scrolling. These are verification targets, not a claim that runtime checks have passed.
