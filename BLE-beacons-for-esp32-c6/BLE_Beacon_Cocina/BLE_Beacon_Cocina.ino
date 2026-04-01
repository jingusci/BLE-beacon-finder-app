#include <NimBLEDevice.h>

void setup() {
  NimBLEDevice::init("BLE Beacon - Cocina");

  NimBLEAdvertising *pAdvertising = NimBLEDevice::getAdvertising();

  std::string beaconData;
  beaconData.reserve(25);

  // Apple ID
  beaconData += (char)0x4C;
  beaconData += (char)0x00;

  // iBeacon type
  beaconData += (char)0x02;
  beaconData += (char)0x15;

  // UUID
  uint8_t uuid[16] = {
    0xB9,0x40,0x7F,0x30,0xF5,0xF8,0x46,0x6E,
    0xAF,0xF9,0x25,0x55,0x6B,0x57,0xFE,0x6D
  };

  for (int i = 0; i < 16; i++) {
    beaconData += (char)uuid[i];
  }

  // Major
  beaconData += (char)0x00;
  beaconData += (char)0x01;

  // Minor
  beaconData += (char)0x00;
  beaconData += (char)0x02;

  // TX power
  beaconData += (char)0xC5;

  NimBLEAdvertisementData advData;
  advData.setFlags(0x06);
  advData.setManufacturerData(beaconData);

  pAdvertising->setAdvertisementData(advData);
  pAdvertising->setMinInterval(32);
  pAdvertising->setMaxInterval(64);

  pAdvertising->start();
}

void loop() {}