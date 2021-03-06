package cm.aptoide.aptoideviews.skeleton

import android.graphics.Color
import cm.aptoide.aptoideviews.skeleton.mask.Border
import cm.aptoide.aptoideviews.skeleton.mask.Shape

internal data class SkeletonViewPreferences(
    var shape: Shape = Shape.Rect(Color.parseColor("#EDEEF2"), 0),
    var border: Border = Border(0, Color.WHITE))