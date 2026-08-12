package org.example;

public class UserDetail {
    private String info;


    public String getInfo() {
        return info;
    }

    public void setInfo(String info) {
        this.info = info;
    }

    @Override
    public String toString() {
        return "Ваша информация: "+ info;
    }
}
