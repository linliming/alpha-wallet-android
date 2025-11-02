package com.alphawallet.app.entity

/**
 * Created by JB on 23/04/2022.
 */
enum class EventSyncState {
    DOWNWARD_SYNC_START,
    DOWNWARD_SYNC,
    DOWNWARD_SYNC_COMPLETE,
    UPWARD_SYNC_MAX,
    UPWARD_SYNC,
    TOP_LIMIT
}
