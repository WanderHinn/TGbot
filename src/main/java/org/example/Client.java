package org.example;

import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.api.methods.AnswerCallbackQuery;
import org.telegram.telegrambots.meta.api.methods.send.SendDice;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.methods.updatingmessages.DeleteMessage;
import org.telegram.telegrambots.meta.api.methods.updatingmessages.EditMessageReplyMarkup;
import org.telegram.telegrambots.meta.api.objects.CallbackQuery;
import org.telegram.telegrambots.meta.api.objects.Message;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.ReplyKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.ReplyKeyboardRemove;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.KeyboardButton;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.KeyboardRow;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

public class Client extends TelegramLongPollingBot {

    private final Set<String> selectedDates= new TreeSet<>();
    private final Set<String> selectedTimes= new TreeSet<>();

    private static final List<String> SUBJECTS = List.of(
            "🧮 Математика",
            "\uD83C\uDFF4\uDB40\uDC67\uDB40\uDC62\uDB40\uDC65\uDB40\uDC6E\uDB40\uDC67\uDB40\uDC7F Английский",
            "\uD83C\uDDEA\uD83C\uDDEA Эстонский"
    );

    private static final List<String> DURATIONS = List.of(
            "40 минут",
            "60 минут",
            "90 минут"
    );

    private final Set<String> DATES = selectedDates;

    private final Set<String> TIMES = selectedTimes;

    private final Map<Long, UserInfo> currentUserStates = new HashMap<>();
    private final Map<Long, List<UserInfo>> savedUserStates = new HashMap<>();
    private final Map<Long, List<Integer>> oldBookingMessageIds= new HashMap<>();
    private Admin admin= new Admin();
    private final List<Slot> slots= new ArrayList<>();



