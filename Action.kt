package com.jarvis.assistant.executor

enum class Cap { APPS, DEVICE, ACCESS, COMMS, MEDIA, NONE }

/** The ONLY things the AI may ask for. Anything else is dropped by the Planner. */
enum class ActionType(val cap: Cap) {
    OPEN_APP(Cap.APPS), OPEN_URL(Cap.APPS), YOUTUBE_SEARCH(Cap.APPS), OPEN_SETTINGS(Cap.APPS),
    FLASHLIGHT(Cap.DEVICE), VOLUME(Cap.DEVICE),
    GO_BACK(Cap.ACCESS), GO_HOME(Cap.ACCESS), SCROLL(Cap.ACCESS), TAP_TEXT(Cap.ACCESS),
    MEDIA(Cap.MEDIA),
    DIAL(Cap.COMMS), SMS_DRAFT(Cap.COMMS),
    WEB_SEARCH(Cap.NONE), WEATHER(Cap.NONE), ADD_NOTE(Cap.NONE), ADD_TODO(Cap.NONE);

    companion object {
        fun fromName(s: String): ActionType? = entries.firstOrNull { it.name.equals(s.trim(), ignoreCase = true) }
    }
}

data class Action(val type: ActionType, val arg: String = "", val arg2: String = "")

data class Outcome(val ok: Boolean, val msg: String)

fun Action.describe(): String = when (type) {
    ActionType.OPEN_APP -> "Open app: $arg"
    ActionType.OPEN_URL -> "Open link: $arg"
    ActionType.YOUTUBE_SEARCH -> "Search YouTube for: $arg"
    ActionType.OPEN_SETTINGS -> "Open Android Settings"
    ActionType.FLASHLIGHT -> "Flashlight: $arg"
    ActionType.VOLUME -> "Volume: $arg"
    ActionType.GO_BACK -> "Press Back (Accessibility)"
    ActionType.GO_HOME -> "Go Home (Accessibility)"
    ActionType.SCROLL -> "Scroll $arg (Accessibility)"
    ActionType.TAP_TEXT -> "Tap on screen item \"$arg\" (Accessibility)"
    ActionType.MEDIA -> "Media key: $arg"
    ActionType.DIAL -> "Open dialer for: $arg (you press Call yourself)"
    ActionType.SMS_DRAFT -> "Draft SMS to $arg: \"$arg2\" (you press Send yourself)"
    ActionType.WEB_SEARCH -> "Web search: $arg"
    ActionType.WEATHER -> "Weather for: $arg"
    ActionType.ADD_NOTE -> "Save note: $arg"
    ActionType.ADD_TODO -> "Add to-do: $arg"
}
