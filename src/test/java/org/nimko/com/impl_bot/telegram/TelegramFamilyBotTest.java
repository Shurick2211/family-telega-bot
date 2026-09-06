package org.nimko.com.impl_bot.telegram;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.nimko.com.ai.AiChatService;
import org.nimko.com.bot.BotSenderService;
import org.nimko.com.bot.commands.ReactionCommand;
import org.nimko.com.entity.ChatContextEntity;
import org.nimko.com.repository.ChatContextRepository;
import org.nimko.com.services.AudioConverter;
import org.nimko.com.services.MediaDownloadService;
import org.nimko.com.services.I18nService;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.User;
import org.telegram.telegrambots.meta.api.objects.chat.Chat;
import org.telegram.telegrambots.meta.api.objects.reactions.MessageReactionUpdated;
import org.telegram.telegrambots.meta.api.objects.reactions.ReactionTypeEmoji;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.*;

public class TelegramFamilyBotTest {

    @Test
    public void testConsume_WithReactionUpdate() {
        // Mock dependencies
        AiChatService aiChatService = mock(AiChatService.class);
        AudioConverter audioConverter = mock(AudioConverter.class);
        MediaDownloadService mediaDownloadService = mock(MediaDownloadService.class);
        I18nService i18nService = mock(I18nService.class);
        BotSenderService botSenderService = mock(BotSenderService.class);
        TelegramFileService telegramFileService = mock(TelegramFileService.class);
        ChatContextRepository chatContextRepository = mock(ChatContextRepository.class);
        ObjectMapper objectMapper = mock(ObjectMapper.class);

        TelegramBotProperties properties = mock(TelegramBotProperties.class);
        when(properties.newsChatId()).thenReturn(12345L);

        TelegramFamilyBot bot = new TelegramFamilyBot(
                "test_bot",
                aiChatService,
                audioConverter,
                mediaDownloadService,
                true,
            i18nService,
                botSenderService,
                List.of(new ReactionCommand(chatContextRepository, properties)),
                telegramFileService,
                chatContextRepository,
                objectMapper,
                12345L
        );

        // Mock message reaction update
        Update update = mock(Update.class);
        MessageReactionUpdated reactionUpdated = mock(MessageReactionUpdated.class);
        Chat chat = mock(Chat.class);
        User user = mock(User.class);
        ReactionTypeEmoji reaction = mock(ReactionTypeEmoji.class);

        when(update.getMessageReaction()).thenReturn(reactionUpdated);
        when(reactionUpdated.getChat()).thenReturn(chat);
        when(reactionUpdated.getMessageId()).thenReturn(999);
        when(reactionUpdated.getUser()).thenReturn(user);
        when(reactionUpdated.getNewReaction()).thenReturn(List.of(reaction));

        when(chat.getId()).thenReturn(100L);
        when(chat.getType()).thenReturn("group");
        when(chat.isGroupChat()).thenReturn(true);

        when(user.getUserName()).thenReturn("john_doe");
        when(user.getFirstName()).thenReturn("John");

        when(reaction.getEmoji()).thenReturn("🔥");

        // Execute consume
        bot.consume(List.of(update));

        // Verify context entity was saved with expected values
        ArgumentCaptor<ChatContextEntity> captor = ArgumentCaptor.forClass(ChatContextEntity.class);
        verify(chatContextRepository, times(1)).save(captor.capture());

        ChatContextEntity savedEntity = captor.getValue();
        assertEquals(100L, savedEntity.getChatId());
        assertEquals(999, savedEntity.getMessageId());
        assertEquals("john_doe", savedEntity.getUserName());
        assertEquals("John", savedEntity.getName());
        assertEquals("[emotion] 🔥", savedEntity.getMessage());
        assertTrue(savedEntity.isGroupChat());
    }
}