    @Override
    public void onUpdateReceived(Update update) {
        try{
        if (update.hasMessage() && update.getMessage().hasText()) {
            String name = update.getMessage().getFrom().getFirstName();
            String text = update.getMessage().getText();
            long chatID = update.getMessage().getChatId();

            if (text.equals("/start")) {
                currentUserStates.put(chatID, new UserInfo());
                currentUserStates.get(chatID).setname(name);
                System.out.println(name);
                mainMenu(chatID);
            } else if (admin.isAdmin(chatID) && text.equals("/admin")) {
                AdminMenu(chatID);
            } else if (admin.isAdmin(chatID) && text.equals("Добавить слот")) {
                removeKeyboard(chatID);
                AdminDates(chatID);
            } else if (admin.isAdmin(chatID) && text.equals("На главную")) {
                AdminMenu(chatID);
            } else if (text.equals("\uD83D\uDD8A Записаться на урок")) {
                currentUserStates.put(chatID, new UserInfo());
                currentUserStates.get(chatID).setname(name);
                subjects(chatID);
            } else if (text.equals("\uD83D\uDCC5 Мои записи")) {
                myBookings(chatID);
            } else if (SUBJECTS.contains(text)) {
                UserInfo user =currentUserStates.get(chatID);

                if (user==null){
                    mainMenu(chatID);
                    return;
                }

                user.setSubject(text);
                lasts(chatID);
            } else if (DURATIONS.contains(text)) {
                UserInfo user = currentUserStates.get(chatID);

                if (user == null) {
                    mainMenu(chatID);
                    return;
                }

                user.setDuration(text);
                dates(chatID);
            } else if (getAvailableDates().contains(text)) {
                currentUserStates.get(chatID).setDate(text);
                times(chatID);
            } else if (getAvailableTimes(currentUserStates.get(chatID).getDate()).contains(text)) {
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
            } else if (text.equals("Да")) {
                UserInfo booking = currentUserStates.get(chatID);

                Slot slot= findSlot(booking.getDate(), booking.getTime());

                if (slot!=null){
                    slot.setBooked(true);
                }
                // Если отсутсвует такой ид, то создает новый список
                savedUserStates.putIfAbsent(chatID, new ArrayList<>());
                // Добвляет в долгосрочный словарь всех пользователей
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
        else if (update.hasCallbackQuery()){
            CallbackQuery query= update.getCallbackQuery();
            int messageID= query.getMessage().getMessageId();
            String data = query.getData();
            long chatID= query.getMessage().getChatId();

            AnswerCallbackQuery answer = new AnswerCallbackQuery();
            answer.setCallbackQueryId(query.getId());
            execute(answer);

            if (data.startsWith("deleted")) {
                int index = Integer.parseInt(data.substring("deleted:".length()));

                List<UserInfo> bookings = savedUserStates.get(chatID);

                UserInfo booking= bookings.get(index);

                Slot slot= findSlot(
                        booking.getDate(),
                        booking.getTime()
                );

                if (slot != null){
                    slot.setBooked(false);
                }
                bookings.remove(index);

                myBookings(chatID);
            } else if (data.startsWith("date:")) {
                String date= data.substring(5);

                if (selectedDates.contains(date)){
                    selectedDates.remove(date);
                } else{
                    selectedDates.add(date);

                }
                System.out.println(selectedDates);

                List<String> dates= generateDates(14);
                List<List<InlineKeyboardButton>> rows = buildRows(dates, 4);

                InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
                markup.setKeyboard(rows);

                EditMessageReplyMarkup edit= new EditMessageReplyMarkup();
                edit.setChatId(chatID);
                edit.setMessageId(messageID);
                edit.setReplyMarkup(markup);

                try{
                    execute(edit);
                } catch(TelegramApiException e){
                    e.printStackTrace();
                }
            } else if (data.startsWith("dates_done")) {
                AdminTimes(chatID);
            } else if (data.startsWith("time:")) {
                String time= data.substring(5);

                if (selectedTimes.contains(time)){
                    selectedTimes.remove(time);
                } else{
                    selectedTimes.add(time);

                }
                System.out.println(selectedTimes);

                List<String> times= generateTimes(12, 20);
                List<List<InlineKeyboardButton>> rows = buildRows2(times, 4);

                InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
                markup.setKeyboard(rows);

                EditMessageReplyMarkup edit= new EditMessageReplyMarkup();
                edit.setChatId(chatID);
                edit.setMessageId(messageID);
                edit.setReplyMarkup(markup);

                try{
                    execute(edit);
                } catch(TelegramApiException e){
                    e.printStackTrace();
                }
            } else if (data.startsWith("times_done")) {
                for (String date : selectedDates) {
                    for (String time : selectedTimes) {
                        Slot slot = new Slot(date, time);
                        slots.add(slot);
                    }
                }
                SendMessage message = new SendMessage();
                message.setChatId(chatID);
                message.setText("Слот создан!");

                try {
                    execute(message);
                } catch (TelegramApiException e){
                    e.printStackTrace();
                }
                selectedDates.clear();
                selectedTimes.clear();

                AdminMenu(chatID);
            } else if (data.startsWith("home_pressed")) {
                AdminMenu(chatID);
            }
        }
    } catch(Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    public String getBotUsername() {
        return "RepDemoBot";
    }

    public String getBotToken() {
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

    private void subjects(long chatID) {
        KeyboardButton math = new KeyboardButton("\uD83E\uDDEE Математика");
        KeyboardButton english = new KeyboardButton("\uD83C\uDFF4\uDB40\uDC67\uDB40\uDC62\uDB40\uDC65\uDB40\uDC6E\uDB40\uDC67\uDB40\uDC7F Английский");
        KeyboardButton estonian = new KeyboardButton("\uD83C\uDDEA\uD83C\uDDEA Эстонский");

        KeyboardRow row = new KeyboardRow();
        row.add(math);
        row.add(english);
        row.add(estonian);

        ReplyKeyboardMarkup markup = new ReplyKeyboardMarkup();
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

    private void lasts(long chatID) {
        KeyboardButton min40 = new KeyboardButton("40 минут");
        KeyboardButton min60 = new KeyboardButton("60 минут");
        KeyboardButton min90 = new KeyboardButton("90 минут");

        KeyboardRow row = new KeyboardRow();
        row.add(min40);
        row.add(min60);
        row.add(min90);

        ReplyKeyboardMarkup markup = new ReplyKeyboardMarkup();
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
        KeyboardRow row= new KeyboardRow();
        Set<String> dates= getAvailableDates();
        if (dates.isEmpty()){
            SendMessage message= new SendMessage();
            message.setChatId(chatID);
            message.setText("К сожалению, сейчас нет свободных дат");

            try {
                execute(message);
            } catch (TelegramApiException e) {
                e.printStackTrace();
            }
            mainMenu(chatID);
            return;
        }
        for (String date : dates) {
            KeyboardButton dateButton= new KeyboardButton(date);
            row.add(dateButton);
        }

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

    private void times(long chatID) {
        KeyboardRow row= new KeyboardRow();
        String selectedDate= currentUserStates.get(chatID).getDate();
        Set<String> times= getAvailableTimes(selectedDate);

        if (times.isEmpty()){
            SendMessage message= new SendMessage();
            message.setChatId(chatID);
            message.setText("К сожалению, сейчас нет свободного времени");
            try {
                execute(message);
            } catch (TelegramApiException e) {
                e.printStackTrace();
            }
            mainMenu(chatID);
            return;
        }

        for (String time : times) {
            KeyboardButton timeButton= new KeyboardButton(time);
            row.add(timeButton);
        }

        ReplyKeyboardMarkup markup = new ReplyKeyboardMarkup();
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

    private void submit(long chatID) {
        KeyboardButton yes = new KeyboardButton("Да");
        KeyboardButton no = new KeyboardButton("Нет");

        KeyboardRow row = new KeyboardRow();
        row.add(yes);
        row.add(no);

        ReplyKeyboardMarkup markup = new ReplyKeyboardMarkup();
        markup.setKeyboard(List.of(row));
        markup.setResizeKeyboard(true);
        markup.setOneTimeKeyboard(false);

        SendMessage message = new SendMessage();
        message.setChatId(String.valueOf(chatID));
        message.setText("Все правильно?");
        message.setReplyMarkup(markup);

        try {
            execute(message);
        } catch (TelegramApiException e) {
            e.printStackTrace();
        }
    }

    private void myBookings(long chatID) {

        List<Integer> oldMessages= oldBookingMessageIds.getOrDefault(chatID, new ArrayList<>());
        for (Integer ids : oldMessages) {
            DeleteMessage deleteMessage= new DeleteMessage();
            deleteMessage.setChatId(String.valueOf(chatID));
            deleteMessage.setMessageId(ids);

            try {
                execute(deleteMessage);
            } catch(TelegramApiException e){
                e.printStackTrace();
            }
        }

        // Все записи конкретного человека (если записей нет, то возвращает пустой лист (если был бы просто get возвращал бы null))
        List<Integer> newMessageIds= new ArrayList<>();
        List<UserInfo> bookings = savedUserStates.getOrDefault(chatID, new ArrayList<>());

        StringBuilder sb = new StringBuilder();
        if (bookings.isEmpty()) {
            sb.append("У вас нет записей");

            SendMessage message = new SendMessage();
            message.setChatId(chatID);
            message.setText(sb.toString());
            try {
                Message sent= execute(message);
                newMessageIds.add(sent.getMessageId());
            } catch (TelegramApiException e) {
                e.printStackTrace();
            }
        } else {
            for (int i = 0; i < bookings.size(); i++) {
                sb.setLength(0);
                // Вытаскиваем конкретную запись человека (так как их может быть несколько)
                UserInfo booking = bookings.get(i);

                sb.append("Запись номер: ").append(i + 1).append("\n");
                sb.append("Предмет: ").append(booking.getSubject()).append("\n");
                sb.append("Длительность: ").append(booking.getDuration()).append("\n");
                sb.append("Дата: ").append(booking.getDate()).append("\n");
                sb.append("Время: ").append(booking.getTime()).append("\n");

                InlineKeyboardButton button= new InlineKeyboardButton();
                button.setText("Удалить запись");
                button.setCallbackData("deleted:"+i);

                List<InlineKeyboardButton> row= new ArrayList<>(List.of(button));
                InlineKeyboardMarkup markup= new InlineKeyboardMarkup();
                markup.setKeyboard(new ArrayList<>(List.of(row)));

                SendMessage message = new SendMessage();
                message.setChatId(String.valueOf(chatID));
                message.setText(sb.toString());
                message.setReplyMarkup(markup);

                try {
                    Message sent=execute(message);
                    newMessageIds.add(sent.getMessageId());
                } catch (TelegramApiException e) {
                    e.printStackTrace();
                }
            }
        }
        oldBookingMessageIds.put(chatID, newMessageIds);
    }




    private void AdminMenu(long chatID){
        KeyboardButton slotsButton= new KeyboardButton("Добавить слот");
        KeyboardButton lessonsButton= new KeyboardButton("Мои уроки");

        KeyboardRow row = new KeyboardRow();
        row.add(slotsButton);
        row.add(lessonsButton);

        ReplyKeyboardMarkup markup = new ReplyKeyboardMarkup();
        markup.setKeyboard(List.of(row));
        markup.setResizeKeyboard(true);
        markup.setOneTimeKeyboard(false);

        SendMessage message= new SendMessage();
        message.setChatId(String.valueOf(chatID));
        message.setText("Выберите действие");
        message.setReplyMarkup(markup);

        try {
            execute(message);
        } catch(TelegramApiException e){
            e.printStackTrace();
        }
    }

    private List<String> generateDates(int daysAhead){
        List<String> dates= new ArrayList<>();
        LocalDate today= LocalDate.now();

        for (int i = 1; i < daysAhead; i++) {
            LocalDate date= today.plusDays(i);
            String formatted= date.format(DateTimeFormatter.ofPattern("dd.MM (E)", new Locale("ru")));
            dates.add(formatted);
        }
        return dates;
    }

    private List<List<InlineKeyboardButton>> buildRows(List<String> dates, int buttonsPerRow){
        List<List<InlineKeyboardButton>> rows= new ArrayList<>();
        for (int i = 0; i < dates.size(); i+=buttonsPerRow) {
            List<InlineKeyboardButton> row= new ArrayList<>();
            for (int j = i; j < i+buttonsPerRow && j<dates.size(); j++) {
                InlineKeyboardButton dateButton= new InlineKeyboardButton(dates.get(j));

                if (selectedDates.contains(dates.get(j))){
                    dateButton.setText("✅ "+ dates.get(j));
                } else{
                    dateButton.setText(dates.get(j));
                }
                dateButton.setCallbackData("date:"+ dates.get(j));
                row.add(dateButton);
            }
            rows.add(row);
        }

        InlineKeyboardButton doneButton= new InlineKeyboardButton("Готово ✅");
        InlineKeyboardButton homeButton= new InlineKeyboardButton("На главную");

        doneButton.setCallbackData("dates_done");
        homeButton.setCallbackData("home_pressed");
        List<InlineKeyboardButton> doneRow= new ArrayList<>();
        doneRow.add(doneButton);
        doneRow.add(homeButton);

        rows.add(doneRow);
        return rows;
    }

    private void AdminDates(long chatID){
        List<String> dates= generateDates(14);
        List<List<InlineKeyboardButton>> rows = buildRows(dates, 4);

        InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
        markup.setKeyboard(rows);
        
        SendMessage message = new SendMessage();
        message.setChatId(String.valueOf(chatID));
        message.setText("Выберите дату, когда вам будет удобно провести урок: ");
        message.setReplyMarkup(markup);

        try{
            execute(message);
        } catch (TelegramApiException e){
            e.printStackTrace();
        }
    }

    private List<String> generateTimes(int start, int end){
        List<String> times = new ArrayList<>();

        for (int i = start; i < end; i++) {
            LocalTime time= LocalTime.of(i, 0);
            String formatted= time.format(DateTimeFormatter.ofPattern("HH:mm"));
            times.add(formatted);
        }
        return times;
    }

    private List<List<InlineKeyboardButton>> buildRows2(List<String> times, int buttonsPerRow){
        List<List<InlineKeyboardButton>> rows= new ArrayList<>();
        for (int i = 0; i < times.size(); i+=buttonsPerRow) {
            List<InlineKeyboardButton> row= new ArrayList<>();
            for (int j = i; j < i+buttonsPerRow && j<times.size(); j++) {
                InlineKeyboardButton timeButton= new InlineKeyboardButton(times.get(j));

                if (selectedTimes.contains(times.get(j))){
                    timeButton.setText("✅ "+ times.get(j));
                } else{
                    timeButton.setText(times.get(j));
                }
                timeButton.setCallbackData("time:"+ times.get(j));
                row.add(timeButton);
            }
            rows.add(row);
        }

        InlineKeyboardButton doneButton= new InlineKeyboardButton("Готово ✅");
        InlineKeyboardButton homeButton= new InlineKeyboardButton("На главную");


        doneButton.setCallbackData("times_done");
        homeButton.setCallbackData("home_pressed");
        List<InlineKeyboardButton> doneRow= new ArrayList<>();
        doneRow.add(doneButton);
        doneRow.add(homeButton);

        rows.add(doneRow);
        return rows;
    }

    private void AdminTimes(long chatID){
        List<String> times= generateTimes(12, 20);
        List<List<InlineKeyboardButton>> rows= buildRows2(times, 4);

        InlineKeyboardMarkup markup= new InlineKeyboardMarkup();
        markup.setKeyboard(rows);

        SendMessage message = new SendMessage();
        message.setChatId(chatID);
        message.setText("Выберите время, во сколько вам будет удобно провести урок: ");
        message.setReplyMarkup(markup);

        try{
            execute(message);
        } catch(TelegramApiException e){
            e.printStackTrace();
        }
    }

    private void removeKeyboard(long chatID) throws InterruptedException {
        ReplyKeyboardRemove remove= new ReplyKeyboardRemove();
        remove.setRemoveKeyboard(true);

        SendMessage loading= new SendMessage();
        loading.setChatId(chatID);
        loading.setText("Загрузка...");
        loading.setReplyMarkup(remove);

        try{
            Message sent= execute(loading);
            DeleteMessage delete= new DeleteMessage();
            delete.setChatId(chatID);
            delete.setMessageId(sent.getMessageId());

            Thread.sleep(Duration.ofSeconds(1));
            execute(delete);

        }catch (TelegramApiException e){
            e.printStackTrace();
        }
    }

    private Slot findSlot(String date, String time){
        for (Slot slot : slots) {
            if (slot.getDate().equals(date) && slot.getTime().equals(time)){
                return slot;
            }
        }
        return null;
    }

    private Set<String> getAvailableDates(){
        Set<String> dates= new TreeSet<>();

        for (Slot slot : slots) {
            if(!slot.isBooked()){
                dates.add(slot.getDate());
            }
        }
        return dates;
    }

    private Set<String> getAvailableTimes(String date){
        Set<String> times= new TreeSet<>();

        for (Slot slot : slots) {
            if(slot.getDate().equals(date)&&!slot.isBooked()){
                times.add(slot.getTime());
            }
        }
        return times;
    }
}

