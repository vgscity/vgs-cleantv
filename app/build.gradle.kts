import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

// Khoá ký nằm ngoài git (tv-sach.keystore + ky.properties). Thiếu thì bản release ký bằng khoá debug.
val kyFile = rootProject.file("ky.properties")
val ky = Properties().apply { if (kyFile.exists()) kyFile.inputStream().use { load(it) } }

android {
    // Bản dựng tái tạo được (24/10/2026): khối "dependency info" AGP nhét vào khối ký được mã hoá bằng khoá của Google,
    // mỗi lần dựng ra khác nhau. App không lên Play nên bỏ (F-Droid cũng khuyên vậy).
    dependenciesInfo { includeInApk = false; includeInBundle = false }
    testOptions { unitTests.isReturnDefaultValues = true }
    namespace = "zone.vgs.tvsach"
    compileSdk = 35

    defaultConfig {
        applicationId = "zone.vgs.cleantv"
        minSdk = 26
        targetSdk = 35
        versionCode = 117
        versionName = "4.0.7"
        ndk { abiFilters += listOf("arm64-v8a", "armeabi-v7a") }
    }

    signingConfigs {
        if (kyFile.exists()) {
            create("vgs") {
                storeFile = rootProject.file(ky.getProperty("storeFile"))
                storePassword = ky.getProperty("storePassword")
                keyAlias = ky.getProperty("keyAlias")
                keyPassword = ky.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            // Không ghi mã commit git vào APK: kho công khai có lịch sử khác kho nội bộ, dựng lại từ đó sẽ lệch.
            vcsInfo.include = false
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.findByName("vgs") ?: signingConfigs.getByName("debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures { compose = true; buildConfig = true }
    // APK launcher đóng sẵn: không nén lại để đọc nhanh.
    androidResources { noCompress += "apk" }
    packaging {
        resources { excludes += setOf("META-INF/versions/9/OSGI-INF/MANIFEST.MF", "META-INF/DEPENDENCIES") }
    }
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2024.10.01"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
    implementation("androidx.work:work-runtime-ktx:2.9.1")
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20240303")

    // ADB qua Wi-Fi (TCP 5555 và ghép đôi Wireless debugging).
    implementation("com.github.MuntashirAkon:libadb-android:3.1.1")
    implementation("org.conscrypt:conscrypt-android:2.5.3")
    // Tạo chứng chỉ X.509 cho khoá ADB (cùng dòng jdk15to18 mà libadb đã kéo về).
    implementation("org.bouncycastle:bcpkix-jdk15to18:1.81")
}
