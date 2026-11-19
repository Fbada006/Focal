package io.disruption.focal.ui.blocking

/**
 * What the Swift side supplies for Apple's Screen Time app picker (T15). Swift presents the
 * `FamilyActivityPicker` and serialises the chosen tokens; Kotlin stores them on the Block List.
 */
interface IosScreenTimePicker {
    /** Present Apple's picker for the Block List with [blockListId]. */
    fun present(blockListId: String)
}

/**
 * The bridge between Compose and Swift for the iOS Apps tab (T15). The Swift app sets [picker] at
 * launch; [saveSelection] is set once Koin is up and writes the tokens to the Block List.
 */
object IosScreenTimeBridge {
    var picker: IosScreenTimePicker? = null

    var saveSelection: ((blockListId: String, apps: Set<String>, categories: Set<String>) -> Unit)? = null

    fun present(blockListId: String) {
        picker?.present(blockListId)
    }

    /** Called by Swift with the serialised `ApplicationToken`s and `ActivityCategoryToken`s. */
    fun selectionMade(blockListId: String, apps: Set<String>, categories: Set<String>) {
        saveSelection?.invoke(blockListId, apps, categories)
    }
}
