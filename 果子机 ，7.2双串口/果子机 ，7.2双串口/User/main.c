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
    
    // 注意：这里的电机通道号(0, 1, 2, 3)需要和实际硬件连接对应
    // 我们假设：0=水泵, 1=果汁泵1, 2=果汁泵2, 3=果汁泵3
    
    // 1. 出水
    if (water > 0) {
        Serial2_Printf("Dispensing %dg water...\r\n", water);
        Motor_ExtendedCtrl(0, 80); // 启动0号电机（水泵），速度80
        BlockingGravityCompensation(water); // 等待达到目标重量
        Motor_ExtendedCtrl(0, 0);   // 停止电机
    }
				
    // 2. 出果汁通道1
    if (juice1 > 0) {
        Serial2_Printf("Dispensing %dg juice from channel 1...\r\n", juice1);
        Motor_ExtendedCtrl(1, 80); // 启动1号电机
        BlockingGravityCompensation(juice1);
        Motor_ExtendedCtrl(1, 0);
    }
    
    // 3. 出果汁通道2
    if (juice2 > 0) {
        Serial2_Printf("Dispensing %dg juice from channel 2...\r\n", juice2);
        Motor_ExtendedCtrl(2, 80); // 启动2号电机
        BlockingGravityCompensation(juice2);
        Motor_ExtendedCtrl(2, 0);
    }
    
    // 4. 出果汁通道3
    if (juice3 > 0) {
        Serial2_Printf("Dispensing %dg juice from channel 3...\r\n", juice3);
        Motor_ExtendedCtrl(3, 80); // 启动3号电机
        BlockingGravityCompensation(juice3);
        Motor_ExtendedCtrl(3, 0);
    }
    
    Serial2_Printf("Recipe execution completed!\r\n");
}

/**
  * @brief  开始清洗
  */
void StartCleaning(void)
{
    Serial2_Printf("Starting cleaning cycle...\r\n");
    // 启动所有泵进行清洗，持续5秒
    Motor_ExtendedCtrl(0, 100);
    Motor_ExtendedCtrl(1, 100);
    Motor_ExtendedCtrl(2, 100);
    Motor_ExtendedCtrl(3, 100);
    Delay_ms(5000); // 清洗5秒
    StopCleaning(); // 调用停止函数
    Serial2_Printf("Cleaning cycle completed!\r\n");
}

/**
  * @brief  停止清洗
  */
void StopCleaning(void)
{
    Serial2_Printf("Stopping all motors...\r\n");
    // 确保所有泵都停止
    Motor_ExtendedCtrl(0, 0);
    Motor_ExtendedCtrl(1, 0);
    Motor_ExtendedCtrl(2, 0);
    Motor_ExtendedCtrl(3, 0);
}


int main(void)
	
{
    // ��ʼ��ϵͳ
    SystemInit();
    Delay_Init();       // ��ʼ����ʱ
    Serial_Init();      // ��ʼ������
	  Serial2_Init();
    Serial2_Printf("2.\r\n");
    Serial2_Printf("2.\r\n");
	  Serial_Printf("1\r\n");
    Serial_Printf("1\r\n");

    while(1)
    {
        
        ProcessCommand();
			

			
        Delay_ms(10);
    }
}







