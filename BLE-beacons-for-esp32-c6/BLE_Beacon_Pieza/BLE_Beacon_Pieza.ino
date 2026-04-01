#include <NimBLEDevice.h>

void setup() {
  NimBLEDevice::init("BLE Beacon - Pieza");

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
    0xA1,0xB2,0xC3,0xD4,0xE5,0xF6,0x47,0x89,
    0xAB,0xCD,0x12,0x34,0x56,0x78,0x90,0xAB
  };

  for (int i = 0; i < 16; i++) {
    beaconData += (char)uuid[i];
  }

  // Major
  beaconData += (char)0x00;
  beaconData += (char)0x01;

  // Minor
  beaconData += (char)0x00;
  beaconData += (char)0x01;

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