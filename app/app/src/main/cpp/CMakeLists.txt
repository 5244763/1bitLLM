# CMakeLists.txt for llama_bridge JNI library
# Sprint 6: llama.cpp Android NDK 統合 (実装)
#
# llama.cpp ソースを add_subdirectory でサブプロジェクトとして組み込む。
# llama_cpp_src は /home/dcm52/1bitLLM/llama_cpp_src/ を想定しているが、
# gradle からは ${LLAMA_SRC_DIR} として引数を渡す。

cmake_minimum_required(VERSION 3.22.1)
project("llama_bridge")

set(CMAKE_C_STANDARD 11)
set(CMAKE_C_STANDARD_REQUIRED true)
set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

# llama.cpp ソースディレクトリ（gradle から -DLLAMA_SRC_DIR=... で渡される）
if(NOT DEFINED LLAMA_SRC_DIR)
    # デフォルトパス: プロジェクトルートからの相対パス
    set(LLAMA_SRC_DIR "${CMAKE_CURRENT_SOURCE_DIR}/../../../../../llama_cpp_src")
endif()
get_filename_component(LLAMA_SRC_DIR "${LLAMA_SRC_DIR}" ABSOLUTE)

message(STATUS "LLAMA_SRC_DIR=${LLAMA_SRC_DIR}")

# Android 向け最適化設定
if(DEFINED ANDROID_ABI)
    message(STATUS "Detected Android ABI: ${ANDROID_ABI}")
    if(ANDROID_ABI STREQUAL "arm64-v8a")
        # ARM64 向け: KleidiAI 無効化（ビルドを単純化）、NEON 最適化は自動
        set(GGML_CPU_KLEIDIAI OFF)
    endif()
endif()

# llama.cpp の不要なバックエンドを無効化（ビルド時間短縮）
set(GGML_CUDA       OFF CACHE BOOL "" FORCE)
set(GGML_VULKAN     OFF CACHE BOOL "" FORCE)
set(GGML_OPENCL     OFF CACHE BOOL "" FORCE)
set(GGML_METAL      OFF CACHE BOOL "" FORCE)
set(GGML_HIP        OFF CACHE BOOL "" FORCE)
set(GGML_SYCL       OFF CACHE BOOL "" FORCE)
set(GGML_BLAS       OFF CACHE BOOL "" FORCE)
set(GGML_LLAMAFILE  OFF CACHE BOOL "" FORCE)
set(GGML_NATIVE     OFF CACHE BOOL "" FORCE)
set(GGML_BACKEND_DL OFF CACHE BOOL "" FORCE)
set(GGML_CPU_KLEIDIAI OFF CACHE BOOL "" FORCE)
set(GGML_OPENMP     OFF CACHE BOOL "" FORCE)

# llama.cpp のビルドオプション
set(LLAMA_BUILD_TESTS    OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_EXAMPLES OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_SERVER   OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_COMMON   ON  CACHE BOOL "" FORCE)
set(LLAMA_OPENSSL        OFF CACHE BOOL "" FORCE)
set(BUILD_SHARED_LIBS    OFF CACHE BOOL "" FORCE)

# llama.cpp をサブプロジェクトとして追加
add_subdirectory(${LLAMA_SRC_DIR} build-llama)

# llama_bridge 共有ライブラリをビルド
add_library(
    llama_bridge
    SHARED
    llama_bridge.cpp
)

# Android ログライブラリ
find_library(log-lib log)

# インクルードパスを設定
target_include_directories(llama_bridge PRIVATE
    ${LLAMA_SRC_DIR}/include
    ${LLAMA_SRC_DIR}/ggml/include
    ${LLAMA_SRC_DIR}/common
)

# ARM64 向け最適化フラグ
if(ANDROID_ABI STREQUAL "arm64-v8a")
    target_compile_options(llama_bridge PRIVATE
        -march=armv8-a
        -O3
    )
endif()

# リンク: llama + common + log
target_link_libraries(
    llama_bridge
    llama
    common
    ${log-lib}
)
