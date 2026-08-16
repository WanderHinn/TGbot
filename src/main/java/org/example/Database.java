package org.example;

import java.sql.*;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.HashMap;
import java.util.Map;

public class Database {

    private static final String URL = System.getenv("DB_URL");
    private static final String USER = System.getenv("DB_USER");
    private static final String PASSWORD = System.getenv("DB_PASSWORD");

    private static Connection getConnection() throws SQLException {
        return DriverManager.getConnection(URL, USER, PASSWORD);
    }

    // ---------- UsersInfo ----------

    public static long upsertUser(long chatId, String firstName, String username) {
        String sql = """
            INSERT INTO UsersInfo (chatid, firstname, username)
            VALUES (?, ?, ?)
            ON CONFLICT (chatid) DO UPDATE SET firstname = EXCLUDED.firstname, username = EXCLUDED.username
            RETURNING id
            """;
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, chatId);
            ps.setString(2, firstName);
            ps.setString(3, username);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getLong("id");
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return -1;
    }

    // ---------- Lessons ----------

    public static long insertLesson(LocalDate date, LocalTime time, long teacherId) {
        String sql = """
        INSERT INTO Lessons (lesson_date, lesson_time, teacher_id)
        VALUES (?, ?, ?)
        RETURNING id
        """;

        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setDate(1, Date.valueOf(date));
            ps.setTime(2, Time.valueOf(time));
            ps.setLong(3, teacherId);

            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getLong("id");
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }

        return -1;
    }

    // ---------- Bookings ----------

    public static long insertBooking(String subject, long userId, long lessonId) {
        String sql = "INSERT INTO Bookings (subject, user_id, lessons_id) VALUES (?, ?, ?) RETURNING id";
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, subject);
            ps.setLong(2, userId);
            ps.setLong(3, lessonId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getLong("id");
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return -1;
    }

    public static void deleteBooking(long bookingId) {
        String sql = "DELETE FROM Bookings WHERE id = ?";
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, bookingId);
            ps.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }


    // --------Teachers---------

    public static long upsertTeacher(long chatId, String name, String username, String subject) {
        String sql = """
        INSERT INTO Teachers (teacher_chat_id, name, username, subject)
        VALUES (?, ?, ?, ?)
        ON CONFLICT (teacher_chat_id)
        DO UPDATE SET
            name = EXCLUDED.name,
            username = EXCLUDED.username,
            subject = EXCLUDED.subject
        RETURNING id
        """;

        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setLong(1, chatId);
            ps.setString(2, name);
            ps.setString(3, username);
            ps.setString(4, subject);

            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getLong("id");
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }

        return -1;
    }

    public static List<Slot> loadLessons() {
        List<Slot> result = new ArrayList<>();

        String sql = """
        SELECT
            l.id,
            l.lesson_date,
            l.lesson_time,
            l.teacher_id,
            EXISTS (
                SELECT 1
                FROM Bookings b
                WHERE b.lessons_id = l.id
            ) AS booked
        FROM Lessons l
        ORDER BY l.lesson_date, l.lesson_time
        """;

        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {

            while (rs.next()) {
                LocalDate date = rs.getDate("lesson_date").toLocalDate();
                LocalTime time = rs.getTime("lesson_time").toLocalTime();

                String displayDate = date.format(
                        DateTimeFormatter.ofPattern("dd.MM (E)", new Locale("ru"))
                );

                String displayTime = time.format(
                        DateTimeFormatter.ofPattern("HH:mm")
                );

                Slot slot = new Slot(displayDate, displayTime);

                slot.setDbId(rs.getLong("id"));
                slot.setTeacherId(rs.getLong("teacher_id"));
                slot.setBooked(rs.getBoolean("booked"));

                result.add(slot);
            }

        } catch (SQLException e) {
            e.printStackTrace();
        }

        return result;
    }

    public static List<UserInfo> getBookingsForUser(long chatId) {
        List<UserInfo> result = new ArrayList<>();

        String sql = """
        SELECT
            b.id AS booking_id,
            b.subject,
            u.id AS user_id,
            u.chatid,
            u.firstname,
            u.username,
            l.lesson_date,
            l.lesson_time
        FROM Bookings b
        JOIN UsersInfo u ON u.id = b.user_id
        JOIN Lessons l ON l.id = b.lessons_id
        WHERE u.chatid = ?
        ORDER BY l.lesson_date, l.lesson_time
        """;

        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setLong(1, chatId);

            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    UserInfo booking = new UserInfo();

                    booking.setDbBookingId(rs.getLong("booking_id"));
                    booking.setDbUserId(rs.getLong("user_id"));
                    booking.setStudentID(rs.getLong("chatid"));
                    booking.setname(rs.getString("firstname"));
                    booking.setUserName(rs.getString("username"));
                    booking.setSubject(rs.getString("subject"));

                    LocalDate date =
                            rs.getDate("lesson_date").toLocalDate();

                    LocalTime time =
                            rs.getTime("lesson_time").toLocalTime();

                    booking.setDate(date.format(
                            DateTimeFormatter.ofPattern(
                                    "dd.MM (E)",
                                    new Locale("ru")
                            )
                    ));

                    booking.setTime(time.format(
                            DateTimeFormatter.ofPattern("HH:mm")
                    ));

                    result.add(booking);
                }
            }

        } catch (SQLException e) {
            e.printStackTrace();
        }

        return result;
    }

    public static Map<Long, Teacher> loadTeachers() {
        Map<Long, Teacher> result = new HashMap<>();

        String sql = """
        SELECT
            id,
            teacher_chat_id,
            name,
            username,
            subject
        FROM Teachers
        """;

        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {

            while (rs.next()) {
                Teacher teacher = new Teacher();

                teacher.setDbTeacherId(rs.getLong("id"));
                teacher.setTeacherID(rs.getLong("teacher_chat_id"));
                teacher.setName(rs.getString("name"));
                teacher.setUserName(rs.getString("username"));
                teacher.setSubject(rs.getString("subject"));

                result.put(
                        teacher.getTeacherID(),
                        teacher
                );
            }

        } catch (SQLException e) {
            e.printStackTrace();
        }

        return result;
    }

    public static List<UserInfo> getBookingsForTeacher(long teacherChatId) {
        List<UserInfo> result = new ArrayList<>();

        String sql = """
        SELECT
            b.id AS booking_id,
            b.subject,
            u.id AS user_id,
            u.chatid,
            u.firstname,
            u.username,
            l.lesson_date,
            l.lesson_time
        FROM Bookings b
        JOIN UsersInfo u ON u.id = b.user_id
        JOIN Lessons l ON l.id = b.lessons_id
        JOIN Teachers t ON t.id = l.teacher_id
        WHERE t.teacher_chat_id = ?
        ORDER BY l.lesson_date, l.lesson_time
        """;

        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setLong(1, teacherChatId);

            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    UserInfo booking = new UserInfo();

                    booking.setDbBookingId(rs.getLong("booking_id"));
                    booking.setDbUserId(rs.getLong("user_id"));
                    booking.setStudentID(rs.getLong("chatid"));
                    booking.setname(rs.getString("firstname"));
                    booking.setUserName(rs.getString("username"));
                    booking.setSubject(rs.getString("subject"));

                    LocalDate date =
                            rs.getDate("lesson_date").toLocalDate();

                    LocalTime time =
                            rs.getTime("lesson_time").toLocalTime();

                    booking.setDate(date.format(
                            DateTimeFormatter.ofPattern(
                                    "dd.MM (E)",
                                    new Locale("ru")
                            )
                    ));

                    booking.setTime(time.format(
                            DateTimeFormatter.ofPattern("HH:mm")
                    ));

                    result.add(booking);
                }
            }

        } catch (SQLException e) {
            e.printStackTrace();
        }

        return result;
    }
}