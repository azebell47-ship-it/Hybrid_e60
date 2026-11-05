
package com.hybride60.launcher;

import android.app.Activity;
import android.os.Bundle;
import android.graphics.Color;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.HorizontalScrollView;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.ActivityInfo;
import android.content.pm.ServiceInfo;
import android.content.pm.ProviderInfo;
import android.content.pm.ResolveInfo;
import android.content.pm.PermissionInfo;
import android.content.ComponentName;
import android.os.Build;
import android.widget.ScrollView;
import android.text.method.ScrollingMovementMethod;
import java.util.ArrayList;
import android.app.AlertDialog;
import android.widget.Button;

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
            bouton.setOnClickListener(v -> {
                if ("Diagnostic".equals(nom)) {
                    afficherDiagnostic();
                } else if ("Navigation".equals(nom)) {
                    Intent i = getPackageManager().getLaunchIntentForPackage("com.google.android.apps.maps");
                    if (i != null) startActivity(i);
                    else new AlertDialog.Builder(this).setMessage("Google Maps non installé").setPositiveButton("OK", null).show();
                } else {
                    new AlertDialog.Builder(this).setTitle(nom).setMessage("Fonction en attente de connexion ou de configuration.").setPositiveButton("OK", null).show();
                }
            });
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

    private void afficherDiagnostic() {
        StringBuilder b = new StringBuilder();
        b.append("HYBRID_E60 - DIAGNOSTIC CARCANBUS\n");
        b.append("Appareil : ").append(Build.MANUFACTURER).append(" / ")
         .append(Build.MODEL).append(" / Android ").append(Build.VERSION.RELEASE).append("\n");
        b.append("Observation uniquement. Aucun message CANBUS envoyé.\n\n");
        final String pkg = "android.car.app.canbus";
        try {
            int flags = PackageManager.GET_ACTIVITIES
                    | PackageManager.GET_SERVICES
                    | PackageManager.GET_RECEIVERS
                    | PackageManager.GET_PROVIDERS
                    | PackageManager.GET_PERMISSIONS;
            PackageInfo info = getPackageManager().getPackageInfo(pkg, flags);
            b.append("Package : ").append(pkg).append("\nVersion : ")
             .append(info.versionName).append("\n\n");
            if (info.activities != null) {
                b.append("ACTIVITES\n");
                for (ActivityInfo a : info.activities)
                    b.append(a.exported ? "[EXPOSEE] " : "[INTERNE] ")
                     .append(a.name).append(" permission=").append(a.permission).append("\n");
            }
            if (info.services != null) {
                b.append("\nSERVICES\n");
                for (ServiceInfo s : info.services)
                    b.append(s.exported ? "[EXPOSE] " : "[INTERNE] ")
                     .append(s.name).append(" permission=").append(s.permission).append("\n");
            }
            if (info.receivers != null) {
                b.append("\nRECEPTEURS\n");
                for (ActivityInfo r : info.receivers)
                    b.append(r.exported ? "[EXPOSE] " : "[INTERNE] ")
                     .append(r.name).append(" permission=").append(r.permission).append("\n");
            }
            if (info.providers != null) {
                b.append("\nFOURNISSEURS\n");
                for (ProviderInfo p : info.providers)
                    b.append(p.exported ? "[EXPOSE] " : "[INTERNE] ")
                     .append(p.name).append(" authority=").append(p.authority)
                     .append(" readPermission=").append(p.readPermission).append("\n");
            }
            if (info.permissions != null) {
                b.append("\nPERMISSIONS DECLAREES\n");
                for (PermissionInfo p : info.permissions)
                    b.append(p.name).append("\n");
            }
            b.append("\nCette liste ne prouve pas que les donnees CANBUS sont lisibles.\n");
            b.append("Aucune lecture des portes, de la ceinture ou du moteur n'est encore active.\n");
        } catch (PackageManager.NameNotFoundException ex) {
            b.append("CarCanbus non visible. Verifier la declaration <queries> du manifeste.\n");
        } catch (Exception ex) {
            b.append("Erreur de diagnostic : ").append(ex.getClass().getSimpleName())
             .append(" : ").append(ex.getMessage()).append("\n");
        }
        final String rapport = b.toString();
        ScrollView scroll = new ScrollView(this);
        TextView text = texte(rapport, 12);
        text.setTextIsSelectable(true);
        text.setPadding(20, 12, 20, 12);
        scroll.addView(text);
        new AlertDialog.Builder(this)
            .setTitle("Diagnostic CANBUS avance")
            .setView(scroll)
            .setPositiveButton("PARTAGER", (d, which) -> {
                Intent send = new Intent(Intent.ACTION_SEND);
                send.setType("text/plain");
                send.putExtra(Intent.EXTRA_TEXT, rapport);
                startActivity(Intent.createChooser(send, "Partager le rapport"));
            })
            .setNegativeButton("FERMER", null)
            .show();
    }

}

