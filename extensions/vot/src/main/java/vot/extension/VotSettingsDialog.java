package vot.extension;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

/**
 * Настройки VOT в стиле оригинального userscript.
 * Ключи — те же, что в storageKeys скрипта.
 */
public final class VotSettingsDialog {
    private static final int BG = 0xFF121212;
    private static final int CARD = 0xFF1E1E1E;
    private static final int TEXT = 0xFFE8E8E8;
    private static final int MUTED = 0xFF9E9E9E;
    private static final int ACCENT = 0xFF8BB4F5;
    private static final int LIVELY = 0xFFE91E63;

    private static final String[] SOURCE_VALUES = {"auto", "ru", "en", "kk"};
    private static final String[] SOURCE_LABELS = {"Авто", "Русский", "English", "Қазақша"};
    private static final String[] TARGET_VALUES = {"ru", "en", "kk"};
    private static final String[] TARGET_LABELS = {"Русский", "English", "Қазақша"};

    private VotSettingsDialog() {
    }

    public static void show(Activity activity) {
        Dialog dialog = new Dialog(activity);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);

        Context context = activity;
        LinearLayout root = new LinearLayout(context);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(BG);
        int pad = dp(context, 16);
        root.setPadding(pad, dp(context, 20), pad, pad);

        TextView header = new TextView(context);
        header.setText("Настройки");
        header.setTextColor(TEXT);
        header.setTextSize(22f);
        header.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        header.setPadding(0, 0, 0, dp(context, 12));
        root.addView(header);

        TextView translate = new TextView(context);
        translate.setText("Перевести текущее видео");
        translate.setTextColor(ACCENT);
        translate.setTextSize(17f);
        translate.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        translate.setPadding(0, dp(context, 6), 0, dp(context, 14));
        root.addView(translate);
        translate.setOnClickListener(view -> {
            int result = VotController.translateCurrent();
            if (result == 0) {
                Toast.makeText(context, "VOT: открой видео", Toast.LENGTH_SHORT).show();
                return;
            }
            dialog.dismiss();
        });

        // ---- Голос ----
        LinearLayout voices = card(context);
        addCategory(voices, "Голос");

        TextView[] normalViews = addVoiceRow(voices, "Обычные голоса",
                "Быстрая и качественная озвучка");
        TextView[] livelyViews = addVoiceRow(voices, "Живые голоса",
                "Максимальное сходство. Будто все знают русский");

        Runnable[] updateVoice = new Runnable[1];
        updateVoice[0] = () -> {
            boolean lively = VotSettings.livelyVoice();
            styleVoice(normalViews, !lively, TEXT);
            styleVoice(livelyViews, lively, LIVELY);
        };
        normalViews[0].setOnClickListener(view -> {
            VotSettings.setBool("useLivelyVoice", false);
            updateVoice[0].run();
        });
        livelyViews[0].setOnClickListener(view -> {
            VotSettings.setBool("useLivelyVoice", true);
            updateVoice[0].run();
            if (VotSettings.apiToken().isEmpty()) {
                VotOAuth.login(activity, () -> {
                    updateVoice[0].run();
                    VotController.toast("Голос: живые");
                });
            } else {
                VotController.toast("Голос: живые");
            }
        });
        updateVoice[0].run();
        root.addView(voices);

        // ---- Настройки перевода ----
        LinearLayout translation = card(context);
        addCategory(translation, "Настройки перевода");
        addSwitch(translation, "autoTranslate", "Переводить при открытии", false);
        addLanguages(translation);
        root.addView(translation);

        // ---- Громкость ----
        LinearLayout volume = card(context);
        addCategory(volume, "Громкость");
        LinearLayout translationVolume = addSlider(volume, "defaultVolume", "Громкость перевода", 100, 100);
        Switch duck = addSwitch(volume, "enabledAutoVolume",
                "Автоматически уменьшать громкость видео", true);
        addInfo(volume, "", "Приглушать оригинал, пока включён перевод");
        LinearLayout originalVolume = addSlider(volume, "originalVolume",
                "Громкость оригинала", 100, 100);
        Switch smart = addSwitch(volume, "enabledSmartDucking", "Адаптивная громкость", true);
        addInfo(volume, "", "Приглушать оригинал только пока звучит перевод (пауза — оригинал возвращается)");
        Switch sync = addSwitch(volume, "syncVolume",
                "Связать громкость перевода и видео", false);

