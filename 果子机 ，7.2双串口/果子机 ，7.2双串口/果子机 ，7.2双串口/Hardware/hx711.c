#include "hx711.h"
#include "stm32f10x_flash.h"
#include "stm32f10x_rcc.h"
#include "Serial.h"  
#include "misc.h"
#include <stddef.h>  // 包含 size_t 类型定义
#include <math.h>



// 全局参数 - 存储校准系数和当前增益状态
static HX711_Params hx711_params = {0};           // 初始化为零值
static uint8_t current_gain = CHANNEL_B_GAIN_32; // 默认使用通道B增益32

// 计算CRC32校验值
static uint32_t calc_crc32(const uint8_t *data, size_t len) {
    uint32_t crc = 0xFFFFFFFF;  // CRC初始值
    
    // 遍历每个字节
    for(size_t i = 0; i < len; i++) {
        crc ^= data[i];  // 与当前字节异或
        
        // 处理每个字节的8位
        for(int j = 0; j < 8; j++) {
            // 右移1位并与多项式异或（按位条件选择）
            crc = (crc >> 1) ^ (0xEDB88320 & -(crc & 1));
        }
    }
    return ~crc;  // 取反得到最终CRC值
}


// HX711初始化函数
void HX711_Init(void) {
    GPIO_InitTypeDef GPIO_InitStruct;  // GPIO配置结构体
    
    // 启用GPIOB时钟（修改为实际使用的端口）
    RCC_APB2PeriphClockCmd(RCC_APB2Periph_GPIOA, ENABLE);
    
    // 配置SCK引脚为推挽输出
    GPIO_InitStruct.GPIO_Pin = HX711_SCK_PIN;      // SCK引脚
    GPIO_InitStruct.GPIO_Mode = GPIO_Mode_Out_PP;  // 推挽输出模式
    GPIO_InitStruct.GPIO_Speed = GPIO_Speed_50MHz; // 高速输出
    GPIO_Init(HX711_SCK_PORT, &GPIO_InitStruct);   // 应用配置
    
    // 配置DOUT引脚为浮空输入
    GPIO_InitStruct.GPIO_Pin = HX711_DOUT_PIN;     // DOUT引脚
    GPIO_InitStruct.GPIO_Mode = GPIO_Mode_IPU;//GPIO_Mode_IN_FLOATING; // 浮空输入模式//11111111不应该是上拉输入吗
    GPIO_Init(HX711_DOUT_PORT, &GPIO_InitStruct);  // 应用配置
    
    // 初始化SCK引脚为高电平（HX711空闲状态）
    GPIO_ResetBits(HX711_SCK_PORT, HX711_SCK_PIN);//222222222空闲状态是不是应该是低电平，使能才应该是高电平，高电平有效
    
    // 添加10ms延时确保HX711上电稳定
    Delay_ms(10);
    
    // 从Flash加载校准参数
    HX711_LoadParams();

    
    // 如果没有有效参数，使用默认增益
    if(hx711_params.crc == 0) {
        hx711_params.current_gain = CHANNEL_B_GAIN_32;
	}
}

//
void HX711_SetGain(void) {
    if(current_gain != CHANNEL_A_GAIN_128 && 
       current_gain != CHANNEL_A_GAIN_64 && 
       current_gain != CHANNEL_B_GAIN_32) {
        return; // 无效增益值
    }
	   hx711_params.current_gain = current_gain;
    
    // 应用新增益前进行稳定处理 (可选)
    // 丢弃前4次读数以确保增益稳定
    for(int i = 0; i < 4; i++) {
        HX711_ReadSingle();
    }
}



// 获取偏移值
float HX711_GetOffset(void) {
	
    return hx711_params.offset;
}


// 获取校准系数
float HX711_GetScale(void) {
	
    return hx711_params.scale;
}




