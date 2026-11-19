package com.tasklean.api.common;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class LabelNamesTest {

    @Test
    void normalise_surroundingSpace_isTrimmed() {
        assertThat(LabelNames.normalise("  Kitchen ")).isEqualTo("Kitchen");
    }

    @Test
    void normalise_runsOfSpaceInside_collapseToOne() {
        // Without this, "Common  Areas" and "Common Areas" are two rows that look identical.
        assertThat(LabelNames.normalise("Common   Areas")).isEqualTo("Common Areas");
    }

    @Test
    void normalise_tabsAndNewlines_countAsSpace() {
        assertThat(LabelNames.normalise("deep\t\nclean")).isEqualTo("deep clean");
    }

    @Test
    void normalise_case_isLeftAlone() {
        // Uniqueness is the index's job, so the household keeps the name as they typed it.
        assertThat(LabelNames.normalise("KITCHEN")).isEqualTo("KITCHEN");
    }

    @Test
    void normalise_null_staysNull() {
        assertThat(LabelNames.normalise(null)).isNull();
    }
}
