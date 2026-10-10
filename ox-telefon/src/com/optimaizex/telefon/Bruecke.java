package com.optimaizex.telefon;

import android.Manifest;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.content.pm.ServiceInfo;
import android.database.Cursor;
import android.media.AudioDeviceInfo;
import android.media.AudioManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.provider.CallLog;
import android.provider.ContactsContract;
import android.telecom.TelecomManager;
import android.telephony.TelephonyManager;

import org.json.JSONArray;
import org.json.JSONObject;

/**
 * Die Bruecke zum Kern. Haelt eine ausgehende lange Abfrage offen (kein offener Port
 * am Telefon, kein fremder Dienst) und fuehrt die Befehle aus dem System aus:
 * waehlen, annehmen, ablehnen, auflegen, lautsprecher, abgleich. Meldet Klingeln,
 * Verbinden und Ende und gleicht die Anrufliste ab.
 */
public class Bruecke extends Service {
    static final String KANAL = "bruecke";
    static volatile String zustand = "aus";
    private volatile boolean laeuft;
    private Thread faden;
    private String letzterZustand = TelephonyManager.EXTRA_STATE_IDLE;
    private String letzteNummer = "";
    private long kontakteZuletzt = 0;
    private final Handler haupt = new Handler(Looper.getMainLooper());

    static void starten(Context c) {
        Intent i = new Intent(c, Bruecke.class);
        if (Build.VERSION.SDK_INT >= 26) c.startForegroundService(i); else c.startService(i);
    }

    static void anhalten(Context c) { c.stopService(new Intent(c, Bruecke.class)); }

    @Override public IBinder onBind(Intent i) { return null; }

    @Override
    public int onStartCommand(Intent i, int f, int id) {
        vordergrund("Verbindet mit " + Netz.host(this));
        if (!laeuft) {
            laeuft = true;
            IntentFilter t = new IntentFilter(TelephonyManager.ACTION_PHONE_STATE_CHANGED);
            if (Build.VERSION.SDK_INT >= 33) registerReceiver(telefonEmpfaenger, t, Context.RECEIVER_EXPORTED);
            else registerReceiver(telefonEmpfaenger, t);
            faden = new Thread(this::schleife, "bruecke");
            faden.start();
        }
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        laeuft = false;
        zustand = "aus";
        try { unregisterReceiver(telefonEmpfaenger); } catch (Exception e) { }
        if (faden != null) faden.interrupt();
        super.onDestroy();
    }

    private void vordergrund(String text) {
        NotificationManager nm = getSystemService(NotificationManager.class);
        if (Build.VERSION.SDK_INT >= 26 && nm.getNotificationChannel(KANAL) == null) {
            NotificationChannel k = new NotificationChannel(KANAL, "Verbindung zum System", NotificationManager.IMPORTANCE_LOW);
            k.setShowBadge(false);
            nm.createNotificationChannel(k);
        }
        PendingIntent oeffnen = PendingIntent.getActivity(this, 0, new Intent(this, Start.class), PendingIntent.FLAG_IMMUTABLE);
        Notification n = new Notification.Builder(this, KANAL)
            .setSmallIcon(android.R.drawable.stat_sys_phone_call)
            .setContentTitle("OX Telefon")
            .setContentText(text)
            .setContentIntent(oeffnen)
            .setOngoing(true)
            .build();
        if (Build.VERSION.SDK_INT >= 29) startForeground(1, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE);
        else startForeground(1, n);
    }

