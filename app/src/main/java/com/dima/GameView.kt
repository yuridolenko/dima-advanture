package com.dima

import android.content.Context
import android.graphics.*
import android.view.MotionEvent
import android.view.SurfaceView
import java.util.*

// Класс для летающего пива
data class FlyingBeer(val rect: RectF, val startY: Float, var angle: Double)

class GameView(context: Context) : SurfaceView(context), Runnable {

    private var thread: Thread? = null
    private var isPlaying = false
    private val paint = Paint()
    private val textPaint = Paint()

    // Экран
    private var screenW = 0
    private var screenH = 0

    // Игрок
    private var playerX = 0f
    private var playerY = 0f
    private var playerSpeedY = 0f
    private val gravity = 2.2f
    private val jumpForce = -55f
    private var isHappy = false
    private var happyTimer = 0L

    // Геймплей и счет
    private var beerCount = 0
    private var vodkaCount = 0
    private var frameCount = 0

    // Спрайты
    private val bmpPlayer = BitmapFactory.decodeResource(resources, R.drawable.player)
    private val bmpPlayerHappy = BitmapFactory.decodeResource(resources, R.drawable.player_happy)
    private val bmpBeer = BitmapFactory.decodeResource(resources, R.drawable.beer)
    private val bmpVodka = BitmapFactory.decodeResource(resources, R.drawable.vodka)

    // Списки объектов
    private val platforms = mutableListOf<RectF>()
    private val regularBeers = mutableListOf<RectF>()
    private val flyingBeers = mutableListOf<FlyingBeer>()

    init {
        textPaint.color = Color.WHITE
        textPaint.textSize = 60f
        textPaint.typeface = Typeface.DEFAULT_BOLD
        textPaint.setShadowLayer(10f, 0f, 0f, Color.BLACK)
    }

    override fun run() {
        while (isPlaying) {
            update()
            draw()
            control()
        }
    }

    private fun update() {
        frameCount++

        // Сброс состояния радости
        if (isHappy && System.currentTimeMillis() > happyTimer) isHappy = false

        // 1. Физика игрока
        playerSpeedY += gravity
        playerY += playerSpeedY

        // 2. Генерация платформ и обычного пива
        if (platforms.isEmpty() || platforms.last().right < screenW + 500) {
            val gap = (400..700).random().toFloat()
            val platW = (400..800).random().toFloat()
            val platTop = (screenH * 0.6f).toInt()..(screenH * 0.85f).toInt()
            val randomY = platTop.random().toFloat()

            val newPlat = RectF(screenW.toFloat() + gap, randomY, screenW.toFloat() + gap + platW, screenH.toFloat())
            platforms.add(newPlat)

            // Шанс появления обычного пива на платформе
            if (Random().nextInt(10) > 4) {
                regularBeers.add(RectF(newPlat.centerX() - 40, newPlat.top - 90, newPlat.centerX() + 40, newPlat.top - 10))
            }
        }

        // 3. Генерация летающего пива (раз в 180 кадров)
        if (frameCount % 180 == 0) {
            val startY = screenH * (0.2f + Random().nextFloat() * (0.5f - 0.2f))
            flyingBeers.add(FlyingBeer(
                RectF(screenW.toFloat() + 100, startY, screenW.toFloat() + 180, startY + 80),
                startY, 0.0
            ))
        }

        // 4. Движение и коллизии платформ
        val platSpeed = 15f
        val pIter = platforms.iterator()
        while (pIter.hasNext()) {
            val p = pIter.next()
            p.left -= platSpeed
            p.right -= platSpeed

            // Приземление
            if (playerX + 80 > p.left && playerX < p.right && playerY + 100 > p.top && playerY + 100 < p.top + 60 && playerSpeedY > 0) {
                playerY = p.top - 100
                playerSpeedY = 0f
            }
            if (p.right < 0) pIter.remove()
        }

        // 5. Движение обычного пива
        val bIter = regularBeers.iterator()
        while (bIter.hasNext()) {
            val b = bIter.next()
            b.left -= platSpeed
            b.right -= platSpeed
            if (RectF.intersects(RectF(playerX, playerY, playerX+100, playerY+100), b)) {
                beerCount++; isHappy = true; happyTimer = System.currentTimeMillis() + 2000
                bIter.remove()
            } else if (b.right < 0) bIter.remove()
        }

        // 6. Движение ЛЕТАЮЩЕГО пива (Синусоида)
        val fIter = flyingBeers.iterator()
        while (fIter.hasNext()) {
            val fb = fIter.next()
            fb.angle += 0.1
            val wave = Math.sin(fb.angle).toFloat() * 60f // Амплитуда

            fb.rect.left -= 18f // Летит чуть быстрее
            fb.rect.right -= 18f
            fb.rect.top = fb.startY + wave
            fb.rect.bottom = fb.startY + wave + 80f

            if (RectF.intersects(RectF(playerX, playerY, playerX+100, playerY+100), fb.rect)) {
                vodkaCount++;
                isHappy = true; happyTimer = System.currentTimeMillis() + 2000
                fIter.remove()
            } else if (fb.rect.right < 0) fIter.remove()
        }

        // 7. Смерть (падение)
        if (playerY > screenH) resetGame()
    }

    private fun resetGame() {
        beerCount = 0
        playerY = 0f
        playerSpeedY = 0f
        flyingBeers.clear()
        // onSizeChanged создаст стартовую платформу заново при необходимости
    }

    private fun draw() {
        if (holder.surface.isValid) {
            // Мы объявляем canvas прямо здесь, получая его из SurfaceHolder
            val canvas = holder.lockCanvas()

            if (canvas != null) {
                try {
                    // 1. Отрисовка фона
                    canvas.drawColor(Color.parseColor("#4FC3F7"))

                    // 2. Отрисовка платформ
                    paint.color = Color.parseColor("#795548")
                    for (p in platforms) canvas.drawRect(p, paint)

                    // 3. Отрисовка обычного пива
                    for (b in regularBeers) canvas.drawBitmap(bmpBeer, null, b, null)

                    // 4. Отрисовка летающего пива
                    for (fb in flyingBeers) canvas.drawBitmap(bmpVodka, null, fb.rect, null)

                    // 5. Отрисовка игрока
                    val bmp = if (isHappy) bmpPlayerHappy else bmpPlayer
                    val playerRect = RectF(playerX, playerY, playerX + 100, playerY + 100)
                    canvas.drawBitmap(bmp, null, playerRect, null)

                    // 6. Отрисовка текста (счётчик)
                    canvas.drawText("Выжрано пива $beerCount, выжрано водки: $vodkaCount", 50f, 100f, textPaint)

                } finally {
                    // Очень важно: разблокировать canvas, даже если произошла ошибка
                    holder.unlockCanvasAndPost(canvas)
                }
            }
        }
    }

    private fun control() { Thread.sleep(17) }

    override fun onTouchEvent(event: MotionEvent?): Boolean {
        if (event?.action == MotionEvent.ACTION_DOWN && playerSpeedY == 0f) {
            playerSpeedY = jumpForce
        }
        return true
    }

    fun pause() { isPlaying = false; thread?.join() }
    fun resume() { isPlaying = true; thread = Thread(this); thread?.start() }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        screenW = w; screenH = h
        val groundY = h * 0.75f
        platforms.clear()
        platforms.add(RectF(0f, groundY, w.toFloat(), h.toFloat()))
        playerX = 150f; playerY = groundY - 100f
    }
}