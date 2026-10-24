package com.cleanpilot.ui;

import java.util.*;

/**
 * Fast in-memory Trie data structure for prefix-based indexing and searching.
 *
 * @param <T> Value type stored in the trie
 */
public class PrefixTrie<T> {

    private static class TrieNode<T> {
        final Map<Character, TrieNode<T>> children = new HashMap<>();
        final List<T> values = new ArrayList<>();
    }

    private final TrieNode<T> root = new TrieNode<>();

    /**
     * Inserts a key and its associated value into the Trie.
     */
    public void insert(String key, T value) {
        if (key == null || value == null) {
            return;
        }
        TrieNode<T> current = root;
        String normalized = key.toLowerCase(Locale.ROOT);
        for (char c : normalized.toCharArray()) {
            current = current.children.computeIfAbsent(c, ch -> new TrieNode<>());
        }
        current.values.add(value);
    }

    /**
     * Returns all values whose keys start with the given prefix.
     */
    public List<T> searchByPrefix(String prefix) {
        if (prefix == null || prefix.isBlank()) {
            return collectAll(root);
        }
        TrieNode<T> current = root;
        String normalized = prefix.toLowerCase(Locale.ROOT);
        for (char c : normalized.toCharArray()) {
            current = current.children.get(c);
            if (current == null) {
                return Collections.emptyList();
            }
        }
        return collectAll(current);
    }

    /**
     * Inserts tokens from the text (split by whitespace and punctuation) so each word is indexed.
     */
    public void insertTokens(String text, T value) {
        if (text == null || value == null) {
            return;
        }
        insert(text, value);
        String[] tokens = text.split("[\\s._\\\\/\\-]+");
        for (String token : tokens) {
            if (!token.isBlank()) {
                insert(token, value);
            }
        }
    }

    private List<T> collectAll(TrieNode<T> node) {
        Set<T> result = new LinkedHashSet<>(node.values);
        for (TrieNode<T> child : node.children.values()) {
            result.addAll(collectAll(child));
        }
        return new ArrayList<>(result);
    }

    /**
     * Clears all entries from the Trie.
     */
    public void clear() {
        root.children.clear();
        root.values.clear();
    }
}
