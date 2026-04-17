plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.example.onbitllm"
    compileSdk = 35

    // Sprint 6: NDK バージョン指定
    ndkVersion = "27.0.12077973"

    defaultConfig {
        applicationId = "com.example.onbitllm"
        minSdk = 31
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // Sprint 6: NDK ビルド設定
        ndk {
            abiFilters += listOf("arm64-v8a")
        }
        externalNativeBuild {
            cmake {
                cppFlags += "-std=c++17"
                // llama.cpp ソースディレクトリを渡す
                arguments += "-DLLAMA_SRC_DIR=${rootDir}/../llama_cpp_src"
                arguments += "-DCMAKE_BUILD_TYPE=Release"
                arguments += "-DBUILD_SHARED_LIBS=OFF"
                arguments += "-DLLAMA_BUILD_TESTS=OFF"
                arguments += "-DLLAMA_BUILD_EXAMPLES=OFF"
                arguments += "-DLLAMA_BUILD_SERVER=OFF"
                arguments += "-DLLAMA_BUILD_COMMON=ON"
                arguments += "-DLLAMA_OPENSSL=OFF"
                arguments += "-DGGML_CUDA=OFF"
                arguments += "-DGGML_VULKAN=OFF"
                arguments += "-DGGML_METAL=OFF"
                arguments += "-DGGML_OPENCL=OFF"
                arguments += "-DGGML_HIP=OFF"
                arguments += "-DGGML_SYCL=OFF"
                arguments += "-DGGML_BLAS=OFF"
                arguments += "-DGGML_LLAMAFILE=OFF"
                arguments += "-DGGML_NATIVE=OFF"
                arguments += "-DGGML_BACKEND_DL=OFF"
                arguments += "-DGGML_CPU_KLEIDIAI=OFF"
                arguments += "-DGGML_OPENMP=OFF"
            }
        }
    }

    // Sprint 6: externalNativeBuild を有効化
    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
            version = "3.22.1"
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    kotlinOptions {
        jvmTarget = "11"
    }
    buildFeatures {
        compose = true
    }
}

dependencies {
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.material.icons.extended)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.process)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.coil.compose)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.ui.test.junit4)
    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)
}
