#include "stm32f10x.h"
#include "serial2.h"
#include <stdio.h>
#include <stdarg.h>

uint8_t Serial2_TxPacket[5];        // USART2发送数据包
uint8_t Serial2_RxPacket[5];        // USART2接收数据包
uint8_t Serial2_RxFlag = 0;         // USART2接收数据包标志位

/**
  * 函    数：串口2初始化
  * 参    数：无
  * 返 回 值：无
  */
void Serial2_Init(void)
{
    /* 开启时钟 */
    RCC_APB1PeriphClockCmd(RCC_APB1Periph_USART2, ENABLE);  // USART2时钟在APB1
    RCC_APB2PeriphClockCmd(RCC_APB2Periph_GPIOA, ENABLE);   // GPIOA时钟
    
    /* GPIO初始化 */
    GPIO_InitTypeDef GPIO_InitStructure;
    
    // PA2作为USART2_TX - 复用推挽输出
    GPIO_InitStructure.GPIO_Mode = GPIO_Mode_AF_PP;
    GPIO_InitStructure.GPIO_Pin = GPIO_Pin_2;
    GPIO_InitStructure.GPIO_Speed = GPIO_Speed_50MHz;
    GPIO_Init(GPIOA, &GPIO_InitStructure);
    
    // PA3作为USART2_RX - 上拉输入
    GPIO_InitStructure.GPIO_Mode = GPIO_Mode_IPU;
    GPIO_InitStructure.GPIO_Pin = GPIO_Pin_3;
    GPIO_InitStructure.GPIO_Speed = GPIO_Speed_50MHz;
    GPIO_Init(GPIOA, &GPIO_InitStructure);
    
    /* USART初始化 */
    USART_InitTypeDef USART_InitStructure;
    USART_InitStructure.USART_BaudRate = 9600;
    USART_InitStructure.USART_HardwareFlowControl = USART_HardwareFlowControl_None;
    USART_InitStructure.USART_Mode = USART_Mode_Tx | USART_Mode_Rx;
    USART_InitStructure.USART_Parity = USART_Parity_No;
    USART_InitStructure.USART_StopBits = USART_StopBits_1;
    USART_InitStructure.USART_WordLength = USART_WordLength_8b;
    USART_Init(USART2, &USART_InitStructure);
    
    /* 中断输出配置 */
    USART_ITConfig(USART2, USART_IT_RXNE, ENABLE);
    
    /* NVIC中断分组 */
    NVIC_PriorityGroupConfig(NVIC_PriorityGroup_2);
    
    /* NVIC配置 */
    NVIC_InitTypeDef NVIC_InitStructure;
    NVIC_InitStructure.NVIC_IRQChannel = USART2_IRQn;
    NVIC_InitStructure.NVIC_IRQChannelCmd = ENABLE;
    NVIC_InitStructure.NVIC_IRQChannelPreemptionPriority = 1;
    NVIC_InitStructure.NVIC_IRQChannelSubPriority = 2;  // 优先级与串口1不同
    NVIC_Init(&NVIC_InitStructure);
    
    /* USART使能 */
    USART_Cmd(USART2, ENABLE);
}

/**
  * 函    数：串口2发送一个字节
  */
void Serial2_SendByte(uint8_t Byte)
{
    USART_SendData(USART2, Byte);
    while (USART_GetFlagStatus(USART2, USART_FLAG_TXE) == RESET);
}

/**
  * 函    数：串口2发送一个数组
  */
void Serial2_SendArray(uint8_t *Array, uint16_t Length)
{
    uint16_t i;
    for (i = 0; i < Length; i++) {
        Serial2_SendByte(Array[i]);
    }
}

/**
  * 函    数：串口2发送一个字符串
  */
void Serial2_SendString(char *String)
{
    uint8_t i;
    for (i = 0; String[i] != '\0'; i++) {
        Serial2_SendByte(String[i]);
    }
}

/**
  * 函    数：次方函数（内部使用）
  */
static uint32_t Serial2_Pow(uint32_t X, uint32_t Y)
{
    uint32_t Result = 1;
    while (Y--) {
        Result *= X;
    }
    return Result;
}

/**
  * 函    数：串口2发送数字
  */
void Serial2_SendNumber(uint32_t Number, uint8_t Length)
{
    uint8_t i;
    for (i = 0; i < Length; i++) {
        Serial2_SendByte(Number / Serial2_Pow(10, Length - i - 1) % 10 + '0');
    }
}

/**
  * 函    数：串口2封装的printf函数
  */
void Serial2_Printf(char *format, ...)
{
    char String[100];
    va_list arg;
    va_start(arg, format);
    vsprintf(String, format, arg);
    va_end(arg);
    Serial2_SendString(String);
}

/**
  * 函    数：串口2发送数据包
  */
void Serial2_SendPacket(void)
{
    Serial2_SendByte(0xFF);
    Serial2_SendArray(Serial2_TxPacket, 5);
    Serial2_SendByte(0xFE);
}

/**
  * 函    数：获取串口2接收数据包标志位
  */
uint8_t Serial2_GetRxFlag(void)
{
    if (Serial2_RxFlag == 1) {
        Serial2_RxFlag = 0;
        return 1;
    }
    return 0;
}

/**
  * 函    数：USART2中断服务函数
  */
void USART2_IRQHandler(void)
{
    static uint8_t RxState = 0;      // 0=等待包头, 1=接收有效数据, 2=等待包尾
    static uint8_t pRxPacket = 0;    // 当前接收位置
    
    if (USART_GetITStatus(USART2, USART_IT_RXNE) == SET) 
    {
        uint8_t RxData = USART_ReceiveData(USART2);
        
        switch(RxState) {
            case 0: // 等待包头0xFF
                if (RxData == 0xFF) {
                    RxState = 1;
                    pRxPacket = 0;
                }
                break;
                
            case 1: // 接收5字节有效数据
                if (pRxPacket < 5) {
                    Serial2_RxPacket[pRxPacket] = RxData;
                    pRxPacket++;
                }
                
                if (pRxPacket >= 5) {
                    RxState = 2;
                }
                break;
                
            case 2: // 验证包尾0xFE
                if (RxData == 0xFE) {
                    Serial2_RxFlag = 1;
                } else {
                    // 包尾错误处理（可选）
                }
                RxState = 0;
                break;
        }
        
        USART_ClearITPendingBit(USART2, USART_IT_RXNE);
    }
}