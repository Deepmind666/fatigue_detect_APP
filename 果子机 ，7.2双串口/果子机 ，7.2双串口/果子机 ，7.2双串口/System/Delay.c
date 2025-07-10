#include "Delay.h"
#include "stm32f10x.h"

// 定义系统滴答计数器（在SysTick中断中更新）
volatile uint32_t sys_tick_counter = 0;

/**
  * @brief  初始化系统滴答计数器
  * @param  无
  * @retval 无
  */
void Delay_Init(void)
{
    // 可选：如果已经配置过系统时钟，这步可以省略
    // SystemCoreClockUpdate();
    
    // 配置SysTick定时器为1ms中断
    if (SysTick_Config(SystemCoreClock / 1000)) 
    {
        // 捕获错误 - 系统核心时钟不正确
        while (1);
    }
    
    // 设置SysTick中断优先级（较低优先级）
    NVIC_SetPriority(SysTick_IRQn, (1 << __NVIC_PRIO_BITS) - 1);
}

/**
  * @brief  系统滴答中断服务函数
  * @note   需要在启动文件中声明为中断处理函数
  *         或在stm32f10x_it.c中重定义此函数
  *         使用weak声明避免冲突
  */
__attribute__((weak)) void SysTick_Handler(void)
{
    sys_tick_counter++; // 每毫秒增加滴答计数器
}

/**
  * @brief  获取当前系统滴答数
  * @param  无
  * @retval 当前系统滴答数（自启动以来的毫秒数）
  */
uint32_t Delay_GetSysTicks(void)
{
    return sys_tick_counter;
}

/**
  * @brief  基于系统滴答的非阻塞延时
  * @param  prev_tick 上次记录的系统滴答数
  * @param  wait_ms 需要等待的毫秒数
  * @retval 无
  */
void Delay_Until(uint32_t prev_tick, uint32_t wait_ms)
{
    uint32_t target = prev_tick + wait_ms;
    
    // 处理计数器溢出情况（约49天溢出一次）
    if (target < prev_tick) {
        // 溢出处理：等待计数器溢出然后继续
        while (Delay_GetSysTicks() > prev_tick);
        while (Delay_GetSysTicks() < target - prev_tick);
    } else {
        // 正常等待
        while (Delay_GetSysTicks() < target);
    }
}

/**
  * @brief  重置滴答计数器
  * @param  无
  * @retval 无
  */
void Delay_ResetTicks(void)
{
    sys_tick_counter = 0;
}

/**
  * @brief  微秒级延时
  * @param  xus 延时时长，范围：0~233015
  * @retval 无
  */
//void Delay_us(uint32_t xus)
//{
//    SysTick->LOAD = (SystemCoreClock / 8000000) * xus; // 72MHz主频下
//    SysTick->VAL = 0x00;                    // 清空当前计数值
//    SysTick->CTRL = 0x00000005;             // 设置时钟源为HCLK，启动定时器
//    while (!(SysTick->CTRL & 0x00010000)); // 等待计数到0
//    SysTick->CTRL = 0x00000004;             // 关闭定时器
//}



void Delay_us(uint32_t xus)
{
	SysTick->LOAD = 72 * xus;				//设置定时器重装值
	SysTick->VAL = 0x00;					//清空当前计数值
	SysTick->CTRL = 0x00000005;				//设置时钟源为HCLK，启动定时器
	while(!(SysTick->CTRL & 0x00010000));	//等待计数到0
	SysTick->CTRL = 0x00000004;				//关闭定时器
}






/**
  * @brief  毫秒级延时
  * @param  xms 延时时长，范围：0~4294967295
  * @retval 无
  */
void Delay_ms(uint32_t xms)
{
    while (xms--) 
    {
        Delay_us(1000);
    }
}

/**
  * @brief  秒级延时
  * @param  xs 延时时长，范围：0~4294967295
  * @retval 无
  */
void Delay_s(uint32_t xs)
{
    while (xs--)
    {
        Delay_ms(1000);
    }
}


