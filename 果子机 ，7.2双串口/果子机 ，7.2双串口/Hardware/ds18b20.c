#include "ds18b20.h"
#include "stm32f10x_gpio.h"
#include "stm32f10x_rcc.h"

// 微秒级延时函数（72MHz主频下优化）
static void DS18B20_Delay_us(uint32_t us) {
    us = us * (SystemCoreClock / 1000000) / 9;  // 根据系统时钟调整
    while(us--) __NOP();
}

// 初始化GPIO和传感器
void DS18B20_Init(void) {
    GPIO_InitTypeDef GPIO_InitStructure;
    
    // 开启GPIOC时钟
    RCC_APB2PeriphClockCmd(DS18B20_RCC | RCC_APB2Periph_AFIO, ENABLE);
    
    // 禁用JTAG/SWD调试接口（如果使用PC15）
    GPIO_PinRemapConfig(GPIO_Remap_SWJ_JTAGDisable, ENABLE);
    
    // 配置引脚为开漏输出
    GPIO_InitStructure.GPIO_Pin = DS18B20_PIN;
    GPIO_InitStructure.GPIO_Mode = GPIO_Mode_Out_OD;
    GPIO_InitStructure.GPIO_Speed = GPIO_Speed_50MHz;
    GPIO_Init(DS18B20_PORT, &GPIO_InitStructure);
    
    // 初始状态拉高总线
    GPIO_SetBits(DS18B20_PORT, DS18B20_PIN);
}

// 复位单总线
static void DS18B20_Reset(void) {
    GPIO_InitTypeDef GPIO_InitStructure;
    
    // 临时切换为推挽输出（确保强下拉）
    GPIO_InitStructure.GPIO_Pin = DS18B20_PIN;
    GPIO_InitStructure.GPIO_Mode = GPIO_Mode_Out_PP;
    GPIO_InitStructure.GPIO_Speed = GPIO_Speed_50MHz;
    GPIO_Init(DS18B20_PORT, &GPIO_InitStructure);
    
    // 拉低总线480us
    GPIO_ResetBits(DS18B20_PORT, DS18B20_PIN);
    DS18B20_Delay_us(480);
    
    // 切换回开漏并释放总线
    GPIO_InitStructure.GPIO_Mode = GPIO_Mode_Out_OD;
    GPIO_Init(DS18B20_PORT, &GPIO_InitStructure);
    GPIO_SetBits(DS18B20_PORT, DS18B20_PIN);
    
    // 等待传感器响应
    DS18B20_Delay_us(70);
    
    // 检测存在脉冲
    while(GPIO_ReadInputDataBit(DS18B20_PORT, DS18B20_PIN) == 0);
    
    // 等待复位周期结束
    DS18B20_Delay_us(410);
}

// 向总线写入一个字节
static void DS18B20_WriteByte(uint8_t data) {
    for(uint8_t i = 0; i < 8; i++) {
        // 开始写周期
        GPIO_ResetBits(DS18B20_PORT, DS18B20_PIN);
        
        if(data & 0x01) {  // 写'1'
            DS18B20_Delay_us(2);
            GPIO_SetBits(DS18B20_PORT, DS18B20_PIN);
            DS18B20_Delay_us(60);
        } else {           // 写'0'
            DS18B20_Delay_us(60);
            GPIO_SetBits(DS18B20_PORT, DS18B20_PIN);
            DS18B20_Delay_us(2);
        }
        
        data >>= 1;
    }
}

// 从总线读取一个字节
static uint8_t DS18B20_ReadByte(void) {
    uint8_t data = 0;
    
    for(uint8_t i = 0; i < 8; i++) {
        // 启动读周期
        GPIO_ResetBits(DS18B20_PORT, DS18B20_PIN);
        DS18B20_Delay_us(2);
        GPIO_SetBits(DS18B20_PORT, DS18B20_PIN);
        
        // 延时等待传感器输出
        DS18B20_Delay_us(10);
        
        // 读取总线状态
        if(GPIO_ReadInputDataBit(DS18B20_PORT, DS18B20_PIN)) {
            data |= (0x01 << i);
        }
        
        // 等待读周期结束
        DS18B20_Delay_us(50);
    }
    
    return data;
}

// 读取温度值（返回浮点数）
float DS18B20_ReadTemp(void) {
    uint8_t tempL, tempH;
    int16_t temp;
    
    DS18B20_Reset();
    DS18B20_WriteByte(0xCC);   // 跳过ROM
    DS18B20_WriteByte(0x44);   // 启动温度转换
    
    // 等待转换完成（最大750ms）
    // 实际应用中建议使用非阻塞延时
    for(uint32_t i = 0; i < 1000000; i++) __NOP();
    
    DS18B20_Reset();
    DS18B20_WriteByte(0xCC);   // 跳过ROM
    DS18B20_WriteByte(0xBE);   // 读取暂存器
    
    tempL = DS18B20_ReadByte();  // 温度低字节
    tempH = DS18B20_ReadByte();  // 温度高字节
    
    // 组合16位温度值
    temp = (tempH << 8) | tempL;
    
    // 转换为浮点温度值
    return temp * 0.0625f;
}  