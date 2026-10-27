package com.mazem3dia.waktusolat;

/**
 * JAKIM's 60 prayer-time zones, as e-solat.gov.my lists them (October 2026), with the approximate position of
 * the main towns JAKIM names in each, for Auto. Island and summit zones (Pulau Aur, Tioman, Jerai, Bukit Larut,
 * Kinabalu, Kampung Patarikan) have no towns: they are chosen by hand, since the nearest-town rule would pull
 * their mainland neighbours into them. Generated from JAKIM's zone list; edit by hand only for a JAKIM change.
 */
final class Zones {
    static final String[] CODES = {
            "JHR01", "JHR02", "JHR03", "JHR04", "KDH01", "KDH02", "KDH03", "KDH04", "KDH05", "KDH06",
            "KDH07", "KTN01", "KTN02", "MLK01", "NGS01", "NGS02", "NGS03", "PHG01", "PHG02", "PHG03",
            "PHG04", "PHG05", "PHG06", "PHG07", "PLS01", "PNG01", "PRK01", "PRK02", "PRK03", "PRK04",
            "PRK05", "PRK06", "PRK07", "SBH01", "SBH02", "SBH03", "SBH04", "SBH05", "SBH06", "SBH07",
            "SBH08", "SBH09", "SGR01", "SGR02", "SGR03", "SWK01", "SWK02", "SWK03", "SWK04", "SWK05",
            "SWK06", "SWK07", "SWK08", "SWK09", "TRG01", "TRG02", "TRG03", "TRG04", "WLY01", "WLY02",
    };
    static final String[] STATES = {
            "Johor",
            "Johor",
            "Johor",
            "Johor",
            "Kedah",
            "Kedah",
            "Kedah",
            "Kedah",
            "Kedah",
            "Kedah",
            "Kedah",
            "Kelantan",
            "Kelantan",
            "Melaka",
            "Negeri Sembilan",
            "Negeri Sembilan",
            "Negeri Sembilan",
            "Pahang",
            "Pahang",
            "Pahang",
            "Pahang",
            "Pahang",
            "Pahang",
            "Pahang",
            "Perlis",
            "Pulau Pinang",
            "Perak",
            "Perak",
            "Perak",
            "Perak",
            "Perak",
            "Perak",
            "Perak",
            "Sabah",
            "Sabah",
            "Sabah",
            "Sabah",
            "Sabah",
            "Sabah",
            "Sabah",
            "Sabah",
            "Sabah",
            "Selangor",
            "Selangor",
            "Selangor",
            "Sarawak",
            "Sarawak",
            "Sarawak",
            "Sarawak",
            "Sarawak",
            "Sarawak",
            "Sarawak",
            "Sarawak",
            "Sarawak",
            "Terengganu",
            "Terengganu",
            "Terengganu",
            "Terengganu",
            "Wilayah Persekutuan",
            "Wilayah Persekutuan",
    };
    static final String[] AREAS = {
            "Pulau Aur dan Pulau Pemanggil",
            "Johor Bahru, Kota Tinggi, Mersing, Kulai",
            "Kluang, Pontian",
            "Batu Pahat, Muar, Segamat, Gemas Johor, Tangkak",
            "Kota Setar, Kubang Pasu, Pokok Sena (Daerah Kecil)",
            "Kuala Muda, Yan, Pendang",
            "Padang Terap, Sik",
            "Baling",
            "Bandar Baharu, Kulim",
            "Langkawi",
            "Puncak Gunung Jerai",
            "Bachok, Kota Bharu, Machang, Pasir Mas, Pasir Puteh, Tanah Merah, Tumpat, Kuala Krai, Mukim Chiku",
            "Gua Musang (Daerah Galas Dan Bertam), Jeli, Jajahan Kecil Lojing",
            "Seluruh Negeri Melaka",
            "Tampin, Jempol",
            "Jelebu, Kuala Pilah, Rembau",
            "Port Dickson, Seremban",
            "Pulau Tioman",
            "Kuantan, Pekan, Muadzam Shah",
            "Jerantut, Temerloh, Maran, Bera, Chenor, Jengka",
            "Bentong, Lipis, Raub",
            "Genting Sempah, Janda Baik, Bukit Tinggi",
            "Cameron Highlands, Genting Higlands, Bukit Fraser",
            "Daerah Rompin, (Mukim Rompin, Mukim Endau, Mukim Pontian)",
            "Kangar, Padang Besar, Arau",
            "Seluruh Negeri Pulau Pinang",
            "Tapah, Slim River, Tanjung Malim",
            "Kuala Kangsar, Sg. Siput , Ipoh, Batu Gajah, Kampar",
            "Lenggong, Pengkalan Hulu, Grik",
            "Temengor, Belum",
            "Kg Gajah, Teluk Intan, Bagan Datuk, Seri Iskandar, Beruas, Parit, Lumut, Sitiawan, Pulau Pangkor",
            "Selama, Taiping, Bagan Serai, Parit Buntar",
            "Bukit Larut",
            "Bahagian Sandakan (Timur), Bukit Garam, Semawang, Temanggong, Tambisan, Bandar Sandakan, Sukau",
            "Beluran, Telupid, Pinangah, Terusan, Kuamut, Bahagian Sandakan (Barat)",
            "Lahad Datu, Silabukan, Kunak, Sahabat, Semporna, Tungku, Bahagian Tawau (Timur)",
            "Bandar Tawau, Balong, Merotai, Kalabakan, Bahagian Tawau (Barat)",
            "Kudat, Kota Marudu, Pitas, Pulau Banggi, Bahagian Kudat",
            "Gunung Kinabalu",
            "Kota Kinabalu, Ranau, Kota Belud, Tuaran, Penampang, Papar, Putatan, Bahagian Pantai Barat",
            "Pensiangan, Keningau, Tambunan, Nabawan, Bahagian Pendalaman (Atas)",
            "Beaufort, Kuala Penyu, Sipitang, Tenom, Long Pasia, Membakut, Weston, Bahagian Pendalaman (Bawah)",
            "Gombak, Petaling, Sepang, Hulu Langat, Hulu Selangor, S.Alam",
            "Kuala Selangor, Sabak Bernam",
            "Klang, Kuala Langat",
            "Limbang, Lawas, Sundar, Trusan",
            "Miri, Niah, Bekenu, Sibuti, Marudi",
            "Pandan, Belaga, Suai, Tatau, Sebauh, Bintulu",
            "Sibu, Mukah, Dalat, Song, Igan, Oya, Balingian, Kanowit, Kapit",
            "Sarikei, Matu, Julau, Rajang, Daro, Bintangor, Belawai",
            "Lubok Antu, Sri Aman, Roban, Debak, Kabong, Lingga, Engkelili, Betong, Spaoh, Pusa, Saratok",
            "Serian, Simunjan, Samarahan, Sebuyau, Meludam",
            "Kuching, Bau, Lundu, Sematan",
            "Zon Khas (Kampung Patarikan)",
            "Kuala Terengganu, Marang, Kuala Nerus",
            "Besut, Setiu",
            "Hulu Terengganu",
            "Dungun, Kemaman",
            "Kuala Lumpur, Putrajaya",
            "Labuan",
    };
    /** Each zone's main towns as latitude, longitude pairs; none for zones chosen by hand only. */
    static final double[][] TOWNS = {
            {},   // JHR01
            {1.49, 103.74, 1.73, 103.90, 2.43, 103.84, 1.66, 103.60},   // JHR02
            {2.03, 103.32, 1.49, 103.39},   // JHR03
            {1.85, 102.93, 2.04, 102.57, 2.51, 102.82, 2.27, 102.55},   // JHR04
            {6.12, 100.37, 6.27, 100.42, 6.17, 100.52},   // KDH01
            {5.65, 100.49, 5.80, 100.38, 5.99, 100.48},   // KDH02
            {6.25, 100.61, 5.81, 100.73},   // KDH03
            {5.68, 100.92},   // KDH04
            {5.37, 100.56, 5.21, 100.61},   // KDH05
            {6.35, 99.80},   // KDH06
            {},   // KDH07
            {6.13, 102.24, 5.53, 102.20, 5.77, 102.22, 5.83, 102.40, 5.80, 102.15},   // KTN01
            {4.88, 101.97, 5.70, 101.84},   // KTN02
            {2.19, 102.25, 2.38, 102.21, 2.31, 102.43},   // MLK01
            {2.47, 102.23, 2.81, 102.41},   // NGS01
            {2.74, 102.25, 2.94, 102.07, 2.59, 102.09},   // NGS02
            {2.73, 101.94, 2.52, 101.80},   // NGS03
            {},   // PHG01
            {3.82, 103.33, 3.49, 103.39, 3.05, 103.08},   // PHG02
            {3.45, 102.42, 3.94, 102.36, 3.59, 102.77, 3.10, 102.40, 3.75, 102.55},   // PHG03
            {3.52, 101.91, 3.79, 101.86, 4.18, 102.05},   // PHG04
            {3.33, 101.86, 3.40, 101.83},   // PHG05
            {4.47, 101.38, 3.71, 101.74, 3.42, 101.79},   // PHG06
            {2.80, 103.49, 2.65, 103.62},   // PHG07
            {6.44, 100.20, 6.66, 100.32},   // PLS01
            {5.41, 100.33, 5.40, 100.36, 5.36, 100.47, 5.17, 100.48},   // PNG01
            {4.20, 101.26, 3.68, 101.52, 3.83, 101.40},   // PRK01
            {4.60, 101.08, 4.77, 100.94, 4.31, 101.15, 4.47, 101.04},   // PRK02
            {5.43, 101.13, 5.10, 100.97, 5.70, 100.99},   // PRK03
            {5.40, 101.55},   // PRK04
            {4.02, 101.02, 4.22, 100.70, 4.23, 100.63, 4.36, 100.96, 3.99, 100.78},   // PRK05
            {4.85, 100.74, 5.13, 100.49, 5.01, 100.54, 5.22, 100.69},   // PRK06
            {},   // PRK07
            {5.84, 118.12},   // SBH01
            {5.89, 117.56, 5.63, 117.12},   // SBH02
            {5.03, 118.33, 4.48, 118.61, 4.69, 118.25},   // SBH03
            {4.24, 117.89, 4.42, 117.48},   // SBH04
            {6.88, 116.84, 6.49, 116.74, 6.72, 117.03},   // SBH05
            {},   // SBH06
            {5.98, 116.07, 5.95, 116.66, 6.35, 116.43, 5.73, 115.93, 6.18, 116.23},   // SBH07
            {5.34, 116.16, 5.67, 116.37, 5.04, 116.45},   // SBH08
            {5.35, 115.75, 5.08, 115.55, 5.12, 115.95, 5.57, 115.60},   // SBH09
            {3.07, 101.52, 3.11, 101.61, 3.05, 101.58, 2.99, 101.79, 3.15, 101.76, 3.32, 101.58, 3.25, 101.65, 2.69, 101.75, 2.92, 101.65, 3.57, 101.66},   // SGR01
            {3.34, 101.25, 3.77, 100.99, 3.42, 101.18},   // SGR02
            {3.04, 101.45, 2.81, 101.50, 3.00, 101.39},   // SGR03
            {4.75, 115.00, 4.86, 115.41},   // SWK01
            {4.40, 113.99, 4.18, 114.32},   // SWK02
            {3.17, 113.04, 2.70, 113.78},   // SWK03
            {2.30, 111.82, 2.90, 112.09, 2.01, 112.93, 2.10, 112.15},   // SWK04
            {2.13, 111.52, 2.17, 111.63, 2.52, 111.43},   // SWK05
            {1.24, 111.46, 1.41, 111.53, 1.74, 111.34},   // SWK06
            {1.46, 110.49, 1.17, 110.57, 1.38, 110.75},   // SWK07
            {1.55, 110.34, 1.42, 110.15, 1.67, 109.85},   // SWK08
            {},   // SWK09
            {5.33, 103.14, 5.21, 103.21},   // TRG01
            {5.74, 102.49, 5.52, 102.73},   // TRG02
            {5.07, 102.78},   // TRG03
            {4.76, 103.42, 4.23, 103.42},   // TRG04
            {3.14, 101.69, 2.93, 101.69},   // WLY01
            {5.28, 115.24},   // WLY02
    };

