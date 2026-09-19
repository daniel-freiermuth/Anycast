cmake_minimum_required(VERSION 3.22.1)

project(creolecast_raop)

add_library(creolecast_raop SHARED
    alac_wrapper.cpp
)

find_library(log-lib log)

target_link_libraries(creolecast_raop ${log-lib})
