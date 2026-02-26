package com.identipay.wallet.crypto

import android.content.Context

/**
 * BIP39 wordlist loader. Loads the English wordlist from assets/bip39_english.txt.
 * The file should contain 2048 words, one per line.
 *
 * To generate this file, download the official BIP39 English wordlist from:
 * https://raw.githubusercontent.com/bitcoin/bips/master/bip-0039/english.txt
 * and place it at app/src/main/assets/bip39_english.txt
 */
object Bip39 {
    @Volatile
    private var cachedWordlist: List<String>? = null

    fun getWordlist(context: Context): List<String> {
        cachedWordlist?.let { return it }
        synchronized(this) {
            cachedWordlist?.let { return it }
            val words = context.assets.open("bip39_english.txt")
                .bufferedReader()
                .readLines()
                .filter { it.isNotBlank() }
            require(words.size == 2048) {
                "BIP39 wordlist must contain 2048 words, found ${words.size}"
            }
            cachedWordlist = words
            return words
        }
    }
}
