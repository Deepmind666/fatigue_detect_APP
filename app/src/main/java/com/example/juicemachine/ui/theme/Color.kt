package com.example.juicemachine.ui.theme

import androidx.compose.ui.graphics.Color

val Purple80 = Color(0xFFD0BCFF)
val PurpleGrey80 = Color(0xFFCCC2DC)
val Pink80 = Color(0xFFEFB8C8)

val Purple40 = Color(0xFF6650a4)
val PurpleGrey40 = Color(0xFF625b71)
val Pink40 = Color(0xFF7D5260)

// 果然新鲜品牌色彩
val FreshOrange = Color(0xFFFF6D00)   // 主要橙色 - 新鲜果汁
val FreshGreen = Color(0xFF4CAF50)    // 新鲜绿色 - 健康活力
val FreshYellow = Color(0xFFFFC107)   // 柠檬黄色 - 酸甜清新
val FreshRed = Color(0xFFE91E63)      // 浆果红色 - 浓郁果香
val FreshCream = Color(0xFFFFF8E1)    // 奶昔色 - 温和背景

// 原有颜色保持兼容
val BrandBlue = FreshOrange           // 替换为果汁橙色
val LightGrey = Color(0xFFF5F5F5)     // 更柔和的灰色
val DarkGrey = Color(0xFF424242)      // 更温和的深灰
val StatusGreen = Color(0xFF66BB6A)   // 状态绿色
val StatusRed = Color(0xFFEF5350)     // 状态红色

// 新增果汁相关颜色
val WarmBeige = Color(0xFFFFF3E0)     // 温暖奶昔色
val DeepFruit = Color(0xFFE65100)     // 深果汁色
val LightFruit = Color(0xFFFFCC80)    // 浅果汁色
val AccentOrange = Color(0xFFFF8A65)  // 强调橙色

// 为了兼容性，保留旧的命名
val TeaGreen = FreshGreen
val TeaBrown = Color(0xFF8D6E63)      // 保持茶褐色用于某些UI元素
val TeaGold = FreshYellow
val TeaCream = FreshCream
val DeepTea = DeepFruit
val LightTea = LightFruit