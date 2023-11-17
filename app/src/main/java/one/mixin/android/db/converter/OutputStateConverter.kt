package one.mixin.android.db.converter

import androidx.room.TypeConverter
import java.util.Locale
import one.mixin.android.vo.safe.OutputState

class OutputStateConverter {
    @TypeConverter
    fun toOutputState(value: String): OutputState {
        return OutputState.valueOf(value.lowercase(Locale.US))
    }

    @TypeConverter
    fun fromOutputState(state: OutputState): String {
        return state.name.lowercase(Locale.US)
    }
}
