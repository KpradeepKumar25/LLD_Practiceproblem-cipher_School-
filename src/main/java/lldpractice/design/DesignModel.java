package lldpractice.design;

import java.util.List;

/**
 * A format-neutral view of a learner's code, produced by a {@link DesignExtractor}. Evaluators
 * read only this model, never the raw source, so a new submission format (e.g. a PlantUML
 * diagram) only needs its own extractor, not changes to any evaluator.
 */
public record DesignModel(List<TypeModel> types, List<String> warnings) {
    public DesignModel {
        types = List.copyOf(types);
        warnings = List.copyOf(warnings);
    }

    public static DesignModel empty() {
        return new DesignModel(List.of(), List.of());
    }

    public boolean hasType(String nameContains) {
        String lower = nameContains.toLowerCase();
        return types.stream().anyMatch(t -> t.name().toLowerCase().contains(lower));
    }
}
