package com.hybride60.launcher;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.TextView;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/** Hybrid_e60: cockpit UI. No simulated CANBUS values. */
public class MainActivity extends Activity {
    private static final int BG=Color.rgb(3,8,16), PANEL=Color.rgb(10,20,34);
    private static final int BLUE=Color.rgb(0,183,255), WHITE=Color.rgb(235,244,255);
    private TextView clock;
    private final android.os.Handler handler=new android.os.Handler();
    private final Runnable clockTick=new Runnable(){public void run(){
        if(clock!=null)clock.setText(new SimpleDateFormat("HH:mm",Locale.FRANCE).format(new Date()));
        handler.postDelayed(this,15000);
    }};
    private int dp(float n){return (int)(getResources().getDisplayMetrics().density*n+0.5f);}
    private GradientDrawable shape(int color,int stroke,int radius){
        GradientDrawable d=new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
            new int[]{color,Color.rgb(4,10,20)});
        d.setCornerRadius(dp(radius));d.setStroke(dp(1),stroke);return d;
    }
    private TextView text(String value,int sp,int color,boolean bold){
        TextView t=new TextView(this);t.setText(value);t.setTextSize(sp);t.setTextColor(color);
        if(bold)t.setTypeface(Typeface.DEFAULT,Typeface.BOLD);t.setGravity(Gravity.CENTER_VERTICAL);return t;
    }
    private LinearLayout row(){LinearLayout l=new LinearLayout(this);l.setOrientation(LinearLayout.HORIZONTAL);return l;}
    private LinearLayout column(){LinearLayout l=new LinearLayout(this);l.setOrientation(LinearLayout.VERTICAL);return l;}
    @Override public void onCreate(Bundle b){super.onCreate(b);
        getWindow().getDecorView().setSystemUiVisibility(5894|View.SYSTEM_UI_FLAG_LAYOUT_STABLE);
        LinearLayout root=column();root.setBackgroundColor(BG);root.setPadding(dp(12),dp(6),dp(12),dp(7));
        LinearLayout header=row();header.setGravity(Gravity.CENTER_VERTICAL);
        TextView badge=text("◉",30,BLUE,true);badge.setGravity(Gravity.CENTER);
        header.addView(badge,new LinearLayout.LayoutParams(dp(48),dp(46)));
        TextView model=text("BMW E60  ",21,WHITE,true);header.addView(model);
        TextView engine=text("530d",21,BLUE,true);header.addView(engine);
        TextView brand=text("Hybrid_e60  ▰",26,WHITE,false);brand.setGravity(Gravity.CENTER);
        header.addView(brand,new LinearLayout.LayoutParams(0,dp(46),1));
        TextView status=text("CANBUS : non connecté",12,BLUE,false);header.addView(status);
        clock=text("--:--",21,WHITE,true);clock.setGravity(Gravity.RIGHT|Gravity.CENTER_VERTICAL);
        header.addView(clock,new LinearLayout.LayoutParams(dp(86),dp(46)));
        root.addView(header);
        View line=new View(this);line.setBackgroundColor(BLUE);root.addView(line,new LinearLayout.LayoutParams(-1,dp(1)));
        LinearLayout middle=row();middle.setPadding(0,dp(12),0,dp(10));
        LinearLayout left=column(),right=column();
        String[][] leftData={{"♨","TEMP. LIQUIDE","-- °C"},{"◈","TEMP. HUILE","-- °C"},{"▣","TENSION BATTERIE","-- V"},{"◎","PRESSION TURBO","-- bar"}};
        String[][] rightData={{"◴","RÉGIME MOTEUR","-- tr/min"},{"◉","PUISSANCE","-- ch"},{"⚙","COUPLE","-- Nm"},{"♨","TEMP. ADMISSION","-- °C"}};
        for(String[] a:leftData)left.addView(metric(a),new LinearLayout.LayoutParams(-1,0,1));
        for(String[] a:rightData)right.addView(metric(a),new LinearLayout.LayoutParams(-1,0,1));
        middle.addView(left,new LinearLayout.LayoutParams(0,-1,1));
        FrameLayout carFrame=new FrameLayout(this);carFrame.setBackground(shape(Color.rgb(5,14,27),Color.rgb(12,55,89),16));
        Car3DView car=new Car3DView(this);carFrame.addView(car,new FrameLayout.LayoutParams(-1,-1));
        TextView carCaption=text("BMW E60 530d  •  ROTATION 3D",13,BLUE,true);
        carCaption.setGravity(Gravity.CENTER);carCaption.setBackground(shape(Color.rgb(7,19,33),Color.rgb(0,88,133),12));
        FrameLayout.LayoutParams cap=new FrameLayout.LayoutParams(dp(250),dp(29),Gravity.BOTTOM|Gravity.CENTER_HORIZONTAL);cap.bottomMargin=dp(7);
        carFrame.addView(carCaption,cap);
        LinearLayout.LayoutParams carParams=new LinearLayout.LayoutParams(0,-1,2.6f);carParams.setMargins(dp(8),0,dp(8),0);
        middle.addView(carFrame,carParams);middle.addView(right,new LinearLayout.LayoutParams(0,-1,1));
        root.addView(middle,new LinearLayout.LayoutParams(-1,0,1));
        HorizontalScrollView scroll=new HorizontalScrollView(this);scroll.setHorizontalScrollBarEnabled(false);
        LinearLayout nav=row();nav.setGravity(Gravity.CENTER_VERTICAL);
        String[] labels={"Véhicule","Moteur","Climatisation","Multimédia","Navigation","Diagnostic","Applications","Réglages"};
        String[] icons={"▰","⚙","✺","♫","➤","⌁","▦","⚙"};
        for(int i=0;i<labels.length;i++){
            final String name=labels[i];LinearLayout item=column();item.setGravity(Gravity.CENTER);
            item.setBackground(shape(i==0?Color.rgb(0,49,86):PANEL,i==0?BLUE:Color.rgb(32,69,101),13));
            TextView ico=text(icons[i],25,i==0?BLUE:WHITE,true);ico.setGravity(Gravity.CENTER);item.addView(ico);
            TextView label=text(name,13,WHITE,false);label.setGravity(Gravity.CENTER);item.addView(label);
            LinearLayout.LayoutParams ip=new LinearLayout.LayoutParams(dp(132),dp(69));ip.setMargins(dp(3),0,dp(3),0);nav.addView(item,ip);
            item.setOnClickListener(v->onMenu(name));
        }
        scroll.addView(nav);root.addView(scroll,new LinearLayout.LayoutParams(-1,dp(73)));
        setContentView(root);handler.post(clockTick);
    }
    private View metric(String[] values){
        LinearLayout panel=row();panel.setGravity(Gravity.CENTER_VERTICAL);panel.setPadding(dp(9),dp(5),dp(6),dp(5));
        panel.setBackground(shape(PANEL,Color.rgb(27,67,103),14));
        TextView icon=text(values[0],22,WHITE,true);icon.setGravity(Gravity.CENTER);
        panel.addView(icon,new LinearLayout.LayoutParams(dp(36),-1));
        LinearLayout info=column();info.setGravity(Gravity.CENTER_VERTICAL);
        TextView title=text(values[1],11,WHITE,false);info.addView(title);
        TextView val=text(values[2],23,BLUE,true);info.addView(val);
        panel.addView(info,new LinearLayout.LayoutParams(0,-1,1));
        LinearLayout wrap=column();LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-1);p.setMargins(0,0,0,dp(7));wrap.addView(panel,p);
        return wrap;
    }
    private void onMenu(String name){
        switch(name){
            case "Véhicule":return;
            case "Navigation":
                if(launchPackage("com.waze")||launchPackage("com.google.android.apps.maps"))return;
                try{startActivity(new Intent(Intent.ACTION_VIEW,Uri.parse("geo:0,0?q=")));}catch(Exception e){message("Navigation","Aucune application de navigation disponible.");}return;
            case "Multimédia":
                if(launchPackage("com.spotify.music")||launchPackage("com.google.android.apps.youtube.music"))return;
                try{Intent i=new Intent("android.intent.action.MUSIC_PLAYER");startActivity(i);}catch(Exception e){message("Multimédia","Aucun lecteur de musique trouvé.");}return;
            case "Réglages":startActivity(new Intent(Settings.ACTION_SETTINGS));return;
            case "Applications":
                try{Intent i=new Intent(Intent.ACTION_MAIN);i.addCategory(Intent.CATEGORY_APP_BROWSER);startActivity(i);}catch(Exception e){message("Applications","Ouvre le tiroir d'applications de ton autoradio. Les applications disponibles dépendent du système.");}return;
            case "Moteur":case "Diagnostic":
                message(name,"Données CANBUS indisponibles pour le moment. Le protocole CarCanbus C83xCIC doit être identifié avant la lecture réelle des valeurs.");return;
            case "Climatisation":
                message(name,"Commande de climatisation non disponible : intégration CANBUS à vérifier. Aucun ordre n'est envoyé au véhicule.");return;
        }
    }
    private boolean launchPackage(String pkg){try{Intent i=getPackageManager().getLaunchIntentForPackage(pkg);if(i!=null){startActivity(i);return true;}}catch(Exception ignored){}return false;}
    private void message(String title,String body){new AlertDialog.Builder(this).setTitle(title).setMessage(body).setPositiveButton("OK",null).show();}
    @Override protected void onDestroy(){handler.removeCallbacks(clockTick);super.onDestroy();}
}
