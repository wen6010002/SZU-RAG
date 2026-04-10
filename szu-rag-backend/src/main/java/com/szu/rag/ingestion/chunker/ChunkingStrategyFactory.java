package com.szu.rag.ingestion.chunker;

import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Component
public class ChunkingStrategyFactory {

    private final Map<String, ChunkingStrategy> strategyMap;

    public ChunkingStrategyFactory(List<ChunkingStrategy> strategies) {
        this.strategyMap = strategies.stream()
                .collect(Collectors.toMap(ChunkingStrategy::getName, Function.identity()));
    }

    public ChunkingStrategy get(String name) {
        ChunkingStrategy strategy = strategyMap.get(name.toUpperCase());
        if (strategy == null) {
            throw new IllegalArgumentException("Unknown chunking strategy: " + name);
        }
        return strategy;
    }

    public ChunkingStrategy getDefault() {
        return get("RECURSIVE");
    }
}
