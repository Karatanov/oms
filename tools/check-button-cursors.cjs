// Guards the central Web/Wasm cursor policy: text inside a Compose button
// must not turn the pointer back into an I-beam.
const fs = require("fs");

const required = [
  "composeApp/src/webMain/kotlin/oms/components/ButtonCursor.kt",
  "composeApp/src/webMain/kotlin/oms/screens/CursorButtons.kt"
];

for (const path of required) {
  const source = fs.readFileSync(path, "utf8");
  const hasPolicy = path.endsWith("ButtonCursor.kt")
    ? source.includes("PointerIcon.Hand") && source.includes("overrideDescendants = true")
    : source.includes("modifier.buttonHandCursor()");
  if (!hasPolicy) {
    throw new Error(`${path} does not enforce the button hand cursor`);
  }
}

const segmentedSources = [
  "composeApp/src/webMain/kotlin/oms/screens/MapScreen.kt",
  "composeApp/src/webMain/kotlin/oms/screens/CreateInspectionScreen.kt",
  "composeApp/src/webMain/kotlin/oms/screens/dashboard/DashboardScreen.kt"
];
for (const path of segmentedSources) {
  const source = fs.readFileSync(path, "utf8");
  if (!source.includes("Modifier.buttonHandCursor()")) {
    throw new Error(`${path} has a segmented control without the hand cursor`);
  }
}

console.log("Button cursor policy check passed.");
