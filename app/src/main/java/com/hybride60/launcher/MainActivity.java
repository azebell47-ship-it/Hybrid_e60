
package com.hybride60.launcher;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.ActivityNotFoundException;
import android.net.Uri;
import android.provider.Settings;
import android.widget.Toast;
import android.content.pm.PackageManager;
import android.graphics.drawable.GradientDrawable;
import java.util.List;
import android.os.Bundle;
import android.graphics.Color;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.HorizontalScrollView;

public class MainActivity extends Activity {

    private final int bleu = Color.rgb(0, 170, 255);
    private final int fond = Color.rgb(8, 12, 20);

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        getWindow().getDecorView().setSystemUiVisibility(
            View.SYSTEM_UI_FLAG_FULLSCREEN
            | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
            | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
        );

        LinearLayout ecran = new LinearLayout(this);
        ecran.setOrientation(LinearLayout.VERTICAL);
        ecran.setBackgroundColor(fond);

        TextView titre = texte("HYBRID_E60   |   BMW 530d", 25);
        titre.setTextColor(bleu);
        titre.setGravity(Gravity.CENTER);
        titre.setPadding(10, 18, 10, 18);
        ecran.addView(titre);

        LinearLayout centre = new LinearLayout(this);
        centre.setOrientation(LinearLayout.HORIZONTAL);

        LinearLayout gauche = colonne();
        gauche.addView(info("LIQUIDE", "-- °C"));
        gauche.addView(info("HUILE", "-- °C"));
        gauche.addView(info("BATTERIE", "-- V"));
        gauche.addView(info("TURBO", "-- bar"));

        
Car3DView voiture = new Car3DView(this);


        LinearLayout droite = colonne();
        droite.addView(info("REGIME", "-- tr/min"));
        droite.addView(info("PUISSANCE", "-- ch"));
        droite.addView(info("COUPLE", "-- Nm"));
        droite.addView(info("ADMISSION", "-- °C"));

        centre.addView(gauche,
            new LinearLayout.LayoutParams(0, -1, 1));
        centre.addView(voiture,
            new LinearLayout.LayoutParams(0, -1, 2));
        centre.addView(droite,
            new LinearLayout.LayoutParams(0, -1, 1));

        ecran.addView(centre,
            new LinearLayout.LayoutParams(-1, 0, 1));

        HorizontalScrollView defilement =
            new HorizontalScrollView(this);

        LinearLayout menu = new LinearLayout(this);
        menu.setOrientation(LinearLayout.HORIZONTAL);

        String[] boutons = {
            "Véhicule", "Moteur", "Climatisation",
            "Multimédia", "Navigation",
            "Diagnostic", "Applications", "Réglages"
        };

        for (String nom : boutons) {
            TextView bouton = texte(nom, 16);
            bouton.setTextColor(Color.WHITE);
            bouton.setPadding(22, 18, 22, 18);
            bouton.setBackgroundColor(Color.rgb(18, 32, 49));
            GradientDrawable cadre = new GradientDrawable();
            cadre.setColor(Color.rgb(14, 29, 46));
            cadre.setStroke(2, bleu);
            cadre.setCornerRadius(12);
            bouton.setBackground(cadre);
            LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-2, -2);
            p.setMargins(5, 4, 5, 6);
            menu.addView(bouton, p);
            bouton.setOnClickListener(v -> action(nom));
        }

        defilement.addView(menu);
        ecran.addView(defilement);

        setContentView(ecran);
    }

    private void action(String nom) {
        switch (nom) {
            case "Véhicule":
                Toast.makeText(this, "BMW E60 : rotation tactile 3D", Toast.LENGTH_SHORT).show();
                break;
            case "Moteur":
            case "Diagnostic":
                String rapport = CanbusDiscovery.inspect(this);
                new AlertDialog.Builder(this).setTitle("Diagnostic CANBUS — " + nom)
                    .setMessage(rapport)
                    .setPositiveButton("OK", null)
                    .setNeutralButton("Copier le rapport", (dialog, which) -> {
                        android.content.ClipboardManager clipboard = (android.content.ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
                        clipboard.setPrimaryClip(android.content.ClipData.newPlainText("Hybrid_e60 CANBUS", rapport));
                        Toast.makeText(this, "Rapport copié", Toast.LENGTH_SHORT).show();
                    }).show();
                break;
            case "Climatisation":
                new AlertDialog.Builder(this).setTitle("Climatisation")
                    .setMessage("Commande de climatisation indisponible sans interface CANBUS compatible. Les commandes d'origine restent utilisables.")
                    .setPositiveButton("OK", null).show();
                break;
            case "Multimédia":
                if (!openPackage("com.spotify.music") && !openPackage("com.google.android.apps.youtube.music"))
                    openAction(new Intent(Intent.ACTION_VIEW, Uri.parse("https://music.youtube.com")));
                break;
            case "Navigation":
                if (!openPackage("com.waze")) {
                    Intent maps = new Intent(Intent.ACTION_VIEW, Uri.parse("geo:0,0?q="));
                    maps.setPackage("com.google.android.apps.maps");
                    if (!openAction(maps)) openAction(new Intent(Intent.ACTION_VIEW, Uri.parse("https://maps.google.com")));
                }
                break;
            case "Applications":
                Intent apps = new Intent(Settings.ACTION_APPLICATION_SETTINGS);
                openAction(apps);
                break;
            case "Réglages":
                openAction(new Intent(Settings.ACTION_SETTINGS));
                break;
        }
    }

    private boolean openPackage(String pkg) {
        try {
            Intent intent = getPackageManager().getLaunchIntentForPackage(pkg);
            if (intent == null) return false;
            startActivity(intent);
            return true;
        } catch (Exception e) { return false; }
    }

    private boolean openAction(Intent intent) {
        try { startActivity(intent); return true; }
        catch (Exception e) {
            Toast.makeText(this, "Application non disponible", Toast.LENGTH_SHORT).show();
            return false;
        }
    }

    private LinearLayout colonne() {
        LinearLayout l = new LinearLayout(this);
        l.setOrientation(LinearLayout.VERTICAL);
        return l;
    }

    private TextView texte(String contenu, int taille) {
        TextView t = new TextView(this);
        t.setText(contenu);
        t.setTextSize(taille);
        t.setTextColor(Color.WHITE);
        return t;
    }

    private TextView info(String nom, String valeur) {
        TextView t = texte(nom + "\n" + valeur, 17);
        t.setGravity(Gravity.CENTER);
        t.setPadding(8, 12, 8, 12);
        t.setTextColor(bleu);
        return t;
    }
}

