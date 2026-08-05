package org.example;

public class UserInfo {
    private String name;
    private String subject;
    private String duration;
    private String date;
    private String time;

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


    public String getname() {
        return name;
    }

    public void setname(String name) {
        this.name = name;
    }

    @Override
    public String toString() {
        return "Ваше имя: "+ name + "\n" +
                "Предмет: "+ subject+ "\n" +
                "Длительность урока: "+ duration + "\n" +
                "Дата проведения урока: "+ date+ "\n" +
                "Время проведения урока: "+ time;
    }
}