int32_t HX711_ReadSingle(void) {
    uint32_t timeout = 100000;
    uint32_t raw_data = 0;
    
    // 确保时钟线为低（准备状态）
    GPIO_ResetBits(HX711_SCK_PORT, HX711_SCK_PIN);
    
    // 等待数据就绪（带超时保护）
    while (GPIO_ReadInputDataBit(HX711_DOUT_PORT, HX711_DOUT_PIN)) {
        if(timeout-- == 0) {
            printf("Error: HX711 data timeout!\n");
            return 0x7FFFFFFF; // 特殊的错误值
        }
        Delay_us(1);
    }
    
    // 如果超时返回后跳过读取
    if(timeout == 0) return 0x7FFFFFFF;
    
    // 读取24位数据
    for(int i = 0; i < 24; i++) {
        GPIO_SetBits(HX711_SCK_PORT, HX711_SCK_PIN);  // 上升沿
        Delay_us(1);
        
        raw_data <<= 1;
        if(GPIO_ReadInputDataBit(HX711_DOUT_PORT, HX711_DOUT_PIN)) {
            raw_data |= 0x01;  // 使用位操作代替加法
        }
        
        GPIO_ResetBits(HX711_SCK_PORT, HX711_SCK_PIN);  // 下降沿
        Delay_us(1);
    }
    
    // 设置增益
    switch(hx711_params.current_gain) {
        case CHANNEL_A_GAIN_128:
            // 发送1个脉冲
            GPIO_SetBits(HX711_SCK_PORT, HX711_SCK_PIN);
            Delay_us(1);
            GPIO_ResetBits(HX711_SCK_PORT, HX711_SCK_PIN);
            Delay_us(1);
            break;
            
        case CHANNEL_A_GAIN_64:
            // 发送2个脉冲
            for(int i=0; i<2; i++) {
                GPIO_SetBits(HX711_SCK_PORT, HX711_SCK_PIN);
                Delay_us(1);
                GPIO_ResetBits(HX711_SCK_PORT, HX711_SCK_PIN);
                Delay_us(1);
            }
            break;
            
        case CHANNEL_B_GAIN_32:
            // 发送3个脉冲
            for(int i=0; i<3; i++) {
                GPIO_SetBits(HX711_SCK_PORT, HX711_SCK_PIN);
                Delay_us(1);
                GPIO_ResetBits(HX711_SCK_PORT, HX711_SCK_PIN);
                Delay_us(1);
            }
            break;
    }
    
    // 转换数据为有符号32位整数
    // 24位数据存储在32位整数的低24位
    if(raw_data & 0x800000) { // 如果最高位是1，则为负数
        raw_data |= 0xFF000000; // 符号扩展
    }
    
    return (int32_t)raw_data;
}

// 修改7: 获取当前增益的函数
uint8_t HX711_GetGain(void) {
	 HX711_SetGain();
    return hx711_params.current_gain;
}

// 去皮函数（设置当前为零点）
void HX711_Tare(void) {
    int32_t sum = 0;              // 存储采样值总和
    const uint8_t samples = 10;   // 采样次数（10次平均）
    
    // 先等待50ms确保传感器稳定
    Delay_ms(50);
    
    // 采集多次读数求平均
    for (int i = 0; i < samples; i++) {
        sum += HX711_ReadSingle();  // 累加单次读数
        
        // 每次读数后添加1ms延时
        Delay_ms(1);
    }
    
    // 计算平均值作为新的偏移值
    hx711_params.offset = sum / (float)samples;
    
    // 自动保存参数到Flash（断电保持）
    HX711_SaveParams();
}

// 正确实现校准函数
void HX711_UpdateCalibration(float known_weight) {
    if (known_weight < 0.001f) return;
    
    Delay_ms(100); // 更长的稳定时间
    
    // 直接获取原始值平均值，不经过现有系数转换
    int32_t raw_sum = 0;
    const uint8_t samples = 15;
    
    for(int i = 0; i < samples; i++) {
        int32_t val = HX711_ReadSingle();
        if(val != 0x80000000) { // 忽略错误读数
            raw_sum += val;
        }
        Delay_ms(1);
    }
    
    float raw_avg = raw_sum / (float)samples;
    
    // 计算公式：校准系数 = (当前原始值 - 偏移值) / 已知重量
    hx711_params.scale = (raw_avg - hx711_params.offset) / known_weight;
    
    HX711_SaveParams();
}
//// 更新校准系数函数
//// 参数：known_weight - 已知重量值（单位：克/千克）
//void HX711_UpdateCalibration(float known_weight) {
//    // 忽略无效重量值（小于1毫克）
//    if (known_weight < 0.001f) return;  
//    
//    // 等待50ms确保传感器稳定
//    Delay_ms(50);
//    
//    // 获取当前重量（10次平均） - 有负载时的原始值
//    float current = HX711_GetWeight(10);
//    
//    // 计算新的校准系数（仅当有负载时）
//    if (current != 0) {
//        // 公式：校准系数 = (有负载时的原始值 - 偏移值) / 已知重量
//        hx711_params.scale = (current - hx711_params.offset) / known_weight;
//    }
//    
//    // 自动保存参数到Flash
//    HX711_SaveParams();
//}

// 获取重量函数（多次平均）
// 参数：sample_times - 采样次数（1-64次）
// 返回值：平均重量值
float HX711_GetWeight(uint8_t sample_times) {
	if(fabsf(hx711_params.scale) < 0.1f) {
        Serial_Printf("! Error: Invalid calibration parameters\r\n");
        return 0.0f;
    }
    int32_t sum = 0;
    int valid_samples = 0;
    
    // 限制采样次数在合理范围
    if(sample_times < 1) sample_times = 1;
    if(sample_times > 64) sample_times = 64;
    
    // 进行指定次数的采样
    for (int i = 0; i < sample_times; i++) {
        int32_t val = HX711_ReadSingle();
        
        // 只忽略真正的错误值，允许负值
        if (val == 0x7FFFFFFF) {
            printf("! Warning: Invalid reading skipped\n");
            continue;  
        }
        
        sum += val;
        valid_samples++;
        Delay_ms(2);  // 增加延时以减少噪声干扰
    }
    
    // 没有有效读数时返回0
    if(valid_samples == 0) {
        printf("! Error: No valid readings\n");
        return 0.0f;
    }
    
    // 计算原始值的平均值
    float avg = sum / (float)valid_samples;
    
    // 转换为实际重量
    float weight = (avg - hx711_params.offset) / 224;
    
    // 为负值时返回0
    if(weight < 0) weight = 0.0f;
    
    return weight;
}




