package io.github.antctrlwin.anomalib4j.evaluation;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

final class MvtecCategories {
    private static final List<String> NAMES = load();
    private MvtecCategories() { }
    static List<String> names() { return NAMES; }
    static void require(String name) {
        if (!NAMES.contains(name)) throw new IllegalArgumentException("Unknown MVTec category: " + name);
    }
    private static List<String> load() {
        try (var input = MvtecCategories.class.getResourceAsStream("/mvtec-categories.txt")) {
            if (input == null) throw new IOException("Missing canonical category resource");
            return new String(input.readAllBytes(), StandardCharsets.UTF_8).lines().filter(s -> !s.isBlank()).toList();
        } catch (IOException failure) { throw new ExceptionInInitializerError(failure); }
    }
}
