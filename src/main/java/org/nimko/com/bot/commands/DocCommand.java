package org.nimko.com.bot.commands;

import org.apache.commons.lang3.StringUtils;
import org.nimko.com.ai.AiChatService;
import org.nimko.com.bot.BotSenderService;
import org.nimko.com.bot.dto.ReplyData;
import org.nimko.com.services.DocxGeneratorService;
import org.nimko.com.services.I18nService;
import org.nimko.com.util.BotUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Service;
import org.nimko.com.bot.messenger.IncomingMessage;

@Service
@Order(1)
public class DocCommand implements CommandProcess {

  private static final Logger log = LoggerFactory.getLogger(DocCommand.class);

  private final AiChatService aiChatService;
  private final DocxGeneratorService docxGeneratorService;
  private final BotSenderService botSenderService;
  private final I18nService i18nService;

  public DocCommand(final AiChatService aiChatService,
      final DocxGeneratorService docxGeneratorService,
      final BotSenderService botSenderService,
      final I18nService i18nService) {
    this.aiChatService = aiChatService;
    this.docxGeneratorService = docxGeneratorService;
    this.botSenderService = botSenderService;
    this.i18nService = i18nService;
  }

  @Override
  public boolean isCommand(final String command) {
    return "/doc".equals(command);
  }

  @Override
  public ReplyData execute(final String normalizedText, final boolean hasPhoto, final byte[] imageBytes,
      final IncomingMessage message, final Long chatId, final boolean hasVoice, final byte[] rawAudioBytes,
      final byte[] extractedAudioFromVideoBytes, final boolean groupChat, final int messageId) {
    String promptDoc = BotUtils.extractCommandPayload(normalizedText);

    if (message.getReplyToMessage() != null) {
      final String replyText = message.getReplyToMessage().getText();
      if (StringUtils.isNotBlank(replyText)) {
        if (StringUtils.isNotBlank(promptDoc)) {
          promptDoc = promptDoc + "\n\nКонтекст/Матеріал:\n" + replyText;
        } else {
          promptDoc = replyText;
        }
      }
    }

    if (StringUtils.isBlank(promptDoc)) {
      botSenderService.sendTextReply(chatId, i18nService.getTranslate("bot.doc.usage"));
      return null;
    }

    try {
      botSenderService.sendTextReply(chatId, i18nService.getTranslate("bot.doc.generating"));

      final String generatedAnswer = aiChatService.askDoc(promptDoc);
      if (StringUtils.isBlank(generatedAnswer) || generatedAnswer.startsWith("AI provider")) {
        botSenderService.sendTextReply(chatId,
            i18nService.getTranslate("bot.doc.generation.failed", generatedAnswer));
        return null;
      }

      log.info("Generating DOCX document from AI response...");
      final byte[] docxBytes = docxGeneratorService.generateDocxFromMarkdown(generatedAnswer);
      if (docxBytes == null || docxBytes.length == 0) {
        botSenderService.sendTextReply(chatId, i18nService.getTranslate("bot.doc.docx.error"));
        return null;
      }

      final String filename = "doc_" + System.currentTimeMillis() + ".docx";
      botSenderService.sendDocument(chatId, docxBytes, filename);
    } catch (final Exception ex) {
      log.error("Error processing /doc command for chat {}", chatId, ex);
      botSenderService.sendTextReply(chatId, i18nService.getTranslate("bot.doc.error", ex.getMessage()));
    }
    return null;
  }
}
