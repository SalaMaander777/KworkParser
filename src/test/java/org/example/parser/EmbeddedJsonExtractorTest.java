package org.example.parser;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class EmbeddedJsonExtractorTest {

    @Test
    void extractsNestedObjectIgnoringBracesInStrings() {
        String html = "<script>window.stateData = {\"a\":{\"b\":\"}{ \\\" }\"},\"c\":1};</script>";
        assertEquals("{\"a\":{\"b\":\"}{ \\\" }\"},\"c\":1}", EmbeddedJsonExtractor.extract(html, "window.stateData"));
    }

    @Test
    void failsWhenVariableMissing() {
        assertThrows(KworkParsingException.class, () -> EmbeddedJsonExtractor.extract("<html/>", "window.stateData"));
    }

    @Test
    void failsOnUnclosedObject() {
        assertThrows(KworkParsingException.class,
                () -> EmbeddedJsonExtractor.extract("window.stateData = {\"a\":1", "window.stateData"));
    }
}
