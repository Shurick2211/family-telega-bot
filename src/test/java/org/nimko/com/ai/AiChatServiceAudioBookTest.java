package org.nimko.com.ai;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import org.junit.jupiter.api.Test;
import org.nimko.com.config.AiChatProperties;
import org.nimko.com.services.AudioConverter;

public class AiChatServiceAudioBookTest {

    @Test
    public void testNarrateBook_NotConfigured() {
        AiChatProperties properties = mock(AiChatProperties.class);
        when(properties.isConfigured()).thenReturn(false);
        AudioConverter audioConverter = mock(AudioConverter.class);

        AiChatServiceAudioBook service = new AiChatServiceAudioBook(
            "gemini-3.1-flash-tts-preview",
            "kore",
            "gemini-2.5-flash",
            properties,
            audioConverter
        );

        byte[] result = service.narrateBook("Some book text", null);
        assertNull(result);
    }

    @Test
    public void testNarrateBook_EmptyText() {
        AiChatProperties properties = mock(AiChatProperties.class);
        when(properties.isConfigured()).thenReturn(true);
        AudioConverter audioConverter = mock(AudioConverter.class);

        AiChatServiceAudioBook service = new AiChatServiceAudioBook(
            "gemini-3.1-flash-tts-preview",
            "kore",
            "gemini-2.5-flash",
            properties,
            audioConverter
        );

        byte[] result = service.narrateBook("", null);
        assertNull(result);
    }
}
