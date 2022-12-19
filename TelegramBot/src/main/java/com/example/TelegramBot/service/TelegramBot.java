package com.example.TelegramBot.service;

import com.example.TelegramBot.config.BotConfig;
import com.example.TelegramBot.model.User;
import com.example.TelegramBot.model.UserRepository;
import com.vdurmont.emoji.EmojiParser;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.api.methods.commands.SetMyCommands;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.methods.updatingmessages.EditMessageText;
import org.telegram.telegrambots.meta.api.objects.Message;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.commands.BotCommand;
import org.telegram.telegrambots.meta.api.objects.commands.scope.BotCommandScopeDefault;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.ReplyKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.KeyboardRow;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;

import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@Component
@Slf4j
public class TelegramBot extends TelegramLongPollingBot {
    public static final String YES_BUTTON = "YES_BUTTON";
    public static final String NO_BUTTON = "NO_BUTTON";
    @Autowired
    private UserRepository userRepository;
    final BotConfig config;
    static final String HELP_TEXT = "This bot is created to demonstrate Spring capabilities.\n\n" +
            "You can execute commands from the main menu on the left or by typing a command:\n\n" +
            "Type /start to see a welcome message\n\n" +
            "Type /mydata to see data stored about yourself\n\n" +
            "Type /help to see this message again";

    public TelegramBot(BotConfig botConfig) {
        this.config = botConfig;
        List<BotCommand> botCommands = new ArrayList<>();
        botCommands.add(new BotCommand("/start", "get a welcome message"));
        botCommands.add(new BotCommand("/mydata", "get your data stored"));
        botCommands.add(new BotCommand("/register", "register user"));
        botCommands.add(new BotCommand("/deletedata", "delete my data"));
        botCommands.add(new BotCommand("/help", "info how to use this bot"));
        botCommands.add(new BotCommand("/settings", "set your preferences"));
        botCommands.add(new BotCommand("/send", "newsletter"));
        try {
            this.execute(new SetMyCommands(botCommands, new BotCommandScopeDefault(), null));
        } catch (TelegramApiException e) {
            log.error("Error setting bot's command list: " + e.getMessage());
        }
    }

    @Override
    public String getBotUsername() {
        return config.getBotName();
    }

    @Override
    public String getBotToken() {
        return config.getToken();
    }

    @Override
    public void onUpdateReceived(Update update) {
        if (update.hasMessage() && update.getMessage().hasText()) {
            String messageText = update.getMessage().getText();
            long chatId = update.getMessage().getChatId();

            if (messageText.contains("/send") ) {
                newsLetter(update, messageText);
                return;
            }
            switch (messageText) {
                case "/start":
                    registerUser(update.getMessage());
                    start(chatId, update.getMessage().getChat().getFirstName());
                    break;
                case "/help":
                    sendMessage(chatId, HELP_TEXT);
                    break;
                case "/register":
                    register(chatId);
                    break;
                default:
                    sendMessage(chatId, "Sorry, not found command.");
            }
        } else if (update.hasCallbackQuery()) {
            answerButton(update);
        }
    }

    private void newsLetter(Update update, String messageText) {
        if (Objects.equals(update.getMessage().getChatId(), config.getOwnerId())) {
            var textToSend = EmojiParser.parseToUnicode(messageText.substring(messageText.indexOf(" ")));
            var users = userRepository.findAll();
            for (User u: users) {
                sendMessage(u.getChatId(), textToSend);
            }
        } else {
            sendMessage(update.getMessage().getChatId(), "Not access.");
        }
    }

    private void answerButton(Update update) {
        String callbackData = update.getCallbackQuery().getData();
        long messageId = update.getCallbackQuery().getMessage().getMessageId();
        long chatId = update.getCallbackQuery().getMessage().getChatId();
        if (callbackData.equals(YES_BUTTON)) {
            String text = "You pressed YES button.";
            editMessageText((int) messageId, chatId, text);
        } else if (callbackData.equals(NO_BUTTON)) {
            String text = "You pressed NO button.";
            editMessageText((int) messageId, chatId, text);
        }
    }

    private void editMessageText(int messageId, long chatId, String text) {
        EditMessageText editMessageText = new EditMessageText();
        editMessageText.setChatId(chatId);
        editMessageText.setText(text);
        editMessageText.setMessageId(messageId);

        send(editMessageText);
    }

    private void send(EditMessageText editMessageText) {
        try {
            execute(editMessageText);
        } catch (TelegramApiException telegramApiException) {
            log.error("error" + telegramApiException.getLocalizedMessage());
        }
    }

    private void register(Long chatId) {
        SendMessage message = new SendMessage();
        message.setChatId(chatId);
        message.setText("Do you really want to register?");

        message.setReplyMarkup(createInlineKeyboardMarkup());
        sendMess(message);
    }

    private InlineKeyboardMarkup createInlineKeyboardMarkup() {
        InlineKeyboardMarkup inlineKeyboardMarkup = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> rowsInLine = new ArrayList<>();
        List<InlineKeyboardButton> rowInLine = new ArrayList<>();
        var yesButton = new InlineKeyboardButton();
        yesButton.setText("Yes");
        yesButton.setCallbackData(YES_BUTTON);

        var noButton = new InlineKeyboardButton();
        noButton.setText("No");
        noButton.setCallbackData(NO_BUTTON);

        rowInLine.add(yesButton);
        rowInLine.add(noButton);

        rowsInLine.add(rowInLine);
        inlineKeyboardMarkup.setKeyboard(rowsInLine);
        return inlineKeyboardMarkup;
    }

    private void registerUser(Message msg) {
        if (userRepository.findById(msg.getChatId()).isEmpty()) {
            User user = userRepository.save(createUser(msg));
            log.info("user save " + user);
        }
    }

    private User createUser(Message msg) {
        return new User(msg.getChatId(),
                msg.getChat().getFirstName(),
                msg.getChat().getLastName(),
                msg.getChat().getUserName(),
                new Timestamp(System.currentTimeMillis()));
    }

    private void start(long chatId, String name) {
        String answer = EmojiParser.parseToUnicode(
                "Hi, " + name + ". nice to meet you!" + ":christmas_tree:");
        log.info("replied to user " + name);
        ReplyKeyboardMarkup keyboardMarkup = getReplyKeyboardStart();

        SendMessage message = new SendMessage();
        message.setChatId(chatId);
        message.setText(answer);

        message.setReplyMarkup(keyboardMarkup);
        sendMess(message);
    }

    private void sendMessage(long chatId, String textToSend) {
        SendMessage message = new SendMessage();
        message.setChatId(chatId);
        message.setText(textToSend);
        sendMess(message);
    }

    private void sendMess(SendMessage sendMessage) {
        try {
            execute(sendMessage);
        } catch (TelegramApiException telegramApiException) {
            log.error("error" + telegramApiException.getLocalizedMessage());
        }
    }

    private static ReplyKeyboardMarkup getReplyKeyboardStart() {
        ReplyKeyboardMarkup keyboardMarkup = new ReplyKeyboardMarkup();
        List<KeyboardRow> keyboardRows = new ArrayList<>();
        KeyboardRow row = new KeyboardRow();

        row.add("weather");
        row.add("get random joke");
        keyboardRows.add(row);

        row = new KeyboardRow();
        row.add("register");
        row.add("check my data");
        row.add("delete my data");
        keyboardRows.add(row);

        keyboardMarkup.setKeyboard(keyboardRows);
        return keyboardMarkup;
    }
}
