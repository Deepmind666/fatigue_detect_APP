#include "hx711.h"
#include "stm32f10x.h" // 添加这行来获取 IWDG_ReloadCounter 的定义
#include "stm32f10x_flash.h"
#include "stm32f10x_rcc.h"
#include "Serial.h"  
#include "misc.h"
#include <stddef.h>  // ���� size_t ���Ͷ���
#include <math.h>
#include <float.h>   // For FLT_MAX



// ȫֲ - 洢У׼ϵ���͵�ǰ����״̬
static HX711_Params hx711_params = {0};           // ��ʼ��Ϊ��ֵ
static uint8_t current_gain = CHANNEL_B_GAIN_32; // Ĭ��ʹ��ͨ��B����32

// ����CRC32У��ֵ
static uint32_t calc_crc32(const uint8_t *data, size_t len) {
    uint32_t crc = 0xFFFFFFFF;  // CRC��ʼֵ
    
    // ����ÿ���ֽ�
    for(size_t i = 0; i < len; i++) {
        crc ^= data[i];  // �뵱ǰ�ֽ����
        
        // ����ÿ���ֽڵ�8λ
        for(int j = 0; j < 8; j++) {
            // ����1λ�������ʽ��򣨰�λ����ѡ��
            crc = (crc >> 1) ^ (0xEDB88320 & -(crc & 1));
        }
    }
    return ~crc;  // ȡ���õ�����CRCֵ
}


// HX711��ʼ������
void HX711_Init(void) {
    GPIO_InitTypeDef GPIO_InitStruct;  // GPIO���ýṹ��
    
    // ����GPIOBʱ�ӣ��޸�Ϊʵ��ʹ�õĶ˿ڣ�
    RCC_APB2PeriphClockCmd(RCC_APB2Periph_GPIOA, ENABLE);
    
    // ����SCK����Ϊ�������
    GPIO_InitStruct.GPIO_Pin = HX711_SCK_PIN;      // SCK����
    GPIO_InitStruct.GPIO_Mode = GPIO_Mode_Out_PP;  // �������ģʽ
    GPIO_InitStruct.GPIO_Speed = GPIO_Speed_50MHz; // �������
    GPIO_Init(HX711_SCK_PORT, &GPIO_InitStruct);   // Ӧ������
    
    // ����DOUT����Ϊ��������
    GPIO_InitStruct.GPIO_Pin = HX711_DOUT_PIN;     // DOUT����
    GPIO_InitStruct.GPIO_Mode = GPIO_Mode_IPU;//GPIO_Mode_IN_FLOATING; // ��������ģʽ//11111111��Ӧ��������������
    GPIO_Init(HX711_DOUT_PORT, &GPIO_InitStruct);  // Ӧ������
    
    // ��ʼ��SCK����Ϊ�ߵ�ƽ��HX711����״̬��
    GPIO_ResetBits(HX711_SCK_PORT, HX711_SCK_PIN);//222222222����״̬�ǲ���Ӧ���ǵ͵�ƽ��ʹ�ܲ�Ӧ���Ǹߵ�ƽ���ߵ�ƽ��Ч
    
    // ����10ms��ʱȷ��HX711�ϵ��ȶ�
    Delay_ms(10);
    
    // ��Flash����У׼����
    HX711_LoadParams();

    
    // ���û����Ч������ʹ��Ĭ������
    if(hx711_params.crc == 0) {
        hx711_params.current_gain = CHANNEL_B_GAIN_32;
	}
}

//
void HX711_SetGain(void) {
    if(current_gain != CHANNEL_A_GAIN_128 && 
       current_gain != CHANNEL_A_GAIN_64 && 
       current_gain != CHANNEL_B_GAIN_32) {
        return; // ��Ч����ֵ
    }
	   hx711_params.current_gain = current_gain;
    
    // Ӧ��������ǰ�����ȶ����� (��ѡ)
    // ����ǰ4�ζ�����ȷ�������ȶ�
    for(int i = 0; i < 4; i++) {
        HX711_ReadSingle();
    }
}



