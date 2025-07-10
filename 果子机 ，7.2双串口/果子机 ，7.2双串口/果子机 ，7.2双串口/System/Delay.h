#ifndef __DELAY_H
#define __DELAY_H

#include "stm32f10x.h"

#ifdef __cplusplus
 extern "C" {
#endif

// 系统滴答计数器变量声明
extern volatile uint32_t sys_tick_counter;

// 初始化函数
void Delay_Init(void);

// 延时函数
void Delay_us(uint32_t xus);   // 微秒延时
void Delay_ms(uint32_t xms);   // 毫秒延时
void Delay_s(uint32_t xs);     // 秒延时

// 系统滴答功能
uint32_t Delay_GetSysTicks(void);      // 获取系统滴答数
void Delay_Until(uint32_t prev_tick, uint32_t wait_ms); // 非阻塞延时
void Delay_ResetTicks(void);           // 重置滴答计数器

// 精确延时宏(无中断需求)
#define DELAY_US(us)    Delay_us(us)
#define DELAY_MS(ms)    Delay_ms(ms)

#ifdef __cplusplus
}
#endif

#endif


