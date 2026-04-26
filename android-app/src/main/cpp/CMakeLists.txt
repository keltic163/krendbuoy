cmake_minimum_required(VERSION 3.22.1)

project(vbam_frontend)

add_library(vbam_frontend SHARED
    native_bridge.cpp
)

find_library(log-lib log)

# Link against Android log + packaged libretro core runtime dependencies later.
target_link_libraries(vbam_frontend
    ${log-lib}
)
