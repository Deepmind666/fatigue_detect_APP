#include "hx711.h"       // 包含称重传感器HX711的驱动函数
#include "PWM.h" // 包含电机控制函数
#include "Delay.h"      // 包含时间相关的函数，如HAL_Delay
#include "serial.h"      // 包含串口输出函数

// 阻塞式重力补偿函数
// 参数：targetWeight - 目标重量（单位：克）
void BlockingGravityCompensation(uint32_t targetWeight)
{
    // 定义局部变量：
    float currentWeight = 0;   // 当前重量（浮点数，更精确）
    float gap = targetWeight;   // 当前重量与目标的差距（初始化为目标重量）


    // 输出补偿开始信息（通过串口）
    Serial_Printf("Starting blocking compensation for %d g\r\n", targetWeight);
    
    // 初次称重（初始化重量值）
    // HX711_GetWeight(10) - 调用称重传感器函数，参数10表示采样10次取平均
    currentWeight = HX711_GetWeight(10);
    
    // 计算初始差距
    gap = targetWeight - currentWeight;
    
    // 输出初始重量和差距
    Serial_Printf("Initial weight: %.2fg, Gap: %.2fg\r\n", currentWeight, gap);

    // 无限循环 - 补偿过程的核心
    while (1) {
       
		currentWeight = HX711_GetWeight(3);
        
        // 更新差距（目标 - 当前）
        gap = targetWeight - currentWeight;
        
        // 输出当前状态（重量和差距）
        Serial_Printf("Current: %.2fg, Gap: %.2fg\r\n", currentWeight, gap);
		 // 检查条件1：是否出现过冲（实际重量超过目标）
        // gap < 0 表示当前重量已经大于目标重量（补偿过度）
        if (gap < 0) {
            // 输出错误信息（过冲）
            Serial_Printf("Compensation failed: overfill (gap: %.2fg)\r\n", gap);
            
            // 停止电机（0表示停止，0表示占空比为0）
            PWM_SetCompare3(0);
            
            // 退出函数（整个补偿过程结束）
            // *****************************************
            // 这里是跳出while循环的第一个方式
            // 通过return语句直接退出函数
            // *****************************************
            return;
        }
        
        // 检查条件2：是否达到目标精度（差距小于5g）
        if (gap < 1.0f) {
            // 输出成功信息
            Serial_Printf("Compensation completed (final gap: %.2fg)\r\n", gap);
            
            // 停止电机
            PWM_SetCompare3(0);
            
            // 退出函数
            // *****************************************
            // 这里是跳出while循环的第二个方式
            // 也是通过return语句退出函数
            // *****************************************
            return;
        }
        
        // 根据当前差距选择合适的速度模式
        // 条件3：差距小于20g（进入低速模式）
        if (gap < 10.0f) {
            // 检查是否需要改变模式（避免重复设置相同的模式）
            
                
                // 控制电机（3表示特定电机，60%占空比）
                PWM_SetCompare3(30);
                
                // 输出模式切换信息
                Serial_Printf("Switching to LOW speed mode\r\n");

			}
		else if (gap < 20.0f) {
                PWM_SetCompare3(35);  // 90%占空比
                Serial_Printf("Switching to MEDIUM speed mode\r\n");
            }
		else if (gap < 40.0f) {
                PWM_SetCompare3(40);  // 90%占空比
                Serial_Printf("Switching to MEDIUM speed mode\r\n");
            }
        // 条件4：差距小于50g（进入中速模式）
        else if (gap < 80.0f) {
                PWM_SetCompare3(60);  // 90%占空比
                Serial_Printf("Switching to MEDIUM speed mode\r\n");
            }
        
        // 条件5：差距大于等于50g（高速模式）
        else {
                PWM_SetCompare3(100); // 80%占空比
                Serial_Printf("Switching to HIGH speed mode\r\n");
            }
        
        
        // 短暂延时（100毫秒）
        // 目的：让液体流动时间，避免过于频繁的称重
        Delay_ms(100);
//        
//        // 重新称重（参数5表示采样5次取平均，更快但精度稍低）
//        currentWeight = HX711_GetWeight(2);
//        
//        // 更新差距（目标 - 当前）
//        gap = targetWeight - currentWeight;
//        
//        // 输出当前状态（重量和差距）
//        Serial_Printf("Current: %.2fg, Gap: %.2fg\r\n", currentWeight, gap);
        
        // *****************************************
        // 循环将再次执行（while(1)保证无限循环）
        // 但后续通过判断条件中的return语句跳出循环
        // *****************************************
    }  // while循环结束
}  // 函数结束

