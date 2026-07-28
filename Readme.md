#  Family Telegram Bot

A highly functional, self-hosted, **Spring Boot 3.x** and **AI-powered** Telegram bot. Built to serve as an intelligent, context-aware companion for family and group chats, it features real-time voice and video transcriptions, rich article generation (delivered as customized Word documents), automated comedic diaries, multi-language support, emotional tracking, and background media downloading.

Specifically optimized for portability, it can run flawlessly on lightweight environments—including **Termux on Android**—as a portable self-hosted private hub.

---

## +50 Key Features

### 1.  Smart Chat Assistant & Multi-Turn Context
* **Context Preservation:** Keeps a daily-rotating rolling window of conversation history in PostgreSQL so discussions with the AI are natural and coherent.
* **Group Identity & Author Attribution:** In group chats, it tracks who said what (attributing names and stripping bot username tags) so that the AI understands individual participant perspectives.
* **Auto-Cleanup:** A daily scheduled task automatically cleans up chat logs older than 30 days to keep the system lightweight and secure.

### 2. Advanced Article Writer (`/articles` & Docx Generation)
* **Custom Markdown to Word Document (.docx):** Enter a prompt, topic, or reply to a text/link with `/articles`. The bot will generate a thorough, comprehensive article using AI, parse the markdown formatting, and convert it into a beautifully styled MS Word document (`.docx`).
* **Professional Typography:** Generated documents feature elegant styling tailored for Ukrainian/Slovak standards, including Times New Roman typography, standard heading hierarchies, clean bullet lists, and customized first-line indentations.

### 3.  Smart News Re-writer (`/news` & Multimodal Input)
* **Catchy Summaries:** Extracts and rewrites news from links or plain text into engaging, easily readable summaries.
* **Multimodal Support:** Upload a photo with the `/news` command, and the AI will analyze the visual content in tandem with your text to generate a unified news story.

### 4. Media Transcription & Audio Extractor (`/text` & `/audio`)
* **`/text` (Speech-to-Text):** Transcribes voice notes, standard audio files, video notes (round video messages / круглі відео), and standard videos. Voice notes are seamlessly transcoded from OGG to MP3 using an internal FFMPEG process prior to transcription.
* **Group Chat Auto-Transcribe:** If enabled (`AUTO_TRANSCRIBE=true`), the bot will automatically transcribe all incoming voice and video messages in group chats on-the-fly and insert them directly into the ongoing AI context.
* **`/audio` (Audio Extraction):** Extracts audio tracks from video notes, standard videos, or video documents and delivers them back as clean, high-fidelity MP3 files.

