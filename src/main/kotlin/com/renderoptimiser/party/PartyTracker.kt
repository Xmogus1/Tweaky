package com.renderoptimiser.party

import com.renderoptimiser.RenderOptimiser.mc

/**
 * Tracks the player's current Hypixel party roster by reading party-chat lines. There is no party
 * API on this mod, so membership is learned passively from join/leave notices and from anyone who
 * talks in party chat. Used by [com.renderoptimiser.features.impl.general.PartyChat] to decide who
 * a relay message goes to and who it may be shown from.
 */
object PartyTracker {
    private val members = linkedSetOf<String>()

    private val joinedParty = Regex("You have joined (\\w{1,16})'s party!", RegexOption.IGNORE_CASE)
    private val someoneJoined = Regex("(\\w{1,16}) joined the party", RegexOption.IGNORE_CASE)
    private val someoneLeft = Regex("(\\w{1,16}) has left the party", RegexOption.IGNORE_CASE)
    private val youLeft = Regex("You (?:left|have left) the party|You are not currently in a party|You have been (?:kicked|removed) from the party", RegexOption.IGNORE_CASE)
    private val disbanded = Regex("The party was disbanded", RegexOption.IGNORE_CASE)
    private val partyChatLine = Regex("^Party > (?:\\[[^\\]]+\\]\\s*)*(\\w{1,16}):", RegexOption.IGNORE_CASE)

    private fun self(): String = runCatching { mc.user.name }.getOrDefault("?")

    fun onChat(textRaw: String) {
        val text = textRaw.trim()
        joinedParty.find(text)?.let { members.clear(); add(it.groupValues[1]) }
        someoneJoined.find(text)?.let { add(it.groupValues[1]) }
        someoneLeft.find(text)?.let { members -= it.groupValues[1] }
        partyChatLine.find(text)?.let { add(it.groupValues[1]) }
        if (youLeft.containsMatchIn(text) || disbanded.containsMatchIn(text)) members.clear()
    }

    private fun add(name: String) {
        if (! name.equals(self(), true)) members += name
    }

    fun recipients(): List<String> = members.filterNot { it.equals(self(), true) }.distinctBy { it.lowercase() }

    fun hasMembers(): Boolean = recipients().isNotEmpty()

    fun isMember(name: String): Boolean = name.equals(self(), true) || members.any { it.equals(name, true) }
}
