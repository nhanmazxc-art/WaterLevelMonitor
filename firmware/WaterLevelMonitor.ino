#include <WiFi.h>
#include <Wire.h>
#include <LiquidCrystal_I2C.h>
#include <ESP32Servo.h>
#include <Firebase_ESP_Client.h>
#include <addons/TokenHelper.h>

// ==================================================
// 1. WIFI
// ==================================================
#define WIFI_SSID       "Jangsu T4"
#define WIFI_PASSWORD   "1661234567"

// ==================================================
// 2. FIREBASE
// ==================================================
#define API_KEY         "AIzaSyB7JTa8VW0h3W8htFrSDncv7WU4jXf8rbc"
#define DATABASE_URL    "https://giam-sat-muc-nuoc-ac899-default-rtdb.asia-southeast1.firebasedatabase.app/"

#define USER_EMAIL      "tanh84553@gmail.com"
#define USER_PASSWORD   "123456"

// ==================================================
// 3. KHAI BAO CHAN GPIO
// ==================================================
#define TRIG_PIN       18
#define ECHO_PIN       19
#define SERVO_PIN      32
#define BUZZER_PIN     27
#define PUMP_PIN       25

#define SDA_PIN        21
#define SCL_PIN        22

// ==================================================
// 4. LCD, SERVO, FIREBASE
// ==================================================
LiquidCrystal_I2C lcd(0x27, 16, 2);

Servo servo;

FirebaseData fbdo;
FirebaseAuth auth;
FirebaseConfig config;

// ==================================================
// 5. CAU HINH MAC DINH
// ==================================================

// Muc nuoc = chieu cao cam bien - khoang cach HC-SR04
float CHIEU_CAO_CAM_BIEN = 30.0;

// Nguong canh bao
float NGUONG_CANH_BAO = 13.0;

// Nguong nguy hiem
float NGUONG_NGUY_HIEM = 16.0;

// Goc servo
const int GOC_MO = 180;
const int GOC_DONG = 0;

// ==================================================
// 6. THOI GIAN
// ==================================================

const unsigned long SENSOR_INTERVAL = 300;
const unsigned long LCD_INTERVAL = 250;

const unsigned long FIREBASE_READ_INTERVAL = 200;
const unsigned long FIREBASE_WRITE_INTERVAL = 1000;

// Coi canh bao: 3 giay
const unsigned long THOI_GIAN_COI = 3000;

// Khoang thoi gian nghi giua 2 lan coi keu
// O muc CANH BAO
const unsigned long THOI_GIAN_NGHI_COI = 3000;

// ==================================================
// 7. BIEN TRANG THAI
// ==================================================

String cheDo = "AUTO";

float khoangCachCm = -1;

int gocServo = GOC_MO;

bool servoState = true;
bool camBienOK = false;

bool canhBao = false;
bool coiState = false;
bool mayBomState = false;

bool firebaseDaKhoiTao = false;
bool daKhoiTaoDB = false;

// ==================================================
// 8. TIMER
// ==================================================

unsigned long lastSensor = 0;
unsigned long lastLCD = 0;
unsigned long lastFirebaseRead = 0;
unsigned long lastFirebaseWrite = 0;

// ==================================================
// 9. TRANG THAI CANH BAO
// ==================================================

enum MucCanhBao
{
  MUC_AN_TOAN,
  MUC_CANH_BAO,
  MUC_NGUY_HIEM
};

MucCanhBao mucCanhBaoHienTai = MUC_AN_TOAN;

// ==================================================
// 10. CANH BAO MUC CANH BAO
// ==================================================

// Coi muc CANH BAO:
// Keu 3 giay
// Tat 3 giay
// Keu lai
bool dangKeuCanhBao = false;
unsigned long mocCanhBao = 0;

// ==================================================
// 11. CANH BAO MUC NGUY HIEM
// ==================================================

// Khi vao NGUY HIEM:
// - Cua dong
// - Coi keu 3 giay
// - Sau 3 giay coi tat
// - May bom bat

bool dangCanhBaoNguyHiem = false;
bool daHet3GiayNguyHiem = false;

unsigned long mocBatDauNguyHiem = 0;

