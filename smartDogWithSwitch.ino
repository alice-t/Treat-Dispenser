#include <SPI.h>
#include <Servo.h>
#include "Adafruit_BLE_UART.h"
#include <EEPROM.h>

// Bluetooth params

#define ADAFRUITBLE_REQ 10
#define ADAFRUITBLE_RDY 2  
#define ADAFRUITBLE_RST 9

Adafruit_BLE_UART BTLEserial = Adafruit_BLE_UART(ADAFRUITBLE_REQ, ADAFRUITBLE_RDY, ADAFRUITBLE_RST);
aci_evt_opcode_t bleStatus;
aci_evt_opcode_t laststatus = ACI_EVT_DISCONNECTED;

// Settings for switch
const int switchPin = 4;
int switchState = 0;
int longPressDelay;

// Settings for button dictating end of dispensing a treat
const int turnPin = 6;
const int maxTimeout = 3000;

// Settings for buzzer
const int buzzerPin = 7;
int treatFreq = 500;
int noTreatFreq = 160;
int errorFreq = 350;

// Audio settings
boolean notifyTreat = true;
boolean notifyLastTreat = false;
boolean notifyNoTreat = true;

// Treat parameters
int remainingTreats;
int dispenserCapacity; // 5, 7, 9, 11

// EEPROM Addresses
const int dispenserCapacityAddress = 0;
const int notifyTreatAddress = 2;
const int notifyLastTreatAddress = 4;
const int notifyNoTreatAddress = 6;
const int longPressDelayAddress = 10;

// Servo motor parameters
Servo myServo;  // create servo object to control a servo
int servoPin = 5;

// *** IMPORTANT: comment out unless connected to a PC and using serial monitor with the Arduino IDE ***
//#define DEBUG // print log messages to serial monitor 

/**************************************************************************/
/*!
    Configure the Arduino and start advertising with the radio
*/
/**************************************************************************/
void setup(void)
{ 
  #ifdef DEBUG
    Serial.begin(9600);
    while(!Serial); // Leonardo/Micro should wait for serial init
    Serial.println(F("Smart Dog Treat Dispenser"));
  #endif
  // Do once - write initial EEPROM values
 //EEPROMWriteInt(dispenserCapacityAddress, 7);
// EEPROMWriteInt(notifyTreatAddress, 1);
// EEPROMWriteInt(notifyLastTreatAddress, 1);
 //EEPROMWriteInt(notifyNoTreatAddress, 1);
 //EEPROMWriteInt(longPressDelayAddress, 1500);

  // read values from EEPROM

  dispenserCapacity = EEPROMReadInt(dispenserCapacityAddress);
  longPressDelay = EEPROMReadInt(longPressDelayAddress);
  int eInt = EEPROMReadInt(notifyTreatAddress);
  if (eInt == 1)
      notifyTreat = true;
  else
      notifyTreat = false;
  eInt = EEPROMReadInt(notifyLastTreatAddress);
  if (eInt == 1)
      notifyLastTreat = true;
  else
      notifyLastTreat = false;
  eInt = EEPROMReadInt(notifyNoTreatAddress);
  if (eInt == 1)
      notifyNoTreat = true;
  else
      notifyNoTreat = false;

  #ifdef DEBUG
      Serial.println(String(dispenserCapacity) + ", " + String(notifyTreat) + ", " 
      + String(notifyLastTreat) + ", " + String(notifyNoTreat) + ", " + String(longPressDelay));
  #endif
  
  // setup bluetooth
  BTLEserial.setDeviceName("SDOG-TD"); /* 7 characters max! */
  BTLEserial.begin();
  
 // setup switch, reload button and buzzer
  pinMode(switchPin, INPUT);
  pinMode(turnPin, INPUT);
  pinMode(buzzerPin, OUTPUT);
}

/**************************************************************************/
/*!
    Constantly checks for new events on the nRF8001
*/
/**************************************************************************/


