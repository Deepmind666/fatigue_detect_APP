#include "hx711.h"
#include "Motor.h"
#include "Delay.h"
#include "serial2.h"
#include "Gra_Com.h"

void BlockingGravityCompensation(uint8_t channel, uint32_t targetWeight)
{
    float currentWeight = 0;
    float gap = targetWeight;

    HX711_Tare();
    Serial2_Printf("Channel %d: Tare before compensation.\r\n", channel);

    Serial2_Printf("Channel %d: Starting compensation for %d g\r\n", channel, targetWeight);
    
    Motor_Control(channel, 1);
    Delay_ms(200);

    uint32_t last_print_time = 0;

    while (1) {
        currentWeight = HX711_GetWeight(3);
        gap = targetWeight - currentWeight;
        
        uint32_t current_time = Delay_GetSysTicks();
        if (current_time - last_print_time > 500) { // 每500ms打印一次
            Serial2_Printf("Channel %d -> Current: %.2fg, Target: %dg, Gap: %.2fg\r\n", channel, currentWeight, targetWeight, gap);
            last_print_time = current_time;
        }
        
        if (gap <= 1.0f) {
            Motor_Control(channel, 0);
            Serial2_Printf("Channel %d -> Compensation completed (final gap: %.2fg)\r\n", channel, gap);
            Delay_ms(1000);
            return;
        }
        
        Delay_ms(50);
    }
}

