export type DecodedGroupPhoto = {
  bytes: Buffer;
  contentType: "image/jpeg" | "image/png" | "image/webp";
  extension: "jpg" | "png" | "webp";
};

const maximumDecodedBytes = 5 * 1024 * 1024;
const maximumEncodedCharacters = 7_000_000;

/**
 * Decodes the already-cropped group avatar sent by a native WAPI client.
 *
 * The MIME type is detected from the bytes instead of trusting the phone. This
 * keeps the privileged server upload limited to JPEG, PNG and WebP images.
 */
export function decodeGroupPhotoBase64(value: unknown): DecodedGroupPhoto | null {
  if (value === undefined || value === null || value === "") return null;
  if (typeof value !== "string" || value.length > maximumEncodedCharacters) {
    throw new Error("group-photo-size");
  }
  const encoded = value.trim();
  if (!encoded || !/^[A-Za-z0-9+/]+={0,2}$/.test(encoded)) {
    throw new Error("group-photo-base64");
  }
  const bytes = Buffer.from(encoded, "base64");
  if (bytes.length < 32 || bytes.length > maximumDecodedBytes) {
    throw new Error("group-photo-size");
  }
  const canonicalInput = encoded.replace(/=+$/, "");
  const canonicalOutput = bytes.toString("base64").replace(/=+$/, "");
  if (canonicalInput !== canonicalOutput) throw new Error("group-photo-base64");

  if (bytes[0] === 0xff && bytes[1] === 0xd8 && bytes[2] === 0xff) {
    return { bytes, contentType: "image/jpeg", extension: "jpg" };
  }
  if (bytes.subarray(0, 8).equals(Buffer.from([0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a]))) {
    return { bytes, contentType: "image/png", extension: "png" };
  }
  if (bytes.subarray(0, 4).toString("ascii") === "RIFF" && bytes.subarray(8, 12).toString("ascii") === "WEBP") {
    return { bytes, contentType: "image/webp", extension: "webp" };
  }
  throw new Error("group-photo-format");
}
