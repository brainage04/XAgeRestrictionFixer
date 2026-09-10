package app.xagefixer;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public final class JsonNormalizerTest {
    @Test
    public void unwrapsNestedVisibilityResultsAndRestoresSensitiveEditability() {
        String input = "{\"data\":{\"__typename\":\"TweetWithVisibilityResults\","
                + "\"tweet\":{\"legacy\":{\"possibly_sensitive_editable\":false},"
                + "\"id\":\"123\"}}}";

        assertEquals(
                "{\"data\":{\"legacy\":{\"possibly_sensitive_editable\":true},"
                        + "\"id\":\"123\",\"__typename\":\"Tweet\"}}",
                JsonNormalizer.normalize(input));
    }

    @Test
    public void normalizesWrappersInsideArraysWithoutTouchingOtherObjects() {
        String input = "{\"items\":[{\"__typename\":\"TweetWithVisibilityResults\","
                + "\"tweet\":{\"id\":\"1\"}},{\"__typename\":\"Tweet\","
                + "\"id\":\"2\"}]}";

        assertEquals(
                "{\"items\":[{\"id\":\"1\",\"__typename\":\"Tweet\"},"
                        + "{\"__typename\":\"Tweet\",\"id\":\"2\"}]}",
                JsonNormalizer.normalize(input));
    }

    @Test
    public void leavesInvalidAndUnmatchedBodiesUnchanged() {
        String unmatched = "{\"__typename\":\"Tweet\",\"text\":\"TweetWithVisibilityResults\"}";
        String invalid = "{\"__typename\":\"TweetWithVisibilityResults\"";

        assertEquals(unmatched, JsonNormalizer.normalize(unmatched));
        assertEquals(invalid, JsonNormalizer.normalize(invalid));
    }
}
