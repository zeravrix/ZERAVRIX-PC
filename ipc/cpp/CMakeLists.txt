cmake_minimum_required(VERSION 3.25)

project(zeravrix_ipc LANGUAGES CXX)

add_library(zeravrix_ipc INTERFACE)
target_compile_features(zeravrix_ipc INTERFACE cxx_std_20)
target_include_directories(zeravrix_ipc INTERFACE
    $<BUILD_INTERFACE:${CMAKE_CURRENT_SOURCE_DIR}/include>
)

if (WIN32)
    target_compile_definitions(zeravrix_ipc INTERFACE NOMINMAX UNICODE _UNICODE)
    target_link_libraries(zeravrix_ipc INTERFACE advapi32)
endif()
