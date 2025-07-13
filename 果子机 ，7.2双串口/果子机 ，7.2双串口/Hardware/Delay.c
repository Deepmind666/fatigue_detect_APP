#include "stm32f10x.h"
#include "Delay.h"

static __IO uint32_t SysTicks;
static __IO uint32_t TimingDelay;

void Delay_Init(void)
{
	if (SysTick_Config(SystemCoreClock / 1000000)) // 1us tick
	{ 
		while (1);
	}
	SysTicks = 0;
}

void Delay_us(uint32_t us)
{
	TimingDelay = us;
	while(TimingDelay != 0);
}

void Delay_ms(uint32_t ms)
{
	while(ms--)
	{
		Delay_us(1000);
	}
}

uint32_t Delay_GetSysTicks(void) {
    return SysTicks;
}

void Delay_ClearSysTicks(void) {
	SysTicks = 0;
}

void SysTick_Handler(void)
{
	SysTicks++;
	if (TimingDelay != 0x00)
	{ 
		TimingDelay--;
	}
} 