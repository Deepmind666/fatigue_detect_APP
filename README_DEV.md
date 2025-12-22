# 智能果汁机前端B版 - 开发者文档 (README_DEV)

本项目是基于 Android Jetpack Compose 开发的智能果汁机前端控制系统。主要负责用户交互、配方管理、订单统计以及与下位机硬件的串口通信。

## 5. 权限与登录逻辑
*   **登录入口**：在点单页（`DrinkMenuScreen`）长按标题栏即可弹出登录弹窗。
*   **默认密码**：
    *   **管理员后台**：`6` (跳转至 `AdminScreen`)
    *   **客户管理界面**：`0` (跳转至 `CustomerCleanScreen`)
*   **代码位置**：`DrinkMenuViewModel.kt` 中的 `onLoginAttempt` 方法。

## 6. 环境配置指南 (新手必看)

如果你的朋友刚下载 Android Studio，请务必按照以下步骤配置，否则项目可能无法编译：

### 1.1 JDK 版本配置
*   **要求**：本项目要求使用 **JDK 17**。
*   **配置方法**：
    1.  打开 Android Studio 设置：`File` -> `Settings` (macOS: `Android Studio` -> `Settings`)。
    2.  导航到：`Build, Execution, Deployment` -> `Build Tools` -> `Gradle`。
    3.  在 `Gradle JDK` 下拉框中，选择 **JDK 17**。如果列表中没有，请点击 `Download JDK` 下载并安装。

### 1.2 Gradle 网络问题 (国内环境)
*   **现状**：本项目配置文件中 `distributionUrl` 指向了本地路径（可能是为了离线开发），这在其他电脑上会报错。
*   **修复方法**：
    1.  打开 `gradle/wrapper/gradle-wrapper.properties`。
    2.  将 `distributionUrl` 修改为官方下载地址：
        `distributionUrl=https\://services.gradle.org/distributions/gradle-8.12-bin.zip`
    3.  如果下载极其缓慢，建议配置 **阿里云镜像** 或使用科学上网。

### 1.3 Android SDK 配置
*   **要求**：安装 **Android SDK 34**。
*   **配置方法**：
    1.  打开 `Tools` -> `SDK Manager`。
    2.  在 `SDK Platforms` 选项卡中，勾选 `Android 14.0 (UpsideDownCake)` (API Level 34)。
    3.  在 `SDK Tools` 中，确保安装了 `Android SDK Build-Tools 34.0.0`。

### 1.4 常见编译报错
*   **"Missing dependency: libs.plugins.androidApplication"**：请点击右上角的 `Sync Project with Gradle Files` 图标，让 IDE 自动下载 `libs.versions.toml` 中定义的插件。
*   **"Namespace not specified"**：通常是 Gradle 版本不匹配导致。请确保 `build.gradle.kts` 中的 `namespace` 与项目包名一致。

## 2. 项目目录结构

```text
d:\juice2\
├── app\src\main\java\com\example\juicemachine\
│   ├── data\                       # 数据层
│   │   ├── database\               # Room 数据库配置 (Recipe, Order, CupConfig)
│   │   ├── hardware\               # 硬件通信 (HardwareManager, 串口协议解析)
│   │   ├── repository\             # 数据仓库 (RecipeRepository, OrderRepository)
│   │   ├── AppPreferences.kt       # SharedPreferences 配置
│   │   └── PreferencesRepository.kt # 配置项仓库
│   ├── ui\                         # UI 层 (Jetpack Compose)
│   │   ├── model\                  # UI 数据模型 (如 IceMode)
│   │   ├── theme\                  # Compose 主题配置 (Color, Theme, Type)
│   │   ├── viewmodel\              # 业务逻辑 (DrinkMenuViewModel, StatisticsViewModel)
│   │   ├── AdminScreen.kt          # 管理员后台页面
│   │   ├── AdsScreen.kt            # 广告循环播放页面
│   │   ├── AppNavigation.kt        # 导航路由中心
│   │   ├── CustomerCleanScreen.kt  # 客户自助清洗页面
│   │   ├── DrinkMenuScreen.kt      # 用户点单主页面
│   │   ├── EditRecipeScreen.kt     # 配方编辑页面
│   │   ├── Splashscreen.kt         # 启动页
│   │   └── StatisticsScreen.kt     # 统计报表页面
│   ├── util\                       # 工具类
│   │   └── DebugLogger.kt          # 日志记录工具 (支持崩溃日志落盘)
│   └── JuiceMachineApplication.kt  # 应用入口，初始化全局配置
└── app\src\main\res\               # 资源文件 (图片、布局、XML配置)
```

