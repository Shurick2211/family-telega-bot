package org.nimko.com.bot.commands;

import java.util.List;
import java.util.Map;
import org.apache.commons.lang3.StringUtils;
import org.nimko.com.ai.AiChatService;
import org.nimko.com.bot.BotSenderService;
import org.nimko.com.bot.FamilyTelegramBot.ReplyData;
import org.nimko.com.services.DocxGeneratorService;
import org.nimko.com.util.BotUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Service;
import org.telegram.telegrambots.meta.api.objects.message.Message;

@Service
@Order(1)
public class ArticlesCommand implements CommandProcess {

  private static final Logger log = LoggerFactory.getLogger(ArticlesCommand.class);

  private final AiChatService aiChatService;
  private final DocxGeneratorService docxGeneratorService;
  private final BotSenderService botSenderService;

  public ArticlesCommand(final AiChatService aiChatService,
      final DocxGeneratorService docxGeneratorService,
      final BotSenderService botSenderService) {
    this.aiChatService = aiChatService;
    this.docxGeneratorService = docxGeneratorService;
    this.botSenderService = botSenderService;
  }

  @Override
  public boolean isCommand(final String command) {
    return "/articles".equals(command);
  }

  @Override
  public ReplyData execute(final String normalizedText, final boolean hasPhoto, final byte[] imageBytes,
      final Message message, final Long chatId, final boolean hasVoice, final byte[] rawAudioBytes,
      final byte[] extractedAudioFromVideoBytes, final boolean groupChat, final int messageId, final Map<Long, List<String>> chatContext) {
    String promptArticles = BotUtils.extractCommandPayload(normalizedText);

    if (message.getReplyToMessage() != null) {
      final String replyText = BotUtils.resolveIncomingText(message.getReplyToMessage());
      if (StringUtils.isNotBlank(replyText)) {
        if (StringUtils.isNotBlank(promptArticles)) {
          promptArticles = promptArticles + "\n\nДжерело/Матеріал:\n" + replyText;
        } else {
          promptArticles = replyText;
        }
      }
    }

    final String preparedPrompt = BotUtils.prepareArticlesPrompt(StringUtils.isNotBlank(promptArticles)
        ? promptArticles
        : "Напиши розгорнуту статтю за інформацією отримавши інформацію з прикріпленого посилання.");

    try {
      botSenderService.sendTextReply(chatId, "Генерую статтю, зачекайте будь ласка...");

      final String generatedArticle = aiChatService.askArticle(preparedPrompt);
      if (StringUtils.isBlank(generatedArticle) || generatedArticle.startsWith("AI provider")) {
        botSenderService.sendTextReply(chatId, "Не вдалося згенерувати статтю: " + generatedArticle);
        return null;
      }

      log.info("Generating DOCX document from generated article...");
      final byte[] docxBytes = docxGeneratorService.generateDocxFromMarkdown(generatedArticle);
      if (docxBytes == null || docxBytes.length == 0) {
        botSenderService.sendTextReply(chatId, "Помилка при створенні .docx файлу.");
        return null;
      }

      final String filename = "article_" + System.currentTimeMillis() + ".docx";
      botSenderService.sendDocument(chatId, docxBytes, filename);
    } catch (final Exception ex) {
      log.error("Error processing /articles command for chat {}", chatId, ex);
      botSenderService.sendTextReply(chatId, "Сталася помилка при обробці команди: " + ex.getMessage());
    }
    return null;
  }
}