// ��ȡƫ��ֵ
float HX711_GetOffset(void) {
	
    return hx711_params.offset;
}


// ��ȡУ׼ϵ��
float HX711_GetScale(void) {
	
    return hx711_params.scale;
}




int32_t HX711_ReadSingle(void) {
    uint32_t timeout = 100000;
    uint32_t raw_data = 0;
    
    // ȷ��ʱ����Ϊ�ͣ�׼��״̬��
    GPIO_ResetBits(HX711_SCK_PORT, HX711_SCK_PIN);
    
    // �ȴ����ݾ���������ʱ������
    while (GPIO_ReadInputDataBit(HX711_DOUT_PORT, HX711_DOUT_PIN)) {
		IWDG_ReloadCounter();
        if(timeout-- == 0) {
            printf("Error: HX711 data timeout!\n");
            return 0x7FFFFFFF; // Ĵֵ
        }
        Delay_us(1);
    }
    
    // �����ʱ���غ�������ȡ
    if(timeout == 0) return 0x7FFFFFFF;
    
    // ��ȡ24λ����
    for(int i = 0; i < 24; i++) {
        GPIO_SetBits(HX711_SCK_PORT, HX711_SCK_PIN);  // ������
        Delay_us(1);
        
        raw_data <<= 1;
        if(GPIO_ReadInputDataBit(HX711_DOUT_PORT, HX711_DOUT_PIN)) {
            raw_data |= 0x01;  // ʹ��λ��������ӷ�
        }
        
        GPIO_ResetBits(HX711_SCK_PORT, HX711_SCK_PIN);  // �½���
        Delay_us(1);
    }
    
    // ��������
    switch(hx711_params.current_gain) {
        case CHANNEL_A_GAIN_128:
            // ����1������
            GPIO_SetBits(HX711_SCK_PORT, HX711_SCK_PIN);
            Delay_us(1);
            GPIO_ResetBits(HX711_SCK_PORT, HX711_SCK_PIN);
            Delay_us(1);
            break;
            
        case CHANNEL_A_GAIN_64:
            // ����2������
            for(int i=0; i<2; i++) {
                GPIO_SetBits(HX711_SCK_PORT, HX711_SCK_PIN);
                Delay_us(1);
                GPIO_ResetBits(HX711_SCK_PORT, HX711_SCK_PIN);
                Delay_us(1);
            }
            break;
            
        case CHANNEL_B_GAIN_32:
            // ����3������
            for(int i=0; i<3; i++) {
                GPIO_SetBits(HX711_SCK_PORT, HX711_SCK_PIN);
                Delay_us(1);
                GPIO_ResetBits(HX711_SCK_PORT, HX711_SCK_PIN);
                Delay_us(1);
            }
            break;
    }
    
    // ת������Ϊ�з���32λ����
    // 24λ���ݴ洢��32λ�����ĵ�24λ
    if(raw_data & 0x800000) { // ������λ��1����Ϊ����
        raw_data |= 0xFF000000; // ������չ
    }
    
    return (int32_t)raw_data;
}

// �޸�7: ��ȡ��ǰ����ĺ���
uint8_t HX711_GetGain(void) {
	 HX711_SetGain();
    return hx711_params.current_gain;
}

// ȥƤ���������õ�ǰΪ��㣩
void HX711_Tare(void) {
    int32_t sum = 0;              // �洢����ֵ�ܺ�
    const uint8_t samples = 10;   // ����������10��ƽ����
    
    // �ȵȴ�50msȷ���������ȶ�
    Delay_ms(50);
    
    // �ɼ���ζ�����ƽ��
    for (int i = 0; i < samples; i++) {
		IWDG_ReloadCounter();
        sum += HX711_ReadSingle();  // ۼӵζ
        
        // ÿζ1msʱ
        Delay_ms(1);
    }
    
    // ����ƽ��ֵ��Ϊ�µ�ƫ��ֵ
    hx711_params.offset = sum / (float)samples;
    
    // �Զ����������Flash���ϵ籣�֣�
    HX711_SaveParams();
}

