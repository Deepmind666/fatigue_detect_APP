#include "stm32f10x.h"
#include "Serial.h"
#include "Delay.h"
#include "hx711.h"
#include "Motor.h"
#include "PWM.h"
#include "Gra_Com.h"

// 全局变量
float calibration_weight = 0.0f; // 存储校准重量

//// 串口指令处理函数
//void ProcessCommand(void)
//{
//    if(Serial_GetRxFlag()) // 检查是否收到有效数据包
//    {
//        switch(Serial_RxPacket[0]) // 解析指令类型
//        {
//            case 'T': // 去皮指令FF 54 00 00 00 FE
//                HX711_Tare();
//                Serial_Printf("Tare completed.\r\n");
//                Serial_Printf("Offset: %.2f\r\n", HX711_GetOffset());
//                break;
//                
//            case 'C': // 校准指令发送：FF 43 00 00 64 FE 100g   FF 43 00 03 E8 FE → 表示使用1000克校准
////                // 解析校准重量（单位g）
//                calibration_weight = 
//                (Serial_RxPacket[1] << 16) | 
//                (Serial_RxPacket[2] << 8) | 
//                Serial_RxPacket[3];
//                    
//                // 执行校准
//                if(calibration_weight > 0.1f) 
//				{
//                    Serial_Printf("Calibrating with %.2fg...\r\n", calibration_weight);
//                    HX711_UpdateCalibration(calibration_weight);
//                    Serial_Printf("Calibration completed. Scale: %.6f\r\n", HX711_GetScale());
//                } 
//				else {
//                    Serial_Printf("Invalid weight: %.2f\r\n", calibration_weight);
//                }
////				Serial_Printf("begin\r\n");//测试电机
////				Motor_ExtendedCtrl(3, 100);
////			Delay_ms(5000);
////			Motor_ExtendedCtrl(0, 100);
//				Serial_Printf("end\r\n");
//                break;
//                
//            case 'W': // 称重指令FF 57 00 00 00 FE
//            {
//                // 使用储存的参数进行称重
//				
//				
//                float weight = HX711_GetWeight(10); // 10次采样平均
//                
//                // 显示当前使用的参数
//                Serial_Printf("Using saved parameters:\r\n");
//                Serial_Printf("Offset: %.2f\r\n", HX711_GetOffset());
//                Serial_Printf("Scale: %.6f\r\n", HX711_GetScale());
//                Serial_Printf("Weight: %d g\r\n", (uint32_t)weight);
//                break;
//            }
//            case 'H': // 帮助指令FF 48 00 00 00 FE
//                Serial_Printf("HX711 Controller Commands:\r\n");
//                Serial_Printf("T - Tare (set zero)\r\n");
//                Serial_Printf("Cxxxx - Calibrate with weight xxxx (in grams)\r\n");
//                Serial_Printf("W - Get current weight using saved parameters\r\n");
//                Serial_Printf("H - Show this help\r\n");
//                break;
//                
//            default:
//                Serial_Printf("Unknown command: %c\r\n", Serial_RxPacket[0]);
//                break;
//        }
//    }
//}

// 串口指令处理函数
void ProcessCommand(void)
{
	float weight;
    if(Serial_GetRxFlag()) // 检查是否收到有效数据包
    {
        // 检查包头和包尾是否符合要求
        switch(Serial_RxPacket[0]) // 指令类型是接收数组的第一个字节
        {
            
                case 0x01: // 用户设置配方
                {
                    uint8_t materialA = Serial_RxPacket[1];
                    uint8_t materialB = Serial_RxPacket[2];
                    uint8_t materialC = Serial_RxPacket[3];
                    uint8_t materialD = Serial_RxPacket[4];
					          BlockingGravityCompensation(materialA);
                    
                    // 此处添加配方设置逻辑
                    Serial_Printf("Recipe set: A=%dg, B=%dg, C=%dg, D=%dg\r\n", materialA, materialB, materialC, materialD);
                    break;
                }
                
                case 0x02: // 管理员清洗开始
                    // 此处添加清洗开始逻辑
                    Serial_Printf("Admin cleaning started\r\n");
                    break;
                    
                case 0x03: // 管理员清洗结束
                    // 此处添加清洗结束逻辑
                    Serial_Printf("Admin cleaning finished\r\n");
                    break;
                    
                case 0x04: // 管理员去皮指令
                    HX711_Tare();
                    Serial_Printf("Tare completed. Offset: %.2f\r\n", HX711_GetOffset());
                    break;
				
                case 0x05: //管理员称重
					
					weight = HX711_GetWeight(10); // 10次采样平均
					Serial_Printf("Weight: %d g\r\n", (uint32_t)weight);
					break;
				
                default:
                    Serial_Printf("Unknown command type: 0x%02X\r\n", Serial_RxPacket[1]);
                    break;
            }
        }
   
}


