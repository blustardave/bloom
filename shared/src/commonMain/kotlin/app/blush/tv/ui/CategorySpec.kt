package app.blush.tv.ui

import org.jellyfin.sdk.model.api.BaseItemDto

/** A list of titles the server can page through: a Home row's title, and how to load any stretch of it (start, count). */
class CategorySpec(val title: String, val load: suspend (Int, Int) -> Pair<List<BaseItemDto>, Int>)
