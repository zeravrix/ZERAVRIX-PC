cmake_minimum_required(VERSION 3.25)

project(ZERAVRIX_DEV_NATIVE VERSION 1.4.0 LANGUAGES CXX)

set(CMAKE_CXX_STANDARD 20)
set(CMAKE_CXX_STANDARD_REQUIRED ON)
set(CMAKE_CXX_EXTENSIONS OFF)

add_subdirectory(${CMAKE_CURRENT_SOURCE_DIR}/../ipc/cpp ${CMAKE_CURRENT_BINARY_DIR}/ipc)

add_library(zeravrix_core INTERFACE)
target_compile_features(zeravrix_core INTERFACE cxx_std_20)
target_include_directories(zeravrix_core INTERFACE
    $<BUILD_INTERFACE:${CMAKE_CURRENT_SOURCE_DIR}/core/include>
)

if (WIN32)
    find_package(Microsoft.WindowsAppSDK CONFIG REQUIRED)

    add_executable(ZeravrixDev WIN32
        app/src/App.cpp
        app/src/JvmHostSupervisor.cpp
        app/src/main.cpp
        reader/src/WicImageDecoder.cpp
    )
    set_target_properties(ZeravrixDev PROPERTIES OUTPUT_NAME "Zeravrix dev")
    target_include_directories(ZeravrixDev PRIVATE app/include reader/include)
    target_compile_definitions(ZeravrixDev PRIVATE UNICODE _UNICODE NOMINMAX)
    target_link_libraries(ZeravrixDev PRIVATE
        zeravrix_ipc
        zeravrix_core
        Microsoft::WindowsAppSDK
        shell32
        ole32
        windowscodecs
    )
else()
    add_executable(zeravrix-native-host-check app/src/portable_main.cpp)
    target_link_libraries(zeravrix-native-host-check PRIVATE zeravrix_ipc zeravrix_core)
    target_include_directories(zeravrix-native-host-check PRIVATE reader/include)

    add_executable(zeravrix-reader-cache-check tests/reader_cache_check.cpp)
    target_compile_features(zeravrix-reader-cache-check PRIVATE cxx_std_20)
    target_link_libraries(zeravrix-reader-cache-check PRIVATE zeravrix_ipc zeravrix_core)
    target_include_directories(zeravrix-reader-cache-check PRIVATE reader/include)
endif()
