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
    private static final int PANEL = Color.rgb(6,19,13);
    private static final int GREEN = Color.rgb(79,255,159);
    private static final int DIM = Color.rgb(97,169,129);
    private static final int CYAN = Color.rgb(102,228,255);
    private static final int AMBER = Color.rgb(255,194,92);
    private static final int TEXT = Color.rgb(225,255,238);

    private final Handler ui = new Handler(Looper.getMainLooper());
    private final ExecutorService io = Executors.newFixedThreadPool(3);
    private SharedPreferences prefs;
    private LinearLayout root;
    private FrameLayout pageHost;
    private RadarView radarView;
    private LinearLayout aircraftCard;
    private ImageView aircraftImage;
    private TextView aircraftTitle, aircraftDetails, aircraftReference;
    private TextView weatherBody, weatherStatus;
    private TextView timeClock, timeDate, timeZones;
    private Runnable radarTick, clockTick;
    private SSLSocketFactory weatherSslFactory;
    private List<Aircraft> aircraft = new ArrayList<Aircraft>();

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        enableTls12();
        prefs = getSharedPreferences("fire_settings", MODE_PRIVATE);
        getWindow().setStatusBarColor(BG);
        buildShell();
        showRadar();
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

    private TextView text(String s, int sp, int color) {
        TextView v = new TextView(this);
        v.setText(s); v.setTextSize(sp); v.setTextColor(color);
        v.setPadding(dp(8),dp(6),dp(8),dp(6));
        return v;
    }

    private Button button(String s) {
        Button b = new Button(this);
        b.setText(s); b.setTextSize(12); b.setTextColor(TEXT);
        b.setBackgroundColor(PANEL);
        b.setAllCaps(false);
        return b;
    }

    private void buildShell() {
        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(BG);

        TextView header = text("IN THE SKY  //  FIRE HD LEGACY", 18, GREEN);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setBackgroundColor(PANEL);
        root.addView(header, new LinearLayout.LayoutParams(-1, dp(52)));

        pageHost = new FrameLayout(this);
        root.addView(pageHost, new LinearLayout.LayoutParams(-1, 0, 1f));

        LinearLayout nav = new LinearLayout(this);
        nav.setOrientation(LinearLayout.HORIZONTAL);
        nav.setBackgroundColor(PANEL);
        String[] labels = {"RADAR","WEATHER","TIME","SETTINGS"};
        for (String label : labels) {
            Button b = button(label);
            final String page = label;
            b.setOnClickListener(v -> {
                if ("RADAR".equals(page)) showRadar();
                else if ("WEATHER".equals(page)) showWeather();
                else if ("TIME".equals(page)) showTime();
                else showSettings();
            });
            nav.addView(b, new LinearLayout.LayoutParams(0, dp(56), 1f));
        }
        root.addView(nav);
        setContentView(root);
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
        TextView status = text("LIVE AIRCRAFT", 11, DIM);
        Button refresh = button("SCAN NOW");
        refresh.setOnClickListener(v -> refreshRadar(status));
        controls.addView(status, new LinearLayout.LayoutParams(0, dp(46), 1f));
        controls.addView(refresh, new LinearLayout.LayoutParams(dp(118), dp(46)));
        page.addView(controls);

        LinearLayout body = new LinearLayout(this);
        body.setOrientation(LinearLayout.HORIZONTAL);
        body.setWeightSum(10f);

        radarView = new RadarView(this);
        radarView.setOnAircraftTapListener(this::selectAircraft);
        LinearLayout.LayoutParams radarLp = new LinearLayout.LayoutParams(0, -1, 7f);
        radarLp.setMargins(dp(6), dp(2), dp(4), dp(6));
        body.addView(radarView, radarLp);

        ScrollView dataScroll = new ScrollView(this);
        dataScroll.setFillViewport(true);
        LinearLayout dataPanel = new LinearLayout(this);
        dataPanel.setOrientation(LinearLayout.VERTICAL);
        dataPanel.setPadding(dp(8), dp(8), dp(8), dp(8));
        dataPanel.setBackgroundColor(PANEL);

        dataPanel.addView(text("SELECTED AIRCRAFT", 12, DIM));

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
        io.execute(() -> {
            try {
                JSONObject root = getJson("https://api.adsb.lol/v2/point/"+lat+"/"+lon+"/"+radiusNm);
                JSONArray arr = root.optJSONArray("ac");
                final ArrayList<Aircraft> list = new ArrayList<Aircraft>();
                if (arr != null) for (int i=0;i<arr.length();i++) {
                    JSONObject o = arr.optJSONObject(i); if (o == null) continue;
                    if (!o.has("lat") || !o.has("lon")) continue;
                    Aircraft a = Aircraft.from(o, lat, lon);
                    if (a.distanceKm <= rangeKm) list.add(a);
                }
                Collections.sort(list, (a,b) -> Double.compare(a.distanceKm,b.distanceKm));
                ui.post(() -> {
                    aircraft = list;
                    if (radarView != null) radarView.setAircraft(list, rangeKm);
                    status.setText(list.size()+" CONTACTS  //  "+rangeKm+" km");
                });
            } catch (final Exception e) {
                ui.post(() -> status.setText("RADAR ERROR: "+shortError(e)));
            }
        });
    }

    private void selectAircraft(final Aircraft a) {
        aircraftTitle.setText(a.callsign+"  //  "+a.type);
        aircraftDetails.setText(
            "ICAO "+a.hex.toUpperCase(Locale.US)+"   REG "+empty(a.registration)+"\n"+
            "ALT "+fmtInt(a.altitudeFeet)+" ft   SPD "+fmt(a.speedKnots)+" kt   HDG "+fmt(a.track)+"°\n"+
            "RANGE "+String.format(Locale.US,"%.1f km",a.distanceKm)+"   BRG "+String.format(Locale.US,"%.0f°",a.bearing)+"\n"+
            empty(a.description)
        );
        aircraftReference.setText("Loading aircraft reference…");
        aircraftImage.setImageDrawable(new ColorDrawable(Color.rgb(3,12,8)));

        io.execute(() -> {
            String meta = "";
            String wikiText = "";
            Bitmap bitmap = null;
            try {
                JSONObject m = getJson("https://api.adsbdb.com/v0/aircraft/"+a.hex)
                    .optJSONObject("response").optJSONObject("aircraft");
                if (m != null) meta =
                    nonBlank(m.optString("manufacturer"))+" "+nonBlank(m.optString("type"))+
                    "\nOwner: "+nonBlank(m.optString("registered_owner"))+
                    "\nCountry: "+nonBlank(m.optString("registered_owner_country_name"));
            } catch (Exception ignored) {}

            try {
                String article = AircraftArticles.article(a.type);
                if (article == null && a.description != null && a.description.length() > 3) article = a.description;
                if (article != null) {
                    String enc = URLEncoder.encode(article, "UTF-8").replace("+","%20");
                    JSONObject w = getJson("https://en.wikipedia.org/api/rest_v1/page/summary/"+enc);
                    wikiText = w.optString("extract");
                    JSONObject thumb = w.optJSONObject("thumbnail");
                    if (thumb != null) bitmap = getBitmap(thumb.optString("source"));
                }
            } catch (Exception ignored) {}

            final String reference = (meta.trim()+"\n"+wikiText.trim()).trim();
            final Bitmap finalBitmap = bitmap;
            ui.post(() -> {
                if (aircraftTitle.getText().toString().startsWith(a.callsign)) {
                    aircraftReference.setText(reference.length()==0 ?
                        "Aircraft-type reference unavailable." : reference);
                    if (finalBitmap != null) aircraftImage.setImageBitmap(finalBitmap);
                }
            });
        });
    }

    private void showWeather() {
        clearPage();
        ScrollView sc = new ScrollView(this);
        LinearLayout page = new LinearLayout(this); page.setOrientation(LinearLayout.VERTICAL); page.setBackgroundColor(BG);
        weatherStatus = text("WEATHER // "+locationLabel(), 16, GREEN);
        Button refresh = button("REFRESH WEATHER");
        refresh.setOnClickListener(v -> refreshWeather());
        weatherBody = text("Loading MET Norway forecast…", 13, TEXT);
        page.addView(weatherStatus); page.addView(refresh); page.addView(weatherBody);
        sc.addView(page); pageHost.addView(sc);
        refreshWeather();
    }

    private void refreshWeather() {
        weatherStatus.setText("WEATHER // "+locationLabel()+" // UPDATING");
        final double lat=currentLat(), lon=currentLon();
        io.execute(() -> {
            try {
                JSONObject root=getJson("https://api.met.no/weatherapi/locationforecast/2.0/compact?lat="+lat+"&lon="+lon);
                JSONArray ts=root.getJSONObject("properties").getJSONArray("timeseries");
                StringBuilder sb=new StringBuilder();
                int count=Math.min(12,ts.length());
                for(int i=0;i<count;i++){
                    JSONObject p=ts.getJSONObject(i);
                    JSONObject d=p.getJSONObject("data").getJSONObject("instant").getJSONObject("details");
                    String time=p.optString("time");
                    double temp=d.optDouble("air_temperature");
                    double wind=d.optDouble("wind_speed");
                    double hum=d.optDouble("relative_humidity");
                    String symbol="";
                    JSONObject next=p.getJSONObject("data").optJSONObject("next_1_hours");
                    if(next!=null) symbol=next.optJSONObject("summary").optString("symbol_code");
                    sb.append(shortIso(time)).append("  ")
                      .append(String.format(Locale.US,"%.1f°C",temp)).append("  ")
                      .append(String.format(Locale.US,"wind %.1f m/s",wind)).append("  ")
                      .append(String.format(Locale.US,"RH %.0f%%",hum)).append("  ")
                      .append(symbol.replace("_"," ")).append("\n\n");
                }
                final String out=sb.toString();
                ui.post(() -> {weatherStatus.setText("WEATHER // "+locationLabel());weatherBody.setText(out);});
            }catch(final Exception e){
                ui.post(() -> weatherStatus.setText("WEATHER ERROR: "+shortError(e)));
            }
        });
    }

    private void showTime() {
        clearPage();

        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.HORIZONTAL);
        page.setWeightSum(10f);
        page.setPadding(dp(10), dp(10), dp(10), dp(10));
        page.setBackgroundColor(BG);

        AnalogClockView analog = new AnalogClockView(this);
        LinearLayout.LayoutParams analogLp = new LinearLayout.LayoutParams(0, -1, 6f);
        analogLp.setMargins(0, 0, dp(8), 0);
        page.addView(analog, analogLp);

        ScrollView infoScroll = new ScrollView(this);
        LinearLayout info = new LinearLayout(this);
        info.setOrientation(LinearLayout.VERTICAL);
        info.setPadding(dp(12), dp(10), dp(12), dp(10));
        info.setBackgroundColor(PANEL);

        info.addView(text("LOCAL TIME", 12, DIM));
        timeClock = text("", 46, GREEN);
        timeDate = text("", 18, TEXT);
        TextView localZone = text("", 12, CYAN);
        TextView calendarInfo = text("", 12, DIM);
        timeZones = text("", 15, TEXT);

        info.addView(timeClock);
        info.addView(timeDate);
        info.addView(localZone);
        info.addView(calendarInfo);
        info.addView(text("WORLD CLOCKS", 12, DIM));
        info.addView(timeZones);

        infoScroll.addView(info);
        page.addView(infoScroll, new LinearLayout.LayoutParams(0, -1, 4f));
        pageHost.addView(page);

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
                    zoneLine("TOKYO", "Asia/Tokyo", now, h24) + "\n\n" +
                    zoneLine("SYDNEY", "Australia/Sydney", now, h24)
                );
                analog.setTime(now);
                ui.postDelayed(this, 1000);
            }
        };
        clockTick.run();
    }

    private String zoneLine(String label, String zone, Date now, boolean h24) {
        SimpleDateFormat f = new SimpleDateFormat(h24 ? "HH:mm:ss  z" : "hh:mm:ss a  z", Locale.UK);
        f.setTimeZone(TimeZone.getTimeZone(zone));
        return label + "\n" + f.format(now);
    }

    private void showSettings() {
        clearPage();
        ScrollView sc=new ScrollView(this);
        LinearLayout page=new LinearLayout(this);page.setOrientation(LinearLayout.VERTICAL);page.setPadding(dp(12),dp(10),dp(12),dp(20));page.setBackgroundColor(BG);
        page.addView(text("FIRE HD LEGACY SETTINGS",20,GREEN));

        EditText name=field(prefs.getString("place","Leeds"),"Location name");
        EditText lat=field(String.valueOf(currentLat()),"Latitude");
        EditText lon=field(String.valueOf(currentLon()),"Longitude");
        SeekBar range=new SeekBar(this);range.setMax(195);range.setProgress(prefs.getInt("range",40)-5);
        TextView rangeLabel=text("Radar range: "+prefs.getInt("range",40)+" km",13,CYAN);
        range.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){
            public void onProgressChanged(SeekBar s,int p,boolean user){rangeLabel.setText("Radar range: "+(p+5)+" km");}
            public void onStartTrackingTouch(SeekBar s){} public void onStopTrackingTouch(SeekBar s){}
        });
        SeekBar refresh=new SeekBar(this);refresh.setMax(110);refresh.setProgress(prefs.getInt("refresh",30)-10);
        TextView refreshLabel=text("Radar refresh: "+prefs.getInt("refresh",30)+" seconds",13,CYAN);
        refresh.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){
            public void onProgressChanged(SeekBar s,int p,boolean user){refreshLabel.setText("Radar refresh: "+(p+10)+" seconds");}
            public void onStartTrackingTouch(SeekBar s){} public void onStopTrackingTouch(SeekBar s){}
        });
        CheckBox clock24=new CheckBox(this);clock24.setText("24-hour clock");clock24.setTextColor(TEXT);clock24.setChecked(prefs.getBoolean("clock24",true));
        Button locate=button("USE LAST DEVICE LOCATION");
        locate.setOnClickListener(v -> {
            Location l=lastLocation();
            if(l!=null){lat.setText(String.valueOf(l.getLatitude()));lon.setText(String.valueOf(l.getLongitude()));name.setText("Device location");}
            else Toast.makeText(this,"No device location available",Toast.LENGTH_SHORT).show();
        });
        Button save=button("SAVE SETTINGS");
        save.setOnClickListener(v -> {
            try{
                prefs.edit().putString("place",name.getText().toString().trim())
                    .putLong("latBits",Double.doubleToRawLongBits(Double.parseDouble(lat.getText().toString())))
                    .putLong("lonBits",Double.doubleToRawLongBits(Double.parseDouble(lon.getText().toString())))
                    .putInt("range",range.getProgress()+5)
                    .putInt("refresh",refresh.getProgress()+10)
                    .putBoolean("clock24",clock24.isChecked()).apply();
                Toast.makeText(this,"Settings saved",Toast.LENGTH_SHORT).show();
            }catch(Exception e){Toast.makeText(this,"Check latitude/longitude",Toast.LENGTH_SHORT).show();}
        });

        page.addView(text("LOCATION",13,DIM));page.addView(name);page.addView(lat);page.addView(lon);page.addView(locate);
        page.addView(rangeLabel);page.addView(range);page.addView(refreshLabel);page.addView(refresh);
        page.addView(clock24);page.addView(save);
        page.addView(text("Designed for Fire OS 5 / Android 5.1 (API 22). This legacy build intentionally contains only Radar, Weather, Time and Settings.",11,DIM));
        sc.addView(page);pageHost.addView(sc);
    }

    private EditText field(String value,String hint){
        EditText e=new EditText(this);e.setText(value);e.setHint(hint);e.setTextColor(TEXT);e.setHintTextColor(DIM);e.setSingleLine(true);e.setBackgroundColor(PANEL);e.setPadding(dp(8),dp(8),dp(8),dp(8));return e;
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
            return new JSONObject(sb.toString());
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

    private String shortIso(String iso){if(iso==null)return "--";return iso.length()>=16?iso.substring(11,16):iso;}
    private String shortError(Exception e){String s=e.getMessage();return s==null?e.getClass().getSimpleName():s;}
    private String empty(String s){return s==null||s.trim().length()==0?"--":s;}
    private String nonBlank(String s){return s==null||s.trim().length()==0?"--":s.trim();}
    private String fmt(Double d){return d==null?"--":String.format(Locale.US,"%.0f",d);}
    private String fmtInt(Integer i){return i==null?"--":String.valueOf(i);}
    private int dp(int v){return (int)(v*getResources().getDisplayMetrics().density+0.5f);}

    @Override protected void onDestroy(){
        if(radarTick!=null)ui.removeCallbacks(radarTick);if(clockTick!=null)ui.removeCallbacks(clockTick);
        io.shutdownNow();super.onDestroy();
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
            float radius = Math.min(getWidth(), getHeight()) * 0.40f;

            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(4f);
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

            drawHand(canvas, cx, cy, radius * .52f, (hour + minute / 60f) * 30f, 7f, AMBER);
            drawHand(canvas, cx, cy, radius * .73f, minute * 6f + second * .1f, 5f, TEXT);
            drawHand(canvas, cx, cy, radius * .82f, second * 6f, 2f, GREEN);

            paint.setStyle(Paint.Style.FILL);
            paint.setColor(GREEN);
            canvas.drawCircle(cx, cy, 7f, paint);

            paint.setTextAlign(Paint.Align.CENTER);
            paint.setTextSize(24f);
            paint.setColor(DIM);
            canvas.drawText("12", cx, cy - radius + 35f, paint);
            canvas.drawText("3", cx + radius - 24f, cy + 8f, paint);
            canvas.drawText("6", cx, cy + radius - 18f, paint);
            canvas.drawText("9", cx - radius + 24f, cy + 8f, paint);
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
        String hex,callsign,registration,type,description;double lat,lon,distanceKm,bearing;Double speedKnots,track;Integer altitudeFeet;
        static Aircraft from(JSONObject o,double homeLat,double homeLon){
            Aircraft a=new Aircraft();a.hex=o.optString("hex");a.callsign=o.optString("flight").trim();
            if(a.callsign.length()==0)a.callsign=o.optString("r",a.hex.toUpperCase(Locale.US));
            a.registration=o.optString("r").trim();a.type=o.optString("t").trim();a.description=o.optString("desc").trim();
            a.lat=o.optDouble("lat");a.lon=o.optDouble("lon");
            Object alt=o.opt("alt_baro");if(alt instanceof Number)a.altitudeFeet=((Number)alt).intValue();
            a.speedKnots=o.has("gs")?o.optDouble("gs"):null;a.track=o.has("track")?o.optDouble("track"):null;
            double[] db=distanceBearing(homeLat,homeLon,a.lat,a.lon);a.distanceKm=db[0];a.bearing=db[1];return a;
        }
        static double[] distanceBearing(double lat1,double lon1,double lat2,double lon2){
            double p1=Math.toRadians(lat1),p2=Math.toRadians(lat2),dp=Math.toRadians(lat2-lat1),dl=Math.toRadians(lon2-lon1);
            double x=Math.sin(dp/2)*Math.sin(dp/2)+Math.cos(p1)*Math.cos(p2)*Math.sin(dl/2)*Math.sin(dl/2);
            double dist=6371.0088*2*Math.atan2(Math.sqrt(x),Math.sqrt(1-x));
            double y=Math.sin(dl)*Math.cos(p2),xx=Math.cos(p1)*Math.sin(p2)-Math.sin(p1)*Math.cos(p2)*Math.cos(dl);
            double br=(Math.toDegrees(Math.atan2(y,xx))+360)%360;return new double[]{dist,br};
        }
    }

    public static class RadarView extends View {
        private Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);private List<Aircraft> data=new ArrayList<Aircraft>();private int range=40;
        private OnAircraftTapListener listener;
        interface OnAircraftTapListener{void onTap(Aircraft a);}
        void setOnAircraftTapListener(OnAircraftTapListener l){listener=l;}
        RadarView(Context c){super(c);p.setTypeface(Typeface.MONOSPACE);setBackgroundColor(BG);}
        void setAircraft(List<Aircraft> a,int r){data=new ArrayList<Aircraft>(a);range=r;invalidate();}
        @Override protected void onDraw(Canvas c){
            super.onDraw(c);float cx=getWidth()/2f,cy=getHeight()/2f,rad=Math.min(getWidth(),getHeight())*.44f;
            p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(2);p.setColor(Color.rgb(25,95,60));
            for(int i=1;i<=4;i++)c.drawCircle(cx,cy,rad*i/4f,p);
            p.setColor(DIM);p.setTextSize(22);c.drawText("N",cx-7,cy-rad-8,p);c.drawText("S",cx-7,cy+rad+26,p);c.drawText("W",cx-rad-26,cy+7,p);c.drawText("E",cx+rad+10,cy+7,p);
            p.setColor(GREEN);p.setStrokeWidth(3);c.drawLine(cx-8,cy,cx+8,cy,p);c.drawLine(cx,cy-8,cx,cy+8,p);
            p.setStyle(Paint.Style.FILL);p.setTextSize(18);
            for(Aircraft a:data){
                double rr=Math.min(1.0,a.distanceKm/range)*rad;double ang=Math.toRadians(a.bearing-90);
                float x=(float)(cx+Math.cos(ang)*rr),y=(float)(cy+Math.sin(ang)*rr);
                p.setColor(a.altitudeFeet!=null&&a.altitudeFeet<5000?AMBER:CYAN);c.drawCircle(x,y,7,p);
                p.setColor(TEXT);c.drawText(a.callsign,x+9,y-8,p);
            }
        }
        @Override public boolean onTouchEvent(android.view.MotionEvent e){
            if(e.getAction()!=MotionEvent.ACTION_UP)return true;
            float cx=getWidth()/2f,cy=getHeight()/2f,rad=Math.min(getWidth(),getHeight())*.44f;Aircraft best=null;double bestPx=50;
            for(Aircraft a:data){
                double rr=Math.min(1.0,a.distanceKm/range)*rad,ang=Math.toRadians(a.bearing-90);
                double x=cx+Math.cos(ang)*rr,y=cy+Math.sin(ang)*rr,d=Math.hypot(e.getX()-x,e.getY()-y);
                if(d<bestPx){bestPx=d;best=a;}
            }
            if(best!=null&&listener!=null)listener.onTap(best);return true;
        }
    }
}
