package org.nimko.com.config;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

public class AiChatPropertiesTest {

    @Test
    public void testIsConfigured_SingleKeyEnabled() {
        AiChatProperties props = new AiChatProperties(
            "https://api.openai.com/v1",
            "key-1",
            null,
            false,
            "gpt-4o",
            "system prompt"
        );
        assertTrue(props.isConfigured());
    }

    @Test
    public void testIsConfigured_SecondaryEnabledWithoutKey() {
        AiChatProperties props = new AiChatProperties(
            "https://api.openai.com/v1",
            "key-1",
            "",
            true,
            "gpt-4o",
            "system prompt"
        );
        assertFalse(props.isConfigured());
    }

    @Test
    public void testIsConfigured_SecondaryEnabledWithKey() {
        AiChatProperties props = new AiChatProperties(
            "https://api.openai.com/v1",
            "key-1",
            "key-2",
            true,
            "gpt-4o",
            "system prompt"
        );
        assertTrue(props.isConfigured());
    }
}
