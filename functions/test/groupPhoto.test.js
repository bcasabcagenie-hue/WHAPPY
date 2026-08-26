const test = require("node:test");
const assert = require("node:assert/strict");
const { decodeGroupPhotoBase64 } = require("../lib/groupPhoto.js");

function payload(prefix, size = 64) {
  const bytes = Buffer.alloc(size, 0);
  Buffer.from(prefix).copy(bytes);
  return bytes.toString("base64");
}

test("accepts JPEG, PNG and WebP bytes", () => {
  assert.equal(decodeGroupPhotoBase64(payload([0xff, 0xd8, 0xff])).contentType, "image/jpeg");
  assert.equal(decodeGroupPhotoBase64(payload([0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a])).contentType, "image/png");
  const webp = Buffer.alloc(64, 0);
  webp.write("RIFF", 0, "ascii");
  webp.write("WEBP", 8, "ascii");
  assert.equal(decodeGroupPhotoBase64(webp.toString("base64")).contentType, "image/webp");
});

test("rejects malformed and non-image payloads", () => {
  assert.throws(() => decodeGroupPhotoBase64("not base64 !!"));
  assert.throws(() => decodeGroupPhotoBase64(Buffer.alloc(64, 1).toString("base64")));
});

test("accepts an omitted photo", () => {
  assert.equal(decodeGroupPhotoBase64(undefined), null);
  assert.equal(decodeGroupPhotoBase64(""), null);
});
