#include "ds18b20.h"
#include "stm32f10x_gpio.h"
#include "stm32f10x_rcc.h"

// ΢�뼶��ʱ������72MHz��Ƶ���Ż���
static void DS18B20_Delay_us(uint32_t us) {
    us = us * (SystemCoreClock / 1000000) / 9;  // ����ϵͳʱ�ӵ���
    while(us--) __NOP();
}

// ��ʼ��GPIO�ʹ�����
void DS18B20_Init(void) {
    GPIO_InitTypeDef GPIO_InitStructure;
    
    // ����GPIOCʱ��
    RCC_APB2PeriphClockCmd(DS18B20_RCC | RCC_APB2Periph_AFIO, ENABLE);
    
    // ����JTAG/SWD���Խӿڣ����ʹ��PC15��
    GPIO_PinRemapConfig(GPIO_Remap_SWJ_JTAGDisable, ENABLE);
    
    // ��������Ϊ��©���
    GPIO_InitStructure.GPIO_Pin = DS18B20_PIN;
    GPIO_InitStructure.GPIO_Mode = GPIO_Mode_Out_OD;
    GPIO_InitStructure.GPIO_Speed = GPIO_Speed_50MHz;
    GPIO_Init(DS18B20_PORT, &GPIO_InitStructure);
    
    // ��ʼ״̬��������
    GPIO_SetBits(DS18B20_PORT, DS18B20_PIN);
}

// ��λ������
static void DS18B20_Reset(void) {
    GPIO_InitTypeDef GPIO_InitStructure;
    
    // ��ʱ�л�Ϊ���������ȷ��ǿ������
    GPIO_InitStructure.GPIO_Pin = DS18B20_PIN;
    GPIO_InitStructure.GPIO_Mode = GPIO_Mode_Out_PP;
    GPIO_InitStructure.GPIO_Speed = GPIO_Speed_50MHz;
    GPIO_Init(DS18B20_PORT, &GPIO_InitStructure);
    
    // ��������480us
    GPIO_ResetBits(DS18B20_PORT, DS18B20_PIN);
    DS18B20_Delay_us(480);
    
    // �л��ؿ�©���ͷ�����
    GPIO_InitStructure.GPIO_Mode = GPIO_Mode_Out_OD;
    GPIO_Init(DS18B20_PORT, &GPIO_InitStructure);
    GPIO_SetBits(DS18B20_PORT, DS18B20_PIN);
    
    // �ȴ���������Ӧ
    DS18B20_Delay_us(70);
    
    // ����������
    while(GPIO_ReadInputDataBit(DS18B20_PORT, DS18B20_PIN) == 0);
    
    // �ȴ���λ���ڽ���
    DS18B20_Delay_us(410);
}

// ������д��һ���ֽ�
static void DS18B20_WriteByte(uint8_t data) {
    for(uint8_t i = 0; i < 8; i++) {
        // ��ʼд����
        GPIO_ResetBits(DS18B20_PORT, DS18B20_PIN);
        
        if(data & 0x01) {  // д'1'
            DS18B20_Delay_us(2);
            GPIO_SetBits(DS18B20_PORT, DS18B20_PIN);
            DS18B20_Delay_us(60);
        } else {           // д'0'
            DS18B20_Delay_us(60);
            GPIO_SetBits(DS18B20_PORT, DS18B20_PIN);
            DS18B20_Delay_us(2);
        }
        
        data >>= 1;
    }
}

// �����߶�ȡһ���ֽ�
static uint8_t DS18B20_ReadByte(void) {
    uint8_t data = 0;
    
    for(uint8_t i = 0; i < 8; i++) {
        // ����������
        GPIO_ResetBits(DS18B20_PORT, DS18B20_PIN);
        DS18B20_Delay_us(2);
        GPIO_SetBits(DS18B20_PORT, DS18B20_PIN);
        
        // ��ʱ�ȴ����������
        DS18B20_Delay_us(10);
        
        // ��ȡ����״̬
        if(GPIO_ReadInputDataBit(DS18B20_PORT, DS18B20_PIN)) {
            data |= (0x01 << i);
        }
        
        // �ȴ������ڽ���
        DS18B20_Delay_us(50);
    }
    
    return data;
}

// ��ȡ�¶�ֵ�����ظ�������
float DS18B20_ReadTemp(void) {
    uint8_t tempL, tempH;
    int16_t temp;
    
    DS18B20_Reset();
    DS18B20_WriteByte(0xCC);   // ����ROM
    DS18B20_WriteByte(0x44);   // �����¶�ת��
    
    // �ȴ�ת����ɣ����750ms��
    // ʵ��Ӧ���н���ʹ�÷�������ʱ
    for(uint32_t i = 0; i < 1000000; i++) __NOP();
    
    DS18B20_Reset();
    DS18B20_WriteByte(0xCC);   // ����ROM
    DS18B20_WriteByte(0xBE);   // ��ȡ�ݴ���
    
    tempL = DS18B20_ReadByte();  // �¶ȵ��ֽ�
    tempH = DS18B20_ReadByte();  // �¶ȸ��ֽ�
    
    // ���16λ�¶�ֵ
    temp = (tempH << 8) | tempL;
    
    // ת��Ϊ�����¶�ֵ
    return temp * 0.0625f;
}
