package com.example.juicemachine.ui.viewmodel

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow

/**
 * 全局事件通知器：当订单数据变化（完成/失败/取消）时，通知统计页面自动刷新。
 * 轻量、无依赖，不持久化，仅在进程内生效。
 */
object StatisticsRefreshNotifier {
    // 无粘性事件，避免历史事件重复触发；提供缓冲，防止背压丢事件
    private val _events = MutableSharedFlow<Unit>(extraBufferCapacity = 64)
    val events: SharedFlow<Unit> = _events

    fun notifyOrderUpdated() {
        _events.tryEmit(Unit)
    }
}