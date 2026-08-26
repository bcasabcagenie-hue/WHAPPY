package com.whappy.chat

/**
 * Keeps legacy WAPI groups recognisable while the current schema uses the
 * dedicated `groups` collection. Older Android builds stored some groups in
 * `conversations` without `conversationType=group`.
 */
internal object WapiGroupClassifier {
    fun isLegacyGroup(
        conversationType: String?,
        kind: String?,
        explicitGroup: Boolean?,
        memberCount: Int,
        hasAdminField: Boolean,
        title: String?,
        groupPhotoUrl: String?,
    ): Boolean =
        conversationType.equals("group", ignoreCase = true) ||
            kind.equals("group", ignoreCase = true) ||
            explicitGroup == true ||
            memberCount > 2 ||
            hasAdminField ||
            !title.isNullOrBlank() ||
            !groupPhotoUrl.isNullOrBlank()
}