    /* ---------- lange Abfrage ---------- */
    private void schleife() {
        int warte = 2000;
        long abgleich = 0;
        while (laeuft) {
            try {
                if (System.currentTimeMillis() - abgleich > 15 * 60000) { anrufliste(); if (System.currentTimeMillis() - kontakteZuletzt > 6 * 3600000L) kontakte(); abgleich = System.currentTimeMillis(); }
                Netz.Antwort a = Netz.rufen(this, "GET", "/api/telefon/geraet/warten", null, 35000);
                if (a.status == 401) { zustand = "getrennt"; Netz.ablage(this).edit().remove("schluessel").apply(); haupt.post(this::stopSelf); return; }
                if (a.status != 200) throw new Exception("Status " + a.status);
                if (!"verbunden".equals(zustand)) { zustand = "verbunden"; haupt.post(() -> vordergrund("Verbunden mit " + Netz.host(this) + ". Anrufe aus dem System.")); }
                warte = 2000;
                JSONArray l = a.json.optJSONArray("befehle");
                if (l != null) for (int k = 0; k < l.length(); k++) ausfuehren(l.getJSONObject(k));
            } catch (InterruptedException e) {
                return;
            } catch (Exception e) {
                if (!laeuft) return;
                if (!"wartet".equals(zustand)) { zustand = "wartet"; haupt.post(() -> vordergrund("Keine Verbindung, neuer Versuch läuft")); }
                try { Thread.sleep(warte); } catch (InterruptedException x) { return; }
                warte = Math.min(warte * 2, 30000);
            }
        }
    }

    private boolean darf(String p) { return checkSelfPermission(p) == PackageManager.PERMISSION_GRANTED; }

    @SuppressWarnings("deprecation")
    private void ausfuehren(JSONObject b) {
        String was = b.optString("was");
        boolean ok = true;
        String text = "";
        try {
            TelecomManager tm = getSystemService(TelecomManager.class);
            switch (was) {
                case "waehlen": {
                    if (!darf(Manifest.permission.CALL_PHONE)) throw new SecurityException("Recht zum Anrufen fehlt");
                    Bundle x = new Bundle();
                    if (Netz.ablage(this).getBoolean("lautsprecher", true)) x.putBoolean(TelecomManager.EXTRA_START_CALL_WITH_SPEAKERPHONE, true);
                    tm.placeCall(Uri.fromParts("tel", b.optString("nummer"), null), x);
                    text = "wählt " + b.optString("nummer");
                    break;
                }
                case "annehmen":
                    if (!darf(Manifest.permission.ANSWER_PHONE_CALLS)) throw new SecurityException("Recht zum Annehmen fehlt");
                    tm.acceptRingingCall();
                    if (Netz.ablage(this).getBoolean("lautsprecher", true)) haupt.postDelayed(() -> lautsprecher(true), 900);
                    break;
                case "ablehnen":
                case "auflegen":
                    if (!darf(Manifest.permission.ANSWER_PHONE_CALLS)) throw new SecurityException("Recht zum Auflegen fehlt");
                    ok = tm.endCall();
                    if (!ok) text = "Kein Anruf aktiv";
                    break;
                case "lautsprecher":
                    ok = lautsprecher(b.optBoolean("an", true));
                    break;
                case "abgleich":
                    anrufliste(); kontakte();
                    break;
                default:
                    ok = false; text = "Unbekannt";
            }
        } catch (Exception e) {
            ok = false; text = String.valueOf(e.getMessage());
        }
        try {
            JSONObject m = new JSONObject().put("art", "befehl").put("text", was + (text.isEmpty() ? "" : ": " + text)).put("ok", ok);
            Netz.rufen(this, "POST", "/api/telefon/geraet/ereignis", m, 15000);
        } catch (Exception e) { }
    }

    @SuppressWarnings("deprecation")
    private boolean lautsprecher(boolean an) {
        AudioManager am = getSystemService(AudioManager.class);
        try {
            if (Build.VERSION.SDK_INT >= 31) {
                if (!an) { am.clearCommunicationDevice(); return true; }
                for (AudioDeviceInfo d : am.getAvailableCommunicationDevices())
                    if (d.getType() == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER) return am.setCommunicationDevice(d);
                return false;
            }
            am.setSpeakerphoneOn(an);
            return true;
        } catch (Exception e) { return false; }
    }