### 5.  Emotion & Reaction Tracking
* **Sentiment Context:** Monitors user message reactions (such as 44d, '64, or 602).
* **AI Feedback:** Registers reactions as special events in the database (e.g., `[emotion] 44d`), allowing the AI to gauge user sentiment and adapt its conversational tone.

### 6. Automated Comedic Diaries & Summaries
* **Humorous Daily Digests:** Every evening at **21:30**, the bot compiles the group's conversation logs from that day and uses AI to generate a hilarious, satirical summary of the family's day.
* **Humorous Monthly Digests:** On the last day of each month at **21:45**, the bot compiles all the month's daily summaries to produce a monthly comedic highlight.
* **On-Demand Retrieval:** Retrieve any historic daily summary directly using `/daily dd-MM-yyyy`.

### 7. Background Media Downloader
* **Automatic Detection:** Paste shared video links (such as TikTok, YouTube, etc.) directly into the chat.
* **Silent Download:** The bot forwards the URL to a configured background media downloader service, fetches the source file, and uploads the actual video/audio file attachment straight to the chat.

### 8. Multi-Language Localization (i18n)
* **Native Support:** Dynamic locale switching supporting **English**, **Ukrainian (uk)**, **Slovak (sk)**, and **Russian (ru)**.
* **Smart Detection:** Automatically infers the preferred language from user profiles or the active group chat context.

---

## Technology Stack

* **Core Framework:** Spring Boot 3.x (Java 21/17)
* **Database & Persistence:** PostgreSQL, Spring Data JPA, Liquibase (Database migrations)
* **Telegram APIs:** Telegram Bots SDK (Long Polling)
* **AI Integration:** Gemini API (configured with a primary API key, system prompt control, and automatic secondary key fallback for high reliability)
* **Media Processing:** JAVE2 / FFMPEG (transcoding OGG to MP3, extracting audio tracks from MP4)
* **Document Generation:** Apache POI (parsing Markdown formatting directly into styled OpenXML `.docx` files)
* **Build System:** Gradle (Kotlin DSL)

---

## Termux Optimization (Self-Hosting on Android)

The bot is uniquely designed to run on a spare Android device inside **Termux**.
* **Termux FFMPEG Locator:** Includes a customized `TermuxFFMPEGLocator` that points directly to the Termux native FFMPEG package path:
  `/data/data/com.termux/files/usr/bin/ffmpeg`
* **Lightweight Footprint:** Configured via startup flags to operate efficiently under restricted resources:
  - Uses the `SerialGC` garbage collector to minimize CPU and RAM overhead on mobile processors.
  - Sets custom memory constraints (`-Xms64m -Xmx1024m`).

---

## Configuration & Environment Variables

You can configure the application using environment variables or by creating a `.env` file in the root folder.

### Configuration Options

| Variable | Description | Example / Default |
|:---|:---|:---|
| `PORT` | Server listening port | `${PORT:9091}` |
| `DB_HOST` | PostgreSQL host | `${DB_HOST:localhost}` |
| `DB_PORT` | PostgreSQL port | `${DB_PORT:5432}` |
| `DB_NAME` | PostgreSQL database name | `${DB_NAME:postgres}` |
| `DB_USERNAME` | PostgreSQL database username | `${DB_USERNAME:u0_a325}` |
| `DB_PASSWORD` | PostgreSQL database password | `${DB_PASSWORD:postgres}` |
| `TELEGRAM_BOT_USERNAME` | Your Telegram Bot's handle | `${TELEGRAM_BOT_USERNAME}` |
| `TELEGRAM_BOT_TOKEN` | Your Telegram Bot's API Token | `${TELEGRAM_BOT_TOKEN}` |
| `TELEGRAM_BOT_API_BASE_URL`| Custom Telegram bot API URL (optional)| `${TELEGRAM_BOT_API_BASE_URL}` |
| `AUTO_TRANSCRIBE` | Auto-transcribe group audio/video | `${AUTO_TRANSCRIBE:false}` |
| `NEWS_CHAT_ID` | Targeted news chat/channel ID | `${NEWS_CHAT_ID}` |
| `DOWNLOADER_ENDPOINT` | Background media downloader service API | `${DOWNLOADER_ENDPOINT}` |
| `AI_API_BASE_URL` | Base API URL of AI Provider | `${AI_API_BASE_URL}` |
| `AI_API_KEY` | Primary API Key | `${AI_API_KEY}` |
| `AI_API_KEY_2` | Secondary backup API Key | `${AI_API_KEY_2}` |
| `ENABLE_SECONDARY` | Fall back to second key on primary error | `${ENABLE_SECONDARY:false}` |
| `AI_DEFAULT_MODEL` | AI Model name | `${AI_DEFAULT_MODEL}` |
| `AI_SYSTEM_PROMPT` | Custom behavior instructions for the bot | `${AI_SYSTEM_PROMPT}` |
| `TRANSCRIPTION_MODEL` | Model used for audio transcribing | `${TRANSCRIPTION_MODEL}` |

---

## Installation & Running

### Prerequisites
1. **Java Runtime:** Java 17 or Java 21.
2. **FFMPEG:**
   - On Linux: `sudo apt install ffmpeg`
   - On Termux: `pkg install ffmpeg`

### 1. Build the Jar
```bash
./gradlew clean build -x test
```

### 2. Configure Environment
Create a `.env` file in the root directory and add the configuration parameters outlined in the table above.

### 3. Run the Bot
You can run the bot directly using Java:
```bash
java -jar build/libs/test-bot-project-1.0-SNAPSHOT.jar
```

Alternatively, use the built-in execution scripts:
```bash
# Starts the bot and loads environment variables from .env
./start.sh
```

---

## Commands Reference

* `/start` - Displays the bot's readiness and starts the conversation.
* `/hello` - A friendly greetings response.
* `/help` - Sends an elegant interactive command cheatsheet.
* `/news <text/link>` - Submits news text or links to the AI, which rewrites it into a punchy news item. Supports attached images.
* `/articles <text/link>` - Prompts the AI to generate a full-length, structured article from the provided source, then generates and returns a fully styled `.docx` file.
* `/text` - Reply to any audio file, voice note, video note, or video file with this command to instantly transcribe its speech to text.
* `/audio` - Reply to any video file or video note with this command to extract its audio track as a downloadable MP3 file.
* `/daily dd-MM-yyyy` - Retrieves the comedic daily summary corresponding to the specified date.
* `/context` - Displays the current rolling conversation context of the current day.

---

## Database Schema

The database relies on **Liquibase** to manage its tables:
1. `chat_context`: Stores context-aware message lines, usernames, time, message IDs, and special reaction triggers.
2. `dayly_summary_chat`: Stores historical daily summaries generated by the scheduled comedy task.

---

## License & Contribution
This is a private project tailored for lightweight personal server deployments. Contributions, forks, and feature suggestions are welcome! Feel free to customize prompts in the `src/main/resources/prompts/` folder.
