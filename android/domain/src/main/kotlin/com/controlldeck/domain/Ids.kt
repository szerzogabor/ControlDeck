package com.controlldeck.domain

/**
 * Strongly-typed identifiers. All are backed by plain Strings (UUIDs in
 * practice) but wrapped in inline value classes so the domain layer never
 * mixes up a DeviceId with a WidgetId etc. at compile time.
 */
@JvmInline
value class DeviceId(val value: String) {
    override fun toString(): String = value
}

@JvmInline
value class WidgetId(val value: String) {
    override fun toString(): String = value
}

@JvmInline
value class DashboardId(val value: String) {
    override fun toString(): String = value
}

@JvmInline
value class GroupId(val value: String) {
    override fun toString(): String = value
}

@JvmInline
value class AppId(val value: String) {
    override fun toString(): String = value
}
