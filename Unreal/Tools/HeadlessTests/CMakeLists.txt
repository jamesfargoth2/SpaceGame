cmake_minimum_required(VERSION 3.16)
project(GalacticProcGenHeadlessTests CXX)

# Builds the engine-independent part of the GalacticProcGen plugin (generators + test cases)
# against the tiny UE stand-in in Shim/, so the procedural generation can be tested in CI or on
# machines without Unreal. Runtime actors/subsystems are not compiled here.

set(CMAKE_CXX_STANDARD 20)
set(CMAKE_CXX_STANDARD_REQUIRED ON)
if(NOT CMAKE_BUILD_TYPE)
	set(CMAKE_BUILD_TYPE Release)
endif()

set(PLUGIN_DIR ${CMAKE_CURRENT_SOURCE_DIR}/../../Plugins/GalacticProcGen)
set(MODULE_DIR ${PLUGIN_DIR}/Source/GalacticProcGen)
set(GEN_DIR ${CMAKE_CURRENT_BINARY_DIR}/generated)

find_package(Python3 REQUIRED COMPONENTS Interpreter)

# Stand-in for UnrealHeaderTool: empty *.generated.h + JSON readers for the data-table rows.
file(GLOB_RECURSE PUBLIC_HEADERS CONFIGURE_DEPENDS ${MODULE_DIR}/Public/*.h)
set(ROW_STRUCTS FGOSpectralClassDef FGOPlanetTypeDef FGOBiomeDef FGONameListDef FGOHubRoomTemplate FGOAssetKitRow)
add_custom_command(
	OUTPUT ${GEN_DIR}/HeadlessReflection.generated.inl
	COMMAND ${Python3_EXECUTABLE} ${CMAKE_CURRENT_SOURCE_DIR}/gen_reflection.py ${MODULE_DIR}/Public ${GEN_DIR} ${ROW_STRUCTS}
	DEPENDS ${PUBLIC_HEADERS} ${CMAKE_CURRENT_SOURCE_DIR}/gen_reflection.py
	COMMENT "Generating headless reflection")
add_custom_target(GOHeadlessReflection DEPENDS ${GEN_DIR}/HeadlessReflection.generated.inl)

file(GLOB GENERATOR_SOURCES CONFIGURE_DEPENDS
	${MODULE_DIR}/Private/Core/*.cpp
	${MODULE_DIR}/Private/Universe/*.cpp
	${MODULE_DIR}/Private/Planet/*.cpp
	${MODULE_DIR}/Private/Hub/*.cpp
	${MODULE_DIR}/Private/Tests/GOProcGenTestCases.cpp)

add_executable(GOHeadlessTests main.cpp HeadlessContent.cpp Preview.cpp ${GENERATOR_SOURCES})
add_dependencies(GOHeadlessTests GOHeadlessReflection)
target_include_directories(GOHeadlessTests PRIVATE
	${CMAKE_CURRENT_SOURCE_DIR}/Shim
	${CMAKE_CURRENT_SOURCE_DIR}
	${GEN_DIR}
	${MODULE_DIR}/Public
	${MODULE_DIR}/Private)
target_compile_definitions(GOHeadlessTests PRIVATE
	GO_HEADLESS_TESTS=1
	GO_DEFAULT_DATA_DIR="${PLUGIN_DIR}/Data")
if(CMAKE_CXX_COMPILER_ID MATCHES "GNU|Clang")
	target_compile_options(GOHeadlessTests PRIVATE -Wall -Wextra -Wshadow -Wno-unused-parameter -Werror)
endif()

enable_testing()
add_test(NAME GalacticProcGen COMMAND GOHeadlessTests)
