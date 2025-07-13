#include "stm32f10x.h"
#include "Motor.h"

/**
  * @brief  初始化4个电机IO口
  */
void Motor_Init(void)
{
	RCC_APB2PeriphClockCmd(RCC_APB2Periph_GPIOA, ENABLE);
	
	GPIO_InitTypeDef GPIO_InitStructure;
	GPIO_InitStructure.GPIO_Mode = GPIO_Mode_Out_PP;
	GPIO_InitStructure.GPIO_Speed = GPIO_Speed_50MHz;
	
	// 初始化安全引脚 PA4, PA5, PA6, PA7
	GPIO_InitStructure.GPIO_Pin = GPIO_Pin_4 | GPIO_Pin_5 | GPIO_Pin_6 | GPIO_Pin_7;
	GPIO_Init(GPIOA, &GPIO_InitStructure);
	
	// 启动时关闭所有电机
	Motor_Control(1, 0);
	Motor_Control(2, 0);
	Motor_Control(3, 0);
	Motor_Control(4, 0);
}

/**
  * @brief  控制单个电机启停
  * @param  channel: 电机通道 (1-4)
  * @param  on_off: 1=开启, 0=停止
  */
void Motor_Control(uint8_t channel, uint8_t on_off)
{
	uint16_t pin;
	switch(channel)
	{
		case 1: pin = GPIO_Pin_4; break; // Motor 1 -> PA4
		case 2: pin = GPIO_Pin_5; break; // Motor 2 -> PA5
		case 3: pin = GPIO_Pin_6; break; // Motor 3 -> PA6
		case 4: pin = GPIO_Pin_7; break; // Motor 4 -> PA7
		default: return;
	}
	
	if (on_off)
	{
		GPIO_SetBits(GPIOA, pin);
	}
	else
	{
		GPIO_ResetBits(GPIOA, pin);
	}
}

