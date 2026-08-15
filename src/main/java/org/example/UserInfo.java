package org.example;

public class UserInfo {
    private long studentID;
    private String userName;
    private String name;
    private String about;
    private String subject;
    private String duration;
    private String date;
    private String time;

    private long dbUserId;
    private long dbBookingId;

    public long getStudentID() {
        return studentID;
    }

    public void setStudentID(long studentID) {
        this.studentID = studentID;
    }

    public String getUserName() {
        return userName;
    }

    public void setUserName(String userName) {
        this.userName = userName;
    }

    public String getDate() {
        return date;
    }

    public void setDate(String date) {
        this.date = date;
    }

    public String getDuration() {
        return duration;
    }

    public void setDuration(String duration) {
        this.duration = duration;
    }

    public String getSubject() {
        return subject;
    }

    public void setSubject(String subject) {
        this.subject = subject;
    }

    public String getTime() {
        return time;
    }

    public void setTime(String time) {
        this.time = time;
    }

    public String getAbout() {
        return about;
    }

    public void setAbout(String about) {
        this.about = about;
    }

    public String getname() {
        return name;
    }

    public void setname(String name) {
        this.name = name;
    }

    public long getDbUserId() {
        return dbUserId;
    }

    public void setDbUserId(long dbUserId) {
        this.dbUserId = dbUserId;
    }

    public long getDbBookingId() {
        return dbBookingId;
    }

    public void setDbBookingId(long dbBookingId) {
        this.dbBookingId = dbBookingId;
    }

    @Override
    public String toString() {
        return "Ваше имя: " + name + "\n" +
                "Предмет: " + subject + "\n" +
                "Длительность урока: " + duration + "\n" +
                "Дата проведения урока: " + date + "\n" +
                "Время проведения урока: " + time;
    }
}