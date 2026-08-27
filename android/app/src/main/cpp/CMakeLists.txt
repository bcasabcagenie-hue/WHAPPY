cmake_minimum_required(VERSION 3.22.1)
project(wapi_vulkan LANGUAGES CXX)

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

