package com.alphawallet.app.util

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.Rect
import java.lang.Character.codePointAt
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/**
 * Blockies 头像生成工具。
 *
 * 该实现遵循以太坊常用的 Blockies 算法，通过输入地址生成可重复的几何头像。
 * 逻辑保持与原 Java 版本一致，但使用 Kotlin 对代码进行了整理，并补充了必要的注释。
 */
object Blockies {

    private const val SIZE = 8
    private val randSeed = LongArray(4)

    /**
     * 根据地址创建 Blockies 头像，默认缩放倍数为 16。
     */
    fun createIcon(address: String): Bitmap = createIcon(address, 16)

    /**
     * 根据地址创建 Blockies 头像，可指定缩放倍数。
     *
     * @param address 需要生成头像的地址
     * @param scale 缩放倍数，生成图片尺寸为 `SIZE * scale`
     */
    fun createIcon(address: String, scale: Int): Bitmap {
        seedRand(address)
        val color = createColor()
        val bgColor = createColor()
        val spotColor = createColor()
        return createCanvas(createImageData(), color, bgColor, spotColor, scale)
    }

    /**
     * 根据生成的数据绘制 Bitmap，并裁剪为圆形图像。
     */
    private fun createCanvas(
        imgData: DoubleArray,
        color: HSL,
        bgColor: HSL,
        spotColor: HSL,
        scale: Int,
    ): Bitmap {
        val width = sqrt(imgData.size.toDouble()).toInt()
        val canvasWidth = width * scale

        val bitmap = Bitmap.createBitmap(canvasWidth, canvasWidth, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        val background = toRGB(bgColor)
        val main = toRGB(color)
        val accent = toRGB(spotColor)

        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            color = background
        }
        canvas.drawRect(0f, 0f, canvasWidth.toFloat(), canvasWidth.toFloat(), paint)

        val cellPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
        imgData.forEachIndexed { index, value ->
            if (value > 0.0) {
                val row = index / width
                val col = index % width
                cellPaint.color = if (value == 1.0) main else accent
                val left = (col * scale).toFloat()
                val top = (row * scale).toFloat()
                canvas.drawRect(left, top, left + scale, top + scale, cellPaint)
            }
        }
        return getCroppedBitmap(bitmap)
    }

    /**
     * 生成 0~1 之间的伪随机数。
     */
    private fun rand(): Double {
        val t = (randSeed[0] xor (randSeed[0] shl 11)).toInt()
        randSeed[0] = randSeed[1]
        randSeed[1] = randSeed[2]
        randSeed[2] = randSeed[3]
        val newSeed = randSeed[3] xor (randSeed[3] ushr 19) xor t.toLong() xor (t ushr 8).toLong()
        randSeed[3] = newSeed
        return abs(randSeed[3].toDouble()) / Int.MAX_VALUE.toDouble()
    }

    /**
     * 随机生成一个 HSL 颜色。
     */
    private fun createColor(): HSL {
        val h = floor(rand() * 360.0)
        val s = rand() * 60.0 + 40.0
        val l = (rand() + rand() + rand() + rand()) * 25.0
        return HSL(h, s, l)
    }

    /**
     * 生成头像所需的像素矩阵数据。
     */
    private fun createImageData(): DoubleArray {
        val width = SIZE
        val dataWidth = ceil(width / 2.0).toInt()
        val mirrorWidth = width - dataWidth

        val data = DoubleArray(SIZE * SIZE)
        var index = 0

        repeat(SIZE) {
            val row = DoubleArray(dataWidth) { floor(rand() * 2.3) }
            val mirror = row.copyOfRange(0, mirrorWidth).also { it.reverseInPlace() }
            val fullRow = concat(row, mirror)
            fullRow.forEach { value ->
                data[index++] = value
            }
        }
        return data
    }

    /**
     * 合并两个 DoubleArray。
     */
    private fun concat(a: DoubleArray, b: DoubleArray): DoubleArray {
        val result = DoubleArray(a.size + b.size)
        System.arraycopy(a, 0, result, 0, a.size)
        System.arraycopy(b, 0, result, a.size, b.size)
        return result
    }

    /**
     * 使用地址字符串初始化伪随机种子。
     */
    private fun seedRand(seed: String) {
        randSeed.fill(0)
        val max = (Int.MAX_VALUE shl 1).toLong()
        val min = (Int.MIN_VALUE shl 1).toLong()

        seed.forEachIndexed { index, _ ->
            val key = index % 4
            var test = randSeed[key] shl 5
            if (test > max || test < min) {
                test = test.toInt().toLong()
            }
            val test2 = test - randSeed[key]
            randSeed[key] = test2 + codePointAt(seed, index)
        }

        for (i in randSeed.indices) {
            randSeed[i] = randSeed[i].toInt().toLong()
        }
    }

    /**
     * 将 HSL 颜色转换为 ARGB。
     */
    private fun toRGB(hsl: HSL): Int {
        val h = (hsl.h % 360.0) / 360.0
        val s = hsl.s / 100.0
        val l = hsl.l / 100.0

        val q = if (l < 0.5) {
            l * (1 + s)
        } else {
            l + s - s * l
        }
        val p = 2 * l - q

        val r = hueToRGB(p, q, h + 1.0 / 3.0)
        val g = hueToRGB(p, q, h)
        val b = hueToRGB(p, q, h - 1.0 / 3.0)

        return Color.rgb((r * 255).toInt(), (g * 255).toInt(), (b * 255).toInt())
    }

    /**
     * hueToRGB 辅助函数，用于 HSL -> RGB 转换。
     */
    private fun hueToRGB(p: Double, q: Double, hInput: Double): Double {
        var h = hInput
        if (h < 0) h += 1.0
        if (h > 1) h -= 1.0
        return when {
            h * 6 < 1 -> p + (q - p) * 6 * h
            h * 2 < 1 -> q
            h * 3 < 2 -> p + (q - p) * 6 * (2.0 / 3.0 - h)
            else -> p
        }.coerceIn(0.0, 1.0)
    }

    /**
     * 将生成的方形位图裁剪为圆形。
     */
    private fun getCroppedBitmap(bitmap: Bitmap): Bitmap {
        val output = Bitmap.createBitmap(bitmap.width, bitmap.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(output)

        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.GRAY
        }
        val rect = Rect(0, 0, bitmap.width, bitmap.height)

        canvas.drawARGB(0, 0, 0, 0)
        val radius = min(bitmap.width, bitmap.height) / 2f
        canvas.drawCircle(bitmap.width / 2f, bitmap.height / 2f, radius, paint)
        paint.xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_IN)
        canvas.drawBitmap(bitmap, rect, rect, paint)
        return output
    }

    /**
     * 简单的 HSL 颜色容器。
     */
    private data class HSL(val h: Double, val s: Double, val l: Double)
}

/**
 * DoubleArray 原地反转。
 */
private fun DoubleArray.reverseInPlace() {
    var start = 0
    var end = size - 1
    while (start < end) {
        val tmp = this[start]
        this[start] = this[end]
        this[end] = tmp
        start++
        end--
    }
}
