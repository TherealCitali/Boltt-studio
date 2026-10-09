// moyface.js — encoder/decoder for the MoYoung "API 0x23" watch-face format
// (Fire-Boltt Brillia, firmware MOY-7QI2-2.0.1, 240x296 screen).
// Images are {w, h, data: Uint8ClampedArray RGBA}. Works in browsers and Node.
(function (root) {
  const W = 240, H = 296, PREVIEW_W = 140, PREVIEW_H = 163;

  function encodeRow(img, y) {
    const { w, data } = img, px = new Array(w);
    for (let x = 0; x < w; x++) {
      const i = (y * w + x) * 4, r = data[i], g = data[i + 1], b = data[i + 2], a = data[i + 3];
      const v = ((r >> 3) << 11) | ((g >> 2) << 5) | (b >> 3);
      px[x] = (a << 16) | v;                       // stored as [alpha, RGB565 big-endian]
    }
    const out = [], lit = [];
    const put = p => out.push(p >> 16, (p >> 8) & 255, p & 255);
    const flush = () => { while (lit.length) { const c = lit.splice(0, 127); out.push(c.length); c.forEach(put); } };
    let i = 0;
    while (i < w) {
      let j = i;
      while (j < w && px[j] === px[i] && j - i < 127) j++;
      if (j - i >= 2) { flush(); out.push(0x80 | (j - i)); put(px[i]); i = j; }
      else { lit.push(px[i]); i++; }
    }
    flush();
    return out;
  }

  function encodeImage(img) {
    const rows = [], table = [];
    let pos = 4 * img.h;
    for (let y = 0; y < img.h; y++) {
      const r = encodeRow(img, y);
      if (r.length >= 2048) throw new Error('compressed row too long');
      table.push(pos & 0xffff, (r.length << 5) | (pos >> 16));
      rows.push(r); pos += r.length;
    }
    const len = pos + ((4 - (pos % 4)) % 4), out = new Uint8Array(len), dv = new DataView(out.buffer);
    table.forEach((v, k) => dv.setUint16(2 * k, v, true));
    let o = 4 * img.h;
    for (const r of rows) { out.set(r, o); o += r.length; }
    return out;
  }

  // digitSets: array of 10-image arrays; timeXY: [[x,y] x4] for H1 H2 M1 M2; timeSets: digit set per position
  function buildFace({ background, digitSets, timeXY, timeSets = [0, 0, 1, 1], preview, dash }) {
    if (background.w !== W || background.h !== H) throw new Error('background must be 240x296');
    dash = dash || { w: 1, h: 1, data: new Uint8ClampedArray(4) };
    const n = digitSets.length;
    const hdr = 16 + 2 + 83 * n + 10 + 14 + 34 + 2, hdrAligned = hdr + ((4 - (hdr % 4)) % 4);
    const blobs = []; let pos = hdrAligned;
    const add = img => { const b = encodeImage(img); const o = pos; blobs.push(b); pos += b.length; return o; };
    const dashOff = add(dash), bgOff = add(background);
    const digitOffs = digitSets.map(s => s.map(add));
    const prevOff = add(preview);
    const out = new Uint8Array(pos), dv = new DataView(out.buffer);
    let o = 0;
    const u8 = v => { out[o++] = v; }, u16 = v => { dv.setUint16(o, v, true); o += 2; }, u32 = v => { dv.setUint32(o, v, true); o += 4; };
    u16(0x23); u16(0xffff); u32(prevOff); u16(preview.w); u16(preview.h); u16(16); u16(16 + 2 + 83 * n);
    u8(1); u8(1);
    digitSets.forEach((s, si) => {
      u8(si);
      s.forEach((g, k) => { u32(digitOffs[si][k]); u16(g.w); u16(g.h); });
      u16(si === 0 ? 0x0101 : 0);
    });
    u8(1); u8(0x23); u32(dashOff); u16(dash.w); u16(dash.h);
    u8(1); u8(0x00); u16(0); u16(0); u32(bgOff); u16(W); u16(H);
    u8(1); u8(0x02); timeSets.forEach(u8); timeXY.forEach(([x, y]) => { u16(x); u16(y); }); o += 12;
    o = hdrAligned;
    for (const b of blobs) { out.set(b, o); o += b.length; }
    return out;
  }

  function decodeImage(buf, off, w, h) {
    const dv = new DataView(buf.buffer, buf.byteOffset, buf.byteLength), data = new Uint8ClampedArray(w * h * 4);
    for (let y = 0; y < h; y++) {
      const lo = dv.getUint16(off + 4 * y, true), sz = dv.getUint16(off + 4 * y + 2, true);
      let i = off + lo + ((sz & 31) << 16); const end = i + (sz >> 5); let x = 0;
      const put = k => {
        if (x < w) {
          const a = buf[k], v = (buf[k + 1] << 8) | buf[k + 2], r = (v >> 11) & 31, g = (v >> 5) & 63, b = v & 31, p = (y * w + x) * 4;
          data[p] = (r << 3) | (r >> 2); data[p + 1] = (g << 2) | (g >> 4); data[p + 2] = (b << 3) | (b >> 2); data[p + 3] = a;
        }
        x++;
      };
      while (i < end) {
        const c = buf[i++];
        if (c & 0x80) { for (let k = 0; k < (c & 127); k++) put(i); i += 3; }
        else { for (let k = 0; k < c; k++) { put(i); i += 3; } }
      }
    }
    return { w, h, data };
  }

  function parsePreview(buf) {
    const dv = new DataView(buf.buffer, buf.byteOffset, buf.byteLength);
    if (dv.getUint16(0, true) !== 0x23) throw new Error('not an API 0x23 face file');
    return decodeImage(buf, dv.getUint32(4, true), dv.getUint16(8, true), dv.getUint16(10, true));
  }

  const api = { W, H, PREVIEW_W, PREVIEW_H, encodeImage, buildFace, decodeImage, parsePreview };
  if (typeof module !== 'undefined') module.exports = api; else root.MoyFace = api;
})(this);
