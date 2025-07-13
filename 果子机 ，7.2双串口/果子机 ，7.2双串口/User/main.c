#include "stm32f10x.h"
#include "Serial.h"
#include "serial2.h"
#include "Delay.h"
#include "hx711.h"
#include "Motor.h"
#include "PWM.h"
#include "Gra_Com.h"

// 函数声明
void ExecuteRecipe(uint8_t water, uint8_t juice1, uint8_t juice2, uint8_t juice3);
void StartCleaning(void);
void StopCleaning(void);


// 完整指令处理函数
void ProcessCommand(void)
{
	float weight;
    if(Serial_GetRxFlag())
    {
        uint8_t cmd_type = Serial_RxPacket[0];
        uint8_t materialA = Serial_RxPacket[1];
        uint8_t materialB = Serial_RxPacket[2];
        uint8_t materialC = Serial_RxPacket[3];
        uint8_t materialD = Serial_RxPacket[4];
        
        switch(cmd_type)
        {
            case 0x01: // 正常冰
            case 0x02: // 去冰
            {
                Serial2_Printf("%02X Recipe received: Water=%dg, Ch1=%dg, Ch2=%dg, Ch3=%dg\r\n", 
                             cmd_type, materialA, materialB, materialC, materialD);
                ExecuteRecipe(materialA, materialB, materialC, materialD);
                break;
            }
            
            case 0x03: // 管理员清洗
                Serial2_Printf("03 Admin cleaning started\r\n");
                StartCleaning();
                break;
                
            case 0x04: // 管理员停止
                Serial2_Printf("04 Admin action stopped\r\n");
                StopCleaning();
                break;
                
            case 0x05: // 管理员去皮
                HX711_Tare();
                Serial2_Printf("05 Tare completed. Offset: %.2f\r\n", HX711_GetOffset());
                break;
            
            case 0x06: // 管理员称重
                weight = HX711_GetWeight(10);
                Serial2_Printf("06 Weight: %d g\r\n", (uint32_t)weight);
                break;
            
            default:
                Serial2_Printf("Unknown command type: 0x%02X\r\n", cmd_type);
                break;
        }
    }
}


// 新增的硬件控制函数

/**
  * @brief  执行配方制作
  * @param  water: 水量 (g)
  * @param  juice1: 通道1果汁量 (g)
  * @param  juice2: 通道2果汁量 (g)
  * @param  juice3: 通道3果汁量 (g)
  * @retval 无
  */
void ExecuteRecipe(uint8_t water, uint8_t juice1, uint8_t juice2, uint8_t juice3)
{
    Serial2_Printf("Starting recipe execution...\r\n");
    
    // 假设：1=水泵, 2=果汁泵1, 3=果汁泵2, 4=果汁泵3
    
    if (water > 0) {
        Serial2_Printf("Dispensing %dg water from channel 1...\r\n", water);
        BlockingGravityCompensation(1, water);
    }
				
    if (juice1 > 0) {
        Serial2_Printf("Dispensing %dg juice from channel 2...\r\n", juice1);
        BlockingGravityCompensation(2, juice1);
    }
    
    if (juice2 > 0) {
        Serial2_Printf("Dispensing %dg juice from channel 3...\r\n", juice2);
        BlockingGravityCompensation(3, juice2);
    }
    
    if (juice3 > 0) {
        Serial2_Printf("Dispensing %dg juice from channel 4...\r\n", juice3);
        BlockingGravityCompensation(4, juice3);
    }
    
    Serial2_Printf("Recipe execution completed!\r\n");
}

/**
  * @brief  开始清洗
  */
void StartCleaning(void)
{
    Serial2_Printf("Starting cleaning cycle...\r\n");
    Motor_Control(1, 1);
    Motor_Control(2, 1);
    Motor_Control(3, 1);
    Motor_Control(4, 1);
}

/**
  * @brief  停止所有活动（包括清洗和制作）
  */
void StopCleaning(void)
{
    Serial2_Printf("Stopping all motors...\r\n");
    Motor_Control(1, 0);
    Motor_Control(2, 0);
    Motor_Control(3, 0);
    Motor_Control(4, 0);
}


int main(void)
	
{
    // ʼϵͳ
    SystemInit();
    Delay_Init();       // ʼʱ
    Serial_Init();      // ʼ1 ()
	Serial2_Init();     // ʼ2 (־)
	HX711_Init();       // ʼ HX711
	Motor_Init();       // ʼ IO

    Serial2_Printf("System Initialized.\r\n");
    Serial_Printf("System Initialized.\r\n");

    while(1)
    {
        
        ProcessCommand();
			

			
        Delay_ms(10);
    }
}







