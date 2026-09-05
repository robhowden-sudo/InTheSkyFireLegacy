package org.inthesky.firelegacy;

import android.app.Activity;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.*;
import android.graphics.drawable.ColorDrawable;
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
    private static final int BG = Color.rgb(2,7,5);
    private static int PANEL = Color.rgb(3,17,13);
    private static int GREEN = Color.rgb(84,224,255);
    private static int DIM = Color.rgb(66,142,151);
    private static int CYAN = Color.rgb(84,224,255);
    private static int AMBER = Color.rgb(255,191,64);
    private static int TEXT = Color.rgb(232,255,255);
    private static int BORDER = Color.rgb(24,113,123);

    private final Handler ui = new Handler(Looper.getMainLooper());
    private final ExecutorService io = Executors.newFixedThreadPool(3);
    private SharedPreferences prefs;
    private LinearLayout root;
    private FrameLayout pageHost;
    private RadarView radarView;
    private LinearLayout aircraftCard;
    private ImageView aircraftImage;
    private TextView aircraftTitle, aircraftDetails, aircraftReference;
    private TextView weatherBody, weatherStatus, weatherCurrent, weatherFiveDay, weatherWarnings, weatherLocalConditions, weatherCity;
    private LinearLayout fiveDayGrid, worldClocksBox;
    private WeatherTrendView temperatureTrendView, humidityTrendView, windTrendView, pressureTrendView, rainTrendView;
    private TextView timeClock, timeDate, timeZones, launchSummary;
    private Button launchMoreButton;
    private final List<LegacyLaunch> launchList = new ArrayList<LegacyLaunch>();
    private boolean launchesExpanded = false;
    private WeatherCompassView windViewRef;
    private WeatherHistoryView historyViewRef;
    private Runnable radarTick, clockTick, pageCycleTick;
    private String currentPage = "RADAR";
    private SSLSocketFactory weatherSslFactory;
    private List<Aircraft> aircraft = new ArrayList<Aircraft>();
    private Set<String> previousContacts = null;
    private String selectedHex = null;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        enableTls12();
        prefs = getSharedPreferences("fire_settings", MODE_PRIVATE);
        applyTheme(prefs.getString("theme", "PHOSPHOR"));
        getWindow().setStatusBarColor(BG);
        buildShell();
        openPage("RADAR");
        schedulePageCycle();
    }

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

    private void applyTheme(String theme) {
        PANEL = Color.rgb(3,17,13);
        TEXT = Color.rgb(232,255,255);
        AMBER = Color.rgb(255,191,64);
        if ("AMBER".equals(theme)) {
            GREEN=Color.rgb(255,191,64); CYAN=Color.rgb(255,214,110); DIM=Color.rgb(170,133,69); BORDER=Color.rgb(131,94,28);
        } else if ("CYAN".equals(theme)) {
            GREEN=Color.rgb(84,224,255); CYAN=Color.rgb(84,224,255); DIM=Color.rgb(66,142,151); BORDER=Color.rgb(24,113,123);
        } else if ("RED".equals(theme)) {
            GREEN=Color.rgb(255,92,109); CYAN=Color.rgb(255,128,139); DIM=Color.rgb(153,76,83); BORDER=Color.rgb(121,45,54);
        } else if ("VIOLET".equals(theme)) {
            GREEN=Color.rgb(188,139,255); CYAN=Color.rgb(208,174,255); DIM=Color.rgb(125,91,168); BORDER=Color.rgb(90,60,126);
        } else {
            GREEN=Color.rgb(84,224,255); CYAN=Color.rgb(84,224,255); DIM=Color.rgb(66,142,151); BORDER=Color.rgb(24,113,123);
        }
    }

    private GradientDrawable panelBackground(boolean strong) {
        GradientDrawable g=new GradientDrawable();
        g.setColor(strong?Color.rgb(2,14,11):PANEL);
        g.setStroke(dp(strong?2:1), strong?CYAN:BORDER);
        g.setCornerRadius(dp(3));
        return g;
    }

    private TextView text(String s, int sp, int color) {
        TextView v = new TextView(this);
        v.setText(s); v.setTextSize(sp); v.setTextColor(color);
        v.setTypeface(Typeface.MONOSPACE);
        v.setPadding(dp(8),dp(6),dp(8),dp(6));
        return v;
    }

    private Button button(String s) {
        Button b = new Button(this);
        b.setText(s); b.setTextSize(12); b.setTextColor(TEXT);
        b.setBackground(panelBackground(true));
        b.setAllCaps(false);
        return b;
    }

    private void buildShell() {
        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(BG);

        TextView header = text("IN THE SKY  //  FIRE HD LEGACY", 18, GREEN);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setBackground(panelBackground(true));
        root.addView(header, new LinearLayout.LayoutParams(-1, dp(52)));

        pageHost = new FrameLayout(this);
        root.addView(pageHost, new LinearLayout.LayoutParams(-1, 0, 1f));

        LinearLayout nav = new LinearLayout(this);
        nav.setOrientation(LinearLayout.HORIZONTAL);
        nav.setBackground(panelBackground(false));
        String[] labels = {"RADAR","WEATHER","TIME","SETTINGS"};
        for (String label : labels) {
            Button b = button(label);
            final String page = label;
            b.setOnClickListener(v -> openPage(page));
            nav.addView(b, new LinearLayout.LayoutParams(0, dp(56), 1f));
        }
        root.addView(nav);
        setContentView(root);
    }

    private void openPage(String page) {
        currentPage = page;
        if ("RADAR".equals(page)) showRadar();
        else if ("WEATHER".equals(page)) showWeather();
        else if ("TIME".equals(page)) showTime();
        else showSettings();
    }

    private void schedulePageCycle() {
        if (pageCycleTick != null) ui.removeCallbacks(pageCycleTick);
        pageCycleTick = new Runnable() {
            public void run() {
                int seconds = Math.max(8, Math.min(120, prefs.getInt("cycleSeconds", 20)));
                if (prefs.getBoolean("autoCycle", false) && !"SETTINGS".equals(currentPage)) {
                    if ("RADAR".equals(currentPage)) openPage("WEATHER");
                    else if ("WEATHER".equals(currentPage)) openPage("TIME");
                    else openPage("RADAR");
                }
                ui.postDelayed(this, seconds * 1000L);
            }
        };
        ui.postDelayed(pageCycleTick, Math.max(8, Math.min(120, prefs.getInt("cycleSeconds",20))) * 1000L);
    }

    private void clearPage() {
        pageHost.removeAllViews();
        if (clockTick != null) ui.removeCallbacks(clockTick);
    }

    private void showRadar() {
        clearPage();

        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setBackgroundColor(BG);

        LinearLayout controls = new LinearLayout(this);
        controls.setOrientation(LinearLayout.HORIZONTAL);
        controls.setGravity(Gravity.CENTER_VERTICAL);

        TextView status = text("LIVE AIRCRAFT", 12, DIM);
        controls.addView(status, new LinearLayout.LayoutParams(0, dp(48), 1f));

        final boolean miles = prefs.getBoolean("miles", false);
        final int[] rangeValues = {5,10,25,50,100,200};
        String[] rangeLabels = new String[rangeValues.length];
        for (int i=0;i<rangeValues.length;i++) rangeLabels[i] = rangeValues[i] + (miles ? " mi" : " km");

        Spinner rangePicker = new Spinner(this);
        rangePicker.setAdapter(new ArrayAdapter<String>(this, android.R.layout.simple_spinner_dropdown_item, rangeLabels));
        int currentDisplay = miles ? (int)Math.round(prefs.getInt("range",40) / 1.609344) : prefs.getInt("range",40);
        int best = 0, bestDelta = Integer.MAX_VALUE;
        for (int i=0;i<rangeValues.length;i++) {
            int d=Math.abs(rangeValues[i]-currentDisplay);
            if(d<bestDelta){bestDelta=d;best=i;}
        }
        rangePicker.setSelection(best, false);
        rangePicker.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            boolean first=true;
            public void onNothingSelected(AdapterView<?> parent) {}
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                if(first){first=false;return;}
                int displayRange=rangeValues[position];
                int km=miles ? (int)Math.round(displayRange*1.609344) : displayRange;
                km=Math.max(5,Math.min(320,km));
                prefs.edit().putInt("range",km).apply();
                previousContacts=null;
                if(radarView!=null) radarView.setMiles(miles);
                refreshRadar(status);
            }
        });
        controls.addView(rangePicker, new LinearLayout.LayoutParams(dp(125), dp(48)));

        Button refresh = button("SCAN NOW");
        refresh.setOnClickListener(v -> refreshRadar(status));
        controls.addView(refresh, new LinearLayout.LayoutParams(dp(112), dp(48)));
        page.addView(controls);

        LinearLayout body = new LinearLayout(this);
        body.setOrientation(LinearLayout.HORIZONTAL);
        body.setWeightSum(10f);

        radarView = new RadarView(this);
        radarView.setStyle(prefs.getString("radarStyle", "classic"));
        radarView.setOrientation(prefs.getInt("orientation", 0));
        radarView.setTrails(prefs.getBoolean("trails", true));
        radarView.setMiles(prefs.getBoolean("miles", false));
        radarView.setAlertRange(prefs.getBoolean("alertEnabled",true),prefs.getInt("alertRange",10));
        radarView.setOnAircraftTapListener(this::selectAircraft);
        LinearLayout.LayoutParams radarLp = new LinearLayout.LayoutParams(0, -1, 7f);
        radarLp.setMargins(dp(6), dp(2), dp(4), dp(6));
        body.addView(radarView, radarLp);

        ScrollView dataScroll = new ScrollView(this);
        dataScroll.setFillViewport(true);
        LinearLayout dataPanel = new LinearLayout(this);
        dataPanel.setOrientation(LinearLayout.VERTICAL);
        dataPanel.setPadding(dp(8), dp(8), dp(8), dp(8));
        dataPanel.setBackground(panelBackground(true));

        dataPanel.addView(text("SELECTED CONTACT // AUTO ON ALERT ENTRY", 12, CYAN));

        aircraftImage = new ImageView(this);
        aircraftImage.setScaleType(ImageView.ScaleType.CENTER_CROP);
        aircraftImage.setBackgroundColor(Color.rgb(3,12,8));
        dataPanel.addView(aircraftImage, new LinearLayout.LayoutParams(-1, dp(150)));

        aircraftTitle = text("TAP A RADAR CONTACT", 16, GREEN);
        aircraftDetails = text("Aircraft broadcast data and range information will appear here.", 12, TEXT);
        aircraftReference = text("", 11, DIM);
        dataPanel.addView(aircraftTitle);
        dataPanel.addView(aircraftDetails);
        dataPanel.addView(text("AIRCRAFT REFERENCE", 11, CYAN));
        dataPanel.addView(aircraftReference);

        dataScroll.addView(dataPanel);
        LinearLayout.LayoutParams dataLp = new LinearLayout.LayoutParams(0, -1, 3f);
        dataLp.setMargins(dp(4), dp(2), dp(6), dp(6));
        body.addView(dataScroll, dataLp);

        page.addView(body, new LinearLayout.LayoutParams(-1, 0, 1f));
        pageHost.addView(page);

        refreshRadar(status);
        scheduleRadar(status);
    }

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
        final int rangeKm = prefs.getInt("range", 40);
        final int radiusNm = Math.max(1, (int)Math.ceil(rangeKm / 1.852));
        final String primaryUrl="https://api.adsb.lol/v2/point/"+lat+"/"+lon+"/"+radiusNm;
        final String secondaryUrl=openSkyUrl(lat,lon,rangeKm);

        io.execute(() -> {
            try {
                final ArrayList<Aircraft> list = new ArrayList<Aircraft>();
                String radarSource;
                try {
                    parseAdsbRadar(getJson(primaryUrl),lat,lon,rangeKm,list);
                    radarSource="ADSB.LOL";
                } catch(Exception primaryError) {
                    try {
                        parseOpenSkyRadar(getJson(secondaryUrl),lat,lon,rangeKm,list);
                        radarSource="OPENSKY FALLBACK";
                    } catch(Exception secondaryError) {
                        JSONObject cached=readJsonCache(primaryUrl,10*60_000L);
                        if(cached!=null){
                            parseAdsbRadar(cached,lat,lon,rangeKm,list);
                            radarSource="ADSB.LOL CACHE";
                        } else {
                            cached=readJsonCache(secondaryUrl,10*60_000L);
                            if(cached==null) throw secondaryError;
                            parseOpenSkyRadar(cached,lat,lon,rangeKm,list);
                            radarSource="OPENSKY CACHE";
                        }
                    }
                }
                final String sourceLabel = radarSource;
                Collections.sort(list, (a,b) -> Double.compare(a.distanceKm,b.distanceKm));
                ui.post(() -> {
                    Set<String> now = new HashSet<String>();
                    for (Aircraft a : list) now.add(a.hex);
                    Aircraft entered = null;
                    int alertKm=prefs.getInt("alertRange",10);
                    boolean alertOn=prefs.getBoolean("alertEnabled",true);
                    if (previousContacts != null && !sourceLabel.contains("CACHE") && alertOn) {
                        for (Aircraft a : list) {
                            if (!previousContacts.contains(a.hex) && a.distanceKm <= alertKm) {
                                entered = a;
                                break;
                            }
                        }
                    }
                    if(!sourceLabel.contains("CACHE")) previousContacts = now;
                    aircraft = list;
                    if (radarView != null) radarView.setAircraft(list, rangeKm);
                    status.setText(list.size()+" CONTACTS  //  "+distanceLabel(rangeKm)+"  //  "+sourceLabel);
                    if (entered != null) selectAircraft(entered);
                });
            } catch (final Exception e) {
                ui.post(() -> status.setText("RADAR UNAVAILABLE // "+shortError(e)));
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
        aircraftImage.setImageDrawable(new ColorDrawable(Color.rgb(3,12,8)));

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
                    if (finalBitmap != null) aircraftImage.setImageBitmap(finalBitmap);
                    else aircraftImage.setImageBitmap(createAircraftPlaceholder(a));
                }
            });
        });
    }

    private void showWeather() {
        clearPage();

        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setPadding(dp(8),dp(6),dp(8),dp(8));
        page.setBackgroundColor(BG);

        LinearLayout top = new LinearLayout(this);
        top.setOrientation(LinearLayout.HORIZONTAL);
        top.setGravity(Gravity.CENTER_VERTICAL);
        weatherStatus = text("WEATHER // "+locationLabel(), 18, GREEN);
        weatherStatus.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
        Button refresh = button("REFRESH");
        refresh.setOnClickListener(v -> refreshWeather());
        top.addView(weatherStatus,new LinearLayout.LayoutParams(0,dp(48),1f));
        top.addView(refresh,new LinearLayout.LayoutParams(dp(112),dp(48)));
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
        hero.setPadding(dp(16),dp(10),dp(16),dp(10));
        hero.setBackground(panelBackground(true));

        weatherCurrent = text("LOADING CURRENT CONDITIONS…", 30, GREEN);
        weatherCurrent.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
        hero.addView(weatherCurrent,new LinearLayout.LayoutParams(0,dp(132),1f));

        TextView heroIcon = text("☁", 58, AMBER);
        heroIcon.setGravity(Gravity.CENTER);
        heroIcon.setTag("weatherHeroIcon");
        hero.addView(heroIcon,new LinearLayout.LayoutParams(dp(125),dp(132)));
        left.addView(hero,new LinearLayout.LayoutParams(-1,dp(142)));

        temperatureTrendView = new WeatherTrendView(this,"TEMPERATURE // LAST 12 HOURS",GREEN);
        left.addView(temperatureTrendView,new LinearLayout.LayoutParams(-1,dp(108)));

        humidityTrendView = new WeatherTrendView(this,"HUMIDITY // LAST 24 HOURS",CYAN);
        left.addView(humidityTrendView,new LinearLayout.LayoutParams(-1,dp(76)));

        windTrendView = new WeatherTrendView(this,"WIND SPEED // LAST 24 HOURS",GREEN);
        left.addView(windTrendView,new LinearLayout.LayoutParams(-1,dp(76)));

        pressureTrendView = new WeatherTrendView(this,"PRESSURE // LAST 12 HOURS",CYAN);
        left.addView(pressureTrendView,new LinearLayout.LayoutParams(-1,dp(76)));

        rainTrendView = new WeatherTrendView(this,"RAINFALL // LAST 24 HOURS",AMBER);
        left.addView(rainTrendView,new LinearLayout.LayoutParams(-1,dp(76)));

        LinearLayout.LayoutParams leftLp=new LinearLayout.LayoutParams(0,-1,6.6f);
        leftLp.setMargins(0,0,dp(6),0);
        columns.addView(left,leftLp);

        // RIGHT 3.4 / 10: city, five-day forecast, local conditions + alerts.
        LinearLayout right = new LinearLayout(this);
        right.setOrientation(LinearLayout.VERTICAL);

        weatherCity = text("NEAREST CITY / TOWN // "+locationLabel().toUpperCase(Locale.UK), 14, CYAN);
        weatherCity.setTypeface(Typeface.MONOSPACE,Typeface.BOLD);
        weatherCity.setGravity(Gravity.CENTER);
        weatherCity.setBackgroundColor(PANEL);
        right.addView(weatherCity,new LinearLayout.LayoutParams(-1,dp(48)));

        TextView fiveTitle=text("FIVE DAY FORECAST",12,DIM);
        fiveTitle.setBackgroundColor(PANEL);
        right.addView(fiveTitle,new LinearLayout.LayoutParams(-1,dp(34)));

        fiveDayGrid=new LinearLayout(this);
        fiveDayGrid.setOrientation(LinearLayout.VERTICAL);
        fiveDayGrid.setPadding(dp(6),dp(4),dp(6),dp(4));
        fiveDayGrid.setBackground(panelBackground(true));
        right.addView(fiveDayGrid,new LinearLayout.LayoutParams(-1,0,1.2f));
        buildFiveDayHeader();
        weatherFiveDay=text("",1,TEXT);
        weatherFiveDay.setVisibility(View.GONE);

        TextView localTitle=text("LOCAL CONDITIONS",12,DIM);
        localTitle.setBackgroundColor(PANEL);
        right.addView(localTitle,new LinearLayout.LayoutParams(-1,dp(34)));

        weatherLocalConditions=text("Loading local conditions…",15,TEXT);
        weatherLocalConditions.setTypeface(Typeface.MONOSPACE,Typeface.BOLD);
        weatherLocalConditions.setBackground(panelBackground(true));
        weatherLocalConditions.setPadding(dp(12),dp(10),dp(12),dp(10));
        right.addView(weatherLocalConditions,new LinearLayout.LayoutParams(-1,0,1f));

        TextView warningTitle=text("WEATHER ALERTS",12,DIM);
        warningTitle.setBackgroundColor(PANEL);
        right.addView(warningTitle,new LinearLayout.LayoutParams(-1,dp(34)));

        weatherWarnings=text("Checking official warning feeds…",12,TEXT);
        weatherWarnings.setBackground(panelBackground(true));
        weatherWarnings.setPadding(dp(10),dp(8),dp(10),dp(8));
        right.addView(weatherWarnings,new LinearLayout.LayoutParams(-1,0,.65f));

        columns.addView(right,new LinearLayout.LayoutParams(0,-1,3.4f));

        page.addView(columns,new LinearLayout.LayoutParams(-1,0,1f));

        weatherBody=text("",10,DIM);
        weatherBody.setVisibility(View.GONE);
        page.addView(weatherBody,new LinearLayout.LayoutParams(1,1));

        pageHost.addView(page);
        refreshWeather();
    }

    private void refreshWeather() {
        weatherStatus.setText("WEATHER // "+locationLabel()+" // UPDATING");
        final double lat=currentLat(), lon=currentLon();
        final String metUrl="https://api.met.no/weatherapi/locationforecast/2.0/compact?lat="+lat+"&lon="+lon;
        final String openUrl="https://api.open-meteo.com/v1/forecast?latitude="+lat+"&longitude="+lon+
            "&current=temperature_2m,relative_humidity_2m,wind_speed_10m,wind_direction_10m,weather_code"+
            "&hourly=temperature_2m,relative_humidity_2m,wind_speed_10m,weather_code"+
            "&daily=weather_code,temperature_2m_max,temperature_2m_min,precipitation_sum,wind_speed_10m_max&forecast_days=7&timezone=auto";
        io.execute(() -> {
            try {
                WeatherDisplay wx;
                String provider;
                try {
                    JSONObject root=getJson(metUrl);
                    wx=parseMetForecast(root.getJSONObject("properties").getJSONArray("timeseries"));
                    provider="MET NORWAY";
                } catch(Exception metLiveError) {
                    try {
                        wx=parseOpenMeteo(getJson(openUrl));
                        provider="OPEN-METEO FALLBACK";
                    } catch(Exception openLiveError) {
                        JSONObject cached=readJsonCache(metUrl,2*60*60_000L);
                        if(cached!=null){
                            wx=parseMetForecast(cached.getJSONObject("properties").getJSONArray("timeseries"));
                            provider="MET NORWAY CACHE";
                        } else {
                            cached=readJsonCache(openUrl,2*60*60_000L);
                            if(cached==null) throw openLiveError;
                            wx=parseOpenMeteo(cached);
                            provider="OPEN-METEO CACHE";
                        }
                    }
                }
                final WeatherDisplay display=wx;
                final String source=provider;
                WeatherHistoryData historyData = null;
                String warnings = "Official warning feed unavailable.";
                try { historyData = loadWeatherHistory(lat,lon); } catch(Exception ignored) {}
                try { warnings = loadWeatherWarnings(lat,lon); } catch(Exception ignored) {}
                final WeatherHistoryData historyFinal=historyData;
                final String warningsFinal=warnings;
                ui.post(() -> {
                    weatherStatus.setText("WEATHER // "+locationLabel()+" // "+source);
                    weatherCity.setText("NEAREST CITY / TOWN // "+locationLabel().toUpperCase(Locale.UK));
                    weatherCurrent.setText(display.current);
                    renderFiveDay(display.days);
                    weatherLocalConditions.setText(display.localConditions);
                    View heroIconView=pageHost.findViewWithTag("weatherHeroIcon");
                    if(heroIconView instanceof TextView)((TextView)heroIconView).setText(display.icon);

                    if(historyFinal!=null){
                        temperatureTrendView.setSeries(historyFinal.temperature,display.tempUnit);
                        humidityTrendView.setSeries(historyFinal.humidity,"%");
                        windTrendView.setSeries(historyFinal.wind,prefs.getBoolean("miles",false)?"mph":"km/h");
                        pressureTrendView.setSeries(display.pressureHistory,"hPa");
                        rainTrendView.setSeries(historyFinal.rain,"mm");
                    } else {
                        temperatureTrendView.setUnavailable();
                        humidityTrendView.setUnavailable();
                        windTrendView.setUnavailable();
                        pressureTrendView.setUnavailable();
                        rainTrendView.setUnavailable();
                    }
                    if(weatherWarnings!=null) weatherWarnings.setText(warningsFinal);
                });
            } catch(final Exception e) {
                ui.post(() -> {
                    weatherStatus.setText("WEATHER UNAVAILABLE");
                    weatherCurrent.setText("NO LIVE OR RECENT CACHED WEATHER\n"+shortError(e));
                });
            }
        });
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
        String current=
            weatherGlyph(symbol)+"  "+temperatureLabel(temp)+"   "+symbol.replace("_"," ").toUpperCase(Locale.US)+"\n"+
            "HUMIDITY  "+String.format(Locale.US,"%.0f%%",hum)+"     WIND  "+String.format(Locale.US,"%.1f m/s",wind)+
            (Double.isNaN(pressure)?"":"     PRESSURE  "+String.format(Locale.US,"%.0f hPa",pressure));

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
            "HUMIDITY   "+String.format(Locale.US,"%.1f%%",hum)+"     CLOUD   "+(Double.isNaN(cloud)?"--":String.format(Locale.US,"%.1f%%",cloud))+"\n"+
            "WIND       "+String.format(Locale.US,"%.1f m/s",wind)+"     PRESSURE "+(Double.isNaN(pressure)?"--":String.format(Locale.US,"%.1f hPa",pressure));
        double[] pHist=new double[Math.min(12,ts.length())];
        for(int i=0;i<pHist.length;i++) pHist[i]=ts.getJSONObject(i).getJSONObject("data").getJSONObject("instant").getJSONObject("details").optDouble("air_pressure_at_sea_level",Double.NaN);
        return new WeatherDisplay(current,sb.toString(),fiveDay,direction,wind,hum,cloud,local,icon,prefs.getBoolean("fahrenheit",false)?"°F":"°C",pHist,buildForecastDaysFromMet(ts));
    }

    private WeatherDisplay parseOpenMeteo(JSONObject root) throws Exception {
        JSONObject cur=root.getJSONObject("current");
        double temp=cur.optDouble("temperature_2m");
        double hum=cur.optDouble("relative_humidity_2m");
        double wind=cur.optDouble("wind_speed_10m");
        double direction=cur.optDouble("wind_direction_10m",Double.NaN);
        int code=cur.optInt("weather_code");
        String current=
            weatherGlyph(code)+"  "+temperatureLabel(temp)+"   "+weatherCode(code).toUpperCase(Locale.US)+"\n"+
            "HUMIDITY  "+String.format(Locale.US,"%.0f%%",hum)+"     WIND  "+String.format(Locale.US,"%.1f km/h",wind);

        JSONObject h=root.getJSONObject("hourly");
        JSONArray times=h.getJSONArray("time"),temps=h.getJSONArray("temperature_2m"),
            hums=h.getJSONArray("relative_humidity_2m"),winds=h.getJSONArray("wind_speed_10m"),
            codes=h.getJSONArray("weather_code");
        StringBuilder sb=new StringBuilder();
        int count=Math.min(12,times.length());
        for(int i=0;i<count;i++){
            int wc=codes.optInt(i);
            sb.append(String.format(Locale.US,"%-5s  %-2s  %7s   RH %3.0f%%   WIND %4.1f km/h\n",
                shortIso(times.optString(i)),weatherGlyph(wc),temperatureLabel(temps.optDouble(i)),
                hums.optDouble(i),winds.optDouble(i)));
        }
        String fiveDay=buildFiveDayFromOpenMeteo(root);
        String icon=weatherGlyph(code);
        String local=
            icon+"  "+weatherCode(code).toUpperCase(Locale.US)+"     "+temperatureLabel(temp)+"\n"+
            "HUMIDITY   "+String.format(Locale.US,"%.1f%%",hum)+"     CLOUD   --\n"+
            "WIND       "+String.format(Locale.US,"%.1f km/h",wind)+"     PRESSURE --";
        double[] pHist=new double[12];Arrays.fill(pHist,Double.NaN);
        return new WeatherDisplay(current,sb.toString(),fiveDay,direction,wind,hum,Double.NaN,local,icon,prefs.getBoolean("fahrenheit",false)?"°F":"°C",pHist,buildForecastDaysFromOpenMeteo(root));
    }

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
            JSONArray rain=d.getJSONArray("precipitation_sum");
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
            return String.format(Locale.US,"%.1f°F",celsius*9.0/5.0+32.0);
        return String.format(Locale.US,"%.1f°C",celsius);
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
        LegacyLaunch(String name,long netMillis,String status,String mission,String pad,String location){
            this.name=name;this.netMillis=netMillis;this.status=status;this.mission=mission;this.pad=pad;this.location=location;
        }
    }

    static class WeatherDisplay {
        final String current,forecast,fiveDay,localConditions,icon,tempUnit;
        final double windDirection,windSpeed,humidity,cloud;
        final double[] pressureHistory;
        final ArrayList<ForecastDay> days;
        WeatherDisplay(String current,String forecast,String fiveDay,double windDirection,double windSpeed,double humidity,double cloud,
                       String localConditions,String icon,String tempUnit,double[] pressureHistory,ArrayList<ForecastDay> days){
            this.current=current;this.forecast=forecast;this.fiveDay=fiveDay;
            this.windDirection=windDirection;this.windSpeed=windSpeed;this.humidity=humidity;this.cloud=cloud;
            this.localConditions=localConditions;this.icon=icon;this.tempUnit=tempUnit;this.pressureHistory=pressureHistory;this.days=days;
        }
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

        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.HORIZONTAL);
        page.setWeightSum(10f);
        page.setPadding(dp(10), dp(10), dp(10), dp(10));
        page.setBackgroundColor(BG);

        AnalogClockView analog = new AnalogClockView(this);
        LinearLayout.LayoutParams analogLp = new LinearLayout.LayoutParams(0, -1, 5.5f);
        analogLp.setMargins(0, 0, dp(8), 0);
        page.addView(analog, analogLp);

        ScrollView infoScroll = new ScrollView(this);
        LinearLayout info = new LinearLayout(this);
        info.setOrientation(LinearLayout.VERTICAL);
        info.setPadding(dp(12), dp(10), dp(12), dp(10));
        info.setBackgroundColor(PANEL);

        info.addView(text("LOCAL TIME", 12, DIM));
        timeClock = text("", 64, GREEN);
        timeDate = text("", 22, TEXT);
        TextView localZone = text("", 12, CYAN);
        TextView calendarInfo = text("", 12, DIM);
        timeZones = text("", 15, TEXT);

        info.addView(timeClock);
        info.addView(timeDate);
        info.addView(localZone);
        info.addView(calendarInfo);
        Button worldToggle=button("WORLD CLOCKS  ▾");
        info.addView(worldToggle,new LinearLayout.LayoutParams(-1,dp(42)));
        worldClocksBox=new LinearLayout(this);
        worldClocksBox.setOrientation(LinearLayout.VERTICAL);
        worldClocksBox.setBackground(panelBackground(true));
        worldClocksBox.addView(timeZones);
        info.addView(worldClocksBox);
        boolean worldOpen=prefs.getBoolean("worldClocksOpen",false);
        worldClocksBox.setVisibility(worldOpen?View.VISIBLE:View.GONE);
        worldToggle.setText(worldOpen?"WORLD CLOCKS  ▴":"WORLD CLOCKS  ▾");
        worldToggle.setOnClickListener(v -> {
            boolean open=worldClocksBox.getVisibility()!=View.VISIBLE;
            worldClocksBox.setVisibility(open?View.VISIBLE:View.GONE);
            worldToggle.setText(open?"WORLD CLOCKS  ▴":"WORLD CLOCKS  ▾");
            prefs.edit().putBoolean("worldClocksOpen",open).apply();
        });

        TextView launchDivider=text("──────  UPCOMING ROCKET LAUNCHES  ──────",12,AMBER);
        launchDivider.setGravity(Gravity.CENTER);
        info.addView(launchDivider);

        launchSummary=text("LOADING UPCOMING MISSIONS…",13,TEXT);
        launchSummary.setBackgroundColor(Color.rgb(3,15,11));
        launchSummary.setPadding(dp(10),dp(10),dp(10),dp(10));
        info.addView(launchSummary);

        launchMoreButton=button("MORE");
        launchMoreButton.setOnClickListener(v -> {
            launchesExpanded=!launchesExpanded;
            launchMoreButton.setText(launchesExpanded?"LESS":"MORE");
            renderLaunches();
        });
        info.addView(launchMoreButton,new LinearLayout.LayoutParams(-1,dp(44)));

        infoScroll.addView(info);
        page.addView(infoScroll, new LinearLayout.LayoutParams(0, -1, 4.5f));
        pageHost.addView(page);
        refreshLaunches();

        clockTick = new Runnable() {
            public void run() {
                Date now = new Date();
                boolean h24 = prefs.getBoolean("clock24", true);
                SimpleDateFormat tf = new SimpleDateFormat(h24 ? "HH:mm:ss" : "hh:mm:ss a", Locale.UK);
                SimpleDateFormat df = new SimpleDateFormat("EEEE, d MMMM yyyy", Locale.UK);
                SimpleDateFormat zf = new SimpleDateFormat("z", Locale.UK);

                Calendar cal = Calendar.getInstance();
                timeClock.setText(tf.format(now));
                timeDate.setText(df.format(now));
                localZone.setText(TimeZone.getDefault().getID() + "  //  " + zf.format(now));
                calendarInfo.setText(
                    "DAY " + cal.get(Calendar.DAY_OF_YEAR) + " OF " + cal.getActualMaximum(Calendar.DAY_OF_YEAR) +
                    "   //   WEEK " + cal.get(Calendar.WEEK_OF_YEAR) + "\n" +
                    "LOCATION  " + locationLabel() + "   " +
                    String.format(Locale.US, "%.4f°, %.4f°", currentLat(), currentLon())
                );

                timeZones.setText(
                    zoneLine("UTC", "UTC", now, h24) + "\n\n" +
                    zoneLine("NEW YORK", "America/New_York", now, h24) + "\n\n" +
                    zoneLine("CHICAGO", "America/Chicago", now, h24) + "\n\n" +
                    zoneLine("LOS ANGELES", "America/Los_Angeles", now, h24) + "\n\n" +
                    zoneLine("TOKYO", "Asia/Tokyo", now, h24) + "\n\n" +
                    zoneLine("SYDNEY", "Australia/Sydney", now, h24)
                );
                analog.setTime(now);
                if(!launchList.isEmpty()) renderLaunches();
                ui.postDelayed(this, 1000);
            }
        };
        clockTick.run();
    }

    private void refreshLaunches() {
        if(launchSummary==null)return;
        launchSummary.setText("LOADING UPCOMING MISSIONS…");
        io.execute(() -> {
            final ArrayList<LegacyLaunch> loaded=new ArrayList<LegacyLaunch>();
            String error=null;
            try {
                String launchUrl="https://ll.thespacedevs.com/2.2.0/launch/upcoming/?limit=10";
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
                else renderLaunches();
            });
        });
    }

    private long parseIsoMillis(String iso) {
        if(iso==null||iso.length()==0)return 0;
        String[] patterns={
            "yyyy-MM-dd'T'HH:mm:ss.SSSXXX",
            "yyyy-MM-dd'T'HH:mm:ssXXX",
            "yyyy-MM-dd'T'HH:mm:ss'Z'"
        };
        for(String pattern:patterns){
            try{
                SimpleDateFormat f=new SimpleDateFormat(pattern,Locale.US);
                f.setTimeZone(TimeZone.getTimeZone("UTC"));
                Date d=f.parse(iso);
                if(d!=null)return d.getTime();
            }catch(Exception ignored){}
        }
        return 0;
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

    private void showSettings() {
        clearPage();
        ScrollView sc=new ScrollView(this);
        LinearLayout page=new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setPadding(dp(12),dp(10),dp(12),dp(20));
        page.setBackgroundColor(BG);
        page.addView(text("FIRE HD LEGACY SETTINGS",20,GREEN));

        EditText name=field(prefs.getString("place","Leeds"),"Location name");
        EditText lat=field(String.valueOf(currentLat()),"Latitude");
        EditText lon=field(String.valueOf(currentLon()),"Longitude");

        page.addView(text("LOCATION",13,DIM));
        page.addView(name); page.addView(lat); page.addView(lon);

        Button locate=button("USE LAST DEVICE LOCATION");
        locate.setOnClickListener(v -> {
            Location l=lastLocation();
            if(l!=null){lat.setText(String.valueOf(l.getLatitude()));lon.setText(String.valueOf(l.getLongitude()));name.setText("Device location");}
            else Toast.makeText(this,"No device location available",Toast.LENGTH_SHORT).show();
        });
        page.addView(locate);

        page.addView(text("THEME / ACCENT",13,DIM));
        final String[] themeIds={"PHOSPHOR","AMBER","CYAN","RED","VIOLET"};
        final String[] themeLabels={"Native Cyan / Teal","Amber Radar","Ice Cyan","Red Tactical","Violet Night"};
        Spinner theme=new Spinner(this);
        theme.setAdapter(new ArrayAdapter<String>(this, android.R.layout.simple_spinner_dropdown_item, themeLabels));
        int themeIndex=0;
        String savedTheme=prefs.getString("theme","PHOSPHOR");
        for(int i=0;i<themeIds.length;i++) if(themeIds[i].equals(savedTheme)) themeIndex=i;
        theme.setSelection(themeIndex);
        page.addView(theme);

        page.addView(text("DISPLAY UNITS",13,DIM));

        final String[] distanceUnits={"Kilometres (km)","Miles (mi)"};
        Spinner distanceUnit=new Spinner(this);
        distanceUnit.setAdapter(new ArrayAdapter<String>(this,android.R.layout.simple_spinner_dropdown_item,distanceUnits));
        distanceUnit.setSelection(prefs.getBoolean("miles",false)?1:0);
        page.addView(distanceUnit);

        final String[] tempUnits={"Celsius (°C)","Fahrenheit (°F)"};
        Spinner temperatureUnit=new Spinner(this);
        temperatureUnit.setAdapter(new ArrayAdapter<String>(this,android.R.layout.simple_spinner_dropdown_item,tempUnits));
        temperatureUnit.setSelection(prefs.getBoolean("fahrenheit",false)?1:0);
        page.addView(temperatureUnit);

        page.addView(text("RADAR STYLE",13,DIM));
        final String[] styleIds={"classic","tactical","pulse","dual","sonar","atc"};
        final String[] styleLabels={"Classic Scope","Tactical Grid","Centre Pulse","Dual Sweep","Sonar Rings","ATC Tower"};
        Spinner style=new Spinner(this);
        style.setAdapter(new ArrayAdapter<String>(this, android.R.layout.simple_spinner_dropdown_item, styleLabels));
        int styleIndex=0;
        String savedStyle=prefs.getString("radarStyle","classic");
        for(int i=0;i<styleIds.length;i++) if(styleIds[i].equals(savedStyle)) styleIndex=i;
        style.setSelection(styleIndex);
        page.addView(style);

        page.addView(text("RADAR ORIENTATION",13,DIM));
        final String[] orientations={"North up","East up","South up","West up"};
        Spinner orientation=new Spinner(this);
        orientation.setAdapter(new ArrayAdapter<String>(this, android.R.layout.simple_spinner_dropdown_item, orientations));
        orientation.setSelection((prefs.getInt("orientation",0)/90)%4);
        page.addView(orientation);

        CheckBox trails=new CheckBox(this);
        trails.setText("Show aircraft trails");
        trails.setTextColor(TEXT);
        trails.setChecked(prefs.getBoolean("trails",true));
        page.addView(trails);

        SeekBar range=new SeekBar(this);
        range.setMax(315);
        range.setProgress(prefs.getInt("range",40)-5);
        final boolean settingsMiles=prefs.getBoolean("miles",false);
        TextView rangeLabel=text("Radar range: "+distanceLabel(prefs.getInt("range",40)),13,CYAN);
        range.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){
            public void onProgressChanged(SeekBar s,int p,boolean user){
                int km=p+5;
                double display=settingsMiles?km/1.609344:km;
                rangeLabel.setText("Radar range: "+String.format(Locale.US,settingsMiles?"%.0f mi":"%.0f km",display));
            }
            public void onStartTrackingTouch(SeekBar s){} public void onStopTrackingTouch(SeekBar s){}
        });
        page.addView(rangeLabel);page.addView(range);

        page.addView(text("ALERT / DETECTION RANGE",13,DIM));
        CheckBox alertEnabled=new CheckBox(this);
        alertEnabled.setText("Show orange detection range");
        alertEnabled.setTextColor(TEXT);
        alertEnabled.setChecked(prefs.getBoolean("alertEnabled",true));
        page.addView(alertEnabled);

        SeekBar alertRange=new SeekBar(this);
        alertRange.setMax(315);
        alertRange.setProgress(Math.max(0,prefs.getInt("alertRange",10)-5));
        TextView alertLabel=text("Orange range: "+distanceLabel(prefs.getInt("alertRange",10)),13,AMBER);
        alertRange.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){
            public void onProgressChanged(SeekBar s,int p,boolean user){alertLabel.setText("Orange range: "+distanceLabel(p+5));}
            public void onStartTrackingTouch(SeekBar s){} public void onStopTrackingTouch(SeekBar s){}
        });
        page.addView(alertLabel);
        page.addView(alertRange);

        SeekBar refresh=new SeekBar(this);
        refresh.setMax(290);
        refresh.setProgress(prefs.getInt("refresh",30)-10);
        TextView refreshLabel=text("Radar refresh: "+prefs.getInt("refresh",30)+" seconds",13,CYAN);
        refresh.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){
            public void onProgressChanged(SeekBar s,int p,boolean user){refreshLabel.setText("Radar refresh: "+(p+10)+" seconds");}
            public void onStartTrackingTouch(SeekBar s){} public void onStopTrackingTouch(SeekBar s){}
        });
        page.addView(refreshLabel);page.addView(refresh);

        page.addView(text("AUTO PAGE CYCLING",13,DIM));
        CheckBox autoCycle=new CheckBox(this);
        autoCycle.setText("Automatically cycle Radar → Weather → Time");
        autoCycle.setTextColor(TEXT);
        autoCycle.setChecked(prefs.getBoolean("autoCycle",false));
        page.addView(autoCycle);

        SeekBar cycleSeconds=new SeekBar(this);
        cycleSeconds.setMax(112);
        cycleSeconds.setProgress(Math.max(0,prefs.getInt("cycleSeconds",20)-8));
        TextView cycleLabel=text("Page duration: "+prefs.getInt("cycleSeconds",20)+" seconds",13,CYAN);
        cycleSeconds.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){
            public void onProgressChanged(SeekBar s,int p,boolean user){cycleLabel.setText("Page duration: "+(p+8)+" seconds");}
            public void onStartTrackingTouch(SeekBar s){} public void onStopTrackingTouch(SeekBar s){}
        });
        page.addView(cycleLabel);page.addView(cycleSeconds);
        page.addView(text("Cycling pauses while Settings is open.",11,DIM));

        CheckBox clock24=new CheckBox(this);
        clock24.setText("24-hour clock");
        clock24.setTextColor(TEXT);
        clock24.setChecked(prefs.getBoolean("clock24",true));
        page.addView(clock24);

        Button save=button("SAVE SETTINGS");
        save.setOnClickListener(v -> {
            try{
                String chosenTheme=themeIds[theme.getSelectedItemPosition()];
                prefs.edit().putString("place",name.getText().toString().trim())
                    .putLong("latBits",Double.doubleToRawLongBits(Double.parseDouble(lat.getText().toString())))
                    .putLong("lonBits",Double.doubleToRawLongBits(Double.parseDouble(lon.getText().toString())))
                    .putInt("range",range.getProgress()+5)
                    .putInt("refresh",refresh.getProgress()+10)
                    .putBoolean("clock24",clock24.isChecked())
                    .putBoolean("miles",distanceUnit.getSelectedItemPosition()==1)
                    .putBoolean("fahrenheit",temperatureUnit.getSelectedItemPosition()==1)
                    .putBoolean("alertEnabled",alertEnabled.isChecked())
                    .putInt("alertRange",alertRange.getProgress()+5)
                    .putBoolean("autoCycle",autoCycle.isChecked())
                    .putInt("cycleSeconds",cycleSeconds.getProgress()+8)
                    .putBoolean("trails",trails.isChecked())
                    .putString("radarStyle",styleIds[style.getSelectedItemPosition()])
                    .putInt("orientation",orientation.getSelectedItemPosition()*90)
                    .putString("theme",chosenTheme).apply();

                applyTheme(chosenTheme);
                Toast.makeText(this,"Settings saved",Toast.LENGTH_SHORT).show();
                buildShell();
                schedulePageCycle();
                openPage("SETTINGS");
            }catch(Exception e){Toast.makeText(this,"Check latitude/longitude",Toast.LENGTH_SHORT).show();}
        });
        page.addView(save);

        page.addView(text("Designed for Fire OS 5 / Android 5.1 (API 22). Four-page parity build: Radar, Weather, Time and Settings.",11,DIM));
        sc.addView(page);pageHost.addView(sc);
    }

    private EditText field(String value,String hint){
        EditText e=new EditText(this);e.setText(value);e.setHint(hint);e.setTextColor(TEXT);e.setHintTextColor(DIM);e.setSingleLine(true);e.setBackgroundColor(PANEL);e.setPadding(dp(8),dp(8),dp(8),dp(8));return e;
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
        if(prefs.getBoolean("miles",false)) return String.format(Locale.US,"%.1f mi",km/1.609344);
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
        if(radarTick!=null)ui.removeCallbacks(radarTick);if(clockTick!=null)ui.removeCallbacks(clockTick);if(pageCycleTick!=null)ui.removeCallbacks(pageCycleTick);
        io.shutdownNow();super.onDestroy();
    }

    public static class WeatherTrendView extends View {
        private final Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);
        private final String title;
        private final int accent;
        private double[] values;
        private String unit="";
        private boolean unavailable=false;

        WeatherTrendView(Context context,String title,int accent){
            super(context);
            this.title=title;this.accent=accent;
            p.setTypeface(Typeface.MONOSPACE);
            setBackgroundColor(PANEL);
        }

        void setSeries(double[] series,String unit){
            this.values=series==null?null:series.clone();
            this.unit=unit==null?"":unit;
            unavailable=false;
            invalidate();
        }

        void setUnavailable(){unavailable=true;values=null;invalidate();}

        @Override protected void onDraw(Canvas canvas){
            super.onDraw(canvas);
            float w=getWidth(),h=getHeight();
            float left=Math.min(155f,w*.22f);
            float right=w-16f;
            float top=24f,bottom=h-14f;

            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(1f);
            p.setColor(Color.rgb(22,72,51));
            canvas.drawRect(1,1,w-2,h-2,p);

            p.setStyle(Paint.Style.FILL);
            p.setTextAlign(Paint.Align.LEFT);
            p.setTextSize(15f);
            p.setColor(CYAN);
            canvas.drawText(title,12,19,p);

            if(unavailable||values==null||values.length==0){
                p.setTextSize(13f);p.setColor(DIM);
                canvas.drawText("DATA UNAVAILABLE",left,Math.max(38,h/2f),p);
                return;
            }

            double min=Double.POSITIVE_INFINITY,max=Double.NEGATIVE_INFINITY;
            double latest=Double.NaN;
            int count=0;
            for(double v:values){
                if(Double.isNaN(v))continue;
                min=Math.min(min,v);max=Math.max(max,v);latest=v;count++;
            }
            if(count==0){setUnavailable();return;}
            if(Math.abs(max-min)<.001){max=min+1;}

            p.setTextSize(21f);p.setColor(TEXT);p.setTypeface(Typeface.create(Typeface.MONOSPACE,Typeface.BOLD));
            canvas.drawText(String.format(Locale.US,"%.1f%s",latest,unit),12,Math.min(h-18,50),p);
            p.setTypeface(Typeface.MONOSPACE);

            p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(1f);
            p.setColor(Color.argb(80,Color.red(GREEN),Color.green(GREEN),Color.blue(GREEN)));
            for(int g=0;g<=3;g++){
                float y=top+(bottom-top)*g/3f;
                canvas.drawLine(left,y,right,y,p);
            }

            p.setStyle(Paint.Style.FILL);
            p.setColor(accent);
            float slot=(right-left)/Math.max(1,values.length);
            float barWidth=Math.max(3f,slot*.58f);
            for(int i=0;i<values.length;i++){
                double v=values[i];if(Double.isNaN(v))continue;
                float x=left+slot*i+slot*.5f;
                float y=(float)(bottom-(v-min)/(max-min)*(bottom-top));
                canvas.drawRect(x-barWidth/2f,y,x+barWidth/2f,bottom,p);
            }
        }
    }

    static class WeatherHistoryData {
        final double[] temperature,humidity,wind,rain;
        final boolean fahrenheit;
        WeatherHistoryData(double[] t,double[] h,double[] w,double[] r,boolean f){temperature=t;humidity=h;wind=w;rain=r;fahrenheit=f;}
    }

    public static class WeatherHistoryView extends View {
        private final Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);
        private WeatherHistoryData data;
        WeatherHistoryView(Context c){super(c);p.setTypeface(Typeface.MONOSPACE);setBackgroundColor(PANEL);}
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
        WeatherCompassView(Context c){super(c);paint.setTypeface(Typeface.MONOSPACE);setBackgroundColor(PANEL);}
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
            setBackgroundColor(PANEL);
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
            drawHand(canvas, cx, cy, radius * .70f, minute * 6f + second * .1f, 8f, TEXT);
            drawHand(canvas, cx, cy, radius * .80f, second * 6f, 3f, GREEN);

            paint.setStyle(Paint.Style.FILL);
            paint.setColor(GREEN);
            canvas.drawCircle(cx, cy, 7f, paint);

            paint.setTextAlign(Paint.Align.CENTER);
            paint.setTextSize(22f);
            paint.setColor(TEXT);
            for(int n=1;n<=12;n++){
                double a=Math.toRadians(n*30-90);
                float nr=radius-34f;
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
            a.military=(o.optInt("dbFlags",0)&1)!=0;
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
            if (category == null) return "FIXED";
            String c=category.toUpperCase(Locale.US);
            if ("A7".equals(c)) return "FAST";
            if ("B7".equals(c)) return "HELICOPTER";
            if ("B1".equals(c) || "B2".equals(c)) return "LIGHT";
            if ("B4".equals(c)) return "GLIDER";
            if ("B6".equals(c)) return "UAV";
            return "FIXED";
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
        private OnAircraftTapListener listener;
        interface OnAircraftTapListener{void onTap(Aircraft a);}

        RadarView(Context c){
            super(c);
            p.setTypeface(Typeface.MONOSPACE);
            setBackgroundColor(BG);
        }

        void setOnAircraftTapListener(OnAircraftTapListener l){listener=l;}
        void setStyle(String s){style=s==null?"classic":s;invalidate();}
        void setOrientation(int degrees){orientation=((degrees%360)+360)%360;invalidate();}
        void setTrails(boolean value){showTrails=value;invalidate();}
        void setMiles(boolean value){miles=value;invalidate();}
        void setAlertRange(boolean enabled,int km){alertEnabled=enabled;alertRange=km;invalidate();}
        void setSelected(String hex){selected=hex;invalidate();}

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

        @Override protected void onDraw(Canvas c){
            super.onDraw(c);
            long now=android.os.SystemClock.uptimeMillis();
            float cx=getWidth()/2f,cy=getHeight()/2f;
            float rad=Math.min(getWidth(),getHeight())*.475f;
            boolean square="tactical".equals(style)||"pulse".equals(style);

            p.setStyle(Paint.Style.FILL);
            p.setColor(Color.rgb(3,15,11));
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
            for(Aircraft a:data){
                PointF pos=point(cx,cy,rad,(float)a.distanceKm,(float)a.bearing);
                int col=a.military?Color.rgb(255,102,119):(a.onGround?AMBER:GREEN);

                if(a.hex.equals(selected)){
                    p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(3);p.setColor(AMBER);
                    c.drawCircle(pos.x,pos.y,18,p);
                }

                drawAircraftIcon(c,a,pos.x,pos.y,col);

                if(a.track!=null && !a.onGround){
                    double ang=Math.toRadians(a.track-orientation-90);
                    float len=(float)Math.min("atc".equals(style)?80:40,(a.speedKnots==null?0:a.speedKnots)*1.852/120.0/range*rad);
                    p.setStyle(Paint.Style.STROKE);p.setStrokeWidth("atc".equals(style)?2:1);p.setColor(withAlpha(col,170));
                    c.drawLine(pos.x,pos.y,(float)(pos.x+Math.cos(ang)*len),(float)(pos.y+Math.sin(ang)*len),p);
                }

                p.setStyle(Paint.Style.FILL);p.setColor(col);
                if("atc".equals(style)){
                    c.drawText(a.callsign,pos.x+14,pos.y-8,p);
                    String level=a.altitudeFeet==null?"---":String.format(Locale.US,"%03d",Math.max(0,a.altitudeFeet/100));
                    String speed=a.speedKnots==null?"---":String.format(Locale.US,"%03d",(int)Math.round(a.speedKnots));
                    c.drawText(level+"  "+speed,pos.x+14,pos.y+9,p);
                    p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(1);p.setColor(withAlpha(col,120));
                    c.drawLine(pos.x+5,pos.y-4,pos.x+12,pos.y-7,p);
                } else c.drawText(a.callsign,pos.x+12,pos.y-8,p);
            }

            p.setColor(AMBER);p.setStrokeWidth(3);p.setStyle(Paint.Style.STROKE);
            c.drawLine(cx-8,cy,cx+8,cy,p);c.drawLine(cx,cy-8,cx,cy+8,p);
            p.setStyle(Paint.Style.FILL);p.setTextSize(13);c.drawText("HOME",cx+10,cy-8,p);

            postInvalidateDelayed(45);
        }

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
            float r=12f;
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
            Aircraft best=null;double bestPx=55;
            for(Aircraft a:data){
                PointF pos=point(cx,cy,rad,(float)a.distanceKm,(float)a.bearing);
                double d=Math.hypot(e.getX()-pos.x,e.getY()-pos.y);
                if(d<bestPx){bestPx=d;best=a;}
            }
            if(best!=null&&listener!=null)listener.onTap(best);
            return true;
        }
    }

}