void loop()
{
  
  // Bluetooth processing
  BTLEserial.pollACI();

  bleStatus = BTLEserial.getState(); // Check current status
  // If status changed...
  if (bleStatus != laststatus) 
  {
      #ifdef DEBUG
        if (bleStatus == ACI_EVT_DEVICE_STARTED) 
        {
            Serial.println(F("* Advertising started"));
        }
        if (bleStatus == ACI_EVT_CONNECTED) 
        {
            Serial.println(F("* Connected!"));
        }
        if (bleStatus == ACI_EVT_DISCONNECTED) 
        {
            Serial.println(F("* Disconnected or advertising timed out"));
        }
      #endif
      laststatus = bleStatus;
    }
    if (bleStatus == ACI_EVT_CONNECTED) {
        // if connected, check for data
        if (BTLEserial.available()) 
        {
            #ifdef DEBUG
              Serial.print("* RX ");
              Serial.print(BTLEserial.available());
              Serial.print(" bytes: ");
            #endif
            int inputChars = BTLEserial.available();
            // While we still have something to read, read a charcter and add to inputString
            String inputString = "";
            while (BTLEserial.available()) 
            {
              inputString += char(BTLEserial.read());
            }
            #ifdef DEBUG
              Serial.println(inputString);
            #endif
            if (inputString.equals("treat")) 
            {
                giveTreat();
            }
            else if (inputString.equals("battery")) 
            {
                sendBatteryVoltage();   
            }
            else if (inputString.equals("remaining")) 
            {
                sendData("Treats=" + String(remainingTreats));
            }
            else if (inputString.equals("reload")) 
            {
                reload();
            }
            else if (inputString.equals("capacity")) 
            {
                sendData("Capacity=" + String(dispenserCapacity));
            }
            else if (inputString.equals("longPressDelay")) 
            {
                sendData("LongPressDelay=" + String(longPressDelay));
            }
            else if (inputString.startsWith("Capacity="))
            {
                String capacityStr = inputString.substring(9);
                dispenserCapacity = capacityStr.toInt();
                if (remainingTreats > dispenserCapacity)
                {
                  remainingTreats = dispenserCapacity;
                }
                EEPROMWriteInt(dispenserCapacityAddress, dispenserCapacity);
                sendData("Set Capacity");
            }
            else if (inputString.startsWith("LongPressDelay="))
            {
                String delayStr = inputString.substring(15);
                longPressDelay = delayStr.toInt();
                EEPROMWriteInt(longPressDelayAddress, longPressDelay);
                sendData("Set LongPressDelay");
            }
            else if (inputString.equals("notifyTreat")) 
            {
                sendData("NotifyTreat=" + String(notifyTreat));
            }
            else if (inputString.equals("notifyLastTreat")) 
            {
                sendData("NotifyLast=" + String(notifyLastTreat));
            }
            else if (inputString.equals("notifyNoTreat")) 
            {
                sendData("NotifyNoTreat=" + String(notifyNoTreat));
            }
            else if (inputString.startsWith("NotifyTreat="))
            {
                String notifyTreatStr = inputString.substring(12);
                if (!notifyTreat && notifyTreatStr.equals("true")) {
                    notifyTreat = true;
                    EEPROMWriteInt(notifyTreatAddress, 1);
                }
                else if (notifyTreat && notifyTreatStr.equals("false")) {
                    notifyTreat = false;
                    EEPROMWriteInt(notifyTreatAddress, 0);
                }
                sendData("Set NotifyTreat");
            }
            else if (inputString.startsWith("NotifyLast="))
            {
                String notifyLastTreatStr = inputString.substring(11);
                if (!notifyLastTreat && notifyLastTreatStr.equals("true")) {
                    notifyLastTreat = true;
                    EEPROMWriteInt(notifyLastTreatAddress, 1);
                }
                else if (notifyLastTreat && notifyLastTreatStr.equals("false")) {
                    notifyLastTreat = false;
                    EEPROMWriteInt(notifyLastTreatAddress, 0);
                }
                sendData("Set NotifyLast");
            }
            else if (inputString.startsWith("NotifyNoTreat="))
            {
                String notifyNoTreatStr = inputString.substring(14);
                if (!notifyNoTreat && notifyNoTreatStr.equals("true")) {
                    notifyNoTreat = true;
                    EEPROMWriteInt(notifyNoTreatAddress, 1);
                }
                else if (notifyNoTreat && notifyNoTreatStr.equals("false")) {
                    notifyNoTreat = false;
                    EEPROMWriteInt(notifyNoTreatAddress, 0);
                }
                sendData("Set NotifyNoTreat");
            }
            else
            {
                sendData("Unrecognised input");
                #ifdef DEBUG
                  Serial.print("Unrecognised input=");
                  Serial.println(inputString);
                #endif
            }
        }
    }

    // Check if switch pressed

    switchState = digitalRead(switchPin);
    if (switchState == HIGH) {
        unsigned long longPressTime = millis() + longPressDelay;
        do {
            delay(100);
            switchState = digitalRead(switchPin);
            if (switchState == LOW) {
              giveTreat();
              #ifdef DEBUG
                Serial.print("remainingTreats=");
                Serial.println(String(remainingTreats));
              #endif
              break;
            }
        } while (longPressTime > millis());
        if (switchState == HIGH) {
            // long press, so reload
            #ifdef DEBUG
                Serial.println("long press - reload");
            #endif
            reload();
        }
        delay(300); // add delay to prevent accidental double press
    } 
    
    // Check if reload button pressed
/*    reloadState = digitalRead(reloadPin);
    if (reloadState == HIGH) {
        // stop rotation with  myServo.write(90);
        //reload();
        //delay(300); // add delay to prevent accidental double press
    }
  */
}

