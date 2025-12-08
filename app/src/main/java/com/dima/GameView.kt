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

    private var screenW = 0
    private var screenH = 0

    // Игрок
    private var playerX = 150f
    private var playerY = 0f
    private var playerSpeedY = 0f
    private val gravity = 2.5f
    private val jumpForce = -60f
    private var isHappy = false
    private var happyTimer = 0L

    // Геймплей
    private var beerCount = 0
    private var vodkaCount = 0
    private var frameCount = 0

    // Спрайты
    private val bmpPlayer = BitmapFactory.decodeResource(resources, R.drawable.player)
    private val bmpPlayerHappy = BitmapFactory.decodeResource(resources, R.drawable.player_happy)
    private val bmpBeer = BitmapFactory.decodeResource(resources, R.drawable.beer)
    private val bmpVodka = BitmapFactory.decodeResource(resources, R.drawable.vodka)
    private val bmpEmptyBottle = BitmapFactory.decodeResource(resources, R.drawable.empty_bottle)

    // Объекты
    private val platforms = mutableListOf<RectF>()
    private val regularBeers = mutableListOf<RectF>()
    private val flyingVodka = mutableListOf<FlyingBeer>()
    private val bullets = mutableListOf<RectF>()

    // Интерфейс (Кнопки)
    private var btnJump = RectF()
    private var btnShoot = RectF()

    init {
        textPaint.color = Color.WHITE
        textPaint.textSize = 50f
        textPaint.typeface = Typeface.DEFAULT_BOLD
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
        if (isHappy && System.currentTimeMillis() > happyTimer) isHappy = false

        // 1. Физика игрока
        playerSpeedY += gravity
        playerY += playerSpeedY

        // 2. Полет пуль (вправо)
        val bulletIter = bullets.iterator()
        while (bulletIter.hasNext()) {
            val b = bulletIter.next()
            b.offset(25f, 0f)
            if (b.left > screenW) bulletIter.remove()
        }

        // 3. Генерация контента
        if (platforms.isEmpty() || platforms.last().right < screenW + 500) {
            val gap = (400..700).random().toFloat()
            val platW = (400..800).random().toFloat()
            val platY = (screenH * 0.6f).toInt()..(screenH * 0.85f).toInt()
            val newPlat = RectF(screenW.toFloat() + gap, platY.random().toFloat(), screenW.toFloat() + gap + platW, screenH.toFloat())
            platforms.add(newPlat)

            if (Random().nextInt(10) > 5) {
                regularBeers.add(RectF(newPlat.centerX() - 40, newPlat.top - 90, newPlat.centerX() + 40, newPlat.top - 10))
            }
        }

        // Генерация летающего пива (раз в 180 кадров)
        if (frameCount % 180 == 0) {
            val startY = (screenH * 0.2f).toInt()..(screenH * 0.5f).toInt()
            val y = startY.random().toFloat()
            flyingVodka.add(FlyingBeer(RectF(screenW.toFloat() + 100, y, screenW.toFloat() + 180, y + 80), y, 0.0))
        }

        // 4. Движение платформ и коллизии
        val worldSpeed = 15f
        platforms.forEach { it.offset(-worldSpeed, 0f) }
        regularBeers.forEach { it.offset(-worldSpeed, 0f) }

        platforms.forEach { p ->
            if (playerX + 80 > p.left && playerX < p.right && playerY + 100 > p.top && playerY + 100 < p.top + 60 && playerSpeedY > 0) {
                playerY = p.top - 100
                playerSpeedY = 0f
            }
        }

        // 5. Сбор обычного пива
        val beerIter = regularBeers.iterator()
        while (beerIter.hasNext()) {
            if (RectF.intersects(RectF(playerX, playerY, playerX + 100, playerY + 100), beerIter.next())) {
                beerCount++; isHappy = true; happyTimer = System.currentTimeMillis() + 2000
                beerIter.remove()
            }
        }

        // 6. Логика ЛЕТАЮЩЕГО пива
        val flyIter = flyingVodka.iterator()
        while (flyIter.hasNext()) {
            val fb = flyIter.next()
            fb.angle += 0.1
            val offset = Math.sin(fb.angle).toFloat() * 60f

            fb.rect.offset(-18f, 0f) // Летит влево
            fb.rect.top = fb.startY + offset
            fb.rect.bottom = fb.startY + offset + 80f

            // Сбор летуна игроком
            if (RectF.intersects(RectF(playerX, playerY, playerX + 100, playerY + 100), fb.rect)) {
                vodkaCount++; isHappy = true; happyTimer = System.currentTimeMillis() + 2000
                flyIter.remove()
            } else if (fb.rect.right < 0) flyIter.remove()
        }

        if (playerY > screenH) resetGame()
    }

    private fun resetGame() {
        beerCount = 0; playerY = 0f; playerSpeedY = 0f; bullets.clear(); flyingVodka.clear()
    }

    private fun draw() {
        if (holder.surface.isValid) {
            val canvas = holder.lockCanvas()
            if (canvas != null) {
                try {
                    canvas.drawColor(Color.parseColor("#4FC3F7"))

                    // Платформы
                    paint.color = Color.parseColor("#795548")
                    platforms.forEach { canvas.drawRect(it, paint) }

                    // Обычное и летающее пиво
                    regularBeers.forEach { canvas.drawBitmap(bmpBeer, null, it, null) }
                    flyingVodka.forEach { canvas.drawBitmap(bmpVodka, null, it.rect, null) }

                    // Пули
                    bullets.forEach { canvas.drawBitmap(bmpEmptyBottle, null, it, null) }

                    // Игрок
                    val bmp = if (isHappy) bmpPlayerHappy else bmpPlayer
                    canvas.drawBitmap(bmp, null, RectF(playerX, playerY, playerX + 100, playerY + 100), null)

                    // UI
                    drawUI(canvas)
                    canvas.drawText("Выжрано пива: $beerCount, выжрано водки: $vodkaCount", 50f, 80f, textPaint)

                } finally {
                    holder.unlockCanvasAndPost(canvas)
                }
            }
        }
    }

    private fun drawUI(canvas: Canvas) {
        paint.color = Color.argb(120, 255, 255, 255)
        canvas.drawRoundRect(btnJump, 20f, 20f, paint)
        canvas.drawText("JUMP", btnJump.centerX() - 60, btnJump.centerY() + 20, textPaint)

        canvas.drawRoundRect(btnShoot, 20f, 20f, paint)
        canvas.drawText("SHOOT", btnShoot.centerX() - 70, btnShoot.centerY() + 20, textPaint)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.action == MotionEvent.ACTION_DOWN) {
            if (btnJump.contains(event.x, event.y) && playerSpeedY == 0f) playerSpeedY = jumpForce
            if (btnShoot.contains(event.x, event.y)) {
                bullets.add(RectF(playerX + 100, playerY + 20, playerX + 160, playerY + 80))
            }
        }
        return true
    }

    private fun control() { Thread.sleep(17) }
    fun pause() { isPlaying = false; thread?.join() }
    fun resume() { isPlaying = true; thread = Thread(this); thread?.start() }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        screenW = w; screenH = h
        btnJump = RectF(w - 250f, h - 350f, w - 50f, h - 210f)
        btnShoot = RectF(w - 250f, h - 180f, w - 50f, h - 40f)
        platforms.add(RectF(0f, h * 0.75f, w.toFloat(), h.toFloat()))
        playerY = h * 0.75f - 100f
    }
}