package com.ccs.coffee_n_crops.lang.serial;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.Iterator;

/** Reads pre-farmer programs without changing user-defined member or class method names. */
final class LegacyFarmApi {
    static final int FARMHAND_VERSION = 2;
    private static final String LEGACY_ACTOR = "drone";
    private static final String FARMER = "farmer";
    private static final String FARMHAND = "Farmhand";
    private static final String CLASS_PREFIX = "CLASS:";
    private static final String LEGACY_MOVE = "flyTo";

    private LegacyFarmApi() { }

    static JSONObject upgrade(JSONObject source) throws JSONException {
        if (source.optInt("actorApi") >= FARMHAND_VERSION) return source;
        String encoded = source.toString();
        JSONObject upgraded = new JSONObject(encoded);
        // Older projects could use the names now reserved by the built-in type and parameter.
        String displacedLocal = freshName(encoded, "previousFarmer");
        String displacedClass = freshName(encoded, "Farmhand2");
        migrate(upgraded, true, displacedLocal, displacedClass);
        return upgraded;
    }

    private static String freshName(String encoded, String preferred) {
        String candidate = preferred;
        for (int suffix = 2; encoded.contains("\"" + candidate + "\""); suffix++) {
            candidate = preferred + suffix;
        }
        return candidate;
    }

    private static void migrate(JSONObject node, boolean implicitFarmer, String displacedLocal,
                                String displacedClass) throws JSONException {
        String tag = node.optString("t");
        switch (tag) {
            case LEGACY_ACTOR:
                if (implicitFarmer) node.put("t", FARMER);
                else node.put("t", "ref").put("name", LEGACY_ACTOR);
                break;
            case "call":
                if (implicitFarmer) migrateLocal(node, "recv", displacedLocal);
                rename(node, "name", LEGACY_MOVE, "walkTo");
                break;
            case "instancecall":
                if ("DRONE".equals(node.optString("recvType"))) rename(node, "name", LEGACY_MOVE, "walkTo");
                break;
            case "var":
            case "assign":
            case "listadd":
            case "access":
            case "ref":
            case "inc":
                if (implicitFarmer) migrateLocal(node, "name", displacedLocal);
                break;
            case "foreach":
                if (implicitFarmer) {
                    migrateLocal(node, "var", displacedLocal);
                    migrateLocal(node, "src", displacedLocal);
                }
                break;
            default:
                if (tag.isEmpty() && node.has("fields")) {
                    rename(node, "name", FARMHAND, displacedClass);
                    rename(node, "super", FARMHAND, displacedClass);
                } else if (tag.isEmpty() && implicitFarmer && node.has("type") && node.has("body")) {
                    migrateLocal(node, "name", displacedLocal); // Catch parameter.
                }
        }
        rename(node, "class", FARMHAND, displacedClass);
        for (Iterator<String> keys = node.keys(); keys.hasNext(); ) {
            String key = keys.next();
            Object value = node.get(key);
            if (value instanceof JSONObject) {
                migrate((JSONObject) value, implicitFarmer, displacedLocal, displacedClass);
            } else if (value instanceof JSONArray) {
                JSONArray array = (JSONArray) value;
                boolean childImplicit = implicitFarmer && !key.equals("classes");
                for (int i = 0; i < array.length(); i++) {
                    Object element = array.get(i);
                    if (element instanceof JSONObject) {
                        migrate((JSONObject) element, childImplicit, displacedLocal, displacedClass);
                    } else if (key.equals("types") && (CLASS_PREFIX + FARMHAND).equals(element)) {
                        array.put(i, CLASS_PREFIX + displacedClass);
                    }
                }
            } else if (isTypeKey(key) && (CLASS_PREFIX + FARMHAND).equals(value)) {
                node.put(key, CLASS_PREFIX + displacedClass);
            }
        }
    }

    private static boolean isTypeKey(String key) {
        return key.equals("type") || key.equals("recvType") || key.equals("returnType")
                || key.equals("elem") || key.equals("on");
    }

    private static void migrateLocal(JSONObject node, String key, String displacedLocal) throws JSONException {
        String name = node.optString(key);
        if (name.equals(LEGACY_ACTOR)) node.put(key, FARMER);
        else if (name.equals(FARMER)) node.put(key, displacedLocal);
    }

    private static void rename(JSONObject node, String key, String from, String to) throws JSONException {
        if (from.equals(node.optString(key))) node.put(key, to);
    }
}
