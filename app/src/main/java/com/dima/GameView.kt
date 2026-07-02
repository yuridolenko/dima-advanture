package com.dima

import android.content.Context
import android.graphics.*
import android.view.MotionEvent
import android.view.SurfaceView
import java.util.*

// Класс для летающего пива
data class FlyingBeer(val rect: RectF, val startY: Float, var angle: Double)

private enum class BossState { NONE, SLIDING, FROZEN, ACTIVE }

class GameView(context: Context) : SurfaceView(context), Runnable {

    private var thread: Thread? = null
    private var isPlaying = false
    private val paint = Paint()
    private val textPaint = Paint()
    private val barTextPaint = Paint()
    private val bossTextPaint = Paint()
    private val bossWordPaint = Paint()

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

    // Опьянение: пиво = 2 очка, водка = 3 очка
    private val maxDrunkScore = 1f // TODO: тестовое значение, вернуть 100

    // Босс
    private var bossTriggered = false
    private var solidPlatformSpawned = false
    private var solidPlatform: RectF? = null
    private var scrollStopped = false
    private var bossState = BossState.NONE
    private var bossFreezeStart = 0L
    private val bossFreezeDuration = 5000L
    private val bossSize = 500f // в 5 раз больше игрока (100)
    private val bossSlideSpeed = 6f
    private var bossX = 0f
    private var bossY = 0f
    private var bossTargetX = 0f
    private var bossTargetY = 0f
    private var bossMoveTimer = 0
    private var bossShootTimer = 0
    private val bossWordSize = 100f // как игрок
    private val bossWordShots = mutableListOf<RectF>()

    // Спрайты
    private val bmpPlayer = BitmapFactory.decodeResource(resources, R.drawable.player)
    private val bmpPlayerHappy = BitmapFactory.decodeResource(resources, R.drawable.player_happy)
    private val bmpBeer = BitmapFactory.decodeResource(resources, R.drawable.beer)
    private val bmpVodka = BitmapFactory.decodeResource(resources, R.drawable.vodka)
    private val bmpEmptyBottle = BitmapFactory.decodeResource(resources, R.drawable.empty_bottle)
    private val bmpBoss = BitmapFactory.decodeResource(resources, R.drawable.boss)

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

        barTextPaint.color = Color.WHITE
        barTextPaint.textSize = 40f
        barTextPaint.typeface = Typeface.DEFAULT_BOLD
        barTextPaint.textAlign = Paint.Align.CENTER

        bossTextPaint.color = Color.RED
        bossTextPaint.textSize = 160f
        bossTextPaint.typeface = Typeface.DEFAULT_BOLD
        bossTextPaint.textAlign = Paint.Align.CENTER

