package org.inthesky.firelegacy;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

final class AircraftArticles {
    private static final Map<String,String> MAP = new HashMap<>();
    static {
        put("Airbus A220","BCS1","BCS3");
        put("Airbus A320 family","A318","A319","A320","A321");
        put("Airbus A320neo family","A19N","A20N","A21N");
        put("Airbus A330","A332","A333");
        put("Airbus A330neo","A338","A339");
        put("Airbus A350","A359","A35K");
        put("Airbus A380","A388");
        put("Boeing 737 Classic","B733","B734","B735");
        put("Boeing 737 Next Generation","B736","B737","B738","B739");
        put("Boeing 737 MAX","B37M","B38M","B39M","B3XM");
        put("Boeing 747","B741","B742","B743","B744","B748");
        put("Boeing 757","B752","B753");
        put("Boeing 767","B762","B763","B764");
        put("Boeing 777","B772","B773","B77L","B77W");
        put("Boeing 787 Dreamliner","B788","B789","B78X");
        put("Embraer E-Jet family","E170","E175","E190","E195");
        put("Embraer E-Jet E2 family","E290","E295");
        put("Bombardier CRJ700 series","CRJ7","CRJ9","CRJX");
        put("ATR 72","AT72","AT73","AT75","AT76");
        put("De Havilland Canada Dash 8","DH8A","DH8B","DH8C","DH8D");
        put("Cessna 172","C172");
        put("Cessna 208 Caravan","C208");
        put("Pilatus PC-12","PC12");
        put("Eurocopter EC135","EC35","H135");
        put("Eurocopter EC145","EC45","H145");
        put("AgustaWestland AW139","A139");
        put("Sikorsky S-76","S76");
        put("Sikorsky S-92","S92");
        put("Lockheed C-130 Hercules","C130");
        put("Lockheed Martin C-130J Super Hercules","C30J");
        put("Boeing C-17 Globemaster III","C17");
        put("Airbus A400M Atlas","A400");
        put("Eurofighter Typhoon","EUFI");
        put("Lockheed Martin F-35 Lightning II","F35");
        put("General Dynamics F-16 Fighting Falcon","F16");
        put("Boeing CH-47 Chinook","H47");
    }
    private static void put(String title, String... codes) {
        for (String c : codes) MAP.put(c, title);
    }
    static String article(String code) {
        if (code == null) return null;
        return MAP.get(code.trim().toUpperCase(Locale.US));
    }
}