## 2. 核心功能实现说明

### 2.1 硬件通信逻辑 (HardwareManager.kt)
*   **实现方式**：使用 `usb-serial-for-android` 库进行 USB 串口通信。
*   **协议格式**：采用 7 字节定长协议 `FF CMD P0 P1 P2 P3 FE`。
    *   `0x01`: 出水/出汁控制。
    *   `0x03`: 任务完成（中性）。
    *   `0x09`: 状态上报（温度、重量、异常）。
*   **健壮性设计**：
    *   包含自动重连机制（指数回退策略）。
    *   串口读写加锁，防止并发冲突。
    *   粘包处理：通过 `frameBuffer` 配合 `FF`/`FE` 边界解析完整帧。

### 2.2 业务逻辑中心 (DrinkMenuViewModel.kt)
*   **状态管理**：使用 `StateFlow` 管理 `DrinkMenuUiState`，驱动 UI 刷新。
*   **下单流程**：
    1.  UI 触发 `onConfirmDialog`。
    2.  计算实际用量（考虑杯型缩放、果肉补偿）。
    3.  订单入库（PENDING 状态）。
    4.  通过 `HardwareManager` 发送制作指令。
    5.  监听硬件回调（CMD 0x03），更新订单状态为 COMPLETED 并扣减库存。
*   **异常处理**：监听重量异常上报，弹出 `WeightChangeDialog` 提醒用户。

### 2.3 导航逻辑 (AppNavigation.kt)
*   使用 `NavHost` 管理页面跳转。
*   **广告逻辑**：默认进入 `AdsScreen`，长时间无人操作（15s）自动返回广告页。
*   **动态图片加载**：广告图支持从内部资源、外部文件或 ContentProvider 加载，并具备失效清理机制。

### 2.4 配方与库存管理 (RecipeRepository.kt)
*   使用 Room 数据库存储配方。
*   **库存逻辑**：每个配方包含 `defaultRemainingWeight` (总量) 和 `currentRemainingWeight` (当前余量)。下单成功后，自动根据计算出的 `actualJuiceConsumption` 扣减余量。

### 2.5 日志与诊断 (DebugLogger.kt)
*   **功能**：统一接管 `Log.i/w/e`。
*   **崩溃拦截**：在 `JuiceMachineApplication` 中通过 `Thread.setDefaultUncaughtExceptionHandler` 拦截未捕获异常，并将堆栈信息强制写入 `debug_logs.txt`，方便售后诊断。

## 3. 开发注意事项
*   **重量单位**：全系统统一使用整数（克），UI 显示不进行小数格式化。
*   **线程安全**：数据库操作和硬件指令发送必须在 `Dispatchers.IO` 中执行。
*   **权限说明**：USB 通信需要设备支持 OTG 并在 `AndroidManifest.xml` 中配置相应的 `intent-filter`。

## 4. 关键逻辑流程图 (逻辑概要)
1.  **冷启动**：`Splashscreen` -> 检查硬件连接 -> 进入 `AdsScreen`。
2.  **点单**：点击广告 -> `DrinkMenuScreen` -> 选择配方 -> `WeightChangeDialog` (检测杯子) -> 发送指令 -> 硬件回调 -> 统计入库。
3.  **管理**：长按标题 -> 登录 -> `AdminScreen` -> 修改配方/查看统计/手动清洗。