// ==================================================
// 12. TRANG THAI BOM HA MUC
// ==================================================

// Sau khi da kich hoat bom o muc nguy hiem,
// bom tiep tuc chay cho den khi muc nuoc ve AN TOAN.

bool dangBomHaMuc = false;

// ==================================================
// 13. DOC CAM BIEN HC-SR04
// ==================================================

float docMotLan()
{
  digitalWrite(TRIG_PIN, LOW);
  delayMicroseconds(3);

  digitalWrite(TRIG_PIN, HIGH);
  delayMicroseconds(10);

  digitalWrite(TRIG_PIN, LOW);

  unsigned long thoiGian = pulseIn(ECHO_PIN, HIGH, 30000);

  if (thoiGian == 0)
  {
    return -1;
  }

  float kc = thoiGian * 0.0343 / 2.0;

  if (kc < 2 || kc > 400)
  {
    return -1;
  }

  return kc;
}

// ==================================================
// 14. LAY TRUNG VI 3 LAN DO
// ==================================================

float docKhoangCach()
{
  float v[3];
  int n = 0;

  for (int i = 0; i < 3; i++)
  {
    float kc = docMotLan();

    if (kc > 0)
    {
      v[n++] = kc;
    }

    delay(15);
  }

  if (n < 2)
  {
    return -1;
  }

  // Sap xep tang dan
  for (int i = 0; i < n - 1; i++)
  {
    for (int j = i + 1; j < n; j++)
    {
      if (v[j] < v[i])
      {
        float t = v[i];
        v[i] = v[j];
        v[j] = t;
      }
    }
  }

  return v[n / 2];
}

// ==================================================
// 15. TINH MUC NUOC
// ==================================================

float layMucNuoc()
{
  if (!camBienOK)
  {
    return -1;
  }

  float mucNuoc = CHIEU_CAO_CAM_BIEN - khoangCachCm;

  if (mucNuoc < 0)
  {
    mucNuoc = 0;
  }

  return mucNuoc;
}

// ==================================================
// 16. DIEU KHIEN SERVO
// ==================================================

void datGocServo(int goc)
{
  goc = constrain(goc, 0, 180);

  if (goc != gocServo)
  {
    servo.write(goc);
    gocServo = goc;
  }

  servoState = (goc == GOC_MO);
}

// ==================================================
// 17. TAT COI
// ==================================================

void tatCoi()
{
  coiState = false;
  digitalWrite(BUZZER_PIN, LOW);
}

// ==================================================
// 18. BAT COI
// ==================================================

void batCoi()
{
  coiState = true;
  digitalWrite(BUZZER_PIN, HIGH);
}

// ==================================================
// 19. KHOI TAO FIREBASE
// ==================================================

void khoiTaoDuLieuFirebase()
{
  if (!Firebase.ready())
  {
    return;
  }

  bool ok = Firebase.RTDB.getFloat(
    &fbdo,
    "/mucnuoc/chieuCaoCamBien"
  );

  if (ok)
  {
    daKhoiTaoDB = true;
    return;
  }

  if (fbdo.dataType() == "null")
  {
    FirebaseJson json;

    json.set("cheDo", "AUTO");

    json.set(
      "chieuCaoCamBien",
      CHIEU_CAO_CAM_BIEN
    );

    json.set(
      "nguongCanhBao",
      NGUONG_CANH_BAO
    );

    json.set(
      "nguongNguyHiem",
      NGUONG_NGUY_HIEM
    );

    json.set("lenhMotor", false);
    json.set("lenhCoi", false);
    json.set("lenhMaybom", false);

    json.set("mayBom", false);
    json.set("coi", false);
    json.set("motor", true);

    if (Firebase.RTDB.updateNode(
          &fbdo,
          "/mucnuoc",
          &json))
    {
      daKhoiTaoDB = true;

      Serial.println(
        "Firebase: da tao du lieu mac dinh"
      );
    }
  }
}

// ==================================================
// 20. KHAI BAO TRUOC
// ==================================================

void guiFirebase();

// ==================================================
// 21. DOC LENH FIREBASE
// ==================================================

