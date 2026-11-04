cmake_minimum_required(VERSION 3.22.1)
project("aira_native")

add_library(
    aira_native
    SHARED
    aira_native.cpp
)

find_library(
    log-lib
    log
)

target_link_libraries(
    aira_native
    ${log-lib}
)
