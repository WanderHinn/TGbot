package org.example;

import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.User;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.ReplyKeyboard;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.ReplyKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.KeyboardButton;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.KeyboardRow;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class Demo extends TelegramLongPollingBot {


    private static final List<String> SUBJECTS=List.of(
            "🧮 Математика",
            "\uD83C\uDFF4\uDB40\uDC67\uDB40\uDC62\uDB40\uDC65\uDB40\uDC6E\uDB40\uDC67\uDB40\uDC7F Английский",
            "\uD83C\uDDEA\uD83C\uDDEA Эстонский"
    );

    private static final List<String> DURATIONS=List.of(
            "40 минут",
            "60 минут",
            "90 минут"
    );

    private static final List<String> DATES= List.of(
            "01.09",
            "02.09",
            "03.09",
            "04.09",
            "05.09",
            "06.09",
            "07.09"
    );

    private static final List<String> TIMES= List.of(
            "10:00",
            "12:00",
            "14:00",
            "18:00"
    );

    private static final Map<Long, UserInfo> currentUserStates= new HashMap<>();
    private static final Map<Long, List<UserInfo>> savedUserStates= new HashMap<>();


    @Override
    public void onUpdateReceived(Update update) {

        if(update.hasMessage() && update.getMessage().hasText()) {
            String name= update.getMessage().getFrom().getFirstName();
            String text = update.getMessage().getText();
            long chatID = update.getMessage().getChatId();

            if (text.equals("/start")) {
                currentUserStates.put(chatID, new UserInfo());
                currentUserStates.get(chatID).setname(name);
                mainMenu(chatID);
            }

            else if (text.equals("\uD83D\uDD8A Записаться на урок")) {
                subjects(chatID);
            }

            else if (SUBJECTS.contains(text)){
                currentUserStates.get(chatID).setSubject(text);
                lasts(chatID);
            }

            else if(DURATIONS.contains(text)){
                currentUserStates.get(chatID).setDuration(text);
                dates(chatID);
            }

            else if(DATES.contains(text)){
                currentUserStates.get(chatID).setDate(text);
                times(chatID);
            }

            else if(TIMES.contains(text)){
                currentUserStates.get(chatID).setTime(text);

                SendMessage message = new SendMessage();
                message.setChatId(String.valueOf(chatID));
                message.setText(String.valueOf(currentUserStates.get(chatID)));

                try {
                    execute(message);
                } catch (TelegramApiException e) {
                    e.printStackTrace();
                }

                submit(chatID);
            }
            else if(text.equals("Да")){
                UserInfo booking= currentUserStates.get(chatID);
                savedUserStates.putIfAbsent(chatID, new ArrayList<>());
                savedUserStates.get(chatID).add(booking);

                currentUserStates.remove(chatID);
                SendMessage message = new SendMessage();
                message.setChatId(String.valueOf(chatID));
                message.setText("Вы записаны!");

                try {
                    execute(message);
                } catch (TelegramApiException e) {
                    e.printStackTrace();
                }
                mainMenu(chatID);
            }
        }
    }

    @Override
    public String getBotUsername() {
        return "RepDemoBot";
    }

    public String getBotToken(){
        return "8934207972:AAHdMPtS-aq_q39u7LcgGo3FXmoTNO2BGl4";
    }

    private ReplyKeyboardMarkup mainMenu(long chatID) {
        KeyboardButton bookingButton = new KeyboardButton("\uD83D\uDD8A Записаться на урок");

        KeyboardButton myNotes = new KeyboardButton("\uD83D\uDCC5 Мои записи");

        KeyboardRow row = new KeyboardRow();
        row.add(bookingButton);
        row.add(myNotes);

        ReplyKeyboardMarkup markup = new ReplyKeyboardMarkup();
        markup.setKeyboard(List.of(row));
        markup.setResizeKeyboard(true);
        markup.setOneTimeKeyboard(false);

        SendMessage message = new SendMessage();
        message.setChatId(String.valueOf(chatID));
        message.setText("\uD83D\uDD8A Записаться на урок - позволяет вам легко " +
                "записаться на урок, выбрать длительность урока и дату.\n" +
                "\uD83D\uDCC5 Мои записи - позволяет посмотреть свои записи");
        message.setReplyMarkup(markup);

        try {
            execute(message);
        } catch (TelegramApiException e) {
            e.printStackTrace();
        }

        return markup;
    }

    private void subjects(long chatID){
        KeyboardButton math= new KeyboardButton("\uD83E\uDDEE Математика");
        KeyboardButton english= new KeyboardButton("\uD83C\uDFF4\uDB40\uDC67\uDB40\uDC62\uDB40\uDC65\uDB40\uDC6E\uDB40\uDC67\uDB40\uDC7F Английский");
        KeyboardButton estonian= new KeyboardButton("\uD83C\uDDEA\uD83C\uDDEA Эстонский");

        KeyboardRow row= new KeyboardRow();
        row.add(math);
        row.add(english);
        row.add(estonian);

        ReplyKeyboardMarkup markup= new ReplyKeyboardMarkup();
        markup.setKeyboard(List.of(row));
        markup.setResizeKeyboard(true);
        markup.setOneTimeKeyboard(false);

        SendMessage message = new SendMessage();
        message.setChatId(String.valueOf(chatID));
        message.setText("Выберите предмет:");
        message.setReplyMarkup(markup);


        try {
            execute(message);
        } catch (TelegramApiException e) {
            e.printStackTrace();
        }
    }

    private void lasts(long chatID){
        KeyboardButton min40= new KeyboardButton("40 минут");
        KeyboardButton min60= new KeyboardButton("60 минут");
        KeyboardButton min90= new KeyboardButton("90 минут");

        KeyboardRow row= new KeyboardRow();
        row.add(min40);
        row.add(min60);
        row.add(min90);

        ReplyKeyboardMarkup markup= new ReplyKeyboardMarkup();
        markup.setKeyboard(List.of(row));
        markup.setResizeKeyboard(true);
        markup.setOneTimeKeyboard(false);

        SendMessage message = new SendMessage();
        message.setChatId(String.valueOf(chatID));
        message.setText("Выберите длительность урока:");
        message.setReplyMarkup(markup);

        try {
            execute(message);
        } catch (TelegramApiException e) {
            e.printStackTrace();
        }
    }

    private void dates(long chatID) {
        KeyboardButton date1 = new KeyboardButton("01.09");
        KeyboardButton date2 = new KeyboardButton("02.09");
        KeyboardButton date3 = new KeyboardButton("03.09");
        KeyboardButton date4 = new KeyboardButton("04.09");
        KeyboardButton date5 = new KeyboardButton("05.09");
        KeyboardButton date6 = new KeyboardButton("06.09");
        KeyboardButton date7 = new KeyboardButton("07.09");

        KeyboardRow row = new KeyboardRow();
        row.add(date1);
        row.add(date2);
        row.add(date3);
        row.add(date4);
        row.add(date5);
        row.add(date6);
        row.add(date7);

        ReplyKeyboardMarkup markup = new ReplyKeyboardMarkup();
        markup.setKeyboard(List.of(row));
        markup.setResizeKeyboard(true);
        markup.setOneTimeKeyboard(false);

        SendMessage message = new SendMessage();
        message.setChatId(String.valueOf(chatID));
        message.setText("Выберите дату проведения урока:");
        message.setReplyMarkup(markup);

        try {
            execute(message);
        } catch (TelegramApiException e) {
            e.printStackTrace();
        }
    }

    private void times(long chatID){
        KeyboardButton time1= new KeyboardButton("10:00");
        KeyboardButton time2=new KeyboardButton("12:00");
        KeyboardButton time3=new KeyboardButton("14:00");
        KeyboardButton time4=new KeyboardButton("18:00");

        KeyboardRow row = new KeyboardRow();
        row.add(time1);
        row.add(time2);
        row.add(time3);
        row.add(time4);

        ReplyKeyboardMarkup markup =new ReplyKeyboardMarkup();
        markup.setKeyboard(List.of(row));
        markup.setResizeKeyboard(true);
        markup.setOneTimeKeyboard(false);

        SendMessage message = new SendMessage();
        message.setChatId(String.valueOf(chatID));
        message.setText("Выберите время проведения урока:");
        message.setReplyMarkup(markup);

        try {
            execute(message);
        } catch (TelegramApiException e) {
            e.printStackTrace();
        }
    }

    private void submit(long chatID){
        KeyboardButton yes= new KeyboardButton("Да");
        KeyboardButton no= new KeyboardButton("Нет");

        KeyboardRow row= new KeyboardRow();
        row.add(yes);
        row.add(no);

        ReplyKeyboardMarkup markup = new ReplyKeyboardMarkup();
        markup.setKeyboard(List.of(row));
        markup.setResizeKeyboard(true);
        markup.setOneTimeKeyboard(false);

        SendMessage message= new SendMessage();
        message.setChatId(String.valueOf(chatID));
        message.setText("Все правильно?");
        message.setReplyMarkup(markup);

        try {
            execute(message);
        } catch (TelegramApiException e) {
            e.printStackTrace();
        }
    }
}

