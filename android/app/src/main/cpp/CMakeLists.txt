cmake_minimum_required(VERSION 3.22.1)
project(wapi_vulkan LANGUAGES CXX)

set(WAPI_SHARED_NATIVE_DIR "${CMAKE_CURRENT_SOURCE_DIR}/../../../../../shared/native")

add_library(
    wapi_core
    SHARED
    wapi_core_jni.cpp
    "${WAPI_SHARED_NATIVE_DIR}/wapi_core.cpp"
)

target_include_directories(wapi_core PRIVATE "${WAPI_SHARED_NATIVE_DIR}")
target_compile_features(wapi_core PRIVATE cxx_std_20)
target_compile_options(wapi_core PRIVATE -Wall -Wextra -Werror=return-type)

add_library(
    wapi_vulkan
    SHARED
    wapi_vulkan_capabilities.cpp
)

find_library(log-lib log)
find_library(vulkan-lib vulkan)

target_compile_features(wapi_vulkan PRIVATE cxx_std_20)
target_compile_options(wapi_vulkan PRIVATE -Wall -Wextra -Werror=return-type)
target_link_libraries(wapi_vulkan PRIVATE ${vulkan-lib} ${log-lib})
target_link_libraries(wapi_core PRIVATE ${log-lib})
