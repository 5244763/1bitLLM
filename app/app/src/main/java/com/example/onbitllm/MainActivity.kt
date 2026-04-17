package com.example.onbitllm

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.ui.Modifier
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.lifecycle.ViewModelProvider
import com.example.onbitllm.engine.EngineManager
import com.example.onbitllm.ui.screens.ChatScreen
import com.example.onbitllm.ui.theme.OneBitLLMTheme
import com.example.onbitllm.viewmodel.ChatViewModel

/**
 * メインActivity
 *
 * Sprint 5:
 * - ProcessLifecycleOwner でアプリのフォアグラウンド/バックグラウンドを監視
 * - ON_STOP でモデルをアンロード（メモリ解放）
 * - ON_START でモデルを再ロード（フォアグラウンド復帰時）
 * - ChatViewModel を ViewModelProvider.Factory 経由で Context を注入して生成
 */
class MainActivity : ComponentActivity() {

    private lateinit var chatViewModel: ChatViewModel
    private val appLifecycleObserver = AppLifecycleObserver()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 外部ストレージ(adb push先)から内部ストレージへモデルファイルを同期
        val engineManager = EngineManager(applicationContext)
        Thread { engineManager.syncModelsFromExternal() }.start()

        // ChatViewModel を Factory 経由で生成（Context注入のため）
        chatViewModel = ViewModelProvider(
            this,
            ChatViewModel.Factory(applicationContext)
        )[ChatViewModel::class.java]

        // アプリ全体のライフサイクルを監視（バックグラウンド/フォアグラウンド検知）
        ProcessLifecycleOwner.get().lifecycle.addObserver(appLifecycleObserver)

        enableEdgeToEdge()
        setContent {
            OneBitLLMTheme {
                ChatScreen(
                    viewModel = chatViewModel,
                    modifier = Modifier
                        .fillMaxSize()
                        .systemBarsPadding()
                )
            }
        }
    }

    /**
     * アプリ全体（全Activity）のライフサイクルを監視するオブザーバー。
     * ProcessLifecycleOwner を使用することで、
     * - ON_STOP: アプリが完全にバックグラウンドに移行した時
     * - ON_START: アプリがフォアグラウンドに戻った時
     * を検知する。
     */
    override fun onDestroy() {
        super.onDestroy()
        ProcessLifecycleOwner.get().lifecycle.removeObserver(appLifecycleObserver)
    }

    private inner class AppLifecycleObserver : DefaultLifecycleObserver {

        override fun onStop(owner: LifecycleOwner) {
            // アプリがバックグラウンドに移行: モデルをアンロードしてメモリ解放
            chatViewModel.onAppBackground()
        }

        override fun onStart(owner: LifecycleOwner) {
            // アプリがフォアグラウンドに復帰: 前回ロードしていたモデルを再ロード
            chatViewModel.onAppForeground()
        }
    }
}