    /* ---------- Telefonzustand ---------- */
    private final BroadcastReceiver telefonEmpfaenger = new BroadcastReceiver() {
        @Override
        public void onReceive(Context c, Intent i) {
            String s = i.getStringExtra(TelephonyManager.EXTRA_STATE);
            String n = i.getStringExtra(TelephonyManager.EXTRA_INCOMING_NUMBER);
            if (n != null && !n.isEmpty()) letzteNummer = n;
            if (s == null) return;
            if (s.equals(letzterZustand) && (n == null || n.isEmpty())) return;
            String alt = letzterZustand;
            letzterZustand = s;
            final String art;
            if (TelephonyManager.EXTRA_STATE_RINGING.equals(s)) art = "klingelt";
            else if (TelephonyManager.EXTRA_STATE_OFFHOOK.equals(s)) art = "verbunden";
            else art = "beendet";
            if (s.equals(alt) && !"klingelt".equals(art)) return;
            final String nummer = letzteNummer;
            if ("beendet".equals(art)) letzteNummer = "";
            new Thread(() -> {
                try { Netz.rufen(Bruecke.this, "POST", "/api/telefon/geraet/ereignis", new JSONObject().put("art", art).put("nummer", nummer), 15000); } catch (Exception e) { }
                if ("beendet".equals(art)) { try { Thread.sleep(2000); } catch (InterruptedException e) { } anrufliste(); }
            }).start();
        }
    };

    /* ---------- Anrufliste ---------- */
    private void anrufliste() {
        if (!darf(Manifest.permission.READ_CALL_LOG)) return;
        JSONArray l = new JSONArray();
        String[] felder = { CallLog.Calls.NUMBER, CallLog.Calls.CACHED_NAME, CallLog.Calls.TYPE, CallLog.Calls.DATE, CallLog.Calls.DURATION };
        try (Cursor z = getContentResolver().query(CallLog.Calls.CONTENT_URI, felder, null, null, CallLog.Calls.DATE + " DESC")) {
            int n = 0;
            while (z != null && z.moveToNext() && n < 200) {
                l.put(new JSONObject().put("nummer", z.getString(0)).put("name", z.getString(1) == null ? "" : z.getString(1))
                    .put("art", z.getInt(2)).put("beginn", z.getLong(3)).put("dauer", z.getLong(4)));
                n++;
            }
            Netz.rufen(this, "POST", "/api/telefon/geraet/anrufliste", new JSONObject().put("eintraege", l), 30000);
        } catch (Exception e) { }
    }

    /* ---------- Kontakte: Namen, Nummern und Mails fuer die Suche im System, nur fuer das eigene Konto ---------- */
    private void kontakte() {
        if (!darf(Manifest.permission.READ_CONTACTS)) return;
        java.util.LinkedHashMap<Long, JSONObject> m = new java.util.LinkedHashMap<>();
        try {
            String[] f = { ContactsContract.Data.CONTACT_ID, ContactsContract.Data.DISPLAY_NAME, ContactsContract.Data.MIMETYPE, ContactsContract.Data.DATA1 };
            String wo = ContactsContract.Data.MIMETYPE + " IN (?, ?)";
            String[] w = { ContactsContract.CommonDataKinds.Phone.CONTENT_ITEM_TYPE, ContactsContract.CommonDataKinds.Email.CONTENT_ITEM_TYPE };
            try (Cursor z = getContentResolver().query(ContactsContract.Data.CONTENT_URI, f, wo, w, ContactsContract.Data.DISPLAY_NAME + " ASC")) {
                while (z != null && z.moveToNext() && m.size() < 5000) {
                    long id = z.getLong(0); String wert = z.getString(3);
                    if (wert == null || wert.isEmpty()) continue;
                    JSONObject k = m.get(id);
                    if (k == null) { k = new JSONObject().put("name", z.getString(1) == null ? "" : z.getString(1)).put("nummern", new JSONArray()).put("mails", new JSONArray()); m.put(id, k); }
                    k.getJSONArray(ContactsContract.CommonDataKinds.Phone.CONTENT_ITEM_TYPE.equals(z.getString(2)) ? "nummern" : "mails").put(wert);
                }
            }
            JSONArray l = new JSONArray();
            for (JSONObject k : m.values()) l.put(k);
            Netz.Antwort a = Netz.rufen(this, "POST", "/api/telefon/geraet/kontakte", new JSONObject().put("kontakte", l), 60000);
            if (a.status == 200) kontakteZuletzt = System.currentTimeMillis();
        } catch (Exception e) { }
    }
}
