package org.example;

public class UserInfo {
    private long studentID;
    private long teacherID;
    private String userName;
    private String name;
    private String about;
    private String subject;
    private String duration;
    private String date;
    private String time;

    private long dbUserId;
    private long dbBookingId;

    public long getTeacherID() {
        return teacherID;
    }

    public void setTeacherID(long teacherID) {
        this.teacherID = teacherID;
    }

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
        return "\uD83D\uDC68\u200D\uD83C\uDF93 <b>Ваше имя: </b>" + name + "\n" +
                "\uD83D\uDCDA <b>Предмет: </b>" + subject + "\n" +
                "⏳ <b>Длительность урока: </b>" + duration + "\n" +
                "\uD83D\uDCC6 <b>Дата проведения урока: </b>" + date + "\n" +
                "\uD83D\uDD50 <b>Время проведения урока: </b>" + time;
    }
}