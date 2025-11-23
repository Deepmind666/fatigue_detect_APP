# 智能果汁机前端（版本 B）

当前分支已切换到版本 B（饮品与广告资源使用 B 名称与图片），并完成关键修复：统一串口协议、保存→发送闭环、图片稳定性（A/B 名解析与兜底）、统计自动刷新。

## 串口协议

- 发送：`FF + 9 数据位 + FE`（11 字节）。正常冰/去冰/热饮与管理/特殊指令统一 9 数据位，未用位补零。
- 接收：`FF + 5 数据位 + FE`（7 字节）。完成帧与异常/温度/重量上报按数据位解析。

实现位置：`data/hardware/HardwareManager.kt`（构帧、校验与解析）。

## 资源与版本切换

- 内置广告为 B 名：`AppNavigation.getBuiltInAds`、`JuiceMachineApplication.preheatAdsAsync`。
- 新库默认配方为 B 名（仅空库插入）：`JuiceMachineApplication.ensureDefaultRecipesAsync`。
- 饮品/后台/编辑页图片解析优先 B 名；缺失时尝试 A 名；仍缺失则占位，确保显示稳定。
- 广告页监听 `ads_prefs` 变化并重算列表，禁用缓存，避免旧图残留。

## 保存→发送一致性

- 保存字段级更新后强制重读最新值；下单前再次读库组帧，打印 `SendCheck` 日志，确保“保存即生效”。

## 统计

- 订单完成/失败/取消/兜底完成都会触发统计刷新；统计页订阅事件重载汇总/趋势/热销/消耗/杯型报表。

## 构建

- `./gradlew.bat assembleDebug -x test`

## 故障排查

- 广告页仍旧：后台“广告图片”保存后即刷新；必要时点击“清理”移除不可读项。
- 饮品图未更新：编辑页保存后列表即时替换；若未替换，检查所选 URI 可读性或重启应用。
- 保存后发送仍旧：查看 `SendCheck` 日志确认参数是否最新。

## 发布与分支

- 切换到 B 的分支：`switch-to-B-2025-11-23`（已推送远端）。如需覆盖 main，请合并该分支或将 main 重置到此分支最新提交。

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