// ��ȷʵ��У׼����
void HX711_UpdateCalibration(float known_weight) {
    if (known_weight < 0.001f) return;
    
    Delay_ms(100); // �������ȶ�ʱ��
    
    // ֱ�ӻ�ȡԭʼֵƽ��ֵ������������ϵ��ת��
    int32_t raw_sum = 0;
    const uint8_t samples = 15;
    
    for(int i = 0; i < samples; i++) {
		IWDG_ReloadCounter();
        int32_t val = HX711_ReadSingle();
        if(val != 0x80000000) { // Դ
            raw_sum += val;
        }
        Delay_ms(1);
    }
    
    float raw_avg = raw_sum / (float)samples;
    
    // ���㹫ʽ��У׼ϵ�� = (��ǰԭʼֵ - ƫ��ֵ) / ��֪����
    hx711_params.scale = (raw_avg - hx711_params.offset) / known_weight;
    
    HX711_SaveParams();
}
//// ����У׼ϵ������
//// ������known_weight - ��֪����ֵ����λ����/ǧ�ˣ�
//void HX711_UpdateCalibration(float known_weight) {
//    // ������Ч����ֵ��С��1���ˣ�
//    if (known_weight < 0.001f) return;  
//    
//    // �ȴ�50msȷ���������ȶ�
//    Delay_ms(50);
//    
//    // ��ȡ��ǰ������10��ƽ���� - �и���ʱ��ԭʼֵ
//    float current = HX711_GetWeight(10);
//    
//    // �����µ�У׼ϵ���������и���ʱ��
//    if (current != 0) {
//        // ��ʽ��У׼ϵ�� = (�и���ʱ��ԭʼֵ - ƫ��ֵ) / ��֪����
//        hx711_params.scale = (current - hx711_params.offset) / known_weight;
//    }
//    
//    // �Զ����������Flash
//    HX711_SaveParams();
//}

// ��ȡ�������������ƽ����
// ������sample_times - ����������1-64�Σ�
// ����ֵ��ƽ������ֵ
float HX711_GetWeight(uint8_t sample_times) {
	if(fabsf(hx711_params.scale) < 0.1f) {
        Serial_Printf("! Error: Invalid calibration parameters\r\n");
        return 0.0f;
    }
    if(sample_times < 1) sample_times = 1;
    
    int64_t sum = 0;
    uint8_t valid_samples = 0;
    
    for (uint8_t i = 0; i < sample_times; i++) {
        IWDG_ReloadCounter(); // 在循环中喂狗
        int32_t raw_data = HX711_ReadSingle();
        
        // 检查是否超时
        if (raw_data != 0x7FFFFFFF) {
            sum += raw_data;
            valid_samples++;
        }
    }
    
    // 如果所有采样都失败（超时），则返回错误代码
    if (valid_samples == 0) {
        return FLT_MAX;
    }
    
    float avg_raw = (float)sum / valid_samples;
    return (avg_raw - hx711_params.offset) / hx711_params.scale;
}




