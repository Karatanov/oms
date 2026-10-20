package oms.components

/**
 * Keeps browser paste usable in Compose/WASM forms without asking the layout
 * engine to measure an accidental multi-megabyte clipboard payload.
 *
 * The browser remains responsible for the native context menu and clipboard
 * permissions. This function is deliberately applied by individual fields,
 * where the data model defines a meaningful maximum length.
 */
fun String.safeTextInput(maxLength: Int): String =
    replace("\u0000", "").take(maxLength)
