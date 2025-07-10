 #include "stm32f10x.h"                  // Device header
#include "PWM.h"
#include "Delay.h"
#include "Motor.h"

/**
  * 函    数：直流电机初始化
  * 参    数：无
  * 返 回 值：无
  */
void Motor_Init(void)
{
	/*开启时钟*/
	RCC_APB2PeriphClockCmd(RCC_APB2Periph_GPIOA, ENABLE);		//开启GPIOA的时钟
	
	GPIO_InitTypeDef GPIO_InitStructure;
	GPIO_InitStructure.GPIO_Mode = GPIO_Mode_Out_PP;
	GPIO_InitStructure.GPIO_Pin = GPIO_Pin_4 | GPIO_Pin_5;
	GPIO_InitStructure.GPIO_Speed = GPIO_Speed_50MHz;
	GPIO_Init(GPIOA, &GPIO_InitStructure);						//将PA4和PA5引脚初始化为推挽输出	
	
	PWM_Init();													//初始化直流电机的底层PWM
}

/**
  * 函    数：直流电机设置速度
  * 参    数：Speed 要设置的速度，范围：-100~100
  * 返 回 值：无
  */
void Motor_SetSpeed(int8_t Speed)
{
	if (Speed >= 0)							//如果设置正转的速度值
	{
		GPIO_SetBits(GPIOA, GPIO_Pin_4);	//PA4置高电平
		GPIO_ResetBits(GPIOA, GPIO_Pin_5);	//PA5置低电平，设置方向为正转
		PWM_SetCompare3(Speed);				//PWM设置为速度值
	}
	else									//否则，即设置反转的速度值
	{
		GPIO_ResetBits(GPIOA, GPIO_Pin_4);	//PA4置低电平
		GPIO_SetBits(GPIOA, GPIO_Pin_5);	//PA5置高电平，设置方向为反转
		PWM_SetCompare3(-Speed);			//PWM设置为负的速度值，因为此时速度值为负数，而PWM只能给正数
	}
}


/**
  * @brief  扩展电机控制函数 (实现表二所有控制逻辑)
  * @param  state 控制状态
  * @param  speed 调速值 (0-100)，仅对调速状态有效
  * @retval 无
  */
void Motor_ExtendedCtrl(uint8_t state, uint8_t speed)
{
    switch(state) {
        case 0: // 刹车 (IN3=0, IN4=0)
            GPIO_ResetBits(GPIOA, GPIO_Pin_4);
            GPIO_ResetBits(GPIOA, GPIO_Pin_5);
            PWM_SetCompare3(100); // 100%占空比增强制动力
            break;
            
        case 1: // 悬空/自由旋转 (IN3=1, IN4=1)
            GPIO_SetBits(GPIOA, GPIO_Pin_4);
            GPIO_SetBits(GPIOA, GPIO_Pin_5);
            PWM_SetCompare3(0); // 0%占空比
            break;
            
        case 2: // 正转调速 (IN3=1, IN4=0, PWM调速)
            GPIO_SetBits(GPIOA, GPIO_Pin_4);
            GPIO_ResetBits(GPIOA, GPIO_Pin_5);
            PWM_SetCompare3(speed);
            break;
            
        case 3: // 反转调速 (IN3=0, IN4=1, PWM调速)
            GPIO_ResetBits(GPIOA, GPIO_Pin_4);
            GPIO_SetBits(GPIOA, GPIO_Pin_5);
            PWM_SetCompare3(speed);
            break;
            
        case 4: // 全速正转 (IN3=1, IN4=0, ENA2=100%)
            GPIO_SetBits(GPIOA, GPIO_Pin_4);
            GPIO_ResetBits(GPIOA, GPIO_Pin_5);
            PWM_SetCompare3(100); // 100%占空比等效高电平
            break;
            
        case 5: // 全速反转 (IN3=0, IN4=1, ENA2=100%)
            GPIO_ResetBits(GPIOA, GPIO_Pin_4);
            GPIO_SetBits(GPIOA, GPIO_Pin_5);
            PWM_SetCompare3(100); // 100%占空比等效高电平
            break;
            
        default: // 安全状态设为悬空
            GPIO_SetBits(GPIOA, GPIO_Pin_4);
            GPIO_SetBits(GPIOA, GPIO_Pin_5);
            PWM_SetCompare3(0);
            break;
    }
}



//低速抽水
void lspeed_sui(void)
{
	Motor_ExtendedCtrl(MOTOR_STATE_FWD_PWM, 60);	
}
//中速抽水
void mspeed_sui(void)
{
	Motor_ExtendedCtrl(MOTOR_STATE_FWD_PWM, 70);	
}

//高速抽水
void hspeed_sui(void)
{
	Motor_ExtendedCtrl(MOTOR_STATE_FWD_PWM, 80);	
}

//刹车
void stop(void)
{
    Motor_ExtendedCtrl(MOTOR_STATE_BRAKE, 100);
}

