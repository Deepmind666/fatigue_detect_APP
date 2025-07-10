#include "stm32f10x.h"                  // Device header

void PWM_Init(void)
{
    /* 开启时钟 */
    RCC_APB1PeriphClockCmd(RCC_APB1Periph_TIM2, ENABLE);         // 开启TIM2时钟
    RCC_APB2PeriphClockCmd(RCC_APB2Periph_GPIOA, ENABLE);        // 开启GPIOA时钟
    
    /* GPIO初始化 - 两个引脚 */
    GPIO_InitTypeDef GPIO_InitStructure;
    GPIO_InitStructure.GPIO_Mode = GPIO_Mode_AF_PP;              // 复用推挽输出
    GPIO_InitStructure.GPIO_Speed = GPIO_Speed_50MHz;
    
    // 配置PA1 (TIM2_CH2)
    GPIO_InitStructure.GPIO_Pin = GPIO_Pin_1;
    GPIO_Init(GPIOA, &GPIO_InitStructure);
    
    // 配置PA2 (TIM2_CH3)
    GPIO_InitStructure.GPIO_Pin = GPIO_Pin_2;
    GPIO_Init(GPIOA, &GPIO_InitStructure);
    
    /* 时基单元初始化 */
    TIM_TimeBaseInitTypeDef TIM_TimeBaseInitStructure;
    TIM_TimeBaseInitStructure.TIM_ClockDivision = TIM_CKD_DIV1;     // 时钟分频
    TIM_TimeBaseInitStructure.TIM_CounterMode = TIM_CounterMode_Up; // 向上计数
    TIM_TimeBaseInitStructure.TIM_Period = 100 - 1;                 // ARR值 (100个计数周期)
    TIM_TimeBaseInitStructure.TIM_Prescaler = 3600 - 1;              // PSC值 (72MHz/360=200kHz)
    TIM_TimeBaseInitStructure.TIM_RepetitionCounter = 0;            // 重复计数器
    TIM_TimeBaseInit(TIM2, &TIM_TimeBaseInitStructure);
    
    /* 输出比较初始化 - 通道2 */
    TIM_OCInitTypeDef TIM_OCInitStructure;
    TIM_OCStructInit(&TIM_OCInitStructure);                         // 初始化默认值
    TIM_OCInitStructure.TIM_OCMode = TIM_OCMode_PWM1;               // PWM模式1
    TIM_OCInitStructure.TIM_OCPolarity = TIM_OCPolarity_High;       // 高电平有效
    TIM_OCInitStructure.TIM_OutputState = TIM_OutputState_Enable;   // 使能输出
    TIM_OCInitStructure.TIM_Pulse = 0;                              // 初始CCR值
    TIM_OC2Init(TIM2, &TIM_OCInitStructure);                        // 初始化通道2
    
    /* 输出比较初始化 - 通道3 */
    TIM_OCInitStructure.TIM_Pulse = 0;                              // 重置CCR初始值
    TIM_OC3Init(TIM2, &TIM_OCInitStructure);                        // 初始化通道3
    
    /* 使能TIM2 */
    TIM_Cmd(TIM2, ENABLE);
}

/* 设置通道2的CCR值 */
void PWM_SetCompare2(uint16_t Compare)
{
    TIM_SetCompare2(TIM2, Compare);   // 设置TIM2通道2的CCR
}

/* 设置通道3的CCR值 */
void PWM_SetCompare3(uint16_t Compare)
{
    TIM_SetCompare3(TIM2, Compare);   // 设置TIM2通道3的CCR
}
