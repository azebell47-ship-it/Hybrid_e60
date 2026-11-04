
package com.hybride60.launcher;

import android.app.Activity;
import android.os.Bundle;
import android.graphics.Color;
import android.view.Gravity;
import android.widget.TextView;

public class MainActivity extends Activity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        TextView accueil = new TextView(this);
        accueil.setText("HYBRID_E60\nBMW E60 530d\nBienvenue");
        accueil.setTextColor(Color.CYAN);
        accueil.setTextSize(30);
        accueil.setGravity(Gravity.CENTER);
        accueil.setBackgroundColor(Color.BLACK);

        setContentView(accueil);
    }
}
