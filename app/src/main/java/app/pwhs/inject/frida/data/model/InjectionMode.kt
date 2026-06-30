package app.pwhs.inject.frida.data.model

enum class InjectionMode(val value: Int) {
    ROOT_SERVER(0),
    NON_ROOT_GADGET(1);

    companion object {
        fun fromInt(value: Int) = entries.firstOrNull { it.value == value } ?: ROOT_SERVER
    }
}