void docLenhFirebase()
{
  if (!Firebase.ready())
  {
    return;
  }

  if (!daKhoiTaoDB)
  {
    khoiTaoDuLieuFirebase();
    return;
  }

  // ----------------------------------------------
  // DOC CHE DO
  // ----------------------------------------------

  if (Firebase.RTDB.getString(
        &fbdo,
        "/mucnuoc/cheDo"))
  {
    String mode = fbdo.stringData();

    mode.trim();
    mode.toUpperCase();

    if (mode == "AUTO" || mode == "MANUAL")
    {
      cheDo = mode;
    }
  }

  // ----------------------------------------------
  // DOC CAU HINH
  // ----------------------------------------------

  float chieuCaoMoi = 0;
  float nguongCanhBaoMoi = 0;
  float nguongNguyHiemMoi = 0;

  bool docChieuCao =
    Firebase.RTDB.getFloat(
      &fbdo,
      "/mucnuoc/chieuCaoCamBien"
    );

  if (docChieuCao)
  {
    chieuCaoMoi = fbdo.floatData();
  }

  bool docCanhBao =
    Firebase.RTDB.getFloat(
      &fbdo,
      "/mucnuoc/nguongCanhBao"
    );

  if (docCanhBao)
  {
    nguongCanhBaoMoi = fbdo.floatData();
  }

  bool docNguyHiem =
    Firebase.RTDB.getFloat(
      &fbdo,
      "/mucnuoc/nguongNguyHiem"
    );

  if (docNguyHiem)
  {
    nguongNguyHiemMoi = fbdo.floatData();
  }

  // ----------------------------------------------
  // KIEM TRA CAU HINH
  // ----------------------------------------------

  if (
    docChieuCao &&
    docCanhBao &&
    docNguyHiem
  )
  {
    if (
      chieuCaoMoi > 0 &&
      chieuCaoMoi <= 400 &&

      nguongCanhBaoMoi > 0 &&

      nguongNguyHiemMoi >
      nguongCanhBaoMoi &&

      nguongNguyHiemMoi <=
      chieuCaoMoi
    )
    {
      CHIEU_CAO_CAM_BIEN =
        chieuCaoMoi;

      NGUONG_CANH_BAO =
        nguongCanhBaoMoi;

      NGUONG_NGUY_HIEM =
        nguongNguyHiemMoi;
    }
    else
    {
      Serial.println(
        "Cau hinh muc nuoc khong hop le"
      );
    }
  }

  // ==================================================
  // MANUAL
  // ==================================================

  if (cheDo == "MANUAL")
  {
    bool trangThaiThayDoi = false;

    // ----------------------------------------------
    // SERVO
    // ----------------------------------------------

    if (
      Firebase.RTDB.getBool(
        &fbdo,
        "/mucnuoc/lenhMotor"
      )
    )
    {
      bool lenh = fbdo.boolData();

      if (lenh != servoState)
      {
        datGocServo(
          lenh ? GOC_MO : GOC_DONG
        );

        trangThaiThayDoi = true;
      }
    }

    // ----------------------------------------------
    // COI
    // ----------------------------------------------

    if (
      Firebase.RTDB.getBool(
        &fbdo,
        "/mucnuoc/lenhCoi"
      )
    )
    {
      bool lenhCoiMoi = fbdo.boolData();

      if (lenhCoiMoi != coiState)
      {
        coiState = lenhCoiMoi;

        digitalWrite(
          BUZZER_PIN,
          coiState ? HIGH : LOW
        );

        trangThaiThayDoi = true;
      }
    }

    // ----------------------------------------------
    // MAY BOM
    // ----------------------------------------------

    if (
      Firebase.RTDB.getBool(
        &fbdo,
        "/mucnuoc/lenhMaybom"
      )
    )
    {
      bool lenhBom = fbdo.boolData();

      if (lenhBom != mayBomState)
      {
        mayBomState = lenhBom;

        digitalWrite(
          PUMP_PIN,
          mayBomState ? HIGH : LOW
        );

        trangThaiThayDoi = true;
      }
    }

    // ----------------------------------------------
    // GUI LAI TRANG THAI
    // ----------------------------------------------

    if (trangThaiThayDoi)
    {
      guiFirebase();
    }
  }
}

// ==================================================
// 22. DIEU KHIEN CUA TU DONG
// ==================================================

