package lldpractice.design;

import java.util.EnumMap;
import java.util.Map;

import lldpractice.domain.SubmissionFormat;

/**
 * Factory/registry that maps a {@link SubmissionFormat} to its {@link DesignExtractor}.
 * Adding support for a new submission format (a diagram notation, another language) means
 * writing one new extractor and registering it here; nothing in {@code evaluation} changes.
 */
public final class ExtractorRegistry {

    private final Map<SubmissionFormat, DesignExtractor> extractors = new EnumMap<>(SubmissionFormat.class);

    public ExtractorRegistry register(DesignExtractor extractor) {
        extractors.put(extractor.format(), extractor);
        return this;
    }

    public DesignExtractor forFormat(SubmissionFormat format) {
        DesignExtractor extractor = extractors.get(format);
        if (extractor == null) {
            throw new IllegalStateException("No DesignExtractor registered for format: " + format);
        }
        return extractor;
    }

    /** A registry pre-populated with every extractor the platform ships with. */
    public static ExtractorRegistry standard() {
        return new ExtractorRegistry().register(new JavaDesignExtractor());
    }
}
