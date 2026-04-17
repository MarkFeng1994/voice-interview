/**
 * AudioWorklet Processor for Real-time PCM Audio Capture
 *
 * Captures microphone input at 16kHz mono and outputs PCM chunks
 * for WebSocket transmission to backend.
 */

class PCMProcessor extends AudioWorkletProcessor {
  constructor() {
    super()
    this.bufferSize = 4096 // ~256ms at 16kHz
    this.buffer = new Float32Array(this.bufferSize)
    this.bufferIndex = 0
  }

  process(inputs, outputs, parameters) {
    const input = inputs[0]

    // No input or no channels
    if (!input || !input.length) {
      return true
    }

    const inputChannel = input[0] // Mono channel

    for (let i = 0; i < inputChannel.length; i++) {
      this.buffer[this.bufferIndex++] = inputChannel[i]

      // Buffer full, send chunk
      if (this.bufferIndex >= this.bufferSize) {
        this.port.postMessage(this.buffer.slice(0, this.bufferIndex))
        this.bufferIndex = 0
      }
    }

    return true // Keep processor alive
  }
}

registerProcessor('pcm-processor', PCMProcessor)
