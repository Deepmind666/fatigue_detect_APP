#ifndef __DS18B20_H
#define __DS18B20_H

#include "stm32f10x.h"

// ʹ��PC15����
#define DS18B20_PORT         GPIOC
#define DS18B20_PIN          GPIO_Pin_15
#define DS18B20_RCC          RCC_APB2Periph_GPIOC

// ��������
void DS18B20_Init(void);
float DS18B20_ReadTemp(void);
static void DS18B20_Reset(void);
static void DS18B20_WriteByte(uint8_t data);
static uint8_t DS18B20_ReadByte(void);
static void DS18B20_Delay_us(uint32_t us);

#endif /* __DS18B20_H */
