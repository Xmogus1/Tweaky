package com.renderoptimiser.config

import kotlinx.serialization.json.JsonElement

internal interface Savable {
    fun write(): JsonElement
    fun read(element: JsonElement?)
}