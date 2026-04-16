package com.example.onbitllm.viewmodel

import android.app.ActivityManager
import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.onbitllm.engine.LlamaEngine
import com.example.onbitllm.model.LlmModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.FileReader

/**
 * リソースモニターのUIステート
 */
data class ResourceUiState(
    // Model RAM: 選択モデルのメモリ使用量 (GB)
    val modelRamGb: Float = 0f,
    val modelRamMaxGb: Float = 4f,   // スケール上限（プログレスバー用）
    // System RAM
    val systemUsedGb: Float = 0f,
    val systemTotalGb: Float = 16f,
    // CPU
    val cpuPercent: Float = 0f
)

/**
 * リソースモニター用 ViewModel
 *
 * Sprint 5:
 * - Model RAM を LlamaEngine.getModelMemoryUsageMb() の実値に切り替え
 * - エンジン未ロード時は 0 GB と表示
 * - System RAM / CPU は Sprint 4 と同じ実値取得ロジックを維持
 */
class ResourceViewModel(
    private val context: Context,
    private val engineProvider: (() -> LlamaEngine?)? = null
) : ViewModel() {

    private val _state = MutableStateFlow(ResourceUiState())
    val state: StateFlow<ResourceUiState> = _state.asStateFlow()

    // /proc/stat のサンプリング用前回値
    private var prevTotal: Long = 0L
    private var prevIdle: Long = 0L

    // 更新用ジョブ
    private var updateJob: kotlinx.coroutines.Job? = null

    /**
     * モニタリング開始（1秒ごとに更新）
     *
     * @param model 現在選択されているモデル（LlamaEngine が未使用時のフォールバック計算に使用）
     */
    fun startMonitoring(model: LlmModel) {
        updateJob?.cancel()
        updateJob = viewModelScope.launch {
            // モデルRAMの最大スケール
            val modelRamMax = 4f

            // 合計メモリを取得（起動時1回）
            val totalRamGb = getTotalRamGb()

            while (true) {
                val systemUsedGb = getSystemUsedRamGb()
                val cpuPercent = getCpuPercent()

                // Sprint 5: LlamaEngine からモデルメモリ使用量を取得
                val modelRamGb = getModelRamGb(model)

                _state.update {
                    it.copy(
                        modelRamGb = modelRamGb,
                        modelRamMaxGb = modelRamMax,
                        systemUsedGb = systemUsedGb,
                        systemTotalGb = totalRamGb,
                        cpuPercent = cpuPercent
                    )
                }

                delay(1000L)
            }
        }
    }

    /**
     * モニタリング停止
     */
    fun stopMonitoring() {
        updateJob?.cancel()
        updateJob = null
    }

    /**
     * モデルのメモリ使用量 (GB) を返す。
     *
     * Sprint 5: LlamaEngine.getModelMemoryUsageMb() を使用。
     * エンジンが利用できない場合は 0 を返す。
     */
    private fun getModelRamGb(model: LlmModel): Float {
        val engine = engineProvider?.invoke()
        return if (engine != null && engine.isModelLoaded()) {
            engine.getModelMemoryUsageMb().toFloat() / 1024f
        } else {
            // エンジン未ロード: 0 GB
            0f
        }
    }

    /**
     * 端末の総 RAM (GB) を取得
     */
    private fun getTotalRamGb(): Float {
        return try {
            val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
            val memInfo = ActivityManager.MemoryInfo()
            am.getMemoryInfo(memInfo)
            memInfo.totalMem.toFloat() / (1024f * 1024f * 1024f)
        } catch (e: Exception) {
            16f // フォールバック
        }
    }

    /**
     * 使用中の RAM (GB) を取得
     */
    private suspend fun getSystemUsedRamGb(): Float = withContext(Dispatchers.IO) {
        try {
            val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
            val memInfo = ActivityManager.MemoryInfo()
            am.getMemoryInfo(memInfo)
            val usedBytes = memInfo.totalMem - memInfo.availMem
            usedBytes.toFloat() / (1024f * 1024f * 1024f)
        } catch (e: Exception) {
            0f
        }
    }

    /**
     * CPU 使用率 (%) を /proc/stat から計算
     */
    private suspend fun getCpuPercent(): Float = withContext(Dispatchers.IO) {
        try {
            val (total, idle) = readCpuStats()
            val diffTotal = total - prevTotal
            val diffIdle = idle - prevIdle

            val usage = if (diffTotal > 0) {
                ((diffTotal - diffIdle).toFloat() / diffTotal.toFloat()) * 100f
            } else {
                0f
            }

            prevTotal = total
            prevIdle = idle

            usage.coerceIn(0f, 100f)
        } catch (e: Exception) {
            0f
        }
    }

    /**
     * /proc/stat の最初の行を読んで (total, idle) を返す
     */
    private fun readCpuStats(): Pair<Long, Long> {
        val reader = BufferedReader(FileReader("/proc/stat"))
        val line = reader.readLine()
        reader.close()

        val parts = line.trim().split("\\s+".toRegex())
        if (parts.size < 5) return Pair(0L, 0L)

        val values = parts.drop(1).mapNotNull { it.toLongOrNull() }
        val total = values.sum()
        val idle = values.getOrElse(3) { 0L } + values.getOrElse(4) { 0L }
        return Pair(total, idle)
    }

    override fun onCleared() {
        super.onCleared()
        updateJob?.cancel()
    }

    /**
     * Factory: Context のみ（Sprint 4との後方互換）
     */
    class Factory(private val context: Context) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return ResourceViewModel(context.applicationContext) as T
        }
    }

    /**
     * Factory: Context + LlamaEngine プロバイダー（Sprint 5用）
     */
    class FactoryWithEngine(
        private val context: Context,
        private val engineProvider: () -> LlamaEngine?
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return ResourceViewModel(context.applicationContext, engineProvider) as T
        }
    }
}
