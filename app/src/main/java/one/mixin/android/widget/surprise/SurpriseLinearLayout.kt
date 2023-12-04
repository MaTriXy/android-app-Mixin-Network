package one.mixin.android.widget.surprise

import android.content.Context
import android.graphics.Canvas
import android.util.AttributeSet
import android.widget.LinearLayout

class SurpriseLinearLayout(context: Context, attrs: AttributeSet) : LinearLayout(context, attrs) {
    init {
        setWillNotDraw(false)
    }

    private val snowflakesEffect = SnowflakesEffect()
    private val fireworksEffect = FireworksEffect()

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (christmas) {
            snowflakesEffect.onDraw(this, canvas)
        } else if (lunarNewYear) {
            fireworksEffect.onDraw(this, canvas)
        }
    }

    companion object {
        // Christmas
        val christmas = System.currentTimeMillis() in 1703347200000..1704297600000

        // Lunar New Year
        val lunarNewYear = System.currentTimeMillis() in 1707408000000..1708099200000
    }
}
