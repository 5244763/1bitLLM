# CMakeLists.txt for llama_bridge JNI library
#
# Sprint 5: llama.cpp Android NDK 統合
#
# 前提:
#   - llama.cpp のプリビルド共有ライブラリ (.so) を
#     app/src/main/jniLibs/{ABI}/libllama.so として配置すること
#   - prism-ml/llama.cpp フォーク（1-bit対応）のビルド済み .so を使用
#
# セットアップ手順:
#   1. Android NDK をインストール (Android Studio > SDK Manager > NDK)
#   2. NDK パスを local.properties に記載:
#      ndk.dir=/path/to/ndk
#   3. llama.cpp の共有ライブラリをビルドして jniLibs に配置
#   4. app/build.gradle.kts の externalNativeBuild ブロックを有効化

cmake_minimum_required(VERSION 3.22.1)
project("llama_bridge")

# C++17 を使用
set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

# llama_bridge 共有ライブラリをビルド
add_library(
    llama_bridge
    SHARED
    llama_bridge.cpp
)

# Android ログライブラリをリンク
find_library(log-lib log)

target_link_libraries(
    llama_bridge
    ${log-lib}
)

# llama.cpp のプリビルド .so が jniLibs に存在する場合はリンク
# (統合後に以下のコメントを外す)
# add_library(llama SHARED IMPORTED)
# set_target_properties(llama PROPERTIES
#     IMPORTED_LOCATION ${CMAKE_SOURCE_DIR}/../jniLibs/${ANDROID_ABI}/libllama.so
# )
# target_link_libraries(llama_bridge llama ${log-lib})