void dieuKhienAuto()
{
  if (!camBienOK)
  {
    return;
  }

  float mucNuocCm = layMucNuoc();

  // ----------------------------------------------
  // AN TOAN
  // Cua MO
  // ----------------------------------------------

  if (mucNuocCm < NGUONG_CANH_BAO)
  {
    datGocServo(GOC_MO);
  }

  // ----------------------------------------------
  // CANH BAO
  // Cua van MO
  // ----------------------------------------------

  else if (
    mucNuocCm >= NGUONG_CANH_BAO &&
    mucNuocCm < NGUONG_NGUY_HIEM
  )
  {
    datGocServo(GOC_MO);
  }

  // ----------------------------------------------
  // NGUY HIEM
  // Cua DONG
  // ----------------------------------------------

  else
  {
    datGocServo(GOC_DONG);
  }
}

// ==================================================
// 23. DIEU KHIEN MAY BOM TU DONG
// ==================================================

void dieuKhienMayBomAuto()
{
  // Mat cam bien
  // Tat bom de an toan
  if (!camBienOK)
  {
    mayBomState = false;
    dangBomHaMuc = false;

    digitalWrite(PUMP_PIN, LOW);

    return;
  }

  float mucNuocCm = layMucNuoc();

  // ==================================================
  // MUC AN TOAN
  // ==================================================

  if (mucNuocCm < NGUONG_CANH_BAO)
  {
    // Da ve muc an toan
    // Tat bom
    mayBomState = false;
    dangBomHaMuc = false;

    digitalWrite(PUMP_PIN, LOW);

    return;
  }

  // ==================================================
  // MUC CANH BAO
  // ==================================================

  if (
    mucNuocCm >= NGUONG_CANH_BAO &&
    mucNuocCm < NGUONG_NGUY_HIEM
  )
  {
    // Neu chua kich hoat bom
    // thi van tat
    if (!dangBomHaMuc)
    {
      mayBomState = false;

      digitalWrite(PUMP_PIN, LOW);
    }

    return;
  }

  // ==================================================
  // MUC NGUY HIEM
  // ==================================================

  if (mucNuocCm >= NGUONG_NGUY_HIEM)
  {
    // Chua het 3 giay coi
    if (!daHet3GiayNguyHiem)
    {
      mayBomState = false;

      digitalWrite(PUMP_PIN, LOW);

      return;
    }

    // Da het 3 giay coi
    // Bat bom
    dangBomHaMuc = true;

    mayBomState = true;

    digitalWrite(PUMP_PIN, HIGH);
  }
}

// ==================================================
// 24. XU LY CANH BAO
// ==================================================

