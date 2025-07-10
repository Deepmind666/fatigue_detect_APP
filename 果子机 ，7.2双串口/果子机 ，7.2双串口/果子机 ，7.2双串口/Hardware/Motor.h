#ifndef __MOTOR_H
#define __MOTOR_H

void Motor_Init(void);
void Motor_SetSpeed(int8_t Speed);
void Motor_ExtendedCtrl(uint8_t state, uint8_t speed);

// 控制状态定义 (表二逻辑)
#define MOTOR_STATE_BRAKE     0   // 刹车 (IN3=0, IN4=0)
#define MOTOR_STATE_RELEASE   1   // 悬空 (IN3=1, IN4=1)
#define MOTOR_STATE_FWD_PWM   2   // 正转调速 (IN3=1, IN4=0)
#define MOTOR_STATE_REV_PWM   3   // 反转调速 (IN3=0, IN4=1)
#define MOTOR_STATE_FWD_FULL  4   // 全速正转 (IN3=1, IN4=0)
#define MOTOR_STATE_REV_FULL  5   // 全速反转 (IN3=0, IN4=1)

//低速抽水
void lspeed_sui(void);

//中速抽水
void mspeed_sui(void);

//高速抽水
void hspeed_sui(void);


void stop(void);

	
#endif
