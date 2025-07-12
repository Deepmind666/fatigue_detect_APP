#include "hx711.h"       // �������ش�����HX711����������
#include "PWM.h" // ����������ƺ���
#include "Delay.h"      // ����ʱ����صĺ�������HAL_Delay
#include "serial.h"      // ���������������

// ����ʽ������������
// ������targetWeight - Ŀ����������λ���ˣ�
void BlockingGravityCompensation(uint32_t targetWeight)
{
    float currentWeight = 0;
    float gap = targetWeight;

    // 1. 开始前先去皮，清零当前重量
    HX711_Tare();
    Serial2_Printf("Tare before compensation.\r\n");

    Serial2_Printf("Starting blocking compensation for %d g\r\n", targetWeight);
    
    // 循环开始
    while (1) {
       
        // 2. 实时获取当前重量（相对于去皮后的0点）
        currentWeight = HX711_GetWeight(3);
        
        gap = targetWeight - currentWeight;
        
        Serial2_Printf("Current: %.2fg, Target: %dg, Gap: %.2fg\r\n", currentWeight, targetWeight, gap);
        
        // 3. 检查是否达到或超过目标
        if (gap <= 1.0f) { // 允许1g的误差
            PWM_SetCompare3(0); // 停止电机
            Serial2_Printf("Compensation completed (final gap: %.2fg)\r\n", gap);
            return; // 成功，退出函数
        }
        
        // 4. 根据差距调整速度 (PID简易控制)
        if (gap < 10.0f) {
            PWM_SetCompare3(30); // 低速
        }
        else if (gap < 40.0f) {
            PWM_SetCompare3(45); // 中速
        }
        else if (gap < 80.0f) {
            PWM_SetCompare3(60); // 较高速
        }
        else {
            PWM_SetCompare3(80); // 高速
        }
        
        Delay_ms(50); // 每次循环延迟50ms，防止过于频繁的读取和控制
    }
}

