package com.optimaizex.telefon;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/** Nach dem Einschalten oder einer Aktualisierung laeuft die Bruecke von selbst wieder an. */
public class Neustart extends BroadcastReceiver {
    @Override
    public void onReceive(Context c, Intent i) {
        if (Netz.gekoppelt(c)) Bruecke.starten(c);
    }
}
