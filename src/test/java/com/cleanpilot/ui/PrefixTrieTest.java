package com.cleanpilot.ui;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class PrefixTrieTest {

    @Test
    @DisplayName("PrefixTrie should accurately index and retrieve elements by prefix")
    void testPrefixTrie() {
        PrefixTrie<String> trie = new PrefixTrie<>();
        trie.insert("chrome.exe", "Chrome Browser");
        trie.insert("chronicle.log", "Log File");
        trie.insert("cleanpilot.jar", "CleanPilot App");
        trie.insert("system.dll", "Windows DLL");

        List<String> chMatches = trie.searchByPrefix("ch");
        assertThat(chMatches).containsExactlyInAnyOrder("Chrome Browser", "Log File");

        List<String> clMatches = trie.searchByPrefix("clean");
        assertThat(clMatches).containsExactly("CleanPilot App");

        List<String> noMatches = trie.searchByPrefix("zebra");
        assertThat(noMatches).isEmpty();

        List<String> all = trie.searchByPrefix("");
        assertThat(all).hasSize(4);

        trie.clear();
        assertThat(trie.searchByPrefix("")).isEmpty();
    }

    @Test
    @DisplayName("PrefixTrie should index tokens and return deduplicated matches")
    void testTokenIndexing() {
        PrefixTrie<String> trie = new PrefixTrie<>();
        trie.insertTokens("node_modules/package.json", "Node Package");
        trie.insertTokens("build/reports/tests.html", "Test Report");

        List<String> pkgMatches = trie.searchByPrefix("pack");
        assertThat(pkgMatches).containsExactly("Node Package");

        List<String> nodeMatches = trie.searchByPrefix("node");
        assertThat(nodeMatches).containsExactly("Node Package");

        List<String> repMatches = trie.searchByPrefix("rep");
        assertThat(repMatches).containsExactly("Test Report");
    }
}
