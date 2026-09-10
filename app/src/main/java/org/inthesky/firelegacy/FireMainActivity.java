package org.inthesky.firelegacy;

import android.app.Activity;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.os.Bundle;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.*;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.location.Location;
import android.location.LocationManager;
import android.view.*;
import android.widget.*;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.*;
import java.net.*;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManagerFactory;
import java.security.KeyStore;
import java.security.cert.Certificate;
import java.security.cert.CertificateFactory;

public class FireMainActivity extends Activity {
    private static final String APP_VERSION = "3.6.23";
    private static final String BUILD_LABEL = "Windows Parity + Native Functionality";
    private static final int BG = Color.rgb(2,7,5);
    private static final int PANEL = Color.rgb(4,10,14);
    private static final int PANEL_RAISED = Color.rgb(7,18,23);
    private static int GREEN = Color.rgb(79,255,159);
    private static final int GREEN_SOFT = Color.rgb(42,190,112);
    private static final int DIM = Color.rgb(97,169,129);
    private static final int CYAN = Color.rgb(102,228,255);
    private static final int AMBER = Color.rgb(255,194,92);
    private static final int TEXT = Color.rgb(225,255,238);
    private static final int BORDER = Color.rgb(34,96,104);

    private final Handler ui = new Handler(Looper.getMainLooper());
    private final ExecutorService io = Executors.newFixedThreadPool(3);
    private SharedPreferences prefs;
    private LinearLayout root;
    private FrameLayout pageHost, appStage;
    private String settingsReturnPage = "RADAR";
    private int nightSunriseMin = 6*60+30, nightSunsetMin = 19*60+30;
    private RadarView radarView;
    private LinearLayout aircraftCard;
    private ImageView aircraftImage;
    private TextView aircraftTitle, aircraftDetails, aircraftReference;
    private RadarNearestView radarNearestView;
    private TextView weatherBody, weatherStatus, weatherCurrent, weatherFiveDay, weatherWarnings, weatherCity;
    private WeatherIconView weatherHeroIconView;
    private WeatherLocalView weatherLocalView;
    private LinearLayout fiveDayGrid, worldClocksBox;
    private WeatherTrendView temperatureTrendView, humidityTrendView, windTrendView, pressureTrendView, rainTrendView;
    private TextView timeClock, timeDate, timeZones, launchSummary;
    private Button launchMoreButton;
    private final List<LegacyLaunch> launchList = new ArrayList<LegacyLaunch>();
    private boolean launchesExpanded = false;
    private WeatherCompassView windViewRef;
    private WeatherHistoryView historyViewRef;
    private Runnable radarTick, clockTick, pageCycleTick, headerTick, weatherRefreshTick, worldTrafficTick;
    private TextView headerClock;
    private Set<String> previousAlertContacts = null;
    private String currentPage = "RADAR";
    private SSLSocketFactory weatherSslFactory;
    private List<Aircraft> aircraft = new ArrayList<Aircraft>();
    private Set<String> previousContacts = null;
    private String selectedHex = null;
    private String pendingFlightCallsign = null;
    private boolean pendingFlightFocus = false;
    private boolean pendingSkyFocus = false;
    private String flightFollowHex = null;
    private Runnable flightFollowTick;
    private LinearLayout flightLiveBox;
    private TextView headerBrand, headerServices, bottomLeft, bottomRight;
    private final List<Button> navButtons = new ArrayList<Button>();
    private FlightMapView flightMapView;
    private WorldMapView launchMapView, spaceMapView;
    private TextView flightResult, flightLiveList, issTelemetry, moonInfo, onThisDayText;
    private EditText flightInput;
    private LinearLayout launchesListBox;
    private TextView launchDetailFull, launchCountdownFull;
    private LegacyLaunch selectedLaunchFull;
    private ImageView launchImageFull;
    private TextView skyObjectStatus;
    private boolean skyShowSmallBodies = true;
    private final List<SmallBodyTarget> skySmallBodies = new ArrayList<SmallBodyTarget>();
    private final List<AstroTarget> skyBelowHorizon = new ArrayList<AstroTarget>();
    private int skySatelliteBelowCount = 0, skySmallBodyBelowCount = 0;
    private String skySatelliteSource = "", skySmallBodySource = "";
    private TextView skySelectedText, skyLiveText, skyViewTitle, skyDetailsText;
    private LinearLayout skyDetailsPanel;
    private Button skyDetailsButton;
    private boolean skyDetailsExpanded = false;
    private String selectedSkyObjectName = null;
    private ImageView skyReferenceImage;
    private SolarSystemView solarSystemView;
    private SkyPanoramaView skyPanoramaView;
    private TextView flightJourney, flightViewStatus;
    private double[] flightOriginCoords, flightDestCoords;
    private final List<AstroTarget> skyCelestial = new ArrayList<AstroTarget>();
    private final List<SatelliteTarget> skySatellites = new ArrayList<SatelliteTarget>();
    private boolean skyShowSatellites = true;
    private final List<WorldContact> worldAircraft = new ArrayList<WorldContact>();
    private String worldAircraftSource = "WAITING";
    private long worldAircraftUpdatedAt = 0L;
    private String openSkyToken = null;
    private long openSkyTokenExpiry = 0L;
    private volatile String openSkyAuthState = "NOT CONFIGURED";
    private volatile long weatherLastSuccessfulUpdate = 0L;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        enableTls12();
        prefs = getSharedPreferences("fire_settings", MODE_PRIVATE);
        // v3.6.10 development build: force the known OpenSky API client credentials
        // so stale values saved by earlier builds cannot override them.
        prefs.edit()
            .putString("openSkyClientId","rob.howden@googlemail.com-api-client")
            .putString("openSkyClientSecret","Dzq1Sv3I8HzrlL8nYMlPyOonvo58kg6B")
            .apply();
        migrateOrientationPreferences();
        applyTheme(prefs.getString("theme", "PHOSPHOR"));
        getWindow().setStatusBarColor(BG);
        buildShell();
        refreshNightWindow();
        openPage(prefs.getString("startup","RADAR"));
        schedulePageCycle();
        refreshOnThisDay();
        scheduleWeatherRefresh();
        validateOpenSkyCredentials();
    }

    private void refreshNightWindow(){io.execute(()->{try{String url="https://api.open-meteo.com/v1/forecast?latitude="+currentLat()+"&longitude="+currentLon()+"&daily=sunrise,sunset&timezone=auto&forecast_days=1";JSONObject d;try{d=getJson(url).getJSONObject("daily");}catch(Exception ex){JSONObject cached=readJsonCache(url,24L*60*60_000L);if(cached==null)throw ex;d=cached.getJSONObject("daily");}String rise=d.getJSONArray("sunrise").optString(0),set=d.getJSONArray("sunset").optString(0);nightSunriseMin=timeMinutes(rise);nightSunsetMin=timeMinutes(set);}catch(Exception ignored){}ui.post(this::applyNightDim);});}
    private void applyNightDim(){boolean enabled=prefs.getBoolean("nightDim",true);Calendar c=Calendar.getInstance();int now=c.get(Calendar.HOUR_OF_DAY)*60+c.get(Calendar.MINUTE);boolean night=now<nightSunriseMin||now>=nightSunsetMin;WindowManager.LayoutParams lp=getWindow().getAttributes();lp.screenBrightness=(enabled&&night)?0.32f:-1f;getWindow().setAttributes(lp);}

    private void enableTls12() {
        try {
            SSLContext sc = SSLContext.getInstance("TLSv1.2");
            sc.init(null, null, null);
            HttpsURLConnection.setDefaultSSLSocketFactory(sc.getSocketFactory());
        } catch (Exception ignored) {}

        try {
            CertificateFactory cf = CertificateFactory.getInstance("X.509");
            InputStream in = getResources().openRawResource(R.raw.isrgrootx1);
            Certificate ca = cf.generateCertificate(in);
            in.close();

            KeyStore store = KeyStore.getInstance(KeyStore.getDefaultType());
            store.load(null, null);
            store.setCertificateEntry("isrg-root-x1", ca);

            TrustManagerFactory tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
            tmf.init(store);

            SSLContext weather = SSLContext.getInstance("TLSv1.2");
            weather.init(null, tmf.getTrustManagers(), null);
            weatherSslFactory = weather.getSocketFactory();
        } catch (Exception ignored) {
            weatherSslFactory = null;
        }
    }

    private void migrateOrientationPreferences() {
        // v3.5.8: Radar and Sky View used to share the same "orientation" key.
        // Preserve the existing value once, then keep the two instruments independent.
        if (!prefs.contains("radarOrientation") || !prefs.contains("skyOrientation")) {
            int legacy=((prefs.getInt("orientation",180)%360)+360)%360;
            SharedPreferences.Editor e=prefs.edit();
            if(!prefs.contains("radarOrientation")) e.putInt("radarOrientation",legacy);
            if(!prefs.contains("skyOrientation")) e.putInt("skyOrientation",legacy);
            e.apply();
        }
    }

    private void applyTheme(String theme) {
        if ("AMBER".equals(theme)) GREEN = AMBER;
        else if ("CYAN".equals(theme)) GREEN = CYAN;
        else if ("RED".equals(theme)) GREEN = Color.rgb(255,102,119);
        else if ("VIOLET".equals(theme)) GREEN = Color.rgb(183,140,255);
        else if ("CUSTOM".equals(theme)) {
            try { GREEN = Color.parseColor(prefs.getString("customAccent","#4FFF9F")); }
            catch(Exception ignored) { GREEN = Color.rgb(79,255,159); }
        } else GREEN = Color.rgb(79,255,159);
    }

    private GradientDrawable panelBackground(boolean strong) {
        GradientDrawable g=new GradientDrawable();
        int base=strong?PANEL_RAISED:PANEL;
        int alpha=strong?108:58;
        g.setColor(Color.argb(alpha,Color.red(base),Color.green(base),Color.blue(base)));
        g.setStroke(dp(strong?3:2), strong?GREEN:BORDER);
        g.setCornerRadius(dp(4));
        return g;
    }

    private GradientDrawable selectedBackground() {
        GradientDrawable g=new GradientDrawable();
        g.setColor(Color.argb(62,Color.red(GREEN),Color.green(GREEN),Color.blue(GREEN)));
        g.setStroke(dp(3),GREEN);
        g.setCornerRadius(dp(4));
        return g;
    }

    private TextView text(String s, int sp, int color) {
        TextView v = new TextView(this);
        v.setText(s);
        float readableSp = sp;
        if(sp<=9) readableSp=11f;
        else if(sp==10) readableSp=12f;
        else if(sp==11) readableSp=13f;
        else if(sp==12) readableSp=14f;
        else if(sp==13) readableSp=14.5f;
        else if(sp==14) readableSp=15.5f;
        else if(sp==15) readableSp=16.5f;
        v.setTextSize(readableSp); v.setTextColor(color);
        v.setTypeface(Typeface.MONOSPACE);
        v.setPadding(dp(7),dp(5),dp(7),dp(5));
        return v;
    }

    private LinearLayout card(String title) {
        LinearLayout box=new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(7),dp(5),dp(7),dp(7));
        box.setBackground(panelBackground(false));
        TextView heading=text(title,11,CYAN);
        heading.setTypeface(Typeface.MONOSPACE,Typeface.BOLD);
        heading.setGravity(Gravity.CENTER_VERTICAL);
        box.addView(heading,new LinearLayout.LayoutParams(-1,dp(30)));
        return box;
    }

    private double normDeg(double x){x%=360.0;return x<0?x+360.0:x;}
    private double julianDay(long millis){return millis/86400000.0+2440587.5;}
    private double gmstDegrees(long millis){
        double jd=julianDay(millis),t=(jd-2451545.0)/36525.0;
        return normDeg(280.46061837+360.98564736629*(jd-2451545.0)+.000387933*t*t-t*t*t/38710000.0);
    }
    private double[] horizontalCoordinates(long millis,double lat,double lon,double raHours,double decDeg){
        double lst=normDeg(gmstDegrees(millis)+lon),ha=Math.toRadians(normDeg(lst-raHours*15.0));
        if(ha>Math.PI)ha-=2*Math.PI;
        double phi=Math.toRadians(lat),dec=Math.toRadians(decDeg);
        double sinAlt=Math.sin(phi)*Math.sin(dec)+Math.cos(phi)*Math.cos(dec)*Math.cos(ha);
        double alt=Math.asin(Math.max(-1,Math.min(1,sinAlt)));
        double az=Math.atan2(-Math.sin(ha)*Math.cos(dec),Math.sin(dec)*Math.cos(phi)-Math.cos(dec)*Math.sin(phi)*Math.cos(ha));
        return new double[]{normDeg(Math.toDegrees(az)),Math.toDegrees(alt)};
    }
    private double[] sunEquatorial(long millis){
        double d=julianDay(millis)-2451545.0,g=Math.toRadians(normDeg(357.529+0.98560028*d));
        double q=normDeg(280.459+0.98564736*d),L=Math.toRadians(normDeg(q+1.915*Math.sin(g)+.020*Math.sin(2*g)));
        double e=Math.toRadians(23.439-.00000036*d);
        double ra=normDeg(Math.toDegrees(Math.atan2(Math.cos(e)*Math.sin(L),Math.cos(L))))/15.0;
        double dec=Math.toDegrees(Math.asin(Math.sin(e)*Math.sin(L)));
        return new double[]{ra,dec};
    }
    private double[] sunHorizontal(long millis,double lat,double lon){double[] eq=sunEquatorial(millis);return horizontalCoordinates(millis,lat,lon,eq[0],eq[1]);}
    private double[] moonEquatorialApprox(long millis){
        double d=julianDay(millis)-2451543.5;
        double N=Math.toRadians(normDeg(125.1228-.0529538083*d)),i=Math.toRadians(5.1454),w=Math.toRadians(normDeg(318.0634+.1643573223*d));
        double a=60.2666,e=.054900,M=Math.toRadians(normDeg(115.3654+13.0649929509*d));
        double E=M+e*Math.sin(M)*(1+e*Math.cos(M));for(int k=0;k<5;k++)E-= (E-e*Math.sin(E)-M)/(1-e*Math.cos(E));
        double xv=a*(Math.cos(E)-e),yv=a*Math.sqrt(1-e*e)*Math.sin(E),v=Math.atan2(yv,xv),r=Math.sqrt(xv*xv+yv*yv),vw=v+w;
        double xh=r*(Math.cos(N)*Math.cos(vw)-Math.sin(N)*Math.sin(vw)*Math.cos(i)),yh=r*(Math.sin(N)*Math.cos(vw)+Math.cos(N)*Math.sin(vw)*Math.cos(i)),zh=r*Math.sin(vw)*Math.sin(i);
        double ob=Math.toRadians(23.4393-3.563E-7*d),xe=xh,ye=yh*Math.cos(ob)-zh*Math.sin(ob),ze=yh*Math.sin(ob)+zh*Math.cos(ob);
        double ra=normDeg(Math.toDegrees(Math.atan2(ye,xe)))/15.0,dec=Math.toDegrees(Math.atan2(ze,Math.sqrt(xe*xe+ye*ye)));
        return new double[]{ra,dec};
    }
    private double[] moonHorizontalApprox(long millis,double lat,double lon){double[] eq=moonEquatorialApprox(millis);return horizontalCoordinates(millis,lat,lon,eq[0],eq[1]);}
    private double[] sunSubpoint(long millis){double[] eq=sunEquatorial(millis);return new double[]{eq[1],((eq[0]*15.0-gmstDegrees(millis)+540)%360)-180};}
    private double[] moonSubpointApprox(long millis){double[] eq=moonEquatorialApprox(millis);return new double[]{eq[1],((eq[0]*15.0-gmstDegrees(millis)+540)%360)-180};}

    private Button button(String s) {
        Button b = new Button(this);
        b.setText(s); b.setTextSize(12.5f); b.setTextColor(TEXT);
        b.setBackground(panelBackground(true));
        b.setAllCaps(true);
        b.setMinHeight(0); b.setMinimumHeight(0);
        b.setPadding(dp(7),dp(1),dp(7),dp(1));
        return b;
    }


    private void postRadarNotification(int id,String title,String body){
        try{
            NotificationManager nm=(NotificationManager)getSystemService(Context.NOTIFICATION_SERVICE);
            if(nm==null)return;
            String channel="radar_contacts";
            Notification.Builder b;
            if(Build.VERSION.SDK_INT>=26){
                NotificationChannel ch=new NotificationChannel(channel,"Radar contacts",NotificationManager.IMPORTANCE_DEFAULT);
                ch.setDescription("Aircraft entering the configured radar alert radius");
                nm.createNotificationChannel(ch);
                b=new Notification.Builder(this,channel);
            }else b=new Notification.Builder(this);
            Intent intent=new Intent(this,FireMainActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP|Intent.FLAG_ACTIVITY_SINGLE_TOP);
            PendingIntent pi=PendingIntent.getActivity(this,0,intent,PendingIntent.FLAG_UPDATE_CURRENT);
            b.setSmallIcon(android.R.drawable.ic_dialog_info).setContentTitle(title).setContentText(body)
                .setStyle(new Notification.BigTextStyle().bigText(body)).setContentIntent(pi).setAutoCancel(true);
            nm.notify(id,b.build());
        }catch(Exception ignored){}
    }

    private void buildShell() {
        FrameLayout stage = new FrameLayout(this);
        appStage = stage;
        stage.setBackgroundColor(BG);
        stage.addView(new NebulaBackgroundView(this), new FrameLayout.LayoutParams(-1,-1));

        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(3),dp(2),dp(3),dp(3));
        root.setBackgroundColor(Color.TRANSPARENT);

        // Windows Store style status/header row.
        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(dp(6),0,dp(3),0);
        header.setBackgroundColor(Color.argb(142,Color.red(BG),Color.green(BG),Color.blue(BG)));

        headerBrand = text("IN THE SKY  //  RADAR LIVE",14,TEXT);
        headerBrand.setTypeface(Typeface.MONOSPACE,Typeface.BOLD);
        header.addView(headerBrand,new LinearLayout.LayoutParams(0,dp(42),1.50f));

        headerClock=text("",10,TEXT); headerClock.setGravity(Gravity.CENTER); headerClock.setTypeface(Typeface.MONOSPACE,Typeface.BOLD);
        header.addView(headerClock,new LinearLayout.LayoutParams(dp(255),dp(42)));

        headerServices = text("OPENSKY --  •  WX HOURLY  •  ISS LIVE  •  LCH LIVE",9,CYAN);
        headerServices.setGravity(Gravity.CENTER_VERTICAL);
        headerServices.setSingleLine(true);
        header.addView(headerServices,new LinearLayout.LayoutParams(0,dp(42),1.72f));

        Button refreshAll=button("REFRESH");
        refreshAll.setOnClickListener(v -> refreshCurrentPage());
        header.addView(refreshAll,new LinearLayout.LayoutParams(dp(82),dp(32)));

        Button settings=button("SETTINGS");
        settings.setOnClickListener(v -> { settingsReturnPage=currentPage; openPage("SETTINGS"); });
        LinearLayout.LayoutParams slp=new LinearLayout.LayoutParams(dp(94),dp(32)); slp.setMargins(dp(5),0,0,0);
        header.addView(settings,slp);

        Spinner themeSpinner=new Spinner(this);
        final String[] themeIds={"PHOSPHOR","AMBER","CYAN","RED","VIOLET","CUSTOM"};
        final String[] themeLabels={"GREEN","AMBER","ICE BLUE","RED","VIOLET","CUSTOM"};
        themeSpinner.setAdapter(new ArrayAdapter<String>(this,android.R.layout.simple_spinner_dropdown_item,themeLabels));
        int ti=0; String saved=prefs.getString("theme","PHOSPHOR");
        for(int i=0;i<themeIds.length;i++)if(themeIds[i].equals(saved))ti=i;
        themeSpinner.setSelection(ti,false);
        themeSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener(){
            public void onNothingSelected(AdapterView<?> p){}
            public void onItemSelected(AdapterView<?> p,View v,int pos,long id){
                String chosen=themeIds[Math.max(0,Math.min(themeIds.length-1,pos))];
                if(chosen.equals(prefs.getString("theme","PHOSPHOR"))) return;
                prefs.edit().putString("theme",chosen).apply();
                applyTheme(chosen);
                String keep=currentPage;
                buildShell();
                openPage(keep);
            }
        });
        LinearLayout.LayoutParams tlp=new LinearLayout.LayoutParams(dp(110),dp(32));tlp.setMargins(dp(5),0,0,0);
        header.addView(themeSpinner,tlp);
        root.addView(header,new LinearLayout.LayoutParams(-1,dp(44)));

        // Numbered navigation row exactly like the Windows Store layout.
        LinearLayout nav=new LinearLayout(this); nav.setOrientation(LinearLayout.HORIZONTAL); nav.setPadding(0,dp(2),0,dp(3));
        String[] labels={"1  RADAR","2  FLIGHT","3  WEATHER","4  TIME","5  SPACE","6  SKY VIEW","7  LAUNCHES"};
        String[] pages={"RADAR","FLIGHT","WEATHER","TIME","SPACE","SKY VIEW","LAUNCHES"};
        navButtons.clear();
        for(int i=0;i<labels.length;i++){
            Button b=button(labels[i]); final String page=pages[i];
            b.setTextSize(14.5f);
            b.setTypeface(Typeface.MONOSPACE,Typeface.BOLD);
            b.setOnClickListener(v->openPage(page));
            LinearLayout.LayoutParams nlp=new LinearLayout.LayoutParams(0,dp(33),1f);
            nlp.setMargins(dp(1),0,dp(1),dp(1));
            nav.addView(b,nlp); navButtons.add(b);
        }
        root.addView(nav,new LinearLayout.LayoutParams(-1,dp(39)));

        pageHost=new FrameLayout(this); pageHost.setBackgroundColor(Color.TRANSPARENT);
        root.addView(pageHost,new LinearLayout.LayoutParams(-1,0,1f));

        // Persistent Windows-style bottom status strip.
        LinearLayout footer=new LinearLayout(this); footer.setOrientation(LinearLayout.HORIZONTAL); footer.setGravity(Gravity.CENTER_VERTICAL);
        bottomLeft=text("",12,TEXT); bottomRight=text("",12,TEXT); bottomRight.setGravity(Gravity.RIGHT|Gravity.CENTER_VERTICAL);
        footer.addView(bottomLeft,new LinearLayout.LayoutParams(0,dp(30),1f)); footer.addView(bottomRight,new LinearLayout.LayoutParams(0,dp(30),1f));
        root.addView(footer,new LinearLayout.LayoutParams(-1,dp(31)));

        stage.addView(root,new FrameLayout.LayoutParams(-1,-1)); setContentView(stage); startHeaderClock(); updateFooter();
    }

    private void refreshCurrentPage(){
        if("RADAR".equals(currentPage)) openPage("RADAR");
        else if("WEATHER".equals(currentPage)) openPage("WEATHER");
        else if("SPACE".equals(currentPage)) openPage("SPACE");
        else if("LAUNCHES".equals(currentPage)) openPage("LAUNCHES");
        else if("FLIGHT".equals(currentPage)){refreshWorldTraffic(true);openPage("FLIGHT");}
        else openPage(currentPage);
    }

    private void updateFooter(){
        if(bottomLeft!=null) bottomLeft.setText(locationLabel()+"   •   "+String.format(Locale.US,"%.4f, %.4f",currentLat(),currentLon())+"   •   RANGE "+distanceLabel(prefs.getInt("range",16))+"   •   UP "+orientationLabel());
        if(bottomRight!=null){
            SimpleDateFormat f=new SimpleDateFormat("HH:mm:ss   dd MMM yyyy",Locale.UK); f.setTimeZone(TimeZone.getTimeZone("UTC"));
            bottomRight.setText("v"+APP_VERSION+"   •   UTC "+f.format(new Date()).toUpperCase(Locale.UK));
        }
    }

    private String orientationLabel(){return orientationName(prefs.getInt("radarOrientation",180));}
    private String skyOrientationLabel(){return orientationName(prefs.getInt("skyOrientation",180));}
    private String orientationName(int value){int d=((value%360)+360)%360;if(d==0)return "NORTH";if(d==90)return "EAST";if(d==180)return "SOUTH";if(d==270)return "WEST";String[] a={"N","NE","E","SE","S","SW","W","NW"};return a[((int)Math.round(d/45.0))%8]+" "+d+"°";}

    private void updateHeaderServices(){
        if(headerServices==null)return;
        String os;
        if("ACCEPTED".equals(openSkyAuthState))os="OPENSKY ✓";
        else if("REJECTED".equals(openSkyAuthState))os="OPENSKY ✕";
        else if("CHECKING".equals(openSkyAuthState))os="OPENSKY …";
        else os="OPENSKY --";
        headerServices.setText(os+"  •  WX 1H  •  ISS LIVE  •  LCH LIVE");
        headerServices.setTextColor("REJECTED".equals(openSkyAuthState)?Color.rgb(255,105,105):CYAN);
    }

    private void startHeaderClock() {
        if(headerTick!=null) ui.removeCallbacks(headerTick);
        headerTick=new Runnable(){
            public void run(){
                if(headerClock!=null){
                    Date now=new Date();
                    SimpleDateFormat tf=new SimpleDateFormat(prefs.getBoolean("clock24",true)?"HH:mm:ss":"hh:mm:ss a",Locale.UK);
                    SimpleDateFormat df=new SimpleDateFormat("EEE  d MMM yyyy",Locale.UK);
                    if(headerClock!=null) headerClock.setText(tf.format(now)+"   //   "+df.format(now).toUpperCase(Locale.UK));
                    updateHeaderServices();
                    updateFooter();
                    if(selectedLaunchFull!=null&&launchCountdownFull!=null)launchCountdownFull.setText(launchCountdown(selectedLaunchFull.netMillis-System.currentTimeMillis()));
                }
                applyNightDim();ui.postDelayed(this,1000);
            }
        };
        headerTick.run();
    }

    private void openPage(String page) {
        if(!"FLIGHT".equals(page)){flightFollowHex=null;if(flightFollowTick!=null)ui.removeCallbacks(flightFollowTick);}
        currentPage=page;
        if(headerBrand!=null) headerBrand.setText("IN THE SKY  //  "+page+" LIVE");
        for(int i=0;i<navButtons.size();i++){
            String[] pages={"RADAR","FLIGHT","WEATHER","TIME","SPACE","SKY VIEW","LAUNCHES"};
            boolean active=pages[i].equals(page);
            navButtons.get(i).setTextColor(active?GREEN:TEXT);
            navButtons.get(i).setTypeface(Typeface.MONOSPACE,active?Typeface.BOLD:Typeface.NORMAL);
            navButtons.get(i).setBackground(active?selectedBackground():panelBackground(false));
        }
        if("RADAR".equals(page))showRadar();
        else if("FLIGHT".equals(page))showFlight();
        else if("WEATHER".equals(page))showWeather();
        else if("TIME".equals(page))showTime();
        else if("SPACE".equals(page))showSpace();
        else if("SKY VIEW".equals(page))showSkyView();
        else if("LAUNCHES".equals(page))showLaunchesPage();
        else showSettings();
        updateFooter();
    }

    private boolean cyclePageEnabled(String page){
        if("RADAR".equals(page))return prefs.getBoolean("cycleRadar",true);
        if("FLIGHT".equals(page))return prefs.getBoolean("cycleFlight",true);
        if("WEATHER".equals(page))return prefs.getBoolean("cycleWeather",true);
        if("TIME".equals(page))return prefs.getBoolean("cycleTime",true);
        if("SPACE".equals(page))return prefs.getBoolean("cycleSpace",true);
        if("SKY VIEW".equals(page))return prefs.getBoolean("cycleSkyView",true);
        if("LAUNCHES".equals(page))return prefs.getBoolean("cycleLaunches",true);
        return false;
    }

    private String nextCyclePage(){
        String[] pages={"RADAR","FLIGHT","WEATHER","TIME","SPACE","SKY VIEW","LAUNCHES"};
        int enabled=0;for(String page:pages)if(cyclePageEnabled(page))enabled++;
        if(enabled==0)return null;
        int start=-1;for(int i=0;i<pages.length;i++)if(pages[i].equals(currentPage)){start=i;break;}
        for(int step=1;step<=pages.length;step++){
            int i=(start+step+pages.length)%pages.length;
            if(cyclePageEnabled(pages[i]))return pages[i];
        }
        return null;
    }

    private void schedulePageCycle() {
        if(pageCycleTick!=null)ui.removeCallbacks(pageCycleTick);
        pageCycleTick=new Runnable(){public void run(){
            int seconds=Math.max(8,Math.min(120,prefs.getInt("cycleSeconds",20)));
            if(prefs.getBoolean("autoCycle",false)&&!"SETTINGS".equals(currentPage)){
                String next=nextCyclePage();
                if(next!=null&&!next.equals(currentPage))openPage(next);
            }
            ui.postDelayed(this,seconds*1000L);
        }};
        ui.postDelayed(pageCycleTick,Math.max(8,Math.min(120,prefs.getInt("cycleSeconds",20)))*1000L);
    }

    private void clearPage() {
        pageHost.removeAllViews();
        if (clockTick != null) ui.removeCallbacks(clockTick);
    }

    private void showRadar() {
        clearPage();
        LinearLayout page=new LinearLayout(this);page.setOrientation(LinearLayout.VERTICAL);page.setPadding(dp(8),dp(3),dp(8),dp(3));
        TextView status=text("ALL TRAFFIC   //   LIVE CONTACTS",11,CYAN);status.setGravity(Gravity.CENTER);status.setBackground(panelBackground(true));page.addView(status,new LinearLayout.LayoutParams(-1,dp(30)));

        LinearLayout filters=new LinearLayout(this);filters.setOrientation(LinearLayout.HORIZONTAL);
        final String[] trafficVals={"ALL TRAFFIC","AIRBORNE","GROUND","MILITARY"};
        final String[] levelVals={"ALL LEVELS","LOW <10K","MID 10-25K","HIGH >25K","GROUND"};
        final String[] labelVals={"LABELS AUTO","LABELS ON","LABELS OFF"};
        final String[] vectorVals={"VECTOR OFF","VECTOR 1 MIN","VECTOR 2 MIN","VECTOR 5 MIN"};
        Spinner traffic=spinner(trafficVals,prefs.getInt("radarTrafficFilter",0));
        Spinner levels=spinner(levelVals,prefs.getInt("radarLevelFilter",0));
        Spinner labels=spinner(labelVals,prefs.getInt("radarLabelMode",0));
        Spinner vectors=spinner(vectorVals,prefs.getInt("radarVectorMode",2));
        AdapterView.OnItemSelectedListener filterListener=new AdapterView.OnItemSelectedListener(){
            public void onNothingSelected(AdapterView<?> p){} public void onItemSelected(AdapterView<?> p,View v,int pos,long id){
                prefs.edit().putInt("radarTrafficFilter",traffic.getSelectedItemPosition())
                    .putInt("radarLevelFilter",levels.getSelectedItemPosition())
                    .putInt("radarLabelMode",labels.getSelectedItemPosition())
                    .putInt("radarVectorMode",vectors.getSelectedItemPosition()).apply();
                applyRadarDisplay(status);
            }};
        traffic.setOnItemSelectedListener(filterListener);levels.setOnItemSelectedListener(filterListener);labels.setOnItemSelectedListener(filterListener);vectors.setOnItemSelectedListener(filterListener);
        filters.addView(traffic,new LinearLayout.LayoutParams(0,dp(40),1f));filters.addView(levels,new LinearLayout.LayoutParams(0,dp(40),1f));filters.addView(labels,new LinearLayout.LayoutParams(0,dp(40),1f));filters.addView(vectors,new LinearLayout.LayoutParams(0,dp(40),1f));page.addView(filters,new LinearLayout.LayoutParams(-1,dp(42)));

        LinearLayout body=new LinearLayout(this);body.setOrientation(LinearLayout.HORIZONTAL);body.setWeightSum(10f);
        LinearLayout radarCol=new LinearLayout(this);radarCol.setOrientation(LinearLayout.VERTICAL);
        radarView=new RadarView(this);radarView.setHome(currentLat(),currentLon());radarView.setStyle(prefs.getString("radarStyle","classic"));radarView.setOrientation(prefs.getInt("radarOrientation",180));radarView.setTrails(prefs.getBoolean("trails",true));radarView.setMiles(prefs.getBoolean("miles",true));radarView.setAlertRange(prefs.getBoolean("alertEnabled",true),prefs.getInt("alertRange",8));radarView.setLabelMode(prefs.getInt("radarLabelMode",0));radarView.setVectorMinutes(vectorMinutes());
        radarView.setOnAircraftTapListener(new RadarView.OnAircraftTapListener(){public void onTap(Aircraft a){selectAircraft(a);}public void onDoubleTap(Aircraft a){trackAircraftFromRadar(a);}});
        radarCol.addView(radarView,new LinearLayout.LayoutParams(-1,0,1f));
        LinearLayout bottomControls=new LinearLayout(this);bottomControls.setOrientation(LinearLayout.HORIZONTAL);TextView mapStatus=text("RANGE "+distanceLabel(prefs.getInt("range",16))+"   •   UP "+orientationLabel()+"   •   OSM REGIONAL MAP // CACHED",10,CYAN);bottomControls.addView(mapStatus,new LinearLayout.LayoutParams(0,dp(40),1f));
        final String[] styleIds={"classic","tactical","pulse","dual","sonar","atc"};Spinner style=spinner(new String[]{"CLASSIC SCOPE","TACTICAL","PULSE","DUAL","SONAR","ATC"},styleIndex());style.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener(){public void onNothingSelected(AdapterView<?> p){}public void onItemSelected(AdapterView<?> p,View v,int pos,long id){String chosen=styleIds[pos];if(chosen.equals(prefs.getString("radarStyle","classic")))return;prefs.edit().putString("radarStyle",chosen).apply();if(radarView!=null)radarView.setStyle(chosen);}});bottomControls.addView(style,new LinearLayout.LayoutParams(dp(150),dp(40)));
        final boolean miles=prefs.getBoolean("miles",true);final int[] vals={5,10,20,40,80,120,200};String[] rangeLabels=new String[vals.length];for(int i=0;i<vals.length;i++)rangeLabels[i]=vals[i]+(miles?" MI":" KM");Spinner range=spinner(rangeLabels,rangeIndex());range.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener(){public void onNothingSelected(AdapterView<?> p){}public void onItemSelected(AdapterView<?> p,View v,int pos,long id){int km=miles?(int)Math.round(vals[pos]*1.609344):vals[pos];if(km==prefs.getInt("range",16))return;prefs.edit().putInt("range",km).apply();previousAlertContacts=null;previousContacts=null;if(radarView!=null){radarView.setDisplayRange(km);radarView.setAlertRange(prefs.getBoolean("alertEnabled",true),prefs.getInt("alertRange",8));}refreshRadar(status);updateFooter();}});bottomControls.addView(range,new LinearLayout.LayoutParams(dp(100),dp(40)));radarCol.addView(bottomControls,new LinearLayout.LayoutParams(-1,dp(42)));
        body.addView(radarCol,new LinearLayout.LayoutParams(0,-1,6f));

        LinearLayout side=new LinearLayout(this);side.setOrientation(LinearLayout.VERTICAL);side.setPadding(dp(7),0,0,0);
        LinearLayout nearest=card("CONTACTS // NEAREST");
        radarNearestView=new RadarNearestView(this);
        radarNearestView.setTextScale(1.68f);
        radarNearestView.setOnAircraftTapListener(this::selectAircraft);
        radarNearestView.setMinimumHeight(dp(438));
        ScrollView nearestScroll=new ScrollView(this);
        nearestScroll.setFillViewport(false);
        nearestScroll.setVerticalScrollBarEnabled(true);
        nearestScroll.addView(radarNearestView,new ScrollView.LayoutParams(-1,dp(438)));
        nearest.addView(nearestScroll,new LinearLayout.LayoutParams(-1,0,1f));
        side.addView(nearest,new LinearLayout.LayoutParams(-1,0,.82f));
        LinearLayout selected=card("SELECTED CONTACT");
        aircraftImage=new ImageView(this);aircraftImage.setScaleType(ImageView.ScaleType.CENTER_CROP);aircraftImage.setBackgroundColor(Color.argb(180,3,12,8));aircraftImage.setVisibility(View.GONE);selected.addView(aircraftImage,new LinearLayout.LayoutParams(-1,dp(96)));
        aircraftTitle=text("Click an aircraft target or select a contact.",13,TEXT);aircraftDetails=text("New contacts entering the orange alert range are selected automatically. Double-tap a radar target to track it in Flight.",12,TEXT);aircraftReference=text("",10,DIM);selected.addView(aircraftTitle);selected.addView(aircraftDetails);ScrollView aircraftScroll=new ScrollView(this);aircraftScroll.setFillViewport(false);aircraftScroll.addView(aircraftReference,new ScrollView.LayoutParams(-1,-2));selected.addView(aircraftScroll,new LinearLayout.LayoutParams(-1,0,1f));
        LinearLayout selectedActions=new LinearLayout(this);selectedActions.setOrientation(LinearLayout.HORIZONTAL);Button flight=button("OPEN IN FLIGHT");flight.setOnClickListener(v->{Aircraft a=findAircraft(selectedHex);if(a!=null)trackAircraftFromRadar(a);});selectedActions.addView(flight,new LinearLayout.LayoutParams(0,dp(38),1f));Button sky=button("VIEW IN SKY");sky.setOnClickListener(v->{Aircraft a=findAircraft(selectedHex);if(a!=null){pendingSkyFocus=true;prefs.edit().putInt("skyOrientation",(int)Math.round(a.bearing)).apply();}if(selectedHex!=null)openPage("SKY VIEW");});selectedActions.addView(sky,new LinearLayout.LayoutParams(0,dp(38),1f));selected.addView(selectedActions);
        Button deselect=button("DESELECT CONTACT");deselect.setOnClickListener(v->{selectedHex=null;if(radarView!=null)radarView.setSelected(null);if(aircraftImage!=null)aircraftImage.setVisibility(View.GONE);aircraftTitle.setText("Click an aircraft target or select a contact.");aircraftDetails.setText("New contacts entering the orange alert range are selected automatically.");aircraftReference.setText("");});selected.addView(deselect,new LinearLayout.LayoutParams(-1,dp(38)));side.addView(selected,new LinearLayout.LayoutParams(-1,0,1.32f));
        body.addView(side,new LinearLayout.LayoutParams(0,-1,4f));page.addView(body,new LinearLayout.LayoutParams(-1,0,1f));pageHost.addView(page);refreshRadar(status);scheduleRadar(status);
    }

    private int vectorMinutes(){int m=prefs.getInt("radarVectorMode",2);return m==0?0:(m==1?1:(m==3?5:2));}
    private List<Aircraft> filteredAircraft(){
        int traffic=prefs.getInt("radarTrafficFilter",0),level=prefs.getInt("radarLevelFilter",0);ArrayList<Aircraft> out=new ArrayList<Aircraft>();
        for(Aircraft a:aircraft){
            if(traffic==1&&a.onGround)continue;if(traffic==2&&!a.onGround)continue;if(traffic==3&&!a.military)continue;
            int alt=a.altitudeFeet==null?0:a.altitudeFeet;
            if(level==1&&(a.onGround||alt>=10000))continue;if(level==2&&(a.onGround||alt<10000||alt>=25000))continue;if(level==3&&(a.onGround||alt<25000))continue;if(level==4&&!a.onGround)continue;
            out.add(a);
        }return out;
    }
    private void applyRadarDisplay(TextView status){
        List<Aircraft> shown=filteredAircraft();if(radarView!=null){radarView.setLabelMode(prefs.getInt("radarLabelMode",0));radarView.setVectorMinutes(vectorMinutes());radarView.setAircraft(shown,prefs.getInt("range",16));}renderRadarNearest(shown);
        if(status!=null)status.setText((prefs.getInt("radarTrafficFilter",0)==3?"MILITARY":"ALL TRAFFIC")+"   //   "+shown.size()+" CONTACTS   //   DISPLAY FILTER");
    }
    private void trackAircraftFromRadar(Aircraft a){if(a==null)return;selectAircraft(a);pendingFlightCallsign=a.callsign;pendingFlightFocus=true;openPage("FLIGHT");}

    private void scheduleRadar(final TextView status) {
        if (radarTick != null) ui.removeCallbacks(radarTick);
        radarTick = new Runnable() {
            public void run() {
                if (radarView != null && radarView.getParent() != null) {
                    refreshRadar(status);
                    ui.postDelayed(this, prefs.getInt("refresh",30) * 1000L);
                }
            }
        };
        ui.postDelayed(radarTick, prefs.getInt("refresh",30) * 1000L);
    }

    private void refreshRadar(final TextView status) {
        status.setText("SCANNING…");
        final double lat = currentLat(), lon = currentLon();
        final int rangeKm = prefs.getInt("range", 16);
        final int radiusNm = Math.max(1, (int)Math.ceil(rangeKm / 1.852));
        final String primaryUrl="https://api.adsb.lol/v2/point/"+lat+"/"+lon+"/"+radiusNm;
        final String mirrorUrl="https://api.airplanes.live/v2/point/"+lat+"/"+lon+"/"+radiusNm;
        final String secondaryUrl=openSkyUrl(lat,lon,rangeKm);

        io.execute(() -> {
            try {
                final ArrayList<Aircraft> list = new ArrayList<Aircraft>();
                final HashSet<String> mergedHex = new HashSet<String>();
                ArrayList<String> liveSources = new ArrayList<String>();
                boolean anyLive=false;

                try {
                    ArrayList<Aircraft> a=new ArrayList<Aircraft>();
                    parseAdsbRadar(getJson(primaryUrl),lat,lon,rangeKm,a);
                    mergeAircraft(list,mergedHex,a);
                    liveSources.add("ADSB.LOL");
                    anyLive=true;
                } catch(Exception ignored) {}

                try {
                    ArrayList<Aircraft> a=new ArrayList<Aircraft>();
                    parseAdsbRadar(getJson(mirrorUrl),lat,lon,rangeKm,a);
                    mergeAircraft(list,mergedHex,a);
                    liveSources.add("AIRPLANES.LIVE");
                    anyLive=true;
                } catch(Exception ignored) {}

                String radarSource;
                if(anyLive){
                    radarSource=android.text.TextUtils.join(" + ",liveSources);
                } else {
                    try {
                        ArrayList<Aircraft> a=new ArrayList<Aircraft>();
                        parseOpenSkyRadar(getJson(secondaryUrl),lat,lon,rangeKm,a);
                        mergeAircraft(list,mergedHex,a);
                        radarSource="OPENSKY FALLBACK";
                    } catch(Exception secondaryError) {
                        JSONObject cached=readJsonCache(primaryUrl,10*60_000L);
                        if(cached!=null){
                            ArrayList<Aircraft> a=new ArrayList<Aircraft>();
                            parseAdsbRadar(cached,lat,lon,rangeKm,a);
                            mergeAircraft(list,mergedHex,a);
                            radarSource="ADSB.LOL CACHE";
                        } else {
                            cached=readJsonCache(mirrorUrl,10*60_000L);
                            if(cached!=null){
                                ArrayList<Aircraft> a=new ArrayList<Aircraft>();
                                parseAdsbRadar(cached,lat,lon,rangeKm,a);
                                mergeAircraft(list,mergedHex,a);
                                radarSource="AIRPLANES.LIVE CACHE";
                            } else {
                                cached=readJsonCache(secondaryUrl,10*60_000L);
                                if(cached==null) throw secondaryError;
                                ArrayList<Aircraft> a=new ArrayList<Aircraft>();
                                parseOpenSkyRadar(cached,lat,lon,rangeKm,a);
                                mergeAircraft(list,mergedHex,a);
                                radarSource="OPENSKY CACHE";
                            }
                        }
                    }
                }
                final String sourceLabel = radarSource;
                Collections.sort(list, (a,b) -> Double.compare(a.distanceKm,b.distanceKm));
                ui.post(() -> {
                    Set<String> now = new HashSet<String>();
                    Set<String> alertNow = new HashSet<String>();
                    Set<String> oldContacts = previousContacts==null?null:new HashSet<String>(previousContacts);
                    for (Aircraft a : list) now.add(a.hex);

                    Aircraft entered = null;
                    int alertKm=prefs.getInt("alertRange",8);
                    boolean alertOn=prefs.getBoolean("alertEnabled",true);

                    if(alertOn){
                        for(Aircraft a:list){
                            if(a.distanceKm<=alertKm) alertNow.add(a.hex);
                        }

                        // Auto-select a contact only when it crosses INTO the orange alert boundary.
                        // Existing contacts outside the ring may therefore trigger later when they enter it.
                        if(!sourceLabel.contains("CACHE")){
                            for(Aircraft a:list){
                                if(a.distanceKm<=alertKm && (previousAlertContacts==null || !previousAlertContacts.contains(a.hex))){
                                    entered=a;
                                    break;
                                }
                            }
                        }
                    }

                    if(!sourceLabel.contains("CACHE")){
                        previousContacts = now;
                        previousAlertContacts = alertNow;
                    }
                    aircraft = list;
                    if (skyPanoramaView != null) skyPanoramaView.setAircraft(list);
                    if(previousAlertContacts==null) previousAlertContacts=new HashSet<String>();
                    List<Aircraft> shown=filteredAircraft();
                    if (radarView != null) {radarView.setLabelMode(prefs.getInt("radarLabelMode",0));radarView.setVectorMinutes(vectorMinutes());radarView.setAircraft(shown, rangeKm);}
                    int groundCount=0;for(Aircraft a:shown)if(a.onGround)groundCount++;
                    status.setText("ALL TRAFFIC   //   "+shown.size()+" CONTACTS   //   "+groundCount+" GROUND   //   "+sourceLabel);
                    renderRadarNearest(shown);
                    if (entered != null) {
                        selectAircraft(entered);
                        if(oldContacts!=null && prefs.getBoolean("aircraftAlerts",true))
                            postRadarNotification(1401,"Aircraft entered alert radius",entered.callsign+"  •  "+distanceLabel(entered.distanceKm));
                    }
                    if(!sourceLabel.contains("CACHE") && prefs.getBoolean("militaryAlerts",true)){
                        ArrayList<String> mil=new ArrayList<String>();
                        for(Aircraft a:list) if(a.military && a.distanceKm<=alertKm && (oldContacts!=null && !oldContacts.contains(a.hex))) mil.add(a.callsign);
                        if(!mil.isEmpty()) postRadarNotification(1402,"Military-flagged aircraft nearby",android.text.TextUtils.join(", ",mil));
                    }
                });
            } catch (final Exception e) {
                ui.post(() -> status.setText("RADAR UNAVAILABLE // "+shortError(e)));
            }
        });
    }

    private void renderRadarNearest(List<Aircraft> list){if(radarNearestView!=null)radarNearestView.setAircraft(list,prefs.getBoolean("miles",true));}

    private void mergeAircraft(List<Aircraft> out,Set<String> seen,List<Aircraft> incoming) {
        for(Aircraft a:incoming){
            if(a==null)continue;
            String key=(a.hex==null?"":a.hex.trim().toLowerCase(Locale.US));
            if(key.length()==0) key=(a.callsign==null?"":a.callsign.trim().toUpperCase(Locale.US))+"@"+String.format(Locale.US,"%.4f,%.4f",a.lat,a.lon);
            if(seen.add(key)) out.add(a);
        }
    }

    private void validateOpenSkyCredentials(){
        final String id=prefs.getString("openSkyClientId","rob.howden@googlemail.com-api-client").trim();
        final String secret=prefs.getString("openSkyClientSecret","Dzq1Sv3I8HzrlL8nYMlPyOonvo58kg6B").trim();
        if(id.length()==0||secret.length()==0){
            openSkyAuthState="NOT CONFIGURED";ui.post(this::updateHeaderServices);return;
        }
        openSkyAuthState="CHECKING";ui.post(this::updateHeaderServices);
        io.execute(()->{
            try{
                String token=openSkyAccessToken();
                openSkyAuthState=(token!=null&&token.length()>20)?"ACCEPTED":"REJECTED";
            }catch(Exception e){openSkyAuthState="REJECTED";}
            ui.post(this::updateHeaderServices);
        });
    }

    private String openSkyAccessToken() throws Exception {
        long now=System.currentTimeMillis();
        if(openSkyToken!=null&&now<openSkyTokenExpiry-60_000L)return openSkyToken;
        String id=prefs.getString("openSkyClientId","rob.howden@googlemail.com-api-client").trim();
        String secret=prefs.getString("openSkyClientSecret","Dzq1Sv3I8HzrlL8nYMlPyOonvo58kg6B").trim();
        if(id.length()==0||secret.length()==0)throw new IOException("OpenSky API client not configured");

        URL u=new URL("https://auth.opensky-network.org/auth/realms/opensky-network/protocol/openid-connect/token");
        HttpsURLConnection c=(HttpsURLConnection)u.openConnection();
        c.setConnectTimeout(12000);c.setReadTimeout(18000);c.setRequestMethod("POST");c.setDoOutput(true);
        c.setRequestProperty("Content-Type","application/x-www-form-urlencoded");
        c.setRequestProperty("Accept","application/json");
        c.setRequestProperty("User-Agent","InTheSky-FireHD-Legacy/3.6.23");
        String body="grant_type=client_credentials&client_id="+URLEncoder.encode(id,"UTF-8")+
            "&client_secret="+URLEncoder.encode(secret,"UTF-8");
        OutputStream out=c.getOutputStream();out.write(body.getBytes("UTF-8"));out.flush();out.close();
        try{
            int code=c.getResponseCode();if(code<200||code>299)throw new IOException("OpenSky auth HTTP "+code);
            BufferedReader br=new BufferedReader(new InputStreamReader(c.getInputStream(),"UTF-8"));
            StringBuilder sb=new StringBuilder();String line;while((line=br.readLine())!=null)sb.append(line);br.close();
            JSONObject j=new JSONObject(sb.toString());
            String token=j.optString("access_token","");
            if(token.length()==0)throw new IOException("OpenSky auth token missing");
            long expires=Math.max(300,j.optLong("expires_in",1800));
            openSkyToken=token;openSkyTokenExpiry=System.currentTimeMillis()+expires*1000L;openSkyAuthState="ACCEPTED";
            return token;
        }finally{c.disconnect();}
    }

    private JSONObject getJsonBearer(String url,String token) throws Exception {
        HttpsURLConnection c=(HttpsURLConnection)new URL(url).openConnection();
        c.setConnectTimeout(12000);c.setReadTimeout(22000);
        c.setRequestProperty("Accept","application/json");
        c.setRequestProperty("Authorization","Bearer "+token);
        c.setRequestProperty("User-Agent","InTheSky-FireHD-Legacy/3.6.23");
        try{
            int code=c.getResponseCode();if(code<200||code>299)throw new IOException("HTTP "+code);
            BufferedReader br=new BufferedReader(new InputStreamReader(c.getInputStream(),"UTF-8"));
            StringBuilder sb=new StringBuilder();String line;while((line=br.readLine())!=null)sb.append(line);br.close();
            String body=sb.toString();JSONObject parsed=new JSONObject(body);
            writeCache(cacheFile("json",url),body);
            return parsed;
        }finally{c.disconnect();}
    }

    private void parseOpenSkyWorld(JSONObject root,List<WorldContact> out){
        JSONArray states=root.optJSONArray("states");if(states==null)return;
        for(int i=0;i<states.length();i++){
            JSONArray st=states.optJSONArray(i);
            if(st==null||st.length()<11||st.isNull(5)||st.isNull(6))continue;
            float lon=(float)st.optDouble(5,Double.NaN),lat=(float)st.optDouble(6,Double.NaN);
            if(Float.isNaN(lat)||Float.isNaN(lon))continue;
            String hex=st.optString(0,""),callsign=st.optString(1,"").trim();
            boolean ground=st.optBoolean(8,false);
            boolean mil=militaryHint(hex,callsign,"");
            out.add(new WorldContact(lat,lon,ground,mil));
        }
    }

    private void refreshWorldTraffic(boolean force){
        // ADSB.lol does not expose a documented unrestricted whole-network snapshot
        // through its public API. For WORLD mode, authenticated OpenSky is therefore
        // the practical fallback. This request is deliberately on-demand/stale-gated,
        // not a permanent 60-second background job on legacy Fire hardware.
        final String openSkyWorldUrl="https://opensky-network.org/api/states/all";
        long age=System.currentTimeMillis()-worldAircraftUpdatedAt;
        if(!force && !worldAircraft.isEmpty() && age<5L*60_000L){
            if(flightMapView!=null)flightMapView.setWorldTraffic(worldAircraft,worldAircraftSource);
            if(flightViewStatus!=null)flightViewStatus.setText("WORLD VIEW  //  "+worldAircraft.size()+" LIVE AIRCRAFT  //  "+worldAircraftSource);
            return;
        }
        if(flightViewStatus!=null)flightViewStatus.setText("WORLD VIEW  //  LOADING OPENSKY GLOBAL SNAPSHOT…");
        io.execute(()->{
            ArrayList<WorldContact> loaded=new ArrayList<WorldContact>();
            String source="";
            try{
                JSONObject root;
                try{
                    String token=openSkyAccessToken();
                    root=getJsonBearer(openSkyWorldUrl,token);
                    source="OPENSKY AUTH";
                }catch(Exception authError){
                    JSONObject cached=readJsonCache(openSkyWorldUrl,15L*60_000L);
                    if(cached==null)throw authError;
                    root=cached;source="OPENSKY CACHE";
                }
                parseOpenSkyWorld(root,loaded);
            }catch(Exception e){}

            if(!loaded.isEmpty()){
                final ArrayList<WorldContact> finalLoaded=loaded;final String finalSource=source;
                synchronized(worldAircraft){worldAircraft.clear();worldAircraft.addAll(finalLoaded);}
                worldAircraftSource=finalSource;worldAircraftUpdatedAt=System.currentTimeMillis();
                ui.post(()->{
                    if(flightMapView!=null)flightMapView.setWorldTraffic(finalLoaded,finalSource);
                    if(flightViewStatus!=null&&flightMapView!=null&&flightMapView.isWorldView())
                        flightViewStatus.setText("WORLD VIEW  //  "+finalLoaded.size()+" LIVE AIRCRAFT  //  "+finalSource);
                });
            }else{
                final String msg="WORLD AIR UNAVAILABLE  //  OPENSKY "+openSkyAuthState;
                worldAircraftSource=msg;
                ui.post(()->{if(flightViewStatus!=null)flightViewStatus.setText(msg);});
            }
        });
    }

    private String openSkyUrl(double lat,double lon,int rangeKm) {
        double latSpan=rangeKm/111.0;
        double cos=Math.max(0.2,Math.cos(Math.toRadians(lat)));
        double lonSpan=rangeKm/(111.0*cos);
        return String.format(Locale.US,
            "https://opensky-network.org/api/states/all?lamin=%.5f&lomin=%.5f&lamax=%.5f&lomax=%.5f",
            lat-latSpan,lon-lonSpan,lat+latSpan,lon+lonSpan);
    }

    private void parseAdsbRadar(JSONObject root,double lat,double lon,int rangeKm,List<Aircraft> out) {
        JSONArray arr=root.optJSONArray("ac");
        if(arr==null)return;
        for(int i=0;i<arr.length();i++){
            JSONObject o=arr.optJSONObject(i); if(o==null||!o.has("lat")||!o.has("lon"))continue;
            Aircraft a=Aircraft.from(o,lat,lon);
            if(a.distanceKm<=rangeKm)out.add(a);
        }
    }

    private void parseOpenSkyRadar(JSONObject root,double lat,double lon,int rangeKm,List<Aircraft> out) {
        JSONArray states=root.optJSONArray("states");
        if(states==null)return;
        for(int i=0;i<states.length();i++){
            JSONArray st=states.optJSONArray(i);
            if(st==null||st.length()<11||st.isNull(5)||st.isNull(6))continue;
            Aircraft a=Aircraft.fromOpenSky(st,lat,lon);
            if(a.distanceKm<=rangeKm)out.add(a);
        }
    }

    private ArrayList<Aircraft> loadOpenSkyFallback(double lat,double lon,int rangeKm) throws Exception {
        ArrayList<Aircraft> out=new ArrayList<Aircraft>();
        parseOpenSkyRadar(getJson(openSkyUrl(lat,lon,rangeKm)),lat,lon,rangeKm,out);
        return out;
    }

    private void selectAircraft(final Aircraft a) {
        selectedHex = a.hex;
        if (radarView != null) radarView.setSelected(a.hex);
        aircraftTitle.setText(a.callsign+"  //  "+a.type);
        aircraftDetails.setText(
            "ICAO "+a.hex.toUpperCase(Locale.US)+"   REG "+empty(a.registration)+"\n"+
            "ALT "+fmtInt(a.altitudeFeet)+" ft   SPD "+fmt(a.speedKnots)+" kt   HDG "+fmt(a.track)+"°\n"+
            "RANGE "+distanceLabel(a.distanceKm)+"   BRG "+String.format(Locale.US,"%.0f°",a.bearing)+"\n"+
            empty(a.description)
        );
        aircraftReference.setText("Loading aircraft reference…");
        if(aircraftImage!=null){aircraftImage.setVisibility(View.VISIBLE);aircraftImage.setImageDrawable(new ColorDrawable(Color.argb(218,3,12,8)));}

        io.execute(() -> {
            String meta = "";
            String routeText = "";
            String wikiText = "";
            Bitmap bitmap = null;
            try {
                String url="https://api.adsbdb.com/v0/aircraft/"+a.hex;
                JSONObject root;
                boolean cached=false;
                try { root=getJson(url); }
                catch(Exception liveError) { root=readJsonCache(url,7L*24*60*60_000); cached=root!=null; if(root==null)throw liveError; }
                JSONObject response=root.optJSONObject("response");
                JSONObject m=response==null?null:response.optJSONObject("aircraft");
                if (m != null) meta =
                    (cached?"ADSBDB CACHE\n":"")+
                    nonBlank(m.optString("manufacturer"))+" "+nonBlank(m.optString("type"))+
                    "\nOwner: "+nonBlank(m.optString("registered_owner"))+
                    "\nCountry: "+nonBlank(m.optString("registered_owner_country_name"));
            } catch (Exception ignored) {}

            try {
                String clean = a.callsign == null ? "" : a.callsign.trim();
                if (clean.length() > 0 && !clean.equalsIgnoreCase(a.hex)) {
                    String encCall = URLEncoder.encode(clean, "UTF-8");
                    String routeUrl="https://api.adsbdb.com/v0/callsign/" + encCall;
                    JSONObject rr;
                    boolean routeCached=false;
                    try { rr=getJson(routeUrl); }
                    catch(Exception liveError) { rr=readJsonCache(routeUrl,3L*24*60*60_000); routeCached=rr!=null; if(rr==null)throw liveError; }
                    JSONObject response = rr.optJSONObject("response");
                    JSONObject route = response == null ? null : response.optJSONObject("flightroute");
                    if (route == null && response != null) route = response.optJSONObject("flightRoute");
                    if (route != null) {
                        JSONObject origin = route.optJSONObject("origin");
                        JSONObject dest = route.optJSONObject("destination");
                        JSONObject airline = route.optJSONObject("airline");
                        routeText =
                            (routeCached?"ADSBDB ROUTE CACHE\n":"")+
                            "Flight: " + nonBlank(route.optString("callsign").length() > 0 ? route.optString("callsign") : clean) + "\n" +
                            "Airline: " + (airline == null ? "--" : nonBlank(airline.optString("name"))) + "\n" +
                            "Origin: " + airportLabel(origin) + "\n" +
                            "Destination: " + airportLabel(dest);
                    }
                }
            } catch (Exception ignored) {}

            try {
                String article = AircraftArticles.article(a.type);
                if (article == null && a.type != null && a.type.trim().length() > 0)
                    article = a.type.trim() + " aircraft";
                if (article != null) {
                    String enc = URLEncoder.encode(article.replace(" ","_"), "UTF-8").replace("+","%20");
                    JSONObject w = getJson("https://en.wikipedia.org/api/rest_v1/page/summary/"+enc);
                    String returnedTitle=w.optString("title","");
                    String extract=w.optString("extract","");
                    if (!"disambiguation".equalsIgnoreCase(w.optString("type")) &&
                        returnedTitle.length()>0 && extract.length()>0) {
                        wikiText = extract;
                        JSONObject thumb = w.optJSONObject("thumbnail");
                        if (thumb != null) {
                            String source=thumb.optString("source");
                            if(source.startsWith("https://upload.wikimedia.org/"))
                                bitmap = getBitmap(source);
                        }
                    }
                }
            } catch (Exception ignored) {}

            if (bitmap == null) {
                try {
                    String article = AircraftArticles.article(a.type);
                    if (article != null) {
                        String enc=URLEncoder.encode(article,"UTF-8");
                        String endpoint="https://en.wikipedia.org/w/api.php?action=query&format=json&formatversion=2"+
                            "&generator=images&titles="+enc+
                            "&gimlimit=40&prop=imageinfo&iiprop=url&iiurlwidth=900&redirects=1&origin=*";
                        JSONObject q=getJson(endpoint);
                        JSONArray pages=q.optJSONObject("query")==null?null:q.optJSONObject("query").optJSONArray("pages");
                        if(pages!=null){
                            for(int i=0;i<pages.length() && bitmap==null;i++){
                                JSONObject pg=pages.optJSONObject(i);
                                String title=pg==null?"":pg.optString("title").toLowerCase(Locale.US);
                                if(title.contains("logo")||title.contains("flag")||title.contains("badge")||
                                   title.contains("diagram")||title.contains("cockpit")||title.contains("interior")||
                                   title.contains("wingtip")) continue;
                                JSONObject info=pg.optJSONArray("imageinfo")==null?null:pg.optJSONArray("imageinfo").optJSONObject(0);
                                if(info==null)continue;
                                String source=info.optString("thumburl",info.optString("url"));
                                if(source.startsWith("https://upload.wikimedia.org/")) bitmap=getBitmap(source);
                            }
                        }
                    }
                } catch (Exception ignored) {}
            }

            final String reference = (meta.trim()+"\n\n"+routeText.trim()+"\n\n"+wikiText.trim()).trim();
            final Bitmap finalBitmap = bitmap;
            ui.post(() -> {
                if (a.hex.equals(selectedHex)) {
                    aircraftReference.setText(reference.length()==0 ?
                        "Aircraft-type reference unavailable." : reference);
                    if(aircraftImage!=null){ if (finalBitmap != null) aircraftImage.setImageBitmap(finalBitmap); else aircraftImage.setImageBitmap(createAircraftPlaceholder(a)); }
                }
            });
        });
    }


    private GradientDrawable cellBackground(int color){GradientDrawable g=new GradientDrawable();g.setColor(Color.argb(80,Color.red(PANEL),Color.green(PANEL),Color.blue(PANEL)));g.setStroke(dp(1),Color.argb(150,Color.red(color),Color.green(color),Color.blue(color)));return g;}

    private void loadLaunchImage(final LegacyLaunch l){
        if(launchImageFull==null||l==null)return;
        launchImageFull.setVisibility(View.VISIBLE);
        launchImageFull.setImageDrawable(new ColorDrawable(Color.argb(210,3,12,8)));

        io.execute(()->{
            Bitmap bm=null;
            String direct=(l.imageUrl!=null&&l.imageUrl.startsWith("https://"))?l.imageUrl:
                    ((l.infographicUrl!=null&&l.infographicUrl.startsWith("https://"))?l.infographicUrl:null);

            if(direct!=null){
                try{bm=getBitmap(direct);}catch(Exception ignored){}
            }

            // Some Launch Library entries provide no usable image, or the remote image
            // host rejects old Android TLS/user-agent requests. Use NASA Images as a
            // second source rather than making double-tap appear broken.
            if(bm==null){
                try{
                    String q=l.name==null?"space launch":l.name;
                    JSONObject nasa=getJson("https://images-api.nasa.gov/search?q="+URLEncoder.encode(q,"UTF-8")+"&media_type=image");
                    JSONObject col=nasa.optJSONObject("collection");
                    JSONArray items=col==null?null:col.optJSONArray("items");
                    if(items!=null&&items.length()>0){
                        JSONArray links=items.optJSONObject(0).optJSONArray("links");
                        if(links!=null&&links.length()>0){
                            String u=links.optJSONObject(0).optString("href","");
                            if(u.startsWith("https://"))bm=getBitmap(u);
                        }
                    }
                }catch(Exception ignored){}
            }

            final Bitmap out=bm;
            ui.post(()->{
                // Ignore a late network result if the user selected another mission.
                if(launchImageFull==null||selectedLaunchFull!=l)return;
                if(out!=null){
                    launchImageFull.setImageBitmap(out);
                    launchImageFull.setVisibility(View.VISIBLE);
                }else{
                    launchImageFull.setImageDrawable(new ColorDrawable(Color.argb(210,3,12,8)));
                    launchImageFull.setVisibility(View.VISIBLE);
                    if(launchDetailFull!=null)launchDetailFull.append("\n\nIMAGE // unavailable for this mission");
                }
            });
        });
    }

    static class AstroTarget {final String name,type;final double azimuth,altitude,magnitude;final int drawable;AstroTarget(String n,String t,double a,double e,double m,int d){name=n;type=t;azimuth=a;altitude=e;magnitude=m;drawable=d;}}
    static class SmallBodyTarget {final String name,type;final double azimuth,altitude;SmallBodyTarget(String n,String t,double a,double e){name=n;type=t;azimuth=a;altitude=e;}}
    static class SatelliteTarget {final String name;final double azimuth,altitude,rangeKm;SatelliteTarget(String n,double a,double e,double r){name=n;azimuth=a;altitude=e;rangeKm=r;}}

    private double norm360(double v){v%=360.0;return v<0?v+360.0:v;}
    private double[] planetHorizontalApprox(String name,long millis,double obsLat,double obsLon){
        try{double d=millis/86400000.0-10957.5;double[] e=planetElements(name,d),earth=planetElements("EARTH",d);if(e==null||earth==null)return null;double[] p=helioFromElements(e),ep=helioFromElements(earth);double x=p[0]-ep[0],y=p[1]-ep[1],z=p[2]-ep[2];double ob=Math.toRadians(23.4393-3.563E-7*d);double xe=x,ye=y*Math.cos(ob)-z*Math.sin(ob),ze=y*Math.sin(ob)+z*Math.cos(ob);double ra=Math.toDegrees(Math.atan2(ye,xe));if(ra<0)ra+=360;ra/=15.0;double dec=Math.toDegrees(Math.atan2(ze,Math.sqrt(xe*xe+ye*ye)));return horizontalCoordinates(millis,obsLat,obsLon,ra,dec);}catch(Exception ex){return null;}}
    private double[] planetElements(String n,double d){
        if("MERCURY".equals(n))return new double[]{48.3313+3.24587E-5*d,7.0047+5.00E-8*d,29.1241+1.01444E-5*d,.387098,.205635+5.59E-10*d,168.6562+4.0923344368*d};
        if("VENUS".equals(n))return new double[]{76.6799+2.46590E-5*d,3.3946+2.75E-8*d,54.8910+1.38374E-5*d,.723330,.006773-1.302E-9*d,48.0052+1.6021302244*d};
        if("EARTH".equals(n))return new double[]{0,0,282.9404+4.70935E-5*d,1.0,.016709-1.151E-9*d,356.0470+.9856002585*d};
        if("MARS".equals(n))return new double[]{49.5574+2.11081E-5*d,1.8497-1.78E-8*d,286.5016+2.92961E-5*d,1.523688,.093405+2.516E-9*d,18.6021+.5240207766*d};
        if("JUPITER".equals(n))return new double[]{100.4542+2.76854E-5*d,1.3030-1.557E-7*d,273.8777+1.64505E-5*d,5.20256,.048498+4.469E-9*d,19.8950+.0830853001*d};
        if("SATURN".equals(n))return new double[]{113.6634+2.38980E-5*d,2.4886-1.081E-7*d,339.3939+2.97661E-5*d,9.55475,.055546-9.499E-9*d,316.9670+.0334442282*d};
        if("URANUS".equals(n))return new double[]{74.0005+1.3978E-5*d,.7733+1.9E-8*d,96.6612+3.0565E-5*d,19.18171,.047318+7.45E-9*d,142.5905+.011725806*d};
        if("NEPTUNE".equals(n))return new double[]{131.7806+3.0173E-5*d,1.7700-2.55E-7*d,272.8461-6.027E-6*d,30.05826,.008606+2.15E-9*d,260.2471+.005995147*d};return null;}
    private double[] helioFromElements(double[] e){double N=Math.toRadians(norm360(e[0])),i=Math.toRadians(e[1]),w=Math.toRadians(norm360(e[2])),a=e[3],ecc=e[4],M=Math.toRadians(norm360(e[5]));double E=M+ecc*Math.sin(M)*(1+ecc*Math.cos(M));for(int k=0;k<6;k++)E-=(E-ecc*Math.sin(E)-M)/(1-ecc*Math.cos(E));double xv=a*(Math.cos(E)-ecc),yv=a*Math.sqrt(1-ecc*ecc)*Math.sin(E),v=Math.atan2(yv,xv),r=Math.sqrt(xv*xv+yv*yv),vw=v+w;return new double[]{r*(Math.cos(N)*Math.cos(vw)-Math.sin(N)*Math.sin(vw)*Math.cos(i)),r*(Math.sin(N)*Math.cos(vw)+Math.cos(N)*Math.sin(vw)*Math.cos(i)),r*Math.sin(vw)*Math.sin(i)};}

    private void refreshLocalSky(){
        final long now=System.currentTimeMillis();final double lat=currentLat(),lon=currentLon();
        final ArrayList<AstroTarget> out=new ArrayList<AstroTarget>();
        final ArrayList<AstroTarget> below=new ArrayList<AstroTarget>();
        String[][] stars={{"SIRIUS","STAR","6.7525","-16.7161","-1.46"},{"CANOPUS","STAR","6.3992","-52.6957","-.74"},{"ARCTURUS","STAR","14.2610","19.1825","-.05"},{"VEGA","STAR","18.6156","38.7837",".03"},{"CAPELLA","STAR","5.2782","45.9980",".08"},{"RIGEL","STAR","5.2423","-8.2016",".13"},{"PROCYON","STAR","7.6550","5.2250",".34"},{"BETELGEUSE","STAR","5.9195","7.4071",".42"},{"ALTAIR","STAR","19.8464","8.8683",".76"},{"DENEB","STAR","20.6905","45.2803","1.25"},{"POLARIS","STAR","2.5303","89.2641","1.98"},{"ANTARES","STAR","16.49","-26.432",".96"},{"SPICA","STAR","13.42","-11.161",".97"},{"FOMALHAUT","STAR","22.961","-29.622","1.16"}};
        for(String[] st:stars){double[] aa=horizontalCoordinates(now,lat,lon,Double.parseDouble(st[2]),Double.parseDouble(st[3]));if(aa[1]>=-5)out.add(new AstroTarget(st[0],st[1],aa[0],aa[1],Double.parseDouble(st[4]),R.drawable.sky_star));}
        double[] sun=sunHorizontal(now,lat,lon);AstroTarget sunT=new AstroTarget("SUN","SUN",sun[0],sun[1],-26.7,R.drawable.sky_sun);if(sun[1]>=-5)out.add(sunT);else below.add(sunT);
        double[] moon=moonHorizontalApprox(now,lat,lon);AstroTarget moonT=new AstroTarget("MOON","MOON",moon[0],moon[1],-12,R.drawable.moon_reference);if(moon[1]>=-5)out.add(moonT);else below.add(moonT);
        String[] planets={"MERCURY","VENUS","MARS","JUPITER","SATURN","URANUS","NEPTUNE"};
        int[] icons={R.drawable.sky_mercury,R.drawable.sky_venus,R.drawable.sky_mars,R.drawable.sky_jupiter,R.drawable.sky_saturn,R.drawable.sky_star,R.drawable.sky_star};
        for(int i=0;i<planets.length;i++){double[] aa=planetHorizontalApprox(planets[i],now,lat,lon);if(aa!=null){AstroTarget t=new AstroTarget(planets[i],"PLANET",aa[0],aa[1],i==1?-4.2:(i==3?-2.2:1.0),icons[i]);if(aa[1]>=-5)out.add(t);else below.add(t);}}
        skyCelestial.clear();skyCelestial.addAll(out);skyBelowHorizon.clear();skyBelowHorizon.addAll(below);if(skyPanoramaView!=null)skyPanoramaView.setCelestial(out);updateSkyBriefing(false);
    }

    private Aircraft findAircraft(String hex){if(hex==null)return null;for(Aircraft a:aircraft)if(hex.equalsIgnoreCase(a.hex))return a;return null;}

    private void showFlight(){
        clearPage();
        LinearLayout page=new LinearLayout(this);page.setOrientation(LinearLayout.HORIZONTAL);page.setPadding(dp(8),dp(4),dp(8),dp(4));page.setWeightSum(10f);
        LinearLayout mapCard=card("FLIGHT ROUTE // WORLD");
        flightMapView=new FlightMapView(this);flightMapView.setHome(currentLat(),currentLon());flightMapView.setWorldTraffic(worldAircraft,worldAircraftSource);
        mapCard.addView(flightMapView,new LinearLayout.LayoutParams(-1,0,1f));
        flightViewStatus=text("WORLD VIEW  //  "+worldAircraft.size()+" LIVE AIRCRAFT  //  "+worldAircraftSource,11,CYAN);flightViewStatus.setGravity(Gravity.CENTER_VERTICAL);mapCard.addView(flightViewStatus,new LinearLayout.LayoutParams(-1,dp(30)));
        String[] camLabels={"FOLLOW TARGET","REMAINING PATH","FULL ROUTE","WORLD"};
        LinearLayout cam1=new LinearLayout(this);cam1.setOrientation(LinearLayout.HORIZONTAL);
        for(int i=0;i<4;i++){final int mode=i;Button b=button(camLabels[i]);b.setOnClickListener(v->{if(flightMapView!=null){if(mode==0)flightMapView.viewFollow();else if(mode==1)flightMapView.viewPath();else if(mode==2)flightMapView.viewRoute();else{flightMapView.viewWorld();refreshWorldTraffic(false);}}if(flightViewStatus!=null){if(mode==3)flightViewStatus.setText("WORLD VIEW  //  "+worldAircraft.size()+" LIVE AIRCRAFT  //  "+worldAircraftSource);else flightViewStatus.setText(camLabels[mode]+" VIEW");}});cam1.addView(b,new LinearLayout.LayoutParams(0,dp(33),1f));}
        mapCard.addView(cam1);
        LinearLayout zoomRow=new LinearLayout(this);zoomRow.setOrientation(LinearLayout.HORIZONTAL);Button zm=button("ZOOM −");zm.setOnClickListener(v->{if(flightMapView!=null)flightMapView.zoomBy(.5f);});Button zp=button("ZOOM +");zp.setOnClickListener(v->{if(flightMapView!=null)flightMapView.zoomBy(2f);});zoomRow.addView(zm,new LinearLayout.LayoutParams(0,dp(32),1f));zoomRow.addView(zp,new LinearLayout.LayoutParams(0,dp(32),1f));mapCard.addView(zoomRow);
        page.addView(mapCard,new LinearLayout.LayoutParams(0,-1,6f));

        LinearLayout side=new LinearLayout(this);side.setOrientation(LinearLayout.VERTICAL);side.setPadding(dp(6),0,0,0);
        LinearLayout tracker=card("FLIGHT TRACKER // CALLSIGN");
        LinearLayout row=new LinearLayout(this);row.setOrientation(LinearLayout.HORIZONTAL);
        flightInput=field("","e.g. BAW123");row.addView(flightInput,new LinearLayout.LayoutParams(0,dp(42),1f));Button track=button("TRACK");track.setOnClickListener(v->trackFlight());row.addView(track,new LinearLayout.LayoutParams(dp(80),dp(42)));tracker.addView(row);
        flightResult=text("Enter a callsign. Live contacts can also be loaded from the radar.",12,TEXT);flightResult.setBackground(panelBackground(false));tracker.addView(flightResult,new LinearLayout.LayoutParams(-1,0,1f));
        LinearLayout actions=new LinearLayout(this);actions.setOrientation(LinearLayout.HORIZONTAL);
        Button follow=button("FOLLOW TARGET");follow.setOnClickListener(v->{Aircraft a=findAircraft(selectedHex);if(a==null){String cs=flightInput.getText().toString().trim();for(Aircraft x:aircraft)if(cs.equalsIgnoreCase(x.callsign)){a=x;break;}}if(a!=null){selectedHex=a.hex;flightFollowHex=a.hex;flightResult.setText("FOLLOWING LIVE AIRCRAFT\n\n"+aircraftText(a));if(flightMapView!=null){flightMapView.setLiveAircraft(a,true);flightMapView.viewFollow();}scheduleFlightFollow();updateFlightJourney(a);}});actions.addView(follow,new LinearLayout.LayoutParams(0,dp(32),1f));
        Button world=button("RETURN TO WORLD");world.setOnClickListener(v->{flightFollowHex=null;if(flightFollowTick!=null)ui.removeCallbacks(flightFollowTick);if(flightMapView!=null)flightMapView.viewWorld();refreshWorldTraffic(false);if(flightViewStatus!=null&&!worldAircraft.isEmpty())flightViewStatus.setText("WORLD VIEW  //  "+worldAircraft.size()+" LIVE AIRCRAFT  //  "+worldAircraftSource);});actions.addView(world,new LinearLayout.LayoutParams(0,dp(32),1f));
        Button sky=button("SHOW IN SKY");sky.setOnClickListener(v->{Aircraft a=findAircraft(selectedHex);if(a==null){String cs=flightInput.getText().toString().trim();for(Aircraft x:aircraft)if(cs.equalsIgnoreCase(x.callsign)){a=x;break;}}if(a!=null){selectedHex=a.hex;pendingSkyFocus=true;prefs.edit().putInt("skyOrientation",(int)Math.round(a.bearing)).apply();}openPage("SKY VIEW");});actions.addView(sky,new LinearLayout.LayoutParams(0,dp(32),1f));tracker.addView(actions);
        flightJourney=text("JOURNEY ESTIMATE  //  WAITING FOR A CURRENT POSITION AND ROUTE",10,DIM);flightJourney.setGravity(Gravity.CENTER_VERTICAL);flightJourney.setBackground(panelBackground(false));tracker.addView(flightJourney,new LinearLayout.LayoutParams(-1,dp(64)));
        side.addView(tracker,new LinearLayout.LayoutParams(-1,0,3.25f));
        LinearLayout live=card("LIVE CONTACT PICKER");flightLiveBox=new LinearLayout(this);flightLiveBox.setOrientation(LinearLayout.VERTICAL);ScrollView liveScroll=new ScrollView(this);liveScroll.addView(flightLiveBox);live.addView(liveScroll,new LinearLayout.LayoutParams(-1,0,1f));side.addView(live,new LinearLayout.LayoutParams(-1,0,1.55f));
        page.addView(side,new LinearLayout.LayoutParams(0,-1,4f));pageHost.addView(page);
        renderFlightContacts();
        refreshWorldTraffic(false);
        if(pendingFlightCallsign!=null&&pendingFlightCallsign.length()>0){flightInput.setText(pendingFlightCallsign);String pc=pendingFlightCallsign;pendingFlightCallsign=null;trackFlight();if(pendingFlightFocus){for(Aircraft a:aircraft)if(pc.equalsIgnoreCase(a.callsign)){selectedHex=a.hex;if(flightMapView!=null)flightMapView.setLiveAircraft(a,true);updateFlightJourney(a);break;}pendingFlightFocus=false;}}
    }

    private void renderFlightContacts(){
        if(flightLiveBox==null)return;flightLiveBox.removeAllViews();int n=0;
        for(final Aircraft a:aircraft){if(n++>=14)break;TextView row=text(String.format(Locale.US,"ACFT %-10s  %7s  %8s",a.callsign,distanceLabel(a.distanceKm),a.onGround?"GROUND":fmtInt(a.altitudeFeet)+" ft"),12,a.hex.equals(selectedHex)?AMBER:TEXT);row.setGravity(Gravity.CENTER_VERTICAL);row.setBackgroundColor(a.hex.equals(selectedHex)?Color.argb(90,Color.red(GREEN),Color.green(GREEN),Color.blue(GREEN)):Color.TRANSPARENT);row.setOnClickListener(v->{selectedHex=a.hex;flightInput.setText(a.callsign);flightResult.setText("LIVE AIRCRAFT\n\n"+aircraftText(a)+"\n\nRoute lookup in progress…");if(flightMapView!=null){flightMapView.setHome(currentLat(),currentLon());flightMapView.setLiveAircraft(a,true);}updateFlightJourney(a);renderFlightContacts();trackFlight();});row.setOnLongClickListener(v->{flightInput.setText(a.callsign);trackFlight();return true;});flightLiveBox.addView(row,new LinearLayout.LayoutParams(-1,dp(30)));}
        if(flightLiveBox.getChildCount()==0)flightLiveBox.addView(text("Waiting for live radar contacts.",12,DIM));
    }

    private void trackFlight(){
        final String cs=flightInput==null?"":flightInput.getText().toString().trim().toUpperCase(Locale.US);
        if(cs.length()==0)return;
        Aircraft match=null; for(Aircraft a:aircraft)if(cs.equalsIgnoreCase(a.callsign)){match=a;break;}
        final Aircraft liveMatch=match;
        if(liveMatch!=null){selectedHex=liveMatch.hex;flightResult.setText("LIVE AIRCRAFT\n\n"+aircraftText(liveMatch)+"\n\nRoute lookup in progress…");if(flightMapView!=null){flightMapView.setLiveAircraft(liveMatch,true);flightMapView.setHome(currentLat(),currentLon());}updateFlightJourney(liveMatch);}else flightResult.setText(cs+"\n\nNo current radar contact. Route lookup in progress…");
        io.execute(()->{try{
            String routeUrl="https://api.adsbdb.com/v0/callsign/"+URLEncoder.encode(cs,"UTF-8");JSONObject root;
            String note="";try{root=getJson(routeUrl);}catch(Exception live){root=readJsonCache(routeUrl,7L*24*60*60_000L);if(root!=null)note="CACHED ROUTE\n";else throw live;}
            JSONObject response=root.optJSONObject("response");JSONObject route=response==null?null:response.optJSONObject("flightroute");if(route==null&&response!=null)route=response.optJSONObject("flightRoute");if(route==null)throw new IOException("No route returned");
            JSONObject o=route.optJSONObject("origin"),d=route.optJSONObject("destination"),airline=route.optJSONObject("airline");final String txt=note+"CALLSIGN: "+cs+"\nAIRLINE: "+(airline==null?"--":nonBlank(airline.optString("name")))+"\nORIGIN: "+airportLabel(o)+"\nDESTINATION: "+airportLabel(d);final double[] oc=airportCoords(o),dc=airportCoords(d);
            ui.post(()->{String live=liveMatch==null?"":("LIVE POSITION\n"+aircraftText(liveMatch)+"\n\n");flightResult.setText(live+txt);flightOriginCoords=oc;flightDestCoords=dc;if(oc!=null&&dc!=null&&flightMapView!=null)flightMapView.setRoute(oc[0],oc[1],dc[0],dc[1]);Aircraft current=findAircraft(selectedHex);if(current!=null)updateFlightJourney(current);});
        }catch(Exception e){final String err=shortError(e);ui.post(()->{if(liveMatch!=null){flightResult.setText("LIVE AIRCRAFT\n\n"+aircraftText(liveMatch)+"\n\nROUTE UNAVAILABLE\nPosition tracking remains live.\n"+err);if(flightMapView!=null)flightMapView.setLiveAircraft(liveMatch,false);}else flightResult.setText(cs+"\n\nROUTE UNAVAILABLE\nNo live aircraft position is currently available.\n"+err);});}});
    }

    private void scheduleFlightFollow(){if(flightFollowTick!=null)ui.removeCallbacks(flightFollowTick);flightFollowTick=new Runnable(){public void run(){if(flightFollowHex==null||!"FLIGHT".equals(currentPage))return;io.execute(()->{Aircraft found=null;String[] urls={"https://api.adsb.lol/v2/hex/"+flightFollowHex,"https://api.airplanes.live/v2/hex/"+flightFollowHex};for(String u:urls){try{JSONObject root=getJson(u);JSONArray arr=root.optJSONArray("ac");if(arr!=null&&arr.length()>0){JSONObject o=arr.optJSONObject(0);if(o!=null&&o.has("lat")&&o.has("lon")){found=Aircraft.from(o,currentLat(),currentLon());break;}}}catch(Exception ignored){}}if(found==null)found=findAircraft(flightFollowHex);if(found!=null){final Aircraft a=found;ui.post(()->{if(flightMapView!=null)flightMapView.setLiveAircraft(a,true);if(flightResult!=null)flightResult.setText("FOLLOWING LIVE AIRCRAFT\n\n"+aircraftText(a));updateFlightJourney(a);});}});ui.postDelayed(this,10_000L);}};ui.postDelayed(flightFollowTick,10_000L);}

    private void updateFlightJourney(Aircraft a){
        if(flightJourney==null)return;
        if(a==null||flightOriginCoords==null||flightDestCoords==null){flightJourney.setText("JOURNEY ESTIMATE  //  WAITING FOR A CURRENT POSITION AND USABLE ROUTE");return;}
        double olat=flightOriginCoords[0],olon=flightOriginCoords[1],dlat=flightDestCoords[0],dlon=flightDestCoords[1];
        double[] totalB=Aircraft.distanceBearing(olat,olon,dlat,dlon);double total=totalB[0],routeBearing=totalB[1];if(total<8||total>20050){flightJourney.setText("JOURNEY ESTIMATE  //  ROUTE GEOMETRY UNAVAILABLE");return;}
        double[] travB=Aircraft.distanceBearing(olat,olon,a.lat,a.lon);double travelled=travB[0],positionBearing=travB[1];double angular=travelled/6371.0088;double delta=Math.toRadians(positionBearing-routeBearing);double along=Math.atan2(Math.sin(angular)*Math.cos(delta),Math.cos(angular))*6371.0088;double cross=Math.abs(Math.asin(Math.max(-1,Math.min(1,Math.sin(angular)*Math.sin(delta))))*6371.0088);double[] remB=Aircraft.distanceBearing(a.lat,a.lon,dlat,dlon);double remaining=remB[0],destBearing=remB[1];double margin=Math.max(35,Math.min(240,total*.08));String conf=cross<=Math.max(18,Math.min(90,total*.025))?"GOOD":(cross<=margin?"FAIR":"LOW");if(cross>Math.max(100,Math.min(480,total*.18))&&remaining>35&&travelled>35)conf="UNRELIABLE";if(a.track!=null&&Math.abs((((a.track-destBearing)+540)%360)-180)>105&&remaining>35){if("GOOD".equals(conf))conf="FAIR";else if("FAIR".equals(conf))conf="LOW";}
        double ratio=travelled/Math.max(.001,travelled+remaining)*100,alongPct=Math.max(0,Math.min(100,along/total*100)),pct=Math.max(0,Math.min(100,ratio*.65+alongPct*.35));String pctText="UNRELIABLE".equals(conf)?"--":String.format(Locale.US,"%.0f%%",pct);
        flightJourney.setText("JOURNEY ESTIMATE  //  "+pctText+"  •  "+conf+" CONFIDENCE\nTO DEST "+distanceLabel(remaining)+"   FROM ORIGIN "+distanceLabel(travelled)+"   ROUTE "+distanceLabel(total)+"   OFF ROUTE "+distanceLabel(cross));
    }

    private String aircraftText(Aircraft a){return "CALLSIGN: "+a.callsign+"\nICAO: "+a.hex.toUpperCase(Locale.US)+"\nTYPE: "+empty(a.type)+"\nALT: "+fmtInt(a.altitudeFeet)+" ft\nSPD: "+fmt(a.speedKnots)+" kt\nBRG: "+String.format(Locale.US,"%.0f°",a.bearing)+"\nDIST: "+distanceLabel(a.distanceKm);}
    private double[] airportCoords(JSONObject a){if(a==null)return null;try{double la=a.has("latitude")?a.optDouble("latitude"):a.optDouble("lat");double lo=a.has("longitude")?a.optDouble("longitude"):a.optDouble("lon");if(la==0&&lo==0)return null;return new double[]{la,lo};}catch(Exception e){return null;}}

    private void showSpace(){
        clearPage();LinearLayout page=new LinearLayout(this);page.setOrientation(LinearLayout.HORIZONTAL);page.setPadding(dp(8),dp(4),dp(8),dp(4));page.setWeightSum(10f);
        LinearLayout left=new LinearLayout(this);left.setOrientation(LinearLayout.VERTICAL);
        LinearLayout top=new LinearLayout(this);top.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout moon=card("LUNAR PHASE");
        LunarInstrumentView lunarInstrument=new LunarInstrumentView(this);
        moon.addView(lunarInstrument,new LinearLayout.LayoutParams(-1,0,1f));

        LinearLayout lunarScheduleBox=new LinearLayout(this);
        lunarScheduleBox.setOrientation(LinearLayout.VERTICAL);
        lunarScheduleBox.setBackground(panelBackground(false));
        TextView lunarScheduleHead=text("UPCOMING PRIMARY PHASES",9,CYAN);
        lunarScheduleHead.setGravity(Gravity.CENTER);
        lunarScheduleHead.setTypeface(Typeface.MONOSPACE,Typeface.BOLD);
        lunarScheduleBox.addView(lunarScheduleHead,new LinearLayout.LayoutParams(-1,dp(24)));

        TextView lunarScheduleRows=text(lunarPrimaryPhaseRows(),10,TEXT);
        lunarScheduleRows.setTypeface(Typeface.MONOSPACE,Typeface.BOLD);
        lunarScheduleRows.setPadding(dp(10),dp(3),dp(10),dp(6));
        ScrollView lunarScheduleScroll=new ScrollView(this);
        lunarScheduleScroll.setFillViewport(false);
        lunarScheduleScroll.setVerticalScrollBarEnabled(true);
        lunarScheduleScroll.addView(lunarScheduleRows,new ScrollView.LayoutParams(-1,-2));
        lunarScheduleBox.addView(lunarScheduleScroll,new LinearLayout.LayoutParams(-1,0,1f));
        moon.addView(lunarScheduleBox,new LinearLayout.LayoutParams(-1,dp(104)));
        top.addView(moon,new LinearLayout.LayoutParams(0,-1,1f));
        LinearLayout iss=card("ISS TELEMETRY");IssView iv=new IssView(this);loadIssReferenceImage(iv);iss.addView(iv,new LinearLayout.LayoutParams(-1,0,1.18f));issTelemetry=text("ISS // INTERNATIONAL SPACE STATION\n\nLoading live telemetry…",13,TEXT);issTelemetry.setTypeface(Typeface.MONOSPACE,Typeface.BOLD);issTelemetry.setGravity(Gravity.CENTER);iss.addView(issTelemetry,new LinearLayout.LayoutParams(-1,dp(116)));top.addView(iss,new LinearLayout.LayoutParams(0,-1,1f));
        left.addView(top,new LinearLayout.LayoutParams(-1,0,1f));
        LinearLayout map=card("ISS // SUN // MOON GROUND TRACK");spaceMapView=new WorldMapView(this);spaceMapView.setSpaceMode(true);spaceMapView.setHome(currentLat(),currentLon());double[] ss=sunSubpoint(System.currentTimeMillis()),mm=moonSubpointApprox(System.currentTimeMillis());spaceMapView.setSunMoon(ss[0],ss[1],mm[0],mm[1]);map.addView(spaceMapView,new LinearLayout.LayoutParams(-1,0,1f));left.addView(map,new LinearLayout.LayoutParams(-1,0,1.08f));
        page.addView(left,new LinearLayout.LayoutParams(0,-1,6f));
        LinearLayout solar=card("PLANETARY ALIGNMENT // HELIOCENTRIC OVERVIEW");solarSystemView=new SolarSystemView(this);solar.addView(solarSystemView,new LinearLayout.LayoutParams(-1,0,1f));page.addView(solar,new LinearLayout.LayoutParams(0,-1,4f));pageHost.addView(page);refreshIss();
    }

    private void loadIssReferenceImage(final IssView target){
        final String url="https://assets.science.nasa.gov/dynamicimage/assets/science/astro/universe/2023/09/SpaceStation-1.png?fit=clip&w=1400";
        io.execute(()->{
            Bitmap bm=null;
            try{bm=getBitmap(url);}catch(Exception ignored){}
            final Bitmap out=bm;
            if(out!=null)ui.post(()->{if(target!=null)target.setReferenceImage(out);});
        });
    }

    private String lunarSummary(){
        long syn=(long)(29.530588853*86400000L);long ref=947182440000L;double age=((System.currentTimeMillis()-ref)%syn)/(double)86400000L;if(age<0)age+=29.5306;double illum=(1-Math.cos(2*Math.PI*age/29.5306))*50;String phase=age<1.8?"NEW MOON":age<7.4?"WAXING CRESCENT":age<9.2?"FIRST QUARTER":age<14.8?"WAXING GIBBOUS":age<16.6?"FULL MOON":age<22.1?"WANING GIBBOUS":age<23.9?"LAST QUARTER":"WANING CRESCENT";return phase+"\n"+String.format(Locale.US,"%.0f%% ILLUMINATED  •  AGE %.1f DAYS",illum,age)+(age<14.8?"\n→ WAXING":"\n← WANING");
    }


    private String lunarRiseSetSummary(){
        // Lightweight local moonrise/moonset estimator for the saved location.
        // Samples lunar altitude through the local civil day and linearly interpolates
        // the horizon crossings. Good enough for the instrument display and works
        // offline on the legacy Fire tablet.
        try{
            final double lat=currentLat(), lon=currentLon();
            Calendar cal=Calendar.getInstance();
            cal.set(Calendar.HOUR_OF_DAY,0);cal.set(Calendar.MINUTE,0);cal.set(Calendar.SECOND,0);cal.set(Calendar.MILLISECOND,0);
            long dayStart=cal.getTimeInMillis();
            Long rise=null,set=null;
            double prevAlt=moonHorizontalApprox(dayStart,lat,lon)[1];
            long prevT=dayStart;
            for(int m=10;m<=24*60;m+=10){
                long t=dayStart+m*60_000L;
                double alt=moonHorizontalApprox(t,lat,lon)[1];
                if(prevAlt<0 && alt>=0 && rise==null){
                    double f=(0-prevAlt)/(alt-prevAlt);
                    rise=prevT+(long)((t-prevT)*Math.max(0,Math.min(1,f)));
                }
                if(prevAlt>=0 && alt<0 && set==null){
                    double f=(0-prevAlt)/(alt-prevAlt);
                    set=prevT+(long)((t-prevT)*Math.max(0,Math.min(1,f)));
                }
                prevAlt=alt;prevT=t;
            }
            SimpleDateFormat fmt=new SimpleDateFormat("HH:mm",Locale.UK);
            String rs=rise==null?"--:--":fmt.format(new Date(rise));
            String ss=set==null?"--:--":fmt.format(new Date(set));
            return "MOONRISE "+rs+"   •   MOONSET "+ss;
        }catch(Exception e){
            return "MOONRISE --:--   •   MOONSET --:--";
        }
    }

    private String lunarPrimaryPhaseRows(){
        final double syn=29.53058867;
        final long ref=947182440000L;
        long now=System.currentTimeMillis();
        double cycle=(now-ref)/(syn*86400000.0);
        long base=(long)Math.floor(cycle);
        double[] f={0,.25,.5,.75,1.0};
        String[] n={"NEW MOON","FIRST QUARTER","FULL MOON","LAST QUARTER","NEW MOON"};
        SimpleDateFormat df=new SimpleDateFormat("EEE dd MMM  HH:mm",Locale.UK);
        StringBuilder out=new StringBuilder();
        int added=0;
        for(int k=0;k<3&&added<4;k++){
            long cycleBase=base+k;
            for(int i=0;i<f.length&&added<4;i++){
                long t=ref+(long)((cycleBase+f[i])*syn*86400000.0);
                if(t>now+60_000){
                    if(added>0)out.append("\n");
                    out.append(String.format(Locale.US,"%-14s",n[i]))
                       .append("  ").append(df.format(new Date(t)));
                    added++;
                }
            }
        }
        return out.toString();
    }

    private String lunarPhaseSchedule(){
        final double syn=29.53058867;final long ref=947182440000L;long now=System.currentTimeMillis();double cycle=(now-ref)/(syn*86400000.0);long base=(long)Math.floor(cycle);double[] f={0,.25,.5,.75,1.0};String[] n={"NEW MOON","FIRST QUARTER","FULL MOON","LAST QUARTER","NEW MOON"};SimpleDateFormat df=new SimpleDateFormat("EEE d MMM  HH:mm",Locale.UK);StringBuilder out=new StringBuilder("UPCOMING LUNAR PHASES");int added=0;for(int k=0;k<3&&added<4;k++){long cycleBase=base+k;for(int i=0;i<f.length&&added<4;i++){long t=ref+(long)((cycleBase+f[i])*syn*86400000.0);if(t>now+60_000){out.append("\n").append(String.format(Locale.US,"%-14s",n[i])).append("  ").append(df.format(new Date(t)));added++;}}}return out.toString();
    }

    private void refreshIss(){io.execute(()->{try{String url="https://api.wheretheiss.at/v1/satellites/25544";JSONObject o;boolean cached=false;try{o=getJson(url);}catch(Exception live){o=readJsonCache(url,30*60_000L);cached=o!=null;if(o==null)throw live;}final double lat=o.optDouble("latitude"),lon=o.optDouble("longitude"),alt=o.optDouble("altitude"),vel=o.optDouble("velocity");final boolean wasCached=cached;ui.post(()->{if(issTelemetry!=null)issTelemetry.setText((wasCached?"CACHED  //  ":"LIVE  //  ")+String.format(Locale.US,"LAT %+.3f°   LON %+.3f°\nALT %.0f mi   VEL %.0f mph\nFROM HOME %.0f mi   BRG %.0f°",lat,lon,alt,vel,Aircraft.distanceBearing(currentLat(),currentLon(),lat,lon)[0]*0.621371,Aircraft.distanceBearing(currentLat(),currentLon(),lat,lon)[1]));if(spaceMapView!=null)spaceMapView.setIss(lat,lon);});}catch(Exception e){ui.post(()->{if(issTelemetry!=null)issTelemetry.setText("ISS feed unavailable // no recent cache");});}});}

    private void refreshSmallBodies(){
        final double lat=currentLat(),lon=currentLon();final long now=System.currentTimeMillis();
        io.execute(()->{
            final ArrayList<SmallBodyTarget> visible=new ArrayList<SmallBodyTarget>();
            int below=0;int liveCount=0,cacheCount=0;
            // Deliberately curated: useful bright/notable asteroids and comets without hammering Horizons.
            String[][] bodies={
                {"CERES","ASTEROID","1;"},{"PALLAS","ASTEROID","2;"},{"JUNO","ASTEROID","3;"},{"VESTA","ASTEROID","4;"},
                {"EROS","ASTEROID","433;"},{"APOPHIS","ASTEROID","99942;"},{"HALLEY","COMET","1P"},{"ENCKE","COMET","2P"},{"67P","COMET","67P"}
            };
            for(String[] b:bodies){
                try{
                    String url=horizonsObserverUrl(b[2],lat,lon,now);JSONObject root=readJsonCache(url,2L*60*60_000L);boolean cached=root!=null;
                    if(root==null){root=getJson(url);liveCount++;}else cacheCount++;
                    double[] ae=parseHorizonsAzEl(root);if(ae==null)continue;
                    if(ae[1]>=-5)visible.add(new SmallBodyTarget(b[0],b[1],ae[0],ae[1]));else below++;
                }catch(Exception ignored){}
            }
            final int belowCount=below;final String source=liveCount>0?"JPL HORIZONS LIVE":(cacheCount>0?"JPL HORIZONS CACHE":"JPL HORIZONS UNAVAILABLE");
            ui.post(()->{skySmallBodies.clear();skySmallBodies.addAll(visible);skySmallBodyBelowCount=belowCount;skySmallBodySource=source;if(skyPanoramaView!=null)skyPanoramaView.setSmallBodies(visible);updateSkyBriefing(false);});
        });
    }

    private String horizonsObserverUrl(String command,double lat,double lon,long now) throws Exception {
        String jd=String.format(Locale.US,"%.8f",julianDay(now));
        String site=String.format(Locale.US,"%.6f,%.6f,0",lon,lat);
        return "https://ssd.jpl.nasa.gov/api/horizons.api?format=json"+
            "&COMMAND="+URLEncoder.encode("'"+command+"'","UTF-8")+
            "&OBJ_DATA=NO&MAKE_EPHEM=YES&EPHEM_TYPE=OBSERVER"+
            "&CENTER="+URLEncoder.encode("'coord@399'","UTF-8")+
            "&COORD_TYPE=GEODETIC&SITE_COORD="+URLEncoder.encode("'"+site+"'","UTF-8")+
            "&TLIST="+URLEncoder.encode("'"+jd+"'","UTF-8")+"&TLIST_TYPE=JD"+
            "&QUANTITIES="+URLEncoder.encode("'4'","UTF-8")+"&CSV_FORMAT=YES&ANG_FORMAT=DEG&ELEV_CUT=-90";
    }

    private double[] parseHorizonsAzEl(JSONObject root){
        try{
            if(root==null||root.has("error"))return null;String result=root.optString("result","");int a=result.indexOf("$$SOE"),b=result.indexOf("$$EOE");if(a<0||b<=a)return null;
            String body=result.substring(a+5,b).trim();String[] lines=body.split("\n");
            for(String line:lines){
                if(line.trim().length()==0)continue;String[] cols=line.split(",");ArrayList<Double> nums=new ArrayList<Double>();
                for(int i=cols.length-1;i>=0;i--){try{String x=cols[i].trim();if(x.length()==0)continue;nums.add(Double.parseDouble(x));if(nums.size()>=2)break;}catch(Exception ignored){}}
                if(nums.size()>=2){double elev=nums.get(0),az=nums.get(1);if(az>=0&&az<=360&&elev>=-90&&elev<=90)return new double[]{az,elev};}
            }
        }catch(Exception ignored){}return null;
    }
    static class SatOrbit {
        String name;double epochMs,meanMotion,ecc,inc,raan,argPerigee,meanAnomaly;
        SatOrbit(String n,double ep,double mm,double ec,double in,double ra,double ap,double ma){name=n;epochMs=ep;meanMotion=mm;ecc=ec;inc=in;raan=ra;argPerigee=ap;meanAnomaly=ma;}
    }
    private void refreshSatellites(){
        final double lat=currentLat(),lon=currentLon();final long now=System.currentTimeMillis();
        io.execute(()->{
            final ArrayList<SatelliteTarget> targets=new ArrayList<SatelliteTarget>();String source="";int below=0;
            try{
                ArrayList<SatOrbit> orbits=new ArrayList<SatOrbit>();
                String satnogs="https://db.satnogs.org/api/tle/?format=json";
                try{
                    String raw=getText(satnogs);
                    JSONArray rows=raw.trim().startsWith("[")?new JSONArray(raw):new JSONObject(raw).optJSONArray("results");
                    parseSatnogs(rows,orbits);source="SATNOGS";
                }catch(Exception primary){
                    try{
                        JSONArray a=new JSONArray(getText("https://celestrak.org/NORAD/elements/gp.php?GROUP=stations&FORMAT=JSON"));
                        JSONArray b=new JSONArray(getText("https://celestrak.org/NORAD/elements/gp.php?GROUP=visual&FORMAT=JSON"));
                        parseCelestrak(a,orbits);parseCelestrak(b,orbits);source="CELESTRAK FALLBACK";
                    }catch(Exception fallback){
                        String a=readTextCache("https://celestrak.org/NORAD/elements/gp.php?GROUP=stations&FORMAT=JSON",7L*24*60*60_000L);
                        String b=readTextCache("https://celestrak.org/NORAD/elements/gp.php?GROUP=visual&FORMAT=JSON",7L*24*60*60_000L);
                        if(a!=null)parseCelestrak(new JSONArray(a),orbits);if(b!=null)parseCelestrak(new JSONArray(b),orbits);source="CELESTRAK CACHE";
                    }
                }
                HashSet<String> seen=new HashSet<String>();
                for(SatOrbit o:orbits){
                    if(o==null||seen.contains(o.name))continue;seen.add(o.name);
                    SatelliteTarget t=satelliteLook(o,now,lat,lon);
                    if(t!=null){if(t.altitude>=-5)targets.add(t);else below++;}
                    if(targets.size()>=80)break;
                }
            }catch(Exception ignored){}
            final String src=source;final int belowCount=below;
            ui.post(()->{
                skySatellites.clear();skySatellites.addAll(targets);skySatelliteBelowCount=belowCount;skySatelliteSource=src;
                if(skyPanoramaView!=null)skyPanoramaView.setSatellites(targets);
                updateSkyBriefing(false);
            });
        });
    }
    private void parseSatnogs(JSONArray rows,List<SatOrbit> out){
        if(rows==null)return;String[] preferred={"ISS","TIANGONG","TIANHE","HUBBLE","HST","NOAA","METEOR","LANDSAT","TERRA","AQUA","SENTINEL","ENVISAT","SWOT","STARLINK","IRIDIUM","CSS","COSMOS","RESURS","ONEWEB"};
        for(int i=0;i<rows.length()&&out.size()<500;i++)try{
            JSONObject o=rows.optJSONObject(i);if(o==null)continue;String name=o.optString("tle0","").replaceFirst("^0\\s+","").trim();boolean ok=false;for(String p:preferred)if(name.toUpperCase(Locale.US).contains(p)){ok=true;break;}if(!ok)continue;
            SatOrbit x=parseTle(name,o.optString("tle1"),o.optString("tle2"));if(x!=null)out.add(x);
        }catch(Exception ignored){}
    }
    private SatOrbit parseTle(String name,String l1,String l2){
        try{
            if(l1==null||l2==null||l1.length()<63||l2.length()<63)return null;int yy=Integer.parseInt(l1.substring(18,20).trim());double day=Double.parseDouble(l1.substring(20,32).trim());int year=yy>=57?1900+yy:2000+yy;Calendar c=Calendar.getInstance(TimeZone.getTimeZone("UTC"));c.clear();c.set(year,Calendar.JANUARY,1,0,0,0);double epoch=c.getTimeInMillis()+(day-1)*86400000.0;
            double inc=Double.parseDouble(l2.substring(8,16).trim()),raan=Double.parseDouble(l2.substring(17,25).trim()),ecc=Double.parseDouble("0."+l2.substring(26,33).trim()),arg=Double.parseDouble(l2.substring(34,42).trim()),ma=Double.parseDouble(l2.substring(43,51).trim()),mm=Double.parseDouble(l2.substring(52,63).trim());
            return new SatOrbit(name,epoch,mm,ecc,inc,raan,arg,ma);
        }catch(Exception e){return null;}
    }
    private void parseCelestrak(JSONArray arr,List<SatOrbit> out){
        if(arr==null)return;for(int i=0;i<arr.length()&&out.size()<700;i++)try{
            JSONObject o=arr.optJSONObject(i);if(o==null)continue;String name=o.optString("OBJECT_NAME","SATELLITE");double epoch=isoMillis(o.optString("EPOCH",""));double mm=o.optDouble("MEAN_MOTION",Double.NaN),ec=o.optDouble("ECCENTRICITY",Double.NaN),inc=o.optDouble("INCLINATION",Double.NaN),raan=o.optDouble("RA_OF_ASC_NODE",Double.NaN),arg=o.optDouble("ARG_OF_PERICENTER",Double.NaN),ma=o.optDouble("MEAN_ANOMALY",Double.NaN);if(Double.isNaN(mm)||Double.isNaN(ec))continue;out.add(new SatOrbit(name,epoch,mm,ec,inc,raan,arg,ma));
        }catch(Exception ignored){}
    }
    private double isoMillis(String iso){
        try{SimpleDateFormat f=new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSSSS",Locale.US);f.setTimeZone(TimeZone.getTimeZone("UTC"));return f.parse(iso).getTime();}catch(Exception e){try{SimpleDateFormat f=new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss",Locale.US);f.setTimeZone(TimeZone.getTimeZone("UTC"));return f.parse(iso.substring(0,19)).getTime();}catch(Exception x){return System.currentTimeMillis();}}
    }
    private SatelliteTarget satelliteLook(SatOrbit o,long now,double obsLat,double obsLon){
        try{
            final double mu=398600.4418,earth=6378.137;double n=o.meanMotion*2*Math.PI/86400.0,a=Math.pow(mu/(n*n),1.0/3.0);double M=Math.toRadians(norm360(o.meanAnomaly+o.meanMotion*360.0*(now-o.epochMs)/86400000.0));double E=M;for(int k=0;k<8;k++)E-= (E-o.ecc*Math.sin(E)-M)/(1-o.ecc*Math.cos(E));double xv=a*(Math.cos(E)-o.ecc),yv=a*Math.sqrt(1-o.ecc*o.ecc)*Math.sin(E),v=Math.atan2(yv,xv),r=Math.sqrt(xv*xv+yv*yv),N=Math.toRadians(o.raan),inc=Math.toRadians(o.inc),w=Math.toRadians(o.argPerigee),vw=v+w;double xeci=r*(Math.cos(N)*Math.cos(vw)-Math.sin(N)*Math.sin(vw)*Math.cos(inc)),yeci=r*(Math.sin(N)*Math.cos(vw)+Math.cos(N)*Math.sin(vw)*Math.cos(inc)),zeci=r*Math.sin(vw)*Math.sin(inc);double th=Math.toRadians(gmstDegrees(now)),xe=xeci*Math.cos(th)+yeci*Math.sin(th),ye=-xeci*Math.sin(th)+yeci*Math.cos(th),ze=zeci;double lat=Math.toRadians(obsLat),lon=Math.toRadians(obsLon);double ox=earth*Math.cos(lat)*Math.cos(lon),oy=earth*Math.cos(lat)*Math.sin(lon),oz=earth*Math.sin(lat);double dx=xe-ox,dy=ye-oy,dz=ze-oz;double east=-Math.sin(lon)*dx+Math.cos(lon)*dy,north=-Math.sin(lat)*Math.cos(lon)*dx-Math.sin(lat)*Math.sin(lon)*dy+Math.cos(lat)*dz,up=Math.cos(lat)*Math.cos(lon)*dx+Math.cos(lat)*Math.sin(lon)*dy+Math.sin(lat)*dz;double range=Math.sqrt(east*east+north*north+up*up),az=norm360(Math.toDegrees(Math.atan2(east,north))),alt=Math.toDegrees(Math.asin(up/range));return new SatelliteTarget(o.name,az,alt,range);
        }catch(Exception e){return null;}
    }

    private void showSkyView(){
        clearPage();skyShowSatellites=prefs.getBoolean("skySatellites",true);skyShowSmallBodies=prefs.getBoolean("skySmallBodies",true);final boolean showAircraft=prefs.getBoolean("skyAircraft",true);
        skyDetailsExpanded=false;selectedSkyObjectName=null;
        LinearLayout page=new LinearLayout(this);page.setOrientation(LinearLayout.HORIZONTAL);page.setPadding(dp(8),dp(4),dp(8),dp(4));page.setWeightSum(10f);
        LinearLayout main=card("SKY VIEW   //   FACING "+skyOrientationLabel()+"   //   184° PANORAMA   //   "+locationLabel().toUpperCase(Locale.UK));
        if(main.getChildCount()>0 && main.getChildAt(0) instanceof TextView) skyViewTitle=(TextView)main.getChildAt(0);
        skyPanoramaView=new SkyPanoramaView(this);skyPanoramaView.setAircraft(aircraft);skyPanoramaView.setOrientation(prefs.getInt("skyOrientation",180));skyPanoramaView.setSelected(selectedHex);skyPanoramaView.setCelestial(skyCelestial);skyPanoramaView.setSatellites(skySatellites);skyPanoramaView.setSmallBodies(skySmallBodies);skyPanoramaView.setShowAircraft(showAircraft);skyPanoramaView.setShowSatellites(skyShowSatellites);skyPanoramaView.setShowSmallBodies(skyShowSmallBodies);
        skyPanoramaView.setOnObjectTapListener(new SkyPanoramaView.OnObjectTapListener(){
            public void onAircraft(Aircraft a){selectedHex=a.hex;skyPanoramaView.setSelected(a.hex);updateSkySelected(a);}
            public void onCelestial(String name,int drawable){selectedHex=null;skyPanoramaView.setSelected(null);updateSkyCelestial(name,drawable);}
        });
        LinearLayout viewBar=new LinearLayout(this);viewBar.setOrientation(LinearLayout.HORIZONTAL);String[] dirs={"NORTH","EAST","SOUTH","WEST"};int[] degs={0,90,180,270};
        for(int i=0;i<dirs.length;i++){final int dd=degs[i];Button b=button(dirs[i]);b.setOnClickListener(v->{prefs.edit().putInt("skyOrientation",dd).apply();if(skyPanoramaView!=null)skyPanoramaView.setOrientation(dd);updateSkyTitle();});viewBar.addView(b,new LinearLayout.LayoutParams(0,dp(32),1f));}
        Button reset=button("RESET VIEW");reset.setOnClickListener(v->{prefs.edit().putInt("skyOrientation",180).apply();if(skyPanoramaView!=null)skyPanoramaView.setOrientation(180);updateSkyTitle();});viewBar.addView(reset,new LinearLayout.LayoutParams(0,dp(32),1.15f));main.addView(viewBar,new LinearLayout.LayoutParams(-1,dp(31)));
        main.addView(skyPanoramaView,new LinearLayout.LayoutParams(-1,0,1f));
        LinearLayout key=card("LIVE SKY KEY");
        TextView keyRow1=new TextView(this);
        keyRow1.setText("✈ FIXED    ✣ HELICOPTER    △ FAST    ⌁ GLIDER/LIGHT    ✦ STAR    ☀ SUN");
        keyRow1.setTextColor(TEXT);keyRow1.setTypeface(Typeface.MONOSPACE);keyRow1.setTextSize(10f);
        keyRow1.setSingleLine(true);keyRow1.setGravity(Gravity.CENTER_VERTICAL);
        keyRow1.setPadding(dp(5),0,dp(5),0);
        key.addView(keyRow1,new LinearLayout.LayoutParams(-1,0,1f));

        TextView keyRow2=new TextView(this);
        keyRow2.setText("◐ MOON    ● PLANET    □ SATELLITE    ◆ ASTEROID    ☄ COMET");
        keyRow2.setTextColor(TEXT);keyRow2.setTypeface(Typeface.MONOSPACE);keyRow2.setTextSize(10f);
        keyRow2.setSingleLine(true);keyRow2.setGravity(Gravity.CENTER_VERTICAL);
        keyRow2.setPadding(dp(5),0,dp(5),0);
        key.addView(keyRow2,new LinearLayout.LayoutParams(-1,0,1f));

        main.addView(key,new LinearLayout.LayoutParams(-1,dp(82)));
        page.addView(main,new LinearLayout.LayoutParams(0,-1,7.15f));

        LinearLayout side=card("SELECTED SKY OBJECT");
        TextView liveTitle=text("LIVE SKY   //   CURRENT TIME",11,CYAN);liveTitle.setGravity(Gravity.CENTER);side.addView(liveTitle,new LinearLayout.LayoutParams(-1,dp(30)));
        skyLiveText=text("",11,AMBER);skyLiveText.setGravity(Gravity.CENTER_VERTICAL);side.addView(skyLiveText,new LinearLayout.LayoutParams(-1,dp(108)));
        LinearLayout ref=new LinearLayout(this);ref.setOrientation(LinearLayout.VERTICAL);ref.setBackground(panelBackground(false));
        skyReferenceImage=new ImageView(this);skyReferenceImage.setImageDrawable(null);skyReferenceImage.setAlpha(1f);skyReferenceImage.setScaleType(ImageView.ScaleType.CENTER_INSIDE);ref.addView(skyReferenceImage,new LinearLayout.LayoutParams(-1,0,1f));
        skySelectedText=text("TAP A SKY OBJECT FOR DETAILS / REFERENCE IMAGE",12,CYAN);skySelectedText.setGravity(Gravity.CENTER);ref.addView(skySelectedText,new LinearLayout.LayoutParams(-1,dp(62)));side.addView(ref,new LinearLayout.LayoutParams(-1,0,1f));

        skyDetailsButton=button("OBJECT DETAILS ▼");skyDetailsButton.setEnabled(false);skyDetailsButton.setOnClickListener(v->toggleSkyDetails());side.addView(skyDetailsButton,new LinearLayout.LayoutParams(-1,dp(36)));
        skyDetailsPanel=new LinearLayout(this);skyDetailsPanel.setOrientation(LinearLayout.VERTICAL);skyDetailsPanel.setPadding(dp(5),dp(3),dp(5),dp(4));skyDetailsPanel.setBackground(panelBackground(false));skyDetailsPanel.setVisibility(View.GONE);
        skyDetailsText=text("Select an object to inspect its specifications.",11,TEXT);ScrollView detailScroll=new ScrollView(this);detailScroll.setFillViewport(false);detailScroll.addView(skyDetailsText,new ScrollView.LayoutParams(-1,-2));skyDetailsPanel.addView(detailScroll,new LinearLayout.LayoutParams(-1,0,1f));side.addView(skyDetailsPanel,new LinearLayout.LayoutParams(-1,dp(238)));
        page.addView(side,new LinearLayout.LayoutParams(0,-1,2.85f));pageHost.addView(page);

        refreshLocalSky();if(skyShowSmallBodies)refreshSmallBodies();if(skyShowSatellites)refreshSatellites();updateSkyBriefing(false);
        Aircraft selected=findAircraft(selectedHex);if(selected!=null){if(pendingSkyFocus){int focus=(int)Math.round(selected.bearing);prefs.edit().putInt("skyOrientation",focus).apply();if(skyPanoramaView!=null)skyPanoramaView.setOrientation(focus);pendingSkyFocus=false;updateSkyTitle();}updateSkySelected(selected);}
    }


    private void updateSkyBriefing(boolean verbose){
        if(skyLiveText==null)return;StringBuilder b=new StringBuilder();
        b.append(verbose?"SKY BRIEFING  //  ":"LIVE SKY  //  ").append(aircraft.size()).append(" aircraft  •  ").append(skySatellites.size()).append(" satellites  •  ").append(skyCelestial.size()).append(" sky objects  •  ").append(skySmallBodies.size()).append(" small bodies");
        if(!skyBelowHorizon.isEmpty()){b.append("\nBELOW DISPLAY HORIZON  ");int n=Math.min(4,skyBelowHorizon.size());for(int i=0;i<n;i++){AstroTarget x=skyBelowHorizon.get(i);if(i>0)b.append("  •  ");b.append(x.name).append(" ").append(String.format(Locale.US,"%.0f°",x.altitude));}if(skyBelowHorizon.size()>n)b.append("  +").append(skyBelowHorizon.size()-n);}
        if(skySatelliteBelowCount>0||skySmallBodyBelowCount>0)b.append("\nORBITAL BELOW  ").append(skySatelliteBelowCount).append(" satellites  •  ").append(skySmallBodyBelowCount).append(" small bodies");
        Aircraft a=findAircraft(selectedHex);if(a!=null)b.append("\nSELECTED ").append(a.callsign).append("  ").append(distanceLabel(a.distanceKm)).append("  BRG ").append(String.format(Locale.US,"%.0f°",a.bearing));else if(!aircraft.isEmpty()){Aircraft n=aircraft.get(0);b.append("\nNEAREST ").append(n.callsign).append("  ").append(distanceLabel(n.distanceKm));}
        skyLiveText.setText(b.toString());
    }
    private void updateSkySelected(Aircraft a){
        if(a==null)return;selectedSkyObjectName=a.callsign;if(skyReferenceImage!=null){skyReferenceImage.setImageDrawable(null);skyReferenceImage.setAlpha(1f);}
        if(skySelectedText!=null)skySelectedText.setText(a.callsign+"  •  "+empty(a.type)+"  •  "+distanceLabel(a.distanceKm)+"  •  BRG "+String.format(Locale.US,"%.0f°",a.bearing));
        setSkyDetails("AIRCRAFT // "+a.callsign,aircraftObjectDetails(a));
        loadSkyAircraftImage(a);loadSkyAircraftReferenceDetails(a);
    }
    private void updateSkyTitle(){
        if(skyViewTitle!=null) skyViewTitle.setText("SKY VIEW   //   FACING "+skyOrientationLabel()+"   //   184° PANORAMA   //   "+locationLabel().toUpperCase(Locale.UK));
    }

    private void updateSkyCelestial(String name,int drawable){
        String upper=name==null?"":name.toUpperCase(Locale.US);selectedSkyObjectName=name;
        SatelliteTarget satTarget=findSkySatellite(name);SmallBodyTarget small=findSkySmallBody(name);AstroTarget astro=findSkyAstro(name);
        boolean sat=satTarget!=null;boolean smallBody=small!=null;boolean planet=Arrays.asList("MERCURY","VENUS","MARS","JUPITER","SATURN","URANUS","NEPTUNE").contains(upper);
        String kind=sat?"SATELLITE / ORBITAL OBJECT":(smallBody?small.type:(planet?"PLANET":("SUN".equals(upper)?"STAR":("MOON".equals(upper)?"MOON":"CELESTIAL REFERENCE"))));
        if(skySelectedText!=null)skySelectedText.setText(name+"  •  "+kind);
        setSkyDetails(kind+" // "+name,skyObjectDetails(name,astro,satTarget,small));
        if(skyReferenceImage!=null){skyReferenceImage.setAlpha(1f);skyReferenceImage.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
            if(sat){skyReferenceImage.setImageBitmap(createSatellitePlaceholder(name));loadSatelliteReferenceImage(name);}
            else if(smallBody){skyReferenceImage.setImageBitmap(createPlanetPlaceholder(upper));loadCelestialReferenceImage(name,small.type.toLowerCase(Locale.US));}
            else if(planet && (drawable==R.drawable.sky_star || drawable==0)){skyReferenceImage.setImageBitmap(createPlanetPlaceholder(upper));loadCelestialReferenceImage(name,"planet");}
            else {try{skyReferenceImage.setImageResource(drawable);}catch(Exception ignored){skyReferenceImage.setImageBitmap(createPlanetPlaceholder(upper));} if(planet)loadCelestialReferenceImage(name,"planet");}
        }
    }

    private void toggleSkyDetails(){
        if(skyDetailsPanel==null||skyDetailsButton==null)return;skyDetailsExpanded=!skyDetailsExpanded;skyDetailsPanel.setVisibility(skyDetailsExpanded?View.VISIBLE:View.GONE);skyDetailsButton.setText(skyDetailsExpanded?"OBJECT DETAILS ▲":"OBJECT DETAILS ▼");
    }
    private void setSkyDetails(String title,String body){
        if(skyDetailsButton!=null){skyDetailsButton.setEnabled(true);skyDetailsButton.setText((skyDetailsExpanded?"OBJECT DETAILS ▲  //  ":"OBJECT DETAILS ▼  //  ")+title);}
        if(skyDetailsText!=null)skyDetailsText.setText(body==null?"Reference data unavailable.":body);
    }
    private AstroTarget findSkyAstro(String name){if(name==null)return null;for(AstroTarget a:skyCelestial)if(name.equalsIgnoreCase(a.name))return a;for(AstroTarget a:skyBelowHorizon)if(name.equalsIgnoreCase(a.name))return a;return null;}
    private SatelliteTarget findSkySatellite(String name){if(name==null)return null;for(SatelliteTarget a:skySatellites)if(name.equalsIgnoreCase(a.name))return a;return null;}
    private SmallBodyTarget findSkySmallBody(String name){if(name==null)return null;for(SmallBodyTarget a:skySmallBodies)if(name.equalsIgnoreCase(a.name))return a;return null;}
    private String horizonState(double alt){return alt>=-5?"ABOVE DISPLAY HORIZON":"BELOW DISPLAY HORIZON";}
    private String skyObjectDetails(String name,AstroTarget astro,SatelliteTarget sat,SmallBodyTarget small){
        String u=name==null?"":name.toUpperCase(Locale.US);StringBuilder b=new StringBuilder();
        if(astro!=null)b.append(String.format(Locale.US,"CURRENT POSITION\nAZIMUTH       %.1f°\nALTITUDE      %+.1f°\nSTATUS        %s\n",astro.azimuth,astro.altitude,horizonState(astro.altitude)));
        if(sat!=null)b.append(String.format(Locale.US,"CURRENT POSITION\nAZIMUTH       %.1f°\nELEVATION     %+.1f°\nRANGE         %.0f km\nSTATUS        %s\n\nPOSITION SOURCE\nCached TLE + local orbital propagation\n",sat.azimuth,sat.altitude,sat.rangeKm,horizonState(sat.altitude)));
        if(small!=null)b.append(String.format(Locale.US,"CURRENT EPHEMERIS\nTYPE          %s\nAZIMUTH       %.1f°\nALTITUDE      %+.1f°\nSTATUS        %s\n\nPOSITION SOURCE\nNASA/JPL Horizons ephemeris\nCached locally for 2 hours\n",small.type,small.azimuth,small.altitude,horizonState(small.altitude)));
        String ref=staticSkyReference(u);if(ref.length()>0){if(b.length()>0)b.append("\n");b.append("REFERENCE SPECIFICATIONS\n").append(ref);}
        if(b.length()==0)b.append("No detailed local reference is available for this object yet.");return b.toString();
    }
    private String staticSkyReference(String u){
        if("SUN".equals(u))return "CLASS         G2V star\nDIAMETER      1,392,700 km\nMASS          1.989 × 10^30 kg\nSURFACE GRAV  274 m/s²\nAGE           ~4.6 billion years\nEARTH DIST    ~149.6 million km";
        if("MOON".equals(u))return "DIAMETER      3,474.8 km\nMASS          7.342 × 10^22 kg\nSURFACE GRAV  1.62 m/s²\nORBIT PERIOD  27.32 days\nMEAN DIST     384,400 km\n\n"+lunarSummary();
        if("MERCURY".equals(u))return "DIAMETER      4,879 km\nMASS          3.301 × 10^23 kg\nGRAVITY       3.70 m/s²\nORBIT PERIOD  88.0 days\nMOONS         0";
        if("VENUS".equals(u))return "DIAMETER      12,104 km\nMASS          4.867 × 10^24 kg\nGRAVITY       8.87 m/s²\nORBIT PERIOD  224.7 days\nMOONS         0";
        if("MARS".equals(u))return "DIAMETER      6,779 km\nMASS          6.417 × 10^23 kg\nGRAVITY       3.71 m/s²\nORBIT PERIOD  687.0 days\nMOONS         2";
        if("JUPITER".equals(u))return "DIAMETER      139,820 km\nMASS          1.898 × 10^27 kg\nGRAVITY       24.79 m/s²\nORBIT PERIOD  11.86 years\nTYPE          Gas giant";
        if("SATURN".equals(u))return "DIAMETER      116,460 km\nMASS          5.683 × 10^26 kg\nGRAVITY       10.44 m/s²\nORBIT PERIOD  29.45 years\nTYPE          Gas giant";
        if("URANUS".equals(u))return "DIAMETER      50,724 km\nMASS          8.681 × 10^25 kg\nGRAVITY       8.69 m/s²\nORBIT PERIOD  84.0 years\nTYPE          Ice giant";
        if("NEPTUNE".equals(u))return "DIAMETER      49,244 km\nMASS          1.024 × 10^26 kg\nGRAVITY       11.15 m/s²\nORBIT PERIOD  164.8 years\nTYPE          Ice giant";
        if("SIRIUS".equals(u))return "CONSTELLATION Canis Major\nSPECTRAL TYPE A1V\nMAGNITUDE     −1.46\nDISTANCE      8.6 light-years";
        if("CANOPUS".equals(u))return "CONSTELLATION Carina\nSPECTRAL TYPE A9 II\nMAGNITUDE     −0.74\nDISTANCE      ~310 light-years";
        if("ARCTURUS".equals(u))return "CONSTELLATION Boötes\nSPECTRAL TYPE K1.5 III\nMAGNITUDE     −0.05\nDISTANCE      36.7 light-years";
        if("VEGA".equals(u))return "CONSTELLATION Lyra\nSPECTRAL TYPE A0V\nMAGNITUDE     +0.03\nDISTANCE      25.0 light-years";
        if("CAPELLA".equals(u))return "CONSTELLATION Auriga\nSYSTEM        Multiple star system\nMAGNITUDE     +0.08\nDISTANCE      42.9 light-years";
        if("RIGEL".equals(u))return "CONSTELLATION Orion\nSPECTRAL TYPE B8 Ia\nMAGNITUDE     +0.13\nDISTANCE      ~860 light-years";
        if("PROCYON".equals(u))return "CONSTELLATION Canis Minor\nSPECTRAL TYPE F5 IV-V\nMAGNITUDE     +0.34\nDISTANCE      11.5 light-years";
        if("BETELGEUSE".equals(u))return "CONSTELLATION Orion\nTYPE          Red supergiant\nMAGNITUDE     Variable\nDISTANCE      ~640 light-years";
        if("ALTAIR".equals(u))return "CONSTELLATION Aquila\nSPECTRAL TYPE A7V\nMAGNITUDE     +0.76\nDISTANCE      16.7 light-years";
        if("DENEB".equals(u))return "CONSTELLATION Cygnus\nTYPE          Blue-white supergiant\nMAGNITUDE     +1.25\nDISTANCE      ~2,600 light-years";
        if("POLARIS".equals(u))return "CONSTELLATION Ursa Minor\nTYPE          Cepheid / multiple system\nMAGNITUDE     ~+1.98\nDISTANCE      ~448 light-years";
        if("ANTARES".equals(u))return "CONSTELLATION Scorpius\nTYPE          Red supergiant\nMAGNITUDE     ~+0.96\nDISTANCE      ~550 light-years";
        if("SPICA".equals(u))return "CONSTELLATION Virgo\nTYPE          Binary star system\nMAGNITUDE     ~+0.97\nDISTANCE      ~250 light-years";
        if("FOMALHAUT".equals(u))return "CONSTELLATION Piscis Austrinus\nSPECTRAL TYPE A3V\nMAGNITUDE     +1.16\nDISTANCE      25.1 light-years";
        return "";
    }
    private String aircraftObjectDetails(Aircraft a){
        StringBuilder b=new StringBuilder();b.append("LIVE TELEMETRY\n");b.append("CALLSIGN      ").append(a.callsign).append("\nICAO          ").append(a.hex==null?"--":a.hex.toUpperCase(Locale.US)).append("\nTYPE          ").append(empty(a.type)).append("\nSTATE         ").append(a.onGround?"GROUND":"AIRBORNE").append("\nALTITUDE      ").append(fmtInt(a.altitudeFeet)).append(" ft\nSPEED         ").append(fmt(a.speedKnots)).append(" kt\nTRACK         ").append(fmt(a.track)).append("°\nVERT RATE     ").append(fmtInt(a.verticalRate)).append(" ft/min\nRANGE         ").append(distanceLabel(a.distanceKm)).append("\nBEARING       ").append(String.format(Locale.US,"%.0f°",a.bearing)).append("\nMIL FLAG      ").append(a.military?"YES":"NO").append("\n\nREFERENCE\nAircraft reference data is cached locally for up to 7 days.");return b.toString();
    }
    private void loadSkyAircraftReferenceDetails(final Aircraft a){
        if(a==null||a.hex==null)return;final String objectName=a.callsign;io.execute(()->{String extra="";try{String url="https://api.adsbdb.com/v0/aircraft/"+a.hex;JSONObject root=readJsonCache(url,7L*24*60*60_000L);boolean cached=root!=null;if(root==null)root=getJson(url);JSONObject response=root.optJSONObject("response"),m=response==null?null:response.optJSONObject("aircraft");if(m!=null)extra="\n\nAIRCRAFT REFERENCE"+(cached?" // CACHE":"")+"\nMANUFACTURER  "+nonBlank(m.optString("manufacturer"))+"\nMODEL / TYPE  "+nonBlank(m.optString("type"))+"\nREGISTRATION  "+nonBlank(m.optString("registration"))+"\nOWNER         "+nonBlank(m.optString("registered_owner"))+"\nCOUNTRY       "+nonBlank(m.optString("registered_owner_country_name"));}catch(Exception ignored){}final String tail=extra;ui.post(()->{if(selectedSkyObjectName!=null&&selectedSkyObjectName.equals(objectName)&&skyDetailsText!=null&&tail.length()>0)skyDetailsText.append(tail);});});
    }

    private void loadSatelliteReferenceImage(final String satelliteName){if(skyReferenceImage==null||satelliteName==null)return;io.execute(()->{Bitmap bm=null;try{String q=URLEncoder.encode(satelliteName+" satellite","UTF-8");JSONObject root=getJson("https://en.wikipedia.org/w/api.php?action=query&generator=search&gsrsearch="+q+"&gsrlimit=1&prop=pageimages&piprop=thumbnail&pithumbsize=900&format=json");JSONObject query=root.optJSONObject("query"),pages=query==null?null:query.optJSONObject("pages");if(pages!=null){java.util.Iterator<String> keys=pages.keys();if(keys.hasNext()){JSONObject page=pages.optJSONObject(keys.next());JSONObject th=page==null?null:page.optJSONObject("thumbnail");if(th!=null){String u=th.optString("source");if(u.startsWith("https://"))bm=getBitmap(u);}}}}catch(Exception ignored){}if(bm==null)try{JSONObject nasa=getJson("https://images-api.nasa.gov/search?q="+URLEncoder.encode(satelliteName+" satellite","UTF-8")+"&media_type=image");JSONObject col=nasa.optJSONObject("collection");JSONArray items=col==null?null:col.optJSONArray("items");if(items!=null&&items.length()>0){JSONArray links=items.optJSONObject(0).optJSONArray("links");if(links!=null&&links.length()>0){String u=links.optJSONObject(0).optString("href");if(u.startsWith("https://"))bm=getBitmap(u);}}}catch(Exception ignored){}final Bitmap out=bm;ui.post(()->{if(skyReferenceImage!=null&&out!=null){skyReferenceImage.setScaleType(ImageView.ScaleType.CENTER_INSIDE);skyReferenceImage.setImageBitmap(out);}});});}

    private void loadSkyAircraftImage(final Aircraft a){if(skyReferenceImage==null||a==null)return;io.execute(()->{Bitmap bm=null;try{String article=AircraftArticles.article(a.type);if(article!=null){JSONObject w=getJson("https://en.wikipedia.org/api/rest_v1/page/summary/"+URLEncoder.encode(article.replace(" ","_"),"UTF-8"));JSONObject th=w.optJSONObject("thumbnail");if(th!=null)bm=getBitmap(th.optString("source"));}}catch(Exception ignored){}final Bitmap out=bm;ui.post(()->{if(skyReferenceImage!=null&&out!=null){skyReferenceImage.setAlpha(1f);skyReferenceImage.setImageBitmap(out);}});});}

    private void showLaunchesPage(){
        clearPage();LinearLayout page=new LinearLayout(this);page.setOrientation(LinearLayout.HORIZONTAL);page.setPadding(dp(8),dp(4),dp(8),dp(4));page.setWeightSum(10f);
        LinearLayout list=card("UPCOMING CREWED / SPACE LAUNCHES");launchesListBox=new LinearLayout(this);launchesListBox.setOrientation(LinearLayout.VERTICAL);ScrollView sc=new ScrollView(this);sc.addView(launchesListBox);list.addView(sc,new LinearLayout.LayoutParams(-1,0,1f));page.addView(list,new LinearLayout.LayoutParams(0,-1,4f));
        LinearLayout right=new LinearLayout(this);right.setOrientation(LinearLayout.VERTICAL);right.setPadding(dp(7),0,0,0);
        LinearLayout detail=card("SELECTED MISSION");launchCountdownFull=text("T− --:--:--",28,CYAN);launchCountdownFull.setGravity(Gravity.CENTER);launchCountdownFull.setTypeface(Typeface.MONOSPACE,Typeface.BOLD);detail.addView(launchCountdownFull,new LinearLayout.LayoutParams(-1,dp(62)));launchImageFull=new ImageView(this);launchImageFull.setScaleType(ImageView.ScaleType.CENTER_CROP);launchImageFull.setVisibility(View.GONE);launchImageFull.setBackgroundColor(Color.argb(180,3,12,8));detail.addView(launchImageFull,new LinearLayout.LayoutParams(-1,dp(116)));launchDetailFull=text("Launch schedule loading…",13,TEXT);ScrollView launchDetailScroll=new ScrollView(this);launchDetailScroll.addView(launchDetailFull,new ScrollView.LayoutParams(-1,-2));detail.addView(launchDetailScroll,new LinearLayout.LayoutParams(-1,0,1f));right.addView(detail,new LinearLayout.LayoutParams(-1,0,1.08f));
        LinearLayout map=card("LAUNCH SITE // WORLD");launchMapView=new WorldMapView(this);map.addView(launchMapView,new LinearLayout.LayoutParams(-1,0,1f));right.addView(map,new LinearLayout.LayoutParams(-1,0,.92f));page.addView(right,new LinearLayout.LayoutParams(0,-1,6f));pageHost.addView(page);refreshLaunchesFull();
    }

    private void refreshLaunchesFull(){
        io.execute(()->{
            final ArrayList<LegacyLaunch> list=new ArrayList<LegacyLaunch>();
            String note="";
            try{
                String primary="https://ll.thespacedevs.com/2.3.0/launches/upcoming/?limit=12&ordering=net";
                JSONObject root=null;
                try{ root=getJson(primary); }
                catch(Exception live){ root=readJsonCache(primary,7L*24*60*60_000L); if(root!=null)note="CACHED // "; }
                if(root!=null) parseSpaceDevsLaunches(root,list);
                if(list.isEmpty()){
                    String fallback="https://fdo.rocketlaunch.live/json/launches/next/5";
                    JSONObject rr=null;
                    try{rr=getJson(fallback);}catch(Exception e){rr=readJsonCache(fallback,7L*24*60*60_000L);}
                    if(rr!=null){parseRocketLaunchLive(rr,list);note="FALLBACK // ";}
                }
                Collections.sort(list,(a,b)->Long.compare(a.netMillis,b.netMillis));
                final String sourceNote=note;
                ui.post(()->{renderLaunchesFull(list);if(launchDetailFull!=null&&!sourceNote.isEmpty()&&!list.isEmpty())launchDetailFull.append("\n\n"+sourceNote+"launch data");});
            }catch(Exception e){
                final String er=shortError(e);ui.post(()->{if(launchDetailFull!=null)launchDetailFull.setText("Launch feeds unavailable.\n\n"+er);});
            }
        });
    }

    private void parseSpaceDevsLaunches(JSONObject root,List<LegacyLaunch> list){
        JSONArray arr=root.optJSONArray("results");if(arr==null)return;
        for(int i=0;i<arr.length();i++){JSONObject item=arr.optJSONObject(i);if(item==null)continue;JSONObject pad=item.optJSONObject("pad"),loc=pad==null?null:pad.optJSONObject("location");LegacyLaunch l=new LegacyLaunch(item.optString("name","Unnamed mission"),parseIsoMillis(item.optString("net")),item.optJSONObject("status")==null?"Scheduled":item.optJSONObject("status").optString("name"),item.optJSONObject("mission")==null?"":item.optJSONObject("mission").optString("description"),pad==null?"":pad.optString("name"),loc==null?"":loc.optString("name"));l.imageUrl=launchImageUrl(item);l.infographicUrl=launchInfographicUrl(item);if(pad!=null){l.lat=pad.optDouble("latitude",Double.NaN);l.lon=pad.optDouble("longitude",Double.NaN);}if(l.netMillis>0)list.add(l);}
    }

    private String imageUrlFromValue(Object v){
        if(v==null||v==JSONObject.NULL)return "";
        if(v instanceof String){String u=((String)v).trim();return u.startsWith("https://")?u:"";}
        if(v instanceof JSONObject){
            JSONObject o=(JSONObject)v;
            String[] keys={"image_url","url","image","thumbnail","source"};
            for(String k:keys){String u=o.optString(k,"").trim();if(u.startsWith("https://"))return u;}
        }
        return "";
    }

    private String launchImageUrl(JSONObject item){
        String u=imageUrlFromValue(item.opt("image"));if(!u.isEmpty())return u;
        JSONObject mission=item.optJSONObject("mission");
        if(mission!=null){u=imageUrlFromValue(mission.opt("image"));if(!u.isEmpty())return u;}
        JSONObject rocket=item.optJSONObject("rocket");
        if(rocket!=null){
            JSONObject cfg=rocket.optJSONObject("configuration");
            if(cfg!=null){u=imageUrlFromValue(cfg.opt("image"));if(!u.isEmpty())return u;}
        }
        return "";
    }

    private String launchInfographicUrl(JSONObject item){
        String u=imageUrlFromValue(item.opt("infographic"));if(!u.isEmpty())return u;
        JSONObject mission=item.optJSONObject("mission");
        if(mission!=null){u=imageUrlFromValue(mission.opt("infographic"));if(!u.isEmpty())return u;}
        return "";
    }

    private void parseRocketLaunchLive(JSONObject root,List<LegacyLaunch> list){
        JSONArray arr=root.optJSONArray("result");if(arr==null)return;
        for(int i=0;i<arr.length();i++){JSONObject item=arr.optJSONObject(i);if(item==null)continue;JSONObject pad=item.optJSONObject("pad"),loc=pad==null?null:pad.optJSONObject("location");String iso=item.optString("t0",item.optString("win_open",""));long net=parseIsoMillis(iso);if(net<=0){try{net=Long.parseLong(item.optString("sort_date","0"))*1000L;}catch(Exception ignored){}}String mission=item.optString("mission_description",item.optString("launch_description",""));LegacyLaunch l=new LegacyLaunch(item.optString("name","Unnamed mission"),net,"Scheduled",mission,pad==null?"":pad.optString("name"),loc==null?"":loc.optString("name"));if(net>0)list.add(l);}
    }

    private void renderLaunchesFull(List<LegacyLaunch> list){
        if(launchesListBox==null)return;
        launchesListBox.removeAllViews();
        ArrayList<LegacyLaunch> upcoming=new ArrayList<LegacyLaunch>();
        long now=System.currentTimeMillis();
        for(LegacyLaunch x:list)if(x.netMillis>=now)upcoming.add(x);

        for(int i=0;i<upcoming.size();i++){
            final LegacyLaunch l=upcoming.get(i);
            final TextView b=text(new SimpleDateFormat("dd MMM HH:mm",Locale.UK).format(new Date(l.netMillis))+" UTC   //   "+l.name,11,TEXT);
            b.setGravity(Gravity.LEFT|Gravity.CENTER_VERTICAL);
            b.setPadding(dp(7),dp(1),dp(7),dp(1));
            b.setTag(l);
            final GestureDetector detector=new GestureDetector(this,new GestureDetector.SimpleOnGestureListener(){
                @Override public boolean onDown(MotionEvent e){return true;}
                @Override public boolean onSingleTapConfirmed(MotionEvent e){selectLaunchFull(l);return true;}
                @Override public boolean onDoubleTap(MotionEvent e){
                    selectLaunchFull(l);
                    loadLaunchImage(l);
                    return true;
                }
            });
            b.setOnTouchListener((v,e)->detector.onTouchEvent(e));
            launchesListBox.addView(b,new LinearLayout.LayoutParams(-1,dp(25)));
        }
        if(!upcoming.isEmpty())selectLaunchFull(upcoming.get(0));
    }

    private void updateLaunchSelectionHighlight(){
        if(launchesListBox==null)return;
        for(int i=0;i<launchesListBox.getChildCount();i++){
            View v=launchesListBox.getChildAt(i);
            Object tag=v.getTag();
            boolean selected=tag==selectedLaunchFull;
            v.setBackgroundColor(selected
                    ?Color.argb(135,Color.red(GREEN),Color.green(GREEN),Color.blue(GREEN))
                    :Color.TRANSPARENT);
        }
    }

    private void selectLaunchFull(LegacyLaunch l){
        selectedLaunchFull=l;
        updateLaunchSelectionHighlight();
        if(launchImageFull!=null){
            launchImageFull.setVisibility(View.GONE);
            launchImageFull.setImageDrawable(null);
        }
        long d=l.netMillis-System.currentTimeMillis();
        if(launchCountdownFull!=null)launchCountdownFull.setText(launchCountdown(d));
        if(launchDetailFull!=null)launchDetailFull.setText(l.name+"\n\nLaunch: "+l.name+"\nNET: "+new SimpleDateFormat("yyyy-MM-dd HH:mm z",Locale.UK).format(new Date(l.netMillis))+"\nStatus: "+l.status+"\nPad: "+empty(l.pad)+"\nLocation: "+empty(l.location)+"\n\n"+empty(l.mission));
        if(launchMapView!=null&&!Double.isNaN(l.lat))launchMapView.setLaunch(l.lat,l.lon,l.pad.length()>0?l.pad:l.location);
    }


    private void scheduleWeatherRefresh(){
        if(weatherRefreshTick!=null)ui.removeCallbacks(weatherRefreshTick);
        weatherRefreshTick=new Runnable(){public void run(){
            refreshWeatherCacheInBackground();
            ui.postDelayed(this,60L*60_000L);
        }};
        weatherRefreshTick.run();
    }

    private void refreshWeatherCacheInBackground(){
        final double lat=currentLat(),lon=currentLon();
        final String openUrl="https://api.open-meteo.com/v1/forecast?latitude="+lat+"&longitude="+lon+
            "&timezone=auto&current=temperature_2m,relative_humidity_2m,apparent_temperature,precipitation,weather_code,cloud_cover,pressure_msl,wind_speed_10m,wind_direction_10m,wind_gusts_10m"+
            "&hourly=temperature_2m,relative_humidity_2m,precipitation,pressure_msl,wind_speed_10m"+
            "&daily=weather_code,temperature_2m_max,temperature_2m_min,precipitation_probability_max,sunrise,sunset,wind_speed_10m_max&past_days=1&forecast_days=5";
        io.execute(()->{
            try{
                getJson(openUrl);
                prefs.edit().putLong("weatherLastRefresh",System.currentTimeMillis()).apply();
                if(weatherStatus!=null&&"WEATHER".equals(currentPage))ui.post(()->refreshWeather(false));
            }catch(Exception ignored){}
        });
    }

    private String weatherLastUpdateLabel(){
        long t=prefs.getLong("weatherLastRefresh",0L);
        if(t<=0)return "LAST UPDATE --:--";
        SimpleDateFormat f=new SimpleDateFormat("HH:mm",Locale.UK);
        return "LAST UPDATE "+f.format(new Date(t));
    }

    private void showWeather() {
        clearPage();

        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setPadding(dp(5),dp(3),dp(5),dp(4));
        page.setBackgroundColor(Color.TRANSPARENT);

        LinearLayout top = new LinearLayout(this);
        top.setOrientation(LinearLayout.HORIZONTAL);
        top.setGravity(Gravity.CENTER_VERTICAL);
        weatherStatus = text("WEATHER // "+locationLabel()+" // "+weatherLastUpdateLabel(), 14, GREEN);
        weatherStatus.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
        Button refresh = button("REFRESH");
        refresh.setOnClickListener(v -> refreshWeather(true));
        top.addView(weatherStatus,new LinearLayout.LayoutParams(0,dp(38),1f));
        top.addView(refresh,new LinearLayout.LayoutParams(dp(88),dp(31)));
        page.addView(top);

        LinearLayout columns = new LinearLayout(this);
        columns.setOrientation(LinearLayout.HORIZONTAL);
        columns.setWeightSum(10f);

        // LEFT 6.6 / 10: hero + history instruments.
        LinearLayout left = new LinearLayout(this);
        left.setOrientation(LinearLayout.VERTICAL);

        LinearLayout hero = new LinearLayout(this);
        hero.setOrientation(LinearLayout.HORIZONTAL);
        hero.setGravity(Gravity.CENTER_VERTICAL);
        hero.setPadding(dp(12),dp(8),dp(12),dp(8));
        hero.setBackground(panelBackground(true));

        weatherCurrent = text("LOADING CURRENT CONDITIONS…", 17, CYAN);
        weatherCurrent.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
        hero.addView(weatherCurrent,new LinearLayout.LayoutParams(0,-1,1f));

        weatherHeroIconView = new WeatherIconView(this);
        hero.addView(weatherHeroIconView,new LinearLayout.LayoutParams(dp(155),-1));
        left.addView(hero,new LinearLayout.LayoutParams(-1,0,1.88f));

        temperatureTrendView = new WeatherTrendView(this,"TEMPERATURE",GREEN);
        left.addView(temperatureTrendView,new LinearLayout.LayoutParams(-1,0,.86f));

        humidityTrendView = new WeatherTrendView(this,"HUMIDITY",CYAN);
        left.addView(humidityTrendView,new LinearLayout.LayoutParams(-1,0,.64f));

        windTrendView = new WeatherTrendView(this,"WIND SPEED",GREEN);
        left.addView(windTrendView,new LinearLayout.LayoutParams(-1,0,.64f));

        pressureTrendView = new WeatherTrendView(this,"PRESSURE",CYAN);
        left.addView(pressureTrendView,new LinearLayout.LayoutParams(-1,0,.64f));

        rainTrendView = new WeatherTrendView(this,"RAINFALL",GREEN);
        left.addView(rainTrendView,new LinearLayout.LayoutParams(-1,0,.64f));

        LinearLayout.LayoutParams leftLp=new LinearLayout.LayoutParams(0,-1,6.45f);
        leftLp.setMargins(0,0,dp(6),0);
        columns.addView(left,leftLp);

        // RIGHT 3.4 / 10: city, five-day forecast, local conditions + alerts.
        LinearLayout right = new LinearLayout(this);
        right.setOrientation(LinearLayout.VERTICAL);

        TextView fiveTitle=text("FIVE DAY FORECAST",12,CYAN);
        fiveTitle.setTypeface(Typeface.MONOSPACE,Typeface.BOLD);
        fiveTitle.setBackground(panelBackground(true));
        right.addView(fiveTitle,new LinearLayout.LayoutParams(-1,dp(30)));

        fiveDayGrid=new LinearLayout(this);
        fiveDayGrid.setOrientation(LinearLayout.VERTICAL);
        fiveDayGrid.setPadding(dp(2),0,dp(2),dp(2));
        fiveDayGrid.setBackground(panelBackground(true));
        right.addView(fiveDayGrid,new LinearLayout.LayoutParams(-1,dp(188)));
        buildFiveDayHeader();
        weatherFiveDay=text("",1,TEXT);
        weatherFiveDay.setVisibility(View.GONE);

        TextView localTitle=text("LOCAL CONDITIONS",12,CYAN);
        localTitle.setTypeface(Typeface.MONOSPACE,Typeface.BOLD);
        localTitle.setBackground(panelBackground(true));
        right.addView(localTitle,new LinearLayout.LayoutParams(-1,dp(30)));

        weatherLocalView=new WeatherLocalView(this);
        weatherLocalView.setBackground(panelBackground(true));
        right.addView(weatherLocalView,new LinearLayout.LayoutParams(-1,0,1f));

        TextView warningTitle=text("WEATHER ALERTS",11,DIM);
        warningTitle.setBackground(panelBackground(false));
        right.addView(warningTitle,new LinearLayout.LayoutParams(-1,dp(26)));

        weatherWarnings=text("Checking official warning feeds…",10,TEXT);
        weatherWarnings.setMaxLines(3);
        weatherWarnings.setBackground(panelBackground(true));
        weatherWarnings.setPadding(dp(8),dp(5),dp(8),dp(5));
        right.addView(weatherWarnings,new LinearLayout.LayoutParams(-1,dp(46)));

        columns.addView(right,new LinearLayout.LayoutParams(0,-1,3.55f));

        page.addView(columns,new LinearLayout.LayoutParams(-1,0,1f));

        weatherBody=text("",10,DIM);
        weatherBody.setVisibility(View.GONE);
        page.addView(weatherBody,new LinearLayout.LayoutParams(1,1));

        pageHost.addView(page);
        refreshWeather(false);
    }

    private void styleWeatherHero(WeatherDisplay d) {
        if(weatherCurrent==null||d==null)return;
        String wind=String.format(Locale.US,"%.0f %s",speedDisplay(d.windSpeed),speedUnit());
        String gust=Double.isNaN(d.gust)?"--":String.format(Locale.US,"%.0f %s",speedDisplay(d.gust),speedUnit());
        String pressure=Double.isNaN(d.pressure)?"--":String.format(Locale.US,"%.0f hPa",d.pressure);
        String raw=d.condition+"\n"+temperatureLabel(d.temperature)+
            "\nFEELS LIKE "+temperatureLabel(d.feels)+"   •   HUMIDITY "+String.format(Locale.US,"%.0f%%",d.humidity)+
            "\nWIND "+wind+"   •   GUST "+gust+"   •   PRESS "+pressure+
            "\nSUNRISE "+nonBlank(d.sunrise)+"   •   SUNSET "+nonBlank(d.sunset);
        android.text.SpannableString ss=new android.text.SpannableString(raw);
        int first=raw.indexOf('\n');
        int second=first<0?-1:raw.indexOf('\n',first+1);
        if(first>0){
            ss.setSpan(new android.text.style.RelativeSizeSpan(1.10f),0,first,android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            ss.setSpan(new android.text.style.ForegroundColorSpan(CYAN),0,first,android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            ss.setSpan(new android.text.style.StyleSpan(Typeface.BOLD),0,first,android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
        if(first>=0&&second>first){
            ss.setSpan(new android.text.style.RelativeSizeSpan(2.55f),first+1,second,android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            ss.setSpan(new android.text.style.StyleSpan(Typeface.BOLD),first+1,second,android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            ss.setSpan(new android.text.style.ForegroundColorSpan(CYAN),first+1,second,android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
        if(second>=0){
            ss.setSpan(new android.text.style.RelativeSizeSpan(.82f),second+1,raw.length(),android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            ss.setSpan(new android.text.style.ForegroundColorSpan(TEXT),second+1,raw.length(),android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
        weatherCurrent.setText(ss);
    }

    private void refreshWeather(){refreshWeather(true);}

    private void refreshWeather(boolean force) {
        if(weatherStatus!=null)weatherStatus.setText("WEATHER // "+locationLabel()+" // "+(force?"UPDATING":"CACHE CHECK")+" // "+weatherLastUpdateLabel());
        final double lat=currentLat(),lon=currentLon();
        final long now=System.currentTimeMillis();
        final boolean cacheFresh=!force && now-prefs.getLong("weatherLastRefresh",0L)<60L*60_000L;
        final String openUrl="https://api.open-meteo.com/v1/forecast?latitude="+lat+"&longitude="+lon+
            "&timezone=auto&current=temperature_2m,relative_humidity_2m,apparent_temperature,precipitation,weather_code,cloud_cover,pressure_msl,wind_speed_10m,wind_direction_10m,wind_gusts_10m"+
            "&hourly=temperature_2m,relative_humidity_2m,precipitation,pressure_msl,wind_speed_10m"+
            "&daily=weather_code,temperature_2m_max,temperature_2m_min,precipitation_probability_max,sunrise,sunset,wind_speed_10m_max&past_days=1&forecast_days=5";
        io.execute(()->{try{
            JSONObject root=null;String source="OPEN-METEO";WeatherDisplay parsed=null;
            try{
                if(cacheFresh){
                    root=readJsonCache(openUrl,60L*60_000L);
                    if(root!=null)source="HOURLY CACHE";
                }
                if(root==null){root=getJson(openUrl);prefs.edit().putLong("weatherLastRefresh",System.currentTimeMillis()).apply();}
                parsed=parseOpenMeteo(root);
            }catch(Exception e){
                try{
                    String met="https://api.met.no/weatherapi/locationforecast/2.0/compact?lat="+lat+"&lon="+lon;JSONObject mr;
                    try{mr=cacheFresh?readJsonCache(met,60L*60_000L):null;if(mr==null)mr=getJson(met);}
                    catch(Exception me){mr=readJsonCache(met,6*60*60_000L);if(mr==null)throw me;source="MET NORWAY CACHE";}
                    if(!source.contains("CACHE"))source="MET NORWAY FALLBACK";
                    parsed=parseMetForecast(mr.getJSONObject("properties").getJSONArray("timeseries"));
                    prefs.edit().putLong("weatherLastRefresh",System.currentTimeMillis()).apply();
                }catch(Exception me){
                    root=readJsonCache(openUrl,6*60*60_000L);if(root==null)throw e;source="OPEN-METEO CACHE";parsed=parseOpenMeteo(root);
                }
            }
            final WeatherDisplay display=parsed;final String sourceFinal=source;WeatherHistoryData hist=null;String warnings="No active weather alerts returned.";try{hist=loadWeatherHistory(lat,lon);}catch(Exception ignored){}try{warnings=loadWeatherWarnings(lat,lon);}catch(Exception ignored){}final WeatherHistoryData h=hist;final String warn=warnings;
            ui.post(()->{weatherLastSuccessfulUpdate=prefs.getLong("weatherLastRefresh",System.currentTimeMillis());
                if(weatherStatus!=null)weatherStatus.setText("WEATHER // "+locationLabel()+" // "+sourceFinal+" // "+weatherLastUpdateLabel());styleWeatherHero(display);renderFiveDay(display.days);if(weatherLocalView!=null)weatherLocalView.setWeather(display);if(weatherHeroIconView!=null)weatherHeroIconView.setCondition(display.condition);if(h!=null){temperatureTrendView.setSeries(h.temperature,display.tempUnit);humidityTrendView.setSeries(h.humidity,"%");windTrendView.setSeries(h.wind,prefs.getBoolean("miles",true)?"mph":"km/h");pressureTrendView.setSeries(display.pressureHistory,"hPa");rainTrendView.setSeries(h.rain,"mm");}if(weatherWarnings!=null)weatherWarnings.setText(warn);});
        }catch(Exception e){final String er=shortError(e);ui.post(()->{if(weatherStatus!=null)weatherStatus.setText("WEATHER OFFLINE");if(weatherCurrent!=null)weatherCurrent.setText("Weather data unavailable\n"+er);});}});
    }

    private WeatherDisplay parseMetForecast(JSONArray ts) throws Exception {
        boolean f=prefs.getBoolean("fahrenheit",false);
        JSONObject first=ts.getJSONObject(0);
        JSONObject firstData=first.getJSONObject("data");
        JSONObject d=firstData.getJSONObject("instant").getJSONObject("details");
        JSONObject next=firstData.optJSONObject("next_1_hours");
        String symbol=next!=null&&next.optJSONObject("summary")!=null?next.optJSONObject("summary").optString("symbol_code"):"";
        double temp=d.optDouble("air_temperature");
        double wind=d.optDouble("wind_speed");
        double hum=d.optDouble("relative_humidity");
        double pressure=d.optDouble("air_pressure_at_sea_level",Double.NaN);
        double direction=d.optDouble("wind_from_direction",Double.NaN);
        double cloud=d.optDouble("cloud_area_fraction",Double.NaN);
        String current=symbol.replace("_"," ").toUpperCase(Locale.US)+"\n"+temperatureLabel(temp)+"\nFEELS LIKE "+temperatureLabel(temp);

        StringBuilder sb=new StringBuilder();
        int count=Math.min(12,ts.length());
        for(int i=0;i<count;i++){
            JSONObject p=ts.getJSONObject(i);
            JSONObject pd=p.getJSONObject("data");
            JSONObject details=pd.getJSONObject("instant").getJSONObject("details");
            JSONObject n=pd.optJSONObject("next_1_hours");
            String code=n!=null&&n.optJSONObject("summary")!=null?n.optJSONObject("summary").optString("symbol_code"):"";
            sb.append(String.format(Locale.US,"%-5s  %-2s  %7s   RH %3.0f%%   WIND %4.1f m/s\n",
                shortIso(p.optString("time")),weatherGlyph(code),temperatureLabel(details.optDouble("air_temperature")),
                details.optDouble("relative_humidity"),details.optDouble("wind_speed")));
        }
        String fiveDay=buildFiveDayFromMet(ts);
        String icon=weatherGlyph(symbol);
        String local=
            icon+"  "+symbol.replace("_"," ").toUpperCase(Locale.US)+"     "+temperatureLabel(temp)+"\n"+
            "HUMIDITY   "+String.format(Locale.US,"%.0f%%",hum)+"     CLOUD   "+(Double.isNaN(cloud)?"--":String.format(Locale.US,"%.0f%%",cloud))+"\n"+
            "WIND       "+String.format(Locale.US,"%.0f m/s",wind)+"     PRESSURE "+(Double.isNaN(pressure)?"--":String.format(Locale.US,"%.0f hPa",pressure));
        double[] pHist=new double[Math.min(12,ts.length())];
        for(int i=0;i<pHist.length;i++) pHist[i]=ts.getJSONObject(i).getJSONObject("data").getJSONObject("instant").getJSONObject("details").optDouble("air_pressure_at_sea_level",Double.NaN);
        return new WeatherDisplay(current,sb.toString(),fiveDay,direction,wind,hum,cloud,local,icon,prefs.getBoolean("fahrenheit",false)?"°F":"°C",pHist,buildForecastDaysFromMet(ts),symbol.replace("_"," ").toUpperCase(Locale.US),temp,temp,Double.NaN,Double.NaN,pressure,"--","--");
    }

    private WeatherDisplay parseOpenMeteo(JSONObject root) throws Exception {
        JSONObject cur=root.getJSONObject("current");double temp=cur.optDouble("temperature_2m"),feels=cur.optDouble("apparent_temperature",temp),hum=cur.optDouble("relative_humidity_2m"),wind=cur.optDouble("wind_speed_10m"),direction=cur.optDouble("wind_direction_10m",Double.NaN),gust=cur.optDouble("wind_gusts_10m",Double.NaN),cloud=cur.optDouble("cloud_cover",Double.NaN),precip=cur.optDouble("precipitation",0),pressure=cur.optDouble("pressure_msl",Double.NaN);int code=cur.optInt("weather_code");
        String cond=weatherCode(code).toUpperCase(Locale.US);String current=cond+"\n"+temperatureLabel(temp)+"\nFEELS LIKE "+temperatureLabel(feels);
        String icon=weatherGlyph(code);String local=icon+"   "+cond+"        "+temperatureLabel(temp)+"\nHUMIDITY   "+String.format(Locale.US,"%.0f%%",hum)+"     CLOUD   "+(Double.isNaN(cloud)?"--":String.format(Locale.US,"%.0f%%",cloud))+"     PRECIP   "+String.format(Locale.US,"%.0f mm",precip)+"\nGUSTS      "+(Double.isNaN(gust)?"--":String.format(Locale.US,"%.0f %s",speedDisplay(gust),speedUnit()))+"     PRESSURE "+(Double.isNaN(pressure)?"--":String.format(Locale.US,"%.0f hPa",pressure));
        JSONObject h=root.getJSONObject("hourly");JSONArray pres=h.optJSONArray("pressure_msl");double[] pHist=new double[Math.min(12,pres==null?0:pres.length())];for(int i=0;i<pHist.length;i++)pHist[i]=pres.optDouble(Math.max(0,pres.length()-pHist.length+i),Double.NaN);
        JSONObject daily=root.optJSONObject("daily");String sunrise=daily==null?"--":shortClock(daily.optJSONArray("sunrise")==null?"":daily.optJSONArray("sunrise").optString(0));String sunset=daily==null?"--":shortClock(daily.optJSONArray("sunset")==null?"":daily.optJSONArray("sunset").optString(0));return new WeatherDisplay(current,"",buildFiveDayFromOpenMeteo(root),direction,wind,hum,cloud,local,icon,prefs.getBoolean("fahrenheit",false)?"°F":"°C",pHist,buildForecastDaysFromOpenMeteo(root),cond,temp,feels,precip,gust,pressure,sunrise,sunset);
    }
    private String shortClock(String iso){try{return iso.length()>=16?iso.substring(11,16):iso;}catch(Exception e){return "--";}}
    private double speedDisplay(double kmh){return prefs.getBoolean("miles",true)?kmh*0.621371:kmh;}
    private String speedUnit(){return prefs.getBoolean("miles",true)?"mph":"km/h";}

    private String buildFiveDayFromMet(JSONArray ts) throws Exception {
        LinkedHashMap<String,double[]> days=new LinkedHashMap<String,double[]>();
        LinkedHashMap<String,String> labels=new LinkedHashMap<String,String>();
        SimpleDateFormat in=new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'",Locale.US);
        in.setTimeZone(TimeZone.getTimeZone("UTC"));
        SimpleDateFormat keyFmt=new SimpleDateFormat("yyyy-MM-dd",Locale.US);
        SimpleDateFormat dayFmt=new SimpleDateFormat("EEE  d MMM",Locale.UK);
        for(int i=0;i<ts.length()&&days.size()<6;i++){
            JSONObject p=ts.getJSONObject(i);
            Date dt;
            try{dt=in.parse(p.optString("time"));}catch(Exception ex){continue;}
            String key=keyFmt.format(dt);
            JSONObject d=p.getJSONObject("data").getJSONObject("instant").getJSONObject("details");
            double t=d.optDouble("air_temperature");
            double w=d.optDouble("wind_speed");
            double[] v=days.get(key);
            if(v==null){v=new double[]{t,t,w};days.put(key,v);labels.put(key,dayFmt.format(dt).toUpperCase(Locale.UK));}
            else{v[0]=Math.min(v[0],t);v[1]=Math.max(v[1],t);v[2]=Math.max(v[2],w);}
        }
        StringBuilder out=new StringBuilder();
        int n=0;
        for(String key:days.keySet()){
            if(n++>=5)break;
            double[] v=days.get(key);
            out.append(String.format(Locale.US,"%-12s  LOW %7s   HIGH %7s   WIND %4.1f m/s\n",
                labels.get(key),temperatureLabel(v[0]),temperatureLabel(v[1]),v[2]));
        }
        return out.toString();
    }

    private String buildFiveDayFromOpenMeteo(JSONObject root) {
        try {
            JSONObject d=root.getJSONObject("daily");
            JSONArray times=d.getJSONArray("time");
            JSONArray codes=d.getJSONArray("weather_code");
            JSONArray highs=d.getJSONArray("temperature_2m_max");
            JSONArray lows=d.getJSONArray("temperature_2m_min");
            JSONArray rain=d.optJSONArray("precipitation_probability_max");
            JSONArray winds=d.getJSONArray("wind_speed_10m_max");
            SimpleDateFormat input=new SimpleDateFormat("yyyy-MM-dd",Locale.US);
            SimpleDateFormat label=new SimpleDateFormat("EEE",Locale.UK);
            StringBuilder out=new StringBuilder();
            int count=Math.min(5,times.length());
            for(int i=0;i<count;i++){
                Date dt=input.parse(times.optString(i));
                out.append(String.format(Locale.US,
                    "%-4s  %-10s  HIGH %7s  LOW %7s  RAIN %4.1f mm  WIND %4.1f km/h\n",
                    dt==null?"---":label.format(dt).toUpperCase(Locale.UK),
                    weatherCode(codes.optInt(i)).toUpperCase(Locale.US),
                    temperatureLabel(highs.optDouble(i)),
                    temperatureLabel(lows.optDouble(i)),
                    rain.optDouble(i),
                    winds.optDouble(i)));
            }
            return out.toString();
        } catch(Exception e) {
            return "Five-day forecast unavailable.";
        }
    }

    private String temperatureLabel(double celsius) {
        if(prefs.getBoolean("fahrenheit",false))
            return String.format(Locale.US,"%.0f°F",celsius*9.0/5.0+32.0);
        return String.format(Locale.US,"%.0f°C",celsius);
    }

    private String weatherGlyph(String code) {
        if(code==null)return "·";
        String s=code.toLowerCase(Locale.US);
        if(s.contains("thunder"))return "⚡";
        if(s.contains("snow"))return "✳";
        if(s.contains("rain")||s.contains("sleet"))return "☂";
        if(s.contains("fog"))return "≋";
        if(s.contains("cloud"))return "☁";
        return "☀";
    }

    private String weatherGlyph(int code) {
        if(code>=95)return "⚡";
        if(code>=71&&code<=77)return "✳";
        if((code>=51&&code<=67)||(code>=80&&code<=82))return "☂";
        if(code==45||code==48)return "≋";
        if(code>=1&&code<=3)return "☁";
        return "☀";
    }

    static class LegacyLaunch {
        final String name,status,mission,pad,location;
        final long netMillis;
        String sourceNote="";
        String imageUrl="",infographicUrl="";
        double lat=Double.NaN,lon=Double.NaN;
        LegacyLaunch(String name,long netMillis,String status,String mission,String pad,String location){
            this.name=name;this.netMillis=netMillis;this.status=status;this.mission=mission;this.pad=pad;this.location=location;
        }
    }

    static class WeatherDisplay {
        final String current,forecast,fiveDay,localConditions,icon,tempUnit,condition,sunrise,sunset;
        final double windDirection,windSpeed,humidity,cloud,temperature,feels,precip,gust,pressure;
        final double[] pressureHistory;
        final ArrayList<ForecastDay> days;
        WeatherDisplay(String current,String forecast,String fiveDay,double windDirection,double windSpeed,double humidity,double cloud,
                       String localConditions,String icon,String tempUnit,double[] pressureHistory,ArrayList<ForecastDay> days,
                       String condition,double temperature,double feels,double precip,double gust,double pressure,String sunrise,String sunset){
            this.current=current;this.forecast=forecast;this.fiveDay=fiveDay;
            this.windDirection=windDirection;this.windSpeed=windSpeed;this.humidity=humidity;this.cloud=cloud;
            this.localConditions=localConditions;this.icon=icon;this.tempUnit=tempUnit;this.pressureHistory=pressureHistory;this.days=days;
            this.condition=condition;this.temperature=temperature;this.feels=feels;this.precip=precip;this.gust=gust;this.pressure=pressure;this.sunrise=sunrise;this.sunset=sunset;
        }
    }

    static class ForecastDay {
        final String day,condition,high,low,rain;
        ForecastDay(String day,String condition,String high,String low,String rain){
            this.day=day;this.condition=condition;this.high=high;this.low=low;this.rain=rain;
        }
    }

    private void buildFiveDayHeader() {
        if(fiveDayGrid==null)return;
        fiveDayGrid.removeAllViews();
        LinearLayout row=new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        String[] heads={"DAY","WX","HIGH","LOW","RAIN"};
        float[] weights={1.0f,1.7f,1.0f,1.0f,1.0f};
        for(int i=0;i<heads.length;i++){
            TextView t=text(heads[i],11,CYAN);
            t.setGravity(Gravity.CENTER);
            t.setTypeface(Typeface.MONOSPACE,Typeface.BOLD);
            t.setBackground(cellBackground(CYAN));
            row.addView(t,new LinearLayout.LayoutParams(0,dp(28),weights[i]));
        }
        fiveDayGrid.addView(row);
    }

    private void renderFiveDay(List<ForecastDay> days) {
        if(fiveDayGrid==null)return;
        buildFiveDayHeader();
        if(days==null||days.isEmpty()){
            TextView none=text("FIVE-DAY FORECAST UNAVAILABLE",12,DIM);
            none.setGravity(Gravity.CENTER);
            fiveDayGrid.addView(none,new LinearLayout.LayoutParams(-1,dp(44)));
            return;
        }
        float[] weights={1.0f,1.7f,1.0f,1.0f,1.0f};
        for(int i=0;i<Math.min(5,days.size());i++){
            ForecastDay d=days.get(i);
            LinearLayout row=new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            String[] vals={d.day,d.condition,d.high,d.low,d.rain};
            for(int k=0;k<vals.length;k++){
                TextView t=text(vals[k],11,k==4?AMBER:(k==1?TEXT:CYAN));
                t.setGravity(Gravity.CENTER);
                t.setSingleLine(true);
                t.setBackground(cellBackground(BORDER));
                row.addView(t,new LinearLayout.LayoutParams(0,dp(31),weights[k]));
            }
            fiveDayGrid.addView(row);
        }
    }

    private ArrayList<ForecastDay> buildForecastDaysFromOpenMeteo(JSONObject root) {
        ArrayList<ForecastDay> out=new ArrayList<ForecastDay>();
        try{
            JSONObject d=root.getJSONObject("daily");
            JSONArray times=d.getJSONArray("time");
            JSONArray codes=d.getJSONArray("weather_code");
            JSONArray highs=d.getJSONArray("temperature_2m_max");
            JSONArray lows=d.getJSONArray("temperature_2m_min");
            JSONArray rain=d.optJSONArray("precipitation_probability_max");
            SimpleDateFormat input=new SimpleDateFormat("yyyy-MM-dd",Locale.US);
            SimpleDateFormat label=new SimpleDateFormat("EEE",Locale.UK);
            for(int i=0;i<Math.min(5,times.length());i++){
                Date dt=input.parse(times.optString(i));
                out.add(new ForecastDay(
                    dt==null?"---":label.format(dt).toUpperCase(Locale.UK),
                    weatherCode(codes.optInt(i)).toUpperCase(Locale.US),
                    temperatureLabel(highs.optDouble(i)),
                    temperatureLabel(lows.optDouble(i)),
                    rain==null?"--%":String.format(Locale.US,"%.0f%%",rain.optDouble(i))
                ));
            }
        }catch(Exception ignored){}
        return out;
    }

    private ArrayList<ForecastDay> buildForecastDaysFromMet(JSONArray ts) {
        ArrayList<ForecastDay> out=new ArrayList<ForecastDay>();
        try{
            LinkedHashMap<String,double[]> values=new LinkedHashMap<String,double[]>();
            LinkedHashMap<String,String> labels=new LinkedHashMap<String,String>();
            LinkedHashMap<String,String> conditions=new LinkedHashMap<String,String>();
            LinkedHashMap<String,Double> rainTotals=new LinkedHashMap<String,Double>();
            SimpleDateFormat in=new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'",Locale.US);
            in.setTimeZone(TimeZone.getTimeZone("UTC"));
            SimpleDateFormat keyFmt=new SimpleDateFormat("yyyy-MM-dd",Locale.US);
            SimpleDateFormat dayFmt=new SimpleDateFormat("EEE",Locale.UK);

            for(int i=0;i<ts.length()&&values.size()<6;i++){
                JSONObject p=ts.getJSONObject(i);
                Date dt;
                try{dt=in.parse(p.optString("time"));}catch(Exception ex){continue;}
                String key=keyFmt.format(dt);
                JSONObject data=p.getJSONObject("data");
                JSONObject details=data.getJSONObject("instant").getJSONObject("details");
                double temp=details.optDouble("air_temperature");
                double[] v=values.get(key);
                if(v==null){
                    v=new double[]{temp,temp};
                    values.put(key,v);
                    labels.put(key,dayFmt.format(dt).toUpperCase(Locale.UK));
                }else{
                    v[0]=Math.min(v[0],temp);
                    v[1]=Math.max(v[1],temp);
                }

                JSONObject next=data.optJSONObject("next_1_hours");
                if(next!=null){
                    JSONObject summary=next.optJSONObject("summary");
                    if(summary!=null && !conditions.containsKey(key)){
                        conditions.put(key,summary.optString("symbol_code","").replace("_"," ").toUpperCase(Locale.US));
                    }
                    JSONObject det=next.optJSONObject("details");
                    if(det!=null){
                        double old=rainTotals.containsKey(key)?rainTotals.get(key):0.0;
                        rainTotals.put(key,old+det.optDouble("precipitation_amount",0.0));
                    }
                }
            }

            for(String key:values.keySet()){
                if(out.size()>=5)break;
                double[] v=values.get(key);
                out.add(new ForecastDay(
                    labels.get(key),
                    conditions.containsKey(key)?conditions.get(key):"FORECAST",
                    temperatureLabel(v[1]),
                    temperatureLabel(v[0]),
                    String.format(Locale.US,"%.1f mm",rainTotals.containsKey(key)?rainTotals.get(key):0.0)
                ));
            }
        }catch(Exception ignored){}
        return out;
    }

    private String weatherCode(int code) {
        if (code == 0) return "clear";
        if (code <= 3) return "cloudy";
        if (code == 45 || code == 48) return "fog";
        if (code >= 51 && code <= 67) return "rain";
        if (code >= 71 && code <= 77) return "snow";
        if (code >= 80 && code <= 82) return "showers";
        if (code >= 95) return "thunderstorm";
        return "weather";
    }

    private void showTime() {
        clearPage();
        LinearLayout outer=new LinearLayout(this);outer.setOrientation(LinearLayout.VERTICAL);outer.setPadding(dp(5),dp(3),dp(5),dp(3));
        LinearLayout summary=new LinearLayout(this);summary.setOrientation(LinearLayout.HORIZONTAL);summary.setPadding(dp(6),dp(3),dp(6),dp(3));summary.setBackground(panelBackground(false));timeClock=text("",24,CYAN);timeClock.setTypeface(Typeface.MONOSPACE,Typeface.BOLD);timeDate=text("",13,TEXT);String homeZone=prefs.getString("timezone","Europe/London");TextView zone=text(locationLabel().toUpperCase(Locale.UK)+"\n"+homeZone,12,AMBER);summary.addView(labeledBlock("LOCAL TIME",timeClock),new LinearLayout.LayoutParams(0,dp(70),1f));summary.addView(labeledBlock("LOCAL DATE",timeDate),new LinearLayout.LayoutParams(0,dp(70),1f));summary.addView(labeledBlock("LOCATION / ZONE",zone),new LinearLayout.LayoutParams(0,dp(70),1f));outer.addView(summary,new LinearLayout.LayoutParams(-1,dp(76)));

        LinearLayout body=new LinearLayout(this);body.setOrientation(LinearLayout.HORIZONTAL);body.setWeightSum(10f);
        AnalogClockView analog=new AnalogClockView(this);body.addView(analog,new LinearLayout.LayoutParams(0,-1,6f));
        LinearLayout right=new LinearLayout(this);right.setOrientation(LinearLayout.VERTICAL);right.setPadding(dp(6),0,0,0);
        LinearLayout daylight=card("DAYLIGHT // LOCAL 24-HOUR TIMELINE");DaylightView daylightView=new DaylightView(this);daylight.addView(daylightView,new LinearLayout.LayoutParams(-1,0,1f));right.addView(daylight,new LinearLayout.LayoutParams(-1,0,1.15f));
        LinearLayout worldWrap=new LinearLayout(this);worldWrap.setOrientation(LinearLayout.VERTICAL);TextView worldToggle=text("WORLD CLOCKS  ▾",11,CYAN);worldToggle.setTypeface(Typeface.MONOSPACE,Typeface.BOLD);worldToggle.setGravity(Gravity.CENTER_VERTICAL);worldToggle.setBackground(panelBackground(false));worldWrap.addView(worldToggle,new LinearLayout.LayoutParams(-1,dp(30)));LinearLayout world=card("");worldClocksBox=new LinearLayout(this);worldClocksBox.setOrientation(LinearLayout.VERTICAL);timeZones=text("",12,TEXT);worldClocksBox.addView(timeZones,new LinearLayout.LayoutParams(-1,-2));ScrollView worldScroll=new ScrollView(this);worldScroll.addView(worldClocksBox);world.addView(worldScroll,new LinearLayout.LayoutParams(-1,0,1f));boolean wcOpen=prefs.getBoolean("worldClocksOpen",false);world.setVisibility(wcOpen?View.VISIBLE:View.GONE);worldToggle.setText(wcOpen?"WORLD CLOCKS  ▴":"WORLD CLOCKS  ▾");worldWrap.addView(world,new LinearLayout.LayoutParams(-1,0,1f));LinearLayout.LayoutParams wcLp=new LinearLayout.LayoutParams(-1,wcOpen?0:dp(32),wcOpen?1.15f:0f);right.addView(worldWrap,wcLp);worldToggle.setOnClickListener(v->{boolean open=world.getVisibility()!=View.VISIBLE;world.setVisibility(open?View.VISIBLE:View.GONE);worldToggle.setText(open?"WORLD CLOCKS  ▴":"WORLD CLOCKS  ▾");LinearLayout.LayoutParams lp=(LinearLayout.LayoutParams)worldWrap.getLayoutParams();lp.height=open?0:dp(32);lp.weight=open?1.15f:0f;worldWrap.setLayoutParams(lp);prefs.edit().putBoolean("worldClocksOpen",open).apply();});
        LinearLayout history=card("ON THIS DAY // AIR & SPACE HISTORY");onThisDayText=text("Loading historical events…",12,TEXT);ScrollView historyScroll=new ScrollView(this);historyScroll.setFillViewport(true);historyScroll.addView(onThisDayText,new ScrollView.LayoutParams(-1,-2));history.addView(historyScroll,new LinearLayout.LayoutParams(-1,0,1f));right.addView(history,new LinearLayout.LayoutParams(-1,0,1.25f));
        body.addView(right,new LinearLayout.LayoutParams(0,-1,4f));outer.addView(body,new LinearLayout.LayoutParams(-1,0,1f));pageHost.addView(outer);
        refreshDaylight(daylightView);
        String savedHistory=prefs.getString("onThisDayText","");
        if(savedHistory.length()>0&&onThisDayText!=null)onThisDayText.setText(savedHistory);
        refreshOnThisDay();

        clockTick=new Runnable(){public void run(){Date now=new Date();boolean h24=prefs.getBoolean("clock24",true);SimpleDateFormat tf=new SimpleDateFormat(h24?"HH:mm:ss":"hh:mm:ss a",Locale.UK);SimpleDateFormat df=new SimpleDateFormat("EEE  dd MMM yyyy",Locale.UK);timeClock.setText(tf.format(now));timeDate.setText(df.format(now).toUpperCase(Locale.UK));timeZones.setText(zoneRow("HOME",prefs.getString("timezone","Europe/London"),now,h24)+"\n"+zoneRow("LONDON","Europe/London",now,h24)+"\n"+zoneRow("NEW YORK","America/New_York",now,h24)+"\n"+zoneRow("LOS ANGELES","America/Los_Angeles",now,h24)+"\n"+zoneRow("DUBAI","Asia/Dubai",now,h24)+"\n"+zoneRow("TOKYO","Asia/Tokyo",now,h24)+"\n"+zoneRow("SYDNEY","Australia/Sydney",now,h24));analog.setTime(now);daylightView.setNow(now);ui.postDelayed(this,1000);}};clockTick.run();
    }

    private LinearLayout labeledBlock(String label,TextView value){LinearLayout b=new LinearLayout(this);b.setOrientation(LinearLayout.VERTICAL);b.setPadding(dp(4),dp(2),dp(4),dp(2));b.addView(text(label,9,DIM),new LinearLayout.LayoutParams(-1,dp(20)));value.setGravity(Gravity.CENTER_VERTICAL);b.addView(value,new LinearLayout.LayoutParams(-1,0,1f));return b;}
    private String zoneRow(String name,String zone,Date now,boolean h24){SimpleDateFormat f=new SimpleDateFormat(h24?"HH:mm:ss z   EEE dd MMM":"hh:mm:ss a z   EEE dd MMM",Locale.UK);f.setTimeZone(TimeZone.getTimeZone(zone));return String.format(Locale.US,"%-12s  %s",name,f.format(now));}

    private void refreshDaylight(final DaylightView view){io.execute(()->{try{String url="https://api.open-meteo.com/v1/forecast?latitude="+currentLat()+"&longitude="+currentLon()+"&daily=sunrise,sunset&timezone=auto&forecast_days=1";JSONObject d=getJson(url).getJSONObject("daily");String rise=d.getJSONArray("sunrise").optString(0),set=d.getJSONArray("sunset").optString(0);final int rm=timeMinutes(rise),sm=timeMinutes(set);ui.post(()->view.setTimes(rm,sm));}catch(Exception ignored){}});}
    private int timeMinutes(String iso){try{String t=iso.substring(11,16);return Integer.parseInt(t.substring(0,2))*60+Integer.parseInt(t.substring(3,5));}catch(Exception e){return 6*60;}}
    private String todayHistoryKey(){
        return new SimpleDateFormat("yyyy-MM-dd",Locale.UK).format(new Date());
    }
    private void refreshOnThisDay(){
        final String today=todayHistoryKey();
        final String cachedDate=prefs.getString("onThisDayDate","");
        final String cachedText=prefs.getString("onThisDayText","");
        if(today.equals(cachedDate)&&cachedText.length()>0){
            if(onThisDayText!=null)onThisDayText.setText(cachedText);
            return;
        }
        final Calendar cal=Calendar.getInstance();final int m=cal.get(Calendar.MONTH)+1,d=cal.get(Calendar.DAY_OF_MONTH);
        io.execute(()->{
            StringBuilder out=new StringBuilder(new SimpleDateFormat("EEEE  dd MMMM",Locale.UK).format(new Date()).toUpperCase(Locale.UK)+"\n\n");
            int added=0;
            try{
                JSONObject root=getJson(String.format(Locale.US,"https://api.wikimedia.org/feed/v1/wikipedia/en/onthisday/events/%02d/%02d",m,d));
                JSONArray arr=root.optJSONArray("events");
                String[] keys={"aircraft","airplane","aeroplane","aviation","flight","pilot","airport","airline","space","rocket","satellite","astronaut","cosmonaut","nasa","esa","moon","solar","astronom","comet","asteroid","planet","telescope","observatory","spacecraft","shuttle","apollo"};
                if(arr!=null)for(int i=0;i<arr.length()&&added<4;i++){
                    JSONObject e=arr.optJSONObject(i);if(e==null)continue;
                    String txt=e.optString("text",""),low=txt.toLowerCase(Locale.US);boolean ok=false;
                    for(String k:keys)if(low.contains(k)){ok=true;break;}
                    if(ok){out.append(e.optInt("year")).append("   ").append(txt).append("\n\n");added++;}
                }
            }catch(Exception ignored){}
            if(added==0){
                out.append("AVIATION / SPACE HISTORY\n\n");
                out.append("1903   Early powered-flight development accelerated the transition from gliders to practical aeroplanes.\n\n");
                out.append("1957   The opening years of the Space Age established artificial satellites as a new astronomical and engineering tool.\n\n");
                out.append("1977   Voyager mission operations began the outer-planet exploration era.\n\n");
            }
            final String txt=out.toString();
            prefs.edit().putString("onThisDayDate",today).putString("onThisDayText",txt).apply();
            ui.post(()->{if(onThisDayText!=null)onThisDayText.setText(txt);});
        });
    }

    private void refreshLaunches() {
        if(launchSummary==null)return;
        launchSummary.setText("LOADING UPCOMING MISSIONS…");
        io.execute(() -> {
            final ArrayList<LegacyLaunch> loaded=new ArrayList<LegacyLaunch>();
            String error=null;
            try {
                String launchUrl="https://ll.thespacedevs.com/2.3.0/launches/upcoming/?limit=12&ordering=net";
                JSONObject root;
                boolean launchCached=false;
                try { root=getJson(launchUrl); }
                catch(Exception liveError) { root=readJsonCache(launchUrl,6*60*60_000L); launchCached=root!=null; if(root==null)throw liveError; }
                JSONArray results=root.optJSONArray("results");
                if(results!=null) for(int i=0;i<results.length();i++){
                    JSONObject item=results.optJSONObject(i);
                    if(item==null)continue;
                    String name=item.optString("name","Unnamed mission");
                    String net=item.optString("net");
                    long millis=parseIsoMillis(net);
                    if(millis<=0)continue;
                    JSONObject status=item.optJSONObject("status");
                    JSONObject mission=item.optJSONObject("mission");
                    JSONObject pad=item.optJSONObject("pad");
                    JSONObject location=pad==null?null:pad.optJSONObject("location");
                    loaded.add(new LegacyLaunch(
                        name,
                        millis,
                        status==null?"Scheduled":status.optString("name","Scheduled"),
                        mission==null?"":mission.optString("description",""),
                        pad==null?"":pad.optString("name",""),
                        location==null?"":location.optString("name","")
                    ));
                }
                Collections.sort(loaded,(a,b)->Long.compare(a.netMillis,b.netMillis));
                if(launchCached && !loaded.isEmpty()) loaded.get(0).sourceNote="THE SPACE DEVS CACHE";
            } catch(Exception e) {
                error="Launch feed unavailable: "+shortError(e);
            }
            final String finalError=error;
            ui.post(() -> {
                launchList.clear();
                launchList.addAll(loaded);
                if(finalError!=null && launchList.isEmpty()) launchSummary.setText(finalError);
                else if(launchList.isEmpty()) launchSummary.setText("Launch service returned no parseable upcoming missions.");
                else renderLaunches();
            });
        });
    }

    private long parseIsoMillis(String iso) {
        if(iso==null)return 0L;
        try {
            String value=iso.trim();
            if(value.length()<19)return 0L;

            int year=Integer.parseInt(value.substring(0,4));
            int month=Integer.parseInt(value.substring(5,7));
            int day=Integer.parseInt(value.substring(8,10));
            int hour=Integer.parseInt(value.substring(11,13));
            int minute=Integer.parseInt(value.substring(14,16));
            int second=Integer.parseInt(value.substring(17,19));

            int millis=0;
            int dot=value.indexOf('.',19);
            int zoneStart=19;
            if(dot==19){
                int i=20;
                StringBuilder frac=new StringBuilder();
                while(i<value.length() && Character.isDigit(value.charAt(i)) && frac.length()<3){
                    frac.append(value.charAt(i++));
                }
                while(frac.length()<3)frac.append('0');
                if(frac.length()>0)millis=Integer.parseInt(frac.toString());
                zoneStart=i;
                while(zoneStart<value.length() && Character.isDigit(value.charAt(zoneStart)))zoneStart++;
            }

            Calendar cal=Calendar.getInstance(TimeZone.getTimeZone("UTC"),Locale.US);
            cal.clear();
            cal.set(Calendar.YEAR,year);
            cal.set(Calendar.MONTH,month-1);
            cal.set(Calendar.DAY_OF_MONTH,day);
            cal.set(Calendar.HOUR_OF_DAY,hour);
            cal.set(Calendar.MINUTE,minute);
            cal.set(Calendar.SECOND,second);
            cal.set(Calendar.MILLISECOND,millis);
            long utc=cal.getTimeInMillis();

            if(value.endsWith("Z"))return utc;

            int plus=value.indexOf('+',zoneStart);
            int minus=value.indexOf('-',zoneStart);
            int pos=plus>=0?plus:minus;
            if(pos>=0 && pos+5<value.length()){
                int sign=value.charAt(pos)=='-'?-1:1;
                int zh=Integer.parseInt(value.substring(pos+1,pos+3));
                int zm=Integer.parseInt(value.substring(pos+4,pos+6));
                long offset=(zh*60L+zm)*60_000L*sign;
                return utc-offset;
            }
            return utc;
        } catch(Exception e) {
            return 0L;
        }
    }

    private void renderLaunches() {
        if(launchSummary==null)return;
        if(launchList.isEmpty()){
            launchSummary.setText("No upcoming launch data available.");
            return;
        }
        long now=System.currentTimeMillis();
        int count=Math.min(launchesExpanded?8:3,launchList.size());
        StringBuilder out=new StringBuilder();

        LegacyLaunch first=launchList.get(0);
        if(first.sourceNote.length()>0) out.append(first.sourceNote).append("\n");
        out.append("NEXT  ").append(launchCountdown(first.netMillis-now)).append("\n");
        out.append(first.name).append("\n");
        out.append(first.status.toUpperCase(Locale.US)).append("  •  ")
           .append(formatLaunchTime(first.netMillis)).append("\n");

        for(int i=0;i<count;i++){
            LegacyLaunch l=launchList.get(i);
            out.append("\n").append(i+1).append(". ").append(l.name).append("\n");
            out.append("   ").append(formatLaunchTime(l.netMillis))
               .append("  •  ").append(launchCountdown(l.netMillis-now)).append("\n");
            if(launchesExpanded){
                if(l.pad.length()>0)out.append("   PAD: ").append(l.pad).append("\n");
                if(l.location.length()>0)out.append("   SITE: ").append(l.location).append("\n");
                if(l.mission.length()>0){
                    String m=l.mission.replace("\n"," ").trim();
                    if(m.length()>220)m=m.substring(0,220)+"…";
                    out.append("   ").append(m).append("\n");
                }
            }
        }
        launchSummary.setText(out.toString().trim());
    }

    private String launchCountdown(long diff) {
        boolean passed=diff<0;
        long sec=Math.abs(diff)/1000;
        long days=sec/86400; sec%=86400;
        long hrs=sec/3600; sec%=3600;
        long min=sec/60; long s=sec%60;
        String core=days>0
            ? String.format(Locale.US,"%dd %02d:%02d:%02d",days,hrs,min,s)
            : String.format(Locale.US,"%02d:%02d:%02d",hrs,min,s);
        return (passed?"T+":"T−")+core;
    }

    private String formatLaunchTime(long millis) {
        SimpleDateFormat f=new SimpleDateFormat("d MMM yyyy  HH:mm z",Locale.UK);
        return f.format(new Date(millis));
    }

    private String zoneLine(String label, String zone, Date now, boolean h24) {
        SimpleDateFormat f = new SimpleDateFormat(h24 ? "HH:mm:ss  z" : "hh:mm:ss a  z", Locale.UK);
        f.setTimeZone(TimeZone.getTimeZone(zone));
        return label + "\n" + f.format(now);
    }

    private void showSettings(){showSettingsTab("HOME");}

    private void showSettingsTab(final String tab){
        clearPage();FrameLayout settingsStage=new FrameLayout(this);settingsStage.setPadding(dp(12),dp(4),dp(12),dp(4));LinearLayout page=new LinearLayout(this);page.setOrientation(LinearLayout.VERTICAL);page.setPadding(dp(8),dp(5),dp(8),dp(6));page.setBackground(panelBackground(true));
        LinearLayout tabs=new LinearLayout(this);tabs.setOrientation(LinearLayout.HORIZONTAL);String[] names={"HOME","RADAR","RADAR FX","SKY / DOCK","DATA","ABOUT"};for(String n:names){Button b=button(n);b.setTextColor(n.equals(tab)?GREEN:TEXT);b.setOnClickListener(v->showSettingsTab(n));tabs.addView(b,new LinearLayout.LayoutParams(0,dp(34),1f));}page.addView(tabs,new LinearLayout.LayoutParams(-1,dp(36)));
        ScrollView sc=new ScrollView(this);LinearLayout content=new LinearLayout(this);content.setOrientation(LinearLayout.VERTICAL);content.setPadding(dp(12),dp(8),dp(12),dp(8));content.setBackground(panelBackground(false));
        final ArrayList<View> saveFields=new ArrayList<View>();

        if("HOME".equals(tab)){
            content.addView(text("Your saved location, measurement preferences and launch layout. These choices drive every local display in the instrument.",13,CYAN));
            addSettingsRow(content,"Location name",field(prefs.getString("place","Leeds"),"Location name"),saveFields);
            addSettingsRow(content,"Latitude",field(String.format(Locale.US,"%.6f",currentLat()),"Latitude"),saveFields);
            addSettingsRow(content,"Longitude",field(String.format(Locale.US,"%.6f",currentLon()),"Longitude"),saveFields);
            Spinner units=spinner(new String[]{"Imperial — miles / mph","Metric — km / km/h"},prefs.getBoolean("miles",true)?0:1);addSettingsRow(content,"Distance / speed",units,saveFields);
            Spinner temp=spinner(new String[]{"Celsius (°C)","Fahrenheit (°F)"},prefs.getBoolean("fahrenheit",false)?1:0);addSettingsRow(content,"Temperature",temp,saveFields);
            addSettingsRow(content,"Timezone",field(prefs.getString("timezone","Europe/London"),"Europe/London"),saveFields);
            Spinner startup=spinner(new String[]{"RADAR","FLIGHT","WEATHER","TIME","SPACE","SKY VIEW","LAUNCHES"},indexOf(new String[]{"RADAR","FLIGHT","WEATHER","TIME","SPACE","SKY VIEW","LAUNCHES"},prefs.getString("startup","RADAR")));addSettingsRow(content,"Startup page",startup,saveFields);
        }else if("RADAR".equals(tab)){
            content.addView(text("Radar geometry, orientation and monitored range.",13,CYAN));
            Spinner ori=spinner(new String[]{"North up","East up","South up","West up"},(prefs.getInt("radarOrientation",180)/90)%4);addSettingsRow(content,"Orientation",ori,saveFields);
            Spinner rng=spinner(new String[]{"5 mi","10 mi","20 mi","40 mi","80 mi","120 mi","200 mi"},rangeIndex());addSettingsRow(content,"Radar range",rng,saveFields);
            Spinner ar=spinner(new String[]{"Off","2 mi","5 mi","10 mi","20 mi","40 mi"},alertIndex());addSettingsRow(content,"Orange alert range",ar,saveFields);
            Spinner refresh=spinner(new String[]{"10 seconds","20 seconds","30 seconds","60 seconds","120 seconds"},refreshIndex());addSettingsRow(content,"Refresh interval",refresh,saveFields);
        }else if("RADAR FX".equals(tab)){
            content.addView(text("Radar visual effects and contact behaviour.",13,CYAN));
            Spinner style=spinner(new String[]{"Classic Scope","Tactical Grid","Centre Pulse","Dual Sweep","Sonar Rings","ATC Tower"},styleIndex());addSettingsRow(content,"Radar style",style,saveFields);
            CheckBox trails=check("Show aircraft trails",prefs.getBoolean("trails",true));content.addView(trails);saveFields.add(trails);
            CheckBox auto=check("Automatically select aircraft entering the alert range",prefs.getBoolean("alertEnabled",true));content.addView(auto);saveFields.add(auto);
            CheckBox aircraftAlerts=check("Notify when a new aircraft enters the alert range",prefs.getBoolean("aircraftAlerts",true));content.addView(aircraftAlerts);saveFields.add(aircraftAlerts);
            CheckBox militaryAlerts=check("Notify when a military-flagged aircraft enters the alert range",prefs.getBoolean("militaryAlerts",true));content.addView(militaryAlerts);saveFields.add(militaryAlerts);
            Spinner theme=spinner(new String[]{"Green","Amber","Ice blue","Red","Violet","Custom"},themeIndex());addSettingsRow(content,"Theme accent",theme,saveFields);
            addSettingsRow(content,"Custom accent",field(prefs.getString("customAccent","#4FFF9F"),"#4FFF9F"),saveFields);
        }else if("SKY / DOCK".equals(tab)){
            content.addView(text("Sky View orientation and automatic information-display cycling.",13,CYAN));
            CheckBox dock=check("Automatic page cycling",prefs.getBoolean("autoCycle",false));content.addView(dock);saveFields.add(dock);
            Spinner dwell=spinner(new String[]{"10 seconds","15 seconds","20 seconds","30 seconds","45 seconds","60 seconds"},cycleIndex());addSettingsRow(content,"Page duration",dwell,saveFields);

            TextView cycleHeading=text("PAGES INCLUDED IN AUTOMATIC CYCLE",11,CYAN);cycleHeading.setTypeface(Typeface.MONOSPACE,Typeface.BOLD);content.addView(cycleHeading);
            LinearLayout cycleGrid=new LinearLayout(this);cycleGrid.setOrientation(LinearLayout.HORIZONTAL);
            LinearLayout cycleLeft=new LinearLayout(this);cycleLeft.setOrientation(LinearLayout.VERTICAL);
            LinearLayout cycleRight=new LinearLayout(this);cycleRight.setOrientation(LinearLayout.VERTICAL);
            CheckBox cycleRadar=check("Radar",prefs.getBoolean("cycleRadar",true));cycleLeft.addView(cycleRadar);saveFields.add(cycleRadar);
            CheckBox cycleFlight=check("Flight",prefs.getBoolean("cycleFlight",true));cycleLeft.addView(cycleFlight);saveFields.add(cycleFlight);
            CheckBox cycleWeather=check("Weather",prefs.getBoolean("cycleWeather",true));cycleLeft.addView(cycleWeather);saveFields.add(cycleWeather);
            CheckBox cycleTime=check("Time",prefs.getBoolean("cycleTime",true));cycleLeft.addView(cycleTime);saveFields.add(cycleTime);
            CheckBox cycleSpace=check("Space",prefs.getBoolean("cycleSpace",true));cycleRight.addView(cycleSpace);saveFields.add(cycleSpace);
            CheckBox cycleSky=check("Sky View",prefs.getBoolean("cycleSkyView",true));cycleRight.addView(cycleSky);saveFields.add(cycleSky);
            CheckBox cycleLaunches=check("Launches",prefs.getBoolean("cycleLaunches",true));cycleRight.addView(cycleLaunches);saveFields.add(cycleLaunches);
            cycleGrid.addView(cycleLeft,new LinearLayout.LayoutParams(0,-2,1f));cycleGrid.addView(cycleRight,new LinearLayout.LayoutParams(0,-2,1f));content.addView(cycleGrid);

            Spinner sky=spinner(new String[]{"NORTH","EAST","SOUTH","WEST"},(prefs.getInt("skyOrientation",180)/90)%4);addSettingsRow(content,"Sky View facing",sky,saveFields);
            CheckBox skyAircraftSetting=new CheckBox(this);skyAircraftSetting.setText("Show aircraft in Sky View");skyAircraftSetting.setTextColor(TEXT);skyAircraftSetting.setChecked(prefs.getBoolean("skyAircraft",true));content.addView(skyAircraftSetting);CheckBox skySatSetting=new CheckBox(this);skySatSetting.setText("Show satellites in Sky View");skySatSetting.setTextColor(TEXT);skySatSetting.setChecked(prefs.getBoolean("skySatellites",true));content.addView(skySatSetting);CheckBox skySmallSetting=new CheckBox(this);skySmallSetting.setText("Show asteroids & comets in Sky View");skySmallSetting.setTextColor(TEXT);skySmallSetting.setChecked(prefs.getBoolean("skySmallBodies",true));content.addView(skySmallSetting);saveFields.add(skyAircraftSetting);saveFields.add(skySatSetting);saveFields.add(skySmallSetting);
            CheckBox night=check("Automatic night-time dimming (sunset → sunrise)",prefs.getBoolean("nightDim",true));content.addView(night);saveFields.add(night);
            CheckBox clock=check("24-hour clock",prefs.getBoolean("clock24",true));content.addView(clock);saveFields.add(clock);
        }else if("DATA".equals(tab)){
            content.addView(text("Data sources and compatibility status. This personal Fire build uses the same public providers as the main In The Sky application.",13,CYAN));
            content.addView(text("RADAR      ADSB.lol + Airplanes.live → OpenSky fallback + cache\nWORLD AIR  ADSB.lol globe → authenticated OpenSky fallback → cache\nROUTES     ADSBDB → local cache\nWEATHER    Open-Meteo hourly → MET Norway → cache\nHISTORY    Wikimedia On This Day → once per local day\nISS        WhereTheISS → recent cache\nLAUNCHES   The Space Devs → RocketLaunch.Live → cache\nSATELLITES SatNOGS → CelesTrak → cache\nSMALL BODY NASA JPL Horizons → cache\nMAPS       themed cached OpenStreetMap + bundled world asset",14,TEXT));
            content.addView(text("OpenSky REST fallback uses an API Client (client_id + client_secret), not your normal website password.",12,DIM));
            EditText osClient=field(prefs.getString("openSkyClientId","rob.howden@googlemail.com-api-client"),"xxxxxxxx-api-client");addSettingsRow(content,"OpenSky client ID",osClient,saveFields);
            EditText osSecret=field(prefs.getString("openSkyClientSecret","Dzq1Sv3I8HzrlL8nYMlPyOonvo58kg6B"),"OpenSky API client secret");osSecret.setInputType(android.text.InputType.TYPE_CLASS_TEXT|android.text.InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD);addSettingsRow(content,"OpenSky secret",osSecret,saveFields);
            Button clear=button("CLEAR LOCAL DATA CACHE");clear.setOnClickListener(v->{File[] ff=getCacheDir().listFiles();if(ff!=null)for(File x:ff)x.delete();Toast.makeText(this,"Cache cleared",Toast.LENGTH_SHORT).show();});content.addView(clear,new LinearLayout.LayoutParams(-1,dp(44)));
        }else{
            TextView aboutTitle=text("IN THE SKY FIRE LEGACY",20,GREEN);aboutTitle.setTypeface(Typeface.MONOSPACE,Typeface.BOLD);content.addView(aboutTitle);
            content.addView(text("Version "+APP_VERSION+"  •  "+BUILD_LABEL,14,CYAN));
            content.addView(text("Landscape target: Fire HD 10 (1920 × 1200)\nWindows Store visual-parity build\n\nPages: Radar • Flight • Weather • Time • Space • Sky View • Launches\n3D Gallery intentionally excluded from this Fire Legacy edition.",13,TEXT));
            content.addView(text("Build identity: v"+APP_VERSION+"\nThis number also appears at the far-right of the persistent footer so screenshots are easy to identify.",12,DIM));
        }
        sc.addView(content);page.addView(sc,new LinearLayout.LayoutParams(-1,0,1f));LinearLayout actions=new LinearLayout(this);actions.setGravity(Gravity.RIGHT);Button save=button("Save");Button cancel=button("Cancel");save.setOnClickListener(v->{saveSettingsTab(tab,saveFields);Toast.makeText(this,"Settings saved",Toast.LENGTH_SHORT).show();buildShell();refreshNightWindow();openPage(settingsReturnPage==null?"RADAR":settingsReturnPage);schedulePageCycle();});cancel.setOnClickListener(v->openPage(settingsReturnPage==null?"RADAR":settingsReturnPage));actions.addView(save,new LinearLayout.LayoutParams(dp(78),dp(40)));actions.addView(cancel,new LinearLayout.LayoutParams(dp(86),dp(40)));page.addView(actions,new LinearLayout.LayoutParams(-1,dp(44)));int sw=getResources().getDisplayMetrics().widthPixels;FrameLayout.LayoutParams modal=new FrameLayout.LayoutParams((int)(sw*.62f),-1,Gravity.CENTER);settingsStage.addView(page,modal);pageHost.addView(settingsStage);
    }

    private void addSettingsRow(LinearLayout parent,String label,View control,List<View> fields){LinearLayout row=new LinearLayout(this);row.setOrientation(LinearLayout.HORIZONTAL);TextView l=text(label,13,TEXT);l.setGravity(Gravity.CENTER_VERTICAL);row.addView(l,new LinearLayout.LayoutParams(dp(150),dp(40)));row.addView(control,new LinearLayout.LayoutParams(0,dp(34),1f));parent.addView(row);fields.add(control);}
    private Spinner spinner(String[] vals,int sel){Spinner s=new Spinner(this);ArrayAdapter<String> a=new ArrayAdapter<String>(this,android.R.layout.simple_spinner_dropdown_item,vals){@Override public View getView(int pos,View v,android.view.ViewGroup parent){TextView t=(TextView)super.getView(pos,v,parent);t.setTextColor(TEXT);t.setTextSize(12.5f);t.setTypeface(Typeface.MONOSPACE,Typeface.BOLD);t.setPadding(dp(8),0,dp(8),0);return t;}@Override public View getDropDownView(int pos,View v,android.view.ViewGroup parent){TextView t=(TextView)super.getDropDownView(pos,v,parent);t.setTextColor(TEXT);t.setBackgroundColor(PANEL_RAISED);t.setTextSize(13);t.setTypeface(Typeface.MONOSPACE);t.setPadding(dp(8),dp(7),dp(8),dp(7));return t;}};s.setAdapter(a);s.setBackground(panelBackground(true));s.setSelection(Math.max(0,Math.min(vals.length-1,sel)));return s;}
    private CheckBox check(String label,boolean checked){CheckBox c=new CheckBox(this);c.setText(label);c.setTextSize(13.5f);c.setTypeface(Typeface.MONOSPACE);c.setTextColor(checked?GREEN:TEXT);c.setChecked(checked);c.setPadding(dp(4),dp(3),dp(4),dp(3));c.setOnCheckedChangeListener((v,on)->{c.setTextColor(on?GREEN:TEXT);c.setBackground(on?selectedBackground():null);});if(checked)c.setBackground(selectedBackground());return c;}
    private int indexOf(String[] a,String v){for(int i=0;i<a.length;i++)if(a[i].equalsIgnoreCase(v))return i;return 0;}
    private int rangeIndex(){int base=prefs.getInt("range",16);int display=prefs.getBoolean("miles",true)?(int)Math.round(base/1.609344):base;int[] v={5,10,20,40,80,120,200};int b=0,d=999;for(int i=0;i<v.length;i++){int z=Math.abs(v[i]-display);if(z<d){d=z;b=i;}}return b;}
    private int alertIndex(){if(!prefs.getBoolean("alertEnabled",true))return 0;int base=prefs.getInt("alertRange",8);int display=prefs.getBoolean("miles",true)?(int)Math.round(base/1.609344):base;int[] v={0,2,5,10,20,40};int b=1,d=999;for(int i=1;i<v.length;i++){int z=Math.abs(v[i]-display);if(z<d){d=z;b=i;}}return b;}
    private int refreshIndex(){int r=prefs.getInt("refresh",30);int[] v={10,20,30,60,120};int b=0,d=999;for(int i=0;i<v.length;i++){int z=Math.abs(v[i]-r);if(z<d){d=z;b=i;}}return b;}
    private int styleIndex(){return indexOf(new String[]{"classic","tactical","pulse","dual","sonar","atc"},prefs.getString("radarStyle","classic"));}
    private int themeIndex(){return indexOf(new String[]{"PHOSPHOR","AMBER","CYAN","RED","VIOLET","CUSTOM"},prefs.getString("theme","PHOSPHOR"));}
    private int cycleIndex(){int r=prefs.getInt("cycleSeconds",20);int[] v={10,15,20,30,45,60};int b=0,d=999;for(int i=0;i<v.length;i++){int z=Math.abs(v[i]-r);if(z<d){d=z;b=i;}}return b;}

    private void saveSettingsTab(String tab,List<View> f){SharedPreferences.Editor e=prefs.edit();try{
        if("HOME".equals(tab)){e.putString("place",((EditText)f.get(0)).getText().toString().trim());e.putLong("latBits",Double.doubleToRawLongBits(Double.parseDouble(((EditText)f.get(1)).getText().toString())));e.putLong("lonBits",Double.doubleToRawLongBits(Double.parseDouble(((EditText)f.get(2)).getText().toString())));e.putBoolean("miles",((Spinner)f.get(3)).getSelectedItemPosition()==0);e.putBoolean("fahrenheit",((Spinner)f.get(4)).getSelectedItemPosition()==1);e.putString("timezone",((EditText)f.get(5)).getText().toString().trim());e.putString("startup",String.valueOf(((Spinner)f.get(6)).getSelectedItem()));}
        else if("RADAR".equals(tab)){e.putInt("radarOrientation",((Spinner)f.get(0)).getSelectedItemPosition()*90);int[] mi={5,10,20,40,80,120,200};e.putInt("range",(int)Math.round(mi[((Spinner)f.get(1)).getSelectedItemPosition()]*1.609344));int ai=((Spinner)f.get(2)).getSelectedItemPosition();int[] ar={0,2,5,10,20,40};e.putBoolean("alertEnabled",ai>0);e.putInt("alertRange",(int)Math.round(ar[ai]*1.609344));int[] rr={10,20,30,60,120};e.putInt("refresh",rr[((Spinner)f.get(3)).getSelectedItemPosition()]);}
        else if("RADAR FX".equals(tab)){String[] st={"classic","tactical","pulse","dual","sonar","atc"};e.putString("radarStyle",st[((Spinner)f.get(0)).getSelectedItemPosition()]);e.putBoolean("trails",((CheckBox)f.get(1)).isChecked());e.putBoolean("alertEnabled",((CheckBox)f.get(2)).isChecked());e.putBoolean("aircraftAlerts",((CheckBox)f.get(3)).isChecked());e.putBoolean("militaryAlerts",((CheckBox)f.get(4)).isChecked());String[] th={"PHOSPHOR","AMBER","CYAN","RED","VIOLET","CUSTOM"};String chosen=th[((Spinner)f.get(5)).getSelectedItemPosition()];e.putString("theme",chosen);e.putString("customAccent",((EditText)f.get(6)).getText().toString().trim());}
        else if("SKY / DOCK".equals(tab)){
            e.putBoolean("autoCycle",((CheckBox)f.get(0)).isChecked());
            int[] d={10,15,20,30,45,60};e.putInt("cycleSeconds",d[((Spinner)f.get(1)).getSelectedItemPosition()]);
            e.putBoolean("cycleRadar",((CheckBox)f.get(2)).isChecked());
            e.putBoolean("cycleFlight",((CheckBox)f.get(3)).isChecked());
            e.putBoolean("cycleWeather",((CheckBox)f.get(4)).isChecked());
            e.putBoolean("cycleTime",((CheckBox)f.get(5)).isChecked());
            e.putBoolean("cycleSpace",((CheckBox)f.get(6)).isChecked());
            e.putBoolean("cycleSkyView",((CheckBox)f.get(7)).isChecked());
            e.putBoolean("cycleLaunches",((CheckBox)f.get(8)).isChecked());
            e.putInt("skyOrientation",((Spinner)f.get(9)).getSelectedItemPosition()*90);
            e.putBoolean("skyAircraft",((CheckBox)f.get(10)).isChecked());
            e.putBoolean("skySatellites",((CheckBox)f.get(11)).isChecked());
            e.putBoolean("skySmallBodies",((CheckBox)f.get(12)).isChecked());
            e.putBoolean("nightDim",((CheckBox)f.get(13)).isChecked());
            e.putBoolean("clock24",((CheckBox)f.get(14)).isChecked());
        }
        else if("DATA".equals(tab)){
            if(f.size()>=2){
                e.putString("openSkyClientId",((EditText)f.get(0)).getText().toString().trim());
                e.putString("openSkyClientSecret",((EditText)f.get(1)).getText().toString().trim());
                openSkyToken=null;openSkyTokenExpiry=0L;openSkyAuthState="CHECKING";ui.post(()->{validateOpenSkyCredentials();refreshWorldTraffic(true);});
            }
        }
        e.apply();applyTheme(prefs.getString("theme","PHOSPHOR"));previousAlertContacts=null;previousContacts=null;
    }catch(Exception ex){Toast.makeText(this,"Check the entered values",Toast.LENGTH_SHORT).show();}}

    private EditText field(String value,String hint){
        EditText e=new EditText(this);e.setText(value);e.setHint(hint);e.setTextColor(TEXT);e.setHintTextColor(DIM);e.setSingleLine(true);e.setBackground(panelBackground(false));e.setPadding(dp(8),dp(6),dp(8),dp(6));return e;
    }

    private Bitmap createPlanetPlaceholder(String name){
        Bitmap b=Bitmap.createBitmap(640,360,Bitmap.Config.ARGB_8888);Canvas c=new Canvas(b);Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);c.drawColor(Color.rgb(2,8,14));float cx=320,cy=165,r=82;p.setStyle(Paint.Style.FILL);int col="MARS".equals(name)?Color.rgb(220,95,65):("NEPTUNE".equals(name)?Color.rgb(75,120,230):("URANUS".equals(name)?Color.rgb(105,220,220):("SATURN".equals(name)?Color.rgb(220,195,130):CYAN)));p.setColor(col);c.drawCircle(cx,cy,r,p);if("SATURN".equals(name)){p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(10);c.drawOval(new RectF(cx-135,cy-34,cx+135,cy+34),p);}p.setStyle(Paint.Style.FILL);p.setColor(TEXT);p.setTypeface(Typeface.create(Typeface.MONOSPACE,Typeface.BOLD));p.setTextAlign(Paint.Align.CENTER);p.setTextSize(27);c.drawText(name,cx,320,p);p.setTextSize(13);p.setColor(DIM);c.drawText("PLANET REFERENCE SCHEMATIC • LIVE IMAGE LOOKUP FOLLOWS",cx,345,p);return b;
    }
    private void loadCelestialReferenceImage(final String name,final String kind){
        if(skyReferenceImage==null||name==null)return;io.execute(()->{Bitmap bm=null;try{String q=URLEncoder.encode(name+" "+kind,"UTF-8");JSONObject root=getJson("https://en.wikipedia.org/w/api.php?action=query&generator=search&gsrsearch="+q+"&gsrlimit=1&prop=pageimages&piprop=thumbnail&pithumbsize=900&format=json");JSONObject pages=root.optJSONObject("query")==null?null:root.optJSONObject("query").optJSONObject("pages");if(pages!=null){Iterator<String> it=pages.keys();if(it.hasNext()){JSONObject pg=pages.optJSONObject(it.next()),th=pg==null?null:pg.optJSONObject("thumbnail");if(th!=null){String u=th.optString("source");if(u.startsWith("https://"))bm=getBitmap(u);}}}}catch(Exception ignored){}final Bitmap out=bm;ui.post(()->{if(skyReferenceImage!=null&&out!=null)skyReferenceImage.setImageBitmap(out);});});
    }

    private Bitmap createSatellitePlaceholder(String name){
        Bitmap b=Bitmap.createBitmap(640,360,Bitmap.Config.ARGB_8888);Canvas c=new Canvas(b);Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);c.drawColor(Color.rgb(2,8,14));float cx=320,cy=168;p.setStyle(Paint.Style.FILL);p.setColor(Color.rgb(198,210,207));c.drawRoundRect(new RectF(cx-42,cy-22,cx+42,cy+22),8,8,p);p.setColor(Color.rgb(28,120,164));c.drawRect(cx-170,cy-55,cx-58,cy+55,p);c.drawRect(cx+58,cy-55,cx+170,cy+55,p);p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(3);p.setColor(CYAN);for(int x=-155;x<=155;x+=28){if(Math.abs(x)<58)continue;c.drawLine(cx+x,cy-55,cx+x,cy+55,p);}for(int y=-40;y<=40;y+=20){c.drawLine(cx-170,cy+y,cx-58,cy+y,p);c.drawLine(cx+58,cy+y,cx+170,cy+y,p);}p.setStyle(Paint.Style.FILL);p.setColor(TEXT);p.setTypeface(Typeface.create(Typeface.MONOSPACE,Typeface.BOLD));p.setTextAlign(Paint.Align.CENTER);p.setTextSize(24);c.drawText(name==null?"SATELLITE":name,cx,325,p);p.setTextSize(14);p.setColor(DIM);c.drawText("ORBITAL OBJECT SCHEMATIC • NOT TO SCALE",cx,348,p);return b;
    }

    private WeatherHistoryData loadWeatherHistory(double lat,double lon) throws Exception {
        String url="https://api.open-meteo.com/v1/forecast?latitude="+lat+"&longitude="+lon+
            "&past_hours=24&forecast_hours=1&hourly=temperature_2m,relative_humidity_2m,wind_speed_10m,rain&timezone=auto";
        JSONObject root;
        try { root=getJson(url); }
        catch(Exception liveError) { root=readJsonCache(url,6*60*60_000L); if(root==null)throw liveError; }
        JSONObject h=root.getJSONObject("hourly");
        JSONArray temp=h.getJSONArray("temperature_2m");
        JSONArray hum=h.getJSONArray("relative_humidity_2m");
        JSONArray wind=h.getJSONArray("wind_speed_10m");
        JSONArray rain=h.getJSONArray("rain");
        int n=Math.min(24,Math.min(Math.min(temp.length(),hum.length()),Math.min(wind.length(),rain.length())));
        double[] t=new double[n],hu=new double[n],w=new double[n],ra=new double[n];
        int start=Math.max(0,temp.length()-n-1);
        for(int i=0;i<n;i++){
            int k=start+i;
            t[i]=temp.optDouble(k,Double.NaN);
            hu[i]=hum.optDouble(k,Double.NaN);
            w[i]=wind.optDouble(k,Double.NaN);
            ra[i]=rain.optDouble(k,0);
            if(prefs.getBoolean("fahrenheit",false) && !Double.isNaN(t[i])) t[i]=t[i]*9/5+32;
        }
        return new WeatherHistoryData(t,hu,w,ra,prefs.getBoolean("fahrenheit",false));
    }

    private String loadWeatherWarnings(double lat,double lon) throws Exception {
        // Fire target is UK-first; use the official Met Office national warning RSS.
        try {
            String warningUrl="https://weather.metoffice.gov.uk/public/data/PWSCache/WarningsRSS/Region/UK";
            String xml;
            boolean cachedWarning=false;
            try { xml=getText(warningUrl); }
            catch(Exception liveError) { xml=readTextCache(warningUrl,6*60*60_000L); cachedWarning=xml!=null; if(xml==null)throw liveError; }
            ArrayList<String> titles=new ArrayList<String>();
            int pos=0;
            while(titles.size()<4){
                int a=xml.indexOf("<title>",pos); if(a<0)break;
                int b=xml.indexOf("</title>",a); if(b<0)break;
                String title=xml.substring(a+7,b).replace("<![CDATA[","").replace("]]>","").trim();
                pos=b+8;
                if(title.length()>0 && !title.toLowerCase(Locale.US).contains("weather warnings")) titles.add(title);
            }
            if(titles.isEmpty()) return "MET OFFICE // No warning entries returned. This does not guarantee no local hazard.";
            StringBuilder out=new StringBuilder(cachedWarning?"MET OFFICE CACHE // RECENT\n":"MET OFFICE // ACTIVE / RECENT\n");
            for(String t:titles)out.append("⚠  ").append(t).append("\n");
            return out.toString().trim();
        } catch(Exception primary) {
            // US official fallback is useful if the saved location is in the USA.
            if(lat>=24 && lat<=50 && lon>=-126 && lon<=-66){
                JSONObject nws=getJson("https://api.weather.gov/alerts/active?point="+lat+","+lon);
                JSONArray features=nws.optJSONArray("features");
                if(features==null||features.length()==0)return "NWS // No active alerts returned.";
                StringBuilder out=new StringBuilder("NWS FALLBACK\n");
                for(int i=0;i<Math.min(4,features.length());i++){
                    JSONObject p=features.optJSONObject(i).optJSONObject("properties");
                    if(p!=null)out.append("⚠  ").append(p.optString("headline",p.optString("event"))).append("\n");
                }
                return out.toString().trim();
            }
            throw primary;
        }
    }

    private String getText(String url) throws Exception {
        HttpsURLConnection con=(HttpsURLConnection)new URL(url).openConnection();
        con.setConnectTimeout(12000);con.setReadTimeout(18000);
        con.setRequestProperty("User-Agent","InTheSky-FireHD-Legacy/1.0");
        try{
            int code=con.getResponseCode();
            if(code<200||code>299)throw new IOException("HTTP "+code);
            BufferedReader br=new BufferedReader(new InputStreamReader(con.getInputStream(),"UTF-8"));
            StringBuilder sb=new StringBuilder();String line;while((line=br.readLine())!=null)sb.append(line).append('\n');
            String body=sb.toString();
            writeCache(cacheFile("text",url),body);
            return body;
        } finally {con.disconnect();}
    }

    private String distanceLabel(double km) {
        if(prefs.getBoolean("miles",true)) return String.format(Locale.US,"%.1f mi",km/1.609344);
        return String.format(Locale.US,"%.1f km",km);
    }

    private double currentLat(){return Double.longBitsToDouble(prefs.getLong("latBits",Double.doubleToRawLongBits(53.8008)));}
    private double currentLon(){return Double.longBitsToDouble(prefs.getLong("lonBits",Double.doubleToRawLongBits(-1.5491)));}
    private String locationLabel(){return prefs.getString("place","Leeds");}

    private Location lastLocation(){
        try{
            LocationManager m=(LocationManager)getSystemService(LOCATION_SERVICE);
            Location a=m.getLastKnownLocation(LocationManager.NETWORK_PROVIDER);
            Location b=m.getLastKnownLocation(LocationManager.GPS_PROVIDER);
            if(a==null)return b;if(b==null)return a;return a.getTime()>b.getTime()?a:b;
        }catch(Exception e){return null;}
    }

    private File cacheFile(String prefix,String key) {
        return new File(getCacheDir(),prefix+"_"+Integer.toHexString(key.hashCode())+".cache");
    }

    private void writeCache(File file,String body) {
        try{
            FileOutputStream out=new FileOutputStream(file);
            out.write(body.getBytes("UTF-8"));
            out.close();
        }catch(Exception ignored){}
    }

    private String readCache(File file,long maxAgeMs) {
        try{
            if(!file.exists() || System.currentTimeMillis()-file.lastModified()>maxAgeMs)return null;
            BufferedReader br=new BufferedReader(new InputStreamReader(new FileInputStream(file),"UTF-8"));
            StringBuilder sb=new StringBuilder();String line;
            while((line=br.readLine())!=null)sb.append(line).append('\n');
            br.close();
            return sb.toString();
        }catch(Exception e){return null;}
    }

    private JSONObject readJsonCache(String url,long maxAgeMs) {
        try{
            String body=readCache(cacheFile("json",url),maxAgeMs);
            return body==null?null:new JSONObject(body);
        }catch(Exception e){return null;}
    }

    private String readTextCache(String url,long maxAgeMs) {
        return readCache(cacheFile("text",url),maxAgeMs);
    }

    private JSONObject getJson(String url) throws Exception {
        HttpsURLConnection c=(HttpsURLConnection)new URL(url).openConnection();
        if (url.startsWith("https://api.met.no/") && weatherSslFactory != null) {
            c.setSSLSocketFactory(weatherSslFactory);
        }
        c.setConnectTimeout(12000);c.setReadTimeout(18000);c.setRequestProperty("Accept","application/json");
        c.setRequestProperty("User-Agent","InTheSky-FireHD-Legacy/1.0");
        try{
            int code=c.getResponseCode();
            if(code<200||code>299)throw new IOException("HTTP "+code);
            BufferedReader br=new BufferedReader(new InputStreamReader(c.getInputStream(),"UTF-8"));
            StringBuilder sb=new StringBuilder();String line;while((line=br.readLine())!=null)sb.append(line);
            String body=sb.toString();
            JSONObject parsed=new JSONObject(body);
            writeCache(cacheFile("json",url),body);
            return parsed;
        }finally{c.disconnect();}
    }

    private Bitmap getBitmap(String url) throws Exception {
        if(url==null||url.length()==0)return null;
        File f=new File(getCacheDir(),"img_"+Integer.toHexString(url.hashCode())+".jpg");
        if(f.exists())return BitmapFactory.decodeFile(f.getAbsolutePath());
        HttpsURLConnection c=(HttpsURLConnection)new URL(url).openConnection();c.setConnectTimeout(12000);c.setReadTimeout(18000);
        try{
            InputStream in=c.getInputStream();Bitmap b=BitmapFactory.decodeStream(in);in.close();
            if(b!=null){FileOutputStream out=new FileOutputStream(f);b.compress(Bitmap.CompressFormat.JPEG,82,out);out.close();}
            return b;
        }finally{c.disconnect();}
    }

    private Bitmap createAircraftPlaceholder(Aircraft a) {
        Bitmap b=Bitmap.createBitmap(640,300,Bitmap.Config.ARGB_8888);
        Canvas canvas=new Canvas(b);
        canvas.drawColor(Color.rgb(3,12,8));
        Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setColor(GREEN);p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(8);
        float cx=320,cy=135;
        Path q=new Path();
        q.moveTo(cx,45);q.lineTo(cx+22,105);q.lineTo(cx+150,150);q.lineTo(cx+150,170);
        q.lineTo(cx+25,150);q.lineTo(cx+18,225);q.lineTo(cx,240);
        q.lineTo(cx-18,225);q.lineTo(cx-25,150);q.lineTo(cx-150,170);q.lineTo(cx-150,150);
        q.lineTo(cx-22,105);q.close();
        canvas.drawPath(q,p);
        p.setStyle(Paint.Style.FILL);p.setTextAlign(Paint.Align.CENTER);p.setTypeface(Typeface.MONOSPACE);
        p.setTextSize(28);p.setColor(TEXT);
        canvas.drawText(a.type.length()>0?a.type:"AIRCRAFT REFERENCE",cx,282,p);
        return b;
    }

    private String airportLabel(JSONObject airport) {
        if (airport == null) return "--";
        String name = airport.optString("name");
        String city = airport.optString("municipality");
        String iata = airport.optString("iata_code");
        if (iata.length() == 0) iata = airport.optString("iata");
        String icao = airport.optString("icao_code");
        if (icao.length() == 0) icao = airport.optString("icao");
        String code = iata.length() > 0 ? iata : (icao.length() > 0 ? icao : "---");
        String place = city.length() > 0 ? city : name;
        return code + "  " + (place.length() > 0 ? place : "--");
    }

    private String shortIso(String iso){if(iso==null)return "--";return iso.length()>=16?iso.substring(11,16):iso;}
    private String shortError(Exception e){String s=e.getMessage();return s==null?e.getClass().getSimpleName():s;}
    private String empty(String s){return s==null||s.trim().length()==0?"--":s;}
    private String nonBlank(String s){return s==null||s.trim().length()==0?"--":s.trim();}
    private String fmt(Double d){return d==null?"--":String.format(Locale.US,"%.0f",d);}
    private String fmtInt(Integer i){return i==null?"--":String.valueOf(i);}
    private int dp(int v){return (int)(v*getResources().getDisplayMetrics().density+0.5f);}

    @Override protected void onDestroy(){
        if(radarTick!=null)ui.removeCallbacks(radarTick);if(clockTick!=null)ui.removeCallbacks(clockTick);if(pageCycleTick!=null)ui.removeCallbacks(pageCycleTick);if(headerTick!=null)ui.removeCallbacks(headerTick);if(flightFollowTick!=null)ui.removeCallbacks(flightFollowTick);if(weatherRefreshTick!=null)ui.removeCallbacks(weatherRefreshTick);if(worldTrafficTick!=null)ui.removeCallbacks(worldTrafficTick);
        io.shutdownNow();super.onDestroy();
    }


    public class LunarInstrumentView extends View {
        private final Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Bitmap moon;
        private final double syn=29.53058867;
        private final long ref=947182440000L;

        LunarInstrumentView(Context c){
            super(c);
            moon=BitmapFactory.decodeResource(getResources(),R.drawable.moon_reference);
            p.setTypeface(Typeface.MONOSPACE);
            setBackgroundColor(Color.TRANSPARENT);
        }

        private double lunarAge(long now){
            double age=(((now-ref)/86400000.0)%syn+syn)%syn;
            return age;
        }
        private double lunarIllum(double age){
            return (1-Math.cos(2*Math.PI*age/syn))/2.0;
        }
        private String phaseName(double age){
            if(age<1.8)return "NEW MOON";
            if(age<7.4)return "WAXING CRESCENT";
            if(age<9.2)return "FIRST QUARTER";
            if(age<14.8)return "WAXING GIBBOUS";
            if(age<16.6)return "FULL MOON";
            if(age<22.1)return "WANING GIBBOUS";
            if(age<23.9)return "LAST QUARTER";
            return "WANING CRESCENT";
        }

        private Date[] riseSet(){
            try{
                final double lat=currentLat(),lon=currentLon();
                Calendar cal=Calendar.getInstance();
                cal.set(Calendar.HOUR_OF_DAY,0);cal.set(Calendar.MINUTE,0);
                cal.set(Calendar.SECOND,0);cal.set(Calendar.MILLISECOND,0);
                long dayStart=cal.getTimeInMillis();
                Long rise=null,set=null;
                double prevAlt=moonHorizontalApprox(dayStart,lat,lon)[1];
                long prevT=dayStart;
                for(int m=10;m<=24*60;m+=10){
                    long t=dayStart+m*60_000L;
                    double alt=moonHorizontalApprox(t,lat,lon)[1];
                    if(prevAlt<0&&alt>=0&&rise==null){
                        double f=(0-prevAlt)/(alt-prevAlt);
                        rise=prevT+(long)((t-prevT)*Math.max(0,Math.min(1,f)));
                    }
                    if(prevAlt>=0&&alt<0&&set==null){
                        double f=(0-prevAlt)/(alt-prevAlt);
                        set=prevT+(long)((t-prevT)*Math.max(0,Math.min(1,f)));
                    }
                    prevAlt=alt;prevT=t;
                }
                return new Date[]{rise==null?null:new Date(rise),set==null?null:new Date(set)};
            }catch(Exception e){return new Date[]{null,null};}
        }

        private ArrayList<String[]> nextPrimaryPhases(long now){
            ArrayList<String[]> out=new ArrayList<String[]>();
            double cycle=(now-ref)/(syn*86400000.0);
            long base=(long)Math.floor(cycle);
            double[] f={0,.25,.5,.75,1.0};
            String[] n={"NEW MOON","FIRST QUARTER","FULL MOON","LAST QUARTER","NEW MOON"};
            SimpleDateFormat df=new SimpleDateFormat("EEE dd MMM  HH:mm",Locale.UK);
            for(int k=0;k<3&&out.size()<4;k++){
                long cycleBase=base+k;
                for(int i=0;i<f.length&&out.size()<4;i++){
                    long t=ref+(long)((cycleBase+f[i])*syn*86400000.0);
                    if(t>now+60_000)out.add(new String[]{n[i],df.format(new Date(t))});
                }
            }
            return out;
        }

        private void text(Canvas c,String str,float x,float y,float size,int color,Paint.Align align,boolean bold){
            p.setStyle(Paint.Style.FILL);p.setColor(color);p.setTextSize(size);
            p.setTextAlign(align);
            p.setTypeface(Typeface.create(Typeface.MONOSPACE,bold?Typeface.BOLD:Typeface.NORMAL));
            c.drawText(str,x,y,p);
        }

        @Override protected void onDraw(Canvas c){
            super.onDraw(c);
            float w=getWidth(),h=getHeight();
            if(w<=0||h<=0)return;

            long now=System.currentTimeMillis();
            double age=lunarAge(now);
            double illumination=lunarIllum(age);
            boolean waxing=age<syn/2.0;
            double fraction=age/syn;
            double k=Math.cos(2*Math.PI*fraction);

            // The reference layout puts the Moon high in the panel, leaving the
            // lower half for clearly separated instrument data.
            float moonR=Math.min(w*.115f,h*.145f);
            moonR=Math.max(dp(30),Math.min(moonR,dp(47)));
            float cx=w*.50f,cy=Math.max(moonR+dp(5),h*.20f);
            float left=cx-moonR,top=cy-moonR,size=moonR*2f;
            RectF dst=new RectF(left,top,left+size,top+size);

            Path circle=new Path();circle.addCircle(cx,cy,moonR,Path.Direction.CW);
            Path lit=new Path();
            Path half=new Path();
            half.addRect(waxing?cx:left,top,waxing?left+size:cx,top+size,Path.Direction.CW);
            half.op(circle,Path.Op.INTERSECT);
            float rx=(float)(Math.abs(k)*moonR);
            Path ellipse=new Path();
            ellipse.addOval(new RectF(cx-rx,top,cx+rx,top+size),Path.Direction.CW);
            if(k>=0){
                Path same=new Path(ellipse);same.op(half,Path.Op.INTERSECT);
                lit.set(half);lit.op(same,Path.Op.DIFFERENCE);
            }else{
                Path opposite=new Path();
                opposite.addRect(waxing?left:cx,top,waxing?cx:left+size,top+size,Path.Direction.CW);
                opposite.op(circle,Path.Op.INTERSECT);
                Path extra=new Path(ellipse);extra.op(opposite,Path.Op.INTERSECT);
                lit.set(half);lit.op(extra,Path.Op.UNION);
            }

            p.setStyle(Paint.Style.FILL);
            c.save();c.clipPath(circle);
            if(moon!=null){p.setAlpha(255);p.setFilterBitmap(true);c.drawBitmap(moon,null,dst,p);p.setFilterBitmap(false);}
            else{p.setColor(Color.rgb(165,174,168));c.drawCircle(cx,cy,moonR,p);}
            c.restore();
            Path shadow=new Path(circle);shadow.op(lit,Path.Op.DIFFERENCE);
            p.setColor(Color.argb(155,0,5,4));c.drawPath(shadow,p);

            // Only a very subtle rim like the supplied reference, not the old blue ring.
            p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(dp(1));p.setColor(Color.argb(115,130,160,155));
            c.drawCircle(cx,cy,moonR,p);

            float illumY=cy+moonR+dp(14);
            text(c,String.format(Locale.US,"%.0f%% ILLUMINATED",illumination*100),cx,illumY,dp(7),CYAN,Paint.Align.CENTER,false);

            float phaseY=illumY+dp(24);
            text(c,phaseName(age),cx,phaseY,dp(13),TEXT,Paint.Align.CENTER,true);

            float statsY=phaseY+dp(19);
            text(c,String.format(Locale.US,"%.0f%% ILLUMINATED  •  AGE %.1f DAYS",illumination*100,age),
                 cx,statsY,dp(9),TEXT,Paint.Align.CENTER,true);

            float dirY=statsY+dp(17);
            text(c,waxing?"→ WAXING":"← WANING",cx,dirY,dp(9),TEXT,Paint.Align.CENTER,true);

            float divider1=dirY+dp(12);
            p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(1);p.setColor(Color.argb(125,Color.red(CYAN),Color.green(CYAN),Color.blue(CYAN)));
            c.drawLine(dp(8),divider1,w-dp(8),divider1,p);

            float localHead=divider1+dp(14);
            text(c,"LOCAL LUNAR EVENTS",cx,localHead,dp(7),CYAN,Paint.Align.CENTER,false);

            Date[] rs=riseSet();
            SimpleDateFormat eventFmt=new SimpleDateFormat("EEE  HH:mm",Locale.UK);
            String rise=rs[0]==null?"--  --:--":eventFmt.format(rs[0]);
            String set=rs[1]==null?"--  --:--":eventFmt.format(rs[1]);
            float riseY=localHead+dp(17),setY=riseY+dp(18);

            text(c,"MOONRISE",dp(10),riseY,dp(9),AMBER,Paint.Align.LEFT,true);
            text(c,rise,w-dp(10),riseY,dp(9),TEXT,Paint.Align.RIGHT,true);
            text(c,"MOONSET",dp(10),setY,dp(9),AMBER,Paint.Align.LEFT,true);
            text(c,set,w-dp(10),setY,dp(9),TEXT,Paint.Align.RIGHT,true);

            float divider2=setY+dp(11);
            p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(1);
            p.setColor(Color.argb(125,Color.red(CYAN),Color.green(CYAN),Color.blue(CYAN)));
            c.drawLine(dp(8),divider2,w-dp(8),divider2,p);
            p.setTextAlign(Paint.Align.LEFT);
        }
    }

    public static class MoonPhaseView extends View {
        private final Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);private Bitmap moon;MoonPhaseView(Context c){super(c);moon=BitmapFactory.decodeResource(getResources(),R.drawable.moon_reference);setBackgroundColor(Color.TRANSPARENT);}
        @Override protected void onDraw(Canvas c){super.onDraw(c);float size=Math.min(getWidth(),getHeight())*.94f,left=(getWidth()-size)/2f,top=Math.max(0f,(getHeight()-size)/2f-12f),cx=left+size/2f,cy=top+size/2f,r=size/2f;RectF dst=new RectF(left,top,left+size,top+size);long ref=947182440000L;double syn=29.53058867;double age=(((System.currentTimeMillis()-ref)/86400000.0)%syn+syn)%syn;double fraction=age/syn;double illumination=(1-Math.cos(2*Math.PI*fraction))/2.0;boolean waxing=fraction<.5;double k=Math.cos(2*Math.PI*fraction);
            Path circle=new Path();circle.addCircle(cx,cy,r,Path.Direction.CW);Path lit=new Path();Path half=new Path();half.addRect(waxing?cx:left,top,waxing?left+size:cx,top+size,Path.Direction.CW);half.op(circle,Path.Op.INTERSECT);float rx=(float)(Math.abs(k)*r);Path ellipse=new Path();ellipse.addOval(new RectF(cx-rx,top,cx+rx,top+size),Path.Direction.CW);if(k>=0){Path same=new Path(ellipse);same.op(half,Path.Op.INTERSECT);lit.set(half);lit.op(same,Path.Op.DIFFERENCE);}else{Path opposite=new Path();opposite.addRect(waxing?left:cx,top,waxing?cx:left+size,top+size,Path.Direction.CW);opposite.op(circle,Path.Op.INTERSECT);Path extra=new Path(ellipse);extra.op(opposite,Path.Op.INTERSECT);lit.set(half);lit.op(extra,Path.Op.UNION);}p.setStyle(Paint.Style.FILL);
            c.save();c.clipPath(circle);
            if(moon!=null){p.setAlpha(255);c.drawBitmap(moon,null,dst,p);}
            else{p.setColor(Color.rgb(165,174,168));c.drawCircle(cx,cy,r,p);}
            c.restore();
            Path shadow=new Path(circle);shadow.op(lit,Path.Op.DIFFERENCE);
            p.setColor(Color.argb(145,0,5,4));c.drawPath(shadow,p);
            p.setTextAlign(Paint.Align.LEFT);}
    }

    public static class DaylightView extends View {
        private final Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);private int sunrise=6*60+20,sunset=19*60+40,nowMin=12*60;DaylightView(Context c){super(c);p.setTypeface(Typeface.MONOSPACE);}
        void setTimes(int r,int s){sunrise=r;sunset=s;invalidate();}void setNow(Date d){Calendar c=Calendar.getInstance();c.setTime(d);nowMin=c.get(Calendar.HOUR_OF_DAY)*60+c.get(Calendar.MINUTE);invalidate();}
        @Override protected void onDraw(Canvas c){
            super.onDraw(c);float l=34,r=getWidth()-34,y=getHeight()*.50f;
            p.setStrokeCap(Paint.Cap.ROUND);p.setStrokeWidth(28);p.setColor(Color.rgb(58,119,190));c.drawLine(l,y,r,y,p);
            float x1=l+(r-l)*sunrise/1440f,x2=l+(r-l)*sunset/1440f;p.setColor(Color.rgb(255,199,82));c.drawLine(x1,y,x2,y,p);
            float xn=l+(r-l)*nowMin/1440f;p.setStrokeWidth(4);p.setColor(CYAN);c.drawLine(xn,y-38,xn,y+40,p);
            p.setStyle(Paint.Style.FILL);p.setTextAlign(Paint.Align.CENTER);p.setTypeface(Typeface.create(Typeface.MONOSPACE,Typeface.BOLD));
            p.setTextSize(17);p.setColor(TEXT);c.drawText(String.format(Locale.US,"SUNRISE %02d:%02d    •    SUNSET %02d:%02d    •    DAYLIGHT %dh %02dm",sunrise/60,sunrise%60,sunset/60,sunset%60,(sunset-sunrise)/60,(sunset-sunrise)%60),getWidth()/2f,30,p);
            p.setTypeface(Typeface.MONOSPACE);p.setTextSize(12.5f);p.setColor(CYAN);
            for(int h=0;h<=24;h+=6){float x=l+(r-l)*h/24f;c.drawText(String.format(Locale.US,"%02d",h),x,y+48,p);}
            p.setTextSize(12.5f);p.setColor(DIM);c.drawText("BLUE NIGHT    •    AMBER DAYLIGHT    •    CYAN CURRENT TIME",getWidth()/2f,getHeight()-14,p);
            p.setTextAlign(Paint.Align.LEFT);
        }
    }

    static final class GeoPlace {
        final String name; final double lat,lon; final int population;
        GeoPlace(String n,double a,double o,int p){name=n;lat=a;lon=o;population=p;}
    }
    static final class GeoAirport {
        final String ident,code,name,type; final double lat,lon;
        GeoAirport(String i,String c,String n,double a,double o,String t){ident=i;code=c;name=n;lat=a;lon=o;type=t;}
    }
    static final class GeoRunway {
        final String airport,ref; final double la1,lo1,la2,lo2;
        GeoRunway(String a,String r,double x1,double y1,double x2,double y2){airport=a;ref=r;la1=x1;lo1=y1;la2=x2;lo2=y2;}
    }

    static final class LocalGeoData {
        private static final ArrayList<GeoPlace> places=new ArrayList<GeoPlace>();
        private static final ArrayList<GeoAirport> airports=new ArrayList<GeoAirport>();
        private static final ArrayList<GeoRunway> runways=new ArrayList<GeoRunway>();
        private static final ExecutorService loader=Executors.newSingleThreadExecutor();
        private static volatile boolean loading=false,loaded=false;

        static boolean isLoaded(){return loaded;}
        static void ensureLoaded(final Context ctx,final View owner){
            if(loaded){if(owner!=null)owner.postInvalidate();return;}
            synchronized(LocalGeoData.class){
                if(loading)return;loading=true;
                loader.execute(()->{
                    try{
                        readPlaces(ctx);readAirports(ctx);readRunways(ctx);loaded=true;
                    }catch(Exception ignored){}finally{
                        loading=false;if(owner!=null)owner.postInvalidate();
                    }
                });
            }
        }
        private static void readPlaces(Context ctx)throws Exception{
            BufferedReader br=new BufferedReader(new InputStreamReader(ctx.getAssets().open("cities_compact.tsv"),"UTF-8"),32768);
            String line;while((line=br.readLine())!=null){String[] f=line.split("\t",-1);if(f.length<4)continue;try{places.add(new GeoPlace(f[0],Double.parseDouble(f[1]),Double.parseDouble(f[2]),Integer.parseInt(f[3])));}catch(Exception ignored){}}
            br.close();
        }
        private static void readAirports(Context ctx)throws Exception{
            BufferedReader br=new BufferedReader(new InputStreamReader(ctx.getAssets().open("airports_compact.tsv"),"UTF-8"),32768);
            String line;while((line=br.readLine())!=null){String[] f=line.split("\t",-1);if(f.length<6)continue;try{airports.add(new GeoAirport(f[0],f[1],f[2],Double.parseDouble(f[3]),Double.parseDouble(f[4]),f[5]));}catch(Exception ignored){}}
            br.close();
        }
        private static void readRunways(Context ctx)throws Exception{
            BufferedReader br=new BufferedReader(new InputStreamReader(ctx.getAssets().open("runways_compact.tsv"),"UTF-8"),32768);
            String line;while((line=br.readLine())!=null){String[] f=line.split("\t",-1);if(f.length<6)continue;try{runways.add(new GeoRunway(f[0],f[1],Double.parseDouble(f[2]),Double.parseDouble(f[3]),Double.parseDouble(f[4]),Double.parseDouble(f[5])));}catch(Exception ignored){}}
            br.close();
        }
        static double[] distanceBearing(double lat1,double lon1,double lat2,double lon2){
            double r=6371.0088,p1=Math.toRadians(lat1),p2=Math.toRadians(lat2),dp=Math.toRadians(lat2-lat1),dl=Math.toRadians(lon2-lon1);
            double aa=Math.sin(dp/2)*Math.sin(dp/2)+Math.cos(p1)*Math.cos(p2)*Math.sin(dl/2)*Math.sin(dl/2);
            double d=2*r*Math.asin(Math.sqrt(Math.max(0,Math.min(1,aa))));
            double y=Math.sin(dl)*Math.cos(p2),x=Math.cos(p1)*Math.sin(p2)-Math.sin(p1)*Math.cos(p2)*Math.cos(dl);
            return new double[]{d,(Math.toDegrees(Math.atan2(y,x))+360)%360};
        }
        static ArrayList<GeoPlace> nearbyPlaces(double lat,double lon,double radiusKm,int limit){
            ArrayList<GeoPlace> out=new ArrayList<GeoPlace>();if(!loaded)return out;
            double latSpan=radiusKm/110.574,lonSpan=radiusKm/Math.max(12,111.320*Math.abs(Math.cos(Math.toRadians(lat))));
            for(GeoPlace q:places){if(Math.abs(q.lat-lat)>latSpan||Math.abs(q.lon-lon)>lonSpan)continue;if(distanceBearing(lat,lon,q.lat,q.lon)[0]<=radiusKm)out.add(q);}
            Collections.sort(out,(a,b)->{int p=Integer.compare(b.population,a.population);if(p!=0)return p;return Double.compare(distanceBearing(lat,lon,a.lat,a.lon)[0],distanceBearing(lat,lon,b.lat,b.lon)[0]);});
            if(out.size()>limit)return new ArrayList<GeoPlace>(out.subList(0,limit));return out;
        }
        static ArrayList<GeoAirport> nearbyAirports(double lat,double lon,double radiusKm,int limit){
            ArrayList<GeoAirport> out=new ArrayList<GeoAirport>();if(!loaded)return out;
            double latSpan=radiusKm/110.574,lonSpan=radiusKm/Math.max(12,111.320*Math.abs(Math.cos(Math.toRadians(lat))));
            for(GeoAirport q:airports){if(Math.abs(q.lat-lat)>latSpan||Math.abs(q.lon-lon)>lonSpan)continue;if(distanceBearing(lat,lon,q.lat,q.lon)[0]<=radiusKm)out.add(q);}
            Collections.sort(out,(a,b)->Double.compare(distanceBearing(lat,lon,a.lat,a.lon)[0],distanceBearing(lat,lon,b.lat,b.lon)[0]));
            if(out.size()>limit)return new ArrayList<GeoAirport>(out.subList(0,limit));return out;
        }
        static ArrayList<GeoRunway> runwaysFor(ArrayList<GeoAirport> selected,int limit){
            ArrayList<GeoRunway> out=new ArrayList<GeoRunway>();if(!loaded||selected==null||selected.isEmpty())return out;
            HashSet<String> ids=new HashSet<String>();for(GeoAirport a:selected)ids.add(a.ident);
            for(GeoRunway r:runways){if(ids.contains(r.airport)){out.add(r);if(out.size()>=limit)break;}}
            return out;
        }
    }

    public class FlightMapView extends View {
        private final Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Bitmap base;
        private final Bitmap regionalBase;
        private final ArrayList<WorldContact> globalTraffic=new ArrayList<WorldContact>();
        private String globalSource="";
        private Double homeLat,homeLon,liveLat,liveLon,oLat,oLon,dLat,dLon;
        private String liveLabel="AIRCRAFT",liveKind="FIXED";
        private boolean liveMilitary=false;
        private double liveTrack=0;

        // 0 follow, 1 remaining, 2 full route, 3 world
        private int mode=3;
        private float cx=.5f,cy=.5f,cw=1f;
        private float fromX=.5f,fromY=.5f,fromW=1f,toX=.5f,toY=.5f,toW=1f;
        private long animStart=0;
        private static final long ANIM_MS=560L;
        private float manualZoom=1f;

        private ArrayList<GeoPlace> labelPlaces=new ArrayList<GeoPlace>();
        private ArrayList<GeoAirport> labelAirports=new ArrayList<GeoAirport>();
        private float labelCx=-9,labelCy=-9,labelW=-9;

        FlightMapView(Context c){
            super(c);
            base=BitmapFactory.decodeResource(getResources(),R.drawable.flight_aviation_world);
            regionalBase=BitmapFactory.decodeResource(getResources(),R.drawable.flight_aviation_europe);
            p.setTypeface(Typeface.MONOSPACE);
            setBackgroundColor(Color.rgb(2,17,16));
            LocalGeoData.ensureLoaded(c,this);
        }

        boolean isWorldView(){return mode==3;}

        void setWorldTraffic(List<WorldContact> items,String source){
            synchronized(globalTraffic){globalTraffic.clear();if(items!=null)globalTraffic.addAll(items);}
            globalSource=source==null?"":source;invalidate();
        }

        void setHome(double lat,double lon){homeLat=lat;homeLon=lon;invalidate();}

        void setRoute(double a,double b,double c,double d){
            oLat=a;oLon=b;dLat=c;dLon=d;
            if(mode==1)viewPath();else if(mode==2)viewRoute();
            invalidate();
        }
        void clearRoute(){oLat=oLon=dLat=dLon=null;invalidate();}

        void setLiveAircraft(double lat,double lon,String label,boolean focus){
            liveLat=lat;liveLon=lon;liveLabel=label==null?"AIRCRAFT":label;
            if(focus){mode=0;manualZoom=1f;animateTo(targetCamera());}
            invalidate();
        }
        void setLiveAircraft(Aircraft a,boolean focus){
            if(a==null)return;
            liveLat=a.lat;liveLon=a.lon;liveLabel=a.callsign==null?"AIRCRAFT":a.callsign;
            liveKind=a.kind();liveMilitary=a.military;liveTrack=a.track;
            if(focus){mode=0;manualZoom=1f;animateTo(targetCamera());}
            invalidate();
        }
        void clearLiveAircraft(){liveLat=liveLon=null;viewWorld();}

        void viewFollow(){mode=0;manualZoom=1f;animateTo(targetCamera());}
        void viewPath(){mode=1;manualZoom=1f;animateTo(targetCamera());}
        void viewRoute(){mode=2;manualZoom=1f;animateTo(targetCamera());}
        void viewWorld(){mode=3;manualZoom=1f;animateTo(new float[]{.5f,.5f,1f});}
        void zoomBy(float f){
            // Existing buttons pass .5 for zoom-out and 2 for zoom-in.
            manualZoom=Math.max(.35f,Math.min(8f,manualZoom*f));
            animateTo(targetCamera());
        }

        private float wx(double lon){return (float)((lon+180.0)/360.0);}
        private float wy(double lat){
            lat=Math.max(-85.05112878,Math.min(85.05112878,lat));
            double r=Math.toRadians(lat);
            return (float)((1.0-Math.log(Math.tan(r)+1.0/Math.cos(r))/Math.PI)/2.0);
        }
        private double invLat(float y){
            return Math.toDegrees(Math.atan(Math.sinh(Math.PI*(1-2*y))));
        }
        private float wrapMid(float a,float b){
            float aa=a,bb=b;
            if(Math.abs(bb-aa)>.5f){if(aa<bb)aa+=1f;else bb+=1f;}
            float m=(aa+bb)*.5f;return m-(float)Math.floor(m);
        }
        private float wrapDx(float a,float b){
            float d=Math.abs(a-b);return Math.min(d,1f-d);
        }

        private float[] fit(double lat1,double lon1,double lat2,double lon2,float minimum){
            float x1=wx(lon1),x2=wx(lon2),y1=wy(lat1),y2=wy(lat2);
            float dx=wrapDx(x1,x2),dy=Math.abs(y2-y1);
            float aspect=getWidth()>0?getHeight()/(float)getWidth():.62f;
            // Width is the single camera scale. Height derives from actual view aspect.
            float needW=Math.max(dx*1.42f,dy/Math.max(.08f,aspect)*1.42f);
            float w=Math.max(minimum,needW);
            w=Math.min(1f,w);
            return new float[]{wrapMid(x1,x2),(y1+y2)*.5f,w};
        }

        private float[] targetCamera(){
            if(mode==3)return new float[]{.5f,.5f,1f};
            if(liveLat==null||liveLon==null)return new float[]{.5f,.5f,1f};

            float[] t;
            if(mode==0){
                // FOLLOW: aircraft dead-centre. Roughly regional scale.
                t=new float[]{wx(liveLon),wy(liveLat),.024f};
            }else if(mode==1&&dLat!=null&&dLon!=null){
                // REMAINING: current aircraft -> destination. No giant arbitrary minimum.
                t=fit(liveLat,liveLon,dLat,dLon,.022f);
            }else if(mode==2&&oLat!=null&&oLon!=null&&dLat!=null&&dLon!=null){
                // FULL: origin -> destination. Short UK routes stay in the UK;
                // intercontinental flights naturally widen to fit.
                t=fit(oLat,oLon,dLat,dLon,.022f);
            }else t=new float[]{wx(liveLon),wy(liveLat),.024f};

            t[2]=Math.max(.006f,Math.min(1f,t[2]/manualZoom));
            return t;
        }

        private void animateTo(float[] t){
            updateAnim();
            fromX=cx;fromY=cy;fromW=cw;
            toX=t[0];toY=t[1];toW=t[2];
            animStart=System.currentTimeMillis();
            postInvalidateOnAnimation();
        }
        private float ease(float t){
            t=Math.max(0,Math.min(1,t));
            return t<.5f?4*t*t*t:1-(float)Math.pow(-2*t+2,3)/2f;
        }
        private void updateAnim(){
            if(animStart==0)return;
            float t=(System.currentTimeMillis()-animStart)/(float)ANIM_MS;
            if(t>=1){cx=toX;cy=toY;cw=toW;animStart=0;return;}
            float e=ease(t);
            float dx=toX-fromX;
            if(dx>.5f)dx-=1f;else if(dx<-.5f)dx+=1f;
            cx=fromX+dx*e;cx=cx-(float)Math.floor(cx);
            cy=fromY+(toY-fromY)*e;
            cw=fromW+(toW-fromW)*e;
            postInvalidateOnAnimation();
        }

        private float cameraH(){return cw*(getWidth()>0?getHeight()/(float)getWidth():.62f);}
        private float px(float v){return v*getResources().getDisplayMetrics().density;}

        private PointF screen(double lat,double lon){
            float x=wx(lon),y=wy(lat),dx=x-cx;
            if(dx>.5f)dx-=1f;else if(dx<-.5f)dx+=1f;
            float h=cameraH();
            return new PointF(getWidth()*.5f+dx/cw*getWidth(),
                              getHeight()*.5f+(y-cy)/h*getHeight());
        }

        private void drawBase(Canvas c){
            if(base==null||base.isRecycled())return;

            if(mode==3){
                p.setFilterBitmap(true);
                c.drawBitmap(base,null,new Rect(0,0,getWidth(),getHeight()),p);
                p.setFilterBitmap(false);
                return;
            }

            float h=cameraH();
            float l=cx-cw*.5f,r=cx+cw*.5f,t=cy-h*.5f,b=cy+h*.5f;

            // High-resolution regional map for UK/Europe/North Africa.
            // It is still a simple bundled bitmap, so there is zero loading/network cost.
            float rx0=wx(-35.0),rx1=wx(45.0);
            float ry0=wy(72.0),ry1=wy(20.0);
            boolean regionalOk=regionalBase!=null&&!regionalBase.isRecycled()
                    && l>=rx0 && r<=rx1 && t>=ry0 && b<=ry1 && cw<=.22f;

            if(regionalOk){
                int bw=regionalBase.getWidth(),bh=regionalBase.getHeight();
                int sl=(int)((l-rx0)/(rx1-rx0)*bw);
                int sr=(int)((r-rx0)/(rx1-rx0)*bw);
                int st=(int)((t-ry0)/(ry1-ry0)*bh);
                int sb=(int)((b-ry0)/(ry1-ry0)*bh);
                sl=Math.max(0,Math.min(bw-1,sl));sr=Math.max(sl+1,Math.min(bw,sr));
                st=Math.max(0,Math.min(bh-1,st));sb=Math.max(st+1,Math.min(bh,sb));
                p.setFilterBitmap(true);
                c.drawBitmap(regionalBase,new Rect(sl,st,sr,sb),new Rect(0,0,getWidth(),getHeight()),p);
                p.setFilterBitmap(false);
                return;
            }

            // World fallback for long routes and regions outside the detailed pack.
            int bw=base.getWidth(),bh=base.getHeight();
            if(l>=0&&r<=1){
                Rect src=new Rect((int)(l*bw),(int)(t*bh),(int)(r*bw),(int)(b*bh));
                src.left=Math.max(0,Math.min(bw-1,src.left));src.right=Math.max(src.left+1,Math.min(bw,src.right));
                src.top=Math.max(0,Math.min(bh-1,src.top));src.bottom=Math.max(src.top+1,Math.min(bh,src.bottom));
                p.setFilterBitmap(true);
                c.drawBitmap(base,src,new Rect(0,0,getWidth(),getHeight()),p);
                p.setFilterBitmap(false);
            }else{
                // Dateline-safe two-piece world draw.
                float leftNorm=l<0?l+1:l,rightNorm=r>1?r-1:r;
                float firstW=l<0?(-l)/cw:(1-l)/cw;
                p.setFilterBitmap(true);
                if(l<0){
                    Rect a=new Rect((int)(leftNorm*bw),(int)(t*bh),bw,(int)(b*bh));
                    Rect bb=new Rect(0,(int)(t*bh),(int)(rightNorm*bw),(int)(b*bh));
                    c.drawBitmap(base,a,new Rect(0,0,(int)(firstW*getWidth()),getHeight()),p);
                    c.drawBitmap(base,bb,new Rect((int)(firstW*getWidth()),0,getWidth(),getHeight()),p);
                }else{
                    Rect a=new Rect((int)(l*bw),(int)(t*bh),bw,(int)(b*bh));
                    Rect bb=new Rect(0,(int)(t*bh),(int)(rightNorm*bw),(int)(b*bh));
                    c.drawBitmap(base,a,new Rect(0,0,(int)(firstW*getWidth()),getHeight()),p);
                    c.drawBitmap(base,bb,new Rect((int)(firstW*getWidth()),0,getWidth(),getHeight()),p);
                }
                p.setFilterBitmap(false);
            }
        }

        private void refreshLabels(){
            if(mode==3||!LocalGeoData.isLoaded()){labelPlaces.clear();labelAirports.clear();return;}
            if(Math.abs(cx-labelCx)<.004f&&Math.abs(cy-labelCy)<.004f&&Math.abs(cw-labelW)<.006f)return;
            double lat=invLat(cy),lon=cx*360.0-180.0;
            double radius=Math.max(80,Math.min(4200,cw*40075*.72));
            int cityLimit=mode==0?28:(mode==1?22:16);
            labelPlaces=LocalGeoData.nearbyPlaces(lat,lon,radius,cityLimit);
            labelAirports=LocalGeoData.nearbyAirports(lat,lon,Math.min(radius,1400),mode==0?12:8);
            labelCx=cx;labelCy=cy;labelW=cw;
        }

        private void drawLabels(Canvas c){
            if(mode==3)return;
            refreshLabels();
            ArrayList<RectF> used=new ArrayList<RectF>();
            int minPop=mode==0?70000:(mode==1?180000:350000);
            float fs=mode==0?8.5f:(mode==1?9.2f:9.0f);

            ArrayList<GeoPlace> cities=new ArrayList<GeoPlace>(labelPlaces);
            Collections.sort(cities,(a,b)->Integer.compare(b.population,a.population));
            for(GeoPlace gp:cities){
                if(gp.population<minPop)continue;
                PointF q=screen(gp.lat,gp.lon);
                if(q.x<8||q.x>getWidth()-8||q.y<10||q.y>getHeight()-8)continue;
                String name=gp.name.toUpperCase(Locale.US);
                p.setTypeface(Typeface.create(Typeface.MONOSPACE,gp.population>=500000?Typeface.BOLD:Typeface.NORMAL));
                p.setTextSize(px(fs));
                float tw=p.measureText(name),th=px(fs);
                RectF box=new RectF(q.x+5,q.y-th-5,q.x+5+tw,q.y+3);
                boolean hit=false;for(RectF r:used)if(RectF.intersects(r,box)){hit=true;break;}
                if(hit)continue;
                used.add(box);
                p.setStyle(Paint.Style.FILL);p.setColor(CYAN);c.drawCircle(q.x,q.y,2.2f,p);
                p.setColor(TEXT);c.drawText(name,q.x+5,q.y-3,p);
            }

            for(GeoAirport a:labelAirports){
                String code=a.code==null?"":a.code.trim().toUpperCase(Locale.US);
                if(!code.matches("[A-Z]{3}"))continue;
                PointF q=screen(a.lat,a.lon);
                if(q.x<8||q.x>getWidth()-8||q.y<8||q.y>getHeight()-8)continue;
                p.setTypeface(Typeface.create(Typeface.MONOSPACE,Typeface.BOLD));p.setTextSize(px(mode==0?9.5f:9f));
                float tw=p.measureText(code);
                RectF box=new RectF(q.x+5,q.y-14,q.x+5+tw,q.y+2);
                boolean hit=false;for(RectF r:used)if(RectF.intersects(r,box)){hit=true;break;}
                if(hit)continue;used.add(box);
                p.setColor(AMBER);p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(1.2f);
                c.drawCircle(q.x,q.y,3.6f,p);c.drawLine(q.x-5,q.y,q.x+5,q.y,p);c.drawLine(q.x,q.y-5,q.x,q.y+5,p);
                p.setStyle(Paint.Style.FILL);c.drawText(code,q.x+5,q.y-3,p);
            }
            p.setTypeface(Typeface.MONOSPACE);
        }

        private void routeLine(Canvas c,double la1,double lo1,double la2,double lo2,int color,boolean dashed){
            PointF a=screen(la1,lo1),b=screen(la2,lo2);
            p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(px(1.8f));p.setColor(color);
            if(dashed)p.setPathEffect(new DashPathEffect(new float[]{dp(7),dp(5)},0));
            c.drawLine(a.x,a.y,b.x,b.y,p);p.setPathEffect(null);
        }

        private void marker(Canvas c,double lat,double lon,String label,int color){
            PointF q=screen(lat,lon);
            if(q.x<-30||q.x>getWidth()+30||q.y<-30||q.y>getHeight()+30)return;
            p.setStyle(Paint.Style.FILL);p.setColor(color);c.drawCircle(q.x,q.y,dp(4),p);
            p.setTypeface(Typeface.create(Typeface.MONOSPACE,Typeface.BOLD));p.setTextSize(dp(10));
            c.drawText(label,q.x+dp(7),q.y-dp(6),p);p.setTypeface(Typeface.MONOSPACE);
        }

        private void drawAircraft(Canvas c){
            if(liveLat==null||liveLon==null)return;
            PointF q=screen(liveLat,liveLon);
            int color=liveMilitary?Color.rgb(255,102,119):AMBER;
            p.setColor(color);p.setStyle(Paint.Style.FILL);p.setAlpha(55);
            c.drawCircle(q.x,q.y,dp(13),p);
            p.setAlpha(255);p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(px(1.8f));c.drawCircle(q.x,q.y,dp(11),p);

            c.save();c.rotate((float)liveTrack,q.x,q.y);
            p.setStyle(Paint.Style.FILL);
            Path plane=new Path();
            float r=dp(9);
            plane.moveTo(q.x,q.y-r);plane.lineTo(q.x+r*.22f,q.y-r*.25f);
            plane.lineTo(q.x+r,q.y+r*.30f);plane.lineTo(q.x,q.y+r*.62f);
            plane.lineTo(q.x-r,q.y+r*.30f);plane.lineTo(q.x-r*.22f,q.y-r*.25f);plane.close();
            c.drawPath(plane,p);c.restore();

            p.setTypeface(Typeface.create(Typeface.MONOSPACE,Typeface.BOLD));p.setTextSize(dp(11));
            c.drawText(liveLabel,q.x+dp(15),q.y-dp(9),p);p.setTypeface(Typeface.MONOSPACE);
        }

        private void drawGlobalTraffic(Canvas c){
            if(mode!=3)return;
            ArrayList<WorldContact> copy;
            synchronized(globalTraffic){copy=new ArrayList<WorldContact>(globalTraffic);}
            p.setStyle(Paint.Style.FILL);
            for(WorldContact a:copy){
                PointF q=screen(a.lat,a.lon);
                p.setColor(a.military?Color.rgb(255,102,119):(a.onGround?DIM:CYAN));
                c.drawCircle(q.x,q.y,a.onGround?1.1f:1.7f,p);
            }
            p.setTypeface(Typeface.create(Typeface.MONOSPACE,Typeface.BOLD));p.setTextSize(dp(9));p.setColor(CYAN);
            c.drawText("WORLD TRAFFIC  "+copy.size()+"  //  "+globalSource,dp(6),dp(13),p);
            p.setTypeface(Typeface.MONOSPACE);
        }

        @Override protected void onDraw(Canvas c){
            super.onDraw(c);updateAnim();drawBase(c);

            if(mode==3){drawGlobalTraffic(c);return;}

            drawLabels(c);

            if(mode==0){
                if(homeLat!=null&&homeLon!=null)
                    marker(c,homeLat,homeLon,"HOME",Color.rgb(230,185,65));

                // FOLLOW still centres the aircraft, but now shows the useful part:
                // the remaining route leaving the aircraft toward its destination.
                if(liveLat!=null&&dLat!=null&&dLon!=null){
                    routeLine(c,liveLat,liveLon,dLat,dLon,CYAN,true);
                    marker(c,dLat,dLon,"DEST",CYAN);
                }
            }

            if(mode==1&&liveLat!=null&&dLat!=null){
                routeLine(c,liveLat,liveLon,dLat,dLon,CYAN,true);
                marker(c,dLat,dLon,"DEST",CYAN);
            }else if(mode==2&&oLat!=null&&dLat!=null){
                routeLine(c,oLat,oLon,dLat,dLon,CYAN,true);
                marker(c,oLat,oLon,"ORIGIN",AMBER);
                marker(c,dLat,dLon,"DEST",CYAN);
            }

            // Aircraft always last, so it cannot disappear below map text.
            drawAircraft(c);
        }
    }

    public static class WorldMapView extends View {
        private final Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);private Bitmap map,themedMap;private int themedMapColor=0;
        private final ArrayList<WorldContact> worldTraffic=new ArrayList<WorldContact>();private String worldTrafficSource="";
        private final LinkedHashMap<String,Bitmap> bundledTiles=new LinkedHashMap<String,Bitmap>(32,0.75f,true);
        private static final int BUNDLED_TILE_CACHE_MAX=24;
        private ArrayList<GeoPlace> localPlaces=new ArrayList<GeoPlace>();private ArrayList<GeoAirport> localAirports=new ArrayList<GeoAirport>();private ArrayList<GeoRunway> localRunways=new ArrayList<GeoRunway>();private long geoCacheAt=0;private float geoCacheX=-9,geoCacheY=-9,geoCacheW=-9;private Double oLat,oLon,dLat,dLon,issLat,issLon,sunLat,sunLon,moonLat,moonLon,launchLat,launchLon,homeLat,homeLon,liveLat,liveLon;private String launchName="",liveLabel="",liveKind="FIXED";private boolean liveMilitary=false;private boolean spaceMode=false;private int cameraMode=3;private float cameraZoom=1f,camX=.5f,camY=.5f,camW=1f,camH=1f,startX=.5f,startY=.5f,startW=1f,startH=1f,targetX=.5f,targetY=.5f,targetW=1f,targetH=1f;private long animStart=0,animDuration=0,introHoldUntil=0;
        private final Map<String,Bitmap> mapTiles=Collections.synchronizedMap(new LinkedHashMap<String,Bitmap>());
        private final Set<String> loadingTiles=Collections.synchronizedSet(new HashSet<String>());
        private final ExecutorService mapTileIo=Executors.newFixedThreadPool(2);
        private ColorMatrixColorFilter mapTileFilter;private int mapTileFilterColor=0;
        private Bitmap stableMapMosaic=null;
        private int stableMapZoom=-1,stableX0=0,stableY0=0,stableX1=-1,stableY1=-1;
        private boolean mosaicBuildQueued=false;
        WorldMapView(Context c){super(c);p.setTypeface(Typeface.MONOSPACE);map=BitmapFactory.decodeResource(getResources(),R.drawable.world_map);setBackgroundColor(Color.TRANSPARENT);LocalGeoData.ensureLoaded(c,this);}
        void setWorldTraffic(List<WorldContact> items,String source){synchronized(worldTraffic){worldTraffic.clear();if(items!=null)worldTraffic.addAll(items);}worldTrafficSource=source==null?"":source;invalidate();}
        boolean isWorldView(){return cameraMode==3;}
        void setRoute(double a,double b,double c,double d){oLat=a;oLon=b;dLat=c;dLon=d;if(cameraMode==2)viewRoute();invalidate();}void clearRoute(){oLat=oLon=dLat=dLon=null;invalidate();}
        void setLiveAircraft(double lat,double lon,String label,boolean focus){liveLat=lat;liveLon=lon;liveLabel=label==null?"AIRCRAFT":label;if(focus){cameraMode=0;startCinematic();}invalidate();}
        void setLiveAircraft(Aircraft a,boolean focus){if(a==null)return;liveLat=a.lat;liveLon=a.lon;liveLabel=a.callsign;liveKind=a.kind();liveMilitary=a.military;if(focus){cameraMode=0;startCinematic();}invalidate();}
        void clearLiveAircraft(){liveLat=liveLon=null;liveKind="FIXED";liveMilitary=false;viewWorld();}
        void viewFollow(){cameraMode=0;animateTo(followCamera(),550);}void viewPath(){cameraMode=1;animateTo(routeCamera(false),550);}void viewRoute(){cameraMode=2;animateTo(routeCamera(true),550);}void viewWorld(){cameraMode=3;cameraZoom=1f;animateTo(new float[]{.5f,.5f,1f,1f},550);}void zoomBy(float f){cameraZoom=Math.max(.25f,Math.min(16f,cameraZoom*f));float[] t=currentModeCamera();t[2]=Math.max(.02f,Math.min(1,t[2]/cameraZoom));t[3]=Math.max(.02f,Math.min(1,t[3]/cameraZoom));animateTo(t,420);}
        private void startCinematic(){cameraZoom=1f;camX=.5f;camY=.5f;camW=camH=1f;introHoldUntil=System.currentTimeMillis()+1150;animStart=0;postInvalidateOnAnimation();}
        private float[] currentModeCamera(){if(cameraMode==0)return followCamera();if(cameraMode==2)return routeCamera(true);if(cameraMode==1)return routeCamera(false);return new float[]{.5f,.5f,1f,1f};}
        private float[] followCamera(){
            if(liveLat==null)return new float[]{.5f,.5f,1f,1f};

            float lx=(float)((liveLon+180)/360.0),ly=mercatorY(liveLat);

            // FOLLOW TARGET means exactly that: the selected aircraft is the camera
            // centre. HOME remains a nearby reference marker but never drives framing.
            // Radar-selected aircraft should already be close to HOME by definition.
            return new float[]{lx,ly,.020f,.0124f};
        }
        private float[] cameraBetween(double lat1,double lon1,double lat2,double lon2,float minW,float minH){
            float x1=(float)((lon1+180)/360.0),x2=(float)((lon2+180)/360.0),y1=mercatorY(lat1),y2=mercatorY(lat2);
            float dx=Math.abs(x2-x1);if(dx>.5f){if(x1<x2)x1+=1;else x2+=1;dx=Math.abs(x2-x1);}
            float cx=(x1+x2)/2f;cx=cx-(float)Math.floor(cx);float cy=(y1+y2)/2f;
            float w=Math.min(1f,Math.max(minW,dx*1.55f));
            float h=Math.min(1f,Math.max(minH,Math.abs(y2-y1)*1.75f));
            return new float[]{cx,cy,w,h};
        }
        private float[] routeCamera(boolean full){
            if(oLat==null||dLat==null)return followCamera();
            if(!full&&liveLat!=null&&liveLon!=null){
                // REMAINING PATH: frame CURRENT AIRCRAFT -> DESTINATION.
                return cameraBetween(liveLat,liveLon,dLat,dLon,.055f,.075f);
            }
            // FULL ROUTE: frame ORIGIN -> DESTINATION. The live aircraft is then
            // overlaid at its true current position along/near that route.
            return cameraBetween(oLat,oLon,dLat,dLon,.18f,.18f);
        }
        private void animateTo(float[] t,long duration){startX=camX;startY=camY;startW=camW;startH=camH;targetX=t[0];targetY=t[1];targetW=t[2];targetH=t[3];animStart=System.currentTimeMillis();animDuration=duration;postInvalidateOnAnimation();}
        private boolean cameraAnimating(){return animStart>0 || introHoldUntil>System.currentTimeMillis();}
        private void updateCamera(){long now=System.currentTimeMillis();if(introHoldUntil>now){postInvalidateOnAnimation();return;}if(introHoldUntil!=0&&animStart==0){introHoldUntil=0;animateTo(followCamera(),2000);}if(animStart>0){float t=Math.min(1f,(now-animStart)/(float)Math.max(1,animDuration));float u=t*t*(3-2*t);camX=startX+(targetX-startX)*u;camY=startY+(targetY-startY)*u;camW=startW+(targetW-startW)*u;camH=startH+(targetH-startH)*u;if(t<1)postInvalidateOnAnimation();else{animStart=0;geoCacheAt=0;invalidate();}}}
        void setHome(double a,double b){homeLat=a;homeLon=b;invalidate();}void setIss(double a,double b){issLat=a;issLon=b;invalidate();}void setSunMoon(double sa,double so,double ma,double mo){sunLat=sa;sunLon=so;moonLat=ma;moonLon=mo;invalidate();}void setLaunch(double a,double b,String n){launchLat=a;launchLon=b;launchName=n==null?"LAUNCH SITE":n;invalidate();}void setSpaceMode(boolean b){spaceMode=b;invalidate();}
        private static float mercatorY(double lat){double cl=Math.max(-85.05112878,Math.min(85.05112878,lat));double r=Math.toRadians(cl);return (float)((1-Math.log(Math.tan(r)+1/Math.cos(r))/Math.PI)/2.0);}
        private PointF xy(double lat,double lon){return new PointF((float)((lon+180)/360.0*getWidth()),mercatorY(lat)*getHeight());}
        private void drawGreatCircle(Canvas c,double lat1,double lon1,double lat2,double lon2,float sx,float sy){double p1=Math.toRadians(lat1),l1=Math.toRadians(lon1),p2=Math.toRadians(lat2),l2=Math.toRadians(lon2);double[] a={Math.cos(p1)*Math.cos(l1),Math.cos(p1)*Math.sin(l1),Math.sin(p1)},b={Math.cos(p2)*Math.cos(l2),Math.cos(p2)*Math.sin(l2),Math.sin(p2)};double omega=Math.acos(Math.max(-1,Math.min(1,a[0]*b[0]+a[1]*b[1]+a[2]*b[2]))),sin=Math.sin(omega);Path path=new Path();PointF prev=null;for(int n=0;n<=72;n++){double t=n/72.0,s1=sin<1e-8?1-t:Math.sin((1-t)*omega)/sin,s2=sin<1e-8?t:Math.sin(t*omega)/sin;double x=s1*a[0]+s2*b[0],y=s1*a[1]+s2*b[1],z=s1*a[2]+s2*b[2],la=Math.toDegrees(Math.atan2(z,Math.sqrt(x*x+y*y))),lo=Math.toDegrees(Math.atan2(y,x));PointF q=xy(la,lo);if(prev==null||Math.abs(q.x-prev.x)>getWidth()*.48f)path.moveTo(q.x,q.y);else path.lineTo(q.x,q.y);prev=q;}p.setColor(CYAN);p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(2/Math.max(sx,sy));p.setPathEffect(new DashPathEffect(new float[]{8/Math.max(sx,sy),6/Math.max(sx,sy)},0));c.drawPath(path,p);p.setPathEffect(null);}
        private Bitmap themedWorldMap(){
            if(map==null)return null;
            if(themedMap!=null&&themedMapColor==GREEN&&!themedMap.isRecycled())return themedMap;
            try{
                Bitmap out=Bitmap.createBitmap(map.getWidth(),map.getHeight(),Bitmap.Config.ARGB_8888);
                Canvas cc=new Canvas(out);Paint mp=new Paint(Paint.ANTI_ALIAS_FLAG|Paint.FILTER_BITMAP_FLAG);
                float tr=Color.red(GREEN)/255f,tg=Color.green(GREEN)/255f,tb=Color.blue(GREEN)/255f;
                ColorMatrix cm=new ColorMatrix(new float[]{
                    .299f*tr,.587f*tr,.114f*tr,0,0,
                    .299f*tg,.587f*tg,.114f*tg,0,0,
                    .299f*tb,.587f*tb,.114f*tb,0,0,
                    0,0,0,1,0
                });
                mp.setColorFilter(new ColorMatrixColorFilter(cm));cc.drawBitmap(map,0,0,mp);
                if(themedMap!=null&&!themedMap.isRecycled())themedMap.recycle();
                themedMap=out;themedMapColor=GREEN;return themedMap;
            }catch(Exception e){return map;}
        }
        private static double inverseMercatorY(double y){
            double n=Math.PI-2*Math.PI*y;return Math.toDegrees(Math.atan(Math.sinh(n)));
        }
        private int bundledMapZoom(){
            if(camW<=.028f)return 6;
            if(camW<=.060f)return 5;
            if(camW<=.14f)return 4;
            return 3;
        }
        private Bitmap bundledTile(int z,int x,int y){
            int n=1<<z;
            if(y<0||y>=n)return null;
            x=((x%n)+n)%n;
            String key=z+"_"+x+"_"+y;
            synchronized(bundledTiles){
                Bitmap hit=bundledTiles.get(key);
                if(hit!=null&&!hit.isRecycled())return hit;
            }
            Bitmap bm=null;
            try{
                InputStream in=getContext().getAssets().open("aviation_tiles/"+z+"/"+x+"/"+y+".jpg");
                bm=BitmapFactory.decodeStream(in);in.close();
            }catch(Exception ignored){}
            if(bm==null&&z==6){
                // z6 is Europe-focused. Fall back instantly to global z5 elsewhere.
                return bundledTile(5,x/2,y/2);
            }
            if(bm!=null){
                synchronized(bundledTiles){
                    bundledTiles.put(key,bm);
                    while(bundledTiles.size()>BUNDLED_TILE_CACHE_MAX){
                        Iterator<Map.Entry<String,Bitmap>> it=bundledTiles.entrySet().iterator();
                        if(!it.hasNext())break;
                        Map.Entry<String,Bitmap> e=it.next();
                        Bitmap old=e.getValue();it.remove();
                        if(old!=null&&!old.isRecycled())old.recycle();
                    }
                }
            }
            return bm;
        }
        private float[] displayCameraBounds(){
            float aspect=(getWidth()>0)?getHeight()/(float)getWidth():.62f;

            // Fit the requested camera box inside an aspect-correct Mercator viewport.
            // One world-normalised X unit and Y unit therefore occupy the same number
            // of screen pixels, so labels/coastlines no longer stretch.
            float w=Math.max(.0001f,camW);
            float h=Math.max(.0001f,camH);
            float correctedW=Math.max(w,h/Math.max(.05f,aspect));
            float correctedH=correctedW*aspect;

            correctedW=Math.min(1f,correctedW);
            correctedH=Math.min(1f,correctedH);
            return new float[]{camX,camY,correctedW,correctedH};
        }

        private boolean drawBundledAviationMap(Canvas c){
            if(spaceMode||cameraMode==3)return false;
            int z=bundledMapZoom(),n=1<<z;
            float[] dc=displayCameraBounds();
            float drawW=dc[2],drawH=dc[3];
            float camL=dc[0]-drawW*.5f,camR=dc[0]+drawW*.5f;
            float camT=dc[1]-drawH*.5f,camB=dc[1]+drawH*.5f;
            int x0=(int)Math.floor(camL*n)-1,x1=(int)Math.floor(camR*n)+1;
            int y0=Math.max(0,(int)Math.floor(camT*n)-1),y1=Math.min(n-1,(int)Math.floor(camB*n)+1);

            c.save();c.setMatrix(new Matrix());
            p.setStyle(Paint.Style.FILL);p.setColor(Color.rgb(2,17,16));c.drawRect(0,0,getWidth(),getHeight(),p);
            p.setFilterBitmap(true);p.setAlpha(255);p.setColorFilter(null);

            boolean any=false;
            float vw=Math.max(.00001f,camR-camL),vh=Math.max(.00001f,camB-camT);
            for(int ty=y0;ty<=y1;ty++){
                for(int tx=x0;tx<=x1;tx++){
                    Bitmap bm=bundledTile(z,tx,ty);
                    int tz=z, ttx=tx, tty=ty;
                    if(bm==null)continue;
                    // If z6 fell back to z5, use the parent tile's geographic bounds.
                    if(z==6){
                        String k=z+"_"+(((tx%n)+n)%n)+"_"+ty;
                        synchronized(bundledTiles){
                            if(!bundledTiles.containsKey(k)){tz=5;ttx=tx/2;tty=ty/2;}
                        }
                    }
                    int tn=1<<tz;
                    float tileL=ttx/(float)tn,tileR=(ttx+1)/(float)tn;
                    float tileT=tty/(float)tn,tileB=(tty+1)/(float)tn;
                    float l=(tileL-camL)/vw*getWidth(),r=(tileR-camL)/vw*getWidth();
                    float t=(tileT-camT)/vh*getHeight(),b=(tileB-camT)/vh*getHeight();
                    c.drawBitmap(bm,null,new RectF(l,t,r,b),p);any=true;
                }
            }
            p.setFilterBitmap(false);c.restore();
            return any;
        }

        private void refreshLocalGeoIfNeeded(){
            // Camera buttons animate the entire map. Keep the current cached geography
            // during movement; refresh once the camera settles.
            if(cameraAnimating())return;
            if(spaceMode||camW>.38f||!LocalGeoData.isLoaded()){
                localPlaces.clear();localAirports.clear();localRunways.clear();return;
            }
            long now=System.currentTimeMillis();
            if(now-geoCacheAt<1200&&Math.abs(camX-geoCacheX)<.008f&&Math.abs(camY-geoCacheY)<.008f&&Math.abs(camW-geoCacheW)<.012f)return;
            double lat=inverseMercatorY(camY),lon=camX*360.0-180.0;
            // Radius tracks the visible viewport rather than using a huge fixed regional grab.
            // This keeps FOLLOW TARGET populated with nearby towns instead of half a continent.
            double radius=Math.max(35,Math.min(1400,camW*20037.5*.78));
            int placeLimit=camW<.035f?70:(camW<.09f?55:40);
            int airportLimit=camW<.05f?28:18;
            localPlaces=LocalGeoData.nearbyPlaces(lat,lon,radius,placeLimit);
            localAirports=LocalGeoData.nearbyAirports(lat,lon,Math.min(radius,700),airportLimit);
            localRunways=camW<.055f?LocalGeoData.runwaysFor(localAirports,80):new ArrayList<GeoRunway>();
            geoCacheAt=now;geoCacheX=camX;geoCacheY=camY;geoCacheW=camW;
        }

        private int mapTileZoom(){
            // Deliberately conservative for 2017 Fire hardware. The custom vector
            // overlay supplies large place labels while OSM supplies real roads/geography.
            if(camW<=.028f)return 7;
            if(camW<=.070f)return 6;
            if(camW<=.140f)return 5;
            return 4;
        }
        private String mapTileKey(int z,int x,int y){return z+"_"+x+"_"+y;}
        private File mapTileFile(int z,int x,int y){
            File dir=new File(getContext().getCacheDir(),"flight_osm/"+z+"/"+x);
            if(!dir.exists())dir.mkdirs();
            return new File(dir,y+".png");
        }
        private void trimMapTileMemory(){
            synchronized(mapTiles){
                if(mapTiles.size()<=48)return;
                Iterator<String> it=mapTiles.keySet().iterator();
                while(mapTiles.size()>40&&it.hasNext()){it.next();it.remove();}
            }
        }
        private void requestMapTile(final int z,final int rawX,final int y){
            final int n=1<<z;
            if(y<0||y>=n)return;
            final int x=((rawX%n)+n)%n;
            final String key=mapTileKey(z,x,y);
            if(mapTiles.containsKey(key)||loadingTiles.contains(key))return;
            loadingTiles.add(key);
            mapTileIo.execute(()->{
                Bitmap bm=null;
                try{
                    File file=mapTileFile(z,x,y);
                    long age=System.currentTimeMillis()-file.lastModified();
                    if(file.exists()&&file.length()>500&&age<14L*24*60*60_000L){
                        bm=BitmapFactory.decodeFile(file.getAbsolutePath());
                    }
                    if(bm==null){
                        URL u=new URL("https://tile.openstreetmap.org/"+z+"/"+x+"/"+y+".png");
                        HttpsURLConnection con=(HttpsURLConnection)u.openConnection();
                        con.setConnectTimeout(9000);con.setReadTimeout(12000);
                        con.setRequestProperty("User-Agent","InTheSky-FireHD-Legacy/3.6.23");
                        con.setRequestProperty("Accept","image/png,image/*;q=0.8");
                        try{
                            if(con.getResponseCode()>=200&&con.getResponseCode()<300){
                                ByteArrayOutputStream out=new ByteArrayOutputStream();
                                InputStream in=con.getInputStream();byte[] buf=new byte[8192];int r;
                                while((r=in.read(buf))>0)out.write(buf,0,r);
                                in.close();byte[] data=out.toByteArray();
                                if(data.length>500){
                                    FileOutputStream fos=new FileOutputStream(file);fos.write(data);fos.close();
                                    bm=BitmapFactory.decodeByteArray(data,0,data.length);
                                }
                            }
                        }finally{con.disconnect();}
                    }
                }catch(Exception ignored){}
                final Bitmap result=bm;
                post(()->{
                    loadingTiles.remove(key);
                    if(result!=null&&!result.isRecycled()){mapTiles.put(key,result);trimMapTileMemory();}
                    if(loadingTiles.isEmpty()){mosaicBuildQueued=false;invalidate();}
                });
            });
        }
        private ColorMatrixColorFilter themedMapTileFilter(){
            if(mapTileFilter!=null&&mapTileFilterColor==GREEN)return mapTileFilter;
            float tr=.18f+.50f*Color.red(GREEN)/255f;
            float tg=.18f+.50f*Color.green(GREEN)/255f;
            float tb=.18f+.50f*Color.blue(GREEN)/255f;
            ColorMatrix cm=new ColorMatrix(new float[]{
                .299f*tr,.587f*tr,.114f*tr,0,2,
                .299f*tg,.587f*tg,.114f*tg,0,5,
                .299f*tb,.587f*tb,.114f*tb,0,6,
                0,0,0,1,0
            });
            mapTileFilter=new ColorMatrixColorFilter(cm);mapTileFilterColor=GREEN;return mapTileFilter;
        }
        private boolean stableMosaicMatches(int z,int x0,int y0,int x1,int y1){
            return stableMapMosaic!=null&&!stableMapMosaic.isRecycled()&&
                stableMapZoom==z&&stableX0==x0&&stableY0==y0&&stableX1==x1&&stableY1==y1;
        }
        private boolean requiredTilesReady(int z,int x0,int y0,int x1,int y1){
            int n=1<<z;
            for(int ty=y0;ty<=y1;ty++)for(int tx=x0;tx<=x1;tx++){
                int wrapped=((tx%n)+n)%n;
                if(!mapTiles.containsKey(mapTileKey(z,wrapped,ty)))return false;
            }
            return true;
        }
        private void requestTileSet(int z,int x0,int y0,int x1,int y1){
            int n=1<<z;
            for(int ty=y0;ty<=y1;ty++)for(int tx=x0;tx<=x1;tx++){
                int wrapped=((tx%n)+n)%n;
                if(!mapTiles.containsKey(mapTileKey(z,wrapped,ty)))requestMapTile(z,tx,ty);
            }
        }
        private void buildStableMosaic(final int z,final int x0,final int y0,final int x1,final int y1){
            if(mosaicBuildQueued||!requiredTilesReady(z,x0,y0,x1,y1))return;
            mosaicBuildQueued=true;
            post(()->{
                try{
                    int tilesWide=x1-x0+1,tilesHigh=y1-y0+1,tilePx=256;
                    Bitmap mosaic=Bitmap.createBitmap(Math.max(1,tilesWide*tilePx),Math.max(1,tilesHigh*tilePx),Bitmap.Config.ARGB_8888);
                    Canvas mc=new Canvas(mosaic);Paint mp=new Paint(Paint.ANTI_ALIAS_FLAG|Paint.FILTER_BITMAP_FLAG);
                    mp.setColorFilter(themedMapTileFilter());
                    int n=1<<z;
                    for(int ty=y0;ty<=y1;ty++)for(int tx=x0;tx<=x1;tx++){
                        int wrapped=((tx%n)+n)%n;Bitmap bm=mapTiles.get(mapTileKey(z,wrapped,ty));if(bm==null)continue;
                        float l=(tx-x0)*tilePx,t=(ty-y0)*tilePx;mc.drawBitmap(bm,null,new RectF(l,t,l+tilePx,t+tilePx),mp);
                    }
                    mp.setColorFilter(null);
                    if(stableMapMosaic!=null&&!stableMapMosaic.isRecycled())stableMapMosaic.recycle();
                    stableMapMosaic=mosaic;stableMapZoom=z;stableX0=x0;stableY0=y0;stableX1=x1;stableY1=y1;
                    invalidate();
                }catch(Exception ignored){}
                mosaicBuildQueued=false;
            });
        }

        private boolean drawRealMapTiles(Canvas c,float blend){
            if(spaceMode||camW>.18f||blend<=0f)return false;

            int z=mapTileZoom(),n=1<<z;
            float camL=camX-camW*.5f,camR=camX+camW*.5f;
            float camT=camY-camH*.5f,camB=camY+camH*.5f;

            int x0=(int)Math.floor(camL*n)-1;
            int x1=(int)Math.floor(camR*n)+1;
            int y0=Math.max(0,(int)Math.floor(camT*n)-1);
            int y1=Math.min(n-1,(int)Math.floor(camB*n)+1);

            // Keep the request footprint small and centred. The vector fallback is
            // always visible underneath, so there is no need to fetch half Britain
            // before the user sees a useful map.
            if(x1-x0>5){int cx=(int)Math.floor(camX*n);x0=cx-2;x1=cx+3;}
            if(y1-y0>5){int cy=(int)Math.floor(camY*n);y0=Math.max(0,cy-2);y1=Math.min(n-1,cy+3);}

            boolean complete=requiredTilesReady(z,x0,y0,x1,y1);
            if(!complete&&!cameraAnimating())requestTileSet(z,x0,y0,x1,y1);
            if(!complete)return false;

            c.save();
            c.setMatrix(new Matrix());

            p.setStyle(Paint.Style.FILL);
            p.setColor(Color.rgb(3,17,15));
            c.drawRect(0,0,getWidth(),getHeight(),p);

            p.setFilterBitmap(true);
            p.setColorFilter(themedMapTileFilter());
            p.setAlpha(255);

            float viewW=Math.max(.00001f,camR-camL);
            float viewH=Math.max(.00001f,camB-camT);

            for(int ty=y0;ty<=y1;ty++){
                for(int tx=x0;tx<=x1;tx++){
                    int wrapped=((tx%n)+n)%n;
                    Bitmap bm=mapTiles.get(mapTileKey(z,wrapped,ty));
                    if(bm==null){p.setColorFilter(null);p.setFilterBitmap(false);c.restore();return false;}

                    float tileL=tx/(float)n,tileR=(tx+1)/(float)n;
                    float tileT=ty/(float)n,tileB=(ty+1)/(float)n;
                    float l=(tileL-camL)/viewW*getWidth();
                    float r=(tileR-camL)/viewW*getWidth();
                    float t=(tileT-camT)/viewH*getHeight();
                    float b=(tileB-camT)/viewH*getHeight();
                    c.drawBitmap(bm,null,new RectF(l,t,r,b),p);
                }
            }

            p.setColorFilter(null);p.setFilterBitmap(false);p.setAlpha(255);
            c.restore();
            return true;
        }

        private float localMapBlend(){
            if(spaceMode||camW>.18f)return 0f;
            return 1f;
        }

        private void drawLocalBackdrop(Canvas c,float sx,float sy,float blend){
            if(blend<=0f)return;
            // Dark teal chart paper. Because the canvas is already in world coordinates,
            // this rectangle naturally fills whatever part of the world is in the viewport.
            p.setStyle(Paint.Style.FILL);
            p.setColor(Color.argb((int)(235*blend),2,22,17));
            c.drawRect(0,0,getWidth(),getHeight(),p);

            float inv=1/Math.max(sx,sy);
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(.75f*inv);
            p.setColor(Color.argb((int)(72*blend),Color.red(CYAN),Color.green(CYAN),Color.blue(CYAN)));
            // Fine chart grid that remains crisp at every zoom level.
            int lonStep=camW<.035f?1:(camW<.10f?2:5);
            int latStep=lonStep;
            double centerLon=camX*360.0-180.0,halfLon=Math.max(lonStep*2,camW*180.0*1.25);
            double topLat=inverseMercatorY(Math.max(0,camY-camH*.63f));
            double bottomLat=inverseMercatorY(Math.min(1,camY+camH*.63f));
            int lon0=(int)Math.floor((centerLon-halfLon)/lonStep)*lonStep;
            int lon1=(int)Math.ceil((centerLon+halfLon)/lonStep)*lonStep;
            int lat0=(int)Math.floor(Math.min(topLat,bottomLat)/latStep)*latStep;
            int lat1=(int)Math.ceil(Math.max(topLat,bottomLat)/latStep)*latStep;
            for(int lon=lon0;lon<=lon1;lon+=lonStep){PointF a=xy(bottomLat,lon),b=xy(topLat,lon);c.drawLine(a.x,a.y,b.x,b.y,p);}
            for(int lat=lat0;lat<=lat1;lat+=latStep){PointF a=xy(lat,centerLon-halfLon),b=xy(lat,centerLon+halfLon);c.drawLine(a.x,a.y,b.x,b.y,p);}
        }

        private PointF toScreen(Canvas c,PointF world){
            Matrix m=new Matrix();
            c.getMatrix(m);
            float[] pt={world.x,world.y};
            m.mapPoints(pt);
            return new PointF(pt[0],pt[1]);
        }
        private void drawScreenLabel(Canvas c,String text,PointF world,float dx,float dy,float size,int color,boolean bold){
            PointF q=toScreen(c,world);
            c.save();
            c.setMatrix(new Matrix());
            p.setStyle(Paint.Style.FILL);
            p.setTypeface(Typeface.create(Typeface.MONOSPACE,bold?Typeface.BOLD:Typeface.NORMAL));
            p.setTextSize(size);
            p.setColor(color);
            c.drawText(text,q.x+dx,q.y+dy,p);
            p.setTypeface(Typeface.MONOSPACE);
            c.restore();
        }

        private void drawLocalGeo(Canvas c,float sx,float sy,float blend){
            refreshLocalGeoIfNeeded();
            if(blend<=0f || (localPlaces.isEmpty()&&localAirports.isEmpty()))return;
            float inv=1/Math.max(sx,sy);
            boolean osmReady=requiredTilesReady(mapTileZoom(),
                (int)Math.floor((camX-camW*.5f)*(1<<mapTileZoom()))-1,
                Math.max(0,(int)Math.floor((camY-camH*.5f)*(1<<mapTileZoom()))-1),
                (int)Math.floor((camX+camW*.5f)*(1<<mapTileZoom()))+1,
                Math.min((1<<mapTileZoom())-1,(int)Math.floor((camY+camH*.5f)*(1<<mapTileZoom()))+1));

            // With real OSM present, roads/geography already provide structure.
            // Keep only a very faint Windows-style network while OSM is unavailable.
            ArrayList<PointF> pts=new ArrayList<PointF>();
            for(GeoPlace gp:localPlaces)pts.add(xy(gp.lat,gp.lon));
            if(!osmReady){
                p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(.75f*inv);
                p.setColor(Color.argb((int)(48*blend),Color.red(GREEN),Color.green(GREEN),Color.blue(GREEN)));
                double maxLinkKm=camW<.035f?70:(camW<.09f?120:220);
                for(int i=0;i<localPlaces.size();i++){
                    GeoPlace a=localPlaces.get(i);PointF pa=pts.get(i);
                    int first=-1;double d1=Double.MAX_VALUE;
                    double cosLat=Math.max(.15,Math.cos(Math.toRadians(a.lat)));
                    for(int j=0;j<localPlaces.size();j++){
                        if(i==j)continue;GeoPlace b=localPlaces.get(j);
                        double dy=(b.lat-a.lat)*110.574,dx=(b.lon-a.lon)*111.320*cosLat;
                        double d=Math.hypot(dx,dy);
                        if(d<d1){d1=d;first=j;}
                    }
                    if(first>=0&&d1<=maxLinkKm){PointF q=pts.get(first);c.drawLine(pa.x,pa.y,q.x,q.y,p);}
                }
            }

            // Runways only when properly close.
            if(camW<.035f&&!localAirports.isEmpty()){
                p.setColor(Color.argb((int)(155*blend),Color.red(AMBER),Color.green(AMBER),Color.blue(AMBER)));
                p.setStrokeWidth(1.5f*inv);
                for(GeoRunway r:localRunways){PointF a=xy(r.la1,r.lo1),b=xy(r.la2,r.lo2);c.drawLine(a.x,a.y,b.x,b.y,p);}
            }

            // Collision-aware place labels. Major cities first, then medium towns if space remains.
            ArrayList<RectF> occupied=new ArrayList<RectF>();
            ArrayList<Integer> order=new ArrayList<Integer>();
            for(int i=0;i<localPlaces.size();i++)order.add(i);
            Collections.sort(order,(ia,ib)->Integer.compare(localPlaces.get(ib).population,localPlaces.get(ia).population));
            int labelLimit=camW<.035f?18:(camW<.09f?12:8),shown=0;
            for(int oi:order){
                if(shown>=labelLimit)break;
                GeoPlace gp=localPlaces.get(oi);
                if(gp.population<(camW<.035f?45000:90000))continue;
                PointF q=pts.get(oi);
                boolean major=gp.population>=180000;
                String label=gp.name.toUpperCase(Locale.US);
                float fs=major?15.5f:12.5f;
                p.setTypeface(Typeface.create(Typeface.MONOSPACE,major?Typeface.BOLD:Typeface.NORMAL));
                p.setTextSize(fs*inv);
                float tw=p.measureText(label),th=fs*inv;
                RectF box=new RectF(q.x+7*inv,q.y-18*inv-th,q.x+7*inv+tw,q.y-4*inv);
                boolean hit=false;
                for(RectF r:occupied)if(RectF.intersects(r,box)){hit=true;break;}
                if(hit)continue;
                occupied.add(box);
                p.setStyle(Paint.Style.FILL);
                p.setColor(Color.argb((int)(210*blend),Color.red(CYAN),Color.green(CYAN),Color.blue(CYAN)));
                c.drawCircle(q.x,q.y,(major?2.6f:1.8f)*inv,p);
                p.setColor(Color.argb((int)(235*blend),Color.red(TEXT),Color.green(TEXT),Color.blue(TEXT)));
                c.drawText(label,q.x+7*inv,q.y-5*inv,p);
                shown++;
            }

            // Airports: only recognisable three-letter codes. Suppress GB-xxxx / local
            // identifiers unless we later add a dedicated airport-detail mode.
            int apShown=0,apLimit=camW<.035f?8:5;
            for(GeoAirport a:localAirports){
                if(apShown>=apLimit)break;
                String code=a.code==null?"":a.code.trim().toUpperCase(Locale.US);
                if(!code.matches("[A-Z]{3}"))continue;
                PointF q=xy(a.lat,a.lon);
                float rr=3.8f*inv;
                p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(1.15f*inv);
                p.setColor(Color.argb((int)(190*blend),Color.red(CYAN),Color.green(CYAN),Color.blue(CYAN)));
                c.drawCircle(q.x,q.y,rr,p);c.drawLine(q.x-rr*1.3f,q.y,q.x+rr*1.3f,q.y,p);c.drawLine(q.x,q.y-rr*1.3f,q.x,q.y+rr*1.3f,p);
                p.setStyle(Paint.Style.FILL);p.setTypeface(Typeface.create(Typeface.MONOSPACE,Typeface.BOLD));p.setTextSize(12f*inv);
                float tw=p.measureText(code);
                RectF box=new RectF(q.x+6*inv,q.y-17*inv,q.x+6*inv+tw,q.y-3*inv);
                boolean hit=false;for(RectF r:occupied)if(RectF.intersects(r,box)){hit=true;break;}
                if(hit)continue;
                occupied.add(box);
                p.setColor(Color.argb((int)(220*blend),Color.red(CYAN),Color.green(CYAN),Color.blue(CYAN)));
                c.drawText(code,q.x+6*inv,q.y-5*inv,p);
                apShown++;
            }
            p.setTypeface(Typeface.MONOSPACE);p.setStyle(Paint.Style.FILL);
        }


        private void drawWorldTraffic(Canvas c,float sx,float sy){
            if(spaceMode||cameraMode!=3)return;
            ArrayList<WorldContact> copy;
            synchronized(worldTraffic){copy=new ArrayList<WorldContact>(worldTraffic);}
            if(copy.isEmpty())return;
            float inv=1/Math.max(sx,sy);
            p.setStyle(Paint.Style.FILL);
            int total=copy.size();
            // Dense global view: tiny targets, no labels. Selected aircraft gets a halo.
            for(WorldContact a:copy){
                if(a==null||Double.isNaN(a.lat)||Double.isNaN(a.lon))continue;
                PointF q=xy(a.lat,a.lon);
                boolean sel=false;
                int col=a.military?Color.rgb(255,102,119):(a.onGround?DIM:GREEN);
                p.setColor(col);
                c.drawCircle(q.x,q.y,(sel?4.5f:2.55f)*inv,p);
                if(sel){p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(1.4f*inv);p.setColor(AMBER);c.drawCircle(q.x,q.y,7.5f*inv,p);p.setStyle(Paint.Style.FILL);}
            }
            p.setTextAlign(Paint.Align.LEFT);p.setTypeface(Typeface.create(Typeface.MONOSPACE,Typeface.BOLD));
            p.setTextSize(12f*inv);p.setColor(CYAN);
            c.drawText("WORLD TRAFFIC  "+total+"  //  "+worldTrafficSource,8*inv,18*inv,p);
            p.setTypeface(Typeface.MONOSPACE);
        }

        private void drawJourneyRoute(Canvas c,float sx,float sy){
            if(oLat==null||dLat==null)return;
            float inv=1/Math.max(sx,sy);
            if(liveLat!=null&&liveLon!=null&&cameraMode!=2){
                // In FOLLOW and ZOOM TO PATH, make the route visually relevant to the
                // selected aircraft: travelled section is dim, remaining journey is bright.
                p.setAlpha(95);
                drawGreatCircle(c,oLat,oLon,liveLat,liveLon,sx,sy);
                p.setAlpha(255);
                drawGreatCircle(c,liveLat,liveLon,dLat,dLon,sx,sy);

                PointF dest=xy(dLat,dLon);
                if(cameraMode==1)dot(c,dest,"DEST",CYAN,sx,sy);
            }else{
                // FRAME ROUTE retains the true full origin -> destination geometry.
                drawGreatCircle(c,oLat,oLon,dLat,dLon,sx,sy);
                dot(c,xy(oLat,oLon),"ORIGIN",AMBER,sx,sy);
                dot(c,xy(dLat,dLon),"DEST",CYAN,sx,sy);
            }
            p.setAlpha(255);
        }

        @Override protected void onDraw(Canvas c){
            super.onDraw(c);updateCamera();

            float[] dc=displayCameraBounds();
            float drawW=dc[2],drawH=dc[3];
            float sx=1f/Math.max(.0001f,drawW),sy=1f/Math.max(.0001f,drawH);

            // Pre-rendered bundled aviation tiles: instant, deterministic, no network.
            boolean bundledMap=drawBundledAviationMap(c);

            c.save();
            c.translate(getWidth()*.5f-dc[0]*getWidth()*sx,getHeight()*.5f-dc[1]*getHeight()*sy);
            c.scale(sx,sy);

            Bitmap wm=themedWorldMap();
            if(wm!=null&&(spaceMode||cameraMode==3)){
                p.setAlpha(228);p.setFilterBitmap(true);
                c.drawBitmap(wm,null,new Rect(0,0,getWidth(),getHeight()),p);
                p.setFilterBitmap(false);p.setAlpha(255);
            }
            if(!bundledMap&&cameraMode!=3&&!spaceMode)drawLocalBackdrop(c,sx,sy,1f);

            // Keep the coarse global grid only while the world raster is relevant.
            if(spaceMode||cameraMode==3){
                p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(Math.max(.6f,2/Math.max(sx,sy)));
                p.setColor(Color.argb(90,Color.red(CYAN),Color.green(CYAN),Color.blue(CYAN)));
                for(int i=1;i<6;i++)c.drawLine(getWidth()*i/6f,0,getWidth()*i/6f,getHeight(),p);
                for(int i=1;i<4;i++)c.drawLine(0,getHeight()*i/4f,getWidth(),getHeight()*i/4f,p);
            }

            drawWorldTraffic(c,sx,sy);
            drawJourneyRoute(c,sx,sy);

            // Local selected-target hierarchy: subdued HOME -> amber track -> selected aircraft.
            if(homeLat!=null&&homeLon!=null&&!spaceMode){
                if(liveLat!=null&&liveLon!=null&&cameraMode==0){
                    float inv=1/Math.max(sx,sy);
                    PointF hp=xy(homeLat,homeLon),lp=xy(liveLat,liveLon);
                    p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(2.0f*inv);
                    p.setPathEffect(new DashPathEffect(new float[]{8*inv,5*inv},0));
                    p.setColor(Color.argb(205,Color.red(AMBER),Color.green(AMBER),Color.blue(AMBER)));
                    c.drawLine(hp.x,hp.y,lp.x,lp.y,p);p.setPathEffect(null);
                }
                dot(c,xy(homeLat,homeLon),"HOME",Color.rgb(235,185,70),sx,sy);
            }
            if(spaceMode){
                p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(2/Math.max(sx,sy));p.setColor(CYAN);Path orbit=new Path();
                for(int i=0;i<=120;i++){double lo=-180+i*3.0;double la=51.6*Math.sin(Math.toRadians(lo*1.25));PointF q=xy(la,lo);if(i==0)orbit.moveTo(q.x,q.y);else orbit.lineTo(q.x,q.y);}c.drawPath(orbit,p);
                PointF home=xy(homeLat==null?0:homeLat,homeLon==null?0:homeLon);dot(c,home,"HOME",AMBER,sx,sy);if(issLat!=null)dot(c,xy(issLat,issLon),"ISS",CYAN,sx,sy);if(sunLat!=null)dot(c,xy(sunLat,sunLon),"SUN",AMBER,sx,sy);if(moonLat!=null)dot(c,xy(moonLat,moonLon),"MOON",CYAN,sx,sy);
            }
            if(launchLat!=null)dot(c,xy(launchLat,launchLon),launchName,AMBER,sx,sy);
            if(liveLat!=null){
                PointF lp=xy(liveLat,liveLon);drawLiveAircraft(c,lp,sx,sy);
                if(introHoldUntil>System.currentTimeMillis()){
                    float inv=1/Math.max(sx,sy);p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(3*inv);int a=((System.currentTimeMillis()/90)%2==0)?255:70;
                    p.setColor(Color.argb(a,Color.red(AMBER),Color.green(AMBER),Color.blue(AMBER)));c.drawRect(lp.x-20*inv,lp.y-20*inv,lp.x+20*inv,lp.y+20*inv,p);postInvalidateOnAnimation();
                }
            }
            c.restore();

        }

        private void drawLiveAircraft(Canvas c,PointF q,float sx,float sy){
            float inv=1/Math.max(sx,sy),r=14*inv;int col=liveMilitary?Color.rgb(255,102,119):AMBER;
            // Halo makes the tracked aircraft unmistakable against map labels/roads.
            p.setStyle(Paint.Style.FILL);p.setColor(Color.argb(55,Color.red(col),Color.green(col),Color.blue(col)));
            c.drawCircle(q.x,q.y,22*inv,p);
            p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(2.4f*inv);p.setColor(col);
            c.drawCircle(q.x,q.y,18*inv,p);
            p.setColor(col);p.setStrokeWidth(2.8f*inv);p.setStyle(Paint.Style.STROKE);
            if("HELICOPTER".equals(liveKind)){c.drawCircle(q.x,q.y,r*.35f,p);c.drawLine(q.x-r,q.y-r*.5f,q.x+r,q.y+r*.3f,p);c.drawLine(q.x-r,q.y+r*.3f,q.x+r,q.y-r*.5f,p);}
            else if("GLIDER".equals(liveKind)||"LIGHT".equals(liveKind)){c.drawLine(q.x,q.y-r,q.x,q.y+r,p);c.drawLine(q.x-r,q.y-r*.2f,q.x+r,q.y-r*.2f,p);}
            else if("UAV".equals(liveKind)){c.drawLine(q.x-r*.7f,q.y-r*.7f,q.x+r*.7f,q.y+r*.7f,p);c.drawLine(q.x-r*.7f,q.y+r*.7f,q.x+r*.7f,q.y-r*.7f,p);}
            else{p.setStyle(Paint.Style.FILL);Path x=new Path();x.moveTo(q.x,q.y-r);x.lineTo(q.x+r*.2f,q.y-r*.3f);x.lineTo(q.x+r,q.y+r*.35f);x.lineTo(q.x,q.y+r*.7f);x.lineTo(q.x-r,q.y+r*.35f);x.lineTo(q.x-r*.2f,q.y-r*.3f);x.close();c.drawPath(x,p);}
            p.setStyle(Paint.Style.FILL);p.setTypeface(Typeface.create(Typeface.MONOSPACE,Typeface.BOLD));p.setColor(col);p.setTextSize(17*inv);c.drawText((liveMilitary?"MIL ":"")+liveLabel,q.x+20*inv,q.y-10*inv,p);p.setTypeface(Typeface.MONOSPACE);
        }
        private void dot(Canvas c,PointF q,String label,int col,float sx,float sy){float inv=1/Math.max(sx,sy);p.setStyle(Paint.Style.FILL);p.setColor(col);c.drawCircle(q.x,q.y,7*inv,p);p.setTextSize(15*inv);c.drawText(label,q.x+9*inv,q.y-7*inv,p);}
    }

    public static class IssView extends View {
        private final Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);
        private Bitmap referenceImage;
        IssView(Context c){super(c);setBackgroundColor(Color.TRANSPARENT);}
        void setReferenceImage(Bitmap b){referenceImage=b;invalidate();}
        @Override protected void onDraw(Canvas c){
            super.onDraw(c);
            float w=getWidth(),h=getHeight();
            if(referenceImage!=null&&!referenceImage.isRecycled()){
                float pad=8f;
                RectF dst=new RectF(pad,pad,w-pad,h-pad);
                p.setAlpha(255);p.setFilterBitmap(true);
                c.drawBitmap(referenceImage,null,dst,p);
                p.setFilterBitmap(false);
                p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(2f);p.setColor(CYAN);
                c.drawRect(dst,p);
                return;
            }
            float cx=w/2,cy=h/2;
            float sc=Math.min(w/500f,h/260f);
            c.save();c.scale(sc,sc,cx,cy);
            p.setStyle(Paint.Style.STROKE);p.setStrokeCap(Paint.Cap.ROUND);
            p.setStrokeWidth(5f);p.setColor(Color.rgb(205,218,214));
            c.drawRect(cx-58,cy-30,cx+58,cy+30,p);
            c.drawLine(cx-45,cy-30,cx-45,cy+30,p);
            c.drawLine(cx+45,cy-30,cx+45,cy+30,p);
            c.drawLine(cx,cy-84,cx,cy+84,p);
            c.drawRect(cx-17,cy-84,cx+17,cy-31,p);
            c.drawRect(cx-17,cy+31,cx+17,cy+84,p);
            c.drawCircle(cx+72,cy-5,18,p);
            c.drawLine(cx-58,cy,cx-202,cy,p);
            c.drawLine(cx+58,cy,cx+202,cy,p);
            for(int side=-1;side<=1;side+=2){
                float x=cx+side*137;
                p.setStyle(Paint.Style.FILL);p.setColor(Color.rgb(18,103,150));
                c.drawRect(x-56,cy-70,x+56,cy+70,p);
                p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(2f);p.setColor(CYAN);
                for(int i=-2;i<=2;i++)c.drawLine(x+i*22,cy-70,x+i*22,cy+70,p);
                for(int j=-3;j<=3;j++)c.drawLine(x-56,cy+j*20,x+56,cy+j*20,p);
            }
            p.setStyle(Paint.Style.FILL);p.setColor(AMBER);c.drawCircle(cx-67,cy+5,6,p);c.drawCircle(cx+67,cy+5,6,p);
            p.setColor(TEXT);p.setTextAlign(Paint.Align.CENTER);p.setTypeface(Typeface.create(Typeface.MONOSPACE,Typeface.BOLD));p.setTextSize(18);
            c.drawText("ISS REFERENCE // OFFLINE SCHEMATIC",cx,cy+118,p);
            p.setTextAlign(Paint.Align.LEFT);
            c.restore();
        }
    }

    public static class SolarSystemView extends View {
        private final Paint p=new Paint(Paint.ANTI_ALIAS_FLAG); SolarSystemView(Context c){super(c);p.setTypeface(Typeface.MONOSPACE);setBackgroundColor(Color.TRANSPARENT);}
        @Override protected void onDraw(Canvas c){super.onDraw(c);float cx=getWidth()/2f,cy=getHeight()/2f,r=Math.min(getWidth(),getHeight())*.44f;p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(1.4f);p.setColor(Color.argb(115,Color.red(GREEN),Color.green(GREEN),Color.blue(GREEN)));for(int i=1;i<=8;i++)c.drawCircle(cx,cy,r*i/8f,p);p.setStyle(Paint.Style.FILL);p.setColor(AMBER);c.drawCircle(cx,cy,25,p);p.setTextAlign(Paint.Align.CENTER);p.setTextSize(16);p.setColor(TEXT);c.drawText("SUN",cx,cy+44,p);String[] names={"MERCURY","VENUS","EARTH","MARS","JUPITER","SATURN","URANUS","NEPTUNE"};int[] cols={TEXT,AMBER,CYAN,Color.rgb(255,120,80),Color.rgb(230,190,140),Color.rgb(225,205,140),CYAN,Color.rgb(110,160,255)};double day=System.currentTimeMillis()/86400000.0;for(int i=0;i<8;i++){double a=day/(20+i*31)+i*.9;float rr=r*(i+1)/8f;float x=(float)(cx+Math.cos(a)*rr),y=(float)(cy+Math.sin(a)*rr);p.setColor(cols[i]);float pr=i==4?15:(i==5?14:(i>=6?11:10));c.drawCircle(x,y,pr,p);if(i==5){p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(1.4f);c.drawOval(new RectF(x-16,y-6,x+16,y+6),p);p.setStyle(Paint.Style.FILL);}p.setTextSize(16);p.setTypeface(Typeface.create(Typeface.MONOSPACE,Typeface.BOLD));p.setTextAlign(Paint.Align.LEFT);c.drawText(names[i],x+pr+5,y-7,p);}p.setTypeface(Typeface.MONOSPACE);p.setColor(DIM);p.setTextSize(9);p.setTextAlign(Paint.Align.CENTER);c.drawText("LIVE APPROXIMATION • NOT FOR ASTRONOMICAL POINTING",cx,getHeight()-8,p);p.setTextAlign(Paint.Align.LEFT);}
    }

    public static class SkyPanoramaView extends View {
        private final Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);private final Random rnd=new Random(9123);private final float[] sx=new float[220],sy=new float[220];private final Map<String,ArrayList<SkyAircraftPoint>> aircraftTrails=new HashMap<String,ArrayList<SkyAircraftPoint>>();private List<Aircraft> aircraft=new ArrayList<Aircraft>();private List<SmallBodyTarget> smallBodies=new ArrayList<SmallBodyTarget>();private List<AstroTarget> celestial=new ArrayList<AstroTarget>();private List<SatelliteTarget> satellites=new ArrayList<SatelliteTarget>();private boolean showAircraft=true,showSmallBodies=true,showSatellites=true;private int orientation=180;private String selected;private OnObjectTapListener listener;interface OnObjectTapListener{void onAircraft(Aircraft a);void onCelestial(String name,int drawable);}
        SkyPanoramaView(Context c){super(c);p.setTypeface(Typeface.MONOSPACE);for(int i=0;i<sx.length;i++){sx[i]=rnd.nextFloat();sy[i]=rnd.nextFloat();}setBackgroundColor(Color.TRANSPARENT);}
        void setAircraft(List<Aircraft> a){
            long now=System.currentTimeMillis();
            HashSet<String> live=new HashSet<String>();
            for(Aircraft ac:a){
                if(ac.hex==null)continue;live.add(ac.hex);
                double elev=Math.min(85,ac.altitudeFeet==null?0:ac.altitudeFeet/450.0);
                ArrayList<SkyAircraftPoint> t=aircraftTrails.get(ac.hex);
                if(t==null){t=new ArrayList<SkyAircraftPoint>();aircraftTrails.put(ac.hex,t);}
                if(t.isEmpty() || now-t.get(t.size()-1).time>=8000){
                    t.add(new SkyAircraftPoint(ac.bearing,elev,now));
                    while(t.size()>18)t.remove(0);
                }
            }
            Iterator<Map.Entry<String,ArrayList<SkyAircraftPoint>>> it=aircraftTrails.entrySet().iterator();
            while(it.hasNext()){
                Map.Entry<String,ArrayList<SkyAircraftPoint>> e=it.next();
                ArrayList<SkyAircraftPoint> t=e.getValue();
                if(!live.contains(e.getKey()) && (t.isEmpty()||now-t.get(t.size()-1).time>180000))it.remove();
            }
            aircraft=new ArrayList<Aircraft>(a);invalidate();
        }void setSmallBodies(List<SmallBodyTarget> a){smallBodies=new ArrayList<SmallBodyTarget>(a);invalidate();}void setCelestial(List<AstroTarget> a){celestial=new ArrayList<AstroTarget>(a);invalidate();}void setSatellites(List<SatelliteTarget> a){satellites=new ArrayList<SatelliteTarget>(a);invalidate();}void setShowAircraft(boolean v){showAircraft=v;invalidate();}void setShowSmallBodies(boolean v){showSmallBodies=v;invalidate();}void setShowSatellites(boolean v){showSatellites=v;invalidate();}void setOrientation(int o){orientation=((o%360)+360)%360;invalidate();}void setSelected(String h){selected=h;invalidate();}void setOnObjectTapListener(OnObjectTapListener l){listener=l;}
        private PointF sky(double az,double alt,float w,float h){double rel=((az-orientation+540)%360)-180;if(Math.abs(rel)>92||alt<-5)return null;float x=(float)(w*(.5+rel/184.0)),y=(float)(h*(.78-Math.min(1,Math.max(-.055,alt/90.0))*.58));return new PointF(x,y);}
        private static class SkyAircraftPoint{final double az,alt;final long time;SkyAircraftPoint(double a,double e,long t){az=a;alt=e;time=t;}}
        private void drawAircraftTrack(Canvas c,Aircraft a,float w,float h,int color){
            if(a.hex==null)return;
            ArrayList<SkyAircraftPoint> t=aircraftTrails.get(a.hex);
            if(t!=null && t.size()>1){
                Path path=new Path();boolean started=false;PointF prev=null;
                for(SkyAircraftPoint sp:t){
                    PointF q=sky(sp.az,sp.alt,w,h);if(q==null){prev=null;continue;}
                    if(!started||prev==null){path.moveTo(q.x,q.y);started=true;}
                    else if(Math.abs(q.x-prev.x)<w*.45f)path.lineTo(q.x,q.y);
                    else path.moveTo(q.x,q.y);
                    prev=q;
                }
                if(started){p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(1.6f);p.setColor(Color.argb(120,Color.red(color),Color.green(color),Color.blue(color)));c.drawPath(path,p);}
            }
            double az=a.bearing,alt=Math.min(85,a.altitudeFeet==null?0:a.altitudeFeet/450.0),da=0,de=0;
            if(t!=null && t.size()>1){
                SkyAircraftPoint p0=t.get(t.size()-2),p1=t.get(t.size()-1);
                da=((p1.az-p0.az+540)%360)-180;de=p1.alt-p0.alt;
            }else if(a.track!=null){da=Math.sin(Math.toRadians(a.track-a.bearing))*5.0;}
            PointF q0=sky(az,alt,w,h),q1=sky((az+da*2.0+360)%360,Math.max(-5,Math.min(90,alt+de*2.0)),w,h);
            if(q0!=null&&q1!=null&&Math.abs(q1.x-q0.x)<w*.45f){
                p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(1.4f);p.setColor(Color.argb(150,Color.red(color),Color.green(color),Color.blue(color)));p.setPathEffect(new DashPathEffect(new float[]{7,5},0));c.drawLine(q0.x,q0.y,q1.x,q1.y,p);p.setPathEffect(null);
            }
            p.setStyle(Paint.Style.FILL);
        }

        @Override protected void onDraw(Canvas c){super.onDraw(c);float w=getWidth(),h=getHeight();ArrayList<RectF> usedLabels=new ArrayList<RectF>();RadialGradient ng=new RadialGradient(w*.25f,h*.28f,Math.max(w,h)*.65f,new int[]{Color.argb(50,24,70,93),Color.argb(20,24,35,72),Color.argb(5,0,0,0)},new float[]{0,.5f,1},Shader.TileMode.CLAMP);p.setShader(ng);c.drawRect(0,0,w,h,p);p.setShader(null);p.setStyle(Paint.Style.FILL);for(int i=0;i<sx.length;i++){p.setColor(Color.argb(55+(i%5)*24,Color.red(TEXT),Color.green(TEXT),Color.blue(TEXT)));c.drawCircle(sx[i]*w,sy[i]*h*.9f,1+(i%4)*.55f,p);}p.setColor(Color.argb(70,Color.red(CYAN),Color.green(CYAN),Color.blue(CYAN)));p.setStrokeWidth(1);c.drawLine(0,h*.49f,w,h*.49f,p);c.drawLine(0,h*.78f,w,h*.78f,p);p.setTextSize(11);p.setColor(CYAN);c.drawText("45°",6,h*.49f-4,p);c.drawText("HORIZON",6,h*.78f-4,p);
            for(AstroTarget a:celestial){
                PointF q=sky(a.azimuth,a.altitude,w,h);if(q==null)continue;drawSkyObject(c,a,q,usedLabels);}
            if(showSatellites)for(SatelliteTarget sat:satellites){PointF q=sky(sat.azimuth,sat.altitude,w,h);if(q==null)continue;p.setColor(Color.rgb(183,140,255));p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(2);float d=9;c.drawRect(q.x-d,q.y-d,q.x+d,q.y+d,p);c.drawLine(q.x-d*2.2f,q.y,q.x+d*2.2f,q.y,p);p.setStyle(Paint.Style.FILL);p.setTextSize(13);c.drawText(sat.name,q.x+16,labelY(sat.name,q.x+16,q.y-8,usedLabels),p);}
            if(showSmallBodies)for(SmallBodyTarget sb:smallBodies){PointF q=sky(sb.azimuth,sb.altitude,w,h);if(q==null)continue;p.setColor("COMET".equals(sb.type)?Color.rgb(102,255,210):AMBER);p.setStyle(Paint.Style.FILL);if("COMET".equals(sb.type)){c.drawCircle(q.x,q.y,9,p);p.setStrokeWidth(3);c.drawLine(q.x+8,q.y+3,q.x+30,q.y+14,p);}else{Path d=new Path();d.moveTo(q.x,q.y-10);d.lineTo(q.x+10,q.y);d.lineTo(q.x,q.y+10);d.lineTo(q.x-10,q.y);d.close();c.drawPath(d,p);}p.setTextSize(12);c.drawText(sb.name,q.x+14,labelY(sb.name,q.x+14,q.y-9,usedLabels),p);}
            int n=0;if(showAircraft)for(Aircraft a:aircraft){if(n++>=24)break;PointF q=sky(a.bearing,Math.min(85,a.altitudeFeet==null?0:a.altitudeFeet/450.0),w,h);if(q==null)continue;boolean sel=a.hex!=null&&a.hex.equals(selected);int col=a.military?Color.rgb(255,102,119):(a.onGround?AMBER:CYAN);drawAircraftTrack(c,a,w,h,col);drawAircraftSkyIcon(c,a,q.x,q.y,col);if(sel){p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(3);p.setColor(AMBER);c.drawCircle(q.x,q.y,18,p);}p.setStyle(Paint.Style.FILL);p.setColor(col);p.setTextSize(sel?15:13);String al=(a.military?"MIL ":"")+a.callsign;c.drawText(al,q.x+18,labelY(al,q.x+18,q.y-8,usedLabels),p);}}
        @Override public boolean onTouchEvent(MotionEvent e){if(e.getAction()!=MotionEvent.ACTION_UP)return true;float w=getWidth(),h=getHeight();Aircraft best=null;double bestD=55;for(Aircraft a:aircraft){PointF q=sky(a.bearing,Math.min(85,a.altitudeFeet==null?0:a.altitudeFeet/450.0),w,h);if(q==null)continue;double d=Math.hypot(e.getX()-q.x,e.getY()-q.y);if(d<bestD){bestD=d;best=a;}}if(best!=null&&listener!=null){listener.onAircraft(best);return true;}if(listener!=null){for(AstroTarget a:celestial){PointF q=sky(a.azimuth,a.altitude,w,h);if(q!=null&&Math.hypot(e.getX()-q.x,e.getY()-q.y)<34){listener.onCelestial(a.name,a.drawable);return true;}}for(SatelliteTarget sat:satellites){PointF q=sky(sat.azimuth,sat.altitude,w,h);if(q!=null&&Math.hypot(e.getX()-q.x,e.getY()-q.y)<34){listener.onCelestial(sat.name,0);return true;}}for(SmallBodyTarget sb:smallBodies){PointF q=sky(sb.azimuth,sb.altitude,w,h);if(q!=null&&Math.hypot(e.getX()-q.x,e.getY()-q.y)<36){listener.onCelestial(sb.name,R.drawable.sky_star);return true;}}}return true;}
        private int planetColor(String name){if("MERCURY".equals(name))return Color.rgb(180,176,168);if("VENUS".equals(name))return Color.rgb(236,205,145);if("MARS".equals(name))return Color.rgb(224,105,71);if("JUPITER".equals(name))return Color.rgb(213,170,126);if("SATURN".equals(name))return Color.rgb(224,197,142);if("URANUS".equals(name))return Color.rgb(111,220,218);if("NEPTUNE".equals(name))return Color.rgb(83,115,222);return CYAN;}
        private float labelY(String label,float x,float desired,ArrayList<RectF> used){p.setTextSize(14);float w=p.measureText(label)+8,h=18,y=desired;for(int tries=0;tries<8;tries++){RectF r=new RectF(x,y-h,x+w,y+4);boolean hit=false;for(RectF u:used)if(RectF.intersects(r,u)){hit=true;break;}if(!hit){used.add(r);return y;}y+=18;}used.add(new RectF(x,y-h,x+w,y+4));return y;}
        private void drawSkyObject(Canvas c,AstroTarget a,PointF q,ArrayList<RectF> usedLabels){
            int col="SUN".equals(a.type)?AMBER:("MOON".equals(a.type)?Color.rgb(220,235,240):("PLANET".equals(a.type)?planetColor(a.name):TEXT));
            p.setStyle(Paint.Style.FILL);p.setColor(col);
            if("STAR".equals(a.type)){
                float r=Math.max(5,(float)(9-a.magnitude));Path st=new Path();for(int i=0;i<10;i++){double an=i*Math.PI/5-Math.PI/2;float rr=(i%2==0)?r:r*.42f;float x=(float)(q.x+Math.cos(an)*rr),y=(float)(q.y+Math.sin(an)*rr);if(i==0)st.moveTo(x,y);else st.lineTo(x,y);}st.close();c.drawPath(st,p);
            }else if("SUN".equals(a.type)){
                float r=15;c.drawCircle(q.x,q.y,r,p);p.setStrokeWidth(2);for(int i=0;i<8;i++){double an=i*Math.PI/4;c.drawLine((float)(q.x+Math.cos(an)*20),(float)(q.y+Math.sin(an)*20),(float)(q.x+Math.cos(an)*27),(float)(q.y+Math.sin(an)*27),p);}
            }else if("MOON".equals(a.type)){
                float r=14;c.drawCircle(q.x,q.y,r,p);p.setColor(Color.rgb(3,12,18));c.drawCircle(q.x+7,q.y-3,12,p);
            }else if("PLANET".equals(a.type)){
                float r="JUPITER".equals(a.name)?12:("SATURN".equals(a.name)?11:9);c.drawCircle(q.x,q.y,r,p);if("SATURN".equals(a.name)){p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(2);c.drawOval(new RectF(q.x-18,q.y-6,q.x+18,q.y+6),p);}
            }
            p.setStyle(Paint.Style.FILL);p.setColor(col);p.setTextSize("PLANET".equals(a.type)?15:14);p.setTypeface(Typeface.create(Typeface.MONOSPACE,Typeface.BOLD));c.drawText(a.name,q.x+18,labelY(a.name,q.x+18,q.y-8,usedLabels),p);p.setTypeface(Typeface.MONOSPACE);
        }
        private void drawAircraftSkyIcon(Canvas c,Aircraft a,float x,float y,int color){
            c.save();c.rotate((float)(a.track==null?a.bearing:a.track),x,y);p.setColor(color);p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(2.5f);p.setStrokeCap(Paint.Cap.ROUND);float r=11;String k=a.kind();
            if("HELICOPTER".equals(k)){c.drawCircle(x,y-1,r*.35f,p);c.drawLine(x,y+2,x,y+r,p);c.drawLine(x-r,y-r*.5f,x+r,y+r*.3f,p);c.drawLine(x-r,y+r*.3f,x+r,y-r*.5f,p);}
            else if("GLIDER".equals(k)||"LIGHT".equals(k)){c.drawLine(x,y-r,x,y+r,p);c.drawLine(x-r,y-r*.2f,x+r,y-r*.2f,p);c.drawLine(x-r*.35f,y+r*.65f,x+r*.35f,y+r*.65f,p);}
            else if("BALLOON".equals(k)){c.drawCircle(x,y-3,r*.6f,p);c.drawLine(x-4,y+4,x-2,y+10,p);c.drawLine(x+4,y+4,x+2,y+10,p);c.drawLine(x-2,y+10,x+2,y+10,p);}
            else if("PARACHUTE".equals(k)){c.drawArc(new RectF(x-r,y-r,x+r,y+r*.4f),180,180,false,p);c.drawLine(x-r,y,x,y+r,p);c.drawLine(x+r,y,x,y+r,p);c.drawCircle(x,y+r+3,2,p);}
            else if("UAV".equals(k)){c.drawLine(x-r*.7f,y-r*.7f,x+r*.7f,y+r*.7f,p);c.drawLine(x-r*.7f,y+r*.7f,x+r*.7f,y-r*.7f,p);for(int xx=-1;xx<=1;xx+=2)for(int yy=-1;yy<=1;yy+=2)c.drawCircle(x+xx*r*.7f,y+yy*r*.7f,3,p);}
            else if("FAST".equals(k)){p.setStyle(Paint.Style.FILL);Path q=new Path();q.moveTo(x,y-r);q.lineTo(x+r*.85f,y+r);q.lineTo(x,y+r*.55f);q.lineTo(x-r*.85f,y+r);q.close();c.drawPath(q,p);}
            else{p.setStyle(Paint.Style.FILL);Path q=new Path();q.moveTo(x,y-r);q.lineTo(x+r*.18f,y-r*.35f);q.lineTo(x+r,y+r*.25f);q.lineTo(x+r,y+r*.45f);q.lineTo(x+r*.2f,y+r*.1f);q.lineTo(x+r*.15f,y+r*.7f);q.lineTo(x,y+r*.85f);q.lineTo(x-r*.15f,y+r*.7f);q.lineTo(x-r*.2f,y+r*.1f);q.lineTo(x-r,y+r*.45f);q.lineTo(x-r,y+r*.25f);q.lineTo(x-r*.18f,y-r*.35f);q.close();c.drawPath(q,p);}
            c.restore();
        }
        private void body(Canvas c,float x,float y,float r,int col,String name){p.setColor(col);p.setStyle(Paint.Style.FILL);c.drawCircle(x,y,r,p);p.setTextSize(16);p.setTypeface(Typeface.create(Typeface.MONOSPACE,Typeface.BOLD));c.drawText(name,x+r+7,y-5,p);p.setTypeface(Typeface.MONOSPACE);}
    }

    public static class NebulaBackgroundView extends View {
        private final Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Random random=new Random(7319);
        private float[] starsX,starsY,starsR;
        private int[] starsA;

        NebulaBackgroundView(Context context){
            super(context);
            setLayerType(View.LAYER_TYPE_SOFTWARE,null);
        }

        private void ensureStars(){
            if(starsX!=null)return;
            final int count=460;
            starsX=new float[count];starsY=new float[count];starsR=new float[count];starsA=new int[count];
            for(int i=0;i<count;i++){
                starsX[i]=random.nextFloat();
                starsY[i]=random.nextFloat();
                starsR[i]=0.50f+random.nextFloat()*1.75f;
                starsA[i]=66+random.nextInt(150);
            }
        }

        @Override protected void onDraw(Canvas canvas){
            super.onDraw(canvas);
            ensureStars();
            int w=getWidth(),h=getHeight();
            if(w<=0||h<=0)return;

            canvas.drawColor(BG);

            // Subtle nebula clouds. Static, low contrast, cheap to render.
            RadialGradient g1=new RadialGradient(
                w*.18f,h*.30f,Math.max(w,h)*.48f,
                new int[]{
                    Color.argb(92,18,86,118),
                    Color.argb(52,25,48,92),
                    Color.TRANSPARENT
                },
                new float[]{0f,.42f,1f},Shader.TileMode.CLAMP);
            p.setShader(g1);canvas.drawRect(0,0,w,h,p);

            RadialGradient g2=new RadialGradient(
                w*.78f,h*.68f,Math.max(w,h)*.52f,
                new int[]{
                    Color.argb(78,50,42,122),
                    Color.argb(42,20,28,76),
                    Color.TRANSPARENT
                },
                new float[]{0f,.48f,1f},Shader.TileMode.CLAMP);
            p.setShader(g2);canvas.drawRect(0,0,w,h,p);

            RadialGradient g3=new RadialGradient(
                w*.55f,h*.08f,Math.max(w,h)*.34f,
                new int[]{
                    Color.argb(58,42,154,174),
                    Color.TRANSPARENT
                },
                new float[]{0f,1f},Shader.TileMode.CLAMP);
            p.setShader(g3);canvas.drawRect(0,0,w,h,p);
            p.setShader(null);

            // Deterministic starfield, inspired by the Windows Store version.
            for(int i=0;i<starsX.length;i++){
                int alpha=starsA[i];
                int tint=i%7==0?CYAN:TEXT;
                p.setColor(Color.argb(alpha,Color.red(tint),Color.green(tint),Color.blue(tint)));
                p.setStyle(Paint.Style.FILL);
                canvas.drawCircle(starsX[i]*w,starsY[i]*h,starsR[i],p);
            }

            // A few restrained brighter anchors.
            for(int i=0;i<28;i++){
                float x=((i*97)%997)/997f*w;
                float y=((i*193)%991)/991f*h;
                p.setColor(Color.argb(105,Color.red(CYAN),Color.green(CYAN),Color.blue(CYAN)));
                canvas.drawCircle(x,y,1.5f+(i%3)*.45f,p);
            }
        }
    }

    public static class WeatherIconView extends View {
        private final Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);private String condition="cloudy";
        WeatherIconView(Context c){super(c);setBackgroundColor(Color.TRANSPARENT);}
        void setCondition(String c){condition=c==null?"":c.toLowerCase(Locale.US);invalidate();}
        @Override protected void onDraw(Canvas c){super.onDraw(c);float w=getWidth(),h=getHeight(),cx=w*.52f,cy=h*.50f;boolean rain=condition.contains("rain")||condition.contains("shower");boolean cloud=condition.contains("cloud")||rain||condition.contains("overcast");boolean storm=condition.contains("thunder");p.setStyle(Paint.Style.STROKE);p.setStrokeCap(Paint.Cap.ROUND);p.setStrokeWidth(4);p.setColor(AMBER);c.drawCircle(cx-20,cy-13,22,p);for(int i=0;i<8;i++){double a=Math.toRadians(i*45);c.drawLine((float)(cx-20+Math.cos(a)*32),(float)(cy-13+Math.sin(a)*32),(float)(cx-20+Math.cos(a)*42),(float)(cy-13+Math.sin(a)*42),p);}if(cloud){p.setColor(TEXT);p.setStrokeWidth(5);RectF a=new RectF(cx-42,cy-4,cx+4,cy+30),b=new RectF(cx-10,cy-18,cx+42,cy+31);c.drawArc(a,190,210,false,p);c.drawArc(b,175,200,false,p);c.drawLine(cx-31,cy+28,cx+31,cy+28,p);}if(rain){p.setColor(CYAN);p.setStrokeWidth(3);for(int i=-2;i<=2;i++)c.drawLine(cx+i*14,cy+38,cx-5+i*14,cy+49,p);}if(storm){p.setColor(AMBER);Path q=new Path();q.moveTo(cx+7,cy+30);q.lineTo(cx-2,cy+49);q.lineTo(cx+8,cy+47);q.lineTo(cx,cy+64);c.drawPath(q,p);}}
    }

    public static class WeatherLocalView extends View {
        private final Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);private WeatherDisplay d;
        WeatherLocalView(Context c){super(c);p.setTypeface(Typeface.MONOSPACE);setWillNotDraw(false);}
        void setWeather(WeatherDisplay x){d=x;invalidate();}
        @Override protected void onDraw(Canvas c){super.onDraw(c);float w=getWidth(),h=getHeight();if(d==null){p.setColor(DIM);p.setTextSize(15);c.drawText("Loading local conditions…",12,30,p);return;}float top=7,heroH=Math.min(66,h*.38f);p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(1.5f);p.setColor(BORDER);c.drawRect(6,top,w-6,top+heroH,p);p.setStyle(Paint.Style.FILL);p.setColor(CYAN);p.setTextSize(29);p.setTypeface(Typeface.create(Typeface.MONOSPACE,Typeface.BOLD));c.drawText(d.condition,66,top+31,p);p.setTextSize(43);p.setTextAlign(Paint.Align.RIGHT);c.drawText(tempLabel(d.temperature,d.tempUnit),w-18,top+34,p);p.setTextAlign(Paint.Align.LEFT);p.setTypeface(Typeface.MONOSPACE);p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(3f);p.setColor(CYAN);c.drawCircle(31,top+29,10,p);c.drawOval(new RectF(18,top+31,48,top+49),p);float gridTop=top+heroH+7,cellW=(w-12)/3f,cellH=(h-gridTop-6)/2f;String[] lab={"HUMIDITY","CLOUD","PRECIP","GUSTS","SUNRISE","SUNSET"};String[] val={String.format(Locale.US,"%.0f%%",d.humidity),Double.isNaN(d.cloud)?"--":String.format(Locale.US,"%.0f%%",d.cloud),Double.isNaN(d.precip)?"--":String.format(Locale.US,"%.0f mm",d.precip),Double.isNaN(d.gust)?"--":String.format(Locale.US,"%.0f mph",d.gust),d.sunrise,d.sunset};for(int i=0;i<6;i++){int row=i/3,col=i%3;float l=6+col*cellW,t=gridTop+row*cellH;p.setStyle(Paint.Style.STROKE);p.setColor(BORDER);p.setStrokeWidth(1);c.drawRect(l,t,l+cellW-3,t+cellH-3,p);p.setStyle(Paint.Style.FILL);p.setColor(DIM);p.setTextSize(19f);c.drawText(lab[i],l+8,t+23,p);p.setColor(i>=4?AMBER:TEXT);p.setTextSize(33f);p.setTypeface(Typeface.create(Typeface.MONOSPACE,Typeface.BOLD));c.drawText(val[i],l+8,t+59,p);p.setTypeface(Typeface.MONOSPACE);}}
        private String tempLabel(double c,String unit){if(Double.isNaN(c))return "--";double v="°F".equals(unit)?c*9/5+32:c;return String.format(Locale.US,"%.0f%s",v,unit);}
    }

    public static class RadarNearestView extends View {
        private final Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);private List<Aircraft> rows=new ArrayList<Aircraft>();private boolean miles=true;private OnAircraftTapListener listener; private float textScale=1f;
        interface OnAircraftTapListener{void onTap(Aircraft a);} RadarNearestView(Context c){super(c);p.setTypeface(Typeface.MONOSPACE);setBackgroundColor(Color.TRANSPARENT);}void setAircraft(List<Aircraft> a,boolean m){rows=new ArrayList<Aircraft>(a.subList(0,Math.min(10,a.size())));miles=m;invalidate();}void setOnAircraftTapListener(OnAircraftTapListener l){listener=l;} void setTextScale(float v){textScale=v;invalidate();}
        @Override protected void onDraw(Canvas c){super.onDraw(c);float w=getWidth(),h=getHeight(),head=48f,rowH=39f;float[] xs={0,.23f,.42f,.64f,.82f,1f};p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(2);p.setColor(BORDER);c.drawRect(0,0,w,h,p);for(int i=1;i<xs.length-1;i++)c.drawLine(w*xs[i],0,w*xs[i],Math.min(h,head+rowH*rows.size()),p);c.drawLine(0,head,w,head,p);for(int i=1;i<=rows.size();i++)c.drawLine(0,head+i*rowH,w,head+i*rowH,p);String[] hd={"CALL","DIST","ALT","SPD","BRG"};p.setStyle(Paint.Style.FILL);p.setTextSize(11*textScale);p.setTextAlign(Paint.Align.CENTER);p.setColor(CYAN);for(int i=0;i<5;i++)c.drawText(hd[i],w*(xs[i]+xs[i+1])/2,31,p);p.setTextSize(10.5f*textScale);p.setColor(TEXT);for(int r=0;r<rows.size();r++){Aircraft a=rows.get(r);float y=head+r*rowH+rowH*.68f;String alt=a.altitudeFeet==null?"GROUND":String.format(Locale.US,"%,d",a.altitudeFeet);double sp=a.speedKnots==null?0:a.speedKnots*(miles?1.15078:1.852);String[] vv={a.callsign,miles?String.format(Locale.US,"%.1f mi",a.distanceKm*.621371):String.format(Locale.US,"%.1f km",a.distanceKm),alt,String.format(Locale.US,"%.0f",sp),String.format(Locale.US,"%.0f°",a.bearing)};for(int i=0;i<5;i++)c.drawText(vv[i],w*(xs[i]+xs[i+1])/2,y,p);}}
        @Override public boolean onTouchEvent(MotionEvent e){if(e.getAction()!=MotionEvent.ACTION_UP)return true;float head=48f,rowH=39f;int i=(int)((e.getY()-head)/rowH);if(i>=0&&i<rows.size()&&listener!=null)listener.onTap(rows.get(i));return true;}
    }

    public static class WeatherTrendView extends View {
        private final Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);private final String title;private final int accent;private double[] values;private String unit="";private boolean unavailable=false;
        WeatherTrendView(Context context,String title,int accent){super(context);this.title=title;this.accent=accent;p.setTypeface(Typeface.MONOSPACE);setBackgroundColor(Color.argb(118,Color.red(PANEL),Color.green(PANEL),Color.blue(PANEL)));}
        void setSeries(double[] series,String unit){values=series==null?null:series.clone();this.unit=unit==null?"":unit;unavailable=false;invalidate();}void setUnavailable(){unavailable=true;values=null;invalidate();}
        @Override protected void onDraw(Canvas c){super.onDraw(c);float w=getWidth(),h=getHeight();boolean temp=title.startsWith("TEMPERATURE");float split=w*.165f,left=split+10,right=w-10,top=22,bottom=h-7;p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(1);p.setColor(BORDER);c.drawRect(1,1,w-2,h-2,p);c.drawLine(split,7,split,h-7,p);p.setStyle(Paint.Style.FILL);p.setColor(CYAN);p.setTextSize(16f);c.drawText(title,8,19,p);p.setColor(DIM);p.setTextSize(14f);c.drawText(temp?"TREND // LAST 12 HOURS":"TREND // LAST 24 HOURS",split+8,19,p);if(unavailable||values==null||values.length==0){p.setColor(DIM);p.setTextSize(10);c.drawText("HISTORY BUILDING",left+18,h*.62f,p);return;}double min=Double.POSITIVE_INFINITY,max=Double.NEGATIVE_INFINITY,latest=Double.NaN;int count=0;for(double v:values)if(!Double.isNaN(v)){min=Math.min(min,v);max=Math.max(max,v);latest=v;count++;}if(count==0)return;if(Math.abs(max-min)<.001)max=min+1;p.setTypeface(Typeface.create(Typeface.MONOSPACE,Typeface.BOLD));p.setTextSize(temp?38:35);p.setColor(TEXT);p.setTextAlign(Paint.Align.LEFT);c.drawText(String.format(Locale.US,"%.0f %s",latest,unit).trim(),10,47,p);p.setTypeface(Typeface.MONOSPACE);p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(1);p.setColor(Color.argb(80,Color.red(GREEN),Color.green(GREEN),Color.blue(GREEN)));for(int g=0;g<=2;g++){float y=top+(bottom-top)*g/2f;c.drawLine(left,y,right,y,p);}float slot=(right-left)/Math.max(1,values.length);float bw=Math.max(2.5f,slot*.50f);p.setStyle(Paint.Style.FILL);p.setColor(accent);for(int i=0;i<values.length;i++){double v=values[i];if(Double.isNaN(v))continue;float x=left+slot*i+slot*.5f;float y=(float)(bottom-(v-min)/(max-min)*(bottom-top));c.drawRect(x-bw/2,y,x+bw/2,bottom,p);}}
    }

    static class WeatherHistoryData {
        final double[] temperature,humidity,wind,rain;
        final boolean fahrenheit;
        WeatherHistoryData(double[] t,double[] h,double[] w,double[] r,boolean f){temperature=t;humidity=h;wind=w;rain=r;fahrenheit=f;}
    }

    public static class WeatherHistoryView extends View {
        private final Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);
        private WeatherHistoryData data;
        WeatherHistoryView(Context c){super(c);p.setTypeface(Typeface.MONOSPACE);setBackgroundColor(Color.argb(118,Color.red(PANEL),Color.green(PANEL),Color.blue(PANEL)));}
        void setData(WeatherHistoryData d){data=d;invalidate();}
        @Override protected void onDraw(Canvas c){
            super.onDraw(c);
            if(data==null||data.temperature.length==0){
                p.setColor(DIM);p.setTextSize(18);c.drawText("HISTORY DATA UNAVAILABLE",20,35,p);return;
            }
            float left=112,right=getWidth()-18,top=18,rowH=(getHeight()-32)/4f;
            drawSeries(c,"TEMP",data.temperature,left,right,top,rowH,data.fahrenheit?"°F":"°C",false);
            drawSeries(c,"HUMID",data.humidity,left,right,top+rowH,rowH,"%",false);
            drawSeries(c,"WIND",data.wind,left,right,top+rowH*2,rowH,"km/h",false);
            drawSeries(c,"RAIN",data.rain,left,right,top+rowH*3,rowH,"mm",true);
        }
        private void drawSeries(Canvas c,String label,double[] v,float left,float right,float top,float h,String unit,boolean bars){
            double min=Double.POSITIVE_INFINITY,max=Double.NEGATIVE_INFINITY;
            for(double x:v)if(!Double.isNaN(x)){min=Math.min(min,x);max=Math.max(max,x);}
            if(min==Double.POSITIVE_INFINITY){min=0;max=1;}
            if(Math.abs(max-min)<1e-6){max=min+1;}
            p.setTextSize(15);p.setStyle(Paint.Style.FILL);p.setColor(GREEN);
            c.drawText(label,12,top+22,p);
            p.setTextSize(11);p.setColor(DIM);
            c.drawText(String.format(Locale.US,"%.1f%s",max,unit),12,top+41,p);
            c.drawText(String.format(Locale.US,"%.1f%s",min,unit),12,top+h-8,p);
            p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(1);p.setColor(Color.argb(70,Color.red(GREEN),Color.green(GREEN),Color.blue(GREEN)));
            for(int g=0;g<=4;g++){
                float y=top+8+(h-18)*g/4f;c.drawLine(left,y,right,y,p);
            }
            p.setColor(bars?AMBER:GREEN);p.setStrokeWidth(bars?4:2.5f);
            float prevX=0,prevY=0;
            for(int i=0;i<v.length;i++){
                if(Double.isNaN(v[i]))continue;
                float x=left+(right-left)*i/Math.max(1,v.length-1);
                float y=(float)(top+8+(max-v[i])/(max-min)*(h-18));
                if(bars)c.drawLine(x,top+h-10,x,y,p);
                else if(i>0)c.drawLine(prevX,prevY,x,y,p);
                prevX=x;prevY=y;
            }
            p.setColor(Color.argb(130,Color.red(DIM),Color.green(DIM),Color.blue(DIM)));p.setStrokeWidth(1);
            c.drawLine(left,top+h-2,right,top+h-2,p);
        }
    }

    public static class WeatherCompassView extends View {
        private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
        private double direction=Double.NaN,wind=Double.NaN,humidity=Double.NaN,cloud=Double.NaN;
        WeatherCompassView(Context c){super(c);paint.setTypeface(Typeface.MONOSPACE);setBackgroundColor(Color.argb(118,Color.red(PANEL),Color.green(PANEL),Color.blue(PANEL)));}
        void setWind(double direction,double wind,double humidity,double cloud){
            this.direction=direction;this.wind=wind;this.humidity=humidity;this.cloud=cloud;invalidate();
        }
        @Override protected void onDraw(Canvas canvas){
            super.onDraw(canvas);
            float cx=getWidth()*.28f,cy=getHeight()/2f,r=Math.min(getHeight()*.38f,getWidth()*.22f);
            paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(3);paint.setColor(Color.argb(120,Color.red(GREEN),Color.green(GREEN),Color.blue(GREEN)));
            canvas.drawCircle(cx,cy,r,paint);canvas.drawCircle(cx,cy,r*.65f,paint);
            for(int i=0;i<32;i++){
                double a=i*Math.PI/16-Math.PI/2;
                float inner=r*(i%8==0?.80f:.92f);
                canvas.drawLine((float)(cx+Math.cos(a)*inner),(float)(cy+Math.sin(a)*inner),(float)(cx+Math.cos(a)*r),(float)(cy+Math.sin(a)*r),paint);
            }
            paint.setStyle(Paint.Style.FILL);paint.setTextAlign(Paint.Align.CENTER);paint.setTextSize(18);paint.setColor(GREEN);
            canvas.drawText("N",cx,cy-r-8,paint);canvas.drawText("S",cx,cy+r+20,paint);canvas.drawText("W",cx-r-18,cy+6,paint);canvas.drawText("E",cx+r+18,cy+6,paint);
            if(!Double.isNaN(direction)){
                double a=Math.toRadians(direction-90);
                paint.setStrokeWidth(6);paint.setColor(AMBER);paint.setStyle(Paint.Style.STROKE);
                canvas.drawLine(cx,cy,(float)(cx+Math.cos(a)*r*.72f),(float)(cy+Math.sin(a)*r*.72f),paint);
            }
            paint.setStyle(Paint.Style.FILL);paint.setTextAlign(Paint.Align.LEFT);paint.setTextSize(18);paint.setColor(TEXT);
            float x=getWidth()*.57f,y=45;
            canvas.drawText("WIND FROM  "+compass(direction),x,y,paint);y+=34;
            canvas.drawText("SPEED      "+(Double.isNaN(wind)?"--":String.format(Locale.US,"%.1f",wind)),x,y,paint);y+=34;
            canvas.drawText("HUMIDITY   "+(Double.isNaN(humidity)?"--":String.format(Locale.US,"%.0f%%",humidity)),x,y,paint);y+=34;
            canvas.drawText("CLOUD      "+(Double.isNaN(cloud)?"--":String.format(Locale.US,"%.0f%%",cloud)),x,y,paint);
        }
        private String compass(double d){
            if(Double.isNaN(d))return "--";
            String[] p={"N","NE","E","SE","S","SW","W","NW"};
            return p[((int)Math.round(((d%360)+360)%360/45.0))%8];
        }
    }

    public static class AnalogClockView extends View {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Calendar time = Calendar.getInstance();

        AnalogClockView(Context context) {
            super(context);
            paint.setTypeface(Typeface.MONOSPACE);
            setBackgroundColor(Color.argb(118,Color.red(PANEL),Color.green(PANEL),Color.blue(PANEL)));
        }

        void setTime(Date date) {
            time.setTime(date);
            invalidate();
        }

        @Override protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            float cx = getWidth() / 2f;
            float cy = getHeight() / 2f;
            float radius = Math.min(getWidth(), getHeight()) * 0.445f;

            paint.setStyle(Paint.Style.FILL);
            paint.setColor(Color.argb(184,Color.red(PANEL),Color.green(PANEL),Color.blue(PANEL)));
            canvas.drawCircle(cx,cy,radius*1.12f,paint);

            paint.setStyle(Paint.Style.STROKE);
            paint.setColor(Color.argb(45,Color.red(GREEN),Color.green(GREEN),Color.blue(GREEN)));
            paint.setStrokeWidth(12f);
            canvas.drawCircle(cx, cy, radius+5f, paint);
            paint.setColor(Color.argb(80,Color.red(GREEN),Color.green(GREEN),Color.blue(GREEN)));
            paint.setStrokeWidth(2f);
            canvas.drawCircle(cx, cy, radius*.73f, paint);
            canvas.drawCircle(cx, cy, radius*.48f, paint);

            paint.setStrokeWidth(5f);
            paint.setColor(GREEN);
            canvas.drawCircle(cx, cy, radius, paint);

            // Windows Store-style outer instrument arcs.
            RectF arcBox=new RectF(cx-radius*1.09f,cy-radius*1.09f,cx+radius*1.09f,cy+radius*1.09f);
            paint.setStrokeCap(Paint.Cap.ROUND);
            paint.setStrokeWidth(4f);
            paint.setColor(Color.argb(145,Color.red(CYAN),Color.green(CYAN),Color.blue(CYAN)));
            canvas.drawArc(arcBox,205,48,false,paint);
            canvas.drawArc(arcBox,287,38,false,paint);
            canvas.drawArc(arcBox,345,42,false,paint);
            canvas.drawArc(arcBox,62,55,false,paint);
            paint.setColor(Color.argb(165,Color.red(AMBER),Color.green(AMBER),Color.blue(AMBER)));
            canvas.drawArc(arcBox,136,34,false,paint);

            for (int i = 0; i < 60; i++) {
                double a = Math.toRadians(i * 6 - 90);
                float outerX = (float)(cx + Math.cos(a) * radius);
                float outerY = (float)(cy + Math.sin(a) * radius);
                float inner = radius - (i % 5 == 0 ? 18f : 9f);
                float innerX = (float)(cx + Math.cos(a) * inner);
                float innerY = (float)(cy + Math.sin(a) * inner);
                paint.setColor(i % 5 == 0 ? CYAN : Color.rgb(35,90,62));
                paint.setStrokeWidth(i % 5 == 0 ? 3f : 1f);
                canvas.drawLine(innerX, innerY, outerX, outerY, paint);
            }

            int hour = time.get(Calendar.HOUR);
            int minute = time.get(Calendar.MINUTE);
            int second = time.get(Calendar.SECOND);

            drawHand(canvas, cx, cy, radius * .50f, (hour + minute / 60f) * 30f, 11f, AMBER);
            drawHand(canvas, cx, cy, radius * .80f, minute * 6f + second * .1f, 8f, TEXT);
            drawHand(canvas, cx, cy, radius * .80f, second * 6f, 3f, GREEN);

            paint.setStyle(Paint.Style.FILL);
            paint.setColor(GREEN);
            canvas.drawCircle(cx, cy, 7f, paint);

            paint.setTextAlign(Paint.Align.CENTER);
            paint.setTextSize(29f);
            paint.setColor(TEXT);
            for(int n=1;n<=12;n++){
                double a=Math.toRadians(n*30-90);
                float nr=radius-39f;
                canvas.drawText(String.valueOf(n),(float)(cx+Math.cos(a)*nr),(float)(cy+Math.sin(a)*nr+8f),paint);
            }

            // Native-style 34-layer phosphor seconds sweep.
            for(int i=34;i>=0;i--){
                double deg=second*6-i*1.15;
                double sa=Math.toRadians(deg-90);
                int alpha=(int)(8+(34-i)*5.4);
                paint.setStyle(Paint.Style.STROKE);
                paint.setStrokeWidth(i==0?3f:2f);
                paint.setColor(Color.argb(Math.min(190,alpha),Color.red(GREEN),Color.green(GREEN),Color.blue(GREEN)));
                canvas.drawLine(cx,cy,(float)(cx+Math.cos(sa)*radius*.84f),(float)(cy+Math.sin(sa)*radius*.84f),paint);
            }
        }

        private void drawHand(Canvas canvas, float cx, float cy, float length, float degrees, float width, int color) {
            double a = Math.toRadians(degrees - 90);
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeCap(Paint.Cap.ROUND);
            paint.setStrokeWidth(width);
            paint.setColor(color);
            canvas.drawLine(cx, cy, (float)(cx + Math.cos(a) * length), (float)(cy + Math.sin(a) * length), paint);
        }
    }

    private static boolean militaryHint(String hex,String callsign,String owner){
        String hx=hex==null?"":hex.trim().toUpperCase(Locale.US),cs=callsign==null?"":callsign.trim().toUpperCase(Locale.US),ow=owner==null?"":owner.trim().toUpperCase(Locale.US);
        try{
            long n=Long.parseLong(hx,16);
            if(n>=0x43C000L&&n<=0x43CFFFL)return true;
            if(n>=0xADF7C8L&&n<=0xAFFFFFL)return true;
        }catch(Exception ignored){}
        String[] prefixes={"RRR","RFR","ASCOT","NATO","CNV","IAM","BAF","GAF","FAF"};
        for(String p:prefixes)if(cs.startsWith(p))return true;
        String[] words={"ROYAL AIR FORCE","ROYAL NAVY","BRITISH ARMY","MINISTRY OF DEFENCE","MINISTRY OF DEFENSE","UNITED STATES AIR FORCE","US AIR FORCE","UNITED STATES NAVY","US NAVY","US ARMY","US MARINE","MILITARY","AIR FORCE","ARMED FORCES","NATO"};
        for(String w:words)if(ow.contains(w))return true;
        return false;
    }

    static class WorldContact {
        final float lat,lon;
        final boolean onGround,military;
        WorldContact(float lat,float lon,boolean onGround,boolean military){
            this.lat=lat;this.lon=lon;this.onGround=onGround;this.military=military;
        }
    }

    static class Aircraft {
        String hex,callsign,registration,type,description,category,squawk;
        double lat,lon,distanceKm,bearing;
        Double speedKnots,track;
        Integer altitudeFeet,verticalRate;
        boolean military,onGround;

        static Aircraft fromOpenSky(JSONArray s,double homeLat,double homeLon){
            Aircraft a=new Aircraft();
            a.hex=s.optString(0);
            a.callsign=s.optString(1).trim();
            if(a.callsign.length()==0)a.callsign=a.hex.toUpperCase(Locale.US);
            a.registration="";
            a.type="";
            a.description=s.optString(2);
            a.category="";
            a.squawk=s.optString(14);
            a.military=militaryHint(a.hex,a.callsign,"");
            a.lon=s.optDouble(5);a.lat=s.optDouble(6);
            a.onGround=s.optBoolean(8,false);
            if(!s.isNull(7))a.altitudeFeet=(int)Math.round(s.optDouble(7)*3.28084);
            if(!s.isNull(9))a.speedKnots=s.optDouble(9)*1.943844;
            if(!s.isNull(10))a.track=s.optDouble(10);
            if(!s.isNull(11))a.verticalRate=(int)Math.round(s.optDouble(11)*196.8504);
            double[] db=distanceBearing(homeLat,homeLon,a.lat,a.lon);
            a.distanceKm=db[0];a.bearing=db[1];
            return a;
        }

        static Aircraft from(JSONObject o,double homeLat,double homeLon){
            Aircraft a=new Aircraft();
            a.hex=o.optString("hex");
            a.callsign=o.optString("flight").trim();
            if(a.callsign.length()==0)a.callsign=o.optString("r",a.hex.toUpperCase(Locale.US));
            a.registration=o.optString("r").trim();
            a.type=o.optString("t").trim();
            a.description=o.optString("desc").trim();
            a.category=o.optString("category");
            a.squawk=o.optString("squawk");
            a.military=(o.optInt("dbFlags",0)&1)!=0||militaryHint(a.hex,a.callsign,o.optString("ownOp"));
            Object alt=o.opt("alt_baro");
            a.onGround="ground".equals(String.valueOf(alt));
            if(alt instanceof Number)a.altitudeFeet=((Number)alt).intValue();
            a.speedKnots=o.has("gs")?o.optDouble("gs"):null;
            a.track=o.has("track")?o.optDouble("track"):null;
            a.verticalRate=o.has("baro_rate")?(int)Math.round(o.optDouble("baro_rate")):null;
            a.lat=o.optDouble("lat");
            a.lon=o.optDouble("lon");
            double[] db=distanceBearing(homeLat,homeLon,a.lat,a.lon);
            a.distanceKm=db[0];a.bearing=db[1];
            return a;
        }

        String kind() {
            if (onGround) return "GROUND";
            if (category == null) return "UNKNOWN";
            String c=category.trim().toUpperCase(Locale.US);
            if ("A1".equals(c) || "B4".equals(c)) return "LIGHT";
            if ("A2".equals(c) || "A3".equals(c) || "A4".equals(c) || "A5".equals(c)) return "FIXED";
            if ("A6".equals(c)) return "FAST";
            if ("A7".equals(c)) return "HELICOPTER";
            if ("B1".equals(c)) return "GLIDER";
            if ("B2".equals(c)) return "BALLOON";
            if ("B3".equals(c)) return "PARACHUTE";
            if ("B6".equals(c)) return "UAV";
            if ("C1".equals(c) || "C2".equals(c)) return "GROUND";
            return "UNKNOWN";
        }

        static double[] distanceBearing(double lat1,double lon1,double lat2,double lon2){
            double p1=Math.toRadians(lat1),p2=Math.toRadians(lat2),dp=Math.toRadians(lat2-lat1),dl=Math.toRadians(lon2-lon1);
            double x=Math.sin(dp/2)*Math.sin(dp/2)+Math.cos(p1)*Math.cos(p2)*Math.sin(dl/2)*Math.sin(dl/2);
            double dist=6371.0088*2*Math.atan2(Math.sqrt(x),Math.sqrt(1-x));
            double y=Math.sin(dl)*Math.cos(p2),xx=Math.cos(p1)*Math.sin(p2)-Math.sin(p1)*Math.cos(p2)*Math.cos(dl);
            double br=(Math.toDegrees(Math.atan2(y,xx))+360)%360;
            return new double[]{dist,br};
        }
    }

    public static class RadarView extends View {
        private final Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);
        private List<Aircraft> data=new ArrayList<Aircraft>();
        private final Map<String,ArrayList<PointF>> trails=new HashMap<String,ArrayList<PointF>>();
        private int range=40;
        private int orientation=0;
        private boolean showTrails=true;
        private boolean miles=false;
        private boolean alertEnabled=true;
        private int alertRange=10;
        private String style="classic";
        private String selected=null;
        private int labelMode=0;
        private int vectorMinutes=2;
        private double homeLat=53.8,homeLon=-1.55;
        private long lastTapMillis=0;
        private String lastTapHex=null;
        private Bitmap offlineMap=null,themedOfflineMap=null;
        private int themedOfflineMapColor=0;
        private boolean geoDirty=true;
        private ArrayList<GeoPlace> radarPlaces=new ArrayList<GeoPlace>();
        private ArrayList<GeoAirport> radarAirports=new ArrayList<GeoAirport>();
        private ArrayList<GeoRunway> radarRunways=new ArrayList<GeoRunway>();
        private Bitmap radarBackgroundCache=null;
        private int radarBackgroundW=0,radarBackgroundH=0,radarBackgroundRange=-1,radarBackgroundOrientation=-1,radarBackgroundTheme=0;
        private double radarBackgroundLat=999,radarBackgroundLon=999;
        private OnAircraftTapListener listener;
        interface OnAircraftTapListener{void onTap(Aircraft a);void onDoubleTap(Aircraft a);}

        RadarView(Context c){
            super(c);
            p.setTypeface(Typeface.MONOSPACE);
            offlineMap=BitmapFactory.decodeResource(getResources(),R.drawable.world_map);
            LocalGeoData.ensureLoaded(c,this);
            setBackgroundColor(Color.TRANSPARENT);
        }

        void setOnAircraftTapListener(OnAircraftTapListener l){listener=l;}
        void setStyle(String s){style=s==null?"classic":s;themedOfflineMapColor=0;radarBackgroundTheme=0;invalidate();}
        void setOrientation(int degrees){orientation=((degrees%360)+360)%360;radarBackgroundOrientation=-1;invalidate();}
        void setTrails(boolean value){showTrails=value;invalidate();}
        void setMiles(boolean value){miles=value;invalidate();}
        void setDisplayRange(int km){range=Math.max(5,km);geoDirty=true;radarBackgroundRange=-1;invalidate();}
        void setAlertRange(boolean enabled,int km){alertEnabled=enabled;alertRange=km;invalidate();}
        void setSelected(String hex){selected=hex;invalidate();}
        void setLabelMode(int mode){labelMode=mode;invalidate();}
        void setVectorMinutes(int minutes){vectorMinutes=Math.max(0,minutes);invalidate();}
        void setHome(double lat,double lon){homeLat=lat;homeLon=lon;geoDirty=true;radarBackgroundLat=999;invalidate();}
void setAircraft(List<Aircraft> a,int r){
            data=new ArrayList<Aircraft>(a);
            range=r;
            for(Aircraft ac:a){
                ArrayList<PointF> t=trails.get(ac.hex);
                if(t==null){t=new ArrayList<PointF>();trails.put(ac.hex,t);}
                t.add(new PointF((float)ac.distanceKm,(float)ac.bearing));
                while(t.size()>6)t.remove(0);
            }
            invalidate();
        }

        private Bitmap themedRadarMap(){
            if(offlineMap==null)return null;
            if(themedOfflineMap!=null&&themedOfflineMapColor==GREEN&&!themedOfflineMap.isRecycled())return themedOfflineMap;
            try{
                Bitmap out=Bitmap.createBitmap(offlineMap.getWidth(),offlineMap.getHeight(),Bitmap.Config.ARGB_8888);
                Canvas cc=new Canvas(out);Paint mp=new Paint(Paint.ANTI_ALIAS_FLAG|Paint.FILTER_BITMAP_FLAG);
                float tr=Color.red(GREEN)/255f,tg=Color.green(GREEN)/255f,tb=Color.blue(GREEN)/255f;
                ColorMatrix cm=new ColorMatrix(new float[]{
                    .299f*tr,.587f*tr,.114f*tr,0,0,
                    .299f*tg,.587f*tg,.114f*tg,0,0,
                    .299f*tb,.587f*tb,.114f*tb,0,0,
                    0,0,0,1,0
                });
                mp.setColorFilter(new ColorMatrixColorFilter(cm));cc.drawBitmap(offlineMap,0,0,mp);
                if(themedOfflineMap!=null&&!themedOfflineMap.isRecycled())themedOfflineMap.recycle();
                themedOfflineMap=out;themedOfflineMapColor=GREEN;return themedOfflineMap;
            }catch(Exception e){return offlineMap;}
        }
        private void refreshRadarGeo(){
            if(!geoDirty||!LocalGeoData.isLoaded())return;
            double radius=Math.max(15,range*1.18);
            radarPlaces=LocalGeoData.nearbyPlaces(homeLat,homeLon,radius,80);
            radarAirports=LocalGeoData.nearbyAirports(homeLat,homeLon,Math.min(120,Math.max(15,radius)),30);
            radarRunways=LocalGeoData.runwaysFor(radarAirports,60);
            geoDirty=false;
        }
        private PointF localPoint(double lat,double lon,float cx,float cy,float rad){
            double[] db=LocalGeoData.distanceBearing(homeLat,homeLon,lat,lon);
            double aa=Math.toRadians(db[1]-orientation-90);
            float rr=(float)Math.min(rad,db[0]/Math.max(1,range)*rad);
            return new PointF((float)(cx+Math.cos(aa)*rr),(float)(cy+Math.sin(aa)*rr));
        }
        private void drawLocalMap(Canvas c,float cx,float cy,float rad,boolean square){
            int w=getWidth(),h=getHeight();if(w<=0||h<=0)return;
            boolean needs=radarBackgroundCache==null||radarBackgroundW!=w||radarBackgroundH!=h||
                radarBackgroundRange!=range||radarBackgroundOrientation!=orientation||radarBackgroundTheme!=GREEN||
                Math.abs(radarBackgroundLat-homeLat)>.00001||Math.abs(radarBackgroundLon-homeLon)>.00001||
                (geoDirty&&LocalGeoData.isLoaded());
            if(needs){
                try{
                    Bitmap bm=Bitmap.createBitmap(w,h,Bitmap.Config.ARGB_8888);
                    Canvas bgc=new Canvas(bm);renderLocalBackground(bgc,cx,cy,rad,square);
                    if(radarBackgroundCache!=null&&!radarBackgroundCache.isRecycled())radarBackgroundCache.recycle();
                    radarBackgroundCache=bm;radarBackgroundW=w;radarBackgroundH=h;radarBackgroundRange=range;
                    radarBackgroundOrientation=orientation;radarBackgroundTheme=GREEN;radarBackgroundLat=homeLat;radarBackgroundLon=homeLon;
                }catch(Exception ignored){}
            }
            if(radarBackgroundCache!=null&&!radarBackgroundCache.isRecycled()){p.setAlpha(255);p.setColorFilter(null);c.drawBitmap(radarBackgroundCache,0,0,p);}
        }

        private void renderLocalBackground(Canvas c,float cx,float cy,float rad,boolean square){
            Bitmap wm=themedRadarMap();
            if(wm!=null){
                double homeX=(homeLon+180.0)/360.0*wm.getWidth();
                double homeY=(90.0-homeLat)/180.0*wm.getHeight();
                double metresPerPixel=40075016.686*Math.max(.08,Math.cos(Math.toRadians(homeLat)))/Math.max(1,wm.getWidth());
                double mapPxForRange=(range*1000.0)/Math.max(.01,metresPerPixel);
                float scale=(float)(rad/Math.max(1.0,mapPxForRange));
                c.save();
                if(square)c.clipRect(cx-rad,cy-rad,cx+rad,cy+rad);else{Path clip=new Path();clip.addCircle(cx,cy,rad,Path.Direction.CW);c.clipPath(clip);}
                c.translate(cx,cy);c.rotate(-orientation);c.scale(scale,scale);
                p.setAlpha(196);p.setColorFilter(null);c.drawBitmap(wm,(float)-homeX,(float)-homeY,p);p.setAlpha(255);c.restore();
            }
            refreshRadarGeo();
            // Windows-style local place network and labels.
            p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(1f);
            ArrayList<PointF> pts=new ArrayList<PointF>();
            for(GeoPlace gp:radarPlaces)pts.add(localPoint(gp.lat,gp.lon,cx,cy,rad));
            for(int i=0;i<pts.size();i++){
                PointF a=pts.get(i);double best=Double.MAX_VALUE;int bj=-1;
                for(int j=0;j<pts.size();j++){if(i==j)continue;PointF b=pts.get(j);double d=(a.x-b.x)*(a.x-b.x)+(a.y-b.y)*(a.y-b.y);if(d<best){best=d;bj=j;}}
                if(bj>=0&&best<rad*rad*.20){p.setColor(Color.argb(70,Color.red(GREEN),Color.green(GREEN),Color.blue(GREEN)));c.drawLine(a.x,a.y,pts.get(bj).x,pts.get(bj).y,p);}
            }
            int labels=0;p.setStyle(Paint.Style.FILL);
            for(int i=0;i<radarPlaces.size();i++){GeoPlace gp=radarPlaces.get(i);PointF q=pts.get(i);p.setColor(Color.argb(180,Color.red(GREEN),Color.green(GREEN),Color.blue(GREEN)));float rr=gp.population>=250000?3.2f:(gp.population>=75000?2.5f:1.8f);c.drawCircle(q.x,q.y,rr,p);if(labels<16&&(gp.population>=50000||labels<8)){p.setTextSize(8);p.setColor(Color.argb(190,Color.red(TEXT),Color.green(TEXT),Color.blue(TEXT)));c.drawText(gp.name.toUpperCase(Locale.US),q.x+5,q.y-4,p);labels++;}}
            // Runway geometry and airport crosses from the original Windows data.
            p.setStyle(Paint.Style.STROKE);
            for(GeoRunway rw:radarRunways){PointF a=localPoint(rw.la1,rw.lo1,cx,cy,rad),b=localPoint(rw.la2,rw.lo2,cx,cy,rad);p.setStrokeWidth(1.6f);p.setColor(Color.argb(135,Color.red(CYAN),Color.green(CYAN),Color.blue(CYAN)));c.drawLine(a.x,a.y,b.x,b.y,p);}
            int ap=0;
            for(GeoAirport ga:radarAirports){PointF q=localPoint(ga.lat,ga.lon,cx,cy,rad);p.setStrokeWidth(1f);p.setColor(Color.argb(170,Color.red(CYAN),Color.green(CYAN),Color.blue(CYAN)));c.drawLine(q.x-6,q.y,q.x+6,q.y,p);c.drawLine(q.x,q.y-6,q.x,q.y+6,p);if(ap++<12){p.setStyle(Paint.Style.FILL);p.setTextSize(7);c.drawText((ga.code.length()>0?ga.code:ga.ident),q.x+7,q.y-4,p);p.setStyle(Paint.Style.STROKE);}}
            p.setStyle(Paint.Style.FILL);
        }

        @Override protected void onDraw(Canvas c){
            super.onDraw(c);
            long now=android.os.SystemClock.uptimeMillis();
            float cx=getWidth()/2f,cy=getHeight()/2f;
            float rad=Math.min(getWidth(),getHeight())*.475f;
            boolean square="tactical".equals(style)||"pulse".equals(style);

            drawLocalMap(c,cx,cy,rad,square);
            p.setStyle(Paint.Style.FILL);
            p.setColor(Color.argb(118,3,15,11));
            if(square)c.drawRect(cx-rad,cy-rad,cx+rad,cy+rad,p);
            else c.drawCircle(cx,cy,rad,p);

            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(3);
            p.setColor(withAlpha(GREEN,155));
            if(square){
                c.drawRect(cx-rad,cy-rad,cx+rad,cy+rad,p);
                for(int i=1;i<=3;i++){
                    float v=-rad+2*rad*i/4f;
                    c.drawLine(cx+v,cy-rad,cx+v,cy+rad,p);
                    c.drawLine(cx-rad,cy+v,cx+rad,cy+v,p);
                }
            } else {
                c.drawCircle(cx,cy,rad,p);
                for(int i=1;i<=4;i++)c.drawCircle(cx,cy,rad*i/4f,p);
                for(int i=0;i<72;i++){
                    double a=Math.toRadians(i*5-90);
                    float len=i%6==0?16:(i%2==0?10:5);
                    float x1=(float)(cx+Math.cos(a)*(rad-len)), y1=(float)(cy+Math.sin(a)*(rad-len));
                    float x2=(float)(cx+Math.cos(a)*rad), y2=(float)(cy+Math.sin(a)*rad);
                    c.drawLine(x1,y1,x2,y2,p);
                }
            }

            p.setColor(withAlpha(DIM,100));
            c.drawLine(cx-rad,cy,cx+rad,cy,p);
            c.drawLine(cx,cy-rad,cx,cy+rad,p);

            drawCardinal(c,cx,cy,rad,"N",0);
            drawCardinal(c,cx,cy,rad,"E",90);
            drawCardinal(c,cx,cy,rad,"S",180);
            drawCardinal(c,cx,cy,rad,"W",270);

            p.setStyle(Paint.Style.FILL);
            p.setTextAlign(Paint.Align.LEFT);
            p.setTextSize(17);
            p.setColor(withAlpha(GREEN,210));
            for(int i=1;i<=4;i++){
                double ringKm=range*i/4.0;
                String label=miles?String.format(Locale.US,"%.0f mi",ringKm/1.609344):String.format(Locale.US,"%.0f km",ringKm);
                float rr=rad*i/4f;
                c.drawText(label,cx+6,cy-rr+17,p);
            }

            if(alertEnabled && alertRange>0){
                float ar=rad*Math.min(1f,alertRange/(float)Math.max(1,range));
                p.setStyle(Paint.Style.STROKE);
                p.setStrokeWidth(3f);
                p.setColor(AMBER);
                PathEffect dash=new DashPathEffect(new float[]{12f,8f},0);
                p.setPathEffect(dash);
                if(square)c.drawRect(cx-ar,cy-ar,cx+ar,cy+ar,p);else c.drawCircle(cx,cy,ar,p);
                p.setPathEffect(null);
                p.setStyle(Paint.Style.FILL);
                p.setTextSize(15f);
                p.setTextAlign(Paint.Align.LEFT);
                c.drawText("ALERT "+(miles?String.format(Locale.US,"%.0f mi",alertRange/1.609344):alertRange+" km"),cx+8,cy-ar+18,p);
            }

            drawSweep(c,cx,cy,rad,now,square);

            if(showTrails){
                for(Aircraft ac:data){
                    ArrayList<PointF> t=trails.get(ac.hex);
                    if(t==null||t.size()<2)continue;
                    p.setStyle(Paint.Style.STROKE);
                    p.setStrokeWidth(3);
                    p.setColor(withAlpha(ac.military?Color.rgb(255,102,119):GREEN,80));
                    for(int i=1;i<t.size();i++){
                        PointF a=t.get(i-1),b=t.get(i);
                        PointF pa=point(cx,cy,rad,a.x,a.y);
                        PointF pb=point(cx,cy,rad,b.x,b.y);
                        c.drawLine(pa.x,pa.y,pb.x,pb.y,p);
                    }
                }
            }

            p.setTextSize(15);
            p.setTextAlign(Paint.Align.LEFT);
            ArrayList<RectF> usedRadarLabels=new ArrayList<RectF>();
            for(Aircraft a:data){
                PointF pos=point(cx,cy,rad,(float)a.distanceKm,(float)a.bearing);
                int col=a.military?Color.rgb(255,102,119):(a.onGround?AMBER:GREEN);

                if(a.hex.equals(selected)){
                    p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(3);p.setColor(AMBER);
                    c.drawCircle(pos.x,pos.y,25,p);
                }

                drawAircraftIcon(c,a,pos.x,pos.y,col);

                if(a.track!=null && !a.onGround && vectorMinutes>0){
                    double ang=Math.toRadians(a.track-orientation-90);
                    float len=vectorMinutes<=0?0f:(float)Math.min("atc".equals(style)?110:90,(a.speedKnots==null?0:a.speedKnots)*1.852*(vectorMinutes/60.0)/Math.max(1,range)*rad);
                    p.setStyle(Paint.Style.STROKE);p.setStrokeWidth("atc".equals(style)?2:1);p.setColor(withAlpha(col,170));
                    c.drawLine(pos.x,pos.y,(float)(pos.x+Math.cos(ang)*len),(float)(pos.y+Math.sin(ang)*len),p);
                }

                p.setStyle(Paint.Style.FILL);p.setColor(col);
                if("atc".equals(style)){
                    c.drawText(a.callsign,pos.x+14,radarLabelY(a.callsign,pos.x+14,pos.y-8,usedRadarLabels),p);
                    String level=a.altitudeFeet==null?"---":String.format(Locale.US,"%03d",Math.max(0,a.altitudeFeet/100));
                    String speed=a.speedKnots==null?"---":String.format(Locale.US,"%03d",(int)Math.round(a.speedKnots));
                    c.drawText(level+"  "+speed,pos.x+14,radarLabelY(level+"  "+speed,pos.x+14,pos.y+9,usedRadarLabels),p);
                    p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(1);p.setColor(withAlpha(col,120));
                    c.drawLine(pos.x+5,pos.y-4,pos.x+12,pos.y-7,p);
                } else if(labelMode==1 || (labelMode==0 && (a.distanceKm<=range*.72 || a.hex.equals(selected)))) c.drawText(a.callsign,pos.x+12,radarLabelY(a.callsign,pos.x+12,pos.y-8,usedRadarLabels),p);
            }

            p.setColor(AMBER);p.setStrokeWidth(3);p.setStyle(Paint.Style.STROKE);
            c.drawLine(cx-8,cy,cx+8,cy,p);c.drawLine(cx,cy-8,cx,cy+8,p);
            p.setStyle(Paint.Style.FILL);p.setTextSize(13);c.drawText("HOME",cx+10,cy-8,p);

            postInvalidateDelayed(45);
        }

        private float radarLabelY(String label,float x,float desired,ArrayList<RectF> used){float w=p.measureText(label)+6,h=18,y=desired;for(int n=0;n<6;n++){RectF r=new RectF(x,y-h,x+w,y+4);boolean hit=false;for(RectF u:used)if(RectF.intersects(r,u)){hit=true;break;}if(!hit){used.add(r);return y;}y+=17;}used.add(new RectF(x,y-h,x+w,y+4));return y;}

        private void drawSweep(Canvas c,float cx,float cy,float r,long now,boolean square){
            float phase=(now%10000L)/10000f;
            p.setStyle(Paint.Style.STROKE);
            if("tactical".equals(style)){
                float x=cx-r+phase*2*r;
                p.setStrokeWidth(3);p.setColor(withAlpha(GREEN,210));
                c.drawLine(x,cy-r,x,cy+r,p);
            }else if("pulse".equals(style)||"sonar".equals(style)){
                int waves="sonar".equals(style)?3:3;
                for(int i=0;i<waves;i++){
                    float wave=(phase-i*0.22f+1f)%1f;
                    p.setStrokeWidth(i==0?3:2);
                    p.setColor(withAlpha(("sonar".equals(style)&&i==0)?AMBER:GREEN,(int)(190*(1-wave))));
                    c.drawCircle(cx,cy,r*wave,p);
                }
            }else{
                drawRotatingSweep(c,cx,cy,r,phase*360f,"atc".equals(style)?0.28f:1f);
                if("dual".equals(style))drawRotatingSweep(c,cx,cy,r,phase*360f+180f,0.55f);
            }
        }

        private void drawRotatingSweep(Canvas c,float cx,float cy,float r,float degrees,float strength){
            for(int i=26;i>=0;i--){
                float d=degrees-i*1.4f-orientation;
                double a=Math.toRadians(d-90);
                p.setStrokeWidth(2);
                p.setColor(withAlpha(GREEN,(int)((18+(26-i)*5)*strength)));
                c.drawLine(cx,cy,(float)(cx+Math.cos(a)*r),(float)(cy+Math.sin(a)*r),p);
            }
        }

        private void drawCardinal(Canvas c,float cx,float cy,float r,String label,double bearing){
            double a=Math.toRadians(bearing-orientation-90);
            p.setStyle(Paint.Style.FILL);p.setTextSize(19);p.setTextAlign(Paint.Align.CENTER);p.setColor(GREEN);
            c.drawText(label,(float)(cx+Math.cos(a)*(r+19)),(float)(cy+Math.sin(a)*(r+19)+6),p);
        }

        private PointF point(float cx,float cy,float rad,float distance,float bearing){
            double ang=Math.toRadians(bearing-orientation-90);
            float rr=Math.min(1f,distance/Math.max(1,range))*rad;
            return new PointF((float)(cx+Math.cos(ang)*rr),(float)(cy+Math.sin(ang)*rr));
        }

        private void drawAircraftIcon(Canvas c,Aircraft a,float x,float y,int color){
            c.save();
            c.rotate((float)((a.track==null?a.bearing:a.track)-orientation),x,y);
            p.setColor(color);p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(3);p.setStrokeCap(Paint.Cap.ROUND);
            float r=17f;
            String kind=a.kind();
            if("HELICOPTER".equals(kind)){
                c.drawCircle(x,y,r*.45f,p);
                c.drawLine(x,y+r*.35f,x,y+r,p);
                c.drawLine(x-r,y-r*.5f,x+r,y+r*.25f,p);
                c.drawLine(x-r,y+r*.25f,x+r,y-r*.5f,p);
            } else if("FAST".equals(kind)){
                Path q=new Path();q.moveTo(x,y-r);q.lineTo(x+r*.9f,y+r);q.lineTo(x,y+r*.55f);q.lineTo(x-r*.9f,y+r);q.close();
                p.setStyle(Paint.Style.FILL);c.drawPath(q,p);
            } else if("UAV".equals(kind)){
                c.drawLine(x-r*.7f,y-r*.7f,x+r*.7f,y+r*.7f,p);c.drawLine(x-r*.7f,y+r*.7f,x+r*.7f,y-r*.7f,p);
                c.drawCircle(x-r*.7f,y-r*.7f,r*.25f,p);c.drawCircle(x+r*.7f,y-r*.7f,r*.25f,p);c.drawCircle(x-r*.7f,y+r*.7f,r*.25f,p);c.drawCircle(x+r*.7f,y+r*.7f,r*.25f,p);
            } else if("GROUND".equals(kind)){
                c.drawRect(x-r*.8f,y-r*.55f,x+r*.8f,y+r*.55f,p);
            } else if("LIGHT".equals(kind)||"GLIDER".equals(kind)){
                c.drawLine(x,y-r,x,y+r,p);c.drawLine(x-r,y-r*.2f,x+r,y-r*.2f,p);c.drawLine(x-r*.35f,y+r*.65f,x+r*.35f,y+r*.65f,p);
            } else {
                p.setStyle(Paint.Style.FILL);
                Path q=new Path();
                q.moveTo(x,y-r);q.lineTo(x+r*.2f,y-r*.35f);q.lineTo(x+r,y+r*.25f);q.lineTo(x+r,y+r*.45f);
                q.lineTo(x+r*.2f,y+r*.1f);q.lineTo(x+r*.15f,y+r*.7f);q.lineTo(x,y+r*.85f);
                q.lineTo(x-r*.15f,y+r*.7f);q.lineTo(x-r*.2f,y+r*.1f);q.lineTo(x-r,y+r*.45f);q.lineTo(x-r,y+r*.25f);q.lineTo(x-r*.2f,y-r*.35f);q.close();
                c.drawPath(q,p);
            }
            c.restore();
        }

        private int withAlpha(int color,int alpha){
            return Color.argb(Math.max(0,Math.min(255,alpha)),Color.red(color),Color.green(color),Color.blue(color));
        }

        @Override public boolean onTouchEvent(MotionEvent e){
            if(e.getAction()!=MotionEvent.ACTION_UP)return true;
            float cx=getWidth()/2f,cy=getHeight()/2f,rad=Math.min(getWidth(),getHeight())*.475f;
            Aircraft best=null;double bestPx=60;
            for(Aircraft a:data){PointF pos=point(cx,cy,rad,(float)a.distanceKm,(float)a.bearing);double d=Math.hypot(e.getX()-pos.x,e.getY()-pos.y);if(d<bestPx){bestPx=d;best=a;}}
            if(best!=null&&listener!=null){long now=android.os.SystemClock.uptimeMillis();if(lastTapHex!=null&&lastTapHex.equals(best.hex)&&now-lastTapMillis<380){listener.onDoubleTap(best);lastTapHex=null;lastTapMillis=0;}else{listener.onTap(best);lastTapHex=best.hex;lastTapMillis=now;}}
            return true;
        }

        @Override protected void onSizeChanged(int w,int h,int oldw,int oldh){
            super.onSizeChanged(w,h,oldw,oldh);
            radarBackgroundW=0;
        }
    }
}
