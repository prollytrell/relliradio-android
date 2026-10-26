// Pitch shifter (runs on the audio thread).
// Two overlapping "read heads" sweep through a short delay line; reading faster or
// slower than real time raises or lowers the pitch without changing the speed.
// Their volumes cross-fade (sin²) so the jumps between grains aren't heard.

class PitchShifter extends AudioWorkletProcessor {
  static get parameterDescriptors() {
    return [{ name: "ratio", defaultValue: 1, minValue: 0.5, maxValue: 2, automationRate: "k-rate" }];
  }

  constructor() {
    super();
    this.size = 16384;                 // ring buffer length (power of two)
    this.mask = this.size - 1;
    this.window = Math.round(sampleRate * 0.06); // 60 ms grains
    this.buffers = [new Float32Array(this.size), new Float32Array(this.size)];
    this.write = 0;
    this.phase = 0;
  }

  read(buf, pos) {
    const i = Math.floor(pos);
    const f = pos - i;
    const a = buf[i & this.mask];
    const b = buf[(i + 1) & this.mask];
    return a + (b - a) * f;
  }

  process(inputs, outputs, params) {
    const input = inputs[0];
    const output = outputs[0];
    const ratio = params.ratio[0];
    if (!input.length) return true;
    const frames = input[0].length;

    if (Math.abs(ratio - 1) < 0.001) {
      for (let c = 0; c < output.length; c++) output[c].set(input[Math.min(c, input.length - 1)]);
      // keep the buffer warm so switching back is smooth
      for (let n = 0; n < frames; n++) {
        for (let c = 0; c < 2; c++) this.buffers[c][this.write] = input[Math.min(c, input.length - 1)][n];
        this.write = (this.write + 1) & this.mask;
      }
      return true;
    }

    const step = (1 - ratio) / this.window;
    for (let n = 0; n < frames; n++) {
      for (let c = 0; c < 2; c++) this.buffers[c][this.write] = input[Math.min(c, input.length - 1)][n];

      this.phase += step;
      if (this.phase >= 1) this.phase -= 1;
      if (this.phase < 0) this.phase += 1;
      const p2 = (this.phase + 0.5) % 1;
      const d1 = this.phase * this.window;
      const d2 = p2 * this.window;
      const g1 = Math.sin(Math.PI * this.phase) ** 2;
      const g2 = Math.sin(Math.PI * p2) ** 2;

      for (let c = 0; c < output.length; c++) {
        const buf = this.buffers[Math.min(c, 1)];
        output[c][n] =
          this.read(buf, this.write - d1 - 1 + this.size) * g1 +
          this.read(buf, this.write - d2 - 1 + this.size) * g2;
      }
      this.write = (this.write + 1) & this.mask;
    }
    return true;
  }
}

registerProcessor("pitch-shifter", PitchShifter);