void xuLyCanhBao()
{
  // ----------------------------------------------
  // Neu cam bien loi
  // ----------------------------------------------

  if (!camBienOK)
  {
    canhBao = false;

    tatCoi();

    mucCanhBaoHienTai =
      MUC_AN_TOAN;

    dangKeuCanhBao = false;

    dangCanhBaoNguyHiem = false;
    daHet3GiayNguyHiem = false;

    return;
  }

  float mucNuocCm = layMucNuoc();

  // Cap nhat co canh bao hay khong
  canhBao =
    (mucNuocCm >= NGUONG_CANH_BAO);

  // ==================================================
  // MANUAL
  // ==================================================

  if (cheDo != "AUTO")
  {
    return;
  }

  // ==================================================
  // XAC DINH MUC HIEN TAI
  // ==================================================

  MucCanhBao mucMoi = MUC_AN_TOAN;

  if (
    mucNuocCm >= NGUONG_NGUY_HIEM
  )
  {
    mucMoi = MUC_NGUY_HIEM;
  }

  else if (
    mucNuocCm >= NGUONG_CANH_BAO
  )
  {
    mucMoi = MUC_CANH_BAO;
  }

  // ==================================================
  // KHI CHUYEN MUC
  // ==================================================

  if (mucMoi != mucCanhBaoHienTai)
  {
    mucCanhBaoHienTai = mucMoi;

    // Reset coi
    tatCoi();

    dangKeuCanhBao = false;

    // ----------------------------------------------
    // NEU VAO NGUY HIEM
    // ----------------------------------------------

    if (mucMoi == MUC_NGUY_HIEM)
    {
      dangCanhBaoNguyHiem = true;

      daHet3GiayNguyHiem = false;

      mocBatDauNguyHiem =
        millis();

      // Coi bat ngay
      batCoi();

      Serial.println(
        "!!! NGUY HIEM - BAT COI 3 GIAY !!!"
      );
    }

    // ----------------------------------------------
    // NEU KHONG CON NGUY HIEM
    // ----------------------------------------------

    else
    {
      dangCanhBaoNguyHiem = false;

      daHet3GiayNguyHiem = false;
    }
  }

  unsigned long now = millis();

  // ==================================================
  // MUC AN TOAN
  // ==================================================

  if (
    mucCanhBaoHienTai ==
    MUC_AN_TOAN
  )
  {
    tatCoi();

    dangKeuCanhBao = false;

    dangCanhBaoNguyHiem = false;

    daHet3GiayNguyHiem = false;

    return;
  }

  // ==================================================
  // MUC NGUY HIEM
  // ==================================================

  if (
    mucCanhBaoHienTai ==
    MUC_NGUY_HIEM
  )
  {
    // Cua se duoc dong boi dieuKhienAuto()

    // ----------------------------------------------
    // 3 GIAY DAU: COI KEU
    // ----------------------------------------------

    if (!daHet3GiayNguyHiem)
    {
      batCoi();

      if (
        now - mocBatDauNguyHiem
        >= THOI_GIAN_COI
      )
      {
        // Het 3 giay
        daHet3GiayNguyHiem = true;

        tatCoi();

        Serial.println(
          "Nguy hiem: het 3 giay coi -> BAT BOM"
        );
      }

      return;
    }

    // ----------------------------------------------
    // SAU 3 GIAY
    // COI TAT
    // BOM DUOC DIEU KHIEN O HAM KHAC
    // ----------------------------------------------

    tatCoi();

    return;
  }

  // ==================================================
  // MUC CANH BAO
  // ==================================================

  if (
    mucCanhBaoHienTai ==
    MUC_CANH_BAO
  )
  {
    // ----------------------------------------------
    // Neu dang keu
    // ----------------------------------------------

    if (dangKeuCanhBao)
    {
      if (
        now - mocCanhBao
        >= THOI_GIAN_COI
      )
      {
        // Het 3 giay
        tatCoi();

        dangKeuCanhBao = false;

        // Bat dau thoi gian nghi
        mocCanhBao =
          now + THOI_GIAN_NGHI_COI;

        Serial.println(
          "Canh bao: het 3 giay coi"
        );
      }

      return;
    }

    // ----------------------------------------------
    // Neu dang nghi
    // ----------------------------------------------

    if (
      (long)(now - mocCanhBao) < 0
    )
    {
      tatCoi();
      return;
    }

    // ----------------------------------------------
    // Het thoi gian nghi
    // Bat dau keu lai
    // ----------------------------------------------

    dangKeuCanhBao = true;

    batCoi();

    mocCanhBao = now;

    Serial.println(
      "Canh bao: bat coi 3 giay"
    );
  }
}

// ==================================================
// 25. HIEN THI LCD
// ==================================================

void capNhatLCD()
{
  float mucNuocCm = layMucNuoc();

  // ==================================================
  // DONG 1: MUC NUOC
  // ==================================================

  lcd.setCursor(0, 0);

  if (!camBienOK)
  {
    lcd.print("MN: LOI CAM BIEN");
  }
  else
  {
    char dong1[17];

    snprintf(
      dong1,
      sizeof(dong1),
      "MN:%5.1fcm     ",
      mucNuocCm
    );

    lcd.print(dong1);
  }

  // ==================================================
  // DONG 2
  // ==================================================

  lcd.setCursor(0, 1);

  if (!camBienOK)
  {
    lcd.print("CAM BIEN LOI   ");
    return;
  }

  // ----------------------------------------------
  // AN TOAN
  // ----------------------------------------------

  if (
    mucNuocCm <
    NGUONG_CANH_BAO
  )
  {
    lcd.print("MO B:OFF C:OFF ");
  }

  // ----------------------------------------------
  // CANH BAO
  // ----------------------------------------------

  else if (
    mucNuocCm <
    NGUONG_NGUY_HIEM
  )
  {
    if (coiState)
    {
      lcd.print("MO B:OFF C:ON  ");
    }
    else
    {
      lcd.print("MO B:OFF C:OFF ");
    }
  }

  // ----------------------------------------------
  // NGUY HIEM
  // ----------------------------------------------

  else
  {
    if (mayBomState)
    {
      lcd.print("DONG B:ON C:OFF");
    }
    else if (coiState)
    {
      lcd.print("DONG B:OFF C:ON");
    }
    else
    {
      lcd.print("DONG B:OFF C:OFF");
    }
  }
}

