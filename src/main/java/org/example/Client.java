package org.example;

import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.api.methods.AnswerCallbackQuery;
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

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.MonthDay;
import java.time.format.DateTimeFormatter;
import java.util.*;

public class Client extends TelegramLongPollingBot {

    private final Map<Long, Set<String>> selectedDates = new HashMap<>();
    private final Map<Long, Set<String>> selectedTimes = new HashMap<>();

    public Client() {
        slots.addAll(Database.loadLessons());
        teachers.putAll(Database.loadTeachers());
    }

    private static final List<String> SUBJECTS = List.of(
            "🧮 Математика",
            "\uD83C\uDFF4\uDB40\uDC67\uDB40\uDC62\uDB40\uDC65\uDB40\uDC6E\uDB40\uDC67\uDB40\uDC7F Английский",
            "🇪🇪 Эстонский"
    );

    private static final List<String> DURATIONS = List.of(
            "40 минут",
            "60 минут",
            "90 минут"
    );

    private final Map<Long, UserInfo> currentUserStates = new HashMap<>();

    // 1 = ждём имя ученика, 2 = ждём информацию об опыте
    private final Map<Long, Integer> registrationStep = new HashMap<>();

    // 1 = ждём имя учителя, 2 = ждём предмет
    private final Map<Long, Integer> teacherRegistrationStep = new HashMap<>();

    private final Map<Long, Teacher> teachers = new HashMap<>();
    private final Map<Long, List<Integer>> oldBookingMessageIds = new HashMap<>();
    private final Map<Long, Boolean> adminMode = new HashMap<>();

    private final Admin admin = new Admin();
    private final List<Slot> slots = new ArrayList<>();

