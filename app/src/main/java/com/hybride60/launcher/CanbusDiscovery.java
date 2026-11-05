package com.hybride60.launcher;

import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.Build;
import java.util.List;
import java.util.Locale;

/** Diagnostic local en lecture seule. Ne lit ni ne modifie les trames CAN. */
public final class CanbusDiscovery {
    private CanbusDiscovery() {}

    public static String inspect(Context context) {
        PackageManager pm = context.getPackageManager();
        StringBuilder out = new StringBuilder();
        out.append("Appareil : ").append(Build.MANUFACTURER).append(" / ").append(Build.MODEL)
           .append("\nAndroid : ").append(Build.VERSION.RELEASE)
           .append("\nMCU : non accessible via l'API Android standard")
           .append("\n\nRecherche des applications CANBUS visibles :\n");
        int count = 0;
        try {
            List<PackageInfo> packages = pm.getInstalledPackages(0);
            for (PackageInfo pkg : packages) {
                String label = "";
                try { label = pm.getApplicationLabel(pkg.applicationInfo).toString(); }
                catch (Exception ignored) {}
                String haystack = (pkg.packageName + " " + label).toLowerCase(Locale.ROOT);
                if (haystack.contains("canbus") || haystack.contains("carcan") ||
                    haystack.contains("vehicle") || haystack.contains("mcu") ||
                    haystack.contains("canbox")) {
                    out.append("• ").append(label).append(" [").append(pkg.packageName).append("]\n");
                    count++;
                }
            }
        } catch (Exception e) { out.append("Liste inaccessible : ").append(e.getClass().getSimpleName()).append("\n"); }
        if (count == 0) out.append("Aucune application correspondante visible.\n");
        out.append("\nAccès aux valeurs CAN : NON VÉRIFIÉ")
           .append("\nAucune lecture CAN ou OBD n'est effectuée.")
           .append("\nLes valeurs moteur restent indisponibles tant qu'une interface documentée n'est pas identifiée.")
           .append("\n\nCopiez ce rapport pour identifier l'intégration possible.");
        return out.toString();
    }
}
