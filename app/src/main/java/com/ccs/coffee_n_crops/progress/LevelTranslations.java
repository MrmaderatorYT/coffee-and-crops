package com.ccs.coffee_n_crops.progress;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Localized lesson text keyed by the same stable IDs as saved programs. */
public final class LevelTranslations {
    public record Text(String title, String goal, String hint) { }

    private record Template(Pattern pattern, String replacement) { }

    public static final LevelTranslations ENGLISH = new LevelTranslations();

    private final Map<String, Text> levels = new HashMap<>();
    private final Map<String, String> messages = new HashMap<>();
    private final List<Template> templates = new ArrayList<>();

    private LevelTranslations() { }

    public static LevelTranslations parse(String... documents) throws JSONException {
        LevelTranslations translations = new LevelTranslations();
        for (String document : documents) translations.add(new JSONObject(document));
        return translations;
    }

    private void add(JSONObject document) throws JSONException {
        JSONObject lessons = document.getJSONObject("levels");
        for (var ids = lessons.keys(); ids.hasNext(); ) {
            String id = ids.next();
            JSONObject text = lessons.getJSONObject(id);
            Text previous = levels.put(id, new Text(text.getString("title"), text.getString("goal"), text.getString("hint")));
            if (previous != null) throw new JSONException("Duplicate level translation: " + id);
        }
        JSONObject feedback = document.getJSONObject("messages");
        for (var keys = feedback.keys(); keys.hasNext(); ) {
            String key = keys.next();
            messages.putIfAbsent(key, feedback.getString(key));
        }
        JSONArray patterns = document.optJSONArray("templates");
        for (int i = 0; patterns != null && i < patterns.length(); i++) {
            JSONObject template = patterns.getJSONObject(i);
            templates.add(new Template(Pattern.compile(template.getString("pattern")), template.getString("replacement")));
        }
    }

    public Text text(LevelDef level) {
        Text translated = levels.get(level.id);
        return translated == null ? new Text(level.title, level.goalText, level.hint) : translated;
    }

    /** Translates lesson feedback and leaves unknown Java diagnostics verbatim. */
    public String message(String original) {
        String exact = messages.get(original);
        if (exact != null) return exact;
        for (Template template : templates) {
            Matcher match = template.pattern().matcher(original);
            if (match.matches()) return match.replaceFirst(template.replacement());
        }
        return original;
    }
}
