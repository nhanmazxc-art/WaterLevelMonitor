/*
 * Chèn các thay đổi dưới đây vào sketch ESP32 hiện tại.
 * GPIO 25 chỉ điều khiển INPUT của relay; máy bơm phải có nguồn riêng.
 * Relay board có thể active-low: nếu vậy đổi HIGH/LOW trong datMayBom().
 */

// 1) Thêm gần các #define GPIO khác
#define PUMP_PIN 25

// 2) Thêm gần các biến trạng thái
bool mayBomState = false;

// 3) Thêm hàm này trước docLenhFirebase()
void datMayBom(bool bat) {
  mayBomState = bat;
  digitalWrite(PUMP_PIN, bat ? HIGH : LOW);
}

// 4) Trong setup(), thêm cùng nhóm pinMode/digitalWrite
pinMode(PUMP_PIN, OUTPUT);
datMayBom(false);

// 5) Trong khoiTaoDuLieuFirebase(), thêm vào FirebaseJson json
json.set("lenhMaybom", false);
json.set("mayBom", false);

// 6) Trong khối if (cheDo == "MANUAL") của docLenhFirebase(), thêm:
if (Firebase.RTDB.getBool(&fbdo, "/mucnuoc/lenhMaybom")) {
  bool lenhMayBomMoi = fbdo.boolData();
  if (lenhMayBomMoi != mayBomState) {
    datMayBom(lenhMayBomMoi);
    trangThaiThayDoi = true;
  }
}

// 7) Trong dieuKhienAuto(), sau phần điều khiển servo, thêm:
// Bơm chỉ chạy khi cảm biến hợp lệ và hệ thống đang ở ngưỡng NGUY HIỂM.
datMayBom(camBienOK && mucNuocCm >= NGUONG_NGUY_HIEM);

// 8) Trong guiFirebase(), thêm cùng các trạng thái thiết bị:
json.set("mayBom", mayBomState);
