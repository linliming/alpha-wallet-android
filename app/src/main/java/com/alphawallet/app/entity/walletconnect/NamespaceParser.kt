package com.alphawallet.app.entity.walletconnect

import com.walletconnect.web3.wallet.client.Wallet
import java.util.stream.Collectors

class NamespaceParser {
    private val chains: MutableList<String> = ArrayList()
    private val methods: MutableList<String> = ArrayList()
    private val events: MutableList<String> = ArrayList()
    private val wallets: MutableList<String> = ArrayList()

    fun parseProposal(requiredNamespaces: Map<String, Wallet.Model.Namespace.Proposal>) {
        for ((_, value) in requiredNamespaces) {
            val chains = value.chains
            if (chains != null) {
                this.chains.addAll(chains)
            }
            methods.addAll(value.methods)
            events.addAll(value.events)
        }
    }

    fun parseSession(namespaces: Map<String, Wallet.Model.Namespace.Session>) {
        for ((_, value) in namespaces) {
            chains.addAll(parseChains(value.accounts))
            methods.addAll(value.methods)
            events.addAll(value.events)
            wallets.addAll(parseWallets(value.accounts))
        }
    }

    private fun parseWallets(accounts: List<String>): List<String> {
        return accounts.stream()
            .map { account: String -> account.substring(account.lastIndexOf(":") + 1) }
            .collect(Collectors.toList())
    }

    private fun parseChains(accounts: List<String>): List<String> {
        return accounts.stream()
            .map { account: String -> account.substring(0, account.lastIndexOf(":")) }
            .collect(Collectors.toList())
    }

    fun getChains(): List<String> {
        return chains
    }

    fun getMethods(): List<String> {
        return methods
    }

    fun getEvents(): List<String> {
        return events
    }

    fun getWallets(): List<String> {
        return ArrayList(HashSet(wallets))
    }
}