// ==================================================
// 26. GUI TRANG THAI FIREBASE
// ==================================================

void guiFirebase()
{
  if (
    !Firebase.ready() ||
    !daKhoiTaoDB
  )
  {
    return;
  }

  FirebaseJson json;

  float mucNuocCm = layMucNuoc();

  // ----------------------------------------------
  // DU LIEU CAM BIEN
  // ----------------------------------------------

  json.set(
    "khoangCachCm",
    khoangCachCm
  );

  json.set(
    "camBienOK",
    camBienOK
  );

  json.set(
    "mucNuocCm",
    camBienOK ? mucNuocCm : -1
  );

  // ----------------------------------------------
  // SERVO
  // ----------------------------------------------

  json.set(
    "motor",
    servoState
  );

  json.set(
    "gocServo",
    gocServo
  );

  // ----------------------------------------------
  // BOM
  // ----------------------------------------------

  json.set(
    "mayBom",
    mayBomState
  );

  // ----------------------------------------------
  // COI
  // ----------------------------------------------

  json.set(
    "coi",
    coiState
  );

  json.set(
    "canhBao",
    canhBao
  );

  // ----------------------------------------------
  // WIFI
  // ----------------------------------------------

  json.set(
    "wifiRSSI",
    WiFi.RSSI()
  );

  json.set(
    "thoiGianChayMs",
    millis()
  );

  // ----------------------------------------------
  // TRANG THAI MUC NUOC
  // ----------------------------------------------

  if (!camBienOK)
  {
    json.set(
      "mucNuoc",
      "LOI CAM BIEN"
    );
  }

  else if (
    mucNuocCm >=
    NGUONG_NGUY_HIEM
  )
  {
    json.set(
      "mucNuoc",
      "NGUY HIEM"
    );
  }

  else if (
    mucNuocCm >=
    NGUONG_CANH_BAO
  )
  {
    json.set(
      "mucNuoc",
      "CANH BAO"
    );
  }

  else
  {
    json.set(
      "mucNuoc",
      "AN TOAN"
    );
  }

  // ----------------------------------------------
  // GUI FIREBASE
  // ----------------------------------------------

  if (
    Firebase.RTDB.updateNode(
      &fbdo,
      "/mucnuoc",
      &json
    )
  )
  {
    Serial.println(
      "Firebase: GUI THANH CONG"
    );
  }

  else
  {
    Serial.print(
      "Firebase loi: "
    );

    Serial.println(
      fbdo.errorReason()
    );
  }
}

// ==================================================
// 27. SETUP
// ==================================================

