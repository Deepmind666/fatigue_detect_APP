# STM32果汁机开发指南

## 📡 通信协议详解

### 7字节协议格式
```
[FF] [类型] [数据1] [数据2] [数据3] [数据4] [FE]
```

### 协议对照表

#### 1. 制作饮品指令

| 饮品 | 冰块 | 完整指令 | 解析说明 |
|------|------|----------|----------|
| 茉莉雪芽 | 正常冰 | `FF 02 69 AF 00 00 FE` | 水105g(0x69), 通道1果汁175g(0xAF) |
| 茉莉雪芽 | 去冰 | `FF 03 69 AF 00 00 FE` | 水105g(0x69), 通道1果汁175g(0xAF) |
| 柳橙百香 | 正常冰 | `FF 02 B4 00 64 00 FE` | 水180g(0xB4), 通道2果汁100g(0x64) |
| 柳橙百香 | 去冰 | `FF 03 B4 00 64 00 FE` | 水180g(0xB4), 通道2果汁100g(0x64) |
| 满杯桑葚 | 正常冰 | `FF 02 82 00 00 96 FE` | 水130g(0x82), 通道3果汁150g(0x96) |
| 满杯桑葚 | 去冰 | `FF 03 82 00 00 96 FE` | 水130g(0x82), 通道3果汁150g(0x96) |

#### 2. 管理员指令

| 功能 | 完整指令 | 解析说明 |
|------|----------|----------|
| 一键清洗 | `FF 01 00 00 00 00 FE` | 类型0x01, 功能码0x00 |
| 停止加水 | `FF 01 01 00 00 00 FE` | 类型0x01, 功能码0x01 |
| 连接测试 | `FF 01 02 00 00 00 FE` | 类型0x01, 功能码0x02 |
| 测试制作 | `FF 01 04 00 00 00 FE` | 类型0x01, 功能码0x04 |

### 字节详解

```
Byte 0: 0xFF - 包头标识
Byte 1: 指令类型
  - 0x01: 管理员指令
  - 0x02: 用户制作指令(正常冰)
  - 0x03: 用户制作指令(去冰)
Byte 2: 数据1
  - 管理员指令: 功能码
  - 制作指令: 水量(克)
Byte 3: 数据2 - 通道1果汁量(克)
Byte 4: 数据3 - 通道2果汁量(克)  
Byte 5: 数据4 - 通道3果汁量(克)
Byte 6: 0xFE - 包尾标识
```

## 🔧 STM32代码实现

### 1. 主要修改点

#### 原代码问题
```c
// 错误的处理方式
switch(Serial_RxPacket[0]) // 只处理第一个字节
{
    case 0x01: // 误将管理员指令当作制作指令
    {
        uint8_t materialA = Serial_RxPacket[1]; // 错误的数据解析
        // ...
    }
}
```

#### 正确的处理方式
```c
// 正确的7字节协议处理
void ProcessCommand(void)
{
    if(Serial_GetRxFlag()) 
    {
        // 验证包头和包尾
        if(Serial_RxPacket[0] != 0xFF || Serial_RxPacket[6] != 0xFE) {
            return;
        }
        
        uint8_t commandType = Serial_RxPacket[1]; // 指令类型
        
        switch(commandType) 
        {
            case 0x01: // 管理员指令
            {
                uint8_t adminCmd = Serial_RxPacket[2];
                // 处理管理员功能
                break;
            }
            case 0x02: // 正常冰制作
            case 0x03: // 去冰制作
            {
                uint8_t water = Serial_RxPacket[2];
                uint8_t juice1 = Serial_RxPacket[3];
                uint8_t juice2 = Serial_RxPacket[4];
                uint8_t juice3 = Serial_RxPacket[5];
                // 制作果汁
                break;
            }
        }
    }
}
```

### 2. 核心函数实现

#### 制作果汁函数
```c
void MakeJuice(uint8_t water, uint8_t juice1, uint8_t juice2, uint8_t juice3, uint8_t withIce)
{
    // 1. 冰块处理
    if(withIce) {
        // 添加冰块逻辑
    }
    
    // 2. 按顺序添加材料
    if(water > 0) Motor_AddWater(water);
    if(juice1 > 0) Motor_AddJuice(1, juice1);
    if(juice2 > 0) Motor_AddJuice(2, juice2);
    if(juice3 > 0) Motor_AddJuice(3, juice3);
    
    // 3. 搅拌
    Motor_Mix();
    
    // 4. 发送完成信号
    Serial_Printf("MAKE_COMPLETE\r\n");
}
```

