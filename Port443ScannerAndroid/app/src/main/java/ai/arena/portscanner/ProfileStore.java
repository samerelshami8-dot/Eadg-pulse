package ai.arena.portscanner;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Persists a small list of named scan profiles (target IPs + connection knobs)
 * into {@link SharedPreferences} under {@link #PREFS} / {@link #KEY_ROOT}.
 *
 * <p>The JSON layout is documented in V2_UPGRADE_FA.md; on first launch we seed
 * a {@code Default} profile so the UI is never empty.
 */
final class ProfileStore {

    static final String PREFS = "edgepulse_v2";
    static final String KEY_ROOT = "edgepulse_profiles_v2";

    /** One named profile. Mutable holder, not thread-safe. */
    static final class Profile {
        String name;
        String sni = "";
        String host = "";
        String path = "/";
        int port = 443;
        final List<String> ips = new ArrayList<>();
        long created = 0L;
        long lastUsed = 0L;

        Profile(String name) { this.name = name; }

        Profile copyAs(String newName) {
            Profile p = new Profile(newName);
            p.sni = sni; p.host = host; p.path = path; p.port = port;
            p.ips.addAll(ips);
            p.created = System.currentTimeMillis();
            p.lastUsed = p.created;
            return p;
        }

        JSONObject toJson() throws JSONException {
            JSONObject o = new JSONObject();
            o.put("name", name);
            o.put("sni", sni == null ? "" : sni);
            o.put("host", host == null ? "" : host);
            o.put("path", path == null ? "/" : path);
            o.put("port", port);
            o.put("created", created);
            o.put("lastUsed", lastUsed);
            JSONArray arr = new JSONArray();
            for (String ip : ips) if (ip != null && !ip.isEmpty()) arr.put(ip);
            o.put("ips", arr);
            return o;
        }

        static Profile fromJson(JSONObject o) {
            Profile p = new Profile(o.optString("name", "Unnamed"));
            p.sni = o.optString("sni", "");
            p.host = o.optString("host", "");
            p.path = o.optString("path", "/");
            p.port = o.optInt("port", 443);
            p.created = o.optLong("created", System.currentTimeMillis());
            p.lastUsed = o.optLong("lastUsed", p.created);
            JSONArray arr = o.optJSONArray("ips");
            if (arr != null) {
                for (int i = 0; i < arr.length(); i++) {
                    String ip = arr.optString(i, "");
                    if (!ip.isEmpty()) p.ips.add(ip);
                }
            }
            return p;
        }
    }

    private final SharedPreferences prefs;
    private final List<Profile> profiles = new ArrayList<>();
    private String selectedName = "";

    ProfileStore(Context ctx) {
        this.prefs = ctx.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        load();
        if (profiles.isEmpty()) {
            Profile def = new Profile("Default");
            def.created = System.currentTimeMillis();
            def.lastUsed = def.created;
            profiles.add(def);
            selectedName = def.name;
            persist();
        } else if (selectedName.isEmpty() || get(selectedName) == null) {
            selectedName = profiles.get(0).name;
            persist();
        }
    }

    synchronized List<Profile> list() {
        return new ArrayList<>(profiles);
    }

    synchronized Profile get(String name) {
        if (name == null) return null;
        for (Profile p : profiles) if (name.equals(p.name)) return p;
        return null;
    }

    synchronized String selected() {
        return selectedName;
    }

    synchronized Profile selectedProfile() {
        Profile p = get(selectedName);
        if (p == null && !profiles.isEmpty()) {
            p = profiles.get(0);
            selectedName = p.name;
            persist();
        }
        return p;
    }

    synchronized void select(String name) {
        if (name == null) return;
        if (get(name) == null) return;
        selectedName = name;
        Profile p = get(name);
        if (p != null) p.lastUsed = System.currentTimeMillis();
        persist();
    }

    synchronized boolean save(Profile p) {
        if (p == null || p.name == null || p.name.trim().isEmpty()) return false;
        Profile existing = get(p.name);
        if (existing == null) {
            if (p.created == 0L) p.created = System.currentTimeMillis();
            p.lastUsed = System.currentTimeMillis();
            profiles.add(p);
        } else {
            existing.sni = p.sni;
            existing.host = p.host;
            existing.path = p.path;
            existing.port = p.port;
            existing.ips.clear();
            existing.ips.addAll(p.ips);
            existing.lastUsed = System.currentTimeMillis();
        }
        return persist();
    }

    synchronized boolean delete(String name) {
        if (name == null || profiles.size() <= 1) return false;
        boolean removed = false;
        for (int i = 0; i < profiles.size(); i++) {
            if (name.equals(profiles.get(i).name)) {
                profiles.remove(i);
                removed = true;
                break;
            }
        }
        if (!removed) return false;
        if (name.equals(selectedName)) selectedName = profiles.get(0).name;
        return persist();
    }

    /** Replaces the profile's IP list with the union of existing + new, deduped, capped at 5000. */
    synchronized boolean addIpsToProfile(String name, List<String> ips) {
        Profile p = get(name);
        if (p == null || ips == null) return false;
        Set<String> uniq = new LinkedHashSet<>(p.ips);
        for (String ip : ips) if (ip != null && !ip.isEmpty()) uniq.add(ip);
        p.ips.clear();
        int cap = Math.min(5000, uniq.size());
        int i = 0;
        for (String ip : uniq) {
            if (i++ >= cap) break;
            p.ips.add(ip);
        }
        p.lastUsed = System.currentTimeMillis();
        return persist();
    }

    synchronized String exportJson() {
        try {
            JSONObject root = new JSONObject();
            JSONArray arr = new JSONArray();
            for (Profile p : profiles) arr.put(p.toJson());
            root.put("profiles", arr);
            root.put("selected", selectedName);
            root.put("version", 2);
            return root.toString(2);
        } catch (JSONException e) {
            return "{\"profiles\":[],\"selected\":\"\",\"version\":2}";
        }
    }

    synchronized boolean importJson(String text) {
        if (text == null || text.trim().isEmpty()) return false;
        try {
            JSONObject root = new JSONObject(text);
            JSONArray arr = root.optJSONArray("profiles");
            if (arr == null) return false;
            List<Profile> next = new ArrayList<>();
            for (int i = 0; i < arr.length(); i++) {
                JSONObject po = arr.optJSONObject(i);
                if (po == null) continue;
                Profile p = Profile.fromJson(po);
                if (p.name == null || p.name.isEmpty()) continue;
                next.add(p);
            }
            if (next.isEmpty()) return false;
            profiles.clear();
            profiles.addAll(next);
            String sel = root.optString("selected", "");
            selectedName = (get(sel) != null) ? sel : profiles.get(0).name;
            return persist();
        } catch (JSONException e) {
            return false;
        }
    }

    // ---------- persistence ----------

    private void load() {
        String text = prefs.getString(KEY_ROOT, "");
        if (text == null || text.isEmpty()) return;
        try {
            JSONObject root = new JSONObject(text);
            JSONArray arr = root.optJSONArray("profiles");
            if (arr != null) {
                for (int i = 0; i < arr.length(); i++) {
                    JSONObject po = arr.optJSONObject(i);
                    if (po == null) continue;
                    Profile p = Profile.fromJson(po);
                    if (p.name == null || p.name.isEmpty()) continue;
                    profiles.add(p);
                }
            }
            selectedName = root.optString("selected", "");
        } catch (JSONException ignored) {
            profiles.clear();
        }
    }

    private boolean persist() {
        try {
            JSONObject root = new JSONObject();
            JSONArray arr = new JSONArray();
            for (Profile p : profiles) arr.put(p.toJson());
            root.put("profiles", arr);
            root.put("selected", selectedName);
            root.put("version", 2);
            prefs.edit().putString(KEY_ROOT, root.toString()).apply();
            return true;
        } catch (JSONException e) {
            return false;
        }
    }

    /** All profile names in insertion order. */
    synchronized List<String> names() {
        List<String> out = new ArrayList<>(profiles.size());
        for (Profile p : profiles) out.add(p.name);
        return out;
    }

    synchronized List<String> ipsOf(String name) {
        Profile p = get(name);
        if (p == null) return Collections.emptyList();
        return new ArrayList<>(p.ips);
    }

    synchronized boolean removeIp(String name, String ip) {
        Profile p = get(name);
        if (p == null || ip == null) return false;
        boolean changed = p.ips.remove(ip);
        if (changed) {
            p.lastUsed = System.currentTimeMillis();
            persist();
        }
        return changed;
    }
}
