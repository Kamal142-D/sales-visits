import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    kotlin("jvm")
    id("org.jetbrains.kotlin.plugin.serialization")
    id("org.jetbrains.compose")
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    implementation(compose.desktop.currentOs)
    implementation(compose.material3)
    implementation(compose.materialIconsExtended)
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.3")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-swing:1.8.1")
    implementation("org.json:json:20240303")
}

compose.desktop {
    application {
        mainClass = "com.sales.visits.MainKt"

        nativeDistributions {
            // Exe = a Windows installer; the portable runnable also lives in the app-image
            // produced by `createDistributable` (VisitFlow/VisitFlow.exe).
            targetFormats(TargetFormat.Exe, TargetFormat.Msi)
            packageName = "VisitFlow"
            packageVersion = "1.8.0"
            description = "Sales visits tracker — Windows desktop version"
            vendor = "VisitFlow"
            copyright = "© 2026 VisitFlow"
            windows {
                packageVersion = "1.8.0"
                menuGroup = "VisitFlow"
                shortcut = true
                dirChooser = true
                upgradeUuid = "8e3f1c2a-5b7d-4c9e-93a1-2f6d0e7b4a58"
            }
        }
    }
}