    @Override
    public void onUpdateReceived(Update update) {
        try {
            if (update.hasMessage() && update.getMessage().hasText()) {
                handleTextMessage(update);
            } else if (update.hasCallbackQuery()) {
                handleCallback(update);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void handleTextMessage(Update update) {
        String userName = update.getMessage().getFrom().getUserName();
        String telegramName = update.getMessage().getFrom().getFirstName();
        String text = update.getMessage().getText();
        long chatID = update.getMessage().getChatId();

        // ---------- Student registration ----------

        if (registrationStep.getOrDefault(chatID, 0) == 1) {
            if (text.matches("[\\p{L} -]{2,50}")) {
                UserInfo user = currentUserStates.computeIfAbsent(chatID, id -> new UserInfo());
                user.setname(text);
                user.setStudentID(chatID);
                user.setUserName(userName);

                registrationStep.put(chatID, 2);
                SendMessage message = new SendMessage();
                message.setText("<b>Расскажите о себе и о том какой предмет вы хотите подтянуть.</b> \uD83D\uDC68\u200D\uD83C\uDF93");
                message.setChatId(chatID);
                message.setParseMode("HTML");

                executeSafely(message);
            } else {
                SendMessage message = new SendMessage();
                message.setText("❌ <b>Пожалуйста, введите имя правильно.</b>");
                message.setChatId(chatID);
                message.setParseMode("HTML");

                executeSafely(message);

                SendMessage message2 = new SendMessage();
                message2.setText("👤 <b>Пожалуйста, введите ваше имя:</b>");
                message2.setChatId(chatID);
                message2.setParseMode("HTML");

                executeSafely(message);
            }
            return;
        }

        if (registrationStep.getOrDefault(chatID, 0) == 2) {
            UserInfo user = currentUserStates.computeIfAbsent(chatID, id -> new UserInfo());
            user.setAbout(text);

            Database.updateUserAbout(chatID, text);

            registrationStep.remove(chatID);
            mainMenu(chatID);
            return;
        }

        // ---------- Teacher registration ----------

        if (teacherRegistrationStep.getOrDefault(chatID, 0) == 1) {
            if (text.matches("[\\p{L} -]{2,50}")) {
                Teacher teacher = teachers.computeIfAbsent(chatID, id -> new Teacher());
                teacher.setName(text);
                teacher.setUserName(userName);
                teacher.setTeacherID(chatID);

                teacherRegistrationStep.put(chatID, 2);
                subjectAdmin(chatID);
            } else {
                SendMessage message = new SendMessage();
                message.setText("❌ <b>Пожалуйста, введите имя правильно.</b>");
                message.setChatId(chatID);
                message.setParseMode("HTML");

                executeSafely(message);

                SendMessage message2 = new SendMessage();
                message2.setText("👤 <b>Пожалуйста, введите ваше имя:</b>");
                message2.setChatId(chatID);
                message2.setParseMode("HTML");

                executeSafely(message);
            }
            return;
        }

        // ---------- Commands and menus ----------

        if (text.equals("/start")) {
            adminMode.put(chatID, false);

            boolean alreadyExists = Database.userExists(chatID);

            UserInfo user = new UserInfo();
            user.setname(telegramName);
            user.setUserName(userName);
            user.setStudentID(chatID);

            long dbUserId = Database.upsertUser(chatID, telegramName, userName);

            if (dbUserId == -1) {
                SendMessage message = new SendMessage();
                message.setText("<b>Не удалось подключиться к базе данных. Попробуйте позже.</b> 😕");
                message.setChatId(chatID);
                message.setParseMode("HTML");

                executeSafely(message);

                return;
            }

            user.setDbUserId(dbUserId);
            currentUserStates.put(chatID, user);

            if (alreadyExists) {
                mainMenu(chatID);
            } else {
                askStudentType(chatID);
            }

            return;
        }

        if ((admin.isAdmin1(chatID) || admin.isAdmin2(chatID)) && text.equals("/admin")) {
            adminMode.put(chatID, true);

            reloadTeachers();

            if (!teachers.containsKey(chatID)) {
                newTeacher(chatID);
            } else {
                AdminMenu(chatID);
            }
            return;
        }

        if ((admin.isAdmin1(chatID) || admin.isAdmin2(chatID))
                && text.equals("🗳 Добавить слот")) {
            returnHomeAdmin(chatID, "🗳 <b>Здесь вы можете выбрать подходящие для вас даты и время:</b>");
            AdminDates(chatID);
            return;
        }

        if ((admin.isAdmin1(chatID) || admin.isAdmin2(chatID))
                && text.equals("📌 Мое расписание")) {
            returnHomeAdmin(chatID, "📌 <b>Ваше расписание:</b>");
            myBookingsAdmin(chatID);
            return;
        }

        if ((admin.isAdmin1(chatID) || admin.isAdmin2(chatID))
                && text.equals("🏠 На главную")) {
            AdminMenu(chatID);
            return;
        }

        if ((admin.isAdmin1(chatID) || admin.isAdmin2(chatID))
                && text.equals("👨‍🎓 Мои ученики")) {
            returnHomeAdmin(chatID,
                    "👨‍🎓 <b>Здесь вы сможете посмотреть ваших учеников и информацию о них:</b>");
            myStudents(chatID);
            return;
        }

        if ((admin.isAdmin1(chatID) || admin.isAdmin2(chatID))
                && text.equals("👨‍🏫 Зарегистрировать учителя")) {
            teachers.put(chatID, new Teacher());
            teacherRegistrationStep.put(chatID, 1);

            ReplyKeyboardRemove remove = new ReplyKeyboardRemove();
            remove.setRemoveKeyboard(true);

            SendMessage message = new SendMessage();
            message.setChatId(chatID);
            message.setText("👤 <b>Пожалуйста, введите ваше имя:</b>");
            message.setParseMode("HTML");
            message.setReplyMarkup(remove);
            executeSafely(message);
            return;
        }

        if (adminMode.getOrDefault(chatID, false) && SUBJECTS.contains(text)) {
            Teacher teacher = teachers.get(chatID);

            if (teacher == null) {
                newTeacher(chatID);
                return;
            }

            teacher.setSubject(text);

            long dbTeacherId = Database.upsertTeacher(
                    teacher.getTeacherID(),
                    teacher.getName(),
                    teacher.getUserName(),
                    teacher.getSubject()
            );

            if (dbTeacherId == -1) {
                SendMessage message = new SendMessage();
                message.setText("<b>Не удалось подключиться к базе данных. Попробуйте позже.</b> 😕");
                message.setChatId(chatID);
                message.setParseMode("HTML");

                executeSafely(message);
                return;
            }

            teacher.setDbTeacherId(dbTeacherId);

            teacherRegistrationStep.remove(chatID);
            SendMessage message = new SendMessage();
            message.setText("<b>Учитель зарегистрирован!</b> 👍");
            message.setChatId(chatID);
            message.setParseMode("HTML");

            executeSafely(message);
            AdminMenu(chatID);
            return;
        }

        if (text.equals("👨‍🏫 Мои учителя")) {
            returnHome(chatID,
                    "👨‍🏫 <b>Здесь вы сможете посмотреть ваших учителей и какой предмет они преподают:</b>");
            myTeachers(chatID);
            return;
        }

        if (text.equals("🖊 Записаться на урок")) {
            UserInfo user = currentUserStates.get(chatID);

            if (user == null) {
                user = new UserInfo();
                user.setname(telegramName);
                currentUserStates.put(chatID, user);
            }

            user.setStudentID(chatID);
            user.setUserName(userName);

            long dbUserId = Database.upsertUser(chatID, user.getname(), userName);

            if (dbUserId == -1) {
                SendMessage message = new SendMessage();
                message.setText("<b>Не удалось подключиться к базе данных. Попробуйте позже.</b>");
                message.setChatId(chatID);
                message.setParseMode("HTML");

                executeSafely(message);
                return;
            }

            user.setDbUserId(dbUserId);

            subjects(chatID);
            return;
        }

        if (text.equals("👶 Я новый ученик")) {
            UserInfo user = currentUserStates.computeIfAbsent(chatID, id -> new UserInfo());
            user.setStudentID(chatID);
            user.setUserName(userName);

            registrationStep.put(chatID, 1);

            ReplyKeyboardRemove remove = new ReplyKeyboardRemove();
            remove.setRemoveKeyboard(true);

            SendMessage message = new SendMessage();
            message.setChatId(chatID);
            message.setText("👤 <b>Пожалуйста, введите ваше имя:</b>");
            message.setParseMode("HTML");
            message.setReplyMarkup(remove);
            executeSafely(message);
            return;
        }

        if (text.equals("📅 Мои записи")) {
            returnHome(chatID, "📅 Ваши записи:");
            myBookings(chatID);
            return;
        }

        if (text.equals("🏠 Домой")) {
            mainMenu(chatID);
            return;
        }

        // ---------- Student booking flow ----------

        if (!adminMode.getOrDefault(chatID, false) && SUBJECTS.contains(text)) {
            if (!findTeacher(text)) {
                SendMessage message = new SendMessage();
                message.setText("<b>К сожалению, для этого предмета нет свободного времени</b> 😕");
                message.setChatId(chatID);
                message.setParseMode("HTML");

                executeSafely(message);
                mainMenu(chatID);
                return;
            }

            UserInfo user = currentUserStates.get(chatID);
            if (user == null) {
                mainMenu(chatID);
                return;
            }

            user.setSubject(text);
            teacherChoise(chatID);
            return;
        }

        if (isTeacherName(text)){

            Teacher selectedTeacher= findTeacherForName(text);
            currentUserStates.get(chatID).setTeacherID(selectedTeacher.getDbTeacherId());

            lasts(chatID);
            return;
        }

        if (DURATIONS.contains(text)) {
            UserInfo user = currentUserStates.get(chatID);

            if (user == null) {
                mainMenu(chatID);
                return;
            }

            user.setDuration(text);
            dates(chatID);
            return;
        }

        UserInfo bookingUser = currentUserStates.get(chatID);

        if (bookingUser != null
                && getAvailableDates(bookingUser.getTeacherID()).contains(text)) {

            bookingUser.setDate(text);
            times(chatID);
            return;
        }

        bookingUser = currentUserStates.get(chatID);

        if (bookingUser != null
                && bookingUser.getDate() != null
                && bookingUser.getSubject() != null) {

            Teacher teacher = getTeacherForSubject(bookingUser.getSubject());

            if (bookingUser != null
                    && bookingUser.getDate()!=null
                    && getAvailableTimes(
                    bookingUser.getDate(),
                    bookingUser.getTeacherID(),
                    bookingUser.getDuration()
            ).contains(text)) {

                bookingUser.setTime(text);
                SendMessage message = new SendMessage();
                message.setText(String.valueOf(bookingUser));
                message.setChatId(chatID);
                message.setParseMode("HTML");

                try{
                    execute(message);
                } catch(TelegramApiException e) {
                    e.printStackTrace();
                }

                submit(chatID);
                return;
            }
        }

        if (text.equals("✅ Да")) {
            confirmBooking(chatID);
            return;
        }

        if (text.equals("❌ Нет")) {
            currentUserStates.remove(chatID);
            mainMenu(chatID);
            return;
        }

        SendMessage message = new SendMessage();
        message.setText("<b>Выберите из представленных вариантов!</b>");
        message.setChatId(chatID);
        message.setParseMode("HTML");

        executeSafely(message);
    }

    private void confirmBooking(long chatID) {
        UserInfo booking = currentUserStates.get(chatID);

        if (booking == null || booking.getDate() == null || booking.getTime() == null || booking.getDuration() == null) {
            SendMessage message = new SendMessage();
            message.setText("<b>Не удалось найти данные текущей записи. Попробуйте записаться заново.</b>");
            message.setChatId(chatID);
            message.setParseMode("HTML");

            executeSafely(message);
            mainMenu(chatID);
            return;
        }
        int duration = Integer.parseInt(booking.getDuration().replace(" минут", ""));

        Slot slot = findSlot(
                booking.getDate(),
                booking.getTime(),
                booking.getTeacherID()
        );

        if (slot == null || slot.isBooked()) {
            SendMessage message = new SendMessage();
            message.setText("<b>Этот слот уже недоступен. Выберите другое время.</b> \uD83D\uDE15");
            message.setChatId(chatID);
            message.setParseMode("HTML");

            executeSafely(message);
            dates(chatID);
            return;
        }

        if (booking.getDbUserId() <= 0) {
            long dbUserId = Database.upsertUser(
                    chatID,
                    booking.getname(),
                    booking.getUserName()
            );

            if (dbUserId == -1) {
                SendMessage message = new SendMessage();
                message.setText("<b>Не удалось сохранить пользователя в базе данных.</b> \uD83D\uDE15");
                message.setChatId(chatID);
                message.setParseMode("HTML");

                executeSafely(message);
                dates(chatID);
                return;
            }

            booking.setDbUserId(dbUserId);
        }

        if (slot.getDbId() <= 0) {
            SendMessage message = new SendMessage();
            message.setText("<b>У выбранного слота нет корректного ID в базе данных.</b> \uD83D\uDE15");
            message.setChatId(chatID);
            message.setParseMode("HTML");

            executeSafely(message);
            return;
        }

        long bookingId = Database.insertBooking(
                booking.getSubject(),
                booking.getDbUserId(),
                slot.getDbId(),
                duration
        );

        if (bookingId == -1) {
            SendMessage message = new SendMessage();
            message.setText("<b>Не удалось сохранить запись в базе данных. \uD83D\uDE15 Попробуйте позже.</b>");
            message.setChatId(chatID);
            message.setParseMode("HTML");

            executeSafely(message);
            return;
        }

        booking.setDbBookingId(bookingId);
        slot.setBooked(true);

        Teacher teacher = findTeacherByBD(booking.getTeacherID());

        if (teacher != null) {
            if (teacher.getTeacherID() != chatID
                    || adminMode.getOrDefault(chatID, false)) {

                bookingMessageAdmin(teacher.getTeacherID(), booking);
            }
        }


        currentUserStates.remove(chatID);

        SendMessage message = new SendMessage();
        message.setText("<b>Вы записаны!</b> 👍");
        message.setChatId(chatID);
        message.setParseMode("HTML");

        executeSafely(message);

        mainMenu(chatID);
    }

    private void handleCallback(Update update) throws TelegramApiException {
        CallbackQuery query = update.getCallbackQuery();
        int messageID = query.getMessage().getMessageId();
        String data = query.getData();
        long chatID = query.getMessage().getChatId();

        AnswerCallbackQuery answer = new AnswerCallbackQuery();
        answer.setCallbackQueryId(query.getId());
        execute(answer);

        if (data.startsWith("deleted:")) {
            int index = Integer.parseInt(data.substring("deleted:".length()));

            List<UserInfo> bookings = Database.getBookingsForUser(chatID);

            if (index < 0 || index >= bookings.size()) {
                SendMessage message = new SendMessage();
                message.setText("<b>Запись уже была удалена или не найдена.</b> \uD83D\uDE15");
                message.setChatId(chatID);
                message.setParseMode("HTML");

                executeSafely(message);
                myBookings(chatID);
                return;
            }

            UserInfo booking = bookings.get(index);

            if (booking.getDbBookingId() > 0) {
                Database.deleteBooking(booking.getDbBookingId());
            }

            slots.clear();
            slots.addAll(Database.loadLessons());

            myBookings(chatID);
            return;
        }

        if (data.startsWith("deleteBookingAdmin:")) {
            long bookingId = Long.parseLong(
                    data.substring("deleteBookingAdmin:".length())
            );

            Database.deleteBooking(bookingId);

            slots.clear();
            slots.addAll(Database.loadLessons());

            myBookingsAdmin(chatID);
            return;
        }

        if (data.startsWith("date:")) {
            String date = data.substring(5);

            Set<String> datesForTeacher =
                    selectedDates.computeIfAbsent(chatID, id -> new TreeSet<>());

            if (datesForTeacher.contains(date)) {
                datesForTeacher.remove(date);
            } else {
                datesForTeacher.add(date);
            }

            List<String> dates = generateDates(14);
            List<List<InlineKeyboardButton>> rows = buildRows(dates, 4, chatID);

            InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
            markup.setKeyboard(rows);

            EditMessageReplyMarkup edit = new EditMessageReplyMarkup();
            edit.setChatId(chatID);
            edit.setMessageId(messageID);
            edit.setReplyMarkup(markup);

            try {
                execute(edit);
            } catch (TelegramApiException e) {
                e.printStackTrace();
            }
            return;
        }

        if (data.equals("dates_done")) {
            AdminTimes(chatID);
            return;
        }

        if (data.startsWith("time:")) {
            String time = data.substring(5);

            Set<String> timesForTeacher =
                    selectedTimes.computeIfAbsent(chatID, id -> new TreeSet<>());

            if (timesForTeacher.contains(time)) {
                timesForTeacher.remove(time);
            } else {
                timesForTeacher.add(time);
            }

            List<String> times = generateTimes(12, 20);
            List<List<InlineKeyboardButton>> rows = buildRows2(times, 4, chatID);

            InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
            markup.setKeyboard(rows);

            EditMessageReplyMarkup edit = new EditMessageReplyMarkup();
            edit.setChatId(chatID);
            edit.setMessageId(messageID);
            edit.setReplyMarkup(markup);

            try {
                execute(edit);
            } catch (TelegramApiException e) {
                e.printStackTrace();
            }
            return;
        }

        if (data.equals("times_done")) {
            createSelectedSlots(chatID);
        }
    }

    private void createSelectedSlots(long chatID) {
        int created = 0;

        Set<String> datesForTeacher =
                selectedDates.getOrDefault(chatID, Collections.emptySet());

        Set<String> timesForTeacher =
                selectedTimes.getOrDefault(chatID, Collections.emptySet());

        for (String date : datesForTeacher) {
            for (String time : timesForTeacher) {
                // Не создаём дубликат в оперативной памяти.
                Teacher teacher = teachers.get(chatID);

                if (teacher == null || teacher.getDbTeacherId() <= 0) {
                    SendMessage message = new SendMessage();
                    message.setText("<b>Не удалось определить учителя.</b> \uD83D\uDE15");
                    message.setChatId(chatID);
                    message.setParseMode("HTML");

                    executeSafely(message);
                    return;
                }

                if (findSlot(date, time, teacher.getDbTeacherId()) != null) {
                    continue;
                }

                LocalDate ld = parseDisplayDate(date);
                LocalTime lt = parseDisplayTime(time);

                long dbId = Database.insertLesson(
                        ld,
                        lt,
                        teacher.getDbTeacherId()
                );

                if (dbId == -1) {
                    continue;
                }

                Slot slot = new Slot(date, time);
                slot.setDbId(dbId);
                slot.setTeacherId(teacher.getDbTeacherId());
                slots.add(slot);
                created++;
            }
        }

        if (created > 0) {
            SendMessage message = new SendMessage();
            message.setText("<b>Слот создан!</b> \uD83D\uDE0A");
            message.setChatId(chatID);
            message.setParseMode("HTML");

            executeSafely(message);
        } else {
            SendMessage message = new SendMessage();
            message.setText("<b>Не удалось создать новые слоты.</b> \uD83D\uDE15");
            message.setChatId(chatID);
            message.setParseMode("HTML");

            executeSafely(message);
        }

        selectedDates.remove(chatID);
        selectedTimes.remove(chatID);
        AdminMenu(chatID);
    }

    @Override
    public String getBotUsername() {
        return "RepDemoBot";
    }

    @Override
    public String getBotToken() {
        return System.getenv("BOT_TOKEN");
    }

    // ---------- Main student UI ----------

    private ReplyKeyboardMarkup mainMenu(long chatID) {
        List<KeyboardRow> rows = new ArrayList<>();

        KeyboardButton bookingButton = new KeyboardButton("🖊 Записаться на урок");
        KeyboardButton myNotes = new KeyboardButton("📅 Мои записи");
        KeyboardButton myTeachers = new KeyboardButton("👨‍🏫 Мои учителя");

        KeyboardRow row1 = new KeyboardRow();
        row1.add(bookingButton);
        rows.add(row1);

        KeyboardRow row2 = new KeyboardRow();
        row2.add(myTeachers);
        row2.add(myNotes);
        rows.add(row2);

        ReplyKeyboardMarkup markup = new ReplyKeyboardMarkup();
        markup.setKeyboard(rows);
        markup.setResizeKeyboard(true);
        markup.setOneTimeKeyboard(false);

        SendMessage message = new SendMessage();
        message.setChatId(String.valueOf(chatID));
        message.setText(
                "👋 <b>Добро пожаловать!</b>\n\n" +
                        "🎓 Здесь вы можете легко управлять своими занятиями: " +
                        "записываться на уроки, следить за расписанием " +
                        "и связываться с преподавателями.\n\n" +
                        "━━━━━━━━━━━━━━━\n\n" +
                        "📝 <b>Записаться на урок</b>\n" +
                        "<i>Выберите предмет, дату и удобное время.</i>\n\n" +
                        "📅 <b>Мои записи</b>\n" +
                        "<i>Посмотрите предстоящие занятия.</i>\n\n" +
                        "👨‍🏫 <b>Мои учителя</b>\n" +
                        "<i>Информация о ваших преподавателях и связь с ними.</i>\n\n" +
                        "━━━━━━━━━━━━━━━\n\n" +
                        "👇 <b>Выберите нужный раздел</b>"
        );
        message.setReplyMarkup(markup);
        message.setParseMode("HTML");

        executeSafely(message);
        return markup;
    }

    private void subjects(long chatID) {
        KeyboardButton math = new KeyboardButton("🧮 Математика");
        KeyboardButton english = new KeyboardButton("\uD83C\uDFF4\uDB40\uDC67\uDB40\uDC62\uDB40\uDC65\uDB40\uDC6E\uDB40\uDC67\uDB40\uDC7F Английский");
        KeyboardButton estonian = new KeyboardButton("🇪🇪 Эстонский");

        KeyboardRow row = new KeyboardRow();
        row.add(math);
        row.add(english);
        row.add(estonian);

        ReplyKeyboardMarkup markup = new ReplyKeyboardMarkup();
        markup.setKeyboard(List.of(row));
        markup.setResizeKeyboard(true);
        markup.setOneTimeKeyboard(false);

        SendMessage message = new SendMessage();
        message.setText("\uD83D\uDCDA <b>Выберите предмет:</b>");
        message.setChatId(chatID);
        message.setParseMode("HTML");
        message.setReplyMarkup(markup);

        executeSafely(message);
    }

    private void askStudentType(long chatID) {
        KeyboardButton newStudent = new KeyboardButton("👶 Я новый ученик");

        KeyboardRow row = new KeyboardRow();
        row.add(newStudent);

        ReplyKeyboardMarkup markup = new ReplyKeyboardMarkup();
        markup.setKeyboard(List.of(row));
        markup.setResizeKeyboard(true);
        markup.setOneTimeKeyboard(false);

        SendMessage message = new SendMessage();
        message.setText("<b>Вы уже занимались у нас раньше?</b>");
        message.setChatId(chatID);
        message.setParseMode("HTML");
        message.setReplyMarkup(markup);

        executeSafely(message);
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
        message.setText("🕐 <b>Выберите длительность урока:</b>");
        message.setChatId(chatID);
        message.setParseMode("HTML");
        message.setReplyMarkup(markup);

        executeSafely(message);
    }

    private void dates(long chatID) {
        UserInfo user = currentUserStates.get(chatID);

        if (user == null || user.getSubject() == null) {
            mainMenu(chatID);
            return;
        }

        Set<String> availableDates =
                getAvailableDates(user.getTeacherID());

        if (availableDates.isEmpty()) {
            SendMessage message = new SendMessage();
            message.setText("<b>К сожалению, сейчас нет свободных дат</b> 😕");
            message.setChatId(chatID);
            message.setParseMode("HTML");

            executeSafely(message);
            mainMenu(chatID);
            return;
        }

        List<KeyboardRow> rows = new ArrayList<>();
        KeyboardRow row = new KeyboardRow();

        for (String date : availableDates) {
            if (row.size() == 3) {
                rows.add(row);
                row = new KeyboardRow();
            }

            row.add(new KeyboardButton(date));
        }

        if (!row.isEmpty()) {
            rows.add(row);
        }

        ReplyKeyboardMarkup markup = new ReplyKeyboardMarkup();
        markup.setKeyboard(rows);
        markup.setResizeKeyboard(true);
        markup.setOneTimeKeyboard(false);

        SendMessage message = new SendMessage();
        message.setText("📆 <b>Выберите дату проведения урока:</b>");
        message.setChatId(chatID);
        message.setParseMode("HTML");
        message.setReplyMarkup(markup);

        executeSafely(message);

    }

    private void times(long chatID) {
        UserInfo user = currentUserStates.get(chatID);

        if (user == null
                || user.getDate() == null
                || user.getSubject() == null) {

            mainMenu(chatID);
            return;
        }

        Set<String> availableTimes =
                getAvailableTimes(
                        user.getDate(),
                        user.getTeacherID(),
                        user.getDuration()
                );

        if (availableTimes.isEmpty()) {
            SendMessage message = new SendMessage();
            message.setText("<b>К сожалению, сейчас нет свободного времени</b> 😕");
            message.setChatId(chatID);
            message.setParseMode("HTML");

            executeSafely(message);
            mainMenu(chatID);
            return;
        }

        List<KeyboardRow> rows = new ArrayList<>();
        KeyboardRow row = new KeyboardRow();

        for (String time : availableTimes) {
            if (row.size() == 4) {
                rows.add(row);
                row = new KeyboardRow();
            }

            row.add(new KeyboardButton(time));
        }

        if (!row.isEmpty()) {
            rows.add(row);
        }

        ReplyKeyboardMarkup markup = new ReplyKeyboardMarkup();
        markup.setKeyboard(rows);
        markup.setResizeKeyboard(true);
        markup.setOneTimeKeyboard(false);

        SendMessage message = new SendMessage();
        message.setText("🕐 <b>Выберите время проведения урока:</b>");
        message.setChatId(chatID);
        message.setParseMode("HTML");
        message.setReplyMarkup(markup);

        executeSafely(message);
    }

    private void submit(long chatID) {
        KeyboardButton yes = new KeyboardButton("✅ Да");
        KeyboardButton no = new KeyboardButton("❌ Нет");

        KeyboardRow row = new KeyboardRow();
        row.add(yes);
        row.add(no);

        ReplyKeyboardMarkup markup = new ReplyKeyboardMarkup();
        markup.setKeyboard(List.of(row));
        markup.setResizeKeyboard(true);
        markup.setOneTimeKeyboard(false);

        SendMessage message = new SendMessage();
        message.setText("🕐 <b>Все правильно?</b>");
        message.setChatId(chatID);
        message.setParseMode("HTML");
        message.setReplyMarkup(markup);

        executeSafely(message);
    }

    private void myBookings(long chatID) {
        deleteOldBookingMessages(chatID);

        List<Integer> newMessageIds = new ArrayList<>();
        List<UserInfo> bookings =
                Database.getBookingsForUser(chatID);

        if (bookings.isEmpty()) {
            SendMessage message = new SendMessage();
            message.setChatId(chatID);
            message.setText("<b>У вас нет записей</b> 😕");
            message.setParseMode("HTML");

            try {
                Message sent = execute(message);
                newMessageIds.add(sent.getMessageId());
            } catch (TelegramApiException e) {
                e.printStackTrace();
            }
        } else {
            for (int i = 0; i < bookings.size(); i++) {
                UserInfo booking = bookings.get(i);

                StringBuilder sb = new StringBuilder();
                sb.append("<b>✏️ Запись номер: </b>").append(i + 1).append("\n");
                sb.append("<b>📚 Предмет: </b>").append(booking.getSubject()).append("\n");
                sb.append("<b>⏳ Длительность: </b>").append(booking.getDuration()).append("\n");
                sb.append("<b>📆 Дата: </b>").append(booking.getDate()).append("\n");
                sb.append("<b>🕐 Время: </b>").append(booking.getTime()).append("\n");

                InlineKeyboardButton button = new InlineKeyboardButton();
                button.setText("Удалить запись ❌");
                button.setCallbackData("deleted:" + i);

                InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
                markup.setKeyboard(List.of(List.of(button)));

                SendMessage message = new SendMessage();
                message.setChatId(String.valueOf(chatID));
                message.setText(sb.toString());
                message.setParseMode("HTML");
                message.setReplyMarkup(markup);

                try {
                    Message sent = execute(message);
                    newMessageIds.add(sent.getMessageId());
                } catch (TelegramApiException e) {
                    e.printStackTrace();
                }
            }
        }

        oldBookingMessageIds.put(chatID, newMessageIds);
    }

    // ---------- Admin UI ----------

    private void AdminMenu(long chatID) {
        List<KeyboardRow> rows = new ArrayList<>();

        KeyboardButton slotsButton = new KeyboardButton("🗳 Добавить слот");
        KeyboardButton lessonsButton = new KeyboardButton("📌 Мое расписание");
        KeyboardButton myStudents = new KeyboardButton("👨‍🎓 Мои ученики");

        KeyboardRow row1 = new KeyboardRow();
        row1.add(slotsButton);
        rows.add(row1);

        KeyboardRow row2 = new KeyboardRow();
        row2.add(lessonsButton);
        row2.add(myStudents);
        rows.add(row2);

        ReplyKeyboardMarkup markup = new ReplyKeyboardMarkup();
        markup.setKeyboard(rows);
        markup.setResizeKeyboard(true);
        markup.setOneTimeKeyboard(false);

        SendMessage message = new SendMessage();
        message.setText("👨‍🏫 <b>Панель преподавателя</b>\n\n" +
                "Здесь вы можете управлять своим расписанием и учениками.\n\n" +

                "🗳 <b>Добавить слот</b>\n" +
                "<i>Выберите даты и время, когда вы готовы провести урок.</i>\n\n" +

                "📌 <b>Мое расписание</b>\n" +
                "<i>Посмотрите все текущие записи учеников и при необходимости удалите их.</i>\n\n" +

                "👨‍🎓 <b>Мои ученики</b>\n" +
                "<i>Посмотрите список учеников и информацию о них.</i>\n\n" +

                "━━━━━━━━━━━━━━━\n\n" +
                "👇 <b>Выберите нужный раздел</b>");
        message.setChatId(chatID);
        message.setReplyMarkup(markup);
        message.setParseMode("HTML");

        try{
            execute(message);
        } catch(TelegramApiException e){
            e.printStackTrace();
        }
    }

    private List<String> generateDates(int daysAhead) {
        List<String> dates = new ArrayList<>();
        LocalDate today = LocalDate.now();

        for (int i = 1; i < daysAhead; i++) {
            LocalDate date = today.plusDays(i);
            String formatted = date.format(
                    DateTimeFormatter.ofPattern("dd.MM (E)", new Locale("ru"))
            );
            dates.add(formatted);
        }

        return dates;
    }

    private List<List<InlineKeyboardButton>> buildRows(List<String> dates, int buttonsPerRow, long chatID) {
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();
        Set<String> datesForTeacher =
                selectedDates.getOrDefault(chatID, Collections.emptySet());

        for (int i = 0; i < dates.size(); i += buttonsPerRow) {
            List<InlineKeyboardButton> row = new ArrayList<>();

            for (int j = i; j < i + buttonsPerRow && j < dates.size(); j++) {
                String date = dates.get(j);
                InlineKeyboardButton dateButton = new InlineKeyboardButton();

                if (datesForTeacher.contains(date)) {
                    dateButton.setText("🟢 " + date);
                } else {
                    dateButton.setText(date);
                }

                dateButton.setCallbackData("date:" + date);
                row.add(dateButton);
            }

            rows.add(row);
        }

        InlineKeyboardButton doneButton = new InlineKeyboardButton("Готово ✅");
        doneButton.setCallbackData("dates_done");
        rows.add(List.of(doneButton));

        return rows;
    }

    private void AdminDates(long chatID) {
        List<String> dates = generateDates(14);
        List<List<InlineKeyboardButton>> rows = buildRows(dates, 4, chatID);

        InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
        markup.setKeyboard(rows);

        SendMessage message = new SendMessage();
        message.setChatId(String.valueOf(chatID));
        message.setText("📆 <b>Выберите дату, когда вам будет удобно провести урок:</b>");
        message.setReplyMarkup(markup);
        message.setParseMode("HTML");

        executeSafely(message);
    }

    private List<String> generateTimes(int start, int end) {
        List<String> times = new ArrayList<>();

        for (int i = start; i < end; i++) {
            LocalTime time = LocalTime.of(i, 0);
            times.add(time.format(DateTimeFormatter.ofPattern("HH:mm")));
        }

        return times;
    }

    private List<List<InlineKeyboardButton>> buildRows2(List<String> times, int buttonsPerRow, long chatID) {
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();
        Set<String> timesForTeacher =
                selectedTimes.getOrDefault(chatID, Collections.emptySet());

        for (int i = 0; i < times.size(); i += buttonsPerRow) {
            List<InlineKeyboardButton> row = new ArrayList<>();

            for (int j = i; j < i + buttonsPerRow && j < times.size(); j++) {
                String time = times.get(j);
                InlineKeyboardButton timeButton = new InlineKeyboardButton();

                if (timesForTeacher.contains(time)) {
                    timeButton.setText("🟢 " + time);
                } else {
                    timeButton.setText(time);
                }

                timeButton.setCallbackData("time:" + time);
                row.add(timeButton);
            }

            rows.add(row);
        }

        InlineKeyboardButton doneButton = new InlineKeyboardButton("Готово ✅");
        doneButton.setCallbackData("times_done");
        rows.add(List.of(doneButton));

        return rows;
    }

    private void AdminTimes(long chatID) {
        List<String> times = generateTimes(12, 20);
        List<List<InlineKeyboardButton>> rows = buildRows2(times, 4, chatID);

        InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
        markup.setKeyboard(rows);

        SendMessage message = new SendMessage();
        message.setChatId(chatID);
        message.setText("🕐 <b>Выберите время, во сколько вам будет удобно провести урок:</b>");
        message.setReplyMarkup(markup);
        message.setParseMode("HTML");

        executeSafely(message);
    }

    private void myBookingsAdmin(long chatID) {
        deleteOldBookingMessages(chatID);

        List<Integer> newMessageIds = new ArrayList<>();

        List<UserInfo> bookings =
                Database.getBookingsForTeacher(chatID);

        if (bookings.isEmpty()) {
            SendMessage message = new SendMessage();
            message.setChatId(chatID);
            message.setText("<b>У вас нет уроков в расписании</b> 😕");
            message.setParseMode("HTML");

            try {
                Message sent = execute(message);
                newMessageIds.add(sent.getMessageId());
            } catch (TelegramApiException e) {
                e.printStackTrace();
            }

            oldBookingMessageIds.put(chatID, newMessageIds);
            return;
        }

        Map<Long, List<UserInfo>> bookingsByStudent = new LinkedHashMap<>();

        for (UserInfo booking : bookings) {
            bookingsByStudent
                    .computeIfAbsent(
                            booking.getStudentID(),
                            id -> new ArrayList<>()
                    )
                    .add(booking);
        }

        for (Map.Entry<Long, List<UserInfo>> entry : bookingsByStudent.entrySet()) {
            long studentChatID = entry.getKey();
            List<UserInfo> studentBookings = entry.getValue();

            StringBuilder sb = new StringBuilder();
            List<List<InlineKeyboardButton>> rows = new ArrayList<>();

            for (int i = 0; i < studentBookings.size(); i++) {
                UserInfo booking = studentBookings.get(i);
                int lessonNumber = i + 1;

                if (i == 0) {
                    sb.append("<b>👨‍🎓 Ученик:</b> ")
                            .append(booking.getname())
                            .append("\n");

                    if (booking.getUserName() != null
                            && !booking.getUserName().isBlank()) {

                        sb.append("🔗 <b>Профиль:</b> https://t.me/")
                                .append(booking.getUserName())
                                .append("\n\n");

                    } else {

                        sb.append("🔗 <b>Профиль:</b> tg://user?id=")
                                .append(studentChatID)
                                .append("\n\n");
                    }
                }

                sb.append("<b>✏️ Запись номер:</b> ")
                        .append(lessonNumber)
                        .append("\n\n");

                sb.append("<b>📚 Предмет:</b> ")
                        .append(booking.getSubject())
                        .append("\n");

                sb.append("<b>⏳ Длительность урока:</b> ")
                        .append(booking.getDuration())
                        .append("\n");

                sb.append("<b>📆 Дата проведения:</b> ")
                        .append(booking.getDate())
                        .append("\n");

                sb.append("<b>🕐 Время проведения:</b> ")
                        .append(booking.getTime())
                        .append("\n");

                if (i < studentBookings.size() - 1) {
                    sb.append("\n");
                }

                InlineKeyboardButton removeButton =
                        new InlineKeyboardButton();

                removeButton.setText(
                        "Удалить запись номер " + lessonNumber + " ❌"
                );

                removeButton.setCallbackData(
                        "deleteBookingAdmin:" + booking.getDbBookingId()
                );

                rows.add(List.of(removeButton));
            }

            InlineKeyboardMarkup markup =
                    new InlineKeyboardMarkup();

            markup.setKeyboard(rows);

            SendMessage message = new SendMessage();
            message.setChatId(chatID);
            message.setText(sb.toString());
            message.setParseMode("HTML");
            message.setReplyMarkup(markup);

            try {
                Message sent = execute(message);
                newMessageIds.add(sent.getMessageId());
            } catch (TelegramApiException e) {
                e.printStackTrace();
            }
        }

        oldBookingMessageIds.put(chatID, newMessageIds);
    }

    // ---------- Teachers ----------

    private void myStudents(long chatID) {
        Teacher teacher = teachers.get(chatID);

        if (teacher == null) {
            SendMessage message = new SendMessage();
            message.setText("<b>Сначала зарегистрируйтесь как учитель.</b>");
            message.setChatId(chatID);
            message.setParseMode("HTML");

            executeSafely(message);
            return;
        }

        List<UserInfo> bookings =
                Database.getBookingsForTeacher(chatID);

        if (bookings.isEmpty()) {
            SendMessage message = new SendMessage();
            message.setText("<b>У вас еще нет учеников</b> 😕");
            message.setChatId(chatID);
            message.setParseMode("HTML");

            executeSafely(message);
            return;
        }

        Set<Long> shownStudents = new HashSet<>();

        for (UserInfo booking : bookings) {

            // Если один ученик записан несколько раз,
            // показываем его только один раз
            if (!shownStudents.add(booking.getStudentID())) {
                continue;
            }

            StringBuilder sb = new StringBuilder();

            sb.append("👨‍🎓 <b>")
                    .append(booking.getname())
                    .append("</b>\n");

            if (booking.getUserName() != null
                    && !booking.getUserName().isBlank()) {

                sb.append("<b>🔗 Профиль:</b> https://t.me/")
                        .append(booking.getUserName())
                        .append("\n");

            } else {
                sb.append("<b>🔗 Профиль:</b> tg://user?id=")
                        .append(booking.getStudentID())
                        .append("\n");
            }

            sb.append("━━━━━━━━━━━━━\n");

            if (booking.getAbout() != null
                    && !booking.getAbout().isBlank()) {

                sb.append("📚 <b>Информация об ученике:</b>\n\n")
                        .append("💭 <i>")
                        .append(booking.getAbout())
                        .append("</i>");

            } else {
                sb.append("📚 <b>Информация об ученике отсутствует</b>");
            }

            SendMessage message = new SendMessage();
            message.setChatId(chatID);
            message.setText(sb.toString());
            message.setParseMode("HTML");

            executeSafely(message);
        }
    }

    private void newTeacher(long chatID) {
        KeyboardButton newButton = new KeyboardButton("👨‍🏫 Зарегистрировать учителя");

        KeyboardRow row = new KeyboardRow();
        row.add(newButton);

        ReplyKeyboardMarkup markup = new ReplyKeyboardMarkup();
        markup.setKeyboard(List.of(row));
        markup.setResizeKeyboard(true);
        markup.setOneTimeKeyboard(false);

        SendMessage message = new SendMessage();
        message.setText("<b>Зарегистрируйте себя как учителя</b>");
        message.setChatId(chatID);
        message.setParseMode("HTML");

        executeSafely(message);
    }

    private void subjectAdmin(long chatID) {
        KeyboardButton subject1 = new KeyboardButton("🧮 Математика");
        KeyboardButton subject2 = new KeyboardButton("\uD83C\uDFF4\uDB40\uDC67\uDB40\uDC62\uDB40\uDC65\uDB40\uDC6E\uDB40\uDC67\uDB40\uDC7F Английский");
        KeyboardButton subject3 = new KeyboardButton("🇪🇪 Эстонский");

        KeyboardRow row = new KeyboardRow();
        row.add(subject1);
        row.add(subject2);
        row.add(subject3);

        ReplyKeyboardMarkup markup = createButtonMarkup(row);
        SendMessage message = new SendMessage();
        message.setText("<b>Выберите предмет, который будете преподавать</b>");
        message.setChatId(chatID);
        message.setParseMode("HTML");
        message.setReplyMarkup(markup);

        executeSafely(message);
    }

    private boolean findTeacher(String studentSubject) {
        for (Teacher teacher : teachers.values()) {
            if (studentSubject.equals(teacher.getSubject())) {
                return true;
            }
        }
        return false;
    }

    private Teacher findTeacherForName(String teacherName){
        for (Teacher teacher : teachers.values()) {
            if (teacherName.equals(teacher.getName())){
                return teacher;
            }
        }
        return null;
    }

    private Teacher findTeacherByBD(long dbTeacherID){
        for (Teacher teacher : teachers.values()) {
            if(teacher.getDbTeacherId() == dbTeacherID){
                return teacher;
            }
        }
        return null;
    }

    private boolean isTeacherName(String teacherName){
        for (Teacher teacher : teachers.values()) {
            if (teacherName.equals(teacher.getName())){
                return true;
            }
        }
        return false;
    }

    private Teacher findTeacherForSubject(String studentSubject) {
        for (Teacher teacher : teachers.values()) {
            if (studentSubject.equals(teacher.getSubject())) {
                return teacher;
            }
        }
        return null;
    }

    private void myTeachers(long chatID) {
        List<UserInfo> studentBookings =
                Database.getBookingsForUser(chatID);

        if (studentBookings.isEmpty()) {
            SendMessage message = new SendMessage();
            message.setText("<b>Учителя отсутствуют 😕 Запишитесь хотя бы на один урок...</b>");
            message.setChatId(chatID);
            message.setParseMode("HTML");

            executeSafely(message);
            return;
        }

        Set<Long> shownTeacherIds = new HashSet<>();
        boolean found = false;

        for (UserInfo studentBooking : studentBookings) {
            for (Teacher teacher : teachers.values()) {
                if (studentBooking.getTeacherID() != teacher.getDbTeacherId()) {
                    continue;
                }

                if (!shownTeacherIds.add(teacher.getTeacherID())) {
                    continue;
                }

                found = true;

                StringBuilder sb = new StringBuilder();
                sb.append("👨‍🏫 <b>")
                        .append(teacher.getName())
                        .append("</b>\n");

                if (teacher.getUserName() != null && !teacher.getUserName().isBlank()) {
                    sb.append("🔗 Профиль: https://t.me/")
                            .append(teacher.getUserName())
                            .append("\n");
                } else {
                    sb.append("🔗 Профиль: tg://user?id=")
                            .append(teacher.getTeacherID())
                            .append("\n");
                }

                sb.append("━━━━━━━━━━━━━\n");
                sb.append("📚 <b>Предмет: </b>").append(teacher.getSubject());

                SendMessage message = new SendMessage();
                message.setChatId(chatID);
                message.setText(sb.toString());
                message.setParseMode("HTML");
                executeSafely(message);
            }
        }

        if (!found) {
            SendMessage message = new SendMessage();
            message.setText("<b>Для ваших предметов зарегистрированные учителя не найдены.</b> 😕");
            message.setChatId(chatID);
            message.setParseMode("HTML");

            executeSafely(message);
        }
    }

    private void teacherChoise(long chatID){
        String studentSubject = currentUserStates.get(chatID).getSubject();
        List<Teacher> teachers = getTeachersForSubject(studentSubject);
        List<KeyboardRow> rows= new ArrayList<>();
        for (Teacher teacher : teachers) {
            KeyboardButton teacherButton= new KeyboardButton(teacher.getName());

            KeyboardRow row= new KeyboardRow();
            row.add(teacherButton);
            rows.add(row);
        }
        ReplyKeyboardMarkup markup= new ReplyKeyboardMarkup();
        markup.setKeyboard(rows);
        markup.setOneTimeKeyboard(false);
        markup.setResizeKeyboard(true);

        SendMessage message = new SendMessage();
        message.setText("<b>Выберите учителя:</b> \uD83D\uDC68\u200D\uD83C\uDFEB");
        message.setChatId(chatID);
        message.setParseMode("HTML");
        message.setReplyMarkup(markup);

        executeSafely(message);
    }

    private List<Teacher> getTeachersForSubject(String studentSubject) {
        List<Teacher> teacherList= new ArrayList<>();
        for (Teacher teacher : teachers.values()) {
            if (studentSubject.equals(teacher.getSubject())) {
                teacherList.add(teacher);
            }
        }
        return teacherList;
    }

    // ---------- Slots ----------

    private Slot findSlot(String date, String time, long teacherId) {
        for (Slot slot : slots) {
            if (slot.getDate().equals(date)
                    && slot.getTime().equals(time)
                    && slot.getTeacherId() == teacherId) {
                return slot;
            }
        }
        return null;
    }

    private Set<String> getAvailableDates(long teacherId) {
        Set<String> dates = new TreeSet<>();

        for (Slot slot : slots) {
            if (!slot.isBooked()
                    && slot.getTeacherId() == teacherId) {

                dates.add(slot.getDate());
            }
        }

        return dates;
    }

    private Set<String> getAvailableTimes(String date, long teacherId, String duration) {
        Set<String> times = new TreeSet<>();

        Teacher teacher= findTeacherByBD(teacherId);

        if (teacher== null){
            return times;
        }

        List<UserInfo> bookings = Database.getBookingsForTeacher(teacher.getTeacherID());

        int newDuration = Integer.parseInt(duration.replace(" минут", ""));


        for (Slot slot : slots) {
            if (slot.getDate().equals(date)
                    && !slot.isBooked()
                    && slot.getTeacherId() == teacherId
                    ) {

                LocalTime newStart = LocalTime.parse(slot.getTime());
                LocalTime newEnd = newStart.plusMinutes(newDuration);

                boolean blocked= false;

                for (UserInfo booking : bookings) {
                    if (!booking.getDate().equals(date)){
                        continue;
                    }

                    LocalTime existingStart = LocalTime.parse(booking.getTime());

                    int existingDuration = Integer.parseInt(booking.getDuration().replace(" минут", ""));

                    LocalTime existingEnd = existingStart.plusMinutes(existingDuration);

                    if (hasTimeConflict(newStart, newEnd, existingStart, existingEnd)){
                        blocked = true;
                        break;
                    }
                }

                if (!blocked){
                    times.add(slot.getTime());
                }
            }
        }
        return times;
    }

    private LocalDate parseDisplayDate(String display) {
        String datePart = display.substring(0, display.indexOf(" "));
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("dd.MM");
        MonthDay monthDay = MonthDay.parse(datePart, fmt);

        LocalDate result = monthDay.atYear(LocalDate.now().getYear());

        if (result.isBefore(LocalDate.now())) {
            result = result.plusYears(1);
        }

        return result;
    }

    private LocalTime parseDisplayTime(String display) {
        return LocalTime.parse(display, DateTimeFormatter.ofPattern("HH:mm"));
    }

    // ---------- Navigation ----------

    private void returnHome(long chatID, String text) {
        KeyboardButton returnButton = new KeyboardButton("🏠 Домой");

        KeyboardRow row = new KeyboardRow();
        row.add(returnButton);

        ReplyKeyboardMarkup markup = new ReplyKeyboardMarkup();
        markup.setOneTimeKeyboard(false);
        markup.setKeyboard(List.of(row));
        markup.setResizeKeyboard(true);

        SendMessage message = new SendMessage();
        message.setText(text);
        message.setChatId(chatID);
        message.setParseMode("HTML");
        message.setReplyMarkup(markup);

        executeSafely(message);
    }

    private void returnHomeAdmin(long chatID, String text) {
        KeyboardButton returnButton = new KeyboardButton("🏠 На главную");

        KeyboardRow row = new KeyboardRow();
        row.add(returnButton);

        ReplyKeyboardMarkup markup = new ReplyKeyboardMarkup();
        markup.setOneTimeKeyboard(false);
        markup.setKeyboard(List.of(row));
        markup.setResizeKeyboard(true);

        SendMessage message = new SendMessage();
        message.setText(text);
        message.setChatId(chatID);
        message.setParseMode("HTML");
        message.setReplyMarkup(markup);

        executeSafely(message);
    }

    private Slot checkBlokedSlots(UserInfo booking){
        LocalTime startTime= LocalTime.parse(booking.getTime());

        int minutes = Integer.parseInt(booking.getDuration().replace(" минут", ""));

        LocalTime endTime= startTime.plusMinutes(minutes);

        for (Slot slot : slots) {
            if (slot.getTeacherId()==booking.getTeacherID() && slot.getDate().equals(booking.getDate())){
                LocalTime slotTime= LocalTime.parse(slot.getTime());

                if (slotTime.isAfter(startTime) && slotTime.isBefore(endTime)){
                    return slot;
                }
            }
        }
        return null;
    }

    private boolean isSlotBlocked(Slot slot, UserInfo booking){
        LocalTime startTime= LocalTime.parse(booking.getTime());

        int minutes = Integer.parseInt(booking.getDuration().replace(" минут", ""));

        LocalTime endTime= startTime.plusMinutes(minutes);

        LocalTime slotTime= LocalTime.parse(slot.getTime());

        return slot.getTeacherId()==booking.getTeacherID() &&
                slot.getDate().equals(booking.getDate()) &&
                slotTime.isAfter(startTime) &&
                slotTime.isBefore(endTime);

    }

    private boolean hasTimeConflict(LocalTime newStart, LocalTime newEnd, LocalTime existingStart, LocalTime existingEnd){
        return newStart.isBefore(existingEnd) && existingStart.isBefore(newEnd);
    }

    // ---------- Notions ----------

    private void bookingMessageAdmin(long teacherChatID, UserInfo booking){
        String name= booking.getname();
        String subject= booking.getSubject();
        String duration= booking.getDuration();
        String date= booking.getDate();
        String time= booking.getTime();

        SendMessage message= new SendMessage();
        message.setChatId(teacherChatID);

        message.setText("🔔 <b>Новая запись!</b>\n\n" +
                "<b>Ученик: </b>" + name + "\n" +
                "<b>Предмет: </b>" + subject + "\n" +
                "<b>Дата: </b>" + date + "\n" +
                "<b>Время: </b>" + time + "\n" +
                "<b>Длительность: </b>" + duration);
        message.setParseMode("HTML");

        try{
            execute(message);
        } catch(TelegramApiException e){
            e.printStackTrace();
        }
    }

    private void reloadTeachers() {
        teachers.clear();
        teachers.putAll(Database.loadTeachers());
    }


    // ---------- Helpers ----------

    private void deleteOldBookingMessages(long chatID) {
        List<Integer> oldMessages =
                oldBookingMessageIds.getOrDefault(chatID, new ArrayList<>());

        for (Integer id : oldMessages) {
            DeleteMessage deleteMessage = new DeleteMessage();
            deleteMessage.setChatId(String.valueOf(chatID));
            deleteMessage.setMessageId(id);

            try {
                execute(deleteMessage);
            } catch (TelegramApiException e) {
                e.printStackTrace();
            }
        }
    }

    private void sendMessage(long chatID, String text) {
        SendMessage message = new SendMessage();
        message.setText(text);
        message.setChatId(chatID);
        executeSafely(message);
    }

    private ReplyKeyboardMarkup createButtonMarkup(KeyboardRow row) {
        ReplyKeyboardMarkup markup = new ReplyKeyboardMarkup();
        markup.setResizeKeyboard(true);
        markup.setKeyboard(List.of(row));
        markup.setOneTimeKeyboard(false);
        return markup;
    }

    private void sendMessageButton(long chatID, String text, ReplyKeyboardMarkup markup) {
        SendMessage message = new SendMessage();
        message.setText(text);
        message.setChatId(chatID);
        message.setReplyMarkup(markup);
        executeSafely(message);
    }

    private void executeSafely(SendMessage message) {
        try {
            execute(message);
        } catch (TelegramApiException e) {
            e.printStackTrace();
        }
    }

    private Teacher getTeacherForSubject(String subject) {
        for (Teacher teacher : teachers.values()) {
            if (subject.equals(teacher.getSubject())) {
                return teacher;
            }
        }

        return null;
    }
}