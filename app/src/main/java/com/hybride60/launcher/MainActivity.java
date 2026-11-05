
package com.hybride60.launcher;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class MainActivity extends Activity {

    private final int bleu = Color.rgb(0, 170, 255);
    private final int fond = Color.rgb(8, 12, 20);
    private final StringBuilder rapport = new StringBuilder();

    private LinearLayout ecran;
    private TextView rapportView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        getWindow().getDecorView().setSystemUiVisibility(
            View.SYSTEM_UI_FLAG_FULLSCREEN |
            View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY |
            View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
        );

        afficherAccueil();
    }

    private void afficherAccueil() {

        ecran = new LinearLayout(this);
        ecran.setOrientation(LinearLayout.VERTICAL);
        ecran.setBackgroundColor(fond);

        TextView titre = texte(
            "BMW E60 530d     |     HYBRID_E60", 24
        );
        titre.setTextColor(bleu);
        titre.setGravity(Gravity.CENTER);
        titre.setPadding(10, 15, 10, 15);
        ecran.addView(titre);

        LinearLayout centre = new LinearLayout(this);
        centre.setOrientation(LinearLayout.HORIZONTAL);

        LinearLayout gauche = colonne();
        gauche.addView(info("LIQUIDE", "-- °C"));
        gauche.addView(info("HUILE", "-- °C"));
        gauche.addView(info("BATTERIE", "-- V"));
        gauche.addView(info("TURBO", "-- bar"));

        // Moteur 3D Filament existant
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
            bouton.setGravity(Gravity.CENTER);
            bouton.setPadding(24, 18, 24, 18);
            bouton.setBackground(cadreBleu());
            menu.addView(bouton);

            bouton.setOnClickListener(v -> action(nom));
        }

        defilement.addView(menu);
        ecran.addView(defilement);
        setContentView(ecran);
    }

    private void action(String nom) {

        switch (nom) {

            case "Diagnostic":
                afficherDiagnostic();
                break;

            case "Navigation":
                lancerApplication(
                    "com.google.android.apps.maps"
                );
                break;

            case "Multimédia":
                lancerApplication(
                    "com.spotify.music"
                );
                break;

            case "Réglages":
                startActivity(new Intent(
                    android.provider.Settings.ACTION_SETTINGS
                ));
                break;

            case "Véhicule":
                message("BMW E60 530d",
                    "Visualisation 3D disponible.");
                break;

            case "Moteur":
                message("Données moteur",
                    "En attente de connexion CANBUS.");
                break;

            case "Climatisation":
                message("Climatisation",
                    "Commande CANBUS non disponible.");
                break;

            case "Applications":
                message("Applications",
                    "Sélectionnez Navigation ou Multimédia.");
                break;
        }
    }

    private void lancerApplication(String packageName) {
        Intent intent = getPackageManager()
            .getLaunchIntentForPackage(packageName);

        if (intent != null) {
            startActivity(intent);
        } else {
            message("Application indisponible",
                "Application non installée ou non accessible.");
        }
    }

    private void afficherDiagnostic() {

        rapport.setLength(0);

        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setBackgroundColor(fond);
        page.setPadding(18, 12, 18, 12);

        TextView titre = texte(
            "HYBRID_E60 | DIAGNOSTIC CANBUS", 22
        );
        titre.setTextColor(bleu);
        titre.setGravity(Gravity.CENTER);
        page.addView(titre);

        TextView avertissement = texte(
            "Mode observation : aucune commande CANBUS envoyée",
            13
        );
        avertissement.setGravity(Gravity.CENTER);
        page.addView(avertissement);

        ScrollView scroll = new ScrollView(this);

        rapportView = texte("", 14);
        rapportView.setTextIsSelectable(true);
        rapportView.setPadding(12, 15, 12, 15);

        scroll.addView(rapportView);
        page.addView(scroll,
            new LinearLayout.LayoutParams(-1, 0, 1));

        ajouterBouton(page, "RECHERCHER CARCANBUS",
            v -> rechercherCanbus());

        ajouterBouton(page, "MARQUER PORTE OUVERTE",
            v -> ajouterRapport(
                "Marqueur manuel : porte ouverte"
            ));

        ajouterBouton(page, "MARQUER PORTE FERMÉE",
            v -> ajouterRapport(
                "Marqueur manuel : porte fermée"
            ));

        ajouterBouton(page, "PARTAGER LE RAPPORT",
            v -> partagerRapport());

        ajouterBouton(page, "RETOUR",
            v -> afficherAccueil());

        setContentView(page);

        ajouterRapport("Diagnostic initialisé");
        ajouterRapport("Fabricant : " + Build.MANUFACTURER);
        ajouterRapport("Modèle : " + Build.MODEL);
        ajouterRapport("Android : " + Build.VERSION.RELEASE);
        ajouterRapport("Lecture CANBUS directe : non disponible");

        rechercherCanbus();
    }

    private void rechercherCanbus() {

        ajouterRapport("Recherche de CarCanbus...");

        try {
            PackageInfo pkg = getPackageManager()
                .getPackageInfo("android.car.app.canbus", 0);

            ajouterRapport(
                "CarCanbus visible : android.car.app.canbus"
            );

            ajouterRapport(
                "Version : " + pkg.versionName
            );

        } catch (PackageManager.NameNotFoundException e) {
            ajouterRapport(
                "CarCanbus non visible par Android."
            );
        }

        ajouterRapport(
            "Aucun événement CANBUS décodé."
        );
    }

    private void ajouterRapport(String message) {

        String heure = new SimpleDateFormat(
            "HH:mm:ss.SSS", Locale.FRANCE
        ).format(new Date());

        rapport.append("[")
            .append(heure)
            .append("] ")
            .append(message)
            .append("\n");

        if (rapportView != null) {
            rapportView.setText(rapport.toString());
        }
    }

    private void partagerRapport() {

        Intent intent = new Intent(Intent.ACTION_SEND);
        intent.setType("text/plain");
        intent.putExtra(
            Intent.EXTRA_TEXT,
            rapport.toString()
        );

        startActivity(Intent.createChooser(
            intent, "Partager le diagnostic CANBUS"
        ));
    }

    private void ajouterBouton(
        LinearLayout parent,
        String nom,
        View.OnClickListener action
    ) {

        TextView bouton = texte(nom, 15);
        bouton.setGravity(Gravity.CENTER);
        bouton.setPadding(12, 13, 12, 13);
        bouton.setBackground(cadreBleu());

        LinearLayout.LayoutParams params =
            new LinearLayout.LayoutParams(-1, -2);
        params.setMargins(0, 4, 0, 4);

        parent.addView(bouton, params);
        bouton.setOnClickListener(action);
    }

    private GradientDrawable cadreBleu() {

        GradientDrawable d = new GradientDrawable();
        d.setColor(Color.rgb(18, 32, 49));
        d.setCornerRadius(12);
        d.setStroke(2, bleu);

        return d;
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
        t.setBackground(cadreBleu());

        LinearLayout.LayoutParams p =
            new LinearLayout.LayoutParams(-1, 0, 1);
        p.setMargins(5, 5, 5, 5);
        t.setLayoutParams(p);

        return t;
    }

    private void message(String titre, String contenu) {

        new AlertDialog.Builder(this)
            .setTitle(titre)
            .setMessage(contenu)
            .setPositiveButton("OK", null)
            .show();
    }
}