int main(void)
	
{
    // 初始化系统
    SystemInit();
    Delay_Init();       // 初始化延时
    Serial_Init();      // 初始化串口
    HX711_Init();       // 初始化HX711
    Motor_Init();
	
    Serial_Printf("HX711 Weighing System Ready.\r\n");
    Serial_Printf("Type 'H' for commands list.\r\n");
//    float weight = HX711_GetWeight(10);
    // 主循环
    while(1)
    {
        // 处理串口指令
        ProcessCommand();
			

			
			Motor_ExtendedCtrl(2,100);
			Delay_ms(2000);
			Motor_ExtendedCtrl(2,60);
			Delay_ms(2000);
			Motor_ExtendedCtrl(0,100);
			Delay_ms(500);
			Motor_ExtendedCtrl(3,100);
			Delay_ms(2000);
			Motor_ExtendedCtrl(3,60);
			Delay_ms(2000);
			Motor_ExtendedCtrl(0,100);
			Delay_ms(500);
//			PWM_SetCompare2(0);
//			PWM_SetCompare3(100);
//			Delay_ms(2000);
//			PWM_SetCompare2(0);
//			PWM_SetCompare3(85);
//			Delay_ms(2000);
//			PWM_SetCompare2(100);
//			PWM_SetCompare3(100);
//			Delay_ms(500);
//			PWM_SetCompare2(100);
//			PWM_SetCompare3(0);			
//			Delay_ms(2000);						
//			PWM_SetCompare2(80);
//			PWM_SetCompare3(0);
//			Delay_ms(2000);	
//			PWM_SetCompare2(100);
//			PWM_SetCompare3(100);
//			Delay_ms(500);
			
//			PWM_SetCompare2(0);
//			PWM_SetCompare3(65);
//			Delay_ms(2000);
//			PWM_SetCompare2(80);
//			PWM_SetCompare3(0);			
//			Delay_ms(5000);						
//			PWM_SetCompare2(60);
//			PWM_SetCompare3(0);			
//			Delay_ms(5000);					
//			PWM_SetCompare2(40);
//			PWM_SetCompare3(0);			
//			Delay_ms(5000);	
//			PWM_SetCompare2(100);
//			PWM_SetCompare3(100);
//			
			
//    lspeed_sui();
//		Delay_ms(2000);
//		mspeed_sui();
//		Delay_ms(2000);
//		hspeed_sui();
//		Delay_ms(2000);		
//		PWM_SetCompare3(100);
//		Delay_ms(2000);
//		PWM_SetCompare3(0);
//		Delay_ms(2000);
//		int i;
//		
//		for (i=30;i<80;i++){
//		PWM_SetCompare3(i);
//			Delay_ms(100);
//			
//			if(i==70){ Delay_ms(2000);}
//		}
		
		
        // 短延时减少CPU占用
        Delay_ms(10);
    }
}




//int main(void)
//{
//    // 系统初始化
//    SystemInit();
//    
//    // 初始化延时函数
//    Delay_Init();
//    
//    // 初始化电机
//    Motor_Init();
//	float weight = HX711_GetWeight(10); // 10次采样平均
//	HX711_Tare();
//	Serial_Printf("Tare completed.\r\n");
//	Serial_Printf("Offset: %.2f\r\n", HX711_GetOffset());
//    chou_5();	//抽5ml
//	stop();		//刹车
//	weight = HX711_GetWeight(10); // 10次采样平均
//                
//                // 显示当前使用的参数

//	Serial_Printf("Weight: %d g\r\n", (uint32_t)weight);
//    // 主循环
//    while(1)
//    {/*60>8v
//		65>10v
//		75>14v
//		85>16v
//		90>17.5v
//	*/
//		
//    }
//}


