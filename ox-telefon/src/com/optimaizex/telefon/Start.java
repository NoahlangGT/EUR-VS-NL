package com.optimaizex.telefon;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.PowerManager;
import android.provider.Settings;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;

import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/** Einmal koppeln, Rechte erteilen, danach bleibt das Telefon in der Tasche. */
public class Start extends Activity {
    private static final String[] RECHTE_BASIS = {
        Manifest.permission.READ_PHONE_STATE, Manifest.permission.READ_CALL_LOG,
        Manifest.permission.CALL_PHONE, Manifest.permission.ANSWER_PHONE_CALLS, Manifest.permission.READ_CONTACTS };
    private LinearLayout inhalt;
    private TextView meldung;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        if (Build.VERSION.SDK_INT >= 35) getWindow().setDecorFitsSystemWindows(true);
        linkAuswerten(getIntent());
        zeichnen();
    }

    @Override
    protected void onNewIntent(Intent i) { super.onNewIntent(i); setIntent(i); linkAuswerten(i); zeichnen(); }

    @Override
    protected void onResume() { super.onResume(); zeichnen(); }

    /* oxtelefon://koppeln?s=<host>&c=<code> aus der Kopplungsseite des Systems */
    private void linkAuswerten(Intent i) {
        Uri u = i == null ? null : i.getData();
        if (u == null || !"oxtelefon".equals(u.getScheme())) return;
        String s = u.getQueryParameter("s"), c = u.getQueryParameter("c");
        if (s != null && c != null) koppeln(s, c);
    }

    private int dp(int v) { return Math.round(v * getResources().getDisplayMetrics().density); }

    private TextView text(String t, int sp, boolean fett, int farbe) {
        TextView v = new TextView(this);
        v.setText(t); v.setTextSize(sp); v.setTextColor(farbe);
        if (fett) v.setTypeface(Typeface.DEFAULT_BOLD);
        v.setPadding(0, dp(4), 0, dp(4));
        return v;
    }

    private Button knopf(String t, boolean haupt, View.OnClickListener f) {
        Button k = new Button(this);
        k.setText(t); k.setAllCaps(false); k.setTextSize(16);
        GradientDrawable g = new GradientDrawable();
        g.setCornerRadius(dp(12));
        if (haupt) { g.setColor(Color.parseColor("#0A66C2")); k.setTextColor(Color.WHITE); }
        else { g.setColor(Color.WHITE); g.setStroke(dp(1), Color.parseColor("#D0D5DD")); k.setTextColor(Color.parseColor("#101828")); }
        k.setBackground(g);
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(52));
        p.topMargin = dp(10);
        k.setLayoutParams(p);
        k.setOnClickListener(f);
        return k;
    }

    private LinearLayout karte() {
        LinearLayout k = new LinearLayout(this);
        k.setOrientation(LinearLayout.VERTICAL);
        k.setPadding(dp(18), dp(16), dp(18), dp(18));
        GradientDrawable g = new GradientDrawable();
        g.setCornerRadius(dp(16)); g.setColor(Color.WHITE);
        k.setBackground(g);
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        p.topMargin = dp(14);
        k.setLayoutParams(p);
        return k;
    }

    private boolean hat(String r) { return checkSelfPermission(r) == PackageManager.PERMISSION_GRANTED; }

    private List<String> fehlend() {
        List<String> l = new ArrayList<>();
        for (String r : RECHTE_BASIS) if (!hat(r)) l.add(r);
        if (Build.VERSION.SDK_INT >= 33 && !hat(Manifest.permission.POST_NOTIFICATIONS)) l.add(Manifest.permission.POST_NOTIFICATIONS);
        return l;
    }

    private boolean akkuFrei() {
        PowerManager pm = getSystemService(PowerManager.class);
        return pm.isIgnoringBatteryOptimizations(getPackageName());
    }

    private void zeichnen() {
        ScrollView sv = new ScrollView(this);
        sv.setBackgroundColor(Color.parseColor("#F4F6FA"));
        sv.setFitsSystemWindows(true);
        inhalt = new LinearLayout(this);
        inhalt.setOrientation(LinearLayout.VERTICAL);
        inhalt.setPadding(dp(18), dp(28), dp(18), dp(28));
        sv.addView(inhalt);
        int tx = Color.parseColor("#101828"), tx2 = Color.parseColor("#475467");
        inhalt.addView(text("OX Telefon", 26, true, tx));
        boolean gek = Netz.gekoppelt(this);
        inhalt.addView(text(gek ? "Gekoppelt mit " + Netz.host(this) + ". Wählen, Annehmen und Auflegen laufen jetzt über das System, das Telefon bleibt in der Tasche."
            : "Verbindet dieses Telefon mit deinem OPTIMaiZEx-System. Danach wählst du aus XRMaps und Xannel, nimmst Anrufe am Rechner an und siehst die Anrufliste im System.", 15, false, tx2));
        meldung = text("", 14, true, Color.parseColor("#B42318"));
        inhalt.addView(meldung);

        if (!gek) {
            LinearLayout k = karte();
            k.addView(text("Koppeln", 18, true, tx));
            k.addView(text("Im System unter Xannel, Telefonie, Smartphone verbinden steht ein sechsstelliger Code.", 14, false, tx2));
            final EditText host = new EditText(this);
            host.setHint("Adresse, z. B. firma.optimaizex.com");
            host.setInputType(InputType.TYPE_TEXT_VARIATION_URI);
            host.setText(Netz.host(this));
            k.addView(host);
            final EditText code = new EditText(this);
            code.setHint("Code");
            code.setInputType(InputType.TYPE_CLASS_NUMBER);
            code.setGravity(Gravity.CENTER);
            code.setTextSize(24);
            k.addView(code);
            k.addView(knopf("Koppeln", true, v -> koppeln(host.getText().toString(), code.getText().toString())));
            inhalt.addView(k);
        }

        List<String> f = fehlend();
        LinearLayout r = karte();
        r.addView(text("Rechte", 18, true, tx));
        r.addView(text(f.isEmpty() ? "✓ Anrufliste, Kontakte, Anrufen, Annehmen und Auflegen sind erlaubt."
            : "Nötig, damit das System für dich wählen, annehmen, auflegen, die Anrufliste zeigen und deine Kontakte bei der Suche anbieten kann. Kontakte sieht nur dein eigenes Konto. Gespräche werden weder aufgezeichnet noch mitgehört.", 14, false, tx2));
        if (!f.isEmpty()) r.addView(knopf("Rechte erteilen", true, v -> requestPermissions(f.toArray(new String[0]), 7)));
        r.addView(text(akkuFrei() ? "✓ Läuft im Hintergrund ohne Akku-Sperre." : "Android hält Apps im Hintergrund an. Einmal freigeben, damit Anrufe aus dem System jederzeit ankommen.", 14, false, tx2));
        if (!akkuFrei()) r.addView(knopf("Im Hintergrund erlauben", false, v -> {
            try { startActivity(new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:" + getPackageName()))); }
            catch (Exception e) { startActivity(new Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)); }
        }));
        inhalt.addView(r);

        if (gek) {
            LinearLayout e = karte();
            e.addView(text("Verbindung: " + ("verbunden".equals(Bruecke.zustand) ? "steht" : "wartet".equals(Bruecke.zustand) ? "wird aufgebaut" : "startet"), 18, true, tx));
            Switch ls = new Switch(this);
            ls.setText("Freisprechen bei Anrufen aus dem System");
            ls.setTextSize(15);
            ls.setChecked(Netz.ablage(this).getBoolean("lautsprecher", true));
            ls.setOnCheckedChangeListener((x, an) -> Netz.ablage(this).edit().putBoolean("lautsprecher", an).apply());
            ls.setPadding(0, dp(10), 0, dp(10));
            e.addView(ls);
            e.addView(text("Mit Kopfhörer oder Auto über Bluetooth läuft der Ton dorthin. Unter Windows kann zusätzlich Smartphone-Link den Ton auf den Rechner legen.", 13, false, tx2));
            e.addView(knopf("Kopplung lösen", false, v -> {
                Bruecke.anhalten(this);
                Netz.ablage(this).edit().remove("schluessel").apply();
                zeichnen();
            }));
            inhalt.addView(e);
            if (f.isEmpty()) Bruecke.starten(this);
        }
        setContentView(sv);
    }

    @Override
    public void onRequestPermissionsResult(int c, String[] r, int[] e) { zeichnen(); }

    private void koppeln(String hostRoh, String codeRoh) {
        final String host = hostRoh.trim().replaceFirst("^https?://", "").replaceAll("/.*$", "").toLowerCase();
        final String code = codeRoh.replaceAll("\\D", "");
        if (host.isEmpty() || code.length() != 6) { if (meldung != null) meldung.setText("Adresse und sechsstelliger Code fehlen."); return; }
        if (meldung != null) { meldung.setTextColor(Color.parseColor("#475467")); meldung.setText("Wird gekoppelt …"); }
        new Thread(() -> {
            String fehler = null;
            try {
                JSONObject k = new JSONObject().put("code", code).put("name", Build.MODEL)
                    .put("modell", Build.MANUFACTURER + " " + Build.MODEL + ", Android " + Build.VERSION.RELEASE);
                Netz.Antwort a = Netz.rufen(host, null, "POST", "/api/telefon/koppeln", k, 20000);
                if (a.status == 200 && a.json.optBoolean("ok")) {
                    Netz.ablage(this).edit().putString("host", host).putString("schluessel", a.json.getString("schluessel")).apply();
                } else fehler = a.json.optString("fehler", "Kopplung abgelehnt (" + a.status + ")");
            } catch (Exception e) { fehler = "Keine Verbindung zu " + host; }
            final String f = fehler;
            runOnUiThread(() -> {
                zeichnen();
                if (f != null) { meldung.setTextColor(Color.parseColor("#B42318")); meldung.setText(f); }
                else { meldung.setTextColor(Color.parseColor("#0F7B52")); meldung.setText("✓ Gekoppelt."); if (!fehlend().isEmpty()) requestPermissions(fehlend().toArray(new String[0]), 7); }
            });
        }).start();
    }
}
