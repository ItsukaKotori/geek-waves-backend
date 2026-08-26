package com.geekwaves.ai;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

class AiPromptTest {

    @Test
    void systemPromptCoversFiveSectionStructure() {
        String text = AiPrompt.SYSTEM;
        assertTrue(text.contains("核心要点"));
        assertTrue(text.contains("技术背景"));
        assertTrue(text.contains("值得关注的原因"));
        assertTrue(text.contains("给开发者的建议"));
        assertTrue(text.contains("风险提示"));
        assertTrue(text.contains("300 字"));
    }
}
