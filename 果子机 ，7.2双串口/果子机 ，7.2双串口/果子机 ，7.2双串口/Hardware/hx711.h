#ifndef __HX711_H
#define __HX711_H

#include "stm32f10x.h"
#include "Delay.h"  // 包含延时库头文件

// 引脚定义 - 根据实际硬件连接修改
#define HX711_DOUT_PORT  GPIOA
#define HX711_DOUT_PIN   GPIO_Pin_1
#define HX711_SCK_PORT   GPIOA
#define HX711_SCK_PIN    GPIO_Pin_0

// 增益设置
#define CHANNEL_A_GAIN_128 1  // 通道A增益128
#define CHANNEL_A_GAIN_64  2  // 通道A增益64 (新增)
#define CHANNEL_B_GAIN_32  3  // 通道B增益32


// Flash参数存储地址（使用Flash最后一页的末尾）
#define HX711_PARAM_FLASH_ADDR  0x0800FC00

// 错误码定义
#define HX711_OK         0
#define HX711_TIMEOUT    -1
#define HX711_READERROR  -2

// 参数结构体（存储到Flash）
typedef struct {
    float offset;
    float scale;
    uint8_t current_gain;   // 增益设置
    uint32_t magic;         // 魔数验证
    uint32_t crc;           // CRC32校验值（不包括crc自身）
} HX711_Params;
extern float HX711_GetOffset(void);   // 获取偏移值
extern float HX711_GetScale(void);   

// 初始化函数
void HX711_Init(void);

// 读取单次原始值（24位有符号数）
int32_t HX711_ReadSingle(void);

// 去皮函数（设置当前读数为零点）
void HX711_Tare(void);

// 更新校准系数（基于已知重量）
void HX711_UpdateCalibration(float known_weight);

// 读取重量（多次平均）
float HX711_GetWeight(uint8_t sample_times);

// 从Flash加载参数
void HX711_LoadParams(void);

// 保存参数到Flash
void HX711_SaveParams(void);

#endif


