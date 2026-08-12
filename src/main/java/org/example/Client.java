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
import org.telegram.telegrambots.meta.api.objects.User;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.ReplyKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.ReplyKeyboardRemove;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.KeyboardButton;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.KeyboardRow;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;

import javax.swing.text.html.HTML;
import java.security.Key;
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
    //Этап регистрации, 1 = ожидание ответа на "Как вас зовут?", 2 = ожидание ответа на duration of studies
    private Map<Long, Integer> registrationStep = new HashMap<>();
    private final Map<Long, List<UserInfo>> savedUserStates = new HashMap<>();
    private final Map<Long, List<UserDetail>> savedUserInfo= new HashMap<>();
    private final Map<Long, List<Integer>> oldBookingMessageIds= new HashMap<>();
    private Admin admin= new Admin();
    private Admin admin2= new Admin();
    private final List<Slot> slots= new ArrayList<>();
    private UserDetail info= new UserDetail();



    @Override
    public void onUpdateReceived(Update update) {
        try{
        if (update.hasMessage() && update.getMessage().hasText()) {
            String userName= update.getMessage().getFrom().getUserName();
            String name = update.getMessage().getFrom().getFirstName();
            String text = update.getMessage().getText();
            long chatID = update.getMessage().getChatId();

            if (registrationStep.getOrDefault(chatID, 0) == 1) {
                currentUserStates.get(chatID).setname(text);
                registrationStep.put(chatID, 2);

                SendMessage message = new SendMessage();
                message.setChatId(chatID);
                message.setText("Сколько времени вы уже занимаетесь этим предметом? Если только начинаете - так и напишите.");
                try {
                    execute(message);
                } catch (TelegramApiException e) {
                    e.printStackTrace();
                }

            } else if (registrationStep.getOrDefault(chatID, 0) == 2) {
                currentUserStates.get(chatID).setAbout(text);
                registrationStep.remove(chatID);
                subjects(chatID);

            } else if (text.equals("/start")) {
                currentUserStates.put(chatID, new UserInfo());
                currentUserStates.get(chatID).setname(name);
                System.out.println(name);
                System.out.println(chatID);
                mainMenu(chatID);
            } else if ((admin.isAdmin1(chatID) || admin.isAdmin2(chatID)) && text.equals("/admin")) {
                AdminMenu(chatID);
            } else if ((admin.isAdmin1(chatID) || admin.isAdmin2(chatID)) && text.equals("\uD83D\uDDF3 Добавить слот")) {
                returnHomeAdmin(chatID, "\uD83D\uDDF3 Здесь вы можете выбрать подходящие для вас даты и время:");
                AdminDates(chatID);
            } else if ((admin.isAdmin1(chatID) || admin.isAdmin2(chatID)) && text.equals("\uD83D\uDCCC Мое расписание")) {
                returnHomeAdmin(chatID, "\uD83D\uDCCC Ваше расписание:");
                myBookingsAdmin(chatID);
            } else if ((admin.isAdmin1(chatID) || admin.isAdmin2(chatID)) && text.equals("\uD83C\uDFE0 На главную")) {
                AdminMenu(chatID);
            } else if ((admin.isAdmin1(chatID) || admin.isAdmin2(chatID)) && text.equals("\uD83D\uDC68\u200D\uD83C\uDF93 Мои ученики")) {
                returnHomeAdmin(chatID, "\uD83D\uDC68\u200D\uD83C\uDF93 Здесь вы сможете посмотреть ваших учеников и информацию о них:");
                myStudents(chatID);
            } else if (text.equals("\uD83D\uDD8A Записаться на урок")) {
                currentUserStates.put(chatID, new UserInfo());
                askStudentType(chatID);
            } else if (text.equals("\uD83D\uDC76 Я новый ученик")) {
            registrationStep.put(chatID, 1);

            SendMessage message = new SendMessage();
            message.setChatId(chatID);
            message.setText("Как вас зовут?");
            try {
                execute(message);
            } catch (TelegramApiException e) {
                e.printStackTrace();
            }

            } else if (text.equals("\uD83D\uDC68\u200D\uD83C\uDF93 Я уже учусь здесь")) {
                currentUserStates.get(chatID).setname(name);
                subjects(chatID);
            } else if (text.equals("\uD83D\uDCC5 Мои записи")) {
                returnHome(chatID);
                myBookings(chatID);
                System.out.println(savedUserStates);
            } else if (text.equals("\uD83C\uDFE0 Домой")) {
                mainMenu(chatID);
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
                message.setText("Вы записаны! \uD83D\uDC4D");

                try {
                    execute(message);
                } catch (TelegramApiException e) {
                    e.printStackTrace();
                }
                mainMenu(chatID);
            } else if (text.equals("Нет")) {
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
            } else if (data.startsWith("deleteBookingAdmin:")) {
                String data1= update.getCallbackQuery().getData();
                String[] parts= data1.split(":");
                long studentChatID= Long.parseLong(parts[1]);
                int index= Integer.parseInt(parts[2])-1;

                List<UserInfo> bookings= savedUserStates.get(studentChatID);

                UserInfo booking= bookings.get(index);
                Slot slot= findSlot(
                        booking.getDate(),
                        booking.getTime()
                );

                if(slot!=null){
                    slot.setBooked(false);
                }
                bookings.remove(index);
                if(bookings.isEmpty()){
                    savedUserStates.remove(chatID);
                }
                myBookingsAdmin(chatID);
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
        return System.getenv("8934207972:AAHdMPtS-aq_q39u7LcgGo3FXmoTNO2BGl4");
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

    private void askStudentType(long chatID) {
        KeyboardButton newStudent = new KeyboardButton("\uD83D\uDC76 Я новый ученик");
        KeyboardButton oldStudent = new KeyboardButton("\uD83D\uDC68\u200D\uD83C\uDF93 Я уже учусь здесь");

        KeyboardRow row = new KeyboardRow();
        row.add(newStudent);
        row.add(oldStudent);

        ReplyKeyboardMarkup markup = new ReplyKeyboardMarkup();
        markup.setKeyboard(List.of(row));
        markup.setResizeKeyboard(true);
        markup.setOneTimeKeyboard(false);

        SendMessage message = new SendMessage();
        message.setChatId(String.valueOf(chatID));
        message.setText("Вы уже занимались у нас раньше?");
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
        message.setText("\uD83D\uDD50 Выберите длительность урока:");
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
            message.setText("К сожалению, сейчас нет свободных дат \uD83D\uDE15");

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
        message.setText("\uD83D\uDCC6 Выберите дату проведения урока:");
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
            message.setText("К сожалению, сейчас нет свободного времени \uD83D\uDE15");
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
        message.setText("\uD83D\uDD50 Выберите время проведения урока: ");
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
            sb.append("У вас нет записей \uD83D\uDE15");

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

                sb.append("<b>✏\uFE0F Запись номер: </b>").append(i + 1).append("\n");
                sb.append("<b>\uD83D\uDCDA Предмет: </b>").append(booking.getSubject()).append("\n");
                sb.append("<b>⏳ Длительность: </b>").append(booking.getDuration()).append("\n");
                sb.append("<b>\uD83D\uDCC6 Дата: </b>").append(booking.getDate()).append("\n");
                sb.append("<b>\uD83D\uDD50 Время: </b>").append(booking.getTime()).append("\n");

                InlineKeyboardButton button= new InlineKeyboardButton();
                button.setText("Удалить запись ❌");
                button.setCallbackData("deleted:"+i);

                List<InlineKeyboardButton> row= new ArrayList<>(List.of(button));
                InlineKeyboardMarkup markup= new InlineKeyboardMarkup();
                markup.setKeyboard(new ArrayList<>(List.of(row)));

                SendMessage message = new SendMessage();
                message.setChatId(String.valueOf(chatID));
                message.setText(sb.toString());
                message.setParseMode("HTML");
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
        List<KeyboardRow> rows= new ArrayList<>();
        KeyboardButton slotsButton= new KeyboardButton("\uD83D\uDDF3 Добавить слот");
        KeyboardButton lessonsButton= new KeyboardButton("\uD83D\uDCCC Мое расписание");
        KeyboardButton myStudents= new KeyboardButton("\uD83D\uDC68\u200D\uD83C\uDF93 Мои ученики");

        KeyboardRow row1= new KeyboardRow();
        row1.add(slotsButton);
        rows.add(row1);

        KeyboardRow row2= new KeyboardRow();
        row2.add(lessonsButton);
        row2.add(myStudents);
        rows.add(row2);

        ReplyKeyboardMarkup markup = new ReplyKeyboardMarkup();
        markup.setKeyboard(rows);
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
                    dateButton.setText("\uD83D\uDFE2 "+ dates.get(j));
                } else{
                    dateButton.setText(dates.get(j));
                }
                dateButton.setCallbackData("date:"+ dates.get(j));
                row.add(dateButton);
            }
            rows.add(row);
        }

        InlineKeyboardButton doneButton= new InlineKeyboardButton("Готово ✅");

        doneButton.setCallbackData("dates_done");
        List<InlineKeyboardButton> doneRow= new ArrayList<>();
        doneRow.add(doneButton);

        rows.add(doneRow);
        return rows;
    }

    private List<KeyboardRow> buildKeyboardRows(KeyboardRow row, int buttonsPerRow){
        List<KeyboardRow> rows= new ArrayList<>();
        for (int i = 0; i < row.size(); i+=buttonsPerRow) {
            KeyboardRow row1= new KeyboardRow();
            for (int j = i; j < i+buttonsPerRow && j<row.size(); j++) {
                KeyboardButton button= new KeyboardButton();
                button.setText(String.valueOf(row.get(j)));
                row1.add(button);
            }
            rows.add(row1);
        }
        return rows;
    }

    private void AdminDates(long chatID){
        List<String> dates= generateDates(14);
        List<List<InlineKeyboardButton>> rows = buildRows(dates, 4);

        InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
        markup.setKeyboard(rows);
        
        SendMessage message = new SendMessage();
        message.setChatId(String.valueOf(chatID));
        message.setText("\uD83D\uDCC6 Выберите дату, когда вам будет удобно провести урок: ");
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
                    timeButton.setText("\uD83D\uDFE2 "+ times.get(j));
                } else{
                    timeButton.setText(times.get(j));
                }
                timeButton.setCallbackData("time:"+ times.get(j));
                row.add(timeButton);
            }
            rows.add(row);
        }

        InlineKeyboardButton doneButton= new InlineKeyboardButton("Готово ✅");


        doneButton.setCallbackData("times_done");
        List<InlineKeyboardButton> doneRow= new ArrayList<>();
        doneRow.add(doneButton);

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
        message.setText("\uD83D\uDD50 Выберите время, во сколько вам будет удобно провести урок: ");
        message.setReplyMarkup(markup);

        try{
            execute(message);
        } catch(TelegramApiException e){
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

    private void myBookingsAdmin(long chatID) {

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

        StringBuilder sb = new StringBuilder();
        if (savedUserStates.isEmpty()) {
            sb.append("У вас нет уроков в расписании \uD83D\uDE15");

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
            int lessons=1;
            for (Map.Entry<Long, List<UserInfo>> allBookings: savedUserStates.entrySet()) {
                sb.setLength(0);
                long studentChatID= allBookings.getKey();
                List<UserInfo> studentBooking= allBookings.getValue();
                List<List<InlineKeyboardButton>> rows= new ArrayList<>();
                for (UserInfo userInfo : studentBooking) {

                    InlineKeyboardButton removeButton= new InlineKeyboardButton();
                    removeButton.setCallbackData("deleteBookingAdmin:"+ studentChatID +":"+ lessons);
                    removeButton.setText("Удалить запись номер "+ lessons+ "❌");

                    List<InlineKeyboardButton> row= new ArrayList<>();
                    row.add(removeButton);
                    rows.add(row);

                    String userName= userInfo.getUserName();
                    String linkID = "tg://user?id=" + studentChatID;
                    String linkUserName="https://t.me/"+userName;
                    String name = userInfo.getname();
                    String subject = userInfo.getSubject();
                    String duration = userInfo.getDuration();
                    String date = userInfo.getDate();
                    String time = userInfo.getTime();

                    if(userInfo== studentBooking.getFirst()) {
                        if(userName!= null) {
                            sb.append("<b>\uD83D\uDC68\u200D\uD83C\uDF93 Ученик:</b>" + name + "\n" +"\uD83D\uDD17 <b>Профиль: </b>"+ "<u>https://t.me/" + userName + "</u>"+ "\n" + "\n");
                        } else{
                            sb.append("<b>\uD83D\uDC68\u200D\uD83C\uDF93 Ученик:</b> " + name + "\n" + "\uD83D\uDD17 <b>Профиль: </b>" + "<u>https://t.me/" + linkID + "</u>" + "\n" + "\n");
                        }
                    }
                    sb.append("<b>✏\uFE0F Запись номер:</b> " + lessons + "\n" + "\n");
                    sb.append("<b>\uD83D\uDCDA Предмет:</b> " + subject + "\n");
                    sb.append("<b>⌛\uFE0F Длительность урока:</b> " + duration + "\n");
                    sb.append("<b>\uD83D\uDCC6 Дата проведения:</b> " + date + "\n");
                    sb.append("<b>\uD83D\uDD50 Время проведения:</b> " + time + "\n");
                    if (userInfo!=studentBooking.getLast()){
                        sb.append("\n");
                    }
                    lessons++;
                }

                InlineKeyboardMarkup markup= new InlineKeyboardMarkup();
                markup.setKeyboard(rows);

                SendMessage message = new SendMessage();
                message.setChatId(chatID);
                message.setText(sb.toString());
                message.setParseMode("HTML");
                message.setReplyMarkup(markup);

                try{
                    Message sent= execute(message);
                    newMessageIds.add(sent.getMessageId());
                } catch (TelegramApiException e){
                    e.printStackTrace();
                }
                lessons=1;
            }
        }
        oldBookingMessageIds.put(chatID, newMessageIds);
    }

    private void returnHome(long chatID){
        KeyboardButton returnButton= new KeyboardButton("\uD83C\uDFE0 Домой");

        KeyboardRow row= new KeyboardRow();
        row.add(returnButton);

        ReplyKeyboardMarkup markup =new ReplyKeyboardMarkup();
        markup.setOneTimeKeyboard(false);
        markup.setKeyboard(List.of(row));
        markup.setResizeKeyboard(true);

        SendMessage message= new SendMessage();
        message.setChatId(chatID);
        message.setText("Ваши записи:");
        message.setReplyMarkup(markup);

        try{
            execute(message);
        } catch(TelegramApiException e){
            e.printStackTrace();
        }
    }

    private void returnHomeAdmin(long chatID, String text){
        KeyboardButton returnButton= new KeyboardButton("\uD83C\uDFE0 На главную");

        KeyboardRow row= new KeyboardRow();
        row.add(returnButton);

        ReplyKeyboardMarkup markup =new ReplyKeyboardMarkup();
        markup.setOneTimeKeyboard(false);
        markup.setKeyboard(List.of(row));
        markup.setResizeKeyboard(true);

        SendMessage message= new SendMessage();
        message.setChatId(chatID);
        message.setText(text);
        message.setReplyMarkup(markup);

        try{
            execute(message);
        } catch(TelegramApiException e){
            e.printStackTrace();
        }
    }


















    private void sendMessage(long chatID, String text){
        SendMessage message =new SendMessage();
        message.setText(text);
        message.setChatId(chatID);

        try {
            execute(message);
        } catch(TelegramApiException e){
            e.printStackTrace();
        }
    }

    private KeyboardRow createButtonRow(long chatID, String text){

        KeyboardButton button = new KeyboardButton(text);
        KeyboardRow row = new KeyboardRow();
        row.add(button);

        return row;
    }

    private ReplyKeyboardMarkup createButtonMarkup(KeyboardRow row){
        ReplyKeyboardMarkup markup = new ReplyKeyboardMarkup();
        markup.setResizeKeyboard(true);
        markup.setKeyboard(List.of(row));
        markup.setOneTimeKeyboard(false);

        return markup;
    }

    private void sendMessageButton(long chatID, String text, ReplyKeyboardMarkup markup){
        SendMessage message =new SendMessage();
        message.setText(text);
        message.setChatId(chatID);
        message.setReplyMarkup(markup);

        try {
            execute(message);
        } catch(TelegramApiException e){
            e.printStackTrace();
        }
    }

    private void myStudents(long chatID){
        StringBuilder sb= new StringBuilder();
        if (savedUserStates.isEmpty()) {
            sb.append("У вас еще нет учеников \uD83D\uDE15");

            SendMessage message = new SendMessage();
            message.setChatId(chatID);
            message.setText(sb.toString());
            try {
                execute(message);
            } catch (TelegramApiException e) {
                e.printStackTrace();
            }
        }
        for (Map.Entry<Long, List<UserInfo>> allBookings: savedUserStates.entrySet()) {
            List<UserInfo> studentBooking= allBookings.getValue();
            for (UserInfo booking : studentBooking) {
                String name = booking.getname();
                String info = booking.getAbout();

                sb.append("\uD83D\uDC68\u200D\uD83C\uDF93 Ученик: ").append(name).append("\n");
                sb.append("ℹ\uFE0F Информаиця: ").append(info);
                break;
            }
            SendMessage message = new SendMessage();
            message.setChatId(chatID);
            message.setText(sb.toString());

            try{
                execute(message);
            } catch(TelegramApiException e){
                e.printStackTrace();
            }
        }
    }
}

