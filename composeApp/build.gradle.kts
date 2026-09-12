import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.kotlinSerialization)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
}

kotlin {
    js {
        browser {
            // The Render image intentionally uses the development Webpack task
            // to stay inside the free builder's memory limit.  Its default
            // eval source map made the delivered browser bundle ~58 MB and
            // blocked navigation while the browser parsed it.
            commonWebpackConfig {
                sourceMaps = false
            }
            testTask {
                useKarma { useChromeHeadless() }
            }
        }
        binaries.executable()
    }

    // The Render server image serves the JS bundle only. Do not even configure
    // the Wasm target there: its tooling setup is unnecessary and memory-heavy.
    if (!providers.gradleProperty("renderJsOnly").isPresent) {
        @OptIn(ExperimentalWasmDsl::class)
        wasmJs {
            browser()
            binaries.executable()
        }
    }

    sourceSets {
        commonMain.dependencies {
            implementation(libs.compose.runtime)
            implementation(libs.compose.foundation)
            implementation(libs.compose.material3)
            implementation(libs.compose.ui)
            implementation(libs.compose.components.resources)
            implementation(libs.compose.uiToolingPreview)
            implementation(libs.androidx.lifecycle.viewmodelCompose)
            implementation(libs.androidx.lifecycle.runtimeCompose)
            implementation(projects.shared)
            implementation("org.jetbrains.compose.material:material-icons-extended:1.7.3")
            implementation("io.ktor:ktor-client-core:3.3.3")
            implementation("io.ktor:ktor-client-content-negotiation:3.3.3")
            implementation("io.ktor:ktor-serialization-kotlinx-json:3.3.3")
        }
        webMain.dependencies {
            implementation("io.ktor:ktor-client-js:3.3.3")
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
        }
    }
}


