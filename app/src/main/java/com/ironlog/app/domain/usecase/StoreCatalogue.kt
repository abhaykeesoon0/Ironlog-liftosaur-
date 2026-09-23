package com.ironlog.app.domain.usecase

/**
 * What coins buy.
 *
 * The catalogue is code rather than a table: these are app content, shipped
 * with the build and identical for everyone, so storing them in the database
 * would mean seeding and migrating rows that only ever change when the app
 * changes. Only what a user owns is persisted.
 *
 * Two shelves are kept deliberately apart. Cosmetics change how the app looks
 * and nothing else. Widgets are convenience only -- an extra card on Home that
 * saves arithmetic -- and nothing needed to log a workout is ever behind a
 * price, because locking the actual job of the app behind a grind would be a
 * dark pattern rather than a game.
 */
object StoreCatalogue {

    enum class Category(val label: String, val blurb: String) {
        BACKGROUND("Backgrounds", "All dark. The app stays readable in a badly lit gym."),
        BADGE("Badges", "An emblem beside your name."),
        TITLE("Titles", "A line under your name on Home."),
        WIDGET("Widgets", "Extra cards on Home. Convenience only, never anything you need.")
    }

    data class Item(
        val id: String,
        val name: String,
        val detail: String,
        val price: Int,
        val category: Category
    )

    /** The background every account starts with, so the app is never colourless. */
    const val DEFAULT_BACKGROUND = "bg_slate"

    val ITEMS = listOf(
        // ---------------------------------------------------------- backgrounds
        Item(DEFAULT_BACKGROUND, "Slate", "The default. Cool grey-blue.", 0, Category.BACKGROUND),
        Item("bg_charcoal", "Charcoal", "Neutral, no colour cast.", 150, Category.BACKGROUND),
        Item("bg_navy", "Deep navy", "Blue-black, easy at night.", 200, Category.BACKGROUND),
        Item("bg_ink", "Near black", "Darkest of the set. Kind to an OLED battery.", 250, Category.BACKGROUND),
        Item("bg_plum", "Dark plum", "A warm shadow of purple.", 300, Category.BACKGROUND),
        Item("bg_forest", "Forest", "Green-black, like a gym with the lights down.", 350, Category.BACKGROUND),
        Item("bg_oxblood", "Oxblood", "The darkest red there is.", 400, Category.BACKGROUND),
        Item("bg_midnight", "Midnight", "Blue so deep it is nearly black.", 450, Category.BACKGROUND),

        // --------------------------------------------------------------- badges
        Item("badge_dumbbell", "Dumbbell", "The standard issue.", 100, Category.BADGE),
        Item("badge_flame", "Flame", "For a streak worth showing.", 200, Category.BADGE),
        Item("badge_bolt", "Bolt", "Fast work.", 250, Category.BADGE),
        Item("badge_crown", "Crown", "The expensive one.", 500, Category.BADGE),
        Item("badge_star", "Star", "Earned, not given.", 150, Category.BADGE),
        Item("badge_shield", "Shield", "For the consistent.", 300, Category.BADGE),
        Item("badge_trophy", "Trophy", "For a record collector.", 400, Category.BADGE),
        Item("badge_diamond", "Diamond", "The one people ask about.", 750, Category.BADGE),

        // --------------------------------------------------------------- titles
        Item("title_iron", "Iron Will", "For the ones who turned up anyway.", 150, Category.TITLE),
        Item("title_grinder", "The Grinder", "Volume is a virtue.", 250, Category.TITLE),
        Item("title_plates", "Plate Collector", "Every record, kept.", 350, Category.TITLE),
        Item("title_legend", "Gym Legend", "Self-awarded, as all the best ones are.", 800, Category.TITLE),

        // -------------------------------------------------------------- widgets
        Item(
            "widget_plates", "Plate calculator",
            "Work out what goes on each side of the bar, from Home.",
            300, Category.WIDGET
        ),
        Item(
            "widget_1rm", "1RM estimator",
            "Turn a weight and a rep count into an estimated one-rep max.",
            250, Category.WIDGET
        )
    )

    fun byId(id: String): Item? = ITEMS.firstOrNull { it.id == id }

    fun inCategory(category: Category): List<Item> = ITEMS.filter { it.category == category }

    /**
     * Whether an item needs buying at all. The default background is listed so
     * it can be chosen again after switching, but it was never bought.
     */
    fun isFree(id: String): Boolean = byId(id)?.price == 0
}
