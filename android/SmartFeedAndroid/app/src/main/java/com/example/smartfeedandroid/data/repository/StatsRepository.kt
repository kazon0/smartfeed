package com.example.smartfeedandroid.data.repository

import com.example.smartfeedandroid.data.remote.SmartFeedApi
import com.example.smartfeedandroid.data.remote.SmartFeedNetwork
import com.example.smartfeedandroid.data.remote.StatsResponse

class StatsRepository(
    private val api: SmartFeedApi = SmartFeedNetwork.api
) {
    suspend fun getStats(): Result<StatsResponse> {
        return runCatching {
            api.stats()
        }
    }
}
