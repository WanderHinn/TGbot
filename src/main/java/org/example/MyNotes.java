package org.example;

import org.telegram.telegrambots.meta.api.objects.User;

import java.util.List;
import java.util.Map;

public class MyNotes {
    private long chatID;
    private Map<Long, List<UserInfo>> saved;

    @Override
    public String toString() {
        return "Мои записи: "+ "\n" +
                saved.get(chatID);
    }
}