    private Zones() { }

    /** "JHR01" to "Zon 1": JAKIM numbers the zones within each state. */
    static String name(String code) {
        try {
            return "Zon " + Integer.parseInt(code.substring(3));
        } catch (RuntimeException e) {
            return code;
        }
    }

    static int indexOf(String code) {
        for (int i = 0; i < CODES.length; i++)
            if (CODES[i].equals(code)) return i;
        return -1;
    }

    /**
     * The zone with the nearest main town, or null outside Malaysia (more than about 150 km from every town).
     * Flat distance with longitude scaled by latitude: plenty at this size, and near zone borders the owner can
     * always pick by hand.
     */
    static String nearest(double latitude, double longitude) {
        double scale = Math.cos(Math.toRadians(latitude)), best = Double.MAX_VALUE;
        String zone = null;
        for (int i = 0; i < CODES.length; i++) {
            for (int t = 0; t + 1 < TOWNS[i].length; t += 2) {
                double dy = TOWNS[i][t] - latitude, dx = (TOWNS[i][t + 1] - longitude) * scale;
                double d = dy * dy + dx * dx;
                if (d < best) {
                    best = d;
                    zone = CODES[i];
                }
            }
        }
        return best <= 1.35 * 1.35 ? zone : null;   // 1.35 degrees, about 150 km
    }
}
