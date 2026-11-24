cmake_minimum_required(VERSION 3.22.1)
project(axvyn_lame C)

set(LAME_DIR ${CMAKE_CURRENT_SOURCE_DIR}/lame-3.100)
set(LAME_LIB_DIR ${LAME_DIR}/libmp3lame)

set(LAME_SOURCES
    ${LAME_LIB_DIR}/bitstream.c
    ${LAME_LIB_DIR}/encoder.c
    ${LAME_LIB_DIR}/fft.c
    ${LAME_LIB_DIR}/gain_analysis.c
    ${LAME_LIB_DIR}/id3tag.c
    ${LAME_LIB_DIR}/lame.c
    ${LAME_LIB_DIR}/mpglib_interface.c
    ${LAME_LIB_DIR}/newmdct.c
    ${LAME_LIB_DIR}/presets.c
    ${LAME_LIB_DIR}/psymodel.c
    ${LAME_LIB_DIR}/quantize.c
    ${LAME_LIB_DIR}/quantize_pvt.c
    ${LAME_LIB_DIR}/reservoir.c
    ${LAME_LIB_DIR}/set_get.c
    ${LAME_LIB_DIR}/tables.c
    ${LAME_LIB_DIR}/takehiro.c
    ${LAME_LIB_DIR}/util.c
    ${LAME_LIB_DIR}/vbrquantize.c
    ${LAME_LIB_DIR}/VbrTag.c
    ${LAME_LIB_DIR}/version.c
)

# Build LAME as a separate shared library for clear LGPL dynamic-linking compliance.
add_library(mp3lame SHARED ${LAME_SOURCES})

target_include_directories(mp3lame PUBLIC
    ${LAME_DIR}/include
    ${LAME_LIB_DIR}
    ${LAME_LIB_DIR}/vector
    ${CMAKE_CURRENT_SOURCE_DIR}
)

target_compile_definitions(mp3lame PRIVATE
    HAVE_CONFIG_H=1
)

# LAME is old C code; silence noisy warnings but keep it buildable.
target_compile_options(mp3lame PRIVATE
    -Wno-unused-variable
    -Wno-unused-function
    -Wno-unused-but-set-variable
    -Wno-shift-negative-value
    -Wno-implicit-function-declaration
    -Wno-tautological-compare
    -Wno-absolute-value
)

add_library(axvyn_lame SHARED lame_jni.c)

target_include_directories(axvyn_lame PRIVATE
    ${LAME_DIR}/include
    ${CMAKE_CURRENT_SOURCE_DIR}
)

target_compile_definitions(axvyn_lame PRIVATE
    HAVE_CONFIG_H=1
)

target_link_libraries(axvyn_lame
    mp3lame
    log
    m
)
