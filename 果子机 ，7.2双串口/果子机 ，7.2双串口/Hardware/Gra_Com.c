#include "stm32f10x.h"
#include "hx711.h"
#include "Motor.h"
#include "Delay.h"
#include "serial2.h"
#include "Gra_Com.h"
#include <float.h>   // 引入float.h以使用FLT_MAX

// 定义一个大致的流速，用于模拟模式，单位：克/秒
#define SIMULATED_FLOW_RATE 30.0f 

void BlockingGravityCompensation(uint8_t channel, uint32_t targetWeight)
{
    float initialWeight = HX711_GetWeight(5); // 尝试获取初始重量

    // 检查传感器是否连接
    if (initialWeight == FLT_MAX) {
        // 传感器未连接，执行基于时间的延时补偿 (用于无硬件调试)
        
        // 根据目标重量和估算的流速计算所需时间
        uint32_t delay_ms = (uint32_t)((targetWeight / SIMULATED_FLOW_RATE) * 1000.0f);
        
        Motor_Control(channel, 1); // 打开电机
        
        // 在延时期间持续喂狗
        uint32_t elapsed_time = 0;
        while(elapsed_time < delay_ms) {
            IWDG_ReloadCounter();
            Delay_ms(50);
            elapsed_time += 50;
        }

        Motor_Control(channel, 0); // 关闭电机
        
        // 根据通道号打印更清晰的日志
        switch(channel) {
            case 1: Serial2_Printf("Water: finished\r\n"); break;
            case 2: Serial2_Printf("Juice1: finished\r\n"); break;
            case 3: Serial2_Printf("Juice2: finished\r\n"); break;
            case 4: Serial2_Printf("Juice3: finished\r\n"); break;
        }
        return;

    } else {
        // 传感器已连接，执行标准的重量补偿
        float currentWeight = initialWeight;
        float targetTotalWeight = initialWeight + targetWeight;
        float gap;

        Motor_Control(channel, 1);
        Delay_ms(200);

        while (1) {
            IWDG_ReloadCounter();
            currentWeight = HX711_GetWeight(3);
            
            // 如果在制作过程中传感器也断开了，则直接停止
            if (currentWeight == FLT_MAX) {
                 Motor_Control(channel, 0);
                 Serial2_Printf("Error: Sensor disconnected during operation on Ch:%d!\r\n", channel);
                 return;
            }

            gap = targetTotalWeight - currentWeight;
            
            if (gap <= 1.5f) { // 稍微放宽容差
                Motor_Control(channel, 0);
                IWDG_ReloadCounter();
                Delay_ms(1000);

                // 根据通道号打印更清晰的日志
                switch(channel) {
                    case 1: Serial2_Printf("Water: finished\r\n"); break;
                    case 2: Serial2_Printf("Juice1: finished\r\n"); break;
                    case 3: Serial2_Printf("Juice2: finished\r\n"); break;
                    case 4: Serial2_Printf("Juice3: finished\r\n"); break;
                }
                return;
            }
            
            Delay_ms(50);
        }
    }
}

