package com.moez.QKSMS.feature.design

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.view.View
import kotlin.math.min

/**
 * True wireframe of each experience in Settings. Even in grayscale the six
 * layouts remain distinct: rail, top search, round cockpit, mosaic, folders, stories.
 */
class ExperienceBlueprint(context: Context, private val style: Experience) : View(context) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private fun fill(canvas: Canvas, l: Float, t: Float, r: Float, b: Float,
                     color: Int, radius: Float = 5f) {
        paint.style = Paint.Style.FILL
        paint.color = color
        canvas.drawRoundRect(RectF(l,t,r,b),radius,radius,paint)
    }
    private fun circle(canvas: Canvas,x: Float,y: Float,r: Float,c: Int) {
        paint.style=Paint.Style.FILL;paint.color=c;canvas.drawCircle(x,y,r,paint)
    }
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0 || h <= 0) return
        val sx = w / 150f
        val sy = h / 85f
        canvas.save()
        canvas.scale(sx,sy)
        fill(canvas,0f,0f,150f,85f,if (style.darkHeader) Color.rgb(29,27,49) else Color.WHITE,10f)
        val ink=if(style.darkHeader)Color.rgb(217,205,247) else Color.rgb(75,94,112)
        val muted=if(style.darkHeader)Color.rgb(75,65,105) else Color.rgb(224,230,237)
        when(style){
            Experience.MICROSOFT -> {
                fill(canvas,121f,0f,150f,85f,Color.rgb(224,236,251),0f)
                for (i in 0..4) fill(canvas,129f,11f+i*14f,143f,20f+i*14f,
                    if(i==1)style.accent else Color.rgb(173,195,224),3f)
                fill(canvas,10f,8f,84f,15f,ink,2f)
                fill(canvas,10f,22f,60f,42f,muted)
                fill(canvas,64f,22f,114f,42f,muted)
                for(i in 0..2)fill(canvas,10f+i*36,49f,43f+i*36,76f,muted)
            }
            Experience.APPLE -> {
                fill(canvas,13f,8f,64f,17f,ink,2f)
                fill(canvas,10f,23f,140f,33f,muted,8f)
                for(i in 0..3)circle(canvas,26f+i*32,43f,5f,if(i==1)style.accent else muted)
                for(i in 0..2){
                    circle(canvas,19f,60f+i*9f,4f,muted)
                    fill(canvas,30f,57f+i*9,125f,60f+i*9,ink,2f)
                }
            }
            Experience.AVATR -> {
                paint.style=Paint.Style.STROKE;paint.strokeWidth=3f;paint.color=style.accent
                canvas.drawCircle(75f,43f,25f,paint)
                paint.style=Paint.Style.FILL
                fill(canvas,60f,41f,90f,45f,ink,2f)
                for(i in 0..2){
                    fill(canvas,7f,10f+i*26,42f,26f+i*26,muted)
                    fill(canvas,108f,10f+i*26,143f,26f+i*26,muted)
                }
                fill(canvas,51f,6f,99f,11f,style.accent,2f)
            }
            Experience.PINTEREST -> {
                fill(canvas,8f,6f,142f,17f,muted,9f)
                fill(canvas,9f,23f,74f,75f,style.accent,8f)
                fill(canvas,80f,23f,141f,47f,Color.rgb(245,207,150),7f)
                fill(canvas,80f,52f,141f,75f,Color.rgb(106,157,165),7f)
                circle(canvas,42f,42f,7f,Color.WHITE)
            }
            Experience.TELEGRAM -> {
                fill(canvas,0f,0f,150f,19f,style.accent,0f)
                fill(canvas,8f,26f,141f,36f,muted,9f)
                for(i in 0..3)fill(canvas,10f+i*35,41f,41f+i*35,47f,
                    if(i==0)style.accent else muted,3f)
                for(i in 0..2){
                    circle(canvas,19f,56f+i*10,5f,style.accent)
                    fill(canvas,30f,53f+i*10,120f,57f+i*10,ink,2f)
                }
            }
            Experience.META -> {
                fill(canvas,11f,8f,60f,14f,ink,3f)
                for(i in 0..4){
                    circle(canvas,19f+i*28,32f,10f,if(i%2==0)style.accent else muted)
                    fill(canvas,10f+i*28,45f,27f+i*28,47f,muted,2f)
                }
                for(i in 0..1)fill(canvas,10f+i*68,54f,73f+i*68,66f,muted,5f)
                fill(canvas,0f,72f,150f,85f,Color.rgb(227,218,250),0f)
                for(i in 0..4)circle(canvas,18f+i*28,79f,3f,style.accent)
            }
        }
        canvas.restore()
    }
}
