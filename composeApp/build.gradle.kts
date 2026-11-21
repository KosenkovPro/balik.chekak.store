import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.util.Properties

/**
 * Ключ подписи release-сборки.
 *
 * Лежит в `.secret/keystore.properties` рядом с самим ключом — в репозиторий
 * не попадает (проверено `git check-ignore`). В CI те же значения приходят
 * переменными окружения. Если ключа нет — сборка подписывается отладочным
 * ключом, чтобы падать не пришлось.
 */
val keystoreProperties = Properties().apply {
    val propertiesFile = rootProject.file(".secret/keystore.properties")
    if (propertiesFile.exists()) {
        propertiesFile.inputStream().use { load(it) }
    }
}

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.kotlinSerialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.room)
}

kotlin {
    jvmToolchain(21)

    androidTarget {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_17)
        }
    }

    // JVM-таргет нужен только для запуска общих тестов на CI без эмулятора.
    jvm("desktop") {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_17)
        }
    }

    listOf(iosX64(), iosArm64(), iosSimulatorArm64()).forEach { iosTarget ->
        iosTarget.binaries.framework {
            baseName = "ComposeApp"
            isStatic = true
        }
    }

    sourceSets {
        commonMain.dependencies {            implementation(compose.runtime)
            implementation(compose.foundation)
            implementation(compose.material3)
            implementation(compose.ui)
            implementation(compose.materialIconsExtended)

            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.kotlinx.datetime)
            implementation(libs.kotlinx.serialization.json)

            implementation(libs.room.runtime)
            implementation(libs.sqlite.bundled)

            implementation(libs.koin.core)
            implementation(libs.koin.compose)
        }

        commonTest.dependencies {
            implementation(kotlin("test"))
            implementation(libs.kotlinx.coroutines.test)
        }

        androidMain.dependencies {
            implementation(libs.androidx.core.ktx)
            implementation(libs.androidx.activity.compose)
            implementation(libs.androidx.lifecycle.runtime)
            implementation(libs.androidx.documentfile)
            implementation(libs.kotlinx.coroutines.android)
            implementation(libs.pdfbox.android)
            // Офлайн-OCR для сканов PDF: Tesseract 5 с русским языком.
            // JavaCPP-пресеты дают и Java-обвязку, и нативные библиотеки.
            implementation(libs.javacpp)
            implementation(libs.javacpp.presets)
            implementation(libs.leptonica)
            implementation(libs.tesseract)
            // Нативные библиотеки под архитектуры устройства и эмулятора.
            // У javacpp-presets нативок нет: это обычные Java-классы.
            listOf("android-arm64", "android-x86_64").forEach { abi ->
                implementation("org.bytedeco:javacpp:${libs.versions.javacpp.get()}:$abi")
                implementation("org.bytedeco:leptonica:${libs.versions.leptonica.get()}:$abi")
                implementation("org.bytedeco:tesseract:${libs.versions.tesseract.get()}:$abi")
            }
        }

        // На JVM используется Apache PDFBox — на нём прогоняются тесты парсера PDF.
        val desktopMain by getting {
            dependencies {
                implementation(libs.pdfbox)
            }
        }
    }
}

room {
    schemaDirectory("$projectDir/schemas")
}

dependencies {
    // Room KMP: код генерируется под каждую платформу. Общая (metadata) генерация
    // не подходит: реализация базы не попадает в байткод Android и JVM —
    // на старте приложения падало бы «AppDatabaseConstructor.initialize()».
    add("kspAndroid", libs.room.compiler)
    add("kspDesktop", libs.room.compiler)
    add("kspIosX64", libs.room.compiler)
    add("kspIosArm64", libs.room.compiler)
    add("kspIosSimulatorArm64", libs.room.compiler)
}

android {
    namespace = "store.chekak.balik"
    compileSdk = libs.versions.compileSdk.get().toInt()

    defaultConfig {
        applicationId = "store.chekak.balik"
        minSdk = libs.versions.minSdk.get().toInt()
        targetSdk = libs.versions.targetSdk.get().toInt()
        versionCode = 1
        versionName = "1.0.0"
        resourceConfigurations += listOf("ru", "en")
    }

    // Ключ подписи объявляем раньше buildTypes: иначе тип сборки не найдёт его
    // и подпишет APK отладочным ключом (так и было до этого изменения).
    signingConfigs {
        create("release") {
            val storePath = keystoreProperties.getProperty("storeFile")
                ?: System.getenv("KEYSTORE_PATH")
            if (!storePath.isNullOrBlank()) {
                storeFile = rootProject.file(storePath)
                storePassword = keystoreProperties.getProperty("storePassword")
                    ?: System.getenv("KEYSTORE_PASSWORD")
                keyAlias = keystoreProperties.getProperty("keyAlias")
                    ?: System.getenv("KEY_ALIAS")
                keyPassword = keystoreProperties.getProperty("keyPassword")
                    ?: System.getenv("KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        getByName("release") {
            isMinifyEnabled = false
            isShrinkResources = false
            // Подпись берётся из `.secret/keystore.properties` или Secrets CI.
            // Если ключа нет — используется debug-подпись, чтобы сборка не падала.
            signingConfig = signingConfigs.findByName("release")
                ?.takeIf { it.storeFile != null }
                ?: signingConfigs.getByName("debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    packaging {
        resources.excludes += setOf("/META-INF/{AL2.0,LGPL2.1}", "/META-INF/DEPENDENCIES")
        // У библиотек JavaCPP одинаковые служебные файлы — оставляем по одному.
        resources.pickFirsts += "META-INF/native-image/**"
        // В пакетах bytedeco кроме .so лежат заголовки C, cmake-файлы и pkgconfig.
        // Движок приложение зовёт только через Java-обвязку, поэтому всё это
        // в APK не нужно. Особенно важен `lib/<abi>/tesseract`: установщик
        // считает любой файл в `lib/<abi>/` нативной библиотекой и из-за
        // этого отказывается ставить приложение.
        resources.excludes += setOf(
            "**/include/**",
            "**/*.cmake",
            "**/*.pc",
            "**/tesseract",
        )
    }
}
