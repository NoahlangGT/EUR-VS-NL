package com.optimaizex.telefon;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

/** Zugang zum Kern des Mandanten: gespeicherte Kopplung und HTTP mit Geraeteschluessel. */
final class Netz {
    static final String DATEI = "kopplung";

    static SharedPreferences ablage(Context c) { return c.getSharedPreferences(DATEI, Context.MODE_PRIVATE); }
    static String host(Context c) { return ablage(c).getString("host", ""); }
    static String schluessel(Context c) { return ablage(c).getString("schluessel", ""); }
    static boolean gekoppelt(Context c) { return !host(c).isEmpty() && !schluessel(c).isEmpty(); }

    static final class Antwort {
        final int status; final JSONObject json;
        Antwort(int s, JSONObject j) { status = s; json = j; }
    }

    static Antwort rufen(Context c, String methode, String weg, JSONObject koerper, int lesezeitMs) throws Exception {
        return rufen(host(c), schluessel(c), methode, weg, koerper, lesezeitMs);
    }

    static Antwort rufen(String host, String schluessel, String methode, String weg, JSONObject koerper, int lesezeitMs) throws Exception {
        HttpURLConnection v = (HttpURLConnection) new URL("https://" + host + weg).openConnection();
        v.setRequestMethod(methode);
        v.setConnectTimeout(15000);
        v.setReadTimeout(lesezeitMs);
        v.setRequestProperty("Accept", "application/json");
        v.setRequestProperty("User-Agent", "OX-Telefon/1.0");
        if (schluessel != null && !schluessel.isEmpty()) v.setRequestProperty("Authorization", "Bearer " + schluessel);
        if (koerper != null) {
            byte[] b = koerper.toString().getBytes(StandardCharsets.UTF_8);
            v.setDoOutput(true);
            v.setRequestProperty("Content-Type", "application/json");
            v.setFixedLengthStreamingMode(b.length);
            try (OutputStream o = v.getOutputStream()) { o.write(b); }
        }
        int s = v.getResponseCode();
        InputStream e = s >= 400 ? v.getErrorStream() : v.getInputStream();
        String t = "";
        if (e != null) {
            ByteArrayOutputStream p = new ByteArrayOutputStream();
            byte[] puffer = new byte[8192];
            int n;
            while ((n = e.read(puffer)) > 0) p.write(puffer, 0, n);
            e.close();
            t = p.toString("UTF-8");
        }
        v.disconnect();
        JSONObject j;
        try { j = new JSONObject(t.isEmpty() ? "{}" : t); } catch (Exception x) { j = new JSONObject(); }
        return new Antwort(s, j);
    }
}
