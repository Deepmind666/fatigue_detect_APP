#include "stm32f10x.h"
#include "Serial.h"
#include "serial2.h"
#include "Delay.h"
#include "hx711.h"
#include "Motor.h"
#include "PWM.h"
#include "Gra_Com.h"

// 7字节协议处理函数
void ProcessCommand(void)
{
    if(Serial_GetRxFlag()) // 检查是否收到有效数据包
    {
        // 验证包头和包尾
        if(Serial_RxPacket[0] != 0xFF || Serial_RxPacket[6] != 0xFE) {
            Serial2_Printf("Invalid packet format\r\n");
            return;
        }
        
        uint8_t commandType = Serial_RxPacket[1]; // 指令类型
        
        switch(commandType) 
        {
            case 0x01: // 管理员指令
            {
                uint8_t adminCmd = Serial_RxPacket[2]; // 管理员功能码
                Serial2_Printf("Admin Command: 0x%02X\r\n", adminCmd);
                
                switch(adminCmd) {
                    case 0x00: // 一键清洗
                        Serial2_Printf("Executing: Clean System\r\n");
                        // TODO: 实现清洗逻辑
                        // Motor_Clean();
                        break;
                        
                    case 0x01: // 停止加水
                        Serial2_Printf("Executing: Stop Water\r\n");
                        // TODO: 实现停止加水逻辑
                        // Motor_StopWater();
                        break;
                        
                    case 0x02: // 连接测试
                        Serial2_Printf("Executing: Connection Test\r\n");
                        // 发送测试响应
                        Serial_Printf("TEST_OK\r\n");
                        break;
                        
                    case 0x04: // 测试制作
                        Serial2_Printf("Executing: Test Make\r\n");
                        // TODO: 实现测试制作逻辑
                        // Motor_TestMake();
                        break;
                        
                    default:
                        Serial2_Printf("Unknown admin command: 0x%02X\r\n", adminCmd);
                        break;
                }
                break;
            }
            
            case 0x02: // 用户制作指令 - 正常冰
            {
                uint8_t water = Serial_RxPacket[2];      // 水量
                uint8_t juice1 = Serial_RxPacket[3];     // 通道1果汁
                uint8_t juice2 = Serial_RxPacket[4];     // 通道2果汁  
                uint8_t juice3 = Serial_RxPacket[5];     // 通道3果汁
                
                Serial2_Printf("Making Juice (Normal Ice):\r\n");
                Serial2_Printf("Water: %dg, Ch1: %dg, Ch2: %dg, Ch3: %dg\r\n", 
                              water, juice1, juice2, juice3);
                
                // 制作流程
                MakeJuice(water, juice1, juice2, juice3, 1); // 1表示正常冰
                break;
            }
            
            case 0x03: // 用户制作指令 - 去冰
            {
                uint8_t water = Serial_RxPacket[2];      // 水量
                uint8_t juice1 = Serial_RxPacket[3];     // 通道1果汁
                uint8_t juice2 = Serial_RxPacket[4];     // 通道2果汁  
                uint8_t juice3 = Serial_RxPacket[5];     // 通道3果汁
                
                Serial2_Printf("Making Juice (No Ice):\r\n");
                Serial2_Printf("Water: %dg, Ch1: %dg, Ch2: %dg, Ch3: %dg\r\n", 
                              water, juice1, juice2, juice3);
                
                // 制作流程
                MakeJuice(water, juice1, juice2, juice3, 0); // 0表示去冰
                break;
            }
            
            default:
                Serial2_Printf("Unknown command type: 0x%02X\r\n", commandType);
                break;
        }
    }
}

