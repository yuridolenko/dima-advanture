package com.dima

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.dima.ui.theme.DimaAdvantureTheme

class MainActivity : ComponentActivity() {
    // Объявляем переменную для нашей игры
    private lateinit var gameView: GameView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Инициализируем наш игровой класс
        gameView = GameView(this)

        // Устанавливаем GameView как основной и единственный контент экрана
        // Вместо загрузки из XML (layout), мы рисуем всё кодом
        setContentView(gameView)
    }

    // Когда пользователь возвращается в приложение
    override fun onResume() {
        super.onResume()
        gameView.resume() // Запускаем игровой поток
    }

    // Когда пользователь сворачивает приложение или ему звонят
    override fun onPause() {
        super.onPause()
        gameView.pause() // Останавливаем поток, чтобы не тратить батарею
    }
}

@Composable
fun Greeting(name: String, modifier: Modifier = Modifier) {
    Text(
        text = "Hello $name!",
        modifier = modifier
    )
}

@Preview(showBackground = true)
@Composable
fun GreetingPreview() {
    DimaAdvantureTheme {
        Greeting("Android")
    }
}