//ԭĴ
// Flashز
void HX711_LoadParams(void) {
    // ȡFlashв洢ַ
    HX711_Params *flash_params = (HX711_Params*)HX711_PARAM_FLASH_ADDR;
    
    // ���������CRC32ֵ��������crc�ֶ�������
    uint32_t crc_calc = calc_crc32((uint8_t*)flash_params, sizeof(HX711_Params)-4);
    
    // �ؼ��޸������Ӹ��ϸ��������Ч�Լ��
    const uint32_t MAGIC_NUMBER = 0xAA55CC33; // ����ħ����֤
    if (flash_params->magic == MAGIC_NUMBER && 
        flash_params->crc == crc_calc && 
        flash_params->scale > 0.1f &&  // ������Χ���
        fabsf(flash_params->offset) > 1000.0f) // ������Χ���
    {
        // CRCƥ�������ݺ�����ʹ�ô洢�Ĳ���
        hx711_params.offset = flash_params->offset;
        hx711_params.scale  = flash_params->scale;
        hx711_params.current_gain = flash_params->current_gain;
        Serial_Printf("Loaded saved parameters:\r\n");
        Serial_Printf("Offset: %.2f\r\n", flash_params->offset);
        Serial_Printf("Scale: %.6f\r\n", flash_params->scale);
    } else {
        // CRC��ƥ������ݲ�������ʹ��Ĭ��ֵ
        hx711_params.offset = 0;
        hx711_params.scale = 1.0f;
        hx711_params.current_gain = CHANNEL_B_GAIN_32;
        Serial_Printf("! Using default parameters (invalid saved data)\r\n");
        
        // �ؼ��޸������ӵ�����Ϣ�����������
        Serial_Printf("! Flash data: magic=0x%08lX, crc_stored=0x%08lX, crc_calc=0x%08lX\r\n",
                      flash_params->magic, flash_params->crc, crc_calc);
    }
}

// ���������Flash����
void HX711_SaveParams(void) {
    FLASH_Status status;  // Flash����״̬
    
    // �ؼ��޸����ڱ���ǰ����ħ����ʶ
    const uint32_t MAGIC_NUMBER = 0xAA55CC33;
    hx711_params.magic = MAGIC_NUMBER;
    
    // ���������CRC32ֵ��������crc�ֶ�������
    hx711_params.crc = calc_crc32((uint8_t*)&hx711_params, sizeof(HX711_Params)-4);
    
    // ����Flash����
    FLASH_Unlock();
    
    // �������Flash״̬��־
    FLASH_ClearFlag(FLASH_FLAG_EOP | FLASH_FLAG_PGERR | FLASH_FLAG_WRPRTERR);
    
    // �ؼ��޸�������Flash����ǰ��״̬���
    while(FLASH_GetFlagStatus(FLASH_FLAG_BSY) != RESET) {
        // �ȴ�Flash����
    }
    
    // ����Ŀ��Flashҳ
    status = FLASH_ErasePage(HX711_PARAM_FLASH_ADDR);
    
    // �����������Ƿ�ɹ�
    if(status != FLASH_COMPLETE) {
        FLASH_Lock(); // ����Flash�󷵻�
        Serial_Printf("! Flash erase failed: %d\r\n", status);
        return;
    }
    
    // ׼��д������
    uint32_t *src = (uint32_t*)&hx711_params;     // Դ���ݣ��ڴ��У�
    uint32_t *dst = (uint32_t*)HX711_PARAM_FLASH_ADDR; // Ŀ���ַ��Flash��
    
    // �ؼ��޸�������д��ʱ���Flash״̬
    for (int i = 0; i < sizeof(HX711_Params)/4; i++) {
        // ÿ��д��ǰ���Flash״̬
        while(FLASH_GetFlagStatus(FLASH_FLAG_BSY) != RESET) {
            // �ȴ�Flash����
        }
        
        // ���һ��32λ�ֵ�Flash
        status = FLASH_ProgramWord((uint32_t)dst, *src);
        
        // �����״̬
        if(status != FLASH_COMPLETE) {
            Serial_Printf("! Flash write failed at 0x%08lX: %d\r\n", (uint32_t)dst, status);
            break; // ��������ֹ
        }
        
        src++;  // ��һԴ����
        dst++;  // ��һĿ��λ��
    }
    
    // ����Flash����ֹ��д��
    FLASH_Lock();
    
    Serial_Printf("Configuration saved to flash.\r\n");
}
