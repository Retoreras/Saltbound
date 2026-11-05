package com.example.saltbound;

/** Предметы быстрого слота. Пока только красная фляга; сюда же позже добавятся другие фляги, бомбы и т.д. */
public enum QuickItem {
    RED_FLASK("quick.saltbound.red_flask");

    public final String translationKey;

    QuickItem(String translationKey) {
        this.translationKey = translationKey;
    }
}
