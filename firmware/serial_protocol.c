// Device-side serial event frames (FF ... FE) for JuiceMachine
// This file provides corrected implementations for upper-stream (device -> app) events
// matching the front-end parser expectations.

#include <stdint.h>
#include <stdbool.h>

// These functions are assumed to be provided by your platform
extern void Serial_SendByte(uint8_t byte);
extern void Serial_SendArray(const uint8_t *buf, uint16_t len);

static inline void Serial_SendFrame5(uint8_t cmd, uint8_t p0, uint8_t p1, uint8_t p2, uint8_t p3)
{
    uint8_t pkt[5];
    pkt[0] = cmd;
    pkt[1] = p0;
    pkt[2] = p1;
    pkt[3] = p2;
    pkt[4] = p3;
    Serial_SendByte(0xFF);
    Serial_SendArray(pkt, 5);
    Serial_SendByte(0xFE);
}

// 重量异常上报：front-end expects FF 09 [severity] [expected_lo] [expected_hi] [reserved] FE
// severity: 1=LOW, 2=MEDIUM, 3/4=HIGH
void Serial_SendAscending_Detail(uint8_t severity, uint16_t expected_weight)
{
    uint8_t lo = (uint8_t)(expected_weight & 0xFF);
    uint8_t hi = (uint8_t)(expected_weight >> 8);
    Serial_SendFrame5(0x09, severity, lo, hi, 0x00);
}

// 保留原函数名，给出合理默认值（severity=2，中等；expected_weight=0）
// 如需有效上报，请改用 Serial_SendAscending_Detail()
void Serial_SendAscending(void)
{
    Serial_SendFrame5(0x09, 2 /*MEDIUM*/, 0x00, 0x00, 0x00);
}

// 订单完成/失败状态：front-end parses any non-0x09 CMD with P0 as status
// status: 0xAA=success, 0xAC=failure; lastCmd can be 0x01/0x02/0x10 etc.
void Serial_SendOrderStatus(uint8_t lastCmd, bool success)
{
    Serial_SendFrame5(lastCmd, success ? 0xAA : 0xAC, 0x00, 0x00, 0x00);
}

// 温度上报：当前前端未解析温度事件，避免与制作CMD冲突，使用保留码 0x0A
void Serial_Temperature(int8_t temp_int)
{
    Serial_SendFrame5(0x0A, (uint8_t)temp_int, 0x00, 0x00, 0x00);
}

// 重量数值上报：当前前端未解析重量上报，避免与去冰/正常冰冲突，使用保留码 0x0C
void Serial_Weight(uint16_t weight_int)
{
    uint8_t lo = (uint8_t)(weight_int & 0xFF);
    uint8_t hi = (uint8_t)(weight_int >> 8);
    Serial_SendFrame5(0x0C, lo, hi, 0x00, 0x00);
}

// 完成结束：保持原函数名，语义为“完成成功”，采用 0xAA 状态
void Serial_FendAscending(void)
{
    Serial_SendFrame5(0x01 /*默认回显正常冰CMD，可按需替换为最近一次制作CMD*/,
                      0xAA /*success*/, 0x00, 0x00, 0x00);
}