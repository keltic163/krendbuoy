cmake_minimum_required(VERSION 3.22.1)

project(vbam_frontend)

add_library(vbam_frontend SHARED
    native_bridge.cpp
)

target_include_directories(vbam_frontend PRIVATE
    ${CMAKE_SOURCE_DIR}/../../../../src/libretro/libretro-common/include
)

find_library(log-lib log)

target_link_options(vbam_frontend PRIVATE
    -Wl,--allow-shlib-undefined
)

target_link_libraries(vbam_frontend
    ${log-lib}
)