//原本的代码
// 从Flash加载参数函数
void HX711_LoadParams(void) {
    // 获取Flash中参数存储地址
    HX711_Params *flash_params = (HX711_Params*)HX711_PARAM_FLASH_ADDR;
    
    // 计算参数的CRC32值（不包括crc字段自身）
    uint32_t crc_calc = calc_crc32((uint8_t*)flash_params, sizeof(HX711_Params)-4);
    
    // 关键修复：添加更严格的数据有效性检查
    const uint32_t MAGIC_NUMBER = 0xAA55CC33; // 添加魔数验证
    if (flash_params->magic == MAGIC_NUMBER && 
        flash_params->crc == crc_calc && 
        flash_params->scale > 0.1f &&  // 合理范围检查
        fabsf(flash_params->offset) > 1000.0f) // 合理范围检查
    {
        // CRC匹配且数据合理：使用存储的参数
        hx711_params.offset = flash_params->offset;
        hx711_params.scale  = flash_params->scale;
        hx711_params.current_gain = flash_params->current_gain;
        Serial_Printf("Loaded saved parameters:\r\n");
        Serial_Printf("Offset: %.2f\r\n", flash_params->offset);
        Serial_Printf("Scale: %.6f\r\n", flash_params->scale);
    } else {
        // CRC不匹配或数据不合理：使用默认值
        hx711_params.offset = 0;
        hx711_params.scale = 1.0f;
        hx711_params.current_gain = CHANNEL_B_GAIN_32;
        Serial_Printf("! Using default parameters (invalid saved data)\r\n");
        
        // 关键修复：添加调试信息帮助诊断问题
        Serial_Printf("! Flash data: magic=0x%08lX, crc_stored=0x%08lX, crc_calc=0x%08lX\r\n",
                      flash_params->magic, flash_params->crc, crc_calc);
    }
}

// 保存参数到Flash函数
void HX711_SaveParams(void) {
    FLASH_Status status;  // Flash操作状态
    
    // 关键修复：在保存前添加魔数标识
    const uint32_t MAGIC_NUMBER = 0xAA55CC33;
    hx711_params.magic = MAGIC_NUMBER;
    
    // 计算参数的CRC32值（不包括crc字段自身）
    hx711_params.crc = calc_crc32((uint8_t*)&hx711_params, sizeof(HX711_Params)-4);
    
    // 解锁Flash操作
    FLASH_Unlock();
    
    // 清除所有Flash状态标志
    FLASH_ClearFlag(FLASH_FLAG_EOP | FLASH_FLAG_PGERR | FLASH_FLAG_WRPRTERR);
    
    // 关键修复：添加Flash擦除前的状态检查
    while(FLASH_GetFlagStatus(FLASH_FLAG_BSY) != RESET) {
        // 等待Flash空闲
    }
    
    // 擦除目标Flash页
    status = FLASH_ErasePage(HX711_PARAM_FLASH_ADDR);
    
    // 检查擦除操作是否成功
    if(status != FLASH_COMPLETE) {
        FLASH_Lock(); // 锁定Flash后返回
        Serial_Printf("! Flash erase failed: %d\r\n", status);
        return;
    }
    
    // 准备写入数据
    uint32_t *src = (uint32_t*)&hx711_params;     // 源数据（内存中）
    uint32_t *dst = (uint32_t*)HX711_PARAM_FLASH_ADDR; // 目标地址（Flash）
    
    // 关键修复：逐字写入时检查Flash状态
    for (int i = 0; i < sizeof(HX711_Params)/4; i++) {
        // 每次写入前检查Flash状态
        while(FLASH_GetFlagStatus(FLASH_FLAG_BSY) != RESET) {
            // 等待Flash空闲
        }
        
        // 编程一个32位字到Flash
        status = FLASH_ProgramWord((uint32_t)dst, *src);
        
        // 检查编程状态
        if(status != FLASH_COMPLETE) {
            Serial_Printf("! Flash write failed at 0x%08lX: %d\r\n", (uint32_t)dst, status);
            break; // 错误则终止
        }
        
        src++;  // 下一源数据
        dst++;  // 下一目标位置
    }
    
    // 锁定Flash（防止误写）
    FLASH_Lock();
    
    Serial_Printf("Configuration saved to flash.\r\n");
}
