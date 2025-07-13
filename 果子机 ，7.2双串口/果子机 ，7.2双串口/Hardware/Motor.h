#ifndef __MOTOR_H
#define __MOTOR_H

#include "stm32f10x.h"

void Motor_Init(void);
void Motor_Control(uint8_t channel, uint8_t on_off);

#endif
