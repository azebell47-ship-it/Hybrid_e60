
package com.hybride60.launcher;

import android.app.Activity;
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
            menu.addView(bouton);
        }

        defilement.addView(menu);
        ecran.addView(defilement);

        setContentView(ecran);
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

