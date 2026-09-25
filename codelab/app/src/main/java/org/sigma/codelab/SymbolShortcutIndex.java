package org.sigma.codelab;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.Reader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class SymbolShortcutIndex {
    private final Map<String, List<String>> entries;
    private final int mappingCount;
    private final int ambiguousShortcutCount;

    private SymbolShortcutIndex(
            Map<String, List<String>> entries,
            int mappingCount,
            int ambiguousShortcutCount) {
        this.entries = entries;
        this.mappingCount = mappingCount;
        this.ambiguousShortcutCount = ambiguousShortcutCount;
    }

    public static SymbolShortcutIndex load(Reader reader) throws IOException {
        if (reader == null) throw new IllegalArgumentException("reader must not be null");

        LinkedHashMap<String, List<String>> mutable = new LinkedHashMap<>();
        int mappings = 0;
        int lineNumber = 0;

        try (BufferedReader buffered = new BufferedReader(reader)) {
            String line;
            while ((line = buffered.readLine()) != null) {
                lineNumber++;
                if (line.isEmpty() || line.startsWith("#")) continue;

                String[] parts = line.split("\\t", -1);
                if (parts.length < 2 || parts[0].isEmpty() || parts[1].isEmpty()) {
                    throw new IOException("invalid symbol mapping at line " + lineNumber);
                }

                String shortcut = parts[0];
                String symbol = parts[1];
                mappings++;

                List<String> candidates = mutable.computeIfAbsent(
                        shortcut, ignored -> new ArrayList<>());
                if (!candidates.contains(symbol)) candidates.add(symbol);
            }
        }

        LinkedHashMap<String, List<String>> frozen = new LinkedHashMap<>();
        int ambiguous = 0;
        for (Map.Entry<String, List<String>> entry : mutable.entrySet()) {
            List<String> candidates = List.copyOf(entry.getValue());
            if (candidates.size() > 1) ambiguous++;
            frozen.put(entry.getKey(), candidates);
        }

        return new SymbolShortcutIndex(
                Collections.unmodifiableMap(frozen),
                mappings,
                ambiguous);
    }

    public List<String> candidates(String shortcut) {
        if (shortcut == null) return List.of();
        List<String> found = entries.get(shortcut);
        return found == null ? List.of() : found;
    }

    public int mappingCount() {
        return mappingCount;
    }

    public int shortcutCount() {
        return entries.size();
    }

    public int ambiguousShortcutCount() {
        return ambiguousShortcutCount;
    }
}
