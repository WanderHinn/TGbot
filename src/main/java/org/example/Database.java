package org.example;

import java.sql.*;
import java.time.LocalDate;
import java.time.LocalTime;

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
}