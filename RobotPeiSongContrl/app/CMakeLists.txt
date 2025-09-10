cmake_minimum_required(VERSION 3.10.2)

# 设置项目名称
project("robot_control")

# 添加串口库
add_library(
        serial_port
        SHARED
        src/main/cpp/serial_port.c
)

# 添加日志库
find_library(
        log-lib
        log
)

# 链接库
target_link_libraries(
        serial_port
        ${log-lib}
)