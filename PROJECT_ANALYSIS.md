# 智能果汁机项目分析文档

## 项目概述
这是一个智能果汁机项目，包含Android应用和STM32硬件控制两部分。

## 项目结构

### Android应用部分
- **包名**: `com.example.juicemachine`
- **架构**: MVVM + Jetpack Compose
- **主要组件**:
  - `MainActivity.kt` - 主活动，处理USB权限和导航
  - `HardwareManager.kt` - 硬件管理器，负责与STM32通信
  - `DrinkMenuScreen.kt` - 饮品菜单界面
  - `AdminScreen.kt` - 管理员界面
  - `AppDatabase.kt` - Room数据库

### STM32硬件部分
- **主控**: STM32F103C8T6
- **核心文件**:
  - `main.c` - 主程序，处理指令和硬件控制
  - `Hardware/` - 硬件驱动文件
    - `hx711.c/h` - 称重传感器
    - `Motor.c/h` - 电机控制
    - `PWM.c/h` - PWM控制
    - `Gra_Com.c/h` - 重力补偿算法

## 通信协议

### 下行指令 (App -> STM32)
- **格式**: 7字节 `FF [指令码] [数据1] [数据2] [数据3] [数据4] FE`
- **指令码**:
  - `0x01` - 正常冰制作
  - `0x02` - 去冰制作
  - `0x03` - 管理员清洗
  - `0x04` - 管理员停止
  - `0x05` - 管理员去皮
  - `0x06` - 管理员称重

### 上行响应 (STM32 -> App)
- **格式**: 5字节 `FD [回显] [状态码] [数据] FC`
- **状态码**:
  - `0xAA` - 任务成功
  - `0xAB` - 任务暂停
  - `0xAC` - 任务失败
  - `0xAD` - 指令无效

## 重要路径记录

### Android应用
- **主活动**: `app/src/main/java/com/example/juicemachine/MainActivity.kt`
- **硬件管理**: `app/src/main/java/com/example/juicemachine/data/hardware/HardwareManager.kt`
- **UI界面**: `app/src/main/java/com/example/juicemachine/ui/`
- **数据库**: `app/src/main/java/com/example/juicemachine/data/database/`
- **构建配置**: `app/build.gradle.kts`

### STM32硬件
- **主程序**: `果子机 ，7.2双串口/果子机 ，7.2双串口/User/main.c`
- **硬件驱动**: `果子机 ，7.2双串口/果子机 ，7.2双串口/Hardware/`
- **系统文件**: `果子机 ，7.2双串口/果子机 ，7.2双串口/Library/`

## 核心功能

### Android应用功能
1. **USB串口通信** - 与STM32建立串口连接
2. **饮品制作** - 发送制作指令到STM32
3. **管理员功能** - 清洗、停止、去皮、称重
4. **配方管理** - 使用Room数据库存储配方
5. **UI界面** - 使用Jetpack Compose构建现代化界面

### STM32硬件功能
1. **指令解析** - 解析来自Android的7字节指令
2. **电机控制** - 控制4个泵的启停
3. **称重功能** - 使用HX711传感器进行重量测量
4. **重力补偿** - 实现精确的液体配比
5. **看门狗** - 防止系统死机

## 技术栈

### Android端
- **语言**: Kotlin
- **UI框架**: Jetpack Compose
- **架构**: MVVM
- **数据库**: Room
- **串口通信**: USB Serial for Android
- **导航**: Navigation Compose

### STM32端
- **语言**: C
- **主控**: STM32F103C8T6
- **传感器**: HX711称重传感器
- **通信**: UART串口
- **电机**: PWM控制

## 项目状态
- Android应用基本完成，包含完整的UI和通信功能
- STM32硬件控制程序已完成，支持所有指令
- 通信协议已标准化，支持双向通信
- 项目已具备完整的功能，可以进行实际测试

## 备份信息
- **Git仓库**: https://github.com/Deepmind666/juice2.git
- **备份时间**: 2025-01-15
- **备份内容**: 完整的Android应用和STM32代码 