        Runnable updateVolumeVisibility = () -> {
            boolean showOriginal = VotSettings.getBool("enabledAutoVolume", true);
            originalVolume.setVisibility(showOriginal ? View.VISIBLE : View.GONE);
            boolean syncOn = VotSettings.getBool("syncVolume", false);
            translationVolume.setVisibility(syncOn ? View.GONE : View.VISIBLE);
        };
        updateVolumeVisibility.run();

        duck.setOnCheckedChangeListener((buttonView, checked) -> {
            VotSettings.setBool("enabledAutoVolume", checked);
            VotController.refreshDucking();
            updateVolumeVisibility.run();
        });
        smart.setOnCheckedChangeListener((buttonView, checked) -> {
            VotSettings.setBool("enabledSmartDucking", checked);
            VotController.refreshDucking();
        });
        sync.setOnCheckedChangeListener((buttonView, checked) -> {
            VotSettings.setBool("syncVolume", checked);
            VotController.refreshVolume();
            updateVolumeVisibility.run();
        });
        root.addView(volume);

        // ---- Прочее ----
        LinearLayout misc = card(context);
        addCategory(misc, "Другие настройки");
        addSwitch(misc, "sendNotifyOnComplete",
                "Отправлять уведомление о том, что видео было переведено", false);
        root.addView(misc);

        // ---- Прокси ----
        LinearLayout proxy = card(context);
        addCategory(proxy, "Настройки прокси-сервера");
        addHost(proxy);
        root.addView(proxy);

        // ---- Аккаунт ----
        LinearLayout account = card(context);
        addCategory(account, "Аккаунт");

        TextView accountStatus = new TextView(context);
        accountStatus.setTextColor(TEXT);
        accountStatus.setTextSize(14f);
        accountStatus.setPadding(0, dp(context, 4), 0, dp(context, 8));
        root.setTag(accountStatus);
        Runnable updateAccount = () -> {
            String user = VotSettings.getString("accountUsername", "");
            accountStatus.setText(user.isEmpty()
                    ? "Вход не выполнен (живые голоса без токена недоступны)"
                    : "Вы вошли: " + user);
        };
        updateAccount.run();
        account.addView(accountStatus);

        TextView login = new TextView(context);
        login.setText("Войти с помощью Yandex ID");
        login.setTextColor(ACCENT);
        login.setTextSize(15f);
        login.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        login.setPadding(0, dp(context, 8), 0, dp(context, 8));
        login.setOnClickListener(view -> VotOAuth.login(activity, updateAccount));
        account.addView(login);

        addToken(account);

        TextView logout = new TextView(context);
        logout.setText("Выйти");
        logout.setTextColor(MUTED);
        logout.setTextSize(14f);
        logout.setPadding(0, dp(context, 8), 0, dp(context, 4));
        logout.setOnClickListener(view -> {
            VotSettings.setString("accountToken", "");
            VotSettings.setString("accountUsername", "");
            VotSettings.setBool("useLivelyVoice", false);
            VotController.init(context);
            updateAccount.run();
            updateVoice[0].run();
            Toast.makeText(context, "Вход отменён", Toast.LENGTH_SHORT).show();
        });
        account.addView(logout);

        root.addView(account);

        // ---- О расширении ----
        LinearLayout about = card(context);
        addCategory(about, "О расширении");
        addInfo(about, "Voice Over Translation для Android",
                "Порт userscript · API Yandex VOT · ключи настроек совпадают со скриптом");
        TextView reset = new TextView(context);
        reset.setText("Сбросить настройки");
        reset.setTextColor(LIVELY);
        reset.setTextSize(16f);
        reset.setPadding(0, dp(context, 10), 0, dp(context, 4));
        reset.setOnClickListener(view -> {
            VotSettings.reset();
            VotController.init(context);
            Toast.makeText(context, "Настройки сброшены", Toast.LENGTH_SHORT).show();
            dialog.dismiss();
        });
        about.addView(reset);
        root.addView(about);

        ScrollView scroll = new ScrollView(context);
        scroll.setBackgroundColor(BG);
        scroll.addView(root);

