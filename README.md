# 🍊 智能果汁机系统 (Smart Juice Machine System)

[![Android](https://img.shields.io/badge/Android-API%2035-green.svg)](https://developer.android.com/)
[![Kotlin](https://img.shields.io/badge/Kotlin-1.9.0-blue.svg)](https://kotlinlang.org/)
[![Compose](https://img.shields.io/badge/Jetpack%20Compose-1.5.0-orange.svg)](https://developer.android.com/jetpack/compose)
[![STM32](https://img.shields.io/badge/STM32-F103C8T6-red.svg)](https://www.st.com/en/microcontrollers-microprocessors/stm32f103.html)

> 基于Android + STM32的智能果汁机控制系统，支持多种饮品制作、实时监控、智能中断恢复等功能。

## 📱 项目预览

<div align="center">
  <img src="https://img.shields.io/badge/功能特性-智能制作-brightgreen" alt="智能制作"/>
  <img src="https://img.shields.io/badge/功能特性-实时监控-blue" alt="实时监控"/>
  <img src="https://img.shields.io/badge/功能特性-中断恢复-orange" alt="中断恢复"/>
  <img src="https://img.shields.io/badge/功能特性-图片管理-purple" alt="图片管理"/>
</div>

## ✨ 核心功能

### 🎯 智能饮品制作
- **多款饮品**: 茉莉雪芽、柳橙百香、满杯桑葚等
- **个性化定制**: 支持中杯/大杯、正常冰/去冰选择
- **精确配比**: 根据配方精确控制水量和果汁比例
- **实时监控**: 制作过程中实时监测重量变化

### 🔄 智能中断恢复
- **重量检测**: 实时监测杯子重量变化
- **中断处理**: 检测到异常时自动暂停制作
- **状态保存**: 完整保存制作参数（配方、杯型、冰度）
- **一键恢复**: 支持继续制作或重新制作选择

### 🖼️ 图片管理系统
- **本地选择**: 支持从本地相册选择饮品图片
- **智能适配**: 自动处理横图、竖图、正方形图片
- **编辑功能**: 支持图片裁剪、缩放、旋转
- **实时预览**: 编辑过程中实时预览效果

### 🛠️ 管理后台
- **配方管理**: 添加、编辑、删除饮品配方
- **库存监控**: 实时显示原料剩余量
- **设备控制**: 一键清洗、紧急停止、去皮称重
- **系统维护**: 恢复默认配方、系统状态监控

## 🏗️ 技术架构

### 系统架构图
```
┌─────────────────────────────────────────────────────────────┐
│                    Android 应用层                           │
│  ┌─────────────┐ ┌─────────────┐ ┌─────────────┐         │
│  │   UI界面    │ │  业务逻辑   │ │  数据管理   │         │
│  │ (Compose)   │ │ (ViewModel) │ │  (Room DB)  │         │
│  └─────────────┘ └─────────────┘ └─────────────┘         │
└─────────────────────────────────────────────────────────────┘
                              │
                    ┌─────────┴─────────┐
                    │    USB 串口通信    │
                    │   自定义协议       │
                    └─────────┬─────────┘
                              │
┌─────────────────────────────────────────────────────────────┐
│                   STM32 硬件控制层                          │
│  ┌─────────────┐ ┌─────────────┐ ┌─────────────┐         │
│  │  通信协议   │ │  硬件控制   │ │  传感器     │         │
│  │  (UART)    │ │ (PWM/GPIO) │ │ (HX711)    │         │
│  └─────────────┘ └─────────────┘ └─────────────┘         │
└─────────────────────────────────────────────────────────────┘
```

### 核心技术栈

#### Android 端
- **语言**: Kotlin
- **UI框架**: Jetpack Compose
- **架构模式**: MVVM + Repository
- **数据库**: Room Database
- **通信**: USB Serial for Android
- **图片加载**: Coil
- **导航**: Navigation Compose

#### STM32 端
- **微控制器**: STM32F103C8T6
- **开发语言**: C语言
- **硬件库**: STM32 HAL
- **通信**: UART串口
- **传感器**: HX711重量传感器
- **控制**: PWM电机控制

## 📦 安装指南

### 环境要求
- **Android Studio**: Arctic Fox 或更高版本
- **Android SDK**: API 35 (Android 15)
- **Kotlin**: 1.9.0 或更高版本
- **STM32开发环境**: Keil MDK-ARM

### 快速开始

#### 1. 克隆项目
```bash
git clone https://github.com/Deepmind666/juice2.git
cd juice2
```

#### 2. 打开Android项目
```bash
# 使用Android Studio打开项目
android-studio app/
```

#### 3. 配置STM32硬件
```bash
# 连接STM32开发板
# 烧录固件到STM32F103C8T6
```

#### 4. 运行应用
```bash
# 编译并运行Android应用
./gradlew assembleDebug
```

## 🔧 使用说明

### 基本操作流程

#### 1. 选择饮品
- 在主界面浏览可用饮品
- 点击饮品卡片查看详情
- 选择喜欢的饮品

#### 2. 定制参数
- **杯型选择**: 中杯（标准）或大杯（+¥2）
- **冰度选择**: 正常冰或去冰
- **价格显示**: 实时显示调整后的价格

#### 3. 开始制作
- 点击"开始制作"按钮
- 系统自动发送指令到STM32
- 实时监控制作进度

#### 4. 异常处理
- **重量检测**: 系统实时监测杯子重量
- **中断处理**: 检测到异常时弹出选择对话框
- **恢复选择**: 
  - **继续制作**: 使用相同参数重新制作
  - **重新制作**: 提示用户倒掉饮品重新开始

### 管理功能

#### 进入管理界面
- 长按主界面顶部标题栏
- 输入管理员密码（默认：123456）

#### 配方管理
- **添加配方**: 点击右下角"+"按钮
- **编辑配方**: 点击配方右侧编辑按钮
- **删除配方**: 点击配方右侧删除按钮
- **图片管理**: 在编辑界面选择本地图片

#### 设备控制
- **一键清洗**: 自动清洗系统管道
- **紧急停止**: 立即停止所有操作
- **去皮称重**: 校准重量传感器
- **恢复默认**: 恢复出厂配方设置

## 📡 通信协议

### 下行指令 (Android → STM32)
```
[0xFF][冰度][水量][果汁1][果汁2][果汁3][0xFE]
```

| 字节 | 说明 | 值范围 |
|------|------|--------|
| 0 | 起始标志 | 0xFF |
| 1 | 冰度选择 | 0x01=正常冰, 0x02=去冰 |
| 2 | 水量(ml) | 0-255 |
| 3 | 通道1果汁量 | 0-255 |
| 4 | 通道2果汁量 | 0-255 |
| 5 | 通道3果汁量 | 0-255 |
| 6 | 结束标志 | 0xFE |

### 上行状态 (STM32 → Android)
```
[状态码][状态信息]
```

| 状态码 | 说明 |
|--------|------|
| 0xAA | 制作完成 |
| 0xAB | 制作暂停 |
| 0xAC | 制作错误 |
| 0xAD | 重量异常 |

## 🎨 界面设计

### 设计理念
- **Material Design 3**: 采用最新的Material Design设计语言
- **响应式布局**: 适配不同屏幕尺寸
- **直观操作**: 简洁明了的用户界面
- **状态反馈**: 清晰的操作状态提示

### 主要界面
- **主界面**: 饮品展示和选择
- **定制界面**: 参数选择和确认
- **管理界面**: 配方管理和设备控制
- **编辑界面**: 配方编辑和图片管理

## 🔍 故障排除

### 常见问题

#### 1. 应用无法启动
```bash
# 检查Android SDK版本
# 确保API 35已安装
sdkmanager "platforms;android-35"
```

#### 2. 串口连接失败
```bash
# 检查USB权限
# 确保设备已正确连接
adb devices
```

#### 3. STM32无响应
```bash
# 检查固件是否正确烧录
# 验证串口配置
# 确认硬件连接
```

#### 4. 重量传感器异常
```bash
# 执行去皮操作
# 检查传感器连接
# 校准重量传感器
```

## 🤝 贡献指南

### 开发环境设置
1. Fork 项目到你的GitHub账户
2. Clone 你的Fork到本地
3. 创建功能分支: `git checkout -b feature/AmazingFeature`
4. 提交更改: `git commit -m 'Add some AmazingFeature'`
5. 推送到分支: `git push origin feature/AmazingFeature`
6. 创建Pull Request

### 代码规范
- 遵循Kotlin编码规范
- 使用有意义的变量和函数名
- 添加必要的注释
- 确保代码通过编译检查

## 📄 许可证

本项目采用 [MIT License](LICENSE) 许可证。

## 👨‍💻 作者

**李康锐** - *初始工作* - [Deepmind666](https://github.com/Deepmind666)

## 🙏 致谢

- [Jetpack Compose](https://developer.android.com/jetpack/compose) - 现代Android UI工具包
- [STM32 HAL](https://www.st.com/en/embedded-software/stm32cube-mcu-mpu-packages.html) - STM32硬件抽象层
- [USB Serial for Android](https://github.com/mik3y/usb-serial-for-android) - Android USB串口通信库
- [Coil](https://coil-kt.github.io/coil/) - Android图片加载库

## 📞 联系方式

- **项目链接**: [https://github.com/Deepmind666/juice2](https://github.com/Deepmind666/juice2)
- **问题反馈**: [Issues](https://github.com/Deepmind666/juice2/issues)
- **功能建议**: [Discussions](https://github.com/Deepmind666/juice2/discussions)

---

<div align="center">
  <strong>🍊 让每一杯果汁都充满科技的味道 🍊</strong>
</div> 