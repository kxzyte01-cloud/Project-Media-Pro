cmake_minimum_required(VERSION 3.22.1)
project(mediapro_whisper CXX C)

set(WHISPER_DIR ${CMAKE_CURRENT_SOURCE_DIR}/whisper.cpp)

if(NOT EXISTS ${WHISPER_DIR}/include/whisper.h)
    message(WARNING
        "whisper.cpp sources not found at ${WHISPER_DIR} — building stub target only. "
        "Clone ggml-org/whisper.cpp into app/src/main/cpp/whisper.cpp to enable native Whisper.")
    add_library(mediapro_whisper_stub STATIC ${CMAKE_CURRENT_SOURCE_DIR}/whisper_stub.c)
    return()
endif()

set(BUILD_SHARED_LIBS OFF CACHE BOOL "" FORCE)
set(WHISPER_BUILD_EXAMPLES OFF CACHE BOOL "" FORCE)
set(WHISPER_BUILD_TESTS OFF CACHE BOOL "" FORCE)
set(WHISPER_BUILD_SERVER OFF CACHE BOOL "" FORCE)
set(GGML_NATIVE OFF CACHE BOOL "" FORCE)
set(GGML_OPENMP OFF CACHE BOOL "" FORCE)
set(GGML_LLAMAFILE OFF CACHE BOOL "" FORCE)

# Always optimize ggml/whisper with -O3 even in Debug APKs so on-device transcription runs in seconds, not minutes.
set(CMAKE_C_FLAGS_DEBUG "${CMAKE_C_FLAGS_DEBUG} -O3")
set(CMAKE_CXX_FLAGS_DEBUG "${CMAKE_CXX_FLAGS_DEBUG} -O3")
add_compile_options(-O3)

add_subdirectory(${WHISPER_DIR} ${CMAKE_CURRENT_BINARY_DIR}/whisper_build EXCLUDE_FROM_ALL)

add_library(mediapro_whisper SHARED mediapro_whisper_jni.cpp)
target_link_libraries(mediapro_whisper PRIVATE whisper log)
target_compile_options(mediapro_whisper PRIVATE -O3)