#### 硬件控制函数
```c
// 水泵控制
void Motor_AddWater(uint8_t grams) {
    uint16_t runtime = grams * 10; // 每克10ms
    PWM_SetCompare1(100);
    Delay_ms(runtime);
    PWM_SetCompare1(0);
}

// 果汁泵控制
void Motor_AddJuice(uint8_t channel, uint8_t grams) {
    uint16_t runtime = grams * 15; // 果汁更粘稠
    
    switch(channel) {
        case 1: // 茉莉雪芽
            PWM_SetCompare2(100);
            Delay_ms(runtime);
            PWM_SetCompare2(0);
            break;
        case 2: // 柳橙百香
            PWM_SetCompare3(100);
            Delay_ms(runtime);
            PWM_SetCompare3(0);
            break;
        case 3: // 满杯桑葚
            PWM_SetCompare4(100);
            Delay_ms(runtime);
            PWM_SetCompare4(0);
            break;
    }
}
```

### 3. 串口配置

#### 串口1 (与Android通信)
- 波特率: 9600
- 数据位: 8
- 停止位: 1
- 校验位: 无

#### 串口2 (调试输出)
- 用于输出调试信息
- 可连接USB转TTL查看日志

## 🏗️ 硬件架构

### 通道分配
```
通道1: 茉莉雪芽 (PWM_SetCompare2)
通道2: 柳橙百香 (PWM_SetCompare3)
通道3: 满杯桑葚 (PWM_SetCompare4)
水泵:   主水源   (PWM_SetCompare1)
搅拌:   搅拌器   (PWM_SetCompare5)
```

### 传感器接口
```
HX711: 称重传感器 (用于精确计量)
温度传感器: 水温监控
液位传感器: 原料液位检测
```

## 🔄 制作流程

### 标准制作流程
1. **接收指令** - 解析7字节协议
2. **参数验证** - 检查数据有效性
3. **硬件准备** - 检查传感器状态
4. **开始制作**:
   - 添加冰块(如需要)
   - 加水到指定重量
   - 按通道添加果汁
   - 搅拌混合
5. **完成确认** - 发送完成信号
6. **清理准备** - 为下次制作做准备

### 错误处理
```c
// 错误码定义
#define ERROR_INVALID_PACKET    0x01
#define ERROR_SENSOR_FAULT      0x02
#define ERROR_MATERIAL_EMPTY    0x03
#define ERROR_HARDWARE_FAULT    0x04

// 错误报告
void ReportError(uint8_t errorCode) {
    Serial_Printf("ERROR:%02X\r\n", errorCode);
}
```

## 📊 调试和测试

### 调试输出示例
```
Juice Machine STM32 System Ready
Protocol: 7-byte format
Channels: 1=茉莉雪芽, 2=柳橙百香, 3=满杯桑葚

Received: FF 02 69 AF 00 00 FE
Making Juice (Normal Ice):
Water: 105g, Ch1: 175g, Ch2: 0g, Ch3: 0g
Adding water: 105g
Adding juice from channel 1: 175g
Mixing...
Juice making completed!
```

### 测试指令
```c
// 可在main函数中添加测试代码
void TestProtocol(void) {
    // 模拟接收茉莉雪芽制作指令
    Serial_RxPacket[0] = 0xFF;
    Serial_RxPacket[1] = 0x02;
    Serial_RxPacket[2] = 105;  // 水量
    Serial_RxPacket[3] = 175;  // 通道1果汁
    Serial_RxPacket[4] = 0;    // 通道2果汁
    Serial_RxPacket[5] = 0;    // 通道3果汁
    Serial_RxPacket[6] = 0xFE;
    
    ProcessCommand();
}
```

## 🚀 部署步骤

1. **替换main.c** - 使用提供的修复代码
2. **配置硬件** - 确保PWM和传感器正确连接
3. **编译下载** - 使用Keil编译并下载到STM32
4. **串口测试** - 连接串口2查看调试输出
5. **协议测试** - 与Android端进行通信测试
6. **硬件调试** - 调整电机控制参数
7. **完整测试** - 进行端到端功能测试

## 📝 注意事项

1. **数据范围**: 重量参数限制在0-255g范围内
2. **时序控制**: 电机控制需要合适的延时
3. **错误处理**: 必须处理各种异常情况
4. **安全保护**: 添加硬件保护逻辑
5. **调试输出**: 保持详细的日志输出

---

*STM32代码开发指南 v1.0*
*配合Android端"果然新鲜"果汁机系统* 