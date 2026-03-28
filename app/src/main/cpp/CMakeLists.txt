cmake_minimum_required(VERSION 3.22.1)

project("luahook" LANGUAGES C CXX ASM)

# XDL
file(GLOB XDL_SRC
        "xdl/xdl/src/main/cpp/*.c"
        "memory/*.cpp"
)
add_library(xdl SHARED ${XDL_SRC})
# NDK 27 Android headers use __VA_OPT__ in C macros; compile xdl's C sources in gnu2x mode
# so those headers parse correctly without patching the vendored xdl submodule.
target_compile_options(xdl PRIVATE
        $<$<COMPILE_LANGUAGE:C>:-std=gnu2x>
)
target_compile_features(xdl PRIVATE cxx_std_20)
target_include_directories(xdl PUBLIC
        xdl/xdl/src/main/cpp/include
        memory/include
)

# Dobby
set(DOBBY_DIR ${CMAKE_CURRENT_SOURCE_DIR}/dobby)
add_library(dobby_lib STATIC IMPORTED)
set_target_properties(dobby_lib PROPERTIES IMPORTED_LOCATION
        ${DOBBY_DIR}/${ANDROID_ABI}/libdobby.a)

file(GLOB_RECURSE LUAHOOK_CORE_SRC CONFIGURE_DEPENDS
        "luahook/core/*.cpp"
        "luahook/bridge/*.cpp"
)

list(FILTER LUAHOOK_CORE_SRC EXCLUDE REGEX "luahook/core/abi/arm32_abi\\.cpp$")
list(FILTER LUAHOOK_CORE_SRC EXCLUDE REGEX "luahook/core/abi/arm64_abi\\.cpp$")

set(LUAHOOK_CORE_ASM)
if(ANDROID_ABI STREQUAL "arm64-v8a")
    list(APPEND LUAHOOK_CORE_SRC
            "luahook/core/abi/arm64_abi.cpp"
    )
    list(APPEND LUAHOOK_CORE_ASM
            "luahook/core/ffi/invoke_arm64.S"
            "luahook/core/hook/trampoline_arm64.S"
    )
elseif(ANDROID_ABI STREQUAL "armeabi-v7a")
    list(APPEND LUAHOOK_CORE_SRC
            "luahook/core/abi/arm32_abi.cpp"
    )
    list(APPEND LUAHOOK_CORE_ASM
            "luahook/core/ffi/invoke_arm32.S"
            "luahook/core/hook/trampoline_arm32.S"
    )
else()
    message(FATAL_ERROR "Unsupported ANDROID_ABI: ${ANDROID_ABI}")
endif()

add_library(luahook SHARED
        ${LUAHOOK_CORE_SRC}
        ${LUAHOOK_CORE_ASM}
)
target_compile_features(luahook PRIVATE cxx_std_20)

target_include_directories(luahook PRIVATE
        ${DOBBY_DIR}/include
        xdl/xdl/src/main/cpp/include
        memory/include
        ${CMAKE_CURRENT_SOURCE_DIR}
)

target_link_libraries(luahook PRIVATE android log xdl dobby_lib)
