package com.alphawallet.app.network

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import retrofit2.Response
import java.io.IOException

interface NetworkService {
    suspend fun <T> executeCall(call: suspend () -> Response<T>): Result<T>

    fun <T> executeCallAsFlow(call: suspend () -> Response<T>): Flow<Result<T>>
}

class NetworkServiceImpl : NetworkService {
    override suspend fun <T> executeCall(call: suspend () -> Response<T>): Result<T> =
        try {
            val response = call()
            if (response.isSuccessful) {
                Result.success(response.body()!!)
            } else {
                Result.failure(IOException("HTTP ${response.code()}: ${response.message()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }

    override fun <T> executeCallAsFlow(call: suspend () -> Response<T>): Flow<Result<T>> =
        flow {
            emit(executeCall(call))
        }
}