        dialog.setContentView(scroll);
        Window window = dialog.getWindow();
        if (window != null) {
            window.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
            window.setBackgroundDrawableResource(android.R.color.black);
        }
        dialog.show();
    }

    // ---------------- helpers ----------------

    /** Возвращает [name, mark]; name — корневой View строки (для клика). */
    private static TextView[] addVoiceRow(LinearLayout card, String title, String subtitle) {
        Context context = card.getContext();
        LinearLayout row = new LinearLayout(context);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setPadding(0, dp(context, 8), 0, dp(context, 8));

        LinearLayout texts = new LinearLayout(context);
        texts.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams textsParams = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        texts.setLayoutParams(textsParams);

        TextView name = new TextView(context);
        name.setText(title);
        name.setTextSize(16f);
        name.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        texts.addView(name);

        TextView summary = new TextView(context);
        summary.setText(subtitle);
        summary.setTextColor(MUTED);
        summary.setTextSize(12f);
        texts.addView(summary);

        row.addView(texts);

        TextView mark = new TextView(context);
        mark.setText("✓");
        mark.setTextSize(18f);
        mark.setGravity(Gravity.CENTER_VERTICAL);
        mark.setPadding(dp(context, 8), 0, 0, 0);
        row.addView(mark);

        card.addView(row);
        return new TextView[]{name, mark};
    }

    private static void styleVoice(TextView[] views, boolean active, int activeColor) {
        views[0].setTextColor(active ? activeColor : TEXT);
        views[1].setTextColor(active ? activeColor : Color.TRANSPARENT);
    }

    private static LinearLayout card(Context context) {
        LinearLayout layout = new LinearLayout(context);
        layout.setOrientation(LinearLayout.VERTICAL);
        GradientDrawable background = new GradientDrawable();
        background.setShape(GradientDrawable.RECTANGLE);
        background.setCornerRadius(dp(context, 16));
        background.setColor(CARD);
        layout.setBackground(background);
        int pad = dp(context, 14);
        layout.setPadding(pad, dp(context, 10), pad, dp(context, 10));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.bottomMargin = dp(context, 12);
        layout.setLayoutParams(params);
        return layout;
    }

    private static void addCategory(LinearLayout card, String title) {
        TextView view = new TextView(card.getContext());
        view.setText(title);
        view.setTextColor(MUTED);
        view.setTextSize(13f);
        view.setPadding(0, dp(card.getContext(), 6), 0, dp(card.getContext(), 6));
        card.addView(view);
    }

    private static void addInfo(LinearLayout card, String title, String subtitle) {
        TextView view = new TextView(card.getContext());
        view.setText(title + "\n" + subtitle);
        view.setTextColor(MUTED);
        view.setTextSize(13f);
        view.setPadding(0, 0, 0, dp(card.getContext(), 6));
        card.addView(view);
    }

    private static Switch addSwitch(LinearLayout card, String key, String title, boolean fallback) {
        Context context = card.getContext();
        Switch toggle = new Switch(context);
        toggle.setText(title);
        toggle.setTextColor(TEXT);
        toggle.setTextSize(15f);
        toggle.setChecked(VotSettings.getBool(key, fallback));
        toggle.setPadding(0, dp(context, 6), 0, dp(context, 6));
        toggle.setOnCheckedChangeListener((buttonView, checked) -> VotSettings.setBool(key, checked));
        card.addView(toggle);
        return toggle;
    }

    private static void addSelect(
            LinearLayout card, String key, String title, String[] labels, String[] values,
            String fallback) {
        Context context = card.getContext();
        String current = VotSettings.getString(key, fallback);
        int index = 0;
        for (int i = 0; i < values.length; i++) {
            if (values[i].equals(current)) index = i;
        }

        TextView row = new TextView(context);
        row.setText(title + ":  " + labels[index]);
        row.setTextColor(TEXT);
        row.setTextSize(15f);
        row.setPadding(0, dp(context, 10), 0, dp(context, 10));
        final int[] selected = {index};
        row.setOnClickListener(view -> new AlertDialog.Builder(context)
                .setTitle(title)
                .setSingleChoiceItems(labels, selected[0], (dialog, which) -> {
                    selected[0] = which;
                    VotSettings.setString(key, values[which]);
                    row.setText(title + ":  " + labels[which]);
                    VotController.init(context);
                    dialog.dismiss();
                })
                .show());
        card.addView(row);
    }

    private static void addLanguages(LinearLayout card) {
        Context context = card.getContext();
        LinearLayout row = new LinearLayout(context);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setPadding(0, dp(context, 8), 0, dp(context, 8));

        TextView source = new TextView(context);
        source.setTextColor(TEXT);
        source.setTextSize(15f);
        source.setPadding(0, dp(context, 10), 0, dp(context, 10));
        updateLanguageLabel(source, "Язык (с): ", VotSettings.sourceLanguage(), SOURCE_VALUES, SOURCE_LABELS);
        LinearLayout.LayoutParams sourceParams = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        source.setLayoutParams(sourceParams);
        source.setOnClickListener(view -> pickLanguage(context, "Язык видео",
                VotSettings.getString("sourceLanguage", "auto"),
                SOURCE_VALUES, SOURCE_LABELS, "sourceLanguage",
                value -> updateLanguageLabel(source, "Язык (с): ", value, SOURCE_VALUES, SOURCE_LABELS)));
        row.addView(source);

        TextView target = new TextView(context);
        target.setTextColor(TEXT);
        target.setTextSize(15f);
        target.setPadding(0, dp(context, 10), 0, dp(context, 10));
        updateLanguageLabel(target, "на: ", VotSettings.targetLanguage(), TARGET_VALUES, TARGET_LABELS);
        LinearLayout.LayoutParams targetParams = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        target.setLayoutParams(targetParams);
        target.setOnClickListener(view -> pickLanguage(context, "Язык перевода",
                VotSettings.targetLanguage(),
                TARGET_VALUES, TARGET_LABELS, "responseLanguage",
                value -> updateLanguageLabel(target, "на: ", value, TARGET_VALUES, TARGET_LABELS)));
        row.addView(target);

        card.addView(row);
    }

    private interface ValueCallback {
        void onValue(String value);
    }

    private static void pickLanguage(
            Context context, String title, String current,
            String[] values, String[] labels, String key, ValueCallback callback) {
        int index = 0;
        for (int i = 0; i < values.length; i++) {
            if (values[i].equals(current)) index = i;
        }
        new AlertDialog.Builder(context)
                .setTitle(title)
                .setSingleChoiceItems(labels, index, (dialog, which) -> {
                    VotSettings.setString(key, values[which]);
                    VotController.init(context);
                    callback.onValue(values[which]);
                    dialog.dismiss();
                })
                .show();
    }

    private static void updateLanguageLabel(
            TextView view, String prefix, String value, String[] values, String[] labels) {
        String label = value;
        for (int i = 0; i < values.length; i++) {
            if (values[i].equals(value)) label = labels[i];
        }
        view.setText(prefix + label);
    }

    private static LinearLayout addSlider(
            LinearLayout card, String key, String title, int max, int fallback) {
        Context context = card.getContext();
        LinearLayout group = new LinearLayout(context);
        group.setOrientation(LinearLayout.VERTICAL);
        TextView label = new TextView(context);
        label.setText(title + ": " + VotSettings.getInt(key, fallback) + "%");
        label.setTextColor(TEXT);
        label.setTextSize(15f);
        group.addView(label);

        SeekBar seekBar = new SeekBar(context);
        seekBar.setMax(max);
        seekBar.setProgress(VotSettings.getInt(key, fallback));
        seekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar bar, int progress, boolean fromUser) {
                label.setText(title + ": " + progress + "%");
            }

            @Override
            public void onStartTrackingTouch(SeekBar bar) {
            }

            @Override
            public void onStopTrackingTouch(SeekBar bar) {
                VotSettings.setInt(key, bar.getProgress());
                if ("defaultVolume".equals(key)) {
                    VotController.setTranslationVolume(bar.getProgress());
                } else if ("originalVolume".equals(key)) {
                    VotController.setOriginalVolume(bar.getProgress());
                }
            }
        });
        group.addView(seekBar);
        card.addView(group);
        return group;
    }

    private static void addHost(LinearLayout card) {
        Context context = card.getContext();
        EditText host = new EditText(context);
        host.setText(VotSettings.host());
        host.setHint("https://api.browser.yandex.ru");
        host.setTextColor(TEXT);
        host.setTextSize(14f);
        host.setOnFocusChangeListener((view, focused) -> {
            if (!focused) {
                VotSettings.setString("proxyWorkerHost", host.getText().toString().trim());
                VotController.init(context);
            }
        });
        card.addView(host);
    }

    private static void addToken(LinearLayout card) {
        Context context = card.getContext();
        TextView hint = new TextView(context);
        hint.setText("Или вставьте OAuth-токен Яндекса вручную");
        hint.setTextColor(MUTED);
        hint.setTextSize(12f);
        card.addView(hint);

        EditText token = new EditText(context);
        token.setText(VotSettings.apiToken());
        token.setHint("Войти с помощью токена");
        token.setTextColor(TEXT);
        token.setTextSize(14f);
        token.setOnFocusChangeListener((view, focused) -> {
            if (!focused) {
                VotSettings.setString("accountToken", token.getText().toString().trim());
                VotController.init(context);
            }
        });
        card.addView(token);
    }

    private static int dp(Context context, int value) {
        return Math.round(value * context.getResources().getDisplayMetrics().density);
    }
}
