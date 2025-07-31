# 🍹 智能果汁机系统

<div align="center">

![Android](https://img.shields.io/badge/Android-3DDC84?style=for-the-badge&logo=android&logoColor=white)
![Kotlin](https://img.shields.io/badge/Kotlin-0095D5?style=for-the-badge&logo=kotlin&logoColor=white)
![STM32](https://img.shields.io/badge/STM32-03234B?style=for-the-badge&logo=stmicroelectronics&logoColor=white)
![C](https://img.shields.io/badge/C-00599C?style=for-the-badge&logo=c&logoColor=white)

**基于Android + STM32的智能果汁机控制系统**

[项目介绍](#项目介绍) • [功能特性](#功能特性) • [技术架构](#技术架构) • [快速开始](#快速开始) • [通信协议](#通信协议) • [开发指南](#开发指南)

</div>

---

## 📋 项目介绍

这是一个完整的智能果汁机控制系统，采用**Android应用 + STM32硬件**的架构设计。系统通过USB串口实现Android应用与STM32硬件之间的实时通信，支持精确的液体配比、重力补偿算法和多种饮品制作功能。

### 🎯 核心特性

- **🎨 现代化UI界面** - 基于Jetpack Compose构建的流畅用户体验
- **⚡ 实时通信** - USB串口实现Android与STM32的双向通信
- **⚖️ 精确称重** - HX711传感器配合重力补偿算法
- **🔧 智能控制** - 4通道电机PWM控制，支持多种饮品配方
- **🛡️ 安全保护** - 独立看门狗防止系统死机
- **📊 数据管理** - Room数据库存储配方和配置信息

## 🚀 功能特性

### Android应用功能
- ✅ **USB串口通信** - 自动检测和连接STM32设备
- ✅ **饮品制作** - 支持多种饮品配方和杯型选择
- ✅ **管理员功能** - 清洗、停止、去皮、称重等操作
- ✅ **配方管理** - 使用Room数据库存储和管理配方
- ✅ **实时状态** - 显示制作进度和设备状态
- ✅ **权限管理** - 动态USB权限请求和处理

### STM32硬件功能
- ✅ **指令解析** - 解析7字节指令协议
- ✅ **电机控制** - 4通道PWM电机控制
- ✅ **称重系统** - HX711传感器精确称重
- ✅ **重力补偿** - 智能液体配比算法
- ✅ **看门狗保护** - 防止系统死机
- ✅ **状态反馈** - 实时状态回传

## 🏗️ 技术架构

### Android端技术栈
```
📱 Android应用层
├── 🎨 UI框架: Jetpack Compose
├── 🏗️ 架构模式: MVVM
├── 💾 数据库: Room
├── 🔌 串口通信: USB Serial for Android
├── 🧭 导航: Navigation Compose
└── 🎯 语言: Kotlin
```

### STM32端技术栈
```
🔧 硬件控制层
├── 🎛️ 主控: STM32F103C8T6
├── ⚖️ 传感器: HX711称重传感器
├── 🔌 通信: UART串口
├── ⚡ 电机: PWM控制
├── 🛡️ 保护: 独立看门狗
└── 💻 语言: C
```

## 🚀 快速开始

### 环境要求
- **Android Studio** 2023.1.1 或更高版本
- **Android SDK** API 26+ (Android 8.0+)
- **STM32开发环境** Keil MDK 或 STM32CubeIDE
- **硬件设备** STM32F103C8T6开发板

### 安装步骤

#### 1. 克隆项目
```bash
git clone https://github.com/Deepmind666/juice2.git
cd juice2
```

#### 2. Android应用编译
```bash
# 打开Android Studio
# 导入项目: File -> Open -> 选择juice2目录
# 等待Gradle同步完成
# 点击运行按钮或使用快捷键 Shift+F10
```

#### 3. STM32程序烧录
```bash
# 使用Keil MDK打开项目
# 路径: 果子机 ，7.2双串口/果子机 ，7.2双串口/
# 编译项目: Project -> Build Target
# 烧录程序: Flash -> Download
```

#### 4. 硬件连接
```
Android设备 ←→ USB线 ←→ STM32开发板
```

## 📡 通信协议

### 下行指令 (Android → STM32)
**格式**: `FF [指令码] [数据1] [数据2] [数据3] [数据4] FE`

| 指令码 | 功能 | 示例 |
|--------|------|------|
| `0x01` | 正常冰制作 | `FF 01 69 AF 00 00 FE` |
| `0x02` | 去冰制作 | `FF 02 69 AF 00 00 FE` |
| `0x03` | 管理员清洗 | `FF 03 00 00 00 00 FE` |
| `0x04` | 管理员停止 | `FF 04 00 00 00 00 FE` |
| `0x05` | 管理员去皮 | `FF 05 00 00 00 00 FE` |
| `0x06` | 管理员称重 | `FF 06 00 00 00 00 FE` |

### 上行响应 (STM32 → Android)
**格式**: `FD [回显] [状态码] [数据] FC`

| 状态码 | 含义 |
|--------|------|
| `0xAA` | 任务成功 |
| `0xAB` | 任务暂停 |
| `0xAC` | 任务失败 |
| `0xAD` | 指令无效 |

## 🛠️ 开发指南

### 项目结构
```
juice2/
├── 📱 app/                          # Android应用
│   ├── src/main/java/com/example/juicemachine/
│   │   ├── MainActivity.kt          # 主活动
│   │   ├── data/
│   │   │   ├── database/           # Room数据库
│   │   │   ├── hardware/           # 硬件管理
│   │   │   └── repository/         # 数据仓库
│   │   └── ui/                     # UI界面
│   └── build.gradle.kts            # 构建配置
├── 🔧 果子机 ，7.2双串口/           # STM32项目
│   ├── User/main.c                 # 主程序
│   ├── Hardware/                   # 硬件驱动
│   └── Library/                    # 系统库
└── 📚 docs/                        # 项目文档
```

### 核心文件说明

#### Android端
- **`MainActivity.kt`** - 应用入口，处理USB权限和导航
- **`HardwareManager.kt`** - 硬件管理器，负责与STM32通信
- **`DrinkMenuScreen.kt`** - 饮品菜单界面
- **`AdminScreen.kt`** - 管理员功能界面

#### STM32端
- **`main.c`** - 主程序，处理指令和硬件控制
- **`hx711.c/h`** - 称重传感器驱动
- **`Motor.c/h`** - 电机控制驱动
- **`Gra_Com.c/h`** - 重力补偿算法

### 开发注意事项
1. **USB权限** - Android需要动态请求USB权限
2. **串口配置** - 波特率9600，8数据位，1停止位，无校验
3. **指令格式** - 严格按照7字节指令格式发送
4. **错误处理** - 实现完整的错误处理和状态反馈
5. **硬件连接** - 确保STM32与Android设备正确连接

## 📊 项目状态

- ✅ **Android应用** - 基本完成，包含完整UI和通信功能
- ✅ **STM32程序** - 已完成，支持所有指令
- ✅ **通信协议** - 已标准化，支持双向通信
- ✅ **文档完善** - 包含详细的技术文档和开发指南
- 🔄 **测试验证** - 正在进行实际硬件测试

## 🤝 贡献指南

欢迎提交Issue和Pull Request来改进项目！

### 贡献步骤
1. Fork 本仓库
2. 创建特性分支 (`git checkout -b feature/AmazingFeature`)
3. 提交更改 (`git commit -m 'Add some AmazingFeature'`)
4. 推送到分支 (`git push origin feature/AmazingFeature`)
5. 打开 Pull Request

## 📄 许可证

本项目采用 MIT 许可证 - 查看 [LICENSE](LICENSE) 文件了解详情

## 📞 联系方式

- **项目维护者**: Deepmind666
- **GitHub**: [https://github.com/Deepmind666](https://github.com/Deepmind666)
- **项目地址**: [https://github.com/Deepmind666/juice2](https://github.com/Deepmind666/juice2)

---

<div align="center">

**如果这个项目对你有帮助，请给个⭐️ Star！**

</div> 