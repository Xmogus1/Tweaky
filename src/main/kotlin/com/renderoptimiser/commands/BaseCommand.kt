package com.renderoptimiser.commands

abstract class BaseCommand(val name: String, val aliases: MutableSet<String> = mutableSetOf()) {
    abstract fun CommandNodeBuilder.build()
}