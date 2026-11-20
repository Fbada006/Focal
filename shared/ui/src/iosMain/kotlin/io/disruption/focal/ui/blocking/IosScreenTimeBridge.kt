package io.disruption.focal.ui.blocking

/**
 * What the Swift side supplies for Apple's Screen Time app picker (T15). Swift presents the
 * `FamilyActivityPicker` and serialises the chosen tokens; Kotlin stores them on the Block List.
 */
interface IosScreenTimePicker {
    /**
     * Present Apple's picker for the Block List with [blockListId], starting from the serialised
     * tokens it already holds, so Done without changes keeps them.
     */
    fun present(blockListId: String, apps: Set<String>, categories: Set<String>)
}

/**
 * The bridge between Compose and Swift for the iOS Apps tab (T15). The Swift app sets [picker] at
 * launch; the open Block List editor sets [onSelection] to save the tokens and show any notice.
 */
object IosScreenTimeBridge {
    var picker: IosScreenTimePicker? = null

    var onSelection: ((blockListId: String, apps: Set<String>, categories: Set<String>) -> Unit)? = null

    fun present(blockListId: String, apps: Set<String>, categories: Set<String>) {
        picker?.present(blockListId, apps, categories)
    }

    /** Called by Swift with the serialised `ApplicationToken`s and `ActivityCategoryToken`s. */
    fun selectionMade(blockListId: String, apps: Set<String>, categories: Set<String>) {
        onSelection?.invoke(blockListId, apps, categories)
    }
}