void sendData(String outputStr)
{
    uint8_t sendbuf[20];
    int strlen = outputStr.length() + 1;
    // check buffer size not exceeded
    if (strlen > 20)
      strlen = 20;
    outputStr.getBytes(sendbuf, strlen);
    BTLEserial.write(sendbuf, strlen);
    #ifdef DEBUG
      Serial.print("* TX ");
      Serial.print(strlen);
      Serial.print(" bytes: ");
      Serial.println(outputStr);
    #endif
}

void giveTreat()
{
    boolean treatDispensed = false;
    if (remainingTreats > 0)
    {
        // Dispense treat
        myServo.attach(servoPin);
        myServo.write(105);
    bool error = true;
    long timeout = millis() + maxTimeout;
    while(millis()<timeout){
      if(digitalRead(6) == LOW){
       break;
      }
    }
    while(millis()<timeout){
      if(digitalRead(6) == HIGH){
      error = false;
      break;  
      }

      
    }
        myServo.write(90);
        myServo.detach();
    if(error){
      tone(buzzerPin,errorFreq,100);
      delay(100);
      tone(buzzerPin,errorFreq-50,100);
      delay(200);
      tone(buzzerPin,errorFreq,100);
      delay(100);
      tone(buzzerPin,errorFreq-50,100);
    } else {
      treatDispensed = true;
      remainingTreats -= 1;
      // sound buzzer   
      if (remainingTreats == 0 && notifyLastTreat) {
        // sound last treat buzzer tones
        tone(buzzerPin, treatFreq, 250); 
        delay(500);
        tone(buzzerPin, treatFreq, 250);   
      }
      else if (notifyTreat) {
        // sound treat dispensed buzzer tones  
        tone(buzzerPin, treatFreq, 400);
      }
    }
    }
    else if (notifyNoTreat) 
    {
      // sound no treat buzzer tones 
      tone(buzzerPin, noTreatFreq, 500);
    }
    if (bleStatus == ACI_EVT_CONNECTED) {
        if (treatDispensed) {
            sendData("Treat Given");
            delay(200);
            sendData("Treats=" + String(remainingTreats));
        }
        else
           sendData("No Treats");
       delay(200);
       sendBatteryVoltage();
    }    
}

void reload()
{
      remainingTreats = dispenserCapacity;
      // sound reload buzzer tones
      tone(buzzerPin, treatFreq, 250); 
      delay(400);
      tone(buzzerPin, treatFreq, 250);
      delay(400);
      tone(buzzerPin, treatFreq, 250);
      #ifdef DEBUG
          Serial.print("remainingTreats=");
          Serial.println(String(remainingTreats));
      #endif
      if (bleStatus == ACI_EVT_CONNECTED) {
        sendData("Treats=" + String(remainingTreats));
      }
}

void sendBatteryVoltage()
{   
    double voltage = double( readVcc() ) / 1000;
    String outputStr = "Voltage=" + String(voltage) + "v";
    sendData(outputStr);
}

long readVcc() {
      long result;
      // Read 1.1V reference against AVcc
      //ADMUX = _BV(REFS0) | _BV(MUX3) | _BV(MUX2) | _BV(MUX1);
      #if defined(__AVR_ATmega32U4__) || defined(__AVR_ATmega1280__) || defined(__AVR_ATmega2560__)
        ADMUX = _BV(REFS0) | _BV(MUX4) | _BV(MUX3) | _BV(MUX2) | _BV(MUX1);
      #elif defined (__AVR_ATtiny24__) || defined(__AVR_ATtiny44__) || defined(__AVR_ATtiny84__)
        ADMUX = _BV(MUX5) | _BV(MUX0);
      #elif defined (__AVR_ATtiny25__) || defined(__AVR_ATtiny45__) || defined(__AVR_ATtiny85__)
        ADMUX = _BV(MUX3) | _BV(MUX2);
      #else
        ADMUX = _BV(REFS0) | _BV(MUX3) | _BV(MUX2) | _BV(MUX1);
      #endif 
      #if defined(__AVR_ATmega2560__)
        ADCSRB &= ~_BV(MUX5); // Without this the function always returns -1 on the ATmega2560
      #endif
      delay(2); // Wait for Vref to settle
      ADCSRA |= _BV(ADSC); // Convert
      while (bit_is_set(ADCSRA, ADSC));
      result = ADCL;
      result |= ADCH << 8;
      result = 1126400L / result; // Back-calculate AVcc in mV
      return result;
}

void EEPROMWriteInt(int address, int value)
{
    byte two = (value & 0xFF);
    byte one = ((value >> 8) & 0xFF);
    
    EEPROM.update(address, two);
    EEPROM.update(address + 1, one);
}

int EEPROMReadInt(int address)
{
    long two = EEPROM.read(address);
    long one = EEPROM.read(address + 1);
  
    return ((two << 0) & 0xFFFFFF) + ((one << 8) & 0xFFFFFFFF);
}
