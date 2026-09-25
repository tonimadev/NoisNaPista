package com.ipirangatech.fidd.core.data

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

@HiltWorker
class SyncWorker
    @AssistedInject
    constructor(
        @Assisted context: Context,
        @Assisted workerParams: WorkerParameters,
        private val repository: PotholeRepository,
    ) : CoroutineWorker(context, workerParams) {
        override suspend fun doWork(): Result {
            return try {
                repository.syncPotholes()
                Result.success()
            } catch (e: Exception) {
                Result.retry()
            }
        }
    }