// 制作果汁的具体实现
void MakeJuice(uint8_t water, uint8_t juice1, uint8_t juice2, uint8_t juice3, uint8_t withIce)
{
    Serial2_Printf("Starting juice making process...\r\n");
    
    // 1. 根据冰块选择调整水量
    if(withIce) {
        Serial2_Printf("Adding ice...\r\n");
        // TODO: 添加冰块逻辑
    }
    
    // 2. 加水
    if(water > 0) {
        Serial2_Printf("Adding water: %dg\r\n", water);
        // TODO: 控制水泵加水
        // Motor_AddWater(water);
    }
    
    // 3. 加果汁 - 通道1 (茉莉雪芽)
    if(juice1 > 0) {
        Serial2_Printf("Adding juice from channel 1: %dg\r\n", juice1);
        // TODO: 控制通道1果汁泵
        // Motor_AddJuice(1, juice1);
    }
    
    // 4. 加果汁 - 通道2 (柳橙百香)
    if(juice2 > 0) {
        Serial2_Printf("Adding juice from channel 2: %dg\r\n", juice2);
        // TODO: 控制通道2果汁泵
        // Motor_AddJuice(2, juice2);
    }
    
    // 5. 加果汁 - 通道3 (满杯桑葚)
    if(juice3 > 0) {
        Serial2_Printf("Adding juice from channel 3: %dg\r\n", juice3);
        // TODO: 控制通道3果汁泵
        // Motor_AddJuice(3, juice3);
    }
    
    // 6. 搅拌
    Serial2_Printf("Mixing...\r\n");
    // TODO: 控制搅拌器
    // Motor_Mix();
    
    Serial2_Printf("Juice making completed!\r\n");
    
    // 7. 发送完成信号给Android
    Serial_Printf("MAKE_COMPLETE\r\n");
}

int main(void)
{
    // 初始化系统
    SystemInit();
    Delay_Init();       // 初始化延时
    Serial_Init();      // 初始化串口1 (与Android通信)
    Serial2_Init();     // 初始化串口2 (调试输出)
    
    // 初始化硬件
    // HX711_Init();    // 初始化称重传感器
    // Motor_Init();    // 初始化电机控制
    // PWM_Init();      // 初始化PWM
    
    Serial2_Printf("Juice Machine STM32 System Ready\r\n");
    Serial2_Printf("Protocol: 7-byte format\r\n");
    Serial2_Printf("Channels: 1=茉莉雪芽, 2=柳橙百香, 3=满杯桑葚\r\n");
    
    // 主循环
    while(1)
    {
        ProcessCommand();  // 处理来自Android的指令
        Delay_ms(10);     // 10ms延时
    }
}

// 以下是需要实现的硬件控制函数示例

/*
// 电机控制函数示例
void Motor_AddWater(uint8_t grams) {
    // 根据重量计算运行时间
    uint16_t runtime = grams * 10; // 假设每克需要10ms
    
    // 启动水泵
    PWM_SetCompare1(100); // 全速
    Delay_ms(runtime);
    PWM_SetCompare1(0);   // 停止
}

void Motor_AddJuice(uint8_t channel, uint8_t grams) {
    uint16_t runtime = grams * 15; // 果汁比水粘稠，需要更长时间
    
    switch(channel) {
        case 1:
            PWM_SetCompare2(100);
            Delay_ms(runtime);
            PWM_SetCompare2(0);
            break;
        case 2:
            PWM_SetCompare3(100);
            Delay_ms(runtime);
            PWM_SetCompare3(0);
            break;
        case 3:
            PWM_SetCompare4(100);
            Delay_ms(runtime);
            PWM_SetCompare4(0);
            break;
    }
}

void Motor_Mix(void) {
    // 搅拌5秒
    for(int i = 0; i < 5; i++) {
        PWM_SetCompare5(100);
        Delay_ms(500);
        PWM_SetCompare5(0);
        Delay_ms(500);
    }
}

void Motor_Clean(void) {
    Serial2_Printf("Cleaning system...\r\n");
    // 清洗程序：依次运行所有泵进行清洗
    Motor_AddWater(50);   // 加清洗水
    Motor_Mix();          // 搅拌
    // 排水等操作...
}
*/ 