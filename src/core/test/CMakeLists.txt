# This defines the `vbam-core-fake` library, which is used for providing a fake
# implementation of the core library for testing purposes.

if(NOT BUILD_TESTING)
    return()
endif()

add_library(vbam-core-fake OBJECT)

target_sources(vbam-core-fake
    PRIVATE
    fake_core.cpp
)

# suite_runner: headless driver for mGBA's suite.gba accuracy tests.
add_executable(suite_runner suite_runner.cpp)
target_link_libraries(suite_runner PRIVATE vbam-core)
if(APPLE)
    target_link_libraries(suite_runner PRIVATE
        "-framework CoreFoundation" iconv)
endif()