        bossWordPaint.color = Color.WHITE
        bossWordPaint.textSize = 132f
        bossWordPaint.typeface = Typeface.DEFAULT_BOLD
        bossWordPaint.textAlign = Paint.Align.CENTER
    }

    private fun drunkScore(): Int = beerCount * 2 + vodkaCount * 3

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
        val prevPlayerBottom = playerY + 100
        playerSpeedY += gravity
        playerY += playerSpeedY

        // 2. Полет пуль (вправо)
        val bulletIter = bullets.iterator()
        while (bulletIter.hasNext()) {
            val b = bulletIter.next()
            b.offset(25f, 0f)
            if (b.left > screenW) bulletIter.remove()
        }

        // Проверка на приближение босса
        if (!bossTriggered && drunkScore() >= maxDrunkScore) bossTriggered = true

        // 3. Генерация контента
        if (!bossTriggered) {
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
        } else if (!solidPlatformSpawned) {
            // Вместо случайных платформ - одна цельная платформа во весь экран
            val solidWidth = screenW * 1.5f
            val solid = RectF(screenW.toFloat(), screenH * 0.75f, screenW.toFloat() + solidWidth, screenH.toFloat())
            platforms.add(solid)
            solidPlatform = solid
            solidPlatformSpawned = true
        }

        // 4. Движение платформ и коллизии
        val worldSpeed = 15f
        if (!scrollStopped) {
            platforms.forEach { it.offset(-worldSpeed, 0f) }
            regularBeers.forEach { it.offset(-worldSpeed, 0f) }
        }

        val playerBottom = playerY + 100
        platforms.forEach { p ->
            // Пересечение по X и пересечение верха платформы отрезком [prevPlayerBottom, playerBottom] -
            // ловит приземление независимо от скорости падения, без "проваливания" сквозь пол
            if (playerX + 80 > p.left && playerX < p.right && playerSpeedY > 0 && prevPlayerBottom <= p.top && playerBottom >= p.top) {
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

        // 7. Логика босса
        when (bossState) {
            BossState.NONE -> {
                if (bossTriggered) {
                    val solid = solidPlatform
                    if (solid != null && solid.left <= 0f && solid.right >= screenW.toFloat()) {
                        scrollStopped = true
                        bossState = BossState.SLIDING
                        bossX = screenW.toFloat()
                        bossY = solid.top - bossSize
                    }
                }
            }
            BossState.SLIDING -> {
                val targetX = screenW - bossSize
                bossX = (bossX - bossSlideSpeed).coerceAtLeast(targetX)
                if (bossX <= targetX) {
                    bossState = BossState.FROZEN
                    bossFreezeStart = System.currentTimeMillis()
                }
            }
            BossState.FROZEN -> {
                if (System.currentTimeMillis() - bossFreezeStart >= bossFreezeDuration) {
                    bossState = BossState.ACTIVE
                    bossTargetX = bossX
                    bossTargetY = bossY
                    bossMoveTimer = 0
                    bossShootTimer = 90
                }
            }
            BossState.ACTIVE -> {
                val platformTop = solidPlatform?.top ?: (screenH * 0.75f)
                val minX = screenW / 2f
                val maxX = screenW - bossSize
                val minY = screenH * 0.05f
                val maxY = platformTop - bossSize

                bossMoveTimer--
                if (bossMoveTimer <= 0) {
                    bossTargetX = minX + Math.random().toFloat() * (maxX - minX)
                    bossTargetY = minY + Math.random().toFloat() * (maxY - minY)
                    bossMoveTimer = (60..120).random()
                }
                bossX += (bossTargetX - bossX) * 0.04f
                bossY += (bossTargetY - bossY) * 0.04f

                bossShootTimer--
                if (bossShootTimer <= 0) {
                    val shotY = bossY + bossSize / 2f - bossWordSize / 2f
                    bossWordShots.add(RectF(bossX, shotY, bossX + bossWordSize, shotY + bossWordSize))
                    bossShootTimer = (90..180).random()
                }
            }
        }

        // Полёт слов босса ("алкаш") влево
        val wordIter = bossWordShots.iterator()
        while (wordIter.hasNext()) {
            val w = wordIter.next()
            w.offset(-20f, 0f)
            if (w.right < 0) wordIter.remove()
        }

        if (playerY > screenH) resetGame()
    }

    private fun resetGame() {
        beerCount = 0; vodkaCount = 0; playerY = 0f; playerSpeedY = 0f; bullets.clear(); flyingVodka.clear()
        bossTriggered = false; solidPlatformSpawned = false; solidPlatform = null; scrollStopped = false
        bossState = BossState.NONE; bossX = 0f; bossY = 0f; bossWordShots.clear()
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
                    drawDrunkBar(canvas)
                    drawBossSequence(canvas)

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

    private fun drawDrunkBar(canvas: Canvas) {
        val barWidth = screenW / 3f
        val barLeft = (screenW - barWidth) / 2f
        val barTop = 30f
        val barHeight = 70f
        val barRight = barLeft + barWidth
        val barBottom = barTop + barHeight
        val barRect = RectF(barLeft, barTop, barRight, barBottom)

        val progress = (drunkScore() / maxDrunkScore).coerceIn(0f, 1f)

        // Подложка бара
        paint.style = Paint.Style.FILL
        paint.color = Color.argb(160, 20, 20, 20)
        canvas.drawRoundRect(barRect, 14f, 14f, paint)

        // Заполнение (синее)
        if (progress > 0f) {
            paint.color = Color.parseColor("#2196F3")
            canvas.drawRoundRect(RectF(barLeft, barTop, barLeft + barWidth * progress, barBottom), 14f, 14f, paint)
        }

        // Рамка бара
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 4f
        paint.color = Color.WHITE
        canvas.drawRoundRect(barRect, 14f, 14f, paint)
        paint.style = Paint.Style.FILL

        // Подпись поверх бара
        canvas.drawText("Опьянение", barRect.centerX(), barRect.centerY() + 12f, barTextPaint)
    }

    private fun drawBossSequence(canvas: Canvas) {
        if (bossState == BossState.NONE) return

        // Спрайт босса
        val bossRect = RectF(bossX, bossY, bossX + bossSize, bossY + bossSize)
        canvas.drawBitmap(bmpBoss, null, bossRect, null)

        // Драматично мигающая надпись "БОСС", пока босс замер после появления
        if (bossState == BossState.FROZEN) {
            val elapsed = System.currentTimeMillis() - bossFreezeStart
            val blinkOn = (elapsed / 250) % 2 == 0L
            if (blinkOn) {
                canvas.drawText("БОСС", screenW / 2f, screenH / 2f, bossTextPaint)
            }
        }

        // Слова "алкаш", летящие от босса влево
        bossWordShots.forEach { canvas.drawText("алкаш", it.centerX(), it.centerY() + 14f, bossWordPaint) }
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