cmake_minimum_required(VERSION 3.22.1)

project("luahook")

file(GLOB XDL_SRC
        "xdl/xdl/src/main/cpp/*.c"
        "memory/*.cpp"
)
add_library(xdl SHARED ${XDL_SRC})
target_compile_features(xdl PRIVATE c_std_17)
target_include_directories(xdl PUBLIC
        xdl/xdl/src/main/cpp/include
        memory/include
)

add_library(luahook SHARED luahook.cpp)
target_link_libraries(luahook PRIVATE android log PRIVATE xdl)
