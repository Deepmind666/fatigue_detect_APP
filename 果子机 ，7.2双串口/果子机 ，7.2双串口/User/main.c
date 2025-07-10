#include "stm32f10x.h"
#include "Serial.h"
#include "serial2.h"
#include "Delay.h"
#include "hx711.h"
#include "Motor.h"
#include "PWM.h"
#include "Gra_Com.h"



// 串口指令处理函数
void ProcessCommand(void)
{
	float weight;
    if(Serial_GetRxFlag()) // 检查是否收到有效数据包
    {
        // 检查包头和包尾是否符合要求
        switch(Serial_RxPacket[0]) // 指令类型是接收数组的第一个字节
        {
            
                case 0x01: 
                {

									  Serial2_Printf("01");
									  uint8_t materialA = Serial_RxPacket[1];
                    uint8_t materialB = Serial_RxPacket[2];
                    uint8_t materialC = Serial_RxPacket[3];
                    uint8_t materialD = Serial_RxPacket[4];
							      Serial2_Printf("Recipe set: A=%dg, B=%dg, C=%dg, D=%dg\r\n", materialA, materialB, materialC, materialD);
									
                    break;
                }
                
                case 0x00: 
                    Serial2_Printf("02");
                    break;
                    
                case 0x03: 

                    break;
                    
                case 0x04: 

                    break;
				
                case 0x05: 
					

					break;
				
                default:
                    
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







