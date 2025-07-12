# STM32代码修复建议

## 问题分析

当前STM32代码存在以下问题：
1. **指令处理不完整**：只处理了0x01指令，缺少0x02-0x05管理员指令
2. **制作逻辑缺失**：收到配方后只打印参数，没有实际控制硬件制作
3. **协议不一致**：存在错误的0x00处理分支

## 修复方案

### 1. 完善指令处理逻辑

```c
void ProcessCommand(void)
{
    float weight;
    if(Serial_GetRxFlag()) // 检查是否收到有效数据包
    {
        switch(Serial_RxPacket[0]) // 指令类型是接收数据的第一个字节
        {
            case 0x01: // 配方设置指令（制作饮料）
            {
                uint8_t materialA = Serial_RxPacket[1]; // 水量
                uint8_t materialB = Serial_RxPacket[2]; // 通道1果汁
                uint8_t materialC = Serial_RxPacket[3]; // 通道2果汁
                uint8_t materialD = Serial_RxPacket[4]; // 通道3果汁
                
                Serial_Printf("Recipe received: Water=%dg, Ch1=%dg, Ch2=%dg, Ch3=%dg\r\n", 
                             materialA, materialB, materialC, materialD);
                
                // 执行实际制作逻辑
                ExecuteRecipe(materialA, materialB, materialC, materialD);
                break;
            }
            
            case 0x02: // 管理员清洗开始
                Serial_Printf("Admin cleaning started\r\n");
                StartCleaning();
                break;
                
            case 0x03: // 管理员清洗结束
                Serial_Printf("Admin cleaning finished\r\n");
                StopCleaning();
                break;
                
            case 0x04: // 管理员去皮指令
                HX711_Tare();
                Serial_Printf("Tare completed. Offset: %.2f\r\n", HX711_GetOffset());
                break;
            
            case 0x05: // 管理员称重
                weight = HX711_GetWeight(10); // 10次测量平均
                Serial_Printf("Weight: %d g\r\n", (uint32_t)weight);
                break;
            
            default:
                Serial_Printf("Unknown command type: 0x%02X\r\n", Serial_RxPacket[0]);
                break;
        }
    }
}
```

### 2. 添加制作执行函数

```c
void ExecuteRecipe(uint8_t water, uint8_t juice1, uint8_t juice2, uint8_t juice3)
{
    Serial_Printf("Starting recipe execution...\r\n");
    
    // 1. 出水
    if (water > 0) {
        Serial_Printf("Dispensing %dg water...\r\n", water);
        Motor_ExtendedCtrl(WATER_PUMP, 100); // 启动水泵
        // 根据水量计算时间，这里需要根据实际硬件调整
        uint32_t waterTime = water * 10; // 假设每克水需要10ms
        Delay_ms(waterTime);
        Motor_ExtendedCtrl(WATER_PUMP, 0); // 停止水泵
    }
    
    // 2. 出果汁通道1
    if (juice1 > 0) {
        Serial_Printf("Dispensing %dg juice from channel 1...\r\n", juice1);
        Motor_ExtendedCtrl(JUICE_PUMP_1, 100);
        uint32_t juiceTime = juice1 * 15; // 假设每克果汁需要15ms
        Delay_ms(juiceTime);
        Motor_ExtendedCtrl(JUICE_PUMP_1, 0);
    }
    
    // 3. 出果汁通道2
    if (juice2 > 0) {
        Serial_Printf("Dispensing %dg juice from channel 2...\r\n", juice2);
        Motor_ExtendedCtrl(JUICE_PUMP_2, 100);
        uint32_t juiceTime = juice2 * 15;
        Delay_ms(juiceTime);
        Motor_ExtendedCtrl(JUICE_PUMP_2, 0);
    }
    
    // 4. 出果汁通道3
    if (juice3 > 0) {
        Serial_Printf("Dispensing %dg juice from channel 3...\r\n", juice3);
        Motor_ExtendedCtrl(JUICE_PUMP_3, 100);
        uint32_t juiceTime = juice3 * 15;
        Delay_ms(juiceTime);
        Motor_ExtendedCtrl(JUICE_PUMP_3, 0);
    }
    
    Serial_Printf("Recipe execution completed!\r\n");
}
```

### 3. 添加清洗函数

```c
void StartCleaning(void)
{
    Serial_Printf("Starting cleaning cycle...\r\n");
    
    // 启动所有泵进行清洗
    Motor_ExtendedCtrl(WATER_PUMP, 100);
    Motor_ExtendedCtrl(JUICE_PUMP_1, 100);
    Motor_ExtendedCtrl(JUICE_PUMP_2, 100);
    Motor_ExtendedCtrl(JUICE_PUMP_3, 100);
    
    // 清洗时间可以根据需要调整
    Delay_ms(5000); // 清洗5秒
    
    // 停止所有泵
    Motor_ExtendedCtrl(WATER_PUMP, 0);
    Motor_ExtendedCtrl(JUICE_PUMP_1, 0);
    Motor_ExtendedCtrl(JUICE_PUMP_2, 0);
    Motor_ExtendedCtrl(JUICE_PUMP_3, 0);
    
    Serial_Printf("Cleaning cycle completed!\r\n");
}

void StopCleaning(void)
{
    Serial_Printf("Stopping cleaning...\r\n");
    
    // 确保所有泵都停止
    Motor_ExtendedCtrl(WATER_PUMP, 0);
    Motor_ExtendedCtrl(JUICE_PUMP_1, 0);
    Motor_ExtendedCtrl(JUICE_PUMP_2, 0);
    Motor_ExtendedCtrl(JUICE_PUMP_3, 0);
    
    Serial_Printf("Cleaning stopped!\r\n");
}
```

### 4. 电机控制定义

需要在代码中定义电机控制的常量：

```c
// 电机控制定义（根据实际硬件连接调整）
#define WATER_PUMP    0  // 水泵
#define JUICE_PUMP_1  1  // 果汁泵1
#define JUICE_PUMP_2  2  // 果汁泵2  
#define JUICE_PUMP_3  3  // 果汁泵3
```

## 烧录建议

1. **必须重新烧录**：当前STM32代码无法正确处理Android发送的指令
2. **测试顺序**：
   - 先测试管理员指令（0x04称重、0x05去皮）
   - 再测试制作指令（0x01配方）
   - 最后测试清洗指令（0x02、0x03）
3. **调试建议**：
   - 保留所有Serial_Printf调试信息
   - 可以通过串口监视器查看指令接收情况
   - 逐步调整电机控制时间和强度

## 注意事项

- 电机控制参数需要根据实际硬件调整
- 时间计算需要根据实际出料速度校准
- 建议先在安全环境下测试，确保硬件不会损坏 