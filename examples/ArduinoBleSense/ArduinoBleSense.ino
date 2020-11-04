#include <PDM.h>
#include <string.h>

/*
 * QRTone header
 */
#include "qrtone.h"


// QRTone instance
qrtone_t* qrtone = NULL;

#define SAMPLE_RATE 41667

// audio circular buffer size
#define MAX_AUDIO_WINDOW_SIZE 512
#define LEDR        (22u)
#define LEDG        (23u)
#define LEDB        (24u)

// buffer to read samples into, each sample is 16-bits
short sampleBuffer[256];

// Audio circular buffer provided to QRTone.
static int64_t scaled_input_buffer_feed_cursor = 0;
static int64_t scaled_input_buffer_consume_cursor = 0;
static float scaled_input_buffer[MAX_AUDIO_WINDOW_SIZE];

// number of samples read
volatile int samplesRead;

void setup() {
  Serial.begin(9600);

  // Uncomment to wait for serial comm to begin setup
  // while (!Serial);

  // initialize digital pins
  pinMode(LEDR, OUTPUT);
  pinMode(LEDG, OUTPUT);
  pinMode(LEDB, OUTPUT);

  // Allocate struct
  qrtone = qrtone_new();

  // Init internal state
  qrtone_init(qrtone, SAMPLE_RATE);

  // Init callback method for displaying noise level for gate frequencies
  // qrtone_set_level_callback(qrtone, NULL, debug_serial);

  // configure the data receive callback
  PDM.onReceive(onPDMdata);

  // optionally set the gain, defaults to 20
  PDM.setGain(30);

  // initialize PDM with:
  // - one channel (mono mode)
  // - a 42 kHz sample rate
  if (!PDM.begin(1, SAMPLE_RATE)) {
    Serial.println("Failed to start PDM!");
    while (1);
  }


  analogWrite(LEDR, UINT8_MAX);
  analogWrite(LEDG, UINT8_MAX);
  analogWrite(LEDB, UINT8_MAX);
}

void debug_serial(void *ptr, int64_t location, float first_tone_level, float second_tone_level, int32_t triggered) {
  char buf[100];
  sprintf(buf, "%.2f,%.2f,%ld\n\r", first_tone_level, second_tone_level, triggered * -10);
  Serial.write(buf);
}

void loop() {
  //Once the recording buffer is full, we process it.
  if (scaled_input_buffer_feed_cursor > scaled_input_buffer_consume_cursor)
  {
    if(scaled_input_buffer_feed_cursor - scaled_input_buffer_consume_cursor > MAX_AUDIO_WINDOW_SIZE) {
      // overflow
      Serial.println("Buffer overflow");
    }
    int sample_to_read = scaled_input_buffer_feed_cursor - scaled_input_buffer_consume_cursor;
    int max_window_length = qrtone_get_maximum_length(qrtone);
    int sample_index = 0;
    while(sample_index < sample_to_read) {
      int32_t position_in_buffer = ((scaled_input_buffer_consume_cursor + sample_index) % MAX_AUDIO_WINDOW_SIZE);
      int32_t window_length = min(max_window_length, min(sample_to_read - sample_index, MAX_AUDIO_WINDOW_SIZE - position_in_buffer % MAX_AUDIO_WINDOW_SIZE));
      if(qrtone_push_samples(qrtone, scaled_input_buffer + position_in_buffer, window_length)) {
        // Got a message        
        int8_t* data = qrtone_get_payload(qrtone);
        int32_t data_length = qrtone_get_payload_length(qrtone);
        if(data_length >= 3) {
          char buf[100];
          sprintf(buf, "%d,%d,%d\n\r", (uint8_t)data[0], (uint8_t)data[1], (uint8_t)data[2]);
          Serial.write(buf);
          analogWrite(LEDR, UINT8_MAX - (uint8_t)data[0]);
          analogWrite(LEDG, UINT8_MAX - (uint8_t)data[1]);
          analogWrite(LEDB, UINT8_MAX - (uint8_t)data[2]);
        }
      }
      sample_index += window_length;
      max_window_length = qrtone_get_maximum_length(qrtone);      
    }
    scaled_input_buffer_consume_cursor += sample_index;
  }  
}

void onPDMdata() {
  // query the number of bytes available
  int bytesAvailable = PDM.available();

  // read into the sample buffer
  PDM.read(sampleBuffer, bytesAvailable);

  // 16-bit, 2 bytes per sample
  samplesRead = bytesAvailable / 2;

  for(int sample_id = 0; sample_id < samplesRead; sample_id++) {
    scaled_input_buffer[(scaled_input_buffer_feed_cursor+sample_id) % MAX_AUDIO_WINDOW_SIZE] = (float)sampleBuffer[sample_id] / 32768.0f;
  }
  scaled_input_buffer_feed_cursor += samplesRead;
}