void setup()
{
  Serial.begin(115200);

  // ----------------------------------------------
  // GPIO
  // ----------------------------------------------

  pinMode(TRIG_PIN, OUTPUT);
  pinMode(ECHO_PIN, INPUT);

  pinMode(BUZZER_PIN, OUTPUT);
  pinMode(PUMP_PIN, OUTPUT);

  digitalWrite(
    TRIG_PIN,
    LOW
  );

  digitalWrite(
    BUZZER_PIN,
    LOW
  );

  digitalWrite(
    PUMP_PIN,
    LOW
  );

  // ----------------------------------------------
  // I2C
  // ----------------------------------------------

  Wire.begin(
    SDA_PIN,
    SCL_PIN
  );

  // ----------------------------------------------
  // LCD
  // ----------------------------------------------

  lcd.init();
  lcd.backlight();
  lcd.clear();

  lcd.setCursor(0, 0);
  lcd.print("WATER MONITOR");

  lcd.setCursor(0, 1);
  lcd.print("Khoi dong...");

  // ----------------------------------------------
  // SERVO
  // ----------------------------------------------

  servo.setPeriodHertz(50);

  servo.attach(
    SERVO_PIN,
    500,
    2400
  );

  // KHOI DONG CUA O TRANG THAI MO
  servo.write(GOC_MO);

  gocServo = GOC_MO;

  servoState = true;

  // ----------------------------------------------
  // WIFI
  // ----------------------------------------------

  WiFi.mode(WIFI_STA);

  WiFi.begin(
    WIFI_SSID,
    WIFI_PASSWORD
  );

  Serial.print(
    "Dang ket noi WiFi"
  );

  unsigned long batDau =
    millis();

  while (
    WiFi.status() != WL_CONNECTED &&
    millis() - batDau < 20000
  )
  {
    delay(300);

    Serial.print(".");
  }

  Serial.println();

  if (
    WiFi.status() ==
    WL_CONNECTED
  )
  {
    Serial.print(
      "IP ESP32: "
    );

    Serial.println(
      WiFi.localIP()
    );
  }
  else
  {
    Serial.println(
      "WiFi chua ket noi"
    );
  }

  // ----------------------------------------------
  // FIREBASE
  // ----------------------------------------------

  config.api_key =
    API_KEY;

  config.database_url =
    DATABASE_URL;

  config.token_status_callback =
    tokenStatusCallback;

  auth.user.email =
    USER_EMAIL;

  auth.user.password =
    USER_PASSWORD;

  Firebase.begin(
    &config,
    &auth
  );

  Firebase.reconnectWiFi(
    true
  );

  firebaseDaKhoiTao = true;

  // ----------------------------------------------
  // LCD
  // ----------------------------------------------

  lcd.clear();

  lcd.setCursor(0, 0);
  lcd.print("MN:0.0cm");

  lcd.setCursor(0, 1);
  lcd.print("MO B:OFF C:OFF");
}

// ==================================================
// 28. LOOP
// ==================================================

void loop()
{
  unsigned long now =
    millis();

  // ==================================================
  // DOC CAM BIEN
  // ==================================================

  if (
    now - lastSensor >=
    SENSOR_INTERVAL
  )
  {
    lastSensor = now;

    khoangCachCm =
      docKhoangCach();

    camBienOK =
      (khoangCachCm >= 2);

    Serial.print(
      "Khoang cach: "
    );

    if (camBienOK)
    {
      Serial.print(
        khoangCachCm
      );

      Serial.print(" cm");

      Serial.print(
        " | Muc nuoc: "
      );

      Serial.print(
        layMucNuoc()
      );

      Serial.println(
        " cm"
      );
    }
    else
    {
      Serial.println(
        "Khong co du lieu hop le"
      );
    }
  }

  // ==================================================
  // XU LY CANH BAO
  // ==================================================

  xuLyCanhBao();

  // ==================================================
  // CHE DO AUTO
  // ==================================================

  if (cheDo == "AUTO")
  {
    // Dieu khien cua
    dieuKhienAuto();

    // Dieu khien bom
    dieuKhienMayBomAuto();
  }

  // ==================================================
  // LCD
  // ==================================================

  if (
    now - lastLCD >=
    LCD_INTERVAL
  )
  {
    lastLCD = now;

    capNhatLCD();
  }

  // ==================================================
  // DOC LENH FIREBASE
  // ==================================================

  if (
    firebaseDaKhoiTao &&
    now - lastFirebaseRead >=
    FIREBASE_READ_INTERVAL
  )
  {
    lastFirebaseRead = now;

    if (
      WiFi.status() ==
      WL_CONNECTED
    )
    {
      docLenhFirebase();
    }
  }

  // ==================================================
  // GUI TRANG THAI FIREBASE
  // ==================================================

  if (
    firebaseDaKhoiTao &&
    now - lastFirebaseWrite >=
    FIREBASE_WRITE_INTERVAL
  )
  {
    lastFirebaseWrite = now;

    if (
      WiFi.status() ==
      WL_CONNECTED
    )
    {
      guiFirebase();
    }
  }

  delay(10);